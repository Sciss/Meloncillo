/*
 *  TableLookupReceiverEditor.java
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
 *		04-Apr-05	created
 */

package de.sciss.meloncillo.receiver;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Locale;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.event.MouseInputAdapter;
import javax.swing.undo.UndoableEdit;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.MenuAction;
import de.sciss.gui.TopPainter;
import de.sciss.gui.VectorSpace;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.edit.EditTableLookupRcvSense;
import de.sciss.meloncillo.gui.Axis;
import de.sciss.meloncillo.gui.ObserverPalette;
import de.sciss.meloncillo.gui.PopupListener;
import de.sciss.meloncillo.gui.ToolBar;
import de.sciss.meloncillo.gui.VectorDisplay;
import de.sciss.meloncillo.gui.VectorEditor;
import de.sciss.meloncillo.gui.VectorEditorToolBar;
import de.sciss.meloncillo.math.MathUtil;
import de.sciss.meloncillo.math.VectorTransformer;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionCollection;
import de.sciss.meloncillo.surface.SurfaceFrame;
import de.sciss.meloncillo.util.PrefsUtil;

/**
 *  An editor suitable for <code>TableLookupReceiver</code>s.
 *  Boundary and name edits are left to the
 *  <code>ObserverPalette</code>. This simply
 *  provides two <code>VectorEditor</code>s for
 *  the distance and rotation table respectively.
 *  It provides a basic copy and paste functionality.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 19-Jun-08
 *
 *  @see		TableLookupReceiver
 *
 *  @todo		in case of a clipboard copy operation, the receiver should
 *				not be copied but just the tables.
 *	@todo		top painter for surface is showing wrong shapes
 */
