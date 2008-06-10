/*
 *  MenuFactory.java
 *  Meloncillo
 *
 *  Copyright (c) 2004-2005 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		15-Jun-04   Filter Trajectories
 *		31-Jul-04   commented and cleaned up.
 *		03-Aug-04   small changes due to modified SyncCompoundEdit.
 *					uses MRJAdpater to set file type and creator.
 *		08-Aug-04   View Menu->Surface Shows added
 *		02-Feb-05	support for warning messages when loading sessions
 *		15-Mar-05	added support for different receiver types
 *		26-Mar-05	bugfix in remove-session-objects
 *		07-Apr-05	help menu
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.undo.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import net.roydesign.app.AboutJMenuItem;
import net.roydesign.app.PreferencesJMenuItem;
import net.roydesign.app.QuitJMenuItem;
import net.roydesign.mac.MRJAdapter;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.debug.*;
import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.io.*;
import de.sciss.meloncillo.lisp.*;
import de.sciss.meloncillo.math.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.receiver.*;
import de.sciss.meloncillo.render.*;
import de.sciss.meloncillo.transmitter.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.gui.*;
import de.sciss.io.*;

/**
 *  <code>JMenu</code>s cannot be added to more than
 *  one frame. Since on MacOS there's one
 *  global menu for all the application windows
 *  we need to 'duplicate' a menu prototype.
 *  Synchronizing all menus is accomplished
 *  by using the same action objects for all
 *  menu copies. However when items are added
 *  or removed, synchronization needs to be
 *  performed manually. That's the point about
 *  this class.
 *  <p>
 *  <code>JInternalFrames</code> have been removed
 *  because they don't offer a constistent look-and-feel
 *  on MacOS and besides the main window would
 *  have to occupy most of the visible screen.
 *  Unfortunately this means we cannot use
 *  'floating' palettes any more.
 *  <p>
 *  There can be only one instance of <code>MenuFactory</code>
 *  for the application, and that will be created by the
 *  <code>Main</code> class.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.74, 31-May-05
 *
 *  @see	de.sciss.meloncillo.Main#menuFactory
 *
 *  @todo   on operating systems that do not have a
 *			global screen menu bar but attach a menubar
 *			directly to a windows frame &mdash; this happens
 *			on Linux and Windows &mdash; not every frame should
 *			display the global menu. Small windows such
 *			as the palettes should go without a menubar
 *			but nevertheless a way of responding to accelerator
 *			keys should be found.
 *  @todo   see actionNewReceiversClass.actionPerformed.
 *	@todo	some menu accelerators do not work on german keyboard.
 *			it's impossible(?) to assign meta+questionmark to the help index
 *			item for example.
 */
public class MenuFactory
{
	/**
	 *	<code>KeyStroke</code> modifier mask
	 *	representing the platform's default
	 *	menu accelerator (e.g. Apple-key on Mac,
	 *	Ctrl on Windows).
	 *
	 *	@see	Toolkit#getMenuShortcutKeyMask()
	 */
	public static final int MENU_SHORTCUT = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

	private final Main		root;
	private final Session   doc;
	private final JMenuBar	protoType;

	private JMenu   openRecentMenu;

	private actionOpenClass				actionOpen;
	private actionOpenRecentClass		actionOpenRecent;
	private actionSaveClass				actionSave;
	private actionSaveAsClass			actionSaveAs;
	private SyncedPrefsMenuAction		actionSnapToObjects, actionViewRcvSense, actionViewRcvSenseEqP,
										actionViewTrnsTraj, actionViewUserImages, actionViewRulers;

	private Action	actionClearSession, actionClearRecent,
					actionCut, actionCopy, actionPaste, actionClear, actionSelectAll,
					actionInsTimeSpan, actionNewReceivers, actionNewTransmitters, actionNewGroup,
					actionRemoveTransmitters, actionRemoveGroups, actionFilter,
					actionBounce, actionSelectionBackwards,
					actionShowSurface, actionShowTimeline, actionShowTransport,
					actionShowObserver, actionShowMeter, actionShowRealtime, actionAbout,
					actionHelpManual, actionHelpShortcuts, actionHelpWebsite, actionQuit;
	
	private Action  actionDebugDumpUndo, actionDebugDumpTracks, actionDebugViewTrack,
					actionDebugDumpPrefs, actionDebugDumpRealtime, actionJathaDiddler,
					actionDebugDumpListeners, actionHRIRPrepare;

	private final Vector		collMenuHosts		= new Vector();
	private final Hashtable		syncedItems			= new Hashtable();
	private final Vector		collGlobalKeyCmd	= new Vector();
	private final PathList		openRecentPaths;

	// for custom JOptionPane calls (see actionNewReceiversClass )
	private final String[]	queryOptions;

	// --- publicly accessible actions ---
	/**
	 *  Action that removes all
	 *  selected receivers.
	 */
	public actionRemoveSessionObjectClass	actionRemoveReceivers;
	/**
	 *  Action that advances the
	 *  the timeline selection.
	 */
	public actionSelectionForwardClass  actionSelectionForward;
	/**
	 *  Action that opens the
	 *  preferences frame.
	 */
	public actionPreferencesClass		actionPreferences;
	
