/*
 *  SurfaceFrame.java
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
 *		27-Jul-04   trace surface resizing and make sure it's square shaped
 *		01-Aug-04   commented
 *      24-Dec-04   support for intruding-grow-box prefs
 *      27-Dec-04   added online help
 *		15-Jan-05	moved to separate package
 *		17-Apr-05	bugfix in editClear
 */

package de.sciss.meloncillo.surface;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.app.Application;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.PerformableEdit;
import de.sciss.common.BasicApplication;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.MenuAction;
import de.sciss.gui.VectorSpace;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.edit.BasicCompoundEdit;
import de.sciss.meloncillo.edit.EditAddSessionObjects;
import de.sciss.meloncillo.edit.EditSetSessionObjects;
import de.sciss.meloncillo.gui.Axis;
import de.sciss.meloncillo.gui.MenuFactory;
import de.sciss.meloncillo.receiver.Receiver;
import de.sciss.meloncillo.session.DocumentFrame;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionCollection;
import de.sciss.meloncillo.session.SessionGroup;
import de.sciss.meloncillo.session.SessionGroupTable;
import de.sciss.meloncillo.util.PrefsUtil;
import de.sciss.meloncillo.util.TransferableCollection;

/**
 *  The frame that hosts the <code>SurfacePane</code>
 *  panel. Implements <code>EditMenuListener</code>
 *  to deal with cut / copy / paste operations.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 19-Jun-08
 */