public abstract class TableLookupReceiverEditor
extends AbstractReceiverEditor
implements  VectorDisplay.Listener, ClipboardOwner, TopPainter
{
	protected final VectorEditor	distanceEditor;
	protected final VectorEditor	rotationEditor;
	private final Axis				distHAxis, distVAxis, rotHAxis, rotVAxis;

	private final JPanel			padPanel1, padPanel2;

	// ---- cursor info ----
	private ObserverPalette				observer		= null;
	private final MouseInputAdapter		cursorListener;
	private final String[]				cursorInfo		= new String[ 2 ];
	private final Object[]				msgArgs			= new Object[ 3 ];
	private final MessageFormat			msgCursorDist;
	private final MessageFormat			msgCursorRot;
	private final MessageFormat			msgCursorSense;

	// ---- top painting ----
	private boolean						isPaintingOnTop = false;
	private SurfaceFrame				surface			= null;
	private Shape						shpTop			= null;
	private static final Color			colrAdjusting   = new Color( 0xFF, 0xFF, 0x00, 0x7F );

	public TableLookupReceiverEditor( Session doc )
	{
		super( doc );
		
		final Application	app	= AbstractApplication.getApplication();
		JPopupMenu			distancePopup, rotationPopup;
		Box					box;
		VectorSpace			distanceSpace, rotationSpace;

		msgCursorDist	= new MessageFormat( app.getResourceString( "rcvEditDistMsg" ), Locale.US );   // XXX
		msgCursorRot	= new MessageFormat( app.getResourceString( "rcvEditRotMsg" ), Locale.US );   // XXX
		msgCursorSense	= new MessageFormat( app.getResourceString( "rcvEditSenseMsg" ), Locale.US );   // XXX

		distanceSpace	= VectorSpace.createLinSpace( 0.0, 1.0, 0.0, 2.0,
									   app.getResourceString( "rcvEditDistance" ), null, null, null );

		distHAxis		= new Axis( Axis.HORIZONTAL, Axis.FIXEDBOUNDS );
		distHAxis.setSpace( distanceSpace );
		distVAxis		= new Axis( Axis.VERTICAL, Axis.FIXEDBOUNDS );
		distVAxis.setSpace( distanceSpace );
		box				= Box.createHorizontalBox();
		box.add( Box.createHorizontalStrut( distVAxis.getPreferredSize().width ));
		box.add( distHAxis );
		
		distanceEditor = new VectorEditor();
		distanceEditor.setSpace( null, distanceSpace );
		distancePopup  = VectorTransformer.createPopupMenu( distanceEditor );

		padPanel1 = new JPanel( new BorderLayout() );
		padPanel1.add( BorderLayout.CENTER, distanceEditor );
		padPanel1.add( box, BorderLayout.NORTH );
		padPanel1.add( distVAxis, BorderLayout.WEST );
//		padPanel1.setBorder( BorderFactory.createMatteBorder( 4, 4, 2, 4, Color.white ));

		rotationSpace	= VectorSpace.createLinSpace( 0.0, 360.0, 0.0, 2.0,
									   app.getResourceString( "rcvEditRotation" ), null, null, null );

		rotHAxis		= new Axis( Axis.HORIZONTAL, Axis.FIXEDBOUNDS );
		rotHAxis.setSpace( rotationSpace );
		rotVAxis		= new Axis( Axis.VERTICAL, Axis.FIXEDBOUNDS );
		rotVAxis.setSpace( rotationSpace );
		box				= Box.createHorizontalBox();
		box.add( Box.createHorizontalStrut( rotVAxis.getPreferredSize().width ));
		box.add( rotHAxis );

		rotationEditor = new VectorEditor();
		rotationEditor.setSpace( null, rotationSpace );
		rotationPopup  = VectorTransformer.createPopupMenu( rotationEditor );
		padPanel2 = new JPanel( new BorderLayout() );
		padPanel2.add( BorderLayout.CENTER, rotationEditor );
		padPanel2.add( box, BorderLayout.NORTH );
		padPanel2.add( rotVAxis, BorderLayout.WEST );
//		padPanel2.setBorder( BorderFactory.createMatteBorder( 2, 4, 4, 4, Color.white ));

        // --- Listener ---
		distanceEditor.addMouseListener( new PopupListener( distancePopup ));
		rotationEditor.addMouseListener( new PopupListener( rotationPopup ));

		cursorListener = new MouseInputAdapter() {
			public void mouseMoved( MouseEvent e )
			{
				handleMouseMotion( e );
			}

			public void mouseEntered( MouseEvent e )
			{
				handleMouseMotion( e );
				startPaintOnTop();
			}

			public void mouseExited( MouseEvent e )
			{
				stopPaintOnTop();
			}

			public void mouseDragged( MouseEvent e )
			{
				handleMouseMotion( e );
			}

			private void handleMouseMotion( MouseEvent e )
			{
				if( e.getSource() == distanceEditor ) {
					showCursorInfo( distanceEditor.screenToVirtual( e.getPoint() ), distanceEditor );
				} else if( e.getSource() == rotationEditor ) {
					showCursorInfo( rotationEditor.screenToVirtual( e.getPoint() ), rotationEditor );
				}
			}
		};

		distanceEditor.addMouseListener( cursorListener );
		rotationEditor.addMouseListener( cursorListener );
		distanceEditor.addMouseMotionListener( cursorListener );
		rotationEditor.addMouseMotionListener( cursorListener );

		distanceEditor.addListener( this );
		rotationEditor.addListener( this );
		
//		getRootPane().setPreferredSize( new Dimension( 320, 320 ));		// XXX
		distanceEditor.setPreferredSize( new Dimension( 320, 160 ));
		rotationEditor.setPreferredSize( new Dimension( 320, 160 ));
	}
	
	protected boolean alwaysPackSize()
	{
		return false;
	}

	/**
	 *  @synchronization	waitExclusive on DOOR_RCV
	 */
	public void init( Receiver rcv )
	{
		super.init( rcv );

		final ToolBar		vtb		= new VectorEditorToolBar();
		final Box			b		= Box.createHorizontalBox();
		final JPanel		gp		= GUIUtil.createGradientPanel();
		final Container		c		= getContentPane();
		c.setLayout( new BoxLayout( c, BoxLayout.Y_AXIS ));
		vtb.setOpaque( false );
		b.add( vtb );
		b.add( Box.createHorizontalGlue() );
		gp.add( b );
		c.add( gp );
		c.add( padPanel1 );
		c.add( padPanel2 );
        if( AbstractApplication.getApplication().getUserPrefs().getBoolean(
			PrefsUtil.KEY_INTRUDINGSIZE, false )) {
			
            c.add( Box.createVerticalStrut( 16 ));
        }
		vtb.addToolActionListener( distanceEditor );
		vtb.addToolActionListener( rotationEditor );

		updateSpaces();
		setVectors();
		observer	= (ObserverPalette) AbstractApplication.getApplication().getComponent( Main.COMP_OBSERVER );
		surface		= (SurfaceFrame) AbstractApplication.getApplication().getComponent( Main.COMP_SURFACE );

		init();	// !
	}
	
	/*
	 *  @synchronization	waitExclusive on DOOR_RCV
	 */
	private void setVectors()
	{
		float[]			tab, editTab;
		TableLookupReceiver   rcv = (TableLookupReceiver) this.rcv;
		
		if( rcv == null || doc == null ) return;
		
		try {
			doc.bird.waitExclusive( Session.DOOR_RCV );
			tab		= rcv.getDistanceTable();
			editTab = distanceEditor.getVector();
			if( tab.length == editTab.length ) {
				System.arraycopy( tab, 0, editTab, 0, tab.length );
			} else {
				editTab = (float[]) tab.clone();
			}
			distanceEditor.setVector( null, editTab );

			tab		= rcv.getRotationTable();
			editTab = rotationEditor.getVector();
			if( tab.length == editTab.length ) {
				System.arraycopy( tab, 0, editTab, 0, tab.length );
			} else {
				editTab = (float[]) tab.clone();
			}
			rotationEditor.setVector( null, editTab );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_RCV );
		}
	}

	private void showCursorInfo( Point2D pt, VectorEditor v )
	{
		float[]				tab		= v.getVector();
		int					x;
		boolean				isRot   = v == rotationEditor;
		TableLookupReceiver rcv		= (TableLookupReceiver) this.rcv;
		double				d		= v.getSpace().hUnityToSpace( pt.getX() );

		if( tab != null ) {
			x				= Math.max( 0, Math.min( tab.length - 1, (int) (d * tab.length + 0.5) ));
			msgArgs[ 0 ]	= new Double( d );
			msgArgs[ 1 ]	= new Double( tab[x] );
			msgArgs[ 2 ]	= new Double( MathUtil.linearToDB( tab[x] ));
			if( observer != null && observer.isVisible() ) {
				cursorInfo[ 0 ]  = isRot ? msgCursorRot.format( msgArgs ) : msgCursorDist.format( msgArgs );
				cursorInfo[ 1 ]  = msgCursorSense.format( msgArgs );
//System.err.println( "cursorInfo[0] = "+cursorInfo[0]+"; cursorInfo[1] = "+cursorInfo[1] );
				observer.showCursorInfo( cursorInfo );
			}
			if( surface != null && surface.isVisible() && rcv != null ) {
				if( isRot ) {
					shpTop	= getRotationShape( (d - 90.0) * Math.PI / 180 );
				} else {
					shpTop	= getDistanceShape( d );
				}
				surface.getSurfacePane().repaint();
			}
		}
	}
	
	/**
	 *	Requests a <code>TopPainter</code> <code>Shape</code>
	 *	object for a given angle.
	 *
	 *	@param	angle	the angle to be visualized, measured in degrees
	 *	@return	a shape to be displayed on the surface pane
	 */
	protected abstract Shape getRotationShape( double angle );

	/**
	 *	Requests a <code>TopPainter</code> <code>Shape</code>
	 *	object for a given angle.
	 *
	 *	@param	dist	the distance to be visualized
	 *	@return	a shape to be displayed on the surface pane
	 */
	protected abstract Shape getDistanceShape( double dist );

	protected abstract VectorSpace getRotationSpace( VectorSpace oldSpace );
	protected abstract VectorSpace getDistanceSpace( VectorSpace oldSpace );
	
	private void startPaintOnTop()
	{
		if( surface != null && surface.isVisible() ) {
			surface.getSurfacePane().addTopPainter( this );
			isPaintingOnTop = true;
		}
	}

	private void stopPaintOnTop()
	{
		if( isPaintingOnTop ) {
			surface.getSurfacePane().removeTopPainter( this );
			shpTop			= null;
			surface.getSurfacePane().repaint();
			isPaintingOnTop = false;
		}
	}

	/**
	 *  Removes listeners and
	 *  clears some references
	 */
	protected void receiverDied()
	{
		distanceEditor.removeMouseListener( cursorListener );
		rotationEditor.removeMouseListener( cursorListener );
		distanceEditor.removeMouseMotionListener( cursorListener );
		rotationEditor.removeMouseMotionListener( cursorListener );

		distanceEditor.removeListener( this );
		rotationEditor.removeListener( this );
		
		distanceEditor.setVector( null, new float[0] );
		rotationEditor.setVector( null, new float[0] );
	}

	private void updateSpaces()
	{
		VectorSpace spc;
		spc = getDistanceSpace( distanceEditor.getSpace() );
		distanceEditor.setSpace( this, spc );
		distHAxis.setSpace( spc );
		distVAxis.setSpace( spc );
		spc = getRotationSpace( rotationEditor.getSpace() );
		rotationEditor.setSpace( this, spc );
		rotHAxis.setSpace( spc );
		rotVAxis.setSpace( spc );
	}
	