	/**
	 *  The constructor is called only once by
	 *  the <code>Main</code> class and will create a prototype
	 *  main menu from which all copies are
	 *  derived.
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
	public MenuFactory( Main root, Session doc )
	{
		this.root   = root;
		this.doc	= doc;
		
		final de.sciss.app.Application	app	= AbstractApplication.getApplication();
		
		queryOptions = new String[] {
			app.getResourceString( "buttonCancel" ),
			app.getResourceString( "buttonOk" )
		};
													 
		openRecentPaths = new PathList( 8, AbstractApplication.getApplication().getUserPrefs(),
										PrefsUtil.KEY_OPENRECENT );
		protoType		= new JMenuBar();

		createActions();
		createProtoType();
	}
	
	/**
	 *  Requests a copy of the main menu.
	 *  When the frame is disposed, it should
	 *  call the <code>forgetAbout</code> method.
	 *
	 *  @param  who		the frame who requests the menu. The
	 *					menu will be set for the frame by this
	 *					method.
	 *
	 *  @see	#forgetAbout( JFrame )
	 *  @see	javax.swing.JFrame#setJMenuBar( JMenuBar )
	 *  @synchronization	must be called in the event thread
	 */
	public void gimmeSomethingReal( JFrame who )
	{
		Action		a;
		String		entry;
		int			i;
		JRootPane   rp  = who.getRootPane();
	
		JMenuBar copy = createMenuBarCopy();
		who.setJMenuBar( copy );
		collMenuHosts.add( who );
		for( i = 0; i < collGlobalKeyCmd.size(); i++ ) {
			a		= (Action) collGlobalKeyCmd.get( i );
			entry   = (String) a.getValue( Action.NAME );
			rp.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW ).put( (KeyStroke) a.getValue( Action.ACCELERATOR_KEY ), entry );
			rp.getActionMap().put( entry, a );
		}
	}
	
	/**
	 *  Tell the <code>MenuFactory</code> that a frame
	 *  is being disposed, therefore allowing the removal
	 *  of the menu bar which will free resources and
	 *  remove unnecessary synchronization.
	 *
	 *  @param  who		the frame which is about to be disposed
	 *
	 *  @see	#gimmeSomethingReal( JFrame )
	 *  @todo   this method should remove any key actions
	 *			attached to the input maps.
	 *  @synchronization	must be called in the event thread
	 */
	public void forgetAbout( JFrame who )
	{
		collMenuHosts.remove( who );
		who.setJMenuBar( null );
	}

	/**
	 *  Sets all JMenuBars enabled or disabled.
	 *  When time taking asynchronous processing
	 *  is done, like loading a session or bouncing
	 *  it to disk, the menus need to be disabled
	 *  to prevent the user from accidentally invoking
	 *  menu actions that can cause deadlocks if they
	 *  try to gain access to blocked doors. This
	 *  method traverses the list of known frames and
	 *  sets each frame's menu bar enabled or disabled.
	 *
	 *  @param  enabled		<code>true</code> to enable
	 *						all menu bars, <code>false</code>
	 *						to disable them.
	 *  @synchronization	must be called in the event thread
	 */
	public void setMenuBarsEnabled( boolean enabled )
	{
		JMenuBar mb;
	
		for( int i = 0; i < collMenuHosts.size(); i++ ) {
			mb = ((JFrame) collMenuHosts.get( i )).getJMenuBar();
			if( mb != null ) mb.setEnabled( enabled );
		}
	}

	private static int uniqueNumber = 0;	// increased by addGlobalKeyCommand()
	/**
	 *  Adds an action object invisibly to all
	 *  menu bars, enabling its keyboard shortcut
	 *  to be accessed no matter what window
	 *  has the focus.
	 *
	 *  @param  a   the <code>Action</code> whose
	 *				accelerator key should be globally
	 *				accessible. The action
	 *				is stored in the input and action map of each
	 *				registered frame's root pane, thus being
	 *				independant of calls to <code>setMenuBarsEnabled/code>.
	 *
	 *  @throws java.lang.IllegalArgumentException  if the action does
	 *												not have an associated
	 *												accelerator key
	 *
	 *  @see  javax.swing.Action#ACCELERATOR_KEY
	 *  @synchronization	must be called in the event thread
	 */
	public void addGlobalKeyCommand( Action a )
	{
		JFrame		frame;
		JRootPane   rp;
		String		entry;
		int			i;
		KeyStroke   acc		= (KeyStroke) a.getValue( Action.ACCELERATOR_KEY );
		
		if( acc == null ) throw new IllegalArgumentException();
		
		entry = "key" + String.valueOf( uniqueNumber++ );
		a.putValue( Action.NAME, entry );

		for( i = 0; i < collMenuHosts.size(); i++ ) {
			frame   = (JFrame) collMenuHosts.get( i );
			rp		= frame.getRootPane();
			rp.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW ).put( acc, entry );
			rp.getActionMap().put( entry, a );
		}
		collGlobalKeyCmd.add( a );
	}

	private void createActions()
	{
		final de.sciss.app.Application	app = AbstractApplication.getApplication();
		
		// --- file menu ---
		actionClearSession	= new actionClearSessionClass( app.getResourceString( "menuClearSession" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_N, MENU_SHORTCUT ));
		actionOpen		= new actionOpenClass(  app.getResourceString( "menuOpen" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_O, MENU_SHORTCUT ));
		actionOpenRecent = new actionOpenRecentClass( app.getResourceString( "menuOpenRecent" ));
		actionClearRecent = new actionClearRecentClass( app.getResourceString( "menuClearRecent" ), null );
		actionSave		= new actionSaveClass(  app.getResourceString( "menuSave" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_S, MENU_SHORTCUT ));
		actionSaveAs	= new actionSaveAsClass( app.getResourceString( "menuSaveAs" ), KeyStroke.getKeyStroke(
												KeyEvent.VK_S, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));

		actionNewReceivers = new actionNewReceiversClass(  app.getResourceString( "menuNewReceivers" ),
							KeyStroke.getKeyStroke( KeyEvent.VK_N, MENU_SHORTCUT + KeyEvent.ALT_MASK ));
		actionNewTransmitters = new actionNewTransmittersClass( app.getResourceString( "menuNewTransmitters" ),
							KeyStroke.getKeyStroke( KeyEvent.VK_N, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		actionNewGroup	= new actionNewGroupClass( app.getResourceString( "menuNewGroup" ), null );
		actionRemoveReceivers = new actionRemoveSessionObjectClass( app.getResourceString( "menuRemoveReceivers" ),
											null, Session.DOOR_RCV, doc.receivers, doc.selectedReceivers, doc.groups );
		actionRemoveTransmitters = new actionRemoveSessionObjectClass( app.getResourceString( "menuRemoveTransmitters" ),
											null, Session.DOOR_TRNS, doc.transmitters, doc.selectedTransmitters, doc.groups );
		actionRemoveGroups	= new actionRemoveSessionObjectClass( app.getResourceString( "menuRemoveGroups" ),
											null, Session.DOOR_GRP, doc.groups, doc.selectedGroups, null );

		actionBounce	= new actionBounceClass( app.getResourceString( "menuBounce" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_R, MENU_SHORTCUT ));
		actionQuit		= new actionQuitClass(  app.getResourceString( "menuQuit" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_Q, MENU_SHORTCUT ));

		// --- edit menu ---
		actionCut		= new actionCutClass(   app.getResourceString( "menuCut" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_X, MENU_SHORTCUT ));
		actionCopy		= new actionCopyClass(  app.getResourceString( "menuCopy" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_C, MENU_SHORTCUT ));
		actionPaste		= new actionPasteClass( app.getResourceString( "menuPaste" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_V, MENU_SHORTCUT ));
//		actionClear		= new actionClearClass( app.getResourceString( "menuClear" ),
//												KeyStroke.getKeyStroke( KeyEvent.VK_BACK_SPACE, MENU_SHORTCUT ));
		actionClear		= new actionClearClass( app.getResourceString( "menuClear" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_BACK_SPACE, 0 ));
		actionSelectAll = new actionSelectAllClass( app.getResourceString( "menuSelectAll" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_A, MENU_SHORTCUT ));
		actionPreferences = new actionPreferencesClass( app.getResourceString( "menuPreferences" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_COMMA, MENU_SHORTCUT ));

		// --- timeline menu ---
		actionInsTimeSpan   = new actionInsTimeSpanClass( app.getResourceString( "menuInsTimeSpan" ),
									KeyStroke.getKeyStroke( KeyEvent.VK_E, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		actionSelectionForward = new actionSelectionForwardClass( app.getResourceString( "menuSelectionForward" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_CLOSE_BRACKET, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		actionSelectionBackwards = new actionSelectionBackwardsClass( app.getResourceString( "menuSelectionBackwards" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_OPEN_BRACKET, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		actionFilter = new actionFilterClass( app.getResourceString( "menuFilter" ),
										KeyStroke.getKeyStroke( KeyEvent.VK_F, MENU_SHORTCUT ));

		// --- view menu ---
		actionSnapToObjects		= new SyncedPrefsMenuAction( app.getResourceString( "menuSnapToObjects" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_LESS, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		syncedItems.put( actionSnapToObjects, new Vector() );
		actionViewRcvSense		= new SyncedPrefsMenuAction( app.getResourceString( "menuViewRcvSense" ), null );
		syncedItems.put( actionViewRcvSense, new Vector() );
		actionViewRcvSenseEqP	= new SyncedPrefsMenuAction( app.getResourceString( "menuViewRcvSenseEqP" ), null );
		syncedItems.put( actionViewRcvSenseEqP, new Vector() );
		actionViewTrnsTraj		= new SyncedPrefsMenuAction( app.getResourceString( "menuViewTrnsTraj" ), null );
		syncedItems.put( actionViewTrnsTraj, new Vector() );
		actionViewUserImages	= new SyncedPrefsMenuAction( app.getResourceString( "menuViewUserImages" ), null );
		syncedItems.put( actionViewUserImages, new Vector() );
		actionViewRulers		= new SyncedPrefsMenuAction( app.getResourceString( "menuViewRulers" ), null );
		syncedItems.put( actionViewRulers, new Vector() );

		// --- window menu ---
		actionShowSurface		= new actionShowWindowClass( app.getResourceString( "frameSurface" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_MULTIPLY, MENU_SHORTCUT ), Main.COMP_SURFACE );
		actionShowTimeline		= new actionShowWindowClass( app.getResourceString( "frameTimeline" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_EQUALS, MENU_SHORTCUT ), Main.COMP_TIMELINE );
		actionShowTransport		= new actionShowWindowClass( app.getResourceString( "paletteTransport" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_NUMPAD1, MENU_SHORTCUT ), Main.COMP_TRANSPORT );
		actionShowObserver		= new actionShowWindowClass( app.getResourceString( "paletteObserver" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_NUMPAD3, MENU_SHORTCUT ), Main.COMP_OBSERVER );
		actionShowMeter			= new actionShowWindowClass( app.getResourceString( "frameMeter" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_NUMPAD4, MENU_SHORTCUT ), Main.COMP_METER );
		actionShowRealtime		= new actionShowWindowClass( app.getResourceString( "frameRealtime" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_NUMPAD2, MENU_SHORTCUT ), Main.COMP_REALTIME );
		actionAbout				= new actionAboutClass( app.getResourceString( "menuAbout" ), null );

		// --- extras menu ---
		actionJathaDiddler		= JathaDiddler.getMenuAction( root );
		actionHRIRPrepare		= HRIRPrepareDialog.getMenuAction( root, doc );

		// --- debug menu ---
		actionDebugDumpUndo		= doc.getUndoManager().getDebugDumpAction();
		actionDebugDumpPrefs	= PrefsUtil.getDebugDumpAction( root, doc );
		actionDebugDumpTracks   = DebugTrackEditor.getDebugDumpAction( root, doc );
		actionDebugViewTrack	= DebugTrackEditor.getDebugViewAction( root, doc );
		actionDebugDumpRealtime	= root.transport.getDebugDumpAction();
		actionDebugDumpListeners= new actionDebugDumpListenersClass();

		// --- help menu ---
// KeyStroke.getKeyStroke( KeyEvent.VK_MINUS, MENU_SHORTCUT + KeyEvent.SHIFT_MASK )
// KeyStroke.getKeyStroke( new Character( '?' ), MENU_SHORTCUT )
		actionHelpManual		= new actionURLViewerClass( app.getResourceString( "menuHelpManual" ), null, "index", false );
		actionHelpShortcuts		= new actionURLViewerClass( app.getResourceString( "menuHelpShortcuts" ), null, "Shortcuts", false );
		actionHelpWebsite		= new actionURLViewerClass( app.getResourceString( "menuHelpWebsite" ), null, app.getResourceString( "appURL" ), true );
	}
	
	private void createProtoType()
	{
		JMenu							mainMenu, subMenu;
		JCheckBoxMenuItem				cbmi;
		JMenuItem						mi;
		Preferences						prefs;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
		final de.sciss.app.UndoManager	undo	= doc.getUndoManager();

		mainMenu = new JMenu( app.getResourceString( "menuFile" ));
		mainMenu.add( new JMenuItem( actionClearSession ));
		mainMenu.add( new JMenuItem( actionOpen ));
		openRecentMenu = new JMenu( actionOpenRecent );
		if( openRecentPaths.getPathCount() > 0 ) {
			for( int i = 0; i < openRecentPaths.getPathCount(); i++ ) {
				openRecentMenu.add( new JMenuItem( new actionOpenRecentClass( openRecentPaths.getPath( i ))));
			}
			actionOpenRecent.setPath( openRecentPaths.getPath( 0 ));
		}
		openRecentMenu.addSeparator();
		openRecentMenu.add( new JMenuItem( actionClearRecent ));
		mainMenu.add( openRecentMenu );
		mainMenu.addSeparator();
		mainMenu.add( new JMenuItem( actionSave ));
		mainMenu.add( new JMenuItem( actionSaveAs ));

		mainMenu.addSeparator();
		subMenu = new JMenu( app.getResourceString( "menuInsert" ));
		subMenu.add( new JMenuItem( actionNewReceivers ));
		subMenu.add( new JMenuItem( actionNewTransmitters ));
		subMenu.add( new JMenuItem( actionNewGroup ));
		mainMenu.add( subMenu );
		subMenu = new JMenu( app.getResourceString( "menuRemove" ));
		subMenu.add( new JMenuItem( actionRemoveReceivers ));
		subMenu.add( new JMenuItem( actionRemoveTransmitters ));
		subMenu.add( new JMenuItem( actionRemoveGroups ));
		mainMenu.add( subMenu );
		mainMenu.addSeparator();

		mainMenu.add( new JMenuItem( actionBounce ));
		mi		= root.getQuitJMenuItem();
		mi.setAction( actionQuit );
		if( !QuitJMenuItem.isAutomaticallyPresent() ) {
			mainMenu.addSeparator();
			mainMenu.add( mi );
		}
		protoType.add( mainMenu );

		mainMenu = new JMenu( app.getResourceString( "menuEdit" ));
		mainMenu.add( new JMenuItem( undo.getUndoAction() ));
		mainMenu.add( new JMenuItem( undo.getRedoAction() ));
		mainMenu.addSeparator();
		mainMenu.add( new JMenuItem( actionCut ));
		mainMenu.add( new JMenuItem( actionCopy ));
		mainMenu.add( new JMenuItem( actionPaste ));
		mainMenu.add( new JMenuItem( actionClear ));
		mainMenu.addSeparator();
		mainMenu.add( new JMenuItem( actionSelectAll ));
		mi		= root.getPreferencesJMenuItem();
		mi.setAction( actionPreferences );
		if( !PreferencesJMenuItem.isAutomaticallyPresent() ) {
			mainMenu.addSeparator();
			mainMenu.add( mi );
		}
		protoType.add( mainMenu );

//		surfaceMenu = new JMenu( Main.getResourceString( "menuSurface" ));
//		protoType.add( surfaceMenu );

		mainMenu = new JMenu( app.getResourceString( "menuTimeline" ));
		mainMenu.add( new JMenuItem( actionInsTimeSpan ));
		mainMenu.add( new JMenuItem( actionSelectionForward ));
		mainMenu.add( new JMenuItem( actionSelectionBackwards ));
		mainMenu.addSeparator();
		mainMenu.add( new JMenuItem( actionFilter ));
		protoType.add( mainMenu );

		mainMenu	= new JMenu( app.getResourceString( "menuView" ));
		prefs		= app.getUserPrefs().node( PrefsUtil.NODE_SHARED );
		cbmi		= new JCheckBoxMenuItem( actionSnapToObjects );
		((Vector) syncedItems.get( actionSnapToObjects )).add( cbmi );
		actionSnapToObjects.setPreferences( prefs, PrefsUtil.KEY_SNAP );
		mainMenu.add( cbmi );
		subMenu		= new JMenu( app.getResourceString( "menuViewSurface" ));
		cbmi		= new JCheckBoxMenuItem( actionViewRcvSense );
		((Vector) syncedItems.get( actionViewRcvSense )).add( cbmi );
		actionViewRcvSense.setPreferences( prefs, PrefsUtil.KEY_VIEWRCVSENSE );
		subMenu.add( cbmi );
		cbmi		= new JCheckBoxMenuItem( actionViewRcvSenseEqP );
		((Vector) syncedItems.get( actionViewRcvSenseEqP )).add( cbmi );
		actionViewRcvSenseEqP.setPreferences( prefs, PrefsUtil.KEY_VIEWEQPRECEIVER );
		subMenu.add( cbmi );
		cbmi		= new JCheckBoxMenuItem( actionViewTrnsTraj );
		((Vector) syncedItems.get( actionViewTrnsTraj )).add( cbmi );
		actionViewTrnsTraj.setPreferences( prefs, PrefsUtil.KEY_VIEWTRNSTRAJ );
		subMenu.add( cbmi );
		cbmi		= new JCheckBoxMenuItem( actionViewUserImages );
		((Vector) syncedItems.get( actionViewUserImages )).add( cbmi );
		actionViewUserImages.setPreferences( prefs, PrefsUtil.KEY_VIEWUSERIMAGES );
		subMenu.add( cbmi );
		cbmi		= new JCheckBoxMenuItem( actionViewRulers );
		((Vector) syncedItems.get( actionViewRulers )).add( cbmi );
		actionViewRulers.setPreferences( prefs, PrefsUtil.KEY_VIEWRULERS );
		subMenu.add( cbmi );
		mainMenu.add( subMenu );
		protoType.add( mainMenu );

		mainMenu	= new JMenu( app.getResourceString( "menuWindow" ));
		mainMenu.add( new JMenuItem( actionShowSurface ));
		mainMenu.add( new JMenuItem( actionShowTimeline ));
		mainMenu.addSeparator();
		mainMenu.add( new JMenuItem( actionShowTransport ));
		mainMenu.add( new JMenuItem( actionShowObserver ));
		mainMenu.add( new JMenuItem( actionShowMeter ));
		mainMenu.add( new JMenuItem( actionShowRealtime ));
		protoType.add( mainMenu );

		mainMenu	= new JMenu( app.getResourceString( "menuExtras" ));
		mainMenu.add( new JMenuItem( actionJathaDiddler ));
		mainMenu.add( new JMenuItem( actionHRIRPrepare ));
		protoType.add( mainMenu );

		mainMenu	= new JMenu( "Debug" );
		mainMenu.add( new JMenuItem( actionDebugDumpUndo ));
		mainMenu.add( new JMenuItem( actionDebugDumpTracks ));
		mainMenu.add( new JMenuItem( actionDebugViewTrack ));
		mainMenu.add( new JMenuItem( actionDebugDumpPrefs ));
		mainMenu.add( new JMenuItem( actionDebugDumpRealtime ));
		mainMenu.add( new JMenuItem( actionDebugDumpListeners ));
		protoType.add( mainMenu );

		mainMenu	= new JMenu( app.getResourceString( "menuHelp" ));
		mainMenu.add( new JMenuItem( actionHelpManual ));
		mainMenu.add( new JMenuItem( actionHelpShortcuts ));
		mainMenu.addSeparator();
		mainMenu.add( new JMenuItem( actionHelpWebsite ));
		mi			= root.getAboutJMenuItem();
		mi.setAction( actionAbout );
		if( !AboutJMenuItem.isAutomaticallyPresent() ) {
			mainMenu.addSeparator();
			mainMenu.add( mi );
		}
		protoType.add( mainMenu );
	}

	private JMenuBar createMenuBarCopy()
	{
		final JMenuBar copy	= new JMenuBar();
		
		for( int i = 0; i < protoType.getMenuCount(); i++ ) {
			copy.add( createMenuCopy( protoType.getMenu( i )));
		}
		
		return copy;
	}
		
	private JMenu createMenuCopy( JMenu pMenu )
	{
		final JMenu	cMenu   = new JMenu( pMenu.getText() );
		JMenuItem   pMenuItem, cMenuItem;
		Action		action;
		Vector		v;
		
		action = pMenu.getAction();
		if( action != null ) {
			cMenu.setAction( action );
		}
		cMenu.setVisible( pMenu.isVisible() );
		
		for( int i = 0; i < pMenu.getItemCount(); i++ ) {
			pMenuItem   = pMenu.getItem( i );
			if( pMenuItem != null ) {
				action		= pMenuItem.getAction();
				if( pMenuItem instanceof JMenu ) {  // recursive into submenus
					cMenuItem = createMenuCopy( (JMenu) pMenuItem );
				} else if( pMenuItem instanceof JCheckBoxMenuItem ) {
					cMenuItem = new JCheckBoxMenuItem( pMenuItem.getText(), ((JCheckBoxMenuItem) pMenuItem).getState() );
					v		  = (Vector) syncedItems.get( action );
					v.add( cMenuItem );
				} else {
					cMenuItem = new JMenuItem( pMenuItem.getText() );
				}
				if( action != null ) {
					cMenuItem.setAction( action );
				}
				cMenu.add( cMenuItem );
			} else {  // components used other that JMenuItems are separators
				cMenu.add( new JSeparator() );
			}
		}
		
		return cMenu;
	}
	
	// returns the current active window
	private JFrame fuckINeedTheWindow( ActionEvent e )
	{
		JFrame host;

		for( int i = 0; i < collMenuHosts.size(); i++ ) {
			host = (JFrame) collMenuHosts.get( i );
			if( host.isActive() ) return host;
		}
		return null;
	}
	
	/**
	 *  Checks if there are unsaved changes to
	 *  the session. If so, displays a confirmation
	 *  dialog. Invokes Save/Save As depending
	 *  on user selection.
	 *  
	 *  @param  parentComponent the component associated with
	 *							the proposed action, e.g. root
	 *  @param  actionName		name of the action that
	 *							threatens the session
	 *  @return					- true if the action should proceed,
	 *							- false if the action should be aborted
	 */
	public boolean confirmUnsaved( Component parentComponent, String actionName )
	{
		if( !doc.isDirty() ) return true;
		
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
		final String[]					options	= { app.getResourceString( "buttonSave" ),
													app.getResourceString( "buttonCancel" ),
													app.getResourceString( "buttonDontSave" ) };
		int								choice;
		ProcessingThread				proc;
		File							f;
		
		choice = JOptionPane.showOptionDialog( parentComponent, app.getResourceString( "optionDlgUnsaved" ), actionName,
											   JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
											   options, options[0] );
		switch( choice ) {
		case JOptionPane.CLOSED_OPTION:
		case 1:
			return false;
			
		case 2:
			return true;
			
		case 0:
			f = (File) doc.getMap().getValue( Session.MAP_KEY_PATH );
			if( f == null ) {
				f = actionSaveAs.queryFile();
			}
			if( f != null ) {
				proc = actionSave.perform( f );
				if( proc != null ) {
					return proc.sync();
				}
			}
			return false;
			
		default:
			assert false : choice;
			return false;
		}
	}
	
	// adds a file to the top of
	// the open recent menu of all menubars
	// and the prototype. calls
	// openRecentPaths.addPathToHead() and
	// thus updates the preferences settings
	// iteratively calls addRecent( JMenuBar, File, boolean )
	private void addRecent( File path )
	{
		JMenuBar	mb;
		boolean		removeTail;
		
		if( openRecentPaths.contains( path )) return;
		removeTail = openRecentPaths.addPathToHead( path );

		actionOpenRecent.setPath( path );
	
		for( int i = 0; i < collMenuHosts.size(); i++ ) {
			mb = ((JFrame) collMenuHosts.get( i )).getJMenuBar();
			if( mb != null ) addRecent( mb, path, removeTail );
		}
		addRecent( protoType, path, removeTail );
	}
	
	private void addRecent( JMenuBar mb, File path, boolean removeTail )
	{
		JMenu m;

		m = (JMenu) findMenuItem( mb, actionOpenRecent );
		if( m != null ) {
			m.insert( new JMenuItem( new actionOpenRecentClass( path )), 0 );
			if( removeTail ) m.remove( openRecentPaths.getPathCount() );
		}
	}

	// find the menuitem whose action is
	// the action passed to the method.
	// traverse the whole hierarchy of the given menubar.
	// iteratively calls findMenuItem( JMenu, Action )
	private JMenuItem findMenuItem( JMenuBar mb, Action action )
	{
		int			i;
		JMenuItem   mi  = null;
	
		for( i = 0; mi == null && i < mb.getMenuCount(); i++ ) {
			mi = findMenuItem( mb.getMenu( i ), action );
		}
		return mi;
	}
	
	private JMenuItem findMenuItem( JMenu m, Action action )
	{
		int			i;
		JMenuItem   mi;
	
		for( i = 0; i < m.getItemCount(); i++ ) {
			mi = m.getItem( i );
			if( mi != null ) {
				if( mi.getAction() == action ) return mi;
				if( mi instanceof JMenu ) {
					mi = findMenuItem( (JMenu) mi, action );
					if( mi != null ) return mi;
				}
			}
		}
		return null;
	}

// ---------------- Action objects for file (session) operations ---------------- 

	// action for the Insert-New-Receivers menu item
	private class actionNewReceiversClass
	extends MenuAction
	{
		private Number		num		= new Integer( 1 );
		private StringItem	type	= null;

		private actionNewReceiversClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		// ask the user for a number of receivers
		// to insert. if dialog is confirmed
		// create those receivers and add them to
		// the session.
		// 
		// @synchronization waitExclusive on DOOR_RCV + DOOR_GRP
		// @todo	this was copied from Surface.createReceiver(); there should
		//			be a central function that creates Receivers and not multiple
		//			versions!
		// @todo	to improve performance, doc.receiverCollectio.add should
		//			be called only once with the whole new collection; implement
		//			a new related method createUniqueName( Collection theseNot );
		// @todo	should select new receives
		public void actionPerformed( ActionEvent e )
		{
			int				numi, result;
			Receiver		rcv;
			java.util.List	coll, coll2;
			Point2D			anchor;
			double			d1;
			Class			c;
			UndoableEdit	edit;
			SessionGroup	group;

			final java.util.List	collTypes	= Main.getReceiverTypes();
			final JPanel			msgPane		= new JPanel( new SpringLayout() );
			final NumberField		ggNum		= new NumberField( 0, NumberSpace.createIntSpace( 1, 0x10000 ), null );
			final JComboBox			ggType		= new JComboBox();

			for( int i = 0; i < collTypes.size(); i++ ) {
				ggType.addItem( collTypes.get( i ));
			}
			
			ggNum.setNumber( num );
			if( type != null ) ggType.setSelectedItem( type );

			msgPane.add( ggNum );
			msgPane.add( ggType );
			GUIUtil.makeCompactSpringGrid( msgPane, 1, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
			HelpGlassPane.setHelp( msgPane, getValue( NAME ).toString() );
		
			result = JOptionPane.showOptionDialog( null, msgPane,
				AbstractApplication.getApplication().getResourceString( "inputDlgNewReceivers" ),
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
				null, queryOptions, queryOptions[ 1 ]);
				
			if( result != 1 ) return;

			num		= ggNum.getNumber();
			type	= (StringItem) ggType.getSelectedItem();
			numi	= num.intValue();
			
			try {
				doc.bird.waitExclusive( Session.DOOR_RCV | Session.DOOR_GRP );
				// we get a list of known receivers from the main class
				// and create a new instance of the first receiver class
				// in the list; in the future when there are more types
				// apart from SigmaReceiver, we could display a selection
				// dialog or the like...
				c		= Class.forName( type.getKey() );
				coll	= new ArrayList();
				coll2	= doc.receivers.getAll();
				for( int i = 0; i < numi; i++ ) {
					d1  = ((double) i / (double) numi - 0.25) * Math.PI * 2;
					anchor = new Point2D.Double( 0.25 * (2.0 + Math.cos( d1 )), 0.25 * (2.0 + Math.sin( d1 )));
					rcv = (Receiver) c.newInstance();
					rcv.setAnchor( anchor );
//					rcv.setSize( new Dimension2DDouble( 0.5, 0.5 ));
					rcv.setName( SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
						new Object[] { new Integer( 1 ), Session.RCV_NAME_PREFIX, Session.RCV_NAME_SUFFIX },
						coll2 ));
					doc.receivers.getMap().copyContexts( this, MapManager.Context.FLAG_DYNAMIC,
														 MapManager.Context.NONE_EXCLUSIVE, rcv.getMap() );
					coll.add( rcv );
					coll2.add( rcv );
				}
				if( doc.selectedGroups.size() == 0 ) {
					edit = new EditAddSessionObjects( this, doc, doc.receivers, coll, Session.DOOR_RCV );
				} else {
					edit	= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_RCV | Session.DOOR_GRP );
					edit.addEdit( new EditAddSessionObjects( this, doc, doc.receivers, coll, Session.DOOR_RCV ));
					for( int i = 0; i < doc.selectedGroups.size(); i++ ) {
						group	= (SessionGroup) doc.selectedGroups.get( i );
						edit.addEdit( new EditAddSessionObjects( this, doc, group.receivers, coll, Session.DOOR_RCV ));
					}
					((CompoundEdit) edit).end();
				}
				doc.getUndoManager().addEdit( edit );
			}
			catch( InstantiationException e1 ) {
				System.err.println( e1.getLocalizedMessage() );
			}
			catch( IllegalAccessException e2 ) {
				System.err.println( e2.getLocalizedMessage() );
			}
			catch( LinkageError e3 ) {
				System.err.println( e3.getLocalizedMessage() );
			}
			catch( ClassNotFoundException e4 ) {
				System.err.println( e4.getLocalizedMessage() );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_RCV | Session.DOOR_GRP );
			}
		}
	}
	
	// action for the Insert-New-Group menu item
	private class actionNewGroupClass
	extends MenuAction
	{
		private actionNewGroupClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		// ask the user for a number of group name.
		// if dialog is confirmed creates a new group
		// with the currently selected transmitters + receivers
		// 
		// @synchronization waitExclusive on DOOR_RCV + DOOR_TRNS, DOOR_GRP
		public void actionPerformed( ActionEvent e )
		{
			String			name;
			boolean			b1;
			int				result;
			java.util.List	collSO;
			SessionGroup	group;
			CompoundEdit	ce;

			try {
				doc.bird.waitShared( Session.DOOR_TRNS | Session.DOOR_RCV );
				b1 = doc.selectedReceivers.isEmpty() && doc.selectedTransmitters.isEmpty();
			} finally {
				doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_RCV );
			}
			if( b1 ) {	// ask if we should create an empty group
				result = JOptionPane.showConfirmDialog( null,
					AbstractApplication.getApplication().getResourceString( "warnNoObjectsSelected" ),
					getValue( Action.NAME ).toString(), JOptionPane.YES_NO_OPTION );
					
				if( result != JOptionPane.YES_OPTION ) return;
			}

			try {
				doc.bird.waitShared( Session.DOOR_GRP );
				name = SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
					new Object[] { new Integer( 1 ), Session.GRP_NAME_PREFIX, Session.GRP_NAME_SUFFIX },
					doc.groups.getAll() );
			} finally {
				doc.bird.releaseShared( Session.DOOR_GRP );
			}
			name = JOptionPane.showInputDialog( null, AbstractApplication.getApplication().getResourceString(
				"inputDlgNewGroup" ), name );
				
			if( name == null ) return;
			
			ce = new BasicSyncCompoundEdit( doc.bird, Session.DOOR_GRP | Session.DOOR_TRNS | Session.DOOR_RCV );
			
			try {
				doc.bird.waitExclusive( Session.DOOR_GRP );
				group	= (SessionGroup) doc.groups.findByName( name );
				b1		= group == null;
				if( !b1 ) {
					result = JOptionPane.showConfirmDialog( null,
						AbstractApplication.getApplication().getResourceString(
						"optionDlgOverwriteGroup" ), getValue( Action.NAME ).toString(),
						JOptionPane.YES_NO_OPTION );
						
					if( result != JOptionPane.YES_OPTION ) return;
				} else {
					group = new SessionGroup();
					group.setName( name );
					doc.groups.getMap().copyContexts( this, MapManager.Context.FLAG_DYNAMIC,
													  MapManager.Context.NONE_EXCLUSIVE, group.getMap() );
				}
				if( !doc.bird.attemptShared( Session.DOOR_TRNS | Session.DOOR_RCV, 250 )) return;
				try {
					collSO		= doc.selectedReceivers.getAll();
					ce.addEdit( new EditAddSessionObjects( this, doc, group.receivers, collSO,
														   Session.DOOR_RCV ));
					collSO		= doc.selectedTransmitters.getAll();
					ce.addEdit( new EditAddSessionObjects( this, doc, group.transmitters, collSO,
														   Session.DOOR_TRNS ));

					if( b1 ) {
						collSO = new ArrayList( 1 );
						collSO.add( group );
						ce.addEdit( new EditAddSessionObjects( this, doc, doc.groups,
																	  collSO, Session.DOOR_GRP ));
					}
					ce.end();
					doc.getUndoManager().addEdit( ce );
					
				} finally {
					doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_RCV );
				}
			} finally {
				doc.bird.releaseExclusive( Session.DOOR_GRP );
			}
		}
	}
	
	// action for the Clear-Session menu item
	private class actionClearSessionClass
	extends MenuAction
	{
		private String text;
	
		private actionClearSessionClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
			
			this.text = text;
		}
		
		/*
		 *  Clears the document. If the session was
		 *  modified, the user is asked to confirm the clear
		 *  action. If the transport is running, it will be
		 *  stopped. Undo history is purged.
		 *
		 *  @synchronization	waitExclusive on DOOR_ALL
		 */
		public void actionPerformed( ActionEvent e )
		{
			if( !confirmUnsaved( null, text )) return;

			final MainFrame mf = (MainFrame) root.getComponent( Main.COMP_MAIN );

			root.transport.stopAndWait();
			try {
				doc.bird.waitExclusive( Session.DOOR_ALL );
				doc.getUndoManager().discardAllEdits();
				doc.clear();
				doc.setDirty( false );
				mf.updateTitle();
				mf.clearLog();
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_ALL );
			}
		}
	}

	// action for the Open-Session menu item
	private class actionOpenClass
	extends MenuAction
	implements RunnableProcessing
	{
		private String text;
	
		private actionOpenClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
			
			this.text = text;
		}
		
		/*
		 *  Open a document. If the current document
		 *  contains unsaved changes, the user is prompted
		 *  to confirm. A file chooser will pop up for
		 *  the user to select the session to open.
		 */
		public void actionPerformed( ActionEvent e )
		{
			if( !confirmUnsaved( null, text )) return;
			File f = queryFile();
			if( f != null ) perform( f );
		}

		private File queryFile()
		{
			FileDialog  fDlg;
			String		strFile, strDir;
			Frame		frame = (Frame) root.getComponent( Main.COMP_MAIN );

			fDlg	= new FileDialog( frame, AbstractApplication.getApplication().getResourceString(
				"fileDlgOpen" ), FileDialog.LOAD );
			fDlg.setFilenameFilter( doc );
			// fDlg.setDirectory();
			// fDlg.setFile();
			fDlg.show();
			strDir	= fDlg.getDirectory();
			strFile	= fDlg.getFile();
			
			if( strFile == null ) return null;   // means the dialog was cancelled

			return( new File( strDir, strFile ));
		}
		
		/**
		 *  Loads a new session file.
		 *  If transport is running, is will be stopped.
		 *  The console window is cleared an a <code>ProcessingThread</code>
		 *  started which loads the new session.
		 *
		 *  @param  path	the file of the session to be loaded
		 *  
		 *  @synchronization	this method must be called in event thread
		 */
		protected ProcessingThread perform( File path )
		{
			root.transport.stopAndWait();
			((MainFrame) root.getComponent( Main.COMP_MAIN )).clearLog();
			Map options = new HashMap();
			options.put( "file", path );
			return( new ProcessingThread( this, root, root, doc, text, options, Session.DOOR_ALL ));
		}

		public boolean run( ProcessingThread context, Object argument )
		{
			org.w3c.dom.Document	domDoc;
			DocumentBuilderFactory  builderFactory;
			DocumentBuilder			builder;
			boolean					success = false;
			Map						options	= (Map) argument;
			File					f		= (File) options.get( "file" );

			builderFactory  = DocumentBuilderFactory.newInstance();
			builderFactory.setValidating( true );
//			builderFactory.setIgnoringComments( true );
//			builderFactory.setIgnoringElementContentWhitespace( true );
//			builderFactory.setNamespaceAware(true);
			doc.getUndoManager().discardAllEdits();
			
			context.setProgression( -1f );

			try {
				builder	=   builderFactory.newDocumentBuilder();
				builder.setEntityResolver( doc );
				domDoc  =   builder.parse( f );
				context.setProgression( -1f );
				options.put( XMLRepresentation.KEY_BASEPATH, f.getParentFile() );
				doc.fromXML( domDoc, domDoc.getDocumentElement(), options );
				doc.getMap().putValue( this, Session.MAP_KEY_PATH, f );
				doc.setName( f.getName() );

				context.setProgression( 1.0f );
				success = true;
			}
			catch( ParserConfigurationException e1 ) {
				context.setException( e1 );
			}
			catch( SAXParseException e2 ) {
				context.setException( e2 );
			}
			catch( SAXException e3 ) {
				context.setException( e3 );
			}
			catch( IOException e4 ) {
				context.setException( e4 );
			}
		
			if( !success ) {
				doc.clear();
			}
			return success;
		} // run()
		
		/**
		 *  When the sesion was successfully
		 *  loaded, its name will be put in the
		 *  Open-Recent menu. All frames' bounds will be
		 *  restored depending on the users preferences.
		 *  <code>setModified</code> will be called on
		 *  the <code>Main</code> class and the
		 *  main frame's title is updated
		 */
		public void finished( ProcessingThread context, Object argument, boolean success )
		{
			final Map		options = (Map) argument;
			Object			warn;
			final MainFrame mf		= (MainFrame) root.getComponent( Main.COMP_MAIN );
		
			if( success ) {
				addRecent( (File) options.get( "file" ));
				if( AbstractApplication.getApplication().getUserPrefs().getBoolean(
					PrefsUtil.KEY_RECALLFRAMES, false )) {

					BasicFrame.restoreAllFromPrefs();
				}
				warn = options.get( XMLRepresentation.KEY_WARNING );
				if( warn != null ) {
					JOptionPane.showMessageDialog( mf, warn, getValue( Action.NAME ).toString(),
												   JOptionPane.WARNING_MESSAGE );
				}
			}
			doc.setDirty( false );
			mf.updateTitle();
		}
	}
	
	// action for the Open-Recent menu
	private class actionOpenRecentClass
	extends MenuAction
	{
		private File path;

		// new action with path set to null
		private actionOpenRecentClass( String text )
		{
			super( text );
			setPath( null );
		}

		// new action with given path
		private actionOpenRecentClass( File path )
		{
			super( IOUtil.abbreviate( path.getParent(), 40 ));
			setPath( path );
		}
		
		// set the path of the action. this
		// is the file that will be loaded
		// if the action is performed
		private void setPath( File path )
		{
			this.path = path;
			setEnabled( path != null );
		}
		
		/**
		 *  If a path was set for the
		 *  action and the user confirms
		 *  an intermitting confirm-unsaved-changes
		 *  dialog, the new session will be loaded
		 */
		public void actionPerformed( ActionEvent e )
		{
			if( path == null ) return;
			if( !confirmUnsaved( null,
				AbstractApplication.getApplication().getResourceString( "menuOpenRecent" ))) return;
			actionOpen.perform( path );
		}
	} // class actionOpenRecentClass

	// action for clearing the Open-Recent menu
	private class actionClearRecentClass
	extends MenuAction
	{
		private actionClearRecentClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			JMenuBar	mb;
			int			i;

			openRecentPaths.clear();
			actionOpenRecent.setPath( null );
		
			for( i = 0; i < collMenuHosts.size(); i++ ) {
				mb = ((JFrame) collMenuHosts.get( i )).getJMenuBar();
				if( mb != null ) clearRecent( mb );
			}
			clearRecent( protoType );
		}
		
		private void clearRecent( JMenuBar mb )
		{
			JMenu		m;
			JMenuItem   mi;
			int			i;
			Action		a;

			m = (JMenu) findMenuItem( mb, actionOpenRecent );
			if( m != null ) {
				for( i = m.getItemCount() - 1; i >= 0; i-- ) {
					mi  = m.getItem( i );
					if( mi != null ) {
						a   = mi.getAction();
						if( a != null && a instanceof actionOpenRecentClass ) {
							m.remove( mi );
						}
					}
				}
			}
		}
	} // class actionClearRecentClass
	
	// action for the Save-Session menu item
	private class actionSaveClass
	extends MenuAction
	implements RunnableProcessing
	{
		private String text;

		private actionSaveClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
			
			this.text = text;
		}

		/**
		 *  Saves a document. If the file
		 *  wasn't saved before, a file chooser
		 *  is shown before.
		 */
		public void actionPerformed( ActionEvent e )
		{
			File f	= (File) doc.getMap().getValue( Session.MAP_KEY_PATH );
		
			if( f == null ) {
				f = actionSaveAs.queryFile();
			}
			if( f != null ) perform( f );
		}
		
		/**
		 *  Save the session to the given file.
		 *  Transport is stopped before, if it was running.
		 *  On success, undo history is purged and
		 *  <code>setModified</code> and <code>updateTitle</code>
		 *  are called, and the file is added to
		 *  the Open-Recent menu.
		 *
		 *  @param  docFile		the file denoting
		 *						the session's name. note that
		 *						<code>Session</code> will create
		 *						a folder of this name and store the actual
		 *						session data in a file of the same name
		 *						plus .XML suffix inside this folder
		 *  @synchronization	this method is to be called in the event thread
		 */
		protected ProcessingThread perform( File docFile )
		{
			root.transport.stopAndWait();
			return new ProcessingThread( this, root, root, doc, text, docFile, Session.DOOR_ALL );
		}

		public boolean run( ProcessingThread context, Object argument )
		{
			org.w3c.dom.Document			domDoc;
			DocumentBuilderFactory			builderFactory;
			DocumentBuilder					builder;
			TransformerFactory				transformerFactory;
			Transformer						transformer;
			Element							childNode;
			final File						f		= (File) argument;
			final File						dir		= f.getParentFile();
			File							tempDir	= null;
			boolean							success = false;
			final Map						options	= new HashMap();
			final de.sciss.app.Application	app	= AbstractApplication.getApplication();
		
			builderFactory		= DocumentBuilderFactory.newInstance();
			builderFactory.setValidating( true );
			transformerFactory  = TransformerFactory.newInstance();

			context.setProgression( -1f );

			try {
				builder		= builderFactory.newDocumentBuilder();
				transformer = transformerFactory.newTransformer();
				builder.setEntityResolver( doc );
				domDoc		= builder.newDocument();
				childNode   = domDoc.createElement( Session.XML_ROOT );
				domDoc.appendChild( childNode );
				options.put( XMLRepresentation.KEY_BASEPATH, dir );
				doc.getMap().putValue( this, Session.MAP_KEY_PATH, f );
				doc.setName( f.getName() );
				
				if( dir.exists() ) {
//System.err.println( "dir exists "+dir.getAbsolutePath() );
					tempDir = new File( dir.getAbsolutePath() + ".tmp" );
					if( tempDir.exists() ) {
//System.err.println( "temp dir exists "+tempDir.getAbsolutePath() );
						IOUtil.deleteAll( tempDir );
					}
					if( !dir.renameTo( tempDir )) {
						throw new IOException( tempDir.getAbsolutePath() + " : " +
											   IOUtil.getResourceString( "errMakeDir" ));
					}
				}
				if( !dir.mkdirs() ) {
//System.err.println( "mkdir failed : "+dir.getAbsolutePath() );
					throw new IOException( dir.getAbsolutePath() + " : " +
										   IOUtil.getResourceString( "errMakeDir" ));
				}
				
				doc.toXML( domDoc, childNode, options );
				context.setProgression( -1f );
				transformer.setOutputProperty( OutputKeys.DOCTYPE_SYSTEM, Session.ICHNOGRAM_DTD );
				transformer.transform( new DOMSource( domDoc ), new StreamResult( f ));
				MRJAdapter.setFileCreatorAndType( f, app.getMacOSCreator(), Session.MACOS_FILE_TYPE );
				
				doc.getUndoManager().discardAllEdits();

				if( tempDir != null && tempDir.exists() ) {
					IOUtil.deleteAll( tempDir );
				}

				context.setProgression( 1.0f );
				success = true;
			}
			catch( ParserConfigurationException e1 ) {
				context.setException( e1 );
			}
			catch( TransformerConfigurationException e2 ) {
				context.setException( e2 );
			}
			catch( TransformerException e3 ) {
				context.setException( e3 );
			}
			catch( IOException e4 ) {
				context.setException( e4 );
			}
			catch( DOMException e5 ) {
				context.setException( e5 );
			}

			return success;
		} // run

		public void finished( ProcessingThread context, Object argument, boolean success )
		{
			final MainFrame mf = (MainFrame) root.getComponent( Main.COMP_MAIN );

			if( success ) {
				addRecent( (File) argument );
				doc.setDirty( false );
			} else {
				File tempDir = new File( ((File) argument).getParentFile().getAbsolutePath() + ".tmp" );
				if( tempDir.exists() ) {
					JOptionPane.showMessageDialog( mf,
						AbstractApplication.getApplication().getResourceString( "warnOldSessionDir" )+ " :\n"+
						tempDir.getAbsolutePath(), getValue( Action.NAME ).toString(),
						JOptionPane.WARNING_MESSAGE );
				}
			}
			mf.updateTitle();
		}
	}
	
	// action for the Save-Session-As menu item
	private class actionSaveAsClass
	extends MenuAction
	{
		private actionSaveAsClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/*
		 *  Query a file name from the user and save the document
		 */
		public void actionPerformed( ActionEvent e )
		{
			File f = queryFile();
			if( f != null ) actionSave.perform( f );
		}
		
		/**
		 *  Open a file chooser so the user
		 *  can select a new output file for the session.
		 *
		 *  @return the chosen <coded>File</code> or <code>null</code>
		 *			if the dialog was cancelled.
		 */
		protected File queryFile()
		{
			FileDialog  fDlg;
			String		strFile, strDir;
			File		f;
			int			i;
			Frame		frame = (Frame) root.getComponent( Main.COMP_MAIN );

			fDlg	= new FileDialog( frame,
				AbstractApplication.getApplication().getResourceString( "fileDlgSave" ),
				FileDialog.SAVE );
			f		= (File) doc.getMap().getValue( Session.MAP_KEY_PATH );
			if( f != null ) f = f.getParentFile();	// use session folder instead of XML file
			if( f != null ) {
				strDir  = f.getParent();
				strFile = f.getName();
				if( strDir != null ) fDlg.setDirectory( strDir );
				fDlg.setFile( strFile );
			}
			fDlg.show();
			strDir	= fDlg.getDirectory();
			strFile	= fDlg.getFile();
			
			if( strFile == null ) return null;   // means the dialog was cancelled

			i = strFile.lastIndexOf( "." );
			strFile = i > 0 ? strFile.substring( 0, i ) : strFile;
			f = new File( new File( strDir, strFile ), strFile + Session.FILE_EXTENSION );
			return f;
		}
	}

	// action for Bounce-to-Disk menu item
	private class actionBounceClass
	extends MenuAction
	{
		private actionBounceClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}
		
		/**
		 *  Opens the bounce-to-disk dialog
		 */
		public void actionPerformed( ActionEvent e )
		{
			JFrame bounceFrame = (JFrame) root.getComponent( Main.COMP_BOUNCE );
		
			if( bounceFrame == null ) {
				bounceFrame = new BounceDialog( root, doc );
				root.addComponent( Main.COMP_BOUNCE, bounceFrame );
			}
			bounceFrame.setVisible( true );
			bounceFrame.show();
		}
	}

	// action for Application-Quit menu item
	private class actionQuitClass
	extends MenuAction
	{
		private actionQuitClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			root.quit();
		}
	}
	