public class SurfaceFrame
extends DocumentFrame
implements ClipboardOwner, PreferenceChangeListener
{
	private final SurfacePane	surface;
	
	private final Axis			haxis, vaxis;
	private final Box			haxisBox;

	/**
	 *  Constructs a new <code>SurfaceFrame</code>
	 *  object. A <code>Surface</code> will be
	 *  added to the frame's root pane.
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
	public SurfaceFrame( Main root, Session doc )
	{
		super( doc );
		
		final Container			cp		= getContentPane();
		final JSplitPane		split	= new JSplitPane( JSplitPane.HORIZONTAL_SPLIT );
		final JSplitPane		split2	= new JSplitPane( JSplitPane.VERTICAL_SPLIT );
		final JPanel			sp		= new JPanel( new BorderLayout() );
		final VectorSpace		space	= VectorSpace.createLinSpace( 0.0, 1.0, 0.0, 1.0, null, null, null, null );	// XXX
		final SurfaceToolBar	stb		= new SurfaceToolBar( root );
		final Application		app		= AbstractApplication.getApplication();
		final JPanel			gp		= GUIUtil.createGradientPanel();

		setTitle( app.getResourceString( "frameSurface" ));

		stb.setOpaque( false );
		gp.add( stb );
		haxisBox	= Box.createHorizontalBox();
		haxis		= new Axis( Axis.HORIZONTAL, Axis.FIXEDBOUNDS );
		vaxis		= new Axis( Axis.VERTICAL, Axis.MIRROIR | Axis.FIXEDBOUNDS );
		surface		= new SurfacePane( root, doc );
		surface.addComponentListener( new ComponentAdapter() {
			public void componentResized( ComponentEvent e )
			{
				Dimension d = surface.getSize();
				if( d.width != d.height ) {
					int diam = Math.min( d.width, d.height );
					surface.setSize( diam, diam );
				}
				haxis.setSize( d.width, haxis.getHeight() );
				vaxis.setSize( vaxis.getWidth(), d.height );
			}
		});
		stb.addToolActionListener( surface );

		cp.setLayout( new BorderLayout() );
//		split.setLeftComponent( new JScrollPane( new SessionGroupTable( root, doc, doc,
//			SessionGroupTable.VIEW_RECEIVERS )));
		split2.setTopComponent( new SessionGroupTable( doc, doc,
			SessionGroupTable.VIEW_RECEIVERS | SessionGroupTable.VIEW_FLAGS ));
		split2.setBottomComponent( new SessionGroupTable( doc, doc, SessionGroupTable.VIEW_GROUPS | SessionGroupTable.VIEW_FLAGS ));
//        HelpGlassPane.setHelp( split2, "SurfaceObjectTables" );	// EEE

		haxis.setSpace( space );
		vaxis.setSpace( space );
//		haxis.setVisible( false );
//		vaxis.setVisible( false );
//		strut.setVisible( false );
		haxisBox.add( Box.createHorizontalStrut( vaxis.getPreferredSize().width ));
		haxisBox.add( haxis );
		sp.add( haxisBox, BorderLayout.NORTH );
		sp.add( vaxis, BorderLayout.WEST );
		sp.add( surface, BorderLayout.CENTER );

		split.setLeftComponent( split2 );
		split.setRightComponent( sp );
		split.setOneTouchExpandable( true );
		split.setContinuousLayout( false );
//		split.setLastDividerLocation( 120 );
		split.setDividerLocation( 80 );		// this will be the size when using one-touch-expansion
		split.setDividerLocation( 0 );		// when showing up, the group component is hidden
//		split.setDividerSize( 4 );
		cp.add( split, BorderLayout.CENTER );
		cp.add( gp, BorderLayout.NORTH );
        if( app.getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
            cp.add( Box.createVerticalStrut( 16 ), BorderLayout.SOUTH );
        }

		// -------

//        addListener( new AbstractWindow.Adapter() {
//			public void windowClosing( AbstractWindow.Event e )
//			{
//				dispose();
//			}
//		});
//		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE ); // window listener see above!

//        HelpGlassPane.setHelp( getRootPane(), "SurfaceFrame" ); // EEE
		AbstractWindowHandler.setDeepFont( cp );
        
        addDynamicListening( new DynamicPrefChangeManager( app.getUserPrefs().node( PrefsUtil.NODE_SHARED ),
			new String[] { PrefsUtil.KEY_VIEWRULERS }, this ));
		
        init();
		app.addComponent( Main.COMP_SURFACE, this );
	}

	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_SURFACE );
		super.dispose();
	}

	protected boolean autoUpdatePrefs()
	{
		return true;
	}

	protected boolean alwaysPackSize()
	{
		return false;
	}

	protected Point2D getPreferredLocation()
	{
		return new Point2D.Float( 0.05f, 0.7f );
	}

	/*
	 *  Performs a copy operation
	 *  on the selected receivers
	 *
	 *  @synchronization	waitShared on DOOR_RCV
	 */
	private boolean editCopy()
	{
		final List			v		= new ArrayList();
		final Application	app		= AbstractApplication.getApplication();
		Object				o;
		Receiver			rcv;
		List				collSelection;
		boolean				success = false;

		try {
			doc.bird.waitShared( Session.DOOR_RCV );
			collSelection   = doc.selectedReceivers.getAll();
			for( int i = 0; i < collSelection.size(); i++ ) {
				o = collSelection.get( i );
				if( o instanceof Transferable ) {
					if( ((Transferable) o).isDataFlavorSupported( Receiver.receiverFlavor )) {
						rcv = (Receiver) ((Transferable) o).getTransferData( Receiver.receiverFlavor );
						v.add( rcv );
					}
				}
			}
			if( !v.isEmpty() ) {
				app.getClipboard().setContents( new TransferableCollection( v ), this );
			}
			success = true;
		}
		catch( IllegalStateException e1 ) {
			System.err.println( AbstractApplication.getApplication().getResourceString( "errClipboard" ));
		}
		catch( UnsupportedFlavorException e2 ) {}
		catch( IOException e3 ) {}
		finally {
			doc.bird.releaseShared( Session.DOOR_RCV );
		}

		return success;
	}

	/*
	 *  Tries to find transferable receivers in the clipboard
	 *  and paste those to the surface, automatically renaming
	 *  them and finding a good position on the surface. (undoable)
	 *
	 *  @synchronization	waitExclusive on DOOR_RCV + DOOR_GRP
	 */
	private boolean editPaste()
	{
		Transferable					t;
		java.util.List					coll, coll2, coll3;
		Receiver						rcv, rcv2;
		Transferable					o;
		Point2D							pt, pt2;
		boolean							success  = false;
		boolean							retry;
		AbstractCompoundEdit			edit;
		Object[]						args;
		SessionGroup					group;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
	
		try {
			t = app.getClipboard().getContents( this );
			if( t == null ) return false;
			
			if( t.isDataFlavorSupported( TransferableCollection.collectionFlavor )) {
				coll = (java.util.List) t.getTransferData( TransferableCollection.collectionFlavor );
			} else if( t.isDataFlavorSupported( Receiver.receiverFlavor )) {
				rcv = (Receiver ) t.getTransferData( Receiver.receiverFlavor );
				coll = new Vector( 1 );
				coll.add( rcv );
			} else {
				return false;
			}

			try {
				args = new Object[ 3 ];
				
				doc.bird.waitExclusive( Session.DOOR_RCV | Session.DOOR_GRP );
				coll2   = doc.getReceivers().getAll();
				coll3	= new ArrayList( coll2 );
				for( int i = 0; i < coll.size(); i++ ) {
					o = (Transferable) coll.get( i );
					if( o.isDataFlavorSupported( Receiver.receiverFlavor )) {
						rcv = (Receiver) o.getTransferData( Receiver.receiverFlavor );
						do {
							retry = false;
							for( int j = 0; j < coll3.size(); j++ ) {
								rcv2 = (Receiver) coll3.get( j );
								pt = rcv.getAnchor();
								if( pt.distance( rcv2.getAnchor() ) < 0.05 ) {
									pt2 = new Point2D.Double( Math.min( 1.0, pt.getX() + 0.05 ),
															  Math.min( 1.0, pt.getY() + 0.05 ));
									rcv.setAnchor( pt2 );
									if( pt2.distance( pt ) > 0.05 ) retry = true;
								}
							}
						} while( retry );
						coll3.add( rcv );
					}
				} // for( i = 0; i < coll.size(); i++ )
				coll3.removeAll( coll2 ); // now only the new ones remain

				// ensure unique names
				for( int i = 0; i < coll3.size(); i++ ) {
					rcv = (Receiver) coll3.get( i );
					if( doc.getReceivers().findByName( rcv.getName() ) != null ) {
						Session.makeNamePattern( rcv.getName(), args );
						rcv.setName( SessionCollection.createUniqueName( Session.SO_NAME_PTRN, args, coll2 ));
					}
					coll2.add( rcv );
				}
				
				if( !coll3.isEmpty() ) {
					edit = new BasicCompoundEdit();
					edit.addPerform( new EditAddSessionObjects( this, doc, doc.getReceivers(), coll3, Session.DOOR_RCV ));
					for( int i = 0; i < doc.selectedGroups.size(); i++ ) {
						group	= (SessionGroup) doc.selectedGroups.get( i );
						edit.addPerform( new EditAddSessionObjects( this, doc, group.getReceivers(), coll3, Session.DOOR_RCV ));
					}

					edit.addPerform( new EditSetSessionObjects( this, doc.selectedReceivers, coll3 ));
					edit.perform();
					edit.end();
					doc.getUndoManager().addEdit( edit );
				}
				
				success = true;
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_RCV | Session.DOOR_GRP );
			}
		}
		catch( IllegalStateException e1 ) {
			System.err.println( AbstractApplication.getApplication().getResourceString( "errClipboard" ));
		}
		catch( UnsupportedFlavorException e2 ) {}
		catch( IOException e3 ) {}

		return success;
	}

	/*
	 *  Deletes all selected receivers
	 *  from the surface (undoable)
	 *
	 *  @synchronization	waitExclusive on DOOR_RCV + DOOR_GRP
	 */
	private void editClear()
	{
		((MenuFactory) ((BasicApplication) AbstractApplication.getApplication()).getMenuFactory()).actionRemoveReceivers.perform();
//	
//		java.util.List  collSelection;
//		CompoundEdit	edit;
//		SessionGroup	group;
//		int				i;
//
//		try {
//			doc.bird.waitExclusive( Session.DOOR_RCV | Session.DOOR_GRP );
//			collSelection   = doc.selectedReceivers.getAll();
//			edit			= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_RCV | Session.DOOR_GRP );
//			edit.addEdit( new EditSetSessionObjects( this, doc, doc.selectedReceivers,
//															  new ArrayList( 1 ), Session.DOOR_RCV ));
//			for( i = 0; i < doc.groups.size(); i++ ) {
//				group	= (SessionGroup) doc.selectedGroups.get( i );
//				edit.addEdit( new EditRemoveSessionObjects( this, doc, group.receivers, collSelection, Session.DOOR_RCV ));
//			}
//			edit.addEdit( new EditRemoveSessionObjects( this, doc, doc.receivers,
//														collSelection, Session.DOOR_RCV ));
//			edit.end();
//			doc.getUndoManager().addEdit( edit );
//		}
//		finally {
//			doc.bird.releaseExclusive( Session.DOOR_RCV | Session.DOOR_GRP );
//		}
	}

	/*
	 *  Selects all receivers (undoable)
	 *
	 *  @synchronization	waitExclusive on DOOR_RCV
	 */
	private void editSelectAll()
	{
		final PerformableEdit edit;
	
		try {
			doc.bird.waitExclusive( Session.DOOR_RCV );
			edit = new EditSetSessionObjects( this, doc.selectedReceivers, doc.getReceivers().getAll() );
			doc.getUndoManager().addEdit( edit.perform() );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_RCV );
		}
	}

	/** 
	 *  Returns the surface panel
	 *
	 *  @return the surface which is attached to this frame
	 */
	public SurfacePane getSurfacePane()
	{
		return surface;
	}