// ---------------- TopPainter interface ---------------- 

	/**
	 *  Used to paint distance or rotation
	 *  hints onto the surface.
	 */
	public void paintOnTop( Graphics2D g2 )
	{
		if( shpTop != null ) {
			g2.setColor( colrAdjusting );
			g2.draw( shpTop );
		}
	}

// ---------------- SessionCollection.Listener interface ---------------- 

	/**
	 *  @synchronization	waitExclusive on DOOR_RCV
	 *  @todo				check if it's our receiver before update
	 */
	public void sessionObjectChanged( SessionCollection.Event e )
	{
		super.sessionObjectChanged( e );
		if( e.getSource() != this && e.collectionContains( rcv ) &&
			e.getModificationType() == Receiver.OWNER_SENSE ) {
			
			setVectors();
		}
	}

	public void sessionObjectMapChanged( SessionCollection.Event e )
	{
		super.sessionObjectMapChanged( e );
		
		if( e.collectionContains( rcv )) {
			String[] boundingKeys = ((TableLookupReceiver) rcv).getBoundingKeys();
			for( int i = 0; i < boundingKeys.length; i++ ) {
				// can't distinguish distance + rotation editor unforntunately
				if( e.setContains( boundingKeys[ i ])) {
					updateSpaces();
					return;
				}
			}
		}
	}

