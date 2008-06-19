/*
 *  MenuFactory.java
 *  Meloncillo
 *
 *  Copyright (c) 2004-2008 Hanns Holger Rutz. All rights reserved.
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

import net.roydesign.mac.MRJAdapter;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.debug.*;
import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.io.*;
import de.sciss.meloncillo.lisp.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.receiver.*;
import de.sciss.meloncillo.render.*;
import de.sciss.meloncillo.transmitter.*;
import de.sciss.meloncillo.util.*;
import de.sciss.util.NumberSpace;

import de.sciss.app.*;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicMenuFactory;
import de.sciss.gui.*;
import de.sciss.gui.MenuItem;
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
extends BasicMenuFactory
{
	private final Session   doc;
	
	private ActionOpen				actionOpen;
	private ActionSave				actionSave;
	private ActionSaveAs			actionSaveAs;
	
	private Action	actionClearSession,
					actionInsTimeSpan, actionNewReceivers, actionNewTransmitters, actionNewGroup,
					actionRemoveTransmitters, actionRemoveGroups, actionFilter,
					actionBounce, actionSelectionBackwards,
					actionShowSurface, actionShowTimeline, actionShowTransport,
					actionShowObserver, actionShowMeter, actionShowRealtime;
	
	private Action  actionDebugDumpUndo, actionDebugDumpTracks, actionDebugViewTrack,
					actionDebugDumpPrefs, actionDebugDumpRealtime, actionJathaDiddler,
					actionDebugDumpListeners, actionHRIRPrepare;


	// for custom JOptionPane calls (see actionNewReceiversClass )
	private final String[]	queryOptions;

	// --- publicly accessible actions ---
	/**
	 *  Action that removes all
	 *  selected receivers.
	 */
	public ActionRemoveSessionObject	actionRemoveReceivers;
	/**
	 *  Action that advances the
	 *  the timeline selection.
	 */
	public ActionSelectionForward  actionSelectionForward;
	/**
	 *  Action that opens the
	 *  preferences frame.
	 */
	public ActionPreferences		actionPreferences;
	
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
		super( root );
		