// ---------------- Action objects for edit operations ---------------- 
	
	// action for Edit-Cut menu item
	private class actionCutClass
	extends MenuAction
	{
		private actionCutClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Tries to find the current active window
		 *  and - if this window implements the
		 *  <code>EditMenuListener</code> interface - calls
		 *  <code>editCut</code> on that window.
		 *
		 *  @see	EditMenuListener#editCut( ActionEvent )
		 */
		public void actionPerformed( ActionEvent e )
		{
			JFrame frame = fuckINeedTheWindow( e );
			if( frame == null || !(frame instanceof EditMenuListener) ) return;
			
			((EditMenuListener) frame).editCut( e );
		}
	}
	
	// action for Edit-Copy menu item
	private class actionCopyClass
	extends MenuAction
	{
		private actionCopyClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Tries to find the current active window
		 *  and - if this window implements the
		 *  <code>EditMenuListener</code> interface - calls
		 *  <code>editCopy</code> on that window.
		 *
		 *  @see	EditMenuListener#editCopy( ActionEvent )
		 */
		public void actionPerformed( ActionEvent e )
		{
			JFrame frame = fuckINeedTheWindow( e );
			if( frame == null || !(frame instanceof EditMenuListener) ) return;
			
			((EditMenuListener) frame).editCopy( e );
		}
	}
	
	// action for Edit-Paste menu item
	private class actionPasteClass
	extends MenuAction
	{
		private actionPasteClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Tries to find the current active window
		 *  and - if this window implements the
		 *  <code>EditMenuListener</code> interface - calls
		 *  <code>editPaste</code> on that window.
		 *
		 *  @see	EditMenuListener#editPaste( ActionEvent )
		 */
		public void actionPerformed( ActionEvent e )
		{
			JFrame frame = fuckINeedTheWindow( e );
			if( frame == null || !(frame instanceof EditMenuListener) ) return;
			
			((EditMenuListener) frame).editPaste( e );
		}
	}
	
	// action for Edit-Clear/Delete menu item
	private class actionClearClass
	extends MenuAction
	{
		private actionClearClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Tries to find the current active window
		 *  and - if this window implements the
		 *  <code>EditMenuListener</code> interface - calls
		 *  <code>editClear</code> on that window.
		 *
		 *  @see	EditMenuListener#editClear( ActionEvent )
		 */
		public void actionPerformed( ActionEvent e )
		{
			JFrame frame = fuckINeedTheWindow( e );
			if( frame == null || !(frame instanceof EditMenuListener) ) return;
			
			((EditMenuListener) frame).editClear( e );
		}
	}
	
	// action for Edit-Select-All menu item
	private class actionSelectAllClass
	extends MenuAction
	{
		private actionSelectAllClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Tries to find the current active window
		 *  and - if this window implements the
		 *  <code>EditMenuListener</code> interface - calls
		 *  <code>editSelectAll</code> on that window.
		 *
		 *  @see	EditMenuListener#editSelectAll( ActionEvent )
		 */
		public void actionPerformed( ActionEvent e )
		{
			JFrame frame = fuckINeedTheWindow( e );
			if( frame == null || !(frame instanceof EditMenuListener) ) return;
			
			((EditMenuListener) frame).editSelectAll( e );
		}
	}
	
	/**
	 *  Action to be attached to
	 *  the Preference item of the Edit menu.
	 *  Will bring up the Preferences frame
	 *  when the action is performed.
	 */
	public class actionPreferencesClass
	extends MenuAction
	{
		private actionPreferencesClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		/**
		 *  Opens the preferences frame
		 */
		public void perform()
		{
			JFrame prefsFrame = (JFrame) root.getComponent( Main.COMP_PREFS );
		
			if( prefsFrame == null ) {
				prefsFrame = new PrefsFrame( root, doc );
				root.addComponent( Main.COMP_PREFS, prefsFrame );
			}
			prefsFrame.setVisible( true );
			prefsFrame.show();
		}
	}