// ---------------- LaterInvocationManager.Listener interface ---------------- 

	// o instanceof PreferenceChangeEvent
	public void preferenceChange( PreferenceChangeEvent pce )
	{
		String  key		= pce.getKey();
		String  value	= pce.getNewValue();
		boolean	b;

		if( key.equals( PrefsUtil.KEY_VIEWRULERS )) {
			b = Boolean.valueOf( value ).booleanValue();
			haxisBox.setVisible( b );
			vaxis.setVisible( b );
		}
	}

// ---------------- DocumentFrame abstract methods ----------------
	
	protected Action getCutAction() { return new ActionCut(); }
	protected Action getCopyAction() { return new ActionCopy(); }
	protected Action getPasteAction() { return new ActionPaste(); }
	protected Action getDeleteAction() { return new ActionDelete(); }
	protected Action getSelectAllAction() { return new ActionSelectAll(); }
	
// ---------------- EditMenuListener interface ---------------- 

	private class ActionCut
	extends MenuAction
	{
		protected ActionCut() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			if( editCopy() ) editClear();
		}
	}
	
	private class ActionCopy
	extends MenuAction
	{
		protected ActionCopy() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			editCopy();
		}
	}

	private class ActionPaste
	extends MenuAction
	{
		protected ActionPaste() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			editPaste();
		}
	}

	private class ActionDelete
	extends MenuAction
	{
		protected ActionDelete() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			editClear();
		}
	}

	private class ActionSelectAll
	extends MenuAction
	{
		protected ActionSelectAll() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			editSelectAll();
		}
	}

// ---------------- ClipboardOwner interface ---------------- 

	public void lostOwnership( Clipboard clipboard, Transferable contents )
	{
		// XXX evtl. dispose() aufrufen
	}
}