// ---------------- VectorDisplay.Listener interface ---------------- 

	/**
	 *  This is called when one of the tables
	 *  change. This updates the receiver's data
	 *  structure by generting an <code>EditTableLookupRcvSense</code>
	 *  object.
	 *
	 *  @see	de.sciss.meloncillo.edit.EditTableLookupRcvSense
	 */
	public void vectorChanged( VectorDisplay.Event e )
	{
		TableLookupReceiver	rcv		= (TableLookupReceiver) this.rcv;
		float[]				distTab = null;
		float[]				rotTab  = null;
		Span				distSpan= null;
		Span				rotSpan = null;
		UndoableEdit		edit;
		
		if( doc == null || rcv == null ) return;
		
		if( !doc.bird.attemptExclusive( Session.DOOR_RCV, 200 )) return;
		try {
			if( e.getSource() == distanceEditor ) {
				distTab = distanceEditor.getVector();
				distSpan= (Span) e.getActionObject();
			} else if( e.getSource() == rotationEditor ) {
				rotTab  = rotationEditor.getVector();
				rotSpan = (Span) e.getActionObject();
			} else {
				assert false : e.getSource();
			}
			edit = new EditTableLookupRcvSense( this, doc, rcv, distTab, distSpan,
																 rotTab, rotSpan );
			doc.getUndoManager().addEdit( edit );
		}
		catch( IOException e1 ) {
			System.err.println( e1.getLocalizedMessage() );
			setVectors();   // undo edits
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_RCV );
		}
	}

	public void vectorSpaceChanged( VectorDisplay.Event e ) {}