// ---------------- Action objects for surface operations ---------------- 

	// for each SyncedMenuAction a
	// hash map entry exists in the MenuFactory's
	// syncedItems object. The value of
	// that entry is a vector with a copy of the same
	// menu item for each frame's menu. when the
	// action of the SyncedMenuAction is performed,
	// the source's isSelected() is queried and
	// all other frames' synchronized menu items
	// (i.e. JCheckBoxMenuItems) are set to the
	// same state. Used e.g. for the Snap-to-Objects item
	private class SyncedMenuAction
	extends MenuAction
	{
		private SyncedMenuAction( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}
	
		public void actionPerformed( ActionEvent e )
		{
			boolean state   = ((AbstractButton) e.getSource()).isSelected();

			setSelected( state );
		}
		
		protected void setSelected( boolean state )
		{
			Vector			v   = (Vector) syncedItems.get( this );
			AbstractButton  b;
			int				i;

			for( i = 0; i < v.size(); i++ ) {
				b = (AbstractButton) v.get( i );
				b.setSelected( state );
			}
		}
	}
	
	// adds PreferenceEntrySync functionality to the superclass
	// note that unlike PrefCheckBox and the like, it's only
	// valid to listen to the prefs changes, not the action events
	private class SyncedPrefsMenuAction
	extends SyncedMenuAction
	implements PreferenceEntrySync, PreferenceChangeListener, LaterInvocationManager.Listener
	{
		private Preferences prefs				= null;
		private String key						= null;
		private final LaterInvocationManager lim= new LaterInvocationManager( this );

		private SyncedPrefsMenuAction( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Switches button state
		 *  and updates preferences. 
		 */
		public void actionPerformed( ActionEvent e )
		{
			boolean state   = ((AbstractButton) e.getSource()).isSelected();

			setSelected( state );
		
			if( prefs != null ) {
				prefs.putBoolean( key, state );
			}
		}

		public void setPreferences( Preferences prefs, String key )
		{
			if( this.prefs != null ) {
				this.prefs.removePreferenceChangeListener( this );
			}
			this.prefs  = prefs;
			this.key	= key;
			if( prefs != null ) {
				prefs.addPreferenceChangeListener( this );
				laterInvocation( new PreferenceChangeEvent( prefs, key, prefs.get( key, null )));
			}
		}

		public Preferences getPreferenceNode() { return prefs; }
		public String getPreferenceKey() { return key; }

		// o instanceof PreferenceChangeEvent
		public void laterInvocation( Object o )
		{
			String prefsValue   = ((PreferenceChangeEvent) o).getNewValue();
			if( prefsValue == null ) return;
			boolean prefsVal	= Boolean.valueOf( prefsValue ).booleanValue();

			setSelected( prefsVal );
		}

		public void preferenceChange( PreferenceChangeEvent e )
		{
			if( e.getKey().equals( key )) {
				if( EventManager.DEBUG_EVENTS ) System.err.println( "@menu preferenceChange : "+key+" --> "+e.getNewValue() );
				lim.queue( e );
			}
		}
	}
	
// ---------------- Action objects for timeline operations ---------------- 

	// action for Insert-New-Transmitters menu item
	private class actionNewTransmittersClass
	extends MenuAction
	implements RunnableProcessing
	{
		private int		defaultValue = 1;
		private String  text;
	
		private actionNewTransmittersClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
			
			this.text = text;
		}

		/**
		 *  Queries the number of transmitters to
		 *  create from the user and creates a
		 *  new ProcessingThread which will perform
		 *  the insertion (and creation of corresponding
		 *  trajectory files).
		 */
		public void actionPerformed( ActionEvent e )
		{
			String					result;
			int						num = 0;
			int						i;
			java.util.List			collTypes	= Main.getTransmitterTypes();
			java.util.List			coll;
		
			result  = JOptionPane.showInputDialog( null,
				AbstractApplication.getApplication().getResourceString( "inputDlgInsNewTransmitters" ),
				String.valueOf( defaultValue ));
				
			if( result == null ) return;
			try {
				num = Integer.parseInt( result );
			}
			catch( NumberFormatException e1 ) {
				System.err.println( e1.getLocalizedMessage() );
				return;
			}
			
			if( num < 1 ) return;
			defaultValue = num;

			coll = new ArrayList( num );
			for( i = 0; i < num; i++ ) {
				coll.add( collTypes.get( 0 ));
			}

			new ProcessingThread( this, root, root, doc, text, coll, Session.DOOR_TIMETRNSMTE | Session.DOOR_GRP );
		}

		/**
		 *  @synchronization	waitExclusive on DOOR_TIMETRNSMTE + DOOR_GRP
		 */
		public boolean run( ProcessingThread context, Object argument )
		{
			int						i, j;
			Transmitter				trns;
			MultirateTrackEditor	mte;
			float[][]				buf			= new float[ 2 ][ 4096 ];
			Class					c;
			long					progress	= 0;
			Map						map;
			String					s;
			double					d1;
			SyncCompoundEdit		edit		= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_TRNS | Session.DOOR_GRP );
			java.util.List			collMap		= (java.util.List) argument;
			int						num			= collMap.size();
			java.util.List			coll		= new ArrayList( num );
			java.util.List			coll2		= doc.transmitters.getAll();
			boolean					success		= false;
			Span					span		= new Span( 0, doc.timeline.getLength() );
			TrackSpan				ts;
			SessionGroup			group;
			float					f1, f2;
			long					frames, framesWritten;
			
			try {
				for( i = 0; i < num; i++ ) {
					map		= (Map) collMap.get( i );
					s		= (String) map.get( Main.KEY_CLASSNAME );
					if( s == null ) continue;

					c		= Class.forName( s );
					trns	= (Transmitter) c.newInstance();
					trns.setName( SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
						new Object[] { new Integer( 1 ), Session.TRNS_NAME_PREFIX, Session.TRNS_NAME_SUFFIX },
						coll2 ));
					doc.transmitters.getMap().copyContexts( this, MapManager.Context.FLAG_DYNAMIC,
															MapManager.Context.NONE_EXCLUSIVE, trns.getMap() );
					coll.add( trns );
					coll2.add( trns );

					// create static trajectory track of the timeline's length
					if( !span.isEmpty() ) {
						d1			= ((double) i / (double) num - 0.25) * Math.PI * 2;
						f1			= (float) (0.25 * (2.0 + Math.cos( d1 )));
						f2			= (float) (0.25 * (2.0 + Math.sin( d1 )));
						for( j = 0; j < 4096; j++ ) {
							buf[0][j] = f1;
							buf[1][j] = f2;
						}
						mte			= trns.getTrackEditor();
						ts			= mte.beginInsert( span, edit );
						for( framesWritten = 0, frames = span.getLength(); framesWritten < frames; ) {
							j		= (int) Math.min( 4096, frames - framesWritten );
							mte.continueWrite( ts, buf, 0, j );
							framesWritten += j;
						}
						mte.finishWrite( ts, edit );
					}
					progress++;
					context.setProgression( (float) progress / (float) num );
				}

				edit.addEdit( new EditAddSessionObjects( this, doc, doc.transmitters, coll, Session.DOOR_TRNS ));
				for( i = 0; i < doc.selectedGroups.size(); i++ ) {
					group	= (SessionGroup) doc.selectedGroups.get( i );
					edit.addEdit( new EditAddSessionObjects( this, doc, group.transmitters, coll, Session.DOOR_TRNS ));
				}
				edit.end();
				doc.getUndoManager().addEdit( edit );
				success = true;
			}
			catch( InstantiationException e1 ) {
				context.setException( e1 );
			}
			catch( IllegalAccessException e2 ) {
				context.setException( e2 );
			}
			catch( IOException e3 ) {
				context.setException( e3 );
			}
			catch( LinkageError e4 ) {
				context.setException( new IOException( e4.getLocalizedMessage() ));
			}
			catch( ClassNotFoundException e5 ) {
				context.setException( e5 );
			}
			// undo partial edits in case of error
			if( !success ) {
				edit.end();
				edit.undo();
			}
			
			return success;
		} // run()
		
		public void finished( ProcessingThread context, Object argument, boolean success ) {}
	}
	
	// action for Remove-Selected-Transmitters/Groups menu item
	public class actionRemoveSessionObjectClass
	extends MenuAction
	{
		private final int				doors;
		private final SessionCollection	scAll;
		private final SessionCollection	scSel;
		private final SessionCollection	scGroups;
	
		private actionRemoveSessionObjectClass( String text, KeyStroke shortcut, int doors,
												SessionCollection scAll, SessionCollection scSel,
												SessionCollection scGroups )
		{
			super( text, shortcut );
			
			this.doors		= doors;
			this.scAll		= scAll;
			this.scSel		= scSel;
			this.scGroups	= scGroups;
		}

		/**
		 *  @synchronization	waitExclusive on doors
		 */
		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		/**
		 *	Removes all current selected receivers
		 *	from the session.
		 */
		public void perform()
		{
			java.util.List  collSelection, collInGroup;
			CompoundEdit	edit;
			SessionGroup	g;

			try {
				doc.bird.waitExclusive( doors );
				collSelection		= scSel.getAll();
				edit				= new CompoundEdit();
				if( scGroups != null ) {
					for( int i = 0; i < scGroups.size(); i++ ) {
						g			= (SessionGroup) scGroups.get( i );
						collInGroup	= g.transmitters.getAll();
						collInGroup.retainAll( collSelection );
						if( !collInGroup.isEmpty() ) {
							edit.addEdit( new EditRemoveSessionObjects( this, doc, g.transmitters, collInGroup,
																		doors | Session.DOOR_GRP ));
						} else {
							collInGroup	= g.receivers.getAll();
							collInGroup.retainAll( collSelection );
							if( !collInGroup.isEmpty() ) {
								edit.addEdit( new EditRemoveSessionObjects( this, doc, g.receivers, collInGroup,
																			doors | Session.DOOR_GRP ));
//							} else {
//								collInGroup	= g.groups.getAll().retainAll( collSelection );
//								if( !collInGroup.isEmpty() ) {
//									edit.addEdit( new EditRemoveSessionObjects( this, doc, g.transmitters, collInGroup,
//																				doors | Session.DOOR_GRP ));
//								}
							}
						}
					}
				}
				edit.addEdit( new EditSetSessionObjects( this, doc, scSel, new ArrayList( 1 ), doors ));
				edit.addEdit( new EditRemoveSessionObjects( this, doc, scAll, collSelection, doors ));
				edit.end();
				doc.getUndoManager().addEdit( edit );
			}
			finally {
				doc.bird.releaseExclusive( doors );
			}
		}
	}

	// action for Insert-Time-Span menu item
	private class actionInsTimeSpanClass
	extends MenuAction
	implements RunnableProcessing
	{
		private double defaultValue = 1.0;
		private String text;
	
		private actionInsTimeSpanClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
			
			this.text = text;
		}

		/**
		 *  Insert time span into all tracks.
		 *  Queries the amount of time from the user
		 *  and starts a ProcessingThread that will
		 *  update the trajectory files.
		 */
		public void actionPerformed( ActionEvent e )
		{
			String	result;
			double	length		= 0.0;
			long	start, stop;

			result  = JOptionPane.showInputDialog( null,
				AbstractApplication.getApplication().getResourceString( "inputDlgInsTimeSpan" ),
				String.valueOf( defaultValue ));  // XXX localized number?
				
			if( result == null ) return;
			try {
				length = Double.parseDouble( result );
			}
			catch( NumberFormatException e1 ) {
				System.err.println( e1.getLocalizedMessage() );
			}
			
			try {
				doc.bird.waitShared( Session.DOOR_TIME );
				start   = doc.timeline.getPosition();
				stop    = start + (long) (doc.timeline.getRate() * length + 0.5);
				if( stop <= start ) return;
				defaultValue = length;
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIME );
			}

			new ProcessingThread( this, root, root, doc, text, new Span( start, stop ), Session.DOOR_TIMETRNSMTE );
		}

		/**
		 *  Insert new data into the trajectory files.
		 *  If the timeline was empty, the tractories of
		 *  the selected transmitters are placed in a circle.
		 *  Otherwise the preceeding and succeeding frame
		 *  is linearly interpolated.
		 *  
		 *  @synchronization	waitExclusive on DOOR_TIMETRNSMTE
		 */
		public boolean run( ProcessingThread context, Object argument )
		{
			Transmitter						trns;
			MultirateTrackEditor			mte;
			float[][]						frameBuf	= new float[2][4096];
			float[][]						interpBuf   = null;
			float[]							chBuf;
			int								i, j, ch, len, interpType;
			float							f1, f2, interpWeight;
			double							d1;
			Span							visibleSpan, interpSpan;
			Span							span		= (Span) argument;
			TrackSpan	ts;
			long							start, interpOff, interpLen;
			long							progress	= 0;
			long							progressLen;
			boolean							success		= false;
			SyncCompoundSessionObjEdit	edit;

			if( span.getStart() > doc.timeline.getLength() ) return false;
			
			interpWeight= 1.0f / (float) (span.getLength() + 1); // +1 because the linear interpolation excludes the neighbouring samples
			visibleSpan = doc.timeline.getVisibleSpan();
			// try to linearly interpolate between sample just before the timeline position
			// and the old sample at the timeline position; if there are no samples for
			// interpolation (this happens at the session start or session en), then
			// just repeat the nearest neighbour.
			interpSpan  = new Span( Math.max( 0, span.getStart() - 1 ),
									Math.min( doc.timeline.getLength(), span.getStart() + 1 ));
			interpLen   = span.getLength();
			interpType  = (int) Math.min( 2, interpSpan.getLength() );
			if( interpType > 1 ) {
				interpBuf   = new float[2][(int) Math.min( interpLen, 4096 )];
			}
			progressLen = Math.max( 1, interpLen ) * doc.transmitters.size();

			edit = new SyncCompoundSessionObjEdit( this, doc, doc.transmitters.getAll(), Transmitter.OWNER_TRAJ,
													   null, null, Session.DOOR_TIMETRNSMTE );
			try {
				for( i = 0; i < doc.transmitters.size(); i++ ) {
					trns	= (Transmitter) doc.transmitters.get( i );
					mte		= trns.getTrackEditor();

					switch( interpType ) {
					case 1: // only one neighbouring sample -> repeat it
					case 0: // session is empty -> fill with angular positions
						if( interpType == 0 ) {
							d1		= ((double) i / (double) doc.transmitters.size() - 0.25) * Math.PI * 2;
							f1		= (float) (0.25 * (2.0 + Math.cos( d1 )));
							f2		= (float) (0.25 * (2.0 + Math.sin( d1 )));
						} else {
							mte.read( interpSpan, frameBuf, 0 );
							f1		= frameBuf[0][0];
							f2		= frameBuf[1][0];
						}
						for( j = 0; j < 4096; j++ ) {
							frameBuf[0][j] = f1;
							frameBuf[1][j] = f2;
						}
						mte			= trns.getTrackEditor();
						ts			= mte.beginInsert( span, edit );
						for( start = span.getStart(); start < span.getStop(); start += len ) {
							len		= (int) Math.min( 4096, span.getStop() - start );
							mte.continueWrite( ts, frameBuf, 0, len );
						}
						mte.finishWrite( ts, edit );
						progress++;
						context.setProgression( (float) progress / (float) progressLen );
						break;

					case 2:	// two neighbouring samples -> interpolation
						mte.read( interpSpan, frameBuf, 0 );
						ts = mte.beginInsert( span, edit );
						for( start = span.getStart(), interpOff = 1; start < span.getStop();
							 start += len, interpOff += len ) {
							 
							len = (int) Math.min( 4096, span.getStop() - start );
							for( ch = 0; ch < 2; ch++ ) {
								f1		= frameBuf[ch][0];
								f2		= (frameBuf[ch][1] - f1) * interpWeight;
								chBuf   = interpBuf[ch];
								for( j = 0; j < len; j++ ) {
									chBuf[j] = (float) (interpOff + j) * f2 + f1;
								}
							}
							mte.continueWrite( ts, interpBuf, 0, len );
							progress += len;
							context.setProgression( (float) progress / (float) progressLen );
						}
						mte.finishWrite( ts, edit );
						break;
					
					default:
						assert false : interpType;
					} // switch( interpType )
				} // for( i = 0; i < doc.transmitterCollection.size(); )

				edit.addEdit( new EditInsertTimeSpan( this, doc, span ));
				if( visibleSpan.isEmpty() ) {
					edit.addEdit( new EditSetTimelineScroll( this, doc, span ));
				} else if( visibleSpan.contains( span.getStart() )) {
					edit.addEdit( new EditSetTimelineScroll( this, doc,
						new Span( visibleSpan.getStart(), visibleSpan.getStop() + span.getLength() )));
				}
				
				edit.end(); // fires doc.tc.modified()
				doc.getUndoManager().addEdit( edit );
				success = true;
			}
			catch( IOException e1 ) {
				edit.cancel();
				context.setException( e1 );
			}
			
			return success;
		} // run()

		public void finished( ProcessingThread context, Object argument, boolean success ) {}
	} // class actionInsTimeSpanClass

	/**
	 *  Action to be attached to
	 *  the Selection-Move-Forward item of the Timeline menu.
	 */
	public class actionSelectionForwardClass
	extends MenuAction
	{
		private actionSelectionForwardClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		/**
		 *  Advance the timeline selection
		 *  by the length of the selection.
		 *  Does nothing if the selection is
		 *  empty or ends at the timeline's end.
		 *
		 *  @synchronization	waitExclusive on DOOR_TIME
		 */
		public void perform()
		{
			Span			span;
			CompoundEdit	edit;
		
			try {
				doc.bird.waitExclusive( Session.DOOR_TIME );
				span	= doc.timeline.getSelectionSpan();
				if( span.isEmpty() || span.getStop() == doc.timeline.getLength() ) return;
				span	= new Span( span.getStop() - 1, Math.min( doc.timeline.getLength(),
									span.getStop() - 1 + span.getLength() ));

				edit	= new CompoundEdit();
				edit.addEdit( new EditSetTimelineSelection( this, doc, span ));
				edit.addEdit( new EditSetTimelinePosition( this, doc, span.getStart() ));
				edit.end();
				doc.getUndoManager().addEdit( edit );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	}

	private class actionSelectionBackwardsClass extends MenuAction
	{
		private actionSelectionBackwardsClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Move the timeline selection backwards
		 *  by the length of the selection.
		 *  Does nothing if the selection is
		 *  empty or starts at the beginning of the timeline.
		 *
		 *  @synchronization	waitExclusive on DOOR_TIME
		 */
		public void actionPerformed( ActionEvent e )
		{
			Span			span;
			CompoundEdit	edit;
		
			try {
				doc.bird.waitExclusive( Session.DOOR_TIME );
				span	= doc.timeline.getSelectionSpan();
				if( span.isEmpty() || span.getStart() == 0 ) return;
				span	= new Span( Math.max( 0, span.getStart() + 1 - span.getLength() ), span.getStart() + 1 );

				edit	= new CompoundEdit();
				edit.addEdit( new EditSetTimelineSelection( this, doc, span ));
				edit.addEdit( new EditSetTimelinePosition( this, doc, span.getStart() ));
				edit.end();
				doc.getUndoManager().addEdit( edit );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	}

	// action for the Filter-Trajectories menu item
	private class actionFilterClass
	extends MenuAction
	{
		private actionFilterClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Brings up the Filter-Trajectories dialog
		 */
		public void actionPerformed( ActionEvent e )
		{
			JFrame filterFrame = (JFrame) root.getComponent( Main.COMP_FILTER );
		
			if( filterFrame == null ) {
				filterFrame = new FilterDialog( root, doc );
				root.addComponent( Main.COMP_FILTER, filterFrame );
			}
			filterFrame.setVisible( true );
			filterFrame.show();
		}
	}
	
// ---------------- Action objects for window operations ---------------- 

	// action for the About menu item
	private class actionAboutClass
	extends MenuAction
	{
		private actionAboutClass( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Brings up the About-Box
		 */
		public void actionPerformed( ActionEvent e )
		{
			de.sciss.gui.AboutBox aboutBox = (de.sciss.gui.AboutBox) root.getComponent( AboutBox.COMP_ABOUTBOX );
		
			if( aboutBox == null ) {
				final de.sciss.app.Application	app = AbstractApplication.getApplication();
				final char sep = File.separatorChar;
				aboutBox = new AboutBox();
				aboutBox.setBuildVersion( new File( app.getName()+".app"+sep+"Contents"+sep+
					"Resources"+ sep+"Java"+sep+app.getName()+".jar" ));
				root.addComponent( AboutBox.COMP_ABOUTBOX, aboutBox );
			}
			aboutBox.setVisible( true );
			aboutBox.show();
		}
	}

	// generic action for bringing up
	// a window which is identified by
	// a component object. the frame is
	// looked up using the Main's getComponent()
	// method.
	private class actionShowWindowClass extends MenuAction
	{
		Object component;
	
		// @param   component   the key for getting the
		//						component using Main.getComponent()
		private actionShowWindowClass( String text, KeyStroke shortcut, Object component )
		{
			super( text, shortcut );
			
			this.component = component;
		}

		/**
		 *  Tries to find the component using
		 *  the <code>Main</code> class' <code>getComponent</code>
		 *  method. It does not instantiate a
		 *  new object if the component is not found.
		 *  If the window is already open, this
		 *  method will bring it to the front.
		 */
		public void actionPerformed( ActionEvent e )
		{
			JFrame frame = (JFrame) root.getComponent( component );
			if( frame != null ) {
				frame.setVisible( true );
				frame.show();
			}
		}
	}

	// generic action for bringing up
	// a html document either in the
	// help viewer or the default web browser
	private class actionURLViewerClass extends MenuAction
	{
		private final String	theURL;
		private final boolean	openWebBrowser;
	
		// @param	theURL			what file to open ; when using the
		//							help viewer, that's the relative help file name
		//							without .html extension. when using web browser,
		//							that's the complete URL!
		// @param   openWebBrowser	if true, use the default web browser,
		//							if false use internal help viewer
		private actionURLViewerClass( String text, KeyStroke shortcut, String theURL, boolean openWebBrowser )
		{
			super( text, shortcut );
			
			this.theURL			= theURL;
			this.openWebBrowser	= openWebBrowser;
		}

		/**
		 *  Tries to find the component using
		 *  the <code>Main</code> class' <code>getComponent</code>
		 *  method. It does not instantiate a
		 *  new object if the component is not found.
		 *  If the window is already open, this
		 *  method will bring it to the front.
		 */
		public void actionPerformed( ActionEvent e )
		{
			if( openWebBrowser ) {
				try {
					MRJAdapter.openURL( theURL );
				}
				catch( IOException e1 ) {
					GUIUtil.displayError( null, e1, NAME );
				}
			} else {
				HelpFrame helpFrame = (HelpFrame) root.getComponent( Main.COMP_HELP );
			
				if( helpFrame == null ) {
					helpFrame = new HelpFrame();
					root.addComponent( Main.COMP_HELP, helpFrame );
				}
				helpFrame.loadHelpFile( theURL );
				helpFrame.setVisible( true );
				helpFrame.show();
			}
		}
	}

	private class actionDebugDumpListenersClass
	extends MenuAction
	{
		private actionDebugDumpListenersClass()
		{
			super( "Dump Listeners" );
		}

		public void actionPerformed( ActionEvent e )
		{
			System.err.println( "---------- all transmitters ----------" );
			doc.transmitters.debugDump();
			System.err.println( "---------- all receivers ----------" );
			doc.receivers.debugDump();
			System.err.println( "---------- all groups ----------" );
			doc.groups.debugDump();
			System.err.println( "---------- active transmitters ----------" );
			doc.activeTransmitters.debugDump();
			System.err.println( "---------- active receivers ----------" );
			doc.activeReceivers.debugDump();
			for( int i = 0; i < doc.groups.size(); i++ ) {
				SessionGroup g = (SessionGroup) doc.groups.get( i );
				System.err.println( "............ group : "+g.getName() );
				System.err.println( "............ group transmitters ............" );
				g.transmitters.debugDump();
				System.err.println( "............ group receivers ............" );
				g.receivers.debugDump();
			}
		}
	}
}