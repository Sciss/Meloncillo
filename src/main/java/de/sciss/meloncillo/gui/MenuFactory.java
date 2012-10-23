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

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.Action;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SpringLayout;

import de.sciss.util.Flag;
import de.sciss.util.NumberSpace;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.BasicWindowHandler;
import de.sciss.common.ProcessingThread;
import de.sciss.gui.BooleanPrefsMenuAction;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.MenuAction;
import de.sciss.gui.MenuCheckItem;
import de.sciss.gui.MenuGroup;
import de.sciss.gui.MenuItem;
import de.sciss.gui.MenuSeparator;
import de.sciss.gui.NumberField;
import de.sciss.gui.ProgressComponent;
import de.sciss.gui.StringItem;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.debug.HRIRPrepareDialog;
import de.sciss.meloncillo.edit.BasicCompoundEdit;
import de.sciss.meloncillo.edit.EditAddSessionObjects;
import de.sciss.meloncillo.edit.EditRemoveSessionObjects;
import de.sciss.meloncillo.edit.TimelineVisualEdit;
import de.sciss.meloncillo.io.AudioStake;
import de.sciss.meloncillo.io.AudioTrail;
import de.sciss.meloncillo.io.XMLRepresentation;
import de.sciss.meloncillo.lisp.JathaDiddler;
import de.sciss.meloncillo.receiver.Receiver;
import de.sciss.meloncillo.render.BounceDialog;
import de.sciss.meloncillo.render.FilterDialog;
import de.sciss.meloncillo.session.BasicSessionCollection;
import de.sciss.meloncillo.session.BasicSessionGroup;
import de.sciss.meloncillo.session.DocumentFrame;
import de.sciss.meloncillo.session.GroupableSessionObject;
import de.sciss.meloncillo.session.MutableSessionCollection;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionCollection;
import de.sciss.meloncillo.session.SessionGroup;
import de.sciss.meloncillo.transmitter.Transmitter;
import de.sciss.meloncillo.util.MapManager;
import de.sciss.meloncillo.util.PrefsUtil;

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
	
	private ActionOpen		actionOpen;
	
	private Action	actionNewReceivers, actionNewTransmitters, actionNewGroup,
					actionRemoveTransmitters, actionRemoveGroups, actionFilter,
					actionBounce, actionSelectionBackwards,
					actionShowSurface, actionShowTimeline, actionShowTransport,
					actionShowMeter, actionShowRealtime;
	
	private Action  actionDebugDumpUndo,
					actionDebugDumpPrefs, actionJathaDiddler,
					actionDebugDumpListeners, actionHRIRPrepare;
//	private Action  actionDebugDumpTracks, actionDebugViewTrack,
	
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
		mg.add( new MenuItem( "clearSession", getResourceString( "menuClearSession" ), KeyStroke.getKeyStroke( KeyEvent.VK_N, MENU_SHORTCUT )));
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
//		mg.add( new MenuItem( "insTimeSpan", actionInsTimeSpan ));
		mg.add( new MenuItem( "trimToSelection", getResourceString( "menuTrimToSelection" )));
		mg.add( new MenuItem( "insertSilence", getResourceString( "menuInsTimeSpan" ),
							  KeyStroke.getKeyStroke( KeyEvent.VK_E, MENU_SHORTCUT + InputEvent.SHIFT_MASK )));
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
		mg.add( new MenuItem( "observer", new ActionObserver( getResourceString( "paletteObserver" ), KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD3, MENU_SHORTCUT ))), 4 );
		mg.add( new MenuItem( "showMeter", actionShowMeter ), 5 );
		mg.add( new MenuItem( "showRealtime", actionShowRealtime ), 6 );

		// --- debug menu ---
		mg   = new MenuGroup( "debug", "Debug" );
		mg.add( new MenuItem( "debugDumpUndo", actionDebugDumpUndo ));
//		mg.add( new MenuItem( "debugDumpTracks", actionDebugDumpTracks ));
//		mg.add( new MenuItem( "debugViewTrack", actionDebugViewTrack ));
		mg.add( new MenuItem( "debugDumpPrefs", actionDebugDumpPrefs ));