// ---------------- DocumentFrame abstract methods ----------------
	
	protected Action getCutAction() { return null; }
	protected Action getCopyAction() { return new ActionCopy(); }
	protected Action getPasteAction() { return new ActionPaste(); }
	protected Action getDeleteAction() { return null; }
	protected Action getSelectAllAction() { return null; }
	
// ---------------- EditMenuListener interface ---------------- 

	/*
	 *  Copies the receiver to the clipboard.
	 *
	 *  @see	Receiver#receiverFlavor
	 *
	 *  @todo   the receiver should
	 *			not be copied but just the tables.
	 */
	private void editCopy()
	{
		final TableLookupReceiver   rcv = (TableLookupReceiver) this.rcv;
		if( rcv == null || doc == null ) return;
		
		final de.sciss.app.Application	app = AbstractApplication.getApplication();

		try {
			doc.bird.waitShared( Session.DOOR_RCV );
//			Main.clipboard.setContents( new TableLookupReceiver( rcv ), this );
			app.getClipboard().setContents( (Transferable) rcv.getTransferData( Receiver.receiverFlavor ), this );
		}
		catch( IllegalStateException e1 ) {
			System.err.println( app.getResourceString( "errClipboard" ));
		}
		catch( UnsupportedFlavorException e2 ) {
			System.err.println( e2.getLocalizedMessage() );
		}
		catch( IOException e3 ) {
			GUIUtil.displayError( getWindow(), e3, app.getResourceString( "menuCopy" ));
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_RCV );
		}
	}
	
	/**
	 *  Copies the clipboard contents to the receiver's tables.
	 *  This will look for a <code>receiverFlavor</code> in the
	 *  clipboard. If it finds one, this receiver is queried as
	 *  transfer data. If the receiver is an instance of
	 *  <code>TableLookupReceiver</code>, it's tables are copied to
	 *  this receiver, using an <code>EditTableLookupRcvSense</code>.
	 *
	 *  @see	Receiver#receiverFlavor
	 *  @see	de.sciss.meloncillo.edit.EditTableLookupRcvSense
	 *
	 *  @todo   the receiver should
	 *			not be copied but just the tables.
	 */
	private void editPaste()
	{
		Transferable					t;
		Receiver						rcv;
		TableLookupReceiver				rcv2;
		UndoableEdit					edit;
		final de.sciss.app.Application	app = AbstractApplication.getApplication();
	
		try {   // sync ?
			t = app.getClipboard().getContents( this );
			if( t == null ) return;
			if( !t.isDataFlavorSupported( Receiver.receiverFlavor )) return;
			rcv		= (Receiver) t.getTransferData( Receiver.receiverFlavor );
			rcv2	= (TableLookupReceiver) this.rcv;
			if( rcv instanceof TableLookupReceiver && rcv2 != null ) {
				edit = new EditTableLookupRcvSense( this, doc, (TableLookupReceiver) rcv2,
								((TableLookupReceiver) rcv).getDistanceTable(), null,
								((TableLookupReceiver) rcv).getRotationTable(), null );
				doc.getUndoManager().addEdit( edit );
				setVectors();
			}
		}
		catch( IllegalStateException e1 ) {
			System.err.println( app.getResourceString( "errClipboard" ));
		}
		catch( UnsupportedFlavorException e2 ) {}
		catch( IOException e3 ) {
			System.err.println( e3.getLocalizedMessage() );
		}
	}

// ---------------- ClipboardOwner interface ---------------- 

	/**
	 *  @todo   check if things need to be disposed
	 */
	public void lostOwnership( Clipboard clipboard, Transferable contents )
	{
		// nothing
	}

	// ---------------- EditMenuListener interface ---------------- 

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
}