//		this.root   = root;
		this.doc	= doc;
		
		final de.sciss.app.Application	app	= AbstractApplication.getApplication();
		
		queryOptions = new String[] {
			app.getResourceString( "buttonCancel" ),
			app.getResourceString( "buttonOk" )
		};

		createActions();
	}
	
	// @todo	this should eventually read the tree from an xml file
	protected void addMenuItems()
	{
		final Preferences		prefs; // = getApplication().getUserPrefs();
		MenuGroup				mg, smg;
		MenuCheckItem			mci;
		BooleanPrefsMenuAction	ba;
		int						i;
		
		// Ctrl on Mac / Ctrl+Alt on PC
//		final int myCtrl = MENU_SHORTCUT == InputEvent.CTRL_MASK ? InputEvent.CTRL_MASK | InputEvent.ALT_MASK : InputEvent.CTRL_MASK;

		// --- file menu ---

		mg	= (MenuGroup) get( "file" );
		mg.add( new MenuItem( "clearSession", actionClearSession ), 0 );
		i	= mg.indexOf( "saveAs" );
		smg = new MenuGroup( "insert", getResourceString( "menuInsert" ));
		smg.add( new MenuItem( "newReceivers", actionNewReceivers ));
		smg.add( new MenuItem( "newTransmitters", actionNewTransmitters ));
		smg.add( new MenuItem( "newGroup", actionNewGroup ));
		mg.add( smg, i + 1 );
		smg = new MenuGroup( "remove", getResourceString( "menuRemove" ));
		smg.add( new MenuItem( "removeReceivers", actionRemoveReceivers ));
		smg.add( new MenuItem( "removeTransmitters", actionRemoveTransmitters ));
		smg.add( new MenuItem( "removeGroup", actionRemoveGroups ));
		mg.add( smg, i + 2 );
		mg.add( new MenuSeparator(), i + 3 );
		mg.add( new MenuItem( "bounce", actionBounce ), i + 4 );
		
		// --- timeline menu ---
		i	= indexOf( "edit" );
		mg	= new MenuGroup( "timeline", getResourceString( "menuTimeline" ));
		mg.add( new MenuItem( "insTimeSpan", actionInsTimeSpan ));
		mg.add( new MenuItem( "selectionForward", actionSelectionForward ));
		mg.add( new MenuItem( "selectionBackwards", actionSelectionBackwards ));
		mg.addSeparator();
		mg.add( new MenuItem( "filter", actionFilter ));
		add( mg, i + 1 );

		// --- view menu ---
		mg			= new MenuGroup( "view", getResourceString( "menuView" ));
		prefs		= getApplication().getUserPrefs().node( PrefsUtil.NODE_SHARED );
		ba			= new BooleanPrefsMenuAction( getResourceString( "menuSnapToObjects" ), KeyStroke.getKeyStroke(
		  			         KeyEvent.VK_LESS, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		mci			= new MenuCheckItem( "snapToObjects", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_SNAP );
		mg.add( mci );

		smg			= new MenuGroup( "viewSurface", getResourceString( "menuViewSurface" ));
		ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewRcvSense" ), null );
		mci			= new MenuCheckItem( "viewRcvSense", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_VIEWRCVSENSE );
		smg.add( mci );
		ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewRcvSenseEqP" ), null );
		mci			= new MenuCheckItem( "viewRcvSenseEqP", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_VIEWEQPRECEIVER );
		smg.add( mci );
		ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewTrnsTraj" ), null );
		mci			= new MenuCheckItem( "viewTrnsTraj", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_VIEWTRNSTRAJ );
		smg.add( mci );
		ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewUserImages" ), null );
		mci			= new MenuCheckItem( "viewUserImages", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_VIEWUSERIMAGES );
		smg.add( mci );
		ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewRulers" ), null );
		mci			= new MenuCheckItem( "viewRulers", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_VIEWRULERS );
		smg.add( mci );
		mg.add( smg );
		add( mg, i + 2 );

		// --- extras menu ---
		mg   = new MenuGroup( "extras", getResourceString( "menuExtras" ));
		mg.add( new MenuItem( "jathaDiddler", actionJathaDiddler ));
		mg.add( new MenuItem( "hrirPrepare", actionHRIRPrepare ));
		add( mg, i + 3 );
		
		// --- window menu ---
		mg	= (MenuGroup) get( "window" );
		mg.add( new MenuItem( "showSurface", actionShowSurface ), 0 );
		mg.add( new MenuItem( "showTimeline", actionShowTimeline ), 1 );
		mg.add( new MenuSeparator(), 2 );
		mg.add( new MenuItem( "showTransport", actionShowTransport ), 3 );
		mg.add( new MenuItem( "showObserver", actionShowObserver ), 4 );
		mg.add( new MenuItem( "showMeter", actionShowMeter ), 5 );
		mg.add( new MenuItem( "showRealtime", actionShowRealtime ), 6 );

		// --- debug menu ---
		mg   = new MenuGroup( "debug", "Debug" );
		mg.add( new MenuItem( "debugDumpUndo", actionDebugDumpUndo ));
		mg.add( new MenuItem( "debugDumpTracks", actionDebugDumpTracks ));
		mg.add( new MenuItem( "debugViewTrack", actionDebugViewTrack ));
		mg.add( new MenuItem( "debugDumpPrefs", actionDebugDumpPrefs ));
		mg.add( new MenuItem( "debugDumpRealtime", actionDebugDumpRealtime ));
		mg.add( new MenuItem( "debugDumpListeners", actionDebugDumpListeners ));
		i	= indexOf( "help" );
		add( mg, i );

	}

	private void createActions()
	{
		final de.sciss.app.Application	app = AbstractApplication.getApplication();
		
		// --- file menu ---
		actionClearSession	= new ActionClearSession( app.getResourceString( "menuClearSession" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_N, MENU_SHORTCUT ));
		actionOpen		= new ActionOpen(  app.getResourceString( "menuOpen" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_O, MENU_SHORTCUT ));
		actionSave		= new ActionSave(  app.getResourceString( "menuSave" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_S, MENU_SHORTCUT ));
		actionSaveAs	= new ActionSaveAs( app.getResourceString( "menuSaveAs" ), KeyStroke.getKeyStroke(
												KeyEvent.VK_S, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));

		actionNewReceivers = new ActionNewReceivers(  app.getResourceString( "menuNewReceivers" ),
							KeyStroke.getKeyStroke( KeyEvent.VK_N, MENU_SHORTCUT + KeyEvent.ALT_MASK ));
		actionNewTransmitters = new ActionNewTransmitters( app.getResourceString( "menuNewTransmitters" ),
							KeyStroke.getKeyStroke( KeyEvent.VK_N, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		actionNewGroup	= new ActionNewGroup( app.getResourceString( "menuNewGroup" ), null );
		actionRemoveReceivers = new ActionRemoveSessionObject( app.getResourceString( "menuRemoveReceivers" ),
											null, Session.DOOR_RCV, doc.receivers, doc.selectedReceivers, doc.groups );
		actionRemoveTransmitters = new ActionRemoveSessionObject( app.getResourceString( "menuRemoveTransmitters" ),
											null, Session.DOOR_TRNS, doc.transmitters, doc.selectedTransmitters, doc.groups );
		actionRemoveGroups	= new ActionRemoveSessionObject( app.getResourceString( "menuRemoveGroups" ),
											null, Session.DOOR_GRP, doc.groups, doc.selectedGroups, null );

		actionBounce	= new ActionBounce( app.getResourceString( "menuBounce" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_R, MENU_SHORTCUT ));
//		actionQuit		= new ActionQuit(  app.getResourceString( "menuQuit" ),
//												KeyStroke.getKeyStroke( KeyEvent.VK_Q, MENU_SHORTCUT ));

		// --- timeline menu ---
		actionInsTimeSpan   = new ActionInsTimeSpan( app.getResourceString( "menuInsTimeSpan" ),
									KeyStroke.getKeyStroke( KeyEvent.VK_E, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		actionSelectionForward = new ActionSelectionForward( app.getResourceString( "menuSelectionForward" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_CLOSE_BRACKET, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		actionSelectionBackwards = new ActionSelectionBackwards( app.getResourceString( "menuSelectionBackwards" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_OPEN_BRACKET, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		actionFilter = new ActionFilter( app.getResourceString( "menuFilter" ),
										KeyStroke.getKeyStroke( KeyEvent.VK_F, MENU_SHORTCUT ));

		// --- view menu ---

		// --- window menu ---
		actionShowSurface		= new ActionShowWindow( app.getResourceString( "frameSurface" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_MULTIPLY, MENU_SHORTCUT ), Main.COMP_SURFACE );
		actionShowTimeline		= new ActionShowWindow( app.getResourceString( "frameTimeline" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_EQUALS, MENU_SHORTCUT ), Main.COMP_TIMELINE );
		actionShowTransport		= new ActionShowWindow( app.getResourceString( "paletteTransport" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_NUMPAD1, MENU_SHORTCUT ), Main.COMP_TRANSPORT );
		actionShowObserver		= new ActionShowWindow( app.getResourceString( "paletteObserver" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_NUMPAD3, MENU_SHORTCUT ), Main.COMP_OBSERVER );
		actionShowMeter			= new ActionShowWindow( app.getResourceString( "frameMeter" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_NUMPAD4, MENU_SHORTCUT ), Main.COMP_METER );
		actionShowRealtime		= new ActionShowWindow( app.getResourceString( "frameRealtime" ), KeyStroke.getKeyStroke( 
										KeyEvent.VK_NUMPAD2, MENU_SHORTCUT ), Main.COMP_REALTIME );

		// --- extras menu ---
		actionJathaDiddler		= JathaDiddler.getMenuAction();
		actionHRIRPrepare		= HRIRPrepareDialog.getMenuAction( doc );

		// --- debug menu ---
		actionDebugDumpUndo		= doc.getUndoManager().getDebugDumpAction();
		actionDebugDumpPrefs	= PrefsUtil.getDebugDumpAction( doc );
		actionDebugDumpTracks   = DebugTrackEditor.getDebugDumpAction( doc );
		actionDebugViewTrack	= DebugTrackEditor.getDebugViewAction( doc );
		actionDebugDumpRealtime	= ((Main) getApplication()).transport.getDebugDumpAction();
		actionDebugDumpListeners= new ActionDebugDumpListeners();
	}

	public void showPreferences()
	{
		PrefsFrame prefsFrame = (PrefsFrame) getApplication().getComponent( Main.COMP_PREFS );
	
		if( prefsFrame == null ) {
			prefsFrame = new PrefsFrame( doc );
		}
		prefsFrame.setVisible( true );
		prefsFrame.toFront();
	}
	
	protected Action getOpenAction()
	{
		return actionOpen;
	}

	protected ActionOpenRecent createOpenRecentAction( String name, File path )
	{
		return new ActionOpenRecent( name, path );
	}

	public void openDocument( File f )
	{
		actionOpen.perform( f );
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

// ---------------- Action objects for file (session) operations ---------------- 

	// action for the Insert-New-Receivers menu item
	private class ActionNewReceivers
	extends MenuAction
	{
		private Number		num		= new Integer( 1 );
		private StringItem	type	= null;

		private ActionNewReceivers( String text, KeyStroke shortcut )
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
			final NumberField		ggNum		= new NumberField( NumberSpace.createIntSpace( 1, 0x10000 ));
			final JComboBox			ggType		= new JComboBox();

			for( int i = 0; i < collTypes.size(); i++ ) {
				ggType.addItem( collTypes.get( i ));
			}
			
			ggNum.setNumber( num );
			if( type != null ) ggType.setSelectedItem( type );

			msgPane.add( ggNum );
			msgPane.add( ggType );
			GUIUtil.makeCompactSpringGrid( msgPane, 1, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
//			HelpGlassPane.setHelp( msgPane, getValue( NAME ).toString() );	// EEE
		
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
	private class ActionNewGroup
	extends MenuAction
	{
		private ActionNewGroup( String text, KeyStroke shortcut )
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
	private class ActionClearSession
	extends MenuAction
	{
		private String text;
	
		private ActionClearSession( String text, KeyStroke shortcut )
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

			final MainFrame mf = (MainFrame) getApplication().getComponent( Main.COMP_MAIN );

			((Main) getApplication()).transport.stopAndWait();
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
	private class ActionOpen
	extends MenuAction
	implements RunnableProcessing
	{
		private String text;
	
		private ActionOpen( String text, KeyStroke shortcut )
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
			Frame		frame = (Frame) getApplication().getComponent( Main.COMP_MAIN );

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
			final Main root = (Main) AbstractApplication.getApplication();
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
			final MainFrame mf		= (MainFrame) getApplication().getComponent( Main.COMP_MAIN );
		
			if( success ) {
				addRecent( (File) options.get( "file" ));
				if( AbstractApplication.getApplication().getUserPrefs().getBoolean(
					PrefsUtil.KEY_RECALLFRAMES, false )) {

//					BasicFrame.restoreAllFromPrefs();
// EEE
				}
				warn = options.get( XMLRepresentation.KEY_WARNING );
				if( warn != null ) {
					JOptionPane.showMessageDialog( mf.getWindow(), warn, getValue( Action.NAME ).toString(),
												   JOptionPane.WARNING_MESSAGE );
				}
			}
			doc.setDirty( false );
			mf.updateTitle();
		}
	}
	
	// action for the Save-Session menu item
	private class ActionSave
	extends MenuAction
	implements RunnableProcessing
	{
		private String text;

		private ActionSave( String text, KeyStroke shortcut )
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
			final Main root = (Main) AbstractApplication.getApplication();
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
			final MainFrame mf = (MainFrame) getApplication().getComponent( Main.COMP_MAIN );

			if( success ) {
				addRecent( (File) argument );
				doc.setDirty( false );
			} else {
				File tempDir = new File( ((File) argument).getParentFile().getAbsolutePath() + ".tmp" );
				if( tempDir.exists() ) {
					JOptionPane.showMessageDialog( mf.getWindow(),
						AbstractApplication.getApplication().getResourceString( "warnOldSessionDir" )+ " :\n"+
						tempDir.getAbsolutePath(), getValue( Action.NAME ).toString(),
						JOptionPane.WARNING_MESSAGE );
				}
			}
			mf.updateTitle();
		}
	}
	
	// action for the Save-Session-As menu item
	private class ActionSaveAs
	extends MenuAction
	{
		private ActionSaveAs( String text, KeyStroke shortcut )
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
			Frame		frame = (Frame) getApplication().getComponent( Main.COMP_MAIN );

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
	private class ActionBounce
	extends MenuAction
	{
		private ActionBounce( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}
		
		/**
		 *  Opens the bounce-to-disk dialog
		 */
		public void actionPerformed( ActionEvent e )
		{
			AppWindow bounceFrame = (AppWindow) getApplication().getComponent( Main.COMP_BOUNCE );
		
			if( bounceFrame == null ) {
				final Main root = (Main) AbstractApplication.getApplication();
				bounceFrame = new BounceDialog( root, doc );
			}
			bounceFrame.setVisible( true );
			bounceFrame.toFront();
		}
	}

//	// action for Application-Quit menu item
//	private class ActionQuit
//	extends MenuAction
//	{
//		private ActionQuit( String text, KeyStroke shortcut )
//		{
//			super( text, shortcut );
//		}
//		
//		public void actionPerformed( ActionEvent e )
//		{
//			getApplication().quit();
//		}
//	}
	
// ---------------- Action objects for edit operations ---------------- 
	
//	// action for Edit-Cut menu item
//	private class ActionCut
//	extends MenuAction
//	{
//		private ActionCut( String text, KeyStroke shortcut )
//		{
//			super( text, shortcut );
//		}
//
//		/**
//		 *  Tries to find the current active window
//		 *  and - if this window implements the
//		 *  <code>EditMenuListener</code> interface - calls
//		 *  <code>editCut</code> on that window.
//		 *
//		 *  @see	EditMenuListener#editCut( ActionEvent )
//		 */
//		public void actionPerformed( ActionEvent e )
//		{
//			JFrame frame = fuckINeedTheWindow( e );
//			if( frame == null || !(frame instanceof EditMenuListener) ) return;
//			
//			((EditMenuListener) frame).editCut( e );
//		}
//	}
// EEE
	
//	// action for Edit-Copy menu item
//	private class ActionCopy
//	extends MenuAction
//	{
//		private ActionCopy( String text, KeyStroke shortcut )
//		{
//			super( text, shortcut );
//		}
//
//		/**
//		 *  Tries to find the current active window
//		 *  and - if this window implements the
//		 *  <code>EditMenuListener</code> interface - calls
//		 *  <code>editCopy</code> on that window.
//		 *
//		 *  @see	EditMenuListener#editCopy( ActionEvent )
//		 */
//		public void actionPerformed( ActionEvent e )
//		{
//			JFrame frame = fuckINeedTheWindow( e );
//			if( frame == null || !(frame instanceof EditMenuListener) ) return;
//			
//			((EditMenuListener) frame).editCopy( e );
//		}
//	}
// EEE
	
//	// action for Edit-Paste menu item
//	private class ActionPaste
//	extends MenuAction
//	{
//		private ActionPaste( String text, KeyStroke shortcut )
//		{
//			super( text, shortcut );
//		}
//
//		/**
//		 *  Tries to find the current active window
//		 *  and - if this window implements the
//		 *  <code>EditMenuListener</code> interface - calls
//		 *  <code>editPaste</code> on that window.
//		 *
//		 *  @see	EditMenuListener#editPaste( ActionEvent )
//		 */
//		public void actionPerformed( ActionEvent e )
//		{
//			JFrame frame = fuckINeedTheWindow( e );
//			if( frame == null || !(frame instanceof EditMenuListener) ) return;
//			
//			((EditMenuListener) frame).editPaste( e );
//		}
//	}
// EEE
	
//	// action for Edit-Clear/Delete menu item
//	private class ActionClear
//	extends MenuAction
//	{
//		private ActionClear( String text, KeyStroke shortcut )
//		{
//			super( text, shortcut );
//		}
//
//		/**
//		 *  Tries to find the current active window
//		 *  and - if this window implements the
//		 *  <code>EditMenuListener</code> interface - calls
//		 *  <code>editClear</code> on that window.
//		 *
//		 *  @see	EditMenuListener#editClear( ActionEvent )
//		 */
//		public void actionPerformed( ActionEvent e )
//		{
//			JFrame frame = fuckINeedTheWindow( e );
//			if( frame == null || !(frame instanceof EditMenuListener) ) return;
//			
//			((EditMenuListener) frame).editClear( e );
//		}
//	}
// EEE
	
//	// action for Edit-Select-All menu item
//	private class ActionSelectAll
//	extends MenuAction
//	{
//		private ActionSelectAll( String text, KeyStroke shortcut )
//		{
//			super( text, shortcut );
//		}
//
//		/**
//		 *  Tries to find the current active window
//		 *  and - if this window implements the
//		 *  <code>EditMenuListener</code> interface - calls
//		 *  <code>editSelectAll</code> on that window.
//		 *
//		 *  @see	EditMenuListener#editSelectAll( ActionEvent )
//		 */
//		public void actionPerformed( ActionEvent e )
//		{
//			JFrame frame = fuckINeedTheWindow( e );
//			if( frame == null || !(frame instanceof EditMenuListener) ) return;
//			
//			((EditMenuListener) frame).editSelectAll( e );
//		}
//	}
// EEE
	
// ---------------- Action objects for surface operations ---------------- 
	
// ---------------- Action objects for timeline operations ---------------- 

	// action for Insert-New-Transmitters menu item
	private class ActionNewTransmitters
	extends MenuAction
	implements RunnableProcessing
	{
		private int		defaultValue = 1;
		private String  text;
	
		private ActionNewTransmitters( String text, KeyStroke shortcut )
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

			final Main root = (Main) AbstractApplication.getApplication();
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
	public class ActionRemoveSessionObject
	extends MenuAction
	{
		private final int				doors;
		private final SessionCollection	scAll;
		private final SessionCollection	scSel;
		private final SessionCollection	scGroups;
	
		private ActionRemoveSessionObject( String text, KeyStroke shortcut, int doors,
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
	private class ActionInsTimeSpan
	extends MenuAction
	implements RunnableProcessing
	{
		private double defaultValue = 1.0;
		private String text;
	
		private ActionInsTimeSpan( String text, KeyStroke shortcut )
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

			final Main root = (Main) AbstractApplication.getApplication();
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
	public class ActionSelectionForward
	extends MenuAction
	{
		private ActionSelectionForward( String text, KeyStroke shortcut )
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

	private class ActionSelectionBackwards extends MenuAction
	{
		private ActionSelectionBackwards( String text, KeyStroke shortcut )
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
	private class ActionFilter
	extends MenuAction
	{
		private ActionFilter( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Brings up the Filter-Trajectories dialog
		 */
		public void actionPerformed( ActionEvent e )
		{
			AppWindow filterFrame = (AppWindow) getApplication().getComponent( Main.COMP_FILTER );
		
			if( filterFrame == null ) {
				final Main root = (Main) AbstractApplication.getApplication();
				filterFrame = new FilterDialog( root, doc );
			}
			filterFrame.setVisible( true );
			filterFrame.toFront();
		}
	}
	
// ---------------- Action objects for window operations ---------------- 

	private class ActionDebugDumpListeners
	extends MenuAction
	{
		private ActionDebugDumpListeners()
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