//		mg.add( new MenuItem( "debugDumpRealtime", actionDebugDumpRealtime ));
		mg.add( new MenuItem( "debugDumpListeners", actionDebugDumpListeners ));
		i	= indexOf( "help" );
		add( mg, i );

	}

	private void createActions()
	{
		final de.sciss.app.Application	app = AbstractApplication.getApplication();
		
		// --- file menu ---
		actionOpen		= new ActionOpen(  app.getResourceString( "menuOpen" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_O, MENU_SHORTCUT ));
		actionNewReceivers = new ActionNewReceivers(  app.getResourceString( "menuNewReceivers" ),
							KeyStroke.getKeyStroke( KeyEvent.VK_N, MENU_SHORTCUT + KeyEvent.ALT_MASK ));
		actionNewTransmitters = new ActionNewTransmitters( app.getResourceString( "menuNewTransmitters" ),
							KeyStroke.getKeyStroke( KeyEvent.VK_N, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
		actionNewGroup	= new ActionNewGroup( app.getResourceString( "menuNewGroup" ), null );
		actionRemoveReceivers = new ActionRemoveSessionObject( app.getResourceString( "menuRemoveReceivers" ), null,
			doc.getMutableReceivers(), doc.getMutableSelectedReceivers(), doc.getSelectedReceivers() );
		actionRemoveTransmitters = new ActionRemoveSessionObject( app.getResourceString( "menuRemoveTransmitters" ), null,
			doc.getMutableTracks(), doc.getMutableSelectedTracks(), doc.getSelectedTransmitters() );
		actionRemoveGroups	= new ActionRemoveSessionObject( app.getResourceString( "menuRemoveGroups" ), null,
			doc.getMutableGroups(), doc.getMutableSelectedGroups(), doc.getSelectedGroups() );

		actionBounce	= new ActionBounce( app.getResourceString( "menuBounce" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_R, MENU_SHORTCUT ));
//		actionQuit		= new ActionQuit(  app.getResourceString( "menuQuit" ),
//												KeyStroke.getKeyStroke( KeyEvent.VK_Q, MENU_SHORTCUT ));

		// --- timeline menu ---
//		actionInsTimeSpan   = new ActionInsTimeSpan( app.getResourceString( "menuInsTimeSpan" ),
//									KeyStroke.getKeyStroke( KeyEvent.VK_E, MENU_SHORTCUT + KeyEvent.SHIFT_MASK ));
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
//		actionShowObserver		= new ActionShowWindow( app.getResourceString( "paletteObserver" ), KeyStroke.getKeyStroke( 
//										KeyEvent.VK_NUMPAD3, MENU_SHORTCUT ), Main.COMP_OBSERVER );
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
//		actionDebugDumpTracks   = DebugTrackEditor.getDebugDumpAction( doc );
//		actionDebugViewTrack	= DebugTrackEditor.getDebugViewAction( doc );
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

//	/**
//	 *  Checks if there are unsaved changes to
//	 *  the session. If so, displays a confirmation
//	 *  dialog. Invokes Save/Save As depending
//	 *  on user selection.
//	 *  
//	 *  @param  parentComponent the component associated with
//	 *							the proposed action, e.g. root
//	 *  @param  actionName		name of the action that
//	 *							threatens the session
//	 *  @return					- true if the action should proceed,
//	 *							- false if the action should be aborted
//	 */
//	public boolean confirmUnsaved( Component parentComponent, String actionName )
//	{
//		if( !doc.isDirty() ) return true;
//		
//		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
//		final String[]					options	= { app.getResourceString( "buttonSave" ),
//													app.getResourceString( "buttonCancel" ),
//													app.getResourceString( "buttonDontSave" ) };
//		int								choice;
//		ProcessingThread				proc;
//		File							f;
//		
//		choice = JOptionPane.showOptionDialog( parentComponent, app.getResourceString( "optionDlgUnsaved" ), actionName,
//											   JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
//											   options, options[0] );
//		switch( choice ) {
//		case JOptionPane.CLOSED_OPTION:
//		case 1:
//			return false;
//			
//		case 2:
//			return true;
//			
//		case 0:
//			f = (File) doc.getMap().getValue( Session.MAP_KEY_PATH );
//			if( f == null ) {
//				f = actionSaveAs.queryFile();
//			}
//			if( f != null ) {
//				proc = actionSave.perform( f );
//				if( proc != null ) {
//					return proc.sync();
//				}
//			}
//			return false;
//			
//		default:
//			assert false : choice;
//			return false;
//		}
//	}

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
			List			collNewRcv, coll2;
			Point2D			anchor;
			double			d1;
			Class			c;

			final List				collTypes	= Main.getReceiverTypes();
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
				collNewRcv	= new ArrayList();
				coll2	= doc.getReceivers().getAll();
				for( int i = 0; i < numi; i++ ) {
					d1  = ((double) i / (double) numi - 0.25) * Math.PI * -2;
//					anchor = new Point2D.Double( 0.25 * (2.0 + Math.cos( d1 )), 0.25 * (2.0 + Math.sin( d1 )));
					anchor = new Point2D.Double( 0.5 * + Math.cos( d1 ), 0.5 * Math.sin( d1 ));
					rcv = (Receiver) c.newInstance();
					rcv.setAnchor( anchor );
//					rcv.setSize( new Dimension2DDouble( 0.5, 0.5 ));
					rcv.setName( BasicSessionCollection.createUniqueName( Session.SO_NAME_PTRN,
						new Object[] { new Integer( 1 ), Session.RCV_NAME_PREFIX, Session.RCV_NAME_SUFFIX },
						coll2 ));
					doc.getReceivers().getMap().copyContexts( this, MapManager.Context.FLAG_DYNAMIC,
														 MapManager.Context.NONE_EXCLUSIVE, rcv.getMap() );
					collNewRcv.add( rcv );
					coll2.add( rcv );
				}
				final BasicCompoundEdit edit = new BasicCompoundEdit( getValue( NAME ).toString() );
				if( !doc.getSelectedGroups().isEmpty() ) {
					final List selectedGroups = doc.getSelectedGroups().getAll();
					for( int i = 0; i < collNewRcv.size(); i++ ) {
						final GroupableSessionObject so = (GroupableSessionObject) collNewRcv.get( i );
						edit.addPerform( new EditAddSessionObjects( this, so.getGroups(), selectedGroups ));
					}
				}
				edit.addPerform( new EditAddSessionObjects( this, doc.getMutableReceivers(), collNewRcv ));
				edit.addPerform( new EditAddSessionObjects( this, doc.getMutableSelectedReceivers(), collNewRcv ));
//				for( int i = 0; i < doc.getSelectedGroups().size(); i++ ) {
//					group	= (SessionGroup) doc.getSelectedGroups().get( i );
//					edit.addPerform( new EditAddSessionObjects( this, group.getReceivers(), coll ));
//				}
				edit.perform();
				edit.end();
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
			String					name;
			boolean					b1;
			int						result;
			List					collSO;
			SessionGroup			group;
			AbstractCompoundEdit	ce;

			try {
				doc.bird.waitShared( Session.DOOR_TRNS | Session.DOOR_RCV );
				b1 = doc.getSelectedReceivers().isEmpty() && doc.getSelectedTransmitters().isEmpty();
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
				name = BasicSessionCollection.createUniqueName( Session.SO_NAME_PTRN,
					new Object[] { new Integer( 1 ), Session.GRP_NAME_PREFIX, Session.GRP_NAME_SUFFIX },
					doc.getGroups().getAll() );
			} finally {
				doc.bird.releaseShared( Session.DOOR_GRP );
			}
			name = JOptionPane.showInputDialog( null, AbstractApplication.getApplication().getResourceString(
				"inputDlgNewGroup" ), name );
				
			if( name == null ) return;
			
			ce = new BasicCompoundEdit( getValue( NAME ).toString() );
			
			try {
				doc.bird.waitExclusive( Session.DOOR_GRP );
				group	= (SessionGroup) doc.getGroups().findByName( name );
				b1		= group == null;
				if( !b1 ) {
					result = JOptionPane.showConfirmDialog( null,
						AbstractApplication.getApplication().getResourceString(
						"optionDlgOverwriteGroup" ), getValue( Action.NAME ).toString(),
						JOptionPane.YES_NO_OPTION );
						
					if( result != JOptionPane.YES_OPTION ) return;
				} else {
					group = new BasicSessionGroup( doc );
					group.setName( name );
					doc.getGroups().getMap().copyContexts( this, MapManager.Context.FLAG_DYNAMIC,
													  MapManager.Context.NONE_EXCLUSIVE, group.getMap() );
				}
				if( !doc.bird.attemptShared( Session.DOOR_TRNS | Session.DOOR_RCV, 250 )) return;
				try {
					collSO		= doc.getSelectedReceivers().getAll();
					final List collGroup = Collections.singletonList( group );
					for( int i = 0; i < collSO.size(); i++ ) {
						final GroupableSessionObject so = (GroupableSessionObject) collSO.get( i );
						ce.addPerform( new EditAddSessionObjects( this, so.getGroups(), collGroup ));
					}
//					ce.addEdit( new EditAddSessionObjects( this, group.getReceivers(), collSO ));
					collSO		= doc.getSelectedTransmitters().getAll();
					for( int i = 0; i < collSO.size(); i++ ) {
						final GroupableSessionObject so = (GroupableSessionObject) collSO.get( i );
						ce.addPerform( new EditAddSessionObjects( this, so.getGroups(), collGroup ));
					}
//					ce.addEdit( new EditAddSessionObjects( this, group.getTransmitters(), collSO ));

					if( b1 ) {
						ce.addPerform( new EditAddSessionObjects( this, doc.getMutableGroups(), collGroup ));
					}
					ce.perform();
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
	
	// action for the Open-Session menu item
	private class ActionOpen
	extends MenuAction
	implements ProcessingThread.Listener
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
			perform();
		}
		
		protected void perform()
		{
			final Flag				confirmed	= new Flag( false );
			final DocumentFrame		frame		= (DocumentFrame) getApplication().getComponent( Main.COMP_MAIN );
			final ProcessingThread	pt			= frame.confirmUnsaved( text, confirmed );
			if( pt == null ) {
				if( !confirmed.isSet() ) return;
				queryAndPerform();
			} else {
				pt.addListener( new ProcessingThread.Listener() {
					public void processStarted( ProcessingThread.Event e ) {}
					public void processStopped( ProcessingThread.Event e ) {
						if( e.getProcessingThread().getReturnCode() == ProgressComponent.DONE ) {
							queryAndPerform();
						}
					}
				});
				pt.start();
			}
		}
		
		private void queryAndPerform()
		{
			File f = queryFile();
			if( f != null ) {
				perform( f );
			}
		}
		
		private void perform( File f )
		{
			final ProcessingThread pt = doc.initiateLoad( f );
			pt.addListener( this );
			pt.start();
		}

		private File queryFile()
		{
			FileDialog  fDlg;
			String		strFile, strDir;
//			Frame		frame = (Frame) getApplication().getComponent( Main.COMP_MAIN );
			final Frame	frame	= new Frame();

			fDlg	= new FileDialog( frame, AbstractApplication.getApplication().getResourceString(
				"fileDlgOpen" ), FileDialog.LOAD );
			fDlg.setFilenameFilter( doc );
			// fDlg.setDirectory();
			// fDlg.setFile();
			fDlg.show();
			frame.dispose();
			strDir	= fDlg.getDirectory();
			strFile	= fDlg.getFile();
			
			if( strFile == null ) return null;   // means the dialog was cancelled

			return( new File( strDir, strFile ));
		}
		
		public void processStopped( ProcessingThread.Event e )
		{
			final ProcessingThread pt = e.getProcessingThread(); 
			if( pt.getReturnCode() == ProgressComponent.DONE ) {
				final Map options = (Map) pt.getClientArg( "options" ); 
				addRecent( (File) options.get( "file" ));
// EEE
//				if( AbstractApplication.getApplication().getUserPrefs().getBoolean(
//					PrefsUtil.KEY_RECALLFRAMES, false )) {
//					BasicFrame.restoreAllFromPrefs();
//				}
				final Object warn = options.get( XMLRepresentation.KEY_WARNING );
				if( warn != null ) {
//					final MainFrame mf = (MainFrame) getApplication().getComponent( Main.COMP_MAIN );
					JOptionPane.showMessageDialog( null, warn, getValue( Action.NAME ).toString(),
												   JOptionPane.WARNING_MESSAGE );
				}
				((MainFrame) AbstractApplication.getApplication().getComponent( Main.COMP_MAIN )).updateTitle();
			}
		}
		
		public void processStarted( ProcessingThread.Event e ) { /* nada */ }
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
	implements ProcessingThread.Client
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
			String			result;
			int				num = 0;
		
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

			final List				collTypes		= Main.getTransmitterTypes();
			final List				collMap;
			final Span				span;
			final List				collNewTrns		= new ArrayList( num );
			final List				collAllTrns		= doc.getTransmitters().getAll();
			final BasicCompoundEdit	edit;
			Class		c;
			Transmitter	trns;
			String		s;
			Map			map;
			
			edit = new BasicCompoundEdit( getValue( NAME ).toString() );
			
			collMap = new ArrayList( num );
			for( int i = 0; i < num; i++ ) {
				collMap.add( collTypes.get( 0 ));
			}

			span = new Span( 0, doc.timeline.getLength() );
			try {
				for( int i = 0; i < num; i++ ) {
					map		= (Map) collMap.get( i );
					s		= (String) map.get( Main.KEY_CLASSNAME );
					if( s == null ) continue;

					c		= Class.forName( s );
					trns	= (Transmitter) c.newInstance();
					trns.setName( BasicSessionCollection.createUniqueName( Session.SO_NAME_PTRN,
						new Object[] { new Integer( 1 ), Session.TRNS_NAME_PREFIX, Session.TRNS_NAME_SUFFIX },
						collAllTrns ));
					doc.getTransmitters().getMap().copyContexts( this, MapManager.Context.FLAG_DYNAMIC,
															MapManager.Context.NONE_EXCLUSIVE, trns.getMap() );
					collNewTrns.add( trns );
					collAllTrns.add( trns );
					
//					trns.getAudioTrail().editBegin( edit );
				}
			}
			catch( InstantiationException e1 ) {
				BasicWindowHandler.showErrorDialog( null, e1, getValue( NAME ).toString() );
			}
			catch( IllegalAccessException e1 ) {
				BasicWindowHandler.showErrorDialog( null, e1, getValue( NAME ).toString() );
			}
			catch( LinkageError e1 ) {
				BasicWindowHandler.showErrorDialog( null, e1, getValue( NAME ).toString() );
			}
			catch( ClassNotFoundException e1 ) {
				BasicWindowHandler.showErrorDialog( null, e1, getValue( NAME ).toString() );
			}

			final Main root = (Main) AbstractApplication.getApplication();
//			new ProcessingThread( this, root, root, doc, text, coll, Session.DOOR_TIMETRNSMTE | Session.DOOR_GRP );
			final ProcessingThread pt;
			pt = new ProcessingThread( this, root, text );
			pt.putClientArg( "trns", collNewTrns );
			pt.putClientArg( "span", span );
			pt.putClientArg( "edit", edit );
			pt.start();
		}

		/**
		 *  @synchronization	waitExclusive on DOOR_TIMETRNSMTE + DOOR_GRP
		 */
		public int processRun( ProcessingThread context )
		throws IOException
		{
			final float[][]			buf			= new float[ 2 ][ 4096 ];
			final BasicCompoundEdit	edit		= (BasicCompoundEdit) context.getClientArg( "edit" );
			final List				collNewTrns	= (List) context.getClientArg( "trns" );
			final int				num			= collNewTrns.size();
			Transmitter				trns;
			AudioTrail				at;
			long					progress	= 0;
			double					d1;
			Span					span		= (Span) context.getClientArg( "span" );
//			TrackSpan				ts;
			float					f1, f2;
			AudioStake				as;
			int						chunkLen;
			
			if( span.isEmpty() ) return DONE;
			
			try {
				for( int i = 0; i < num; i++ ) {
					d1		= ((double) i / (double) num - 0.25) * Math.PI * -2;
//					f1		= (float) (0.25 * (2.0 + Math.cos( d1 )));
//					f2		= (float) (0.25 * (2.0 + Math.sin( d1 )));
					f1		= (float) (0.5 * Math.cos( d1 ));
					f2		= (float) (0.5 * Math.sin( d1 ));
					for( int j = 0; j < 4096; j++ ) {
						buf[0][j] = f1;
						buf[1][j] = f2;
					}
					trns	= (Transmitter) collNewTrns.get( i );
					at		= trns.getAudioTrail();
//					ts		= at.beginInsert( span, edit );
					as		= at.alloc( span );
					for( long framesWritten = 0, frames = span.getLength(); framesWritten < frames; ) {
						chunkLen = (int) Math.min( 4096, frames - framesWritten );
//						at.continueWrite( ts, buf, 0, j );
						as.writeFrames( buf, 0, new Span( span.start + framesWritten, span.start + framesWritten + chunkLen ));
						framesWritten += chunkLen;
					}
//					stakes.add( as );
					at.editBegin( edit );
					try {
						at.editAdd( this, as, edit ); // EEE should undy the stake alloc!!!
					} finally {
						at.editEnd( edit );
					}
//					at.finishWrite( ts, edit );
					progress++;
					context.setProgression( (float) progress / (float) num );
				}

				return DONE;
			}
			catch( IOException e1 ) {
				context.setException( e1 );
				return FAILED;
			}
		} // run()
		
		public void processFinished( ProcessingThread context )
		{
			final BasicCompoundEdit edit = (BasicCompoundEdit) context.getClientArg( "edit" );

			if( context.getReturnCode() == ProgressComponent.DONE ) {
				final List collNewTrns	= (List) context.getClientArg( "trns" );
//				final List stakes		= (List) context.getClientArg( "stakes" );
//				for( int i = 0; i < collNewTrns.size(); i++ ) {
//					final AudioTrail at = ((Transmitter) collNewTrns.get( i )).getAudioTrail();
//					.editAdd(
//					    this, (AudioStake) stakes.get( i ), edit );
//				}
				final List selectedGroups = doc.getSelectedGroups().getAll();
				if( !selectedGroups.isEmpty() ) {
					for( int i = 0; i < collNewTrns.size(); i++ ) {
						final GroupableSessionObject so = (GroupableSessionObject) collNewTrns.get( i );
						edit.addPerform( new EditAddSessionObjects( this, so.getGroups(), selectedGroups ));
					}
				}
				edit.addPerform( new EditAddSessionObjects( this, doc.getMutableTracks(), collNewTrns ));
				edit.addPerform( new EditAddSessionObjects( this, doc.getMutableSelectedTracks(), collNewTrns ));
//				for( int i = 0; i < doc.getSelectedGroups().size(); i++ ) {
//					final SessionGroup group = (SessionGroup) doc.getSelectedGroups().get( i );
//					edit.addPerform( new EditAddSessionObjects( this, group.getTransmitters(), collNewTrns ));
//				}
				edit.perform();
				edit.end();
				doc.getUndoManager().addEdit( edit );
			} else {
				// EEE should undo the stake alloc!!!
				edit.cancel();
			}
		}
		
		public void processCancel( ProcessingThread context ) {}
	}
	
	// action for Remove-Selected-Transmitters/Groups menu item
	public class ActionRemoveSessionObject
	extends MenuAction
	{
		private final MutableSessionCollection	scAll;
		private final MutableSessionCollection	scSelAll;
		private final SessionCollection			scSel;
//		private final SessionCollection			scGroups;
	
		private ActionRemoveSessionObject( String text, KeyStroke shortcut,
										   MutableSessionCollection scAll,
										   MutableSessionCollection scSelAll,
										   SessionCollection scSel )
		{
			super( text, shortcut );
			
//			this.doors		= doors;
			this.scAll		= scAll;
			this.scSelAll	= scSelAll;
			this.scSel		= scSel;
//			this.scGroups	= scGroups;
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
			List					collSelection;
			AbstractCompoundEdit	edit;
//			SessionGroup			g;

//			try {
//				doc.bird.waitExclusive( doors );
				collSelection		= scSel.getAll();
				edit				= new BasicCompoundEdit( getValue( NAME ).toString() );
//				if( scGroups != null ) {
//					for( int i = 0; i < scGroups.size(); i++ ) {
//						g			= (SessionGroup) scGroups.get( i );
//						collInGroup	= g.getTransmitters().getAll();
//						collInGroup.retainAll( collSelection );
//						if( !collInGroup.isEmpty() ) {
//							edit.addPerform( new EditRemoveSessionObjects( this, doc, g.getTransmitters(), collInGroup,
//							                                               doors | Session.DOOR_GRP ));
//						} else {
//							collInGroup	= g.getReceivers().getAll();
//							collInGroup.retainAll( collSelection );
//							if( !collInGroup.isEmpty() ) {
//								edit.addPerform( new EditRemoveSessionObjects( this, doc, g.getReceivers(), collInGroup,
//								                                               doors | Session.DOOR_GRP ));
////							} else {
////								collInGroup	= g.groups.getAll().retainAll( collSelection );
////								if( !collInGroup.isEmpty() ) {
////									edit.addEdit( new EditRemoveSessionObjects( this, doc, g.transmitters, collInGroup,
////																				doors | Session.DOOR_GRP ));
////								}
//							}
//						}
//					}
//				}
				edit.addPerform( new EditRemoveSessionObjects( this, scSelAll, collSelection ));
				edit.addPerform( new EditRemoveSessionObjects( this, scAll, collSelection ));
				edit.perform();
				edit.end();
				doc.getUndoManager().addEdit( edit );
//			}
//			finally {
//				doc.bird.releaseExclusive( doors );
//			}
		}
	}

//	// action for Insert-Time-Span menu item
//	private class ActionInsTimeSpan
//	extends MenuAction
//	implements ProcessingThread.Client
//	{
//		private double defaultValue = 1.0;
//		private String text;
//	
//		private ActionInsTimeSpan( String text, KeyStroke shortcut )
//		{
//			super( text, shortcut );
//			
//			this.text = text;
//		}
//
//		/**
//		 *  Insert time span into all tracks.
//		 *  Queries the amount of time from the user
//		 *  and starts a ProcessingThread that will
//		 *  update the trajectory files.
//		 */
//		public void actionPerformed( ActionEvent e )
//		{
//			String	result;
//			double	length		= 0.0;
//			long	start, stop;
//
//			result  = JOptionPane.showInputDialog( null,
//				AbstractApplication.getApplication().getResourceString( "inputDlgInsTimeSpan" ),
//				String.valueOf( defaultValue ));  // XXX localized number?
//				
//			if( result == null ) return;
//			try {
//				length = Double.parseDouble( result );
//			}
//			catch( NumberFormatException e1 ) {
//				System.err.println( e1.getLocalizedMessage() );
//			}
//			
//			try {
//				doc.bird.waitShared( Session.DOOR_TIME );
//				start   = doc.timeline.getPosition();
//				stop    = start + (long) (doc.timeline.getRate() * length + 0.5);
//				if( stop <= start ) return;
//				defaultValue = length;
//			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_TIME );
//			}
//
//			final Main root = (Main) AbstractApplication.getApplication();
////			new ProcessingThread( this, root, root, doc, text, new Span( start, stop ), Session.DOOR_TIMETRNSMTE );
//			final ProcessingThread pt;
//			pt = new ProcessingThread( this, root, text );
//			pt.putClientArg( "span", new Span( start, stop ));
//			pt.start();
//		}
//
//		/**
//		 *  Insert new data into the trajectory files.
//		 *  If the timeline was empty, the tractories of
//		 *  the selected transmitters are placed in a circle.
//		 *  Otherwise the preceeding and succeeding frame
//		 *  is linearly interpolated.
//		 *  
//		 *  @synchronization	waitExclusive on DOOR_TIMETRNSMTE
//		 */
//		public int processRun( ProcessingThread context )
//		throws IOException
//		{
//			Transmitter						trns;
//			AudioTrail						at;
//			float[][]						frameBuf	= new float[2][4096];
//			float[][]						interpBuf   = null;
//			float[]							chBuf;
//			int								i, j, ch, len, interpType;
//			float							f1, f2, interpWeight;
//			double							d1;
//			Span							visibleSpan, interpSpan;
//			Span							span		= (Span) context.getClientArg( "span" );
////			TrackSpan						ts;
//			long							start, interpOff, interpLen;
//			long							progress	= 0;
//			long							progressLen;
//			boolean							success		= false;
//			CompoundSessionObjEdit			edit;
//			AudioStake						as;
//
//			if( span.getStart() > doc.timeline.getLength() ) return DONE;
//			
//			interpWeight= 1.0f / (float) (span.getLength() + 1); // +1 because the linear interpolation excludes the neighbouring samples
//			visibleSpan = doc.timeline.getVisibleSpan();
//			// try to linearly interpolate between sample just before the timeline position
//			// and the old sample at the timeline position; if there are no samples for
//			// interpolation (this happens at the session start or session en), then
//			// just repeat the nearest neighbour.
//			interpSpan  = new Span( Math.max( 0, span.getStart() - 1 ),
//									Math.min( doc.timeline.getLength(), span.getStart() + 1 ));
//			interpLen   = span.getLength();
//			interpType  = (int) Math.min( 2, interpSpan.getLength() );
//			if( interpType > 1 ) {
//				interpBuf   = new float[2][(int) Math.min( interpLen, 4096 )];
//			}
//			progressLen = Math.max( 1, interpLen ) * doc.getTransmitters().size();
//
//			edit = new CompoundSessionObjEdit( this, doc.getTransmitters().getAll(), Transmitter.OWNER_TRAJ,
//											   null, null, getValue( NAME ).toString() );
//			try {
//				for( i = 0; i < doc.getTransmitters().size(); i++ ) {
//					trns	= (Transmitter) doc.getTransmitters().get( i );
//					at		= trns.getAudioTrail();
//
//					switch( interpType ) {
//					case 1: // only one neighbouring sample -> repeat it
//					case 0: // session is empty -> fill with angular positions
//						if( interpType == 0 ) {
//							d1		= ((double) i / (double) doc.getTransmitters().size() - 0.25) * Math.PI * 2;
//							f1		= (float) (0.25 * (2.0 + Math.cos( d1 )));
//							f2		= (float) (0.25 * (2.0 + Math.sin( d1 )));
//						} else {
//							at.readFrames( frameBuf, 0, interpSpan );
////							at.read( interpSpan, frameBuf, 0 );
//							f1		= frameBuf[0][0];
//							f2		= frameBuf[1][0];
//						}
//						for( j = 0; j < 4096; j++ ) {
//							frameBuf[0][j] = f1;
//							frameBuf[1][j] = f2;
//						}
//						at			= trns.getAudioTrail();
////						at.copyRangeFrom( srcTrail, copySpan, insertPos, mode, source, ce, trackMap, bcPre, bcPost )
////						ts			= at.beginInsert( span, edit );
//						as			= at.alloc( span );
//						for( start = span.getStart(); start < span.getStop(); start += len ) {
//							len		= (int) Math.min( 4096, span.getStop() - start );
////							at.continueWrite( ts, frameBuf, 0, len );
//							as.writeFrames( frameBuf, 0, new Span( start, start + len ));
//						}
////						at.finishWrite( ts, edit );
//						at.editInsert( this, span, edit );
//						at.editAdd( this, as, edit );
//						progress++;
//						context.setProgression( (float) progress / (float) progressLen );
//						break;
//
//					case 2:	// two neighbouring samples -> interpolation
////						at.read( interpSpan, frameBuf, 0 );
//						at.readFrames( frameBuf, 0, interpSpan );
////						ts = at.beginInsert( span, edit );
//						as = at.alloc( span );
//						for( start = span.getStart(), interpOff = 1; start < span.getStop();
//							 start += len, interpOff += len ) {
//							 
//							len = (int) Math.min( 4096, span.getStop() - start );
//							for( ch = 0; ch < 2; ch++ ) {
//								f1		= frameBuf[ch][0];
//								f2		= (frameBuf[ch][1] - f1) * interpWeight;
//								chBuf   = interpBuf[ch];
//								for( j = 0; j < len; j++ ) {
//									chBuf[j] = (float) (interpOff + j) * f2 + f1;
//								}
//							}
////							at.continueWrite( ts, interpBuf, 0, len );
//							as.writeFrames( interpBuf, 0, new Span( start, start + len ));
//							progress += len;
//							context.setProgression( (float) progress / (float) progressLen );
//						}
////						at.finishWrite( ts, edit );
//						at.editInsert( this, span, edit );
//						at.editAdd( this, as, edit );
//						break;
//					
//					default:
//						assert false : interpType;
//					} // switch( interpType )
//				} // for( i = 0; i < doc.transmitterCollection.size(); )
//
//				edit.addPerform( new EditInsertTimeSpan( this, doc, span ));
//				if( visibleSpan.isEmpty() ) {
//					edit.addPerform( TimelineVisualEdit.scroll( this, doc, span ));
//				} else if( visibleSpan.contains( span.getStart() )) {
//					edit.addPerform( TimelineVisualEdit.scroll( this, doc,
//						new Span( visibleSpan.getStart(), visibleSpan.getStop() + span.getLength() )));
//				}
//				
//				edit.perform();
//				edit.end(); // fires doc.tc.modified()
//				doc.getUndoManager().addEdit( edit );
//				success = true;
//			}
//			catch( IOException e1 ) {
//				edit.cancel();
//				context.setException( e1 );
//			}
//			
//			return success ? DONE : FAILED;
//		} // run()
//
//		public void processFinished( ProcessingThread context ) {}
//		public void processCancel( ProcessingThread context ) {}
//	} // class actionInsTimeSpanClass

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
			Span					span;
			AbstractCompoundEdit	edit;
		
			try {
				doc.bird.waitExclusive( Session.DOOR_TIME );
				span	= doc.timeline.getSelectionSpan();
				if( span.isEmpty() || span.getStop() == doc.timeline.getLength() ) return;
				span	= new Span( span.getStop() - 1, Math.min( doc.timeline.getLength(),
									span.getStop() - 1 + span.getLength() ));

				edit	= new BasicCompoundEdit( getValue( NAME ).toString() );
				edit.addPerform( TimelineVisualEdit.select( this, doc, span ));
				edit.addPerform( TimelineVisualEdit.position( this, doc, span.getStart() ));
				edit.perform();
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
			Span					span;
			AbstractCompoundEdit	edit;
		
			try {
				doc.bird.waitExclusive( Session.DOOR_TIME );
				span	= doc.timeline.getSelectionSpan();
				if( span.isEmpty() || span.getStart() == 0 ) return;
				span	= new Span( Math.max( 0, span.getStart() + 1 - span.getLength() ), span.getStart() + 1 );

				edit	= new BasicCompoundEdit( getValue( NAME ).toString() );
				edit.addPerform( TimelineVisualEdit.select( this, doc, span ));
				edit.addPerform( TimelineVisualEdit.position( this, doc, span.getStart() ));
				edit.perform();
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
			doc.getTransmitters().debugDump();
			System.err.println( "---------- all receivers ----------" );
			doc.getReceivers().debugDump();
			System.err.println( "---------- all groups ----------" );
			doc.getGroups().debugDump();
			System.err.println( "---------- active transmitters ----------" );
			doc.getActiveTransmitters().debugDump();
			System.err.println( "---------- active receivers ----------" );
			doc.getActiveReceivers().debugDump();
			for( int i = 0; i < doc.getGroups().size(); i++ ) {
				SessionGroup g = (SessionGroup) doc.getGroups().get( i );
				System.err.println( "............ group : "+g.getName() );
				System.err.println( "............ group transmitters ............" );
				g.getTransmitters().debugDump();
				System.err.println( "............ group receivers ............" );
				g.getReceivers().debugDump();
			}
		}
	}
	
	// action for the Observer menu item
	private class ActionObserver
	extends MenuAction
	{
		protected ActionObserver( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Brings up the IOSetup
		 */
		public void actionPerformed( ActionEvent e )
		{
			ObserverPalette f = (ObserverPalette) getApplication().getComponent( Main.COMP_OBSERVER );
		
			if( f == null ) {
				f = new ObserverPalette();	// automatically adds component
			}
			f.setVisible( true );
			f.toFront();
		}
	}
}