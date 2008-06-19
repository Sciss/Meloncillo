/*
 *  SurfacePane.java
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
 *		13-Jun-04   Removed SurfacePaneListener interface
 *		23-Jul-04   collTransmitterShapes/Path sync removed because it was superfluous
 *		27-Jul-04   is a RealtimeConsumer
 *		01-Aug-04   commented. added checkTrnsNames(); moved trns+rcv coll. listener
 *					registration from dyn.list to constructor.
 *		08-Aug-04   dynamic prefs listener, new view flags. freehand works.
 *		11-Aug-04   extends JComponent, not JPanel
 *		02-Sep-04	darker green colour for transmitters when not showing rcv sense
 *      27-Dec-04   added online help. opening receiver editor switches to observer cursor tab
 *		15-Jan-05	renamed to SurfacePane, moved to separate package
 *		02-Feb-05	pencil tool will automatically start/stop transport
 *		19-Mar-05	only active transmitters are displayed
 *		18-Apr-05	fixed arc tool
 *
 *  XXX TO-DO : dragging multiple receivers should collapse into a compound edit!
 */

package de.sciss.meloncillo.surface;

import java.awt.*;
import java.awt.color.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.*;
import java.util.*;
import java.util.prefs.*;

import javax.swing.*;
import javax.swing.undo.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.io.*;
import de.sciss.meloncillo.math.*;
import de.sciss.meloncillo.realtime.*;
import de.sciss.meloncillo.receiver.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.transmitter.*;
import de.sciss.meloncillo.timeline.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.common.ProcessingThread;
import de.sciss.gui.*;
import de.sciss.io.*;

/**
 *  The <code>SurfacePane</code> is one of the core GUI
 *  elements of Meloncillo : a two dimensional
 *  planar display of the receiver distribution
 *  and a momentary display of transmitter locations.
 *  <p>
 *  It implements the <code>VirtualSurfacePane</code>
 *  interface which describes the transition from
 *  screen space to a normalized virtual space.
 *  It is also a <code>RealtimeConsumer</code> which
 *  tracks the movement of the transmitters in time.
 *  It implements the <code>ToolActionListener</code>
 *  interface and has a internal classes for different
 *  geometric and other tools.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @todo		opening multiple editor windows for the
 *				same receiver should be avoided
 */
public class SurfacePane
extends JComponent		// JPanel
implements  VirtualSurface, TimelineListener,
			ToolActionListener, DynamicListening, RealtimeConsumer, PreferenceChangeListener
{
    // --- global communication ---

	private final Main		root;
	private final Session	doc;
	private final Transport transport;

    // --- shapes and paints ---
        
	/**
	 *  Stroke used to paint Receiver outlines
	 *  and transmitter paths
	 */
	protected static final Stroke	strkDottedLine;
	/**
	 *  Stroke to paint solid lines
	 */
	protected static final Stroke	strkLine;
	/**
	 *  Shape for painting the anchors
	 *  of Receivers and current Transmitter positions
	 */
	protected static final Shape	shpCrossHair;
	/**
	 *  Color for selected objects and lines
	 */
	protected static final	Color	colrSelection   = GraphicsUtil.colrSelection;
	/**
	 *  Color for selected text
	 */
	protected static final	Color	colrTextSelection = Color.lightGray;
	
//	private static final	Color	colrAdjusting   = GraphicsUtil.colrAdjusting;
	private static final	Color	colrReceiver    = new Color( 0xFF, 0x00, 0x00 );
	private static final	Color   colrTrnsLight	= new Color( 0x00, 0xFF, 0x00 );
	private static final	Color   colrTrnsDark	= new Color( 0x00, 0xAB, 0x00 );

	// XXX move to GraphicsUtil
    private static final	Font	fntLabel		= new Font( "Gill Sans", Font.ITALIC, 5 );

	private final BufferedImage bufImg;
	private final int bufImgExtent			= 256;  // XXX user prefs
	private final float[] bufImgRow			= new float[ bufImgExtent ];
	private final float[] bufImgRow2		= new float[ bufImgExtent ];
	private final float[][] bufImgPt		= new float[ 2 ][ bufImgExtent ];
	private Image				image		= null;
	private Dimension			recentSize;
	
	// --- cursor info ---
	private ObserverPalette				observer		= null;
	private final MouseMotionListener	cursorListener;
	private static final String			EMPTY_STR		= "";
	private final String[]				cursorInfo		= new String[] {
		EMPTY_STR, EMPTY_STR, EMPTY_STR, EMPTY_STR, EMPTY_STR
	};
	private final Object[]				msgArgs			= new Object[ 2 ];
	private static final MessageFormat  msgCursorX		= new MessageFormat( "X {0,number,0.0000}", Locale.US );   // XXX
	private static final MessageFormat  msgCursorY		= new MessageFormat( "Y {1,number,0.0000}", Locale.US );   // XXX
	
	// --- points and paths ---

    /*
     *  elements are GeneralPath objects with the transmitter
     *  trajectory for the current timeline selection
     */
    private final java.util.List	collTransmitterPath	= new ArrayList();
    /*
     *  elements are ReceiverShape objects with the receiver
     *  anchors and outlines
     */
    private final java.util.List	collReceiverShapes	= new ArrayList();
    /*
     *  elements are Image objects with the group's background images
     */
    private final java.util.List	collUserImages		= new ArrayList();
    
	// --- tools ---
	
	private final   Map				tools				= new HashMap();
	private			AbstractTool	activeTool			= null;

	// --- top painter ---

	private final	java.util.List	collTopPainters		= new ArrayList();
	
	// --- realtime ---
	private boolean		rt_valid		= false;			// false if rt shouldn't update transmitter locs
	private String[]	rt_trnsNames	= new String[0];	// names of all transmitters
	private float[]		rt_trnsLocX		= new float[0];		// x locations of all transmitters
	private float[]		rt_trnsLocY		= new float[0];		// y locations of all transmitters
	private int[]		rt_peak			= new int[0];		// maps active transmitter indices to context indices
	private long		rt_pos;

	// --- prefs ---
	private boolean prefSnap, prefRcvSense, prefRcvEqP, prefTrnsTraj, prefUserImages;

	private static final java.util.List grpKeys;
	
	static {
		// --- Strokes ---
		float[] dash	= { 5.0e-3f, 5.0e-3f };
		strkDottedLine  = new BasicStroke( 2.0e-3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL, 1.0f, dash, 0.0f );
		strkLine		= new BasicStroke( 2.0e-3f );
		// --- Shapes ---
		shpCrossHair	= new Area( new Rectangle2D.Float( -1.0e-2f, -1.0e-3f, 2.0e-2f, 2.0e-3f ));
		((Area) shpCrossHair).add( new Area( new Rectangle2D.Float( -1.0e-3f, -1.0e-2f, 2.0e-3f, 2.0e-2f )));

		grpKeys = new ArrayList( 1 );
		grpKeys.add( SessionGroup.MAP_KEY_USERIMAGE );
	}

	/**
	 *  Constructs a <code>SurfacePane</code>
	 *  object. Allocates memory for a 8-bit
	 *  grayscale image that displays the
	 *  receivers' sensibilities.
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
	public SurfacePane( Main root, final Session doc )
	{
		super();
		
		this.root   = root;
		this.doc	= doc;
		transport   = doc.getTransport();
		
		setPreferredSize( new Dimension( 480, 640 ));

		WritableRaster rast = Raster.createInterleavedRaster(
			DataBuffer.TYPE_BYTE, bufImgExtent, bufImgExtent, 1, new Point( 0, 0 ));
		ColorSpace colrSpace= ColorSpace.getInstance( ColorSpace.CS_GRAY );
		int[] cbits			= new int[1];
		cbits[0]			= 8;
		ColorModel cm		= new ComponentColorModel( colrSpace, cbits, false, false, Transparency.OPAQUE,
													   DataBuffer.TYPE_BYTE );
		bufImg				= new BufferedImage( cm, rast, false, new Hashtable() );
		recentSize			= getMinimumSize();
	
		// --- Tools ---
		
		tools.put( new Integer( ToolAction.POINTER ),	new SurfacePointerTool() );
		tools.put( new Integer( ToolAction.LINE ),		new SurfaceLineTool( root, doc, this ));
		tools.put( new Integer( ToolAction.CURVE ),		new SurfaceCurveTool( root, doc, this ));
		tools.put( new Integer( ToolAction.ARC ),		new SurfaceArcTool( root, doc, this ));
		tools.put( new Integer( ToolAction.PENCIL ),	new SurfacePencilTool( false ));
		tools.put( new Integer( ToolAction.FORK ),		new SurfacePencilTool( true ));

		// --- Listener ---
		
        new DynamicAncestorAdapter( this ).addTo( this );
        new DynamicAncestorAdapter( new DynamicPrefChangeManager(
			AbstractApplication.getApplication().getUserPrefs().node( PrefsUtil.NODE_SHARED ),
			new String[] { PrefsUtil.KEY_SNAP, PrefsUtil.KEY_VIEWRCVSENSE, PrefsUtil.KEY_VIEWEQPRECEIVER,
						   PrefsUtil.KEY_VIEWTRNSTRAJ, PrefsUtil.KEY_VIEWUSERIMAGES }, this )).addTo( this );
	
//		this.addMouseListener( new MouseAdapter() {
//			public void mousePressed( MouseEvent e )
//			{
//				requestFocus();
//			}
//		});

		final SurfacePane enc_this = this;
		
		cursorListener = new MouseMotionAdapter() {
			public void mouseMoved( MouseEvent e )
			{
				showCursorInfo( screenToVirtual( e.getPoint() ));
			}

			public void mouseDragged( MouseEvent e )
			{
				showCursorInfo( screenToVirtual( e.getPoint() ));
			}
		};
        
		doc.activeReceivers.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
//System.err.println( "activeReceivers sessionCollectionChanged!" );
				update( e );
			}
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.getSource() != enc_this && e.getSource() != activeTool ) {
					switch( e.getModificationType() ) {
					case SessionObject.OWNER_RENAMED:
						updateReceiverShapes();
						redrawImage();
						repaint();
						break;

					case Receiver.OWNER_SENSE:
						update( e );
						break;
					
					case SessionObject.OWNER_VISUAL:
						updateReceiverShapes();
						updateSurfacePaneImage( null );
						redrawImage();
						repaint();
						break;
					}
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
//			{
//				if( e.getSource() != enc_this && e.getSource() != activeTool &&
//					e.setContainsAny( rcvKeys )) {
//					
//					// cannot use 'update( e )' because we would need the previous union rect ;-( XXX
//					updateReceiverShapes();
//					updateSurfacePaneImage( null );
//					redrawImage();
//					repaint();
//				}
//			}
		});
		
		doc.selectedReceivers.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				if( e.getSource() != enc_this && e.getSource() != activeTool ) {
					updateReceiverShapes();
					redrawImage();
					repaint();
				}
			}
			
			public void sessionObjectChanged( SessionCollection.Event e ) {}
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		});
		
		doc.activeTransmitters.addListener( new SessionCollection.Listener() {
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.getModificationType() == Transmitter.OWNER_RENAMED ) {
					checkTrnsNames();			// XXX optimize!
					redrawImage();
					repaint();
				}
			}

			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				if( rt_valid ) {
					rt_valid = false;
					transport.removeRealtimeConsumer( enc_this );
					transport.addRealtimeConsumer( enc_this );
				}
			}
			
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		});

		doc.selectedTransmitters.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				if( prefTrnsTraj ) {
					checkSchoko( e );
				}
			}
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( prefTrnsTraj && e.getModificationType() == Transmitter.OWNER_TRAJ ) {
					checkSchoko( e );
				}
			}
			
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}

			private void checkSchoko( SessionCollection.Event e ) 
			{
				try {
					doc.bird.waitShared( Session.DOOR_TRNS | Session.DOOR_GRP );
					java.util.List coll = e.getCollection();
					coll.retainAll( doc.selectedTransmitters.getAll() );
					if( !coll.isEmpty() ) {
						updateTransmitterPath();	// XXX optimize
						redrawImage();				// XXX not if frame is hidden
						repaint();
					}
				}
				finally {
					doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_GRP );
				}
			}
		});

		doc.selectedGroups.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				if( prefUserImages ) {
//System.err.println( "B" );
					updateUserImages();
					redrawImage();
					repaint();
				}
			}
			
			public void sessionObjectChanged( SessionCollection.Event e ) {}

			public void sessionObjectMapChanged( SessionCollection.Event e )
			{
				if( e.setContainsAny( grpKeys )) {
					if( prefUserImages ) {
//System.err.println( "A" );
						updateUserImages();
						redrawImage();
						repaint();
					}
				}
			}
		});

		// -------
						
		setOpaque( true );
		setFocusable( true );		// required for the tools to hear key presses
		updateSurfacePaneImage( null );
//        HelpGlassPane.setHelp( this, "Surface" );	// EEE
    }
	
	/**
	 *  Registers a new top painter.
	 *  If the top painter wants to paint
	 *  a specific portion of the surface,
	 *  it must make an appropriate repaint call!
	 *
	 *  @param  p   the <code>TopPainter</code> to be added to
	 *				the painting queue
	 *
	 *  @synchronization	this method must be called in the event thread
	 */
	public void addTopPainter( TopPainter p )
	{
		if( !collTopPainters.contains( p )) {
			collTopPainters.add( p );
		}
	}

	/**
	 *  Removes a registered top painter.
	 *
	 *  @param  p   the <code>TopPainter</code> to be removed from
	 *				the painting queue
	 *
	 *  @synchronization	this method must be called in the event thread
	 */
	public void removeTopPainter( TopPainter p )
	{
		collTopPainters.remove( p );
	}
	
	/**
	 *  Recalculates the surface buffered image, e.g. after a receiver movement.
	 *
	 *  @param  clipRect	the part of the image that needs update
	 *						or null to update the complete image
	 *
	 *  @synchronization	attemptShared on DOOR_RCV | DOOR_GRP
	 *  @todo				increase calculation speed
	 */
	private void updateSurfacePaneImage( Rectangle2D clipRect )
	{
		if( !prefRcvSense ) return;
	
		WritableRaster  rast		= bufImg.getRaster();
		float			scaleDown	= 1.0f / (float) bufImgExtent;
		int				x, y, i, rcvIdx, x1, x2, y1, y2, numRcv;
		float			f1;
		java.util.List	collRcv;

		if( clipRect == null ) {
			x1  = 0;
			y1  = 0;
			x2  = bufImgExtent;
			y2  = bufImgExtent;
		} else {
			x1  = Math.max( 0, (int) (clipRect.getMinX() * bufImgExtent) );
			y1  = Math.max( 0, (int) (clipRect.getMinY() * bufImgExtent) );
			x2  = Math.min( bufImgExtent, (int) Math.ceil( clipRect.getMaxX() * bufImgExtent ));
			y2  = Math.min( bufImgExtent, (int) Math.ceil( clipRect.getMaxY() * bufImgExtent ));
		}

		// we calculate full rows at once, hence
		// we can precalc the x coords for all rows
		for( x = x1; x < x2; x++ ) {
			bufImgPt[ 0 ][ x ] = x * scaleDown;
		}

		if( !doc.bird.attemptShared( Session.DOOR_RCV | Session.DOOR_GRP, 250 )) return;
		try {
			collRcv	= doc.activeReceivers.getAll();
			numRcv	= collRcv.size();

			if( numRcv == 0 ) {
				for( i = x2 - x1 - 1; i >= 0; i-- ) {
					bufImgRow2[ i ] = 255f;
				}
				for( y = y1; y < y2; y++ ) {
					rast.setPixels( x1, y, x2 - x1, 1, bufImgRow2 );
				}
				return;
			}

			if( prefRcvEqP ) {		// ----------------------- equal power -----------------------
//System.err.println( "here! x1 "+x1+" x2 "+x2+" y1 "+y1+" y2 "+y2+" numRcv "+numRcv );
//System.err.println( "rcv 1 : anchor "+((Receiver) collRcv.get( 0 )).getAnchor().getX()+","+
//					((Receiver) collRcv.get( 0 )).getAnchor().getY()+" size "+
//					((Receiver) collRcv.get( 0 )).getSize().getWidth()+","+
//					((Receiver) collRcv.get( 0 )).getSize().getHeight() );

				for( y = y1; y < y2; y++ ) {
					f1 = y * scaleDown;
					// all points in a row share the same y coord
					for( x = x1; x < x2; x++ ) {
						bufImgPt[ 1 ][ x ] = f1;
					}

					
					((Receiver) collRcv.get( 0 )).getSensitivities(
						bufImgPt, bufImgRow, x1, x2, 1 );
					for( x = x1, i = 0; x < x2; x++, i++ ) {
						bufImgRow2[ i ] = bufImgRow[ x ] * bufImgRow[ x ];
					}
					for( rcvIdx = 1; rcvIdx < numRcv; rcvIdx++ ) {
						((Receiver) collRcv.get( rcvIdx )).getSensitivities(
							bufImgPt, bufImgRow, x1, x2, 1 );
						for( x = x1, i = 0; x < x2; x++, i++ ) {
							bufImgRow2[ i ] += bufImgRow[ x ] * bufImgRow[ x ];
						}
					}

					for( i = x2 - x1 - 1; i >= 0; i-- ) {
						f1 = bufImgRow2[ i ];
						if( f1 == 0.0f ) {
							bufImgRow2[ i ] = 255f;
						} else if( f1 >= 1.0f ) {
							bufImgRow2[ i ] = 0f;
						} else {
							bufImgRow2[ i ] = (float) ((1.0 - Math.sqrt( bufImgRow2[ i ])) * 255);
						}
					}
					rast.setPixels( x1, y, x2 - x1, 1, bufImgRow2 );
				}
			} else {		// ----------------------- linear ----------------------- 
				for( y = y1; y < y2; y++ ) {
					f1 = y * scaleDown;
					// all points in a row share the same y coord
					for( x = x1; x < x2; x++ ) {
						bufImgPt[ 1 ][ x ] = f1;
					}

					((Receiver) collRcv.get( 0 )).getSensitivities(
						bufImgPt, bufImgRow, x1, x2, 1 );
					for( x = x1, i = 0; x < x2; x++, i++ ) {
						bufImgRow2[ i ] = bufImgRow[ x ];
					}
					for( rcvIdx = 1; rcvIdx < numRcv; rcvIdx++ ) {
						((Receiver) collRcv.get( rcvIdx )).getSensitivities(
							bufImgPt, bufImgRow, x1, x2, 1 );
						for( x = x1, i = 0; x < x2; x++, i++ ) {
							bufImgRow2[ i ] += bufImgRow[ x ];
						}
					}

					for( i = x2 - x1 - 1; i >= 0; i-- ) {
						f1 = bufImgRow2[ i ];
						bufImgRow2[ i ] = (float) ((1.0 - Math.min( 1.0f, bufImgRow2[ i ])) * 255);
					}
					rast.setPixels( x1, y, x2 - x1, 1, bufImgRow2 );
				}
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_RCV | Session.DOOR_GRP );
		}
	}
	
	/**
	 *  Creates a new default Receiver located at the provided point
	 *
	 *  @param  anchor  initial position of the receiver
	 *  @return			the new receiver or null when the instantiation failed
	 *
	 *  @synchronization	waitShared on DOOR_RCV + DOOR_GRP
	 */
	private Receiver createReceiver( Point2D anchor)
	{
		Receiver		rcv			= null;
		java.util.List  collTypes	= Main.getReceiverTypes();
		java.util.List	collRcv		= new ArrayList( 1 );
		Class			c;
		UndoableEdit	edit;
		int				i;
		SessionGroup	group;

		try {
			doc.bird.waitExclusive( Session.DOOR_RCV | Session.DOOR_GRP );
			// we get a list of known receivers from the main class
			// and create a new instance of the first receiver class
			// in the list; in the future when there are more types
			// apart from SigmaReceiver, we could display a selection
			// dialog or the like...
			c   = Class.forName( (String) ((StringItem) collTypes.get( 0 )).getKey() );
			rcv = (Receiver) c.newInstance();
			rcv.setAnchor( anchor );
//			rcv.setSize( new Dimension2DDouble( 0.5, 0.5 ));
			rcv.setName( SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
				new Object[] { new Integer( 1 ), Session.RCV_NAME_PREFIX, Session.RCV_NAME_SUFFIX },
				doc.receivers.getAll() ));
			doc.receivers.getMap().copyContexts( this, MapManager.Context.FLAG_DYNAMIC,
												 MapManager.Context.NONE_EXCLUSIVE, rcv.getMap() );					
			collRcv.add( rcv );

			if( doc.selectedGroups.size() == 0 ) {
				edit	= new EditAddSessionObjects( this, doc, doc.receivers, collRcv, Session.DOOR_RCV );
			} else {
				edit	= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_RCV | Session.DOOR_GRP );
				edit.addEdit( new EditAddSessionObjects( this, doc, doc.receivers, collRcv, Session.DOOR_RCV ));
				for( i = 0; i < doc.selectedGroups.size(); i++ ) {
					group	= (SessionGroup) doc.selectedGroups.get( i );
					edit.addEdit( new EditAddSessionObjects( this, doc, group.receivers, collRcv, Session.DOOR_RCV ));
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
		
		return rcv;
	}

	/**
	 *  Custom paint method paints surface image and receiver/transmitter
	 *  anchors and outlines
	 */
	public void paintComponent( Graphics g )
	{
//		super.paintComponent( g );
		
		Dimension			d			= getSize();
		int					diam		= Math.min( d.width, d.height );
		Graphics2D			g2			= (Graphics2D) g;
		AffineTransform		trnsOrig	= g2.getTransform();
		AffineTransform		trnsRecent;
		Stroke				strkOrig	= g2.getStroke();
		int					i;
		
		if( (recentSize.width != d.width) || (recentSize.height != d.height) ) {
			recentSize = d;
			recreateImage();
			redrawImage();
		}
		if( image != null ) g.drawImage( image, 0, 0, this );

		g2.scale( diam, diam );		// virtual space has dimension 1.0 x 1.0
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
//		g2.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
//		g2.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
    	g2.setFont( fntLabel );
		g2.setStroke( strkDottedLine );

		trnsRecent = g2.getTransform();

		// --- transmitters ---
		if( rt_valid ) {
			g2.setColor( prefRcvSense ? colrTrnsLight : colrTrnsDark );
			for( i = rt_trnsNames.length - 1; i >= 0; i-- ) {
                g2.translate( rt_trnsLocX[ i ], rt_trnsLocY[ i ]);
                g2.fill( shpCrossHair );
				g2.scale( 5.0e-3f, 5.0e-3f );   // the scaling is necessary because we cannot get font size < 1
				if( rt_trnsNames[ i ] != null ) g2.drawString( rt_trnsNames[ i ], 1.0f, -1.0f );
                g2.setTransform( trnsRecent );  // undo translation and scaling
            }
		}

		// --- paint tool state ---
		
		if( activeTool != null ) activeTool.paintOnTop( g2 );

		// --- invoke top painters ---
		for( i = 0; i < collTopPainters.size(); i++ ) {
			((TopPainter) collTopPainters.get( i )).paintOnTop( g2 );
		}

		// --- restore Graphics2D properties ---
		g2.setTransform( trnsOrig );
		g2.setStroke( strkOrig );
	}

	private void recreateImage()
	{
		if( image != null ) image.flush();
		image = createImage( recentSize.width, recentSize.height );
	}
	
	// sync : must be called on the event thread
	private void redrawImage()
	{
		if( image == null ) return;

		int				diam		= Math.min( recentSize.width, recentSize.height );
		Graphics2D		g2			= (Graphics2D) image.getGraphics();
		AffineTransform trnsRecent;
		ReceiverShape   rcvShp;
		int				i;
		Image			userImg;
		
		// --- surface image ---
		if( prefRcvSense ) {
			g2.drawImage( bufImg, 0, 0, diam, diam, this );
		} else {
			g2.setColor( Color.white );
			g2.fillRect( 0, 0, recentSize.width, recentSize.height );
		}
		if( prefUserImages ) {
			for( i = 0; i < collUserImages.size(); i++ ) {
				userImg = (Image) collUserImages.get( i );
//System.err.println( "userImg w "+userImg.getWidth( this )+" ; h "+userImg.getHeight( this ));
				g2.drawImage( userImg, 0, 0, diam, diam, this );
			}
		}
		g2.scale( diam, diam );		// virtual space has dimension 1.0 x 1.0
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
//		g2.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
//		g2.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
    	g2.setFont( fntLabel );

		trnsRecent = g2.getTransform();
	// --- active selection ---
		for( i = 0; i < collReceiverShapes.size(); i++ ) {
			rcvShp = (ReceiverShape) collReceiverShapes.get( i );
			if( rcvShp.selected ) {
				g2.translate( rcvShp.loc.getX(), rcvShp.loc.getY() );
				g2.setColor( colrSelection );
				g2.fill( rcvShp.outline );
				g2.setTransform( trnsRecent );  // undo translation
			}
		}
	// --- receivers ---
		g2.setStroke( strkDottedLine );
		for( i = 0; i < collReceiverShapes.size(); i++ ) {
			rcvShp = (ReceiverShape) collReceiverShapes.get( i );
			g2.setColor( rcvShp.selected ? colrTextSelection : colrReceiver );
			g2.translate( rcvShp.loc.getX(), rcvShp.loc.getY() );
			g2.draw( rcvShp.outline );
			g2.fill( shpCrossHair );
			g2.scale( 5.0e-3f, 5.0e-3f );   // the scaling is necessary because we cannot get font size < 1
			if( rcvShp.name != null ) g2.drawString( rcvShp.name, 1.0f, -1.0f );
			g2.setTransform( trnsRecent );  // undo translation and scaling
		}

	// --- transmitter selected trajectories ---
		if( prefTrnsTraj ) {
			g2.setColor( prefRcvSense ? colrTrnsLight : colrTrnsDark );
			for( i = 0; i < collTransmitterPath.size(); i++ ) {
				g2.draw( (Shape) collTransmitterPath.get( i ));
			}
		}

		g2.dispose();
	}

	/*
	 *  Efficiently recalculates / repaints the surface : if the two clips intersect,
	 *  recalc the union and paint it if its area is smaller than the sum of the separate
	 *  clips. if not, repaint each clip separately
	 *
	 *  @param  clipRect		first clipping rectangle in virtual coords
	 *  @param  clipRect2		second clipping rectangle in virtual coords
	 *  @param  updateSurfacePane	whether the surface image needs to be recalculated. if true,
	 *							the offscreen image will be redrawn as well. if true, redrawImage()
	 *							is invoked right after the update
	 *
	 *  @synchronization	waitShared on DOOR_RCV if <code>updateSurfacePane</code> is <code>true</code>
	 *						since in this case <code>updateSurfacePaneImage</code> is called
	 */
	private void efficientUpdateAndRepaint( Rectangle2D clipRect, Rectangle2D clipRect2, boolean updateSurfacePane )
	{
		Rectangle2D clipRect3;

		if( clipRect != null ) {
			if( clipRect2 != null ) {
				clipRect3 = clipRect.createUnion( clipRect2 );
				if( clipRect3.getWidth() * clipRect3.getHeight() <
					(clipRect.getWidth() * clipRect.getHeight() + clipRect2.getWidth() * clipRect2.getHeight()) ) {
					
					if( updateSurfacePane ) {
						updateSurfacePaneImage( clipRect3 );
						redrawImage();
					}
					repaint( virtualToScreenClip( clipRect3 ));
				} else {
					if( updateSurfacePane ) {
						updateSurfacePaneImage( clipRect );
						updateSurfacePaneImage( clipRect2 );
						redrawImage();
					}
					repaint( virtualToScreenClip( clipRect ));
					repaint( virtualToScreenClip( clipRect2 ));
				}
			} else {
				if( updateSurfacePane ) {
					updateSurfacePaneImage( clipRect );
					redrawImage();
				}
				repaint( virtualToScreenClip( clipRect ));
			}
		} else if( clipRect2 != null ) {
			if( updateSurfacePane ) {
				updateSurfacePaneImage( clipRect2 );
				redrawImage();
			}
			repaint( virtualToScreenClip( clipRect2 ));
		}
	}

	/*
	 *  Calculate the rectangle that contains all the rectangles
	 *  specified by the bounds of Receivers provided as a List.
	 *
	 *  @param  coll	Collection of Receivers
	 *  @return			a (possibly empty) Rectangle2D describing
	 *					the union of all Receivers' bounds
	 *
	 *  @throws ClassCastException  if one of the collection elements is not a Receiver
	 */
	private Rectangle2D getUnionRect( java.util.List coll )
	{
		Rectangle2D clipRect = new Rectangle2D.Double();
		Object		obj;
		int			i;
		
		for( i = 0; i < coll.size(); i++ ) {
			obj = coll.get( i );
			Rectangle.union( clipRect, ((Receiver) obj).getBounds(), clipRect );
		}
		
		return clipRect;
	}

	/*
	 *  Reads all currently selected transmitter paths
	 *  and stores them in collTransmitterPath.
	 *
	 *  @synchronization	waitShared on DOOR_TIMETRNSMTE | DOOR_GRP
	 */
    private void updateTransmitterPath()
    {
        int						i, j, reqLen, pathLen, len, lastLen;
        Transmitter				trns;
        Span					span;
        float[][]				frames  = null;
        float[]					x, y;
        GeneralPath				path	= null;
		SubsampleInfo			info;
		MultirateTrackEditor	mte;
		float					lx, ly;
		java.util.List			collTrns;
        
		try {
			doc.bird.waitShared( Session.DOOR_TIMETRNSMTE | Session.DOOR_GRP );
			span = doc.timeline.getSelectionSpan();
			collTransmitterPath.clear();
			if( span.getLength() < 2 ) return;

			collTrns	= doc.activeTransmitters.getAll();
			collTrns.retainAll( doc.selectedTransmitters.getAll() );
			
			for( i = 0; i < collTrns.size(); i++ ) {
				trns	= (Transmitter) collTrns.get( i );
				mte		= trns.getTrackEditor();
				// performance measures show that this routine is
				// vey fast, like one or two millisecs, while the draw method
				// of the Graphics2D called in redrawImage() becomes hell
				// slow if the GeneralPath contains more than say 250 lines.
				// therefore we start with a fairly big subsample to get a 
				// good resolution; however in the path creation loop, points
				// are skipped if they lie very close to each other. therefore,
				// right after the creation of the path we know how many lines
				// it actually contains, and if these exceed 256 we'll restart
				// with a smaller subsample.
				for( reqLen = 1024, pathLen = 257, len = -1; pathLen > 256; reqLen >>= 1 ) {
					info	= mte.getBestSubsample( span, reqLen );
					lastLen = len;
					len		= (int) info.sublength;
					if( lastLen == len ) continue;

					if( frames == null || frames[0].length < len ) {
						frames = new float[2][len];
					}
					mte.read( info, frames, 0 );
					x		= frames[0];
					y		= frames[1];
					path	= new GeneralPath( GeneralPath.WIND_EVEN_ODD, len );
					lx		= x[0];
					ly		= y[0];
					path.moveTo( lx, ly );
					for( j = 1, pathLen = 0; j < len; j++ ) {
						// only points sufficiently far away from each other are
						// added, this speeds up the painting
						// the value corresponds to a few pixels when the surface
						// frame is viewed at about 1000 x 1000 pixels
						if( Point2D.distanceSq( lx, ly, x[j], y[j] ) < 2.0e-5 ) continue;
						lx = x[j];
						ly = y[j];
						path.lineTo( lx, ly );
						pathLen++;
					}
				} // for( ... )
				collTransmitterPath.add( path );
			}
		}
		catch( IOException e1 ) {
			System.err.println( e1.getLocalizedMessage() );   
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIMETRNSMTE | Session.DOOR_GRP );
		}
    }

	/*
	 *  Reads all receiver locations and shapes
	 *
	 *  @synchronization	waitShared on DOOR_RCV + DOOR_GRP
	 */
    private void updateReceiverShapes()
    {
        int				i;
        Receiver		rcv;
		java.util.List	collRcv;
		java.util.List	collRcvSel;

		try {
			doc.bird.waitShared( Session.DOOR_RCV | Session.DOOR_GRP );
			collRcvSel	= doc.selectedReceivers.getAll();
			collRcv		= doc.activeReceivers.getAll();
			collReceiverShapes.clear();
			for( i = 0; i < collRcv.size(); i++ ) {
				rcv = (Receiver) collRcv.get( i );
				collReceiverShapes.add( new ReceiverShape(
					rcv.getAnchor(), rcv.getOutline(), rcv.getName(), collRcvSel.contains( rcv )));
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_RCV | Session.DOOR_GRP );
		}
    }

	/*
	 *  Reads all group's background images
	 *
	 *  @synchronization	waitShared on DOOR_GRP
	 */
    private void updateUserImages()
    {
        int				i;
		java.util.List	collSO;
		SessionObject	so;
		File			f;
		Toolkit			tk	= Toolkit.getDefaultToolkit();
		MediaTracker	mt	= new MediaTracker( this );
		Image			img;
		Object[]		errors;

		try {
			doc.bird.waitShared( Session.DOOR_GRP );
			collSO	= doc.selectedGroups.getAll();
			disposeUserImages();
			for( i = 0; i < collSO.size(); i++ ) {
				so	= (SessionObject) collSO.get( i );
				f	= (File) so.getMap().getValue( SessionGroup.MAP_KEY_USERIMAGE );
				if( f != null ) {
					img = tk.createImage( f.getAbsolutePath() );
					collUserImages.add( img );
				}
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_GRP );
		}
		
		try {
			mt.waitForAll();
		}
		catch( InterruptedException e1 ) {}
		
		errors = mt.getErrorsAny();
		if( errors != null ) {
			for( i = 0; i < errors.length; i++ ) {
				System.err.println( "Media error on "+errors[ i ].toString() );
			}
		}
    }
	
	private void disposeUserImages()
	{
		for( int i = 0; i < collUserImages.size(); i++ ) {
			((Image) collUserImages.get( i )).flush();
		}
		collUserImages.clear();
	}

	private void update( SessionCollection.Event e )
	{
		updateReceiverShapes();
		updateSurfacePaneImage( getUnionRect( e.getCollection() ));
		redrawImage();
		repaint();
	}

// ---------------- VirtualSurface interface ----------------
	
	/**
	 *  Snaps a point to neighbouring objects
	 *  (transmitters or receivers).
	 *
	 *  @param  freePt  'unsnapped' point
	 *  @param  virtual whether freePt is in virtual (true)
	 *					or screen (false) space
	 *  @return			the snapped point or the original
	 *					point if snapping deactivated or no
	 *					neighbouring points found. the point
	 *					will be in virtual or screen space
	 *					depending on the value of 'virtual'!
	 */
	public Point2D snap( Point2D freePt, boolean virtual )
	{
		if( !prefSnap ) return freePt;
		
		Point2D				trnsFreePt  = virtual ? virtualToScreen( freePt ) : freePt;
		Point2D				objectPt, trnsObjectPt;
		Point2D				snapPt		= null;
		ReceiverShape		rcvShp;
		Point2D				trnsSnapPt	= null;
		double				snapDistSq  = 25.1;
		double				distSq;
		int					i;
		
		objectPt = new Point2D.Double();
		for( i = 0; i < rt_trnsNames.length; i++ ) {
			objectPt.setLocation( rt_trnsLocX[i], rt_trnsLocY[i] );
			trnsObjectPt	= virtualToScreen( objectPt );
			distSq			= trnsObjectPt.distanceSq( trnsFreePt );
			if( distSq < snapDistSq ) {
				snapPt		= objectPt;
				trnsSnapPt  = trnsObjectPt;
				snapDistSq  = distSq;
			}
		}
		for( i = 0; i < collReceiverShapes.size(); i++ ) {
			rcvShp			= (ReceiverShape) collReceiverShapes.get( i );
			objectPt		= rcvShp.loc;
			trnsObjectPt	= virtualToScreen( objectPt );
			distSq			= trnsObjectPt.distanceSq( trnsFreePt );
			if( distSq < snapDistSq ) {
				snapPt		= objectPt;
				trnsSnapPt  = trnsObjectPt;
				snapDistSq  = distSq;
			}
		}

		return( snapPt == null ? freePt : (virtual ? snapPt : trnsSnapPt) );
	}

	/**
	 *  Converts a location on the screen
	 *  into a point the virtual space.
	 *  Neither input nor output point need to
	 *  be limited to particular bounds
	 *
	 *  @param  screenPt		point in screen space
	 *  @return the input point transformed to virtual space
	 */
	public Point2D screenToVirtual( Point2D screenPt )
	{
		Dimension   d		= getSize();
		int			diam	= Math.min( d.width, d.height );
		return new Point2D.Double( screenPt.getX() / diam, screenPt.getY() / diam );
	}

	/**
	 *  Converts a point in the virtual space
	 *  into a location on the screen.
	 *  Neither input nor output point need to
	 *  be limited to particular bounds
	 *
	 *  @param  virtualPt   point in the virtual space whose
	 *						visible bounds are (0, 0 ... 1, 1)
	 *  @return				point in the display space
	 */
	public Point2D virtualToScreen( Point2D virtualPt )
	{
		Dimension   d		= getSize();
		int			diam	= Math.min( d.width, d.height );
		return new Point2D.Double( virtualPt.getX() * diam, virtualPt.getY() * diam );
	}

	/**
	 *  Converts a rectangle in the virtual space
	 *  into a rectangle suitable for Graphics clipping
	 *
	 *  @param  virtualClip		a rectangle in virtual space
	 *  @return the input rectangle transformed to screen space,
	 *			possibly with extra margin padding to ensure
	 *			correct graphics clipping of decorated shapes
	 */
	public Rectangle virtualToScreenClip( Rectangle2D virtualClip )
	{
		Dimension   d			= getSize();
		int			diam		= Math.min( d.width, d.height );
		// die vergroesserung der rechtecks um einige pixel haengt damit zusammen, dass das bufImg
		// vergroessert gezeichnet wird und weitere pixel durch antialiasing interpolation
		// eingefaerbt werden!
		Rectangle   screenClip  = new Rectangle( (int) (virtualClip.getX() * diam) - 1, (int) (virtualClip.getY() * diam) - 1,
												 (int) (virtualClip.getWidth() * diam) + 4, (int) (virtualClip.getHeight() * diam) + 4 );
		return screenClip;
	}

	/**
	 *  Converts a shape in the virtual space
	 *  into a shape on the screen.
	 *
	 *  @param  virtualShape	arbitrary shape in virtual space
	 *  @return the input shape transformed to screen space
	 */
	public Shape virtualToScreen( Shape virtualShape )
	{
		Dimension   d		= getSize();
		int			diam	= Math.min( d.width, d.height );
		
		return AffineTransform.getScaleInstance( diam, diam ).createTransformedShape( virtualShape );
	}

	/**
	 *  Converts a shape in the screen space
	 *  into a shape in the virtual space.
	 *
	 *  @param  screenShape		arbitrary shape in screen space
	 *  @return the input shape transformed to virtual space
	 */
	public Shape screenToVirtual( Shape screenShape )
	{
		Dimension   d		= getSize();
		double		diam	= 1.0 / (double) Math.min( d.width, d.height );
		
		return AffineTransform.getScaleInstance( diam, diam ).createTransformedShape( screenShape );
	}

	private void showCursorInfo( Point2D pt )
	{
		if( observer != null && observer.isVisible() ) {
			msgArgs[0]		= new Double( pt.getX() );
			msgArgs[1]		= new Double( pt.getY() );
			cursorInfo[0]   = msgCursorX.format( msgArgs );
			cursorInfo[1]   = msgCursorY.format( msgArgs );
			observer.showCursorInfo( cursorInfo );
		}
	}

	private void showCursorTab()
	{
		if( observer == null ) {
			observer = (ObserverPalette) root.getComponent( Main.COMP_OBSERVER );
		}
		if( observer != null && observer.isVisible() ) {
			observer.showTab( ObserverPalette.CURSOR_TAB );
		}
	}

	// syncs attemptShared to DOOR_TRNS + DOOR_GRP
	private void checkTrnsNames()
	{
		int				trnsIdx, numTrns;
		java.util.List	collTrns;
	
		if( !doc.bird.attemptShared( Session.DOOR_TRNS | Session.DOOR_GRP, 250 )) return;
		try {
			collTrns	= doc.activeTransmitters.getAll();
			numTrns		= Math.min( rt_trnsNames.length, collTrns.size() );
			for( trnsIdx = 0; trnsIdx < numTrns; trnsIdx++ ) {
				rt_trnsNames[ trnsIdx ] = ((SessionObject) collTrns.get( trnsIdx )).getName();
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_GRP );
		}
	}

	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}	

// ---------------- LaterInvocationManager.Listener interface ---------------- 

	// o instanceof PreferenceChangeEvent
	public void preferenceChange( PreferenceChangeEvent pce)
	{
		String  key		= pce.getKey();
		String  value	= pce.getNewValue();

		if( key.equals( PrefsUtil.KEY_SNAP )) {
			prefSnap	= Boolean.valueOf( value ).booleanValue();
		} else if( key.equals( PrefsUtil.KEY_VIEWRCVSENSE )) {
			prefRcvSense= Boolean.valueOf( value ).booleanValue();
			if( prefRcvSense ) {
				updateSurfacePaneImage( null );
			}
			redrawImage();
			repaint();
		} else if( key.equals( PrefsUtil.KEY_VIEWEQPRECEIVER )) {
			prefRcvEqP	= Boolean.valueOf( value ).booleanValue();
			if( prefRcvSense ) {
				updateSurfacePaneImage( null );
				redrawImage();
				repaint();
			}
		} else if( key.equals( PrefsUtil.KEY_VIEWTRNSTRAJ )) {
			prefTrnsTraj = Boolean.valueOf( value ).booleanValue();
			if( prefTrnsTraj ) {
				updateTransmitterPath();
			}
			redrawImage();
			repaint();
		} else if( key.equals( PrefsUtil.KEY_VIEWUSERIMAGES )) {
			prefUserImages = Boolean.valueOf( value ).booleanValue();
			if( prefUserImages ) {
				updateUserImages();
			} else {
				disposeUserImages();
			}
			redrawImage();
			repaint();
		}
	}

// ---------------- RealtimeConsumer interface ---------------- 

	/**
	 *  Creates a request for realtime consumption.
	 *  The SurfacePane is interested in transmitter trajectory
	 *  data at a visually fluent rate, some 30 fps.
	 *  Delivery notification is requested as offhand and realtime ticks.
	 *
	 *  @param  context		the context of the realtime performance
	 *  @return the request of realtime streaming data for the surface
	 *
	 *	@synchronization	this method attempts shared on TRNS + GRP
	 */
	public RealtimeConsumerRequest createRequest( RealtimeContext context )
	{
		RealtimeConsumerRequest	request;
		int						trnsIdx, numTrns;
		java.util.List			collTrns;
		
		rt_valid				= false;
		request					= new RealtimeConsumerRequest( this, context );

		if( !doc.bird.attemptShared( Session.DOOR_TRNS | Session.DOOR_GRP, 250 )) return request;
		try {
			// 30 fps is visually fluent
			request.frameStep		= RealtimeConsumerRequest.approximateStep( context, 30 );
			request.notifyTickStep  = request.frameStep;
			request.notifyTicks		= true;
			request.notifyOffhand	= true;
//			collTrns				= context.getTransmitters();
			collTrns				= doc.activeTransmitters.getAll();
			numTrns					= collTrns.size();
			if( rt_peak.length != numTrns ) {
				rt_trnsNames= new String[ numTrns ];
				rt_trnsLocX = new float[ numTrns ];
				rt_trnsLocY = new float[ numTrns ];
				rt_peak		= new int[ numTrns ];
			}

			for( trnsIdx = 0; trnsIdx < numTrns; trnsIdx++ ) {
				rt_peak[ trnsIdx ] = context.getTransmitters().indexOf( collTrns.get( trnsIdx ));
				if( rt_peak[ trnsIdx ] >= 0 ) {
					request.trajRequest[ rt_peak[ trnsIdx ]]  = true;
					rt_trnsNames[ trnsIdx ]	= ((Transmitter) collTrns.get( trnsIdx )).getName();
				}
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_GRP );
		}
//		rt_currentClip.setRect( 0.0, 0.0, 1.0, 1.0 );	// repaint whole surface the first time
		rt_valid		= true;
		
		return request;
	}

	/**
	 *  Copies realtime generated trajectory
	 *  data to the surface's internal buffers
	 *  and schedules graphics update
	 */
	public void offhandTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
	{
		int trnsIdx;

		if( !rt_valid ) return;
		
		for( trnsIdx = 0; trnsIdx < rt_peak.length; trnsIdx++ ) {
			if( rt_peak[ trnsIdx ] < 0 ) continue;
			rt_trnsLocX[ trnsIdx ]  = source.trajOffhand[ rt_peak[ trnsIdx ]][ 0 ];
			rt_trnsLocY[ trnsIdx ]  = source.trajOffhand[ rt_peak[ trnsIdx ]][ 1 ];
		}

		repaint();
	}
	
	/**
	 *  Copies realtime generated trajectory
	 *  data to the surface's internal buffers
	 *  and schedules graphics update
	 */
	public void realtimeTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
	{
		int bufOff, trnsIdx;

		if( !rt_valid ) return;
		
		rt_pos = currentPos;
		
		bufOff = (int) (currentPos - source.firstHalf.getStart());
		if( bufOff < 0 || bufOff >= source.bufSizeH ) {
			bufOff = (int) (currentPos - source.secondHalf.getStart());
			if( bufOff < 0 || bufOff >= source.bufSizeH ) return;  // currently no valid buffer
			bufOff += source.bufSizeH;
		}

		for( trnsIdx = 0; trnsIdx < rt_peak.length; trnsIdx++ ) {
			if( rt_peak[ trnsIdx ] < 0 ) continue;
			// x and y have chances to be displayed out of sync but we don't really care
			rt_trnsLocX[ trnsIdx ]  = source.trajBlockBuf[ rt_peak[ trnsIdx ]][ 0 ][ bufOff ];
			rt_trnsLocY[ trnsIdx ]  = source.trajBlockBuf[ rt_peak[ trnsIdx ]][ 1 ][ bufOff ];
		}

		repaint();
	}

	/**
	 *  We didn't register for block notification
	 */
	public void realtimeBlock( RealtimeContext context, RealtimeProducer.Source source, boolean even ) {}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		doc.timeline.addTimelineListener( this );
		rt_valid = false;
		transport.addRealtimeConsumer( this );
// System.err.println( "surface starts listening" );

		if( prefTrnsTraj ) {
			updateTransmitterPath();
		}
		redrawImage();
        repaint();
    }

    public void stopListening()
    {
		doc.timeline.removeTimelineListener( this );
		transport.removeRealtimeConsumer( this );
		rt_valid = false;
// System.err.println( "surface stops listening" );
    }

// ---------------- ToolListener interface ---------------- 
 
	public void toolChanged( ToolActionEvent e )
	{
		if( activeTool != null ) {
			activeTool.toolDismissed( this );
			removeMouseMotionListener( cursorListener );
		}
		activeTool = (AbstractTool) tools.get( new Integer( e.getToolAction().getID() ));
		if( activeTool != null ) {
			activeTool.toolAcquired( this );
			setCursor( e.getToolAction().getDefaultCursor() );
			showCursorTab();
			addMouseMotionListener( cursorListener );
		} else {
			setCursor( null );
		}
	}
 
// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		if( prefTrnsTraj ) {
			updateTransmitterPath();
			redrawImage();
			repaint();
		}
    }
    
	public void timelinePositioned( TimelineEvent e ) {}
//    {
//        updateTransmitterShapes();
//        repaint();
//    }

	public void timelineChanged( TimelineEvent e ) {}
    public void timelineScrolled( TimelineEvent e ) {}

// ---------------- Shape information classes ---------------- 

	private class ReceiverShape
	{
		private Point2D loc;
		private Shape   outline;
		private String  name;
		private boolean selected;
		
		private ReceiverShape( Point2D loc, Shape outline, String name, boolean selected )
		{
			this.loc		= loc;
			this.outline	= outline;
			this.name		= name;
			this.selected	= selected;
		}
	}

// ---------------- Pointer Tool ---------------- 

	private class SurfacePointerTool
	extends AbstractTool
	{
		// --- drag-and-drop ---
		
		private MouseEvent	dndFirstEvent   = null;		// event from the initial mouse button press; if null it's not a valid drag gesture
		private boolean		dndDragging		= false;	// true when mouse has been moved to a reasonable amount
		private Rectangle2D	dndRecentRect;				// for graphics rendering update
		private Hashtable   dndAnchorRef	= new Hashtable();

		private SurfacePointerTool()
		{
			super();
		}

		public void toolAcquired( Component c )
		{
			super.toolAcquired( c );

			// essential inits already in finishGesture()
		}

		public void toolDismissed( Component c )
		{
			finishGesture();
			super.toolDismissed( c );
		}
		
		private void finishGesture()
		{
			// clean up after DnD
			if( dndFirstEvent != null ) {
				dndFirstEvent   = null;
				dndDragging		= false;
				dndAnchorRef.clear();   // deletes references and allows garbage collection
			}
		}

		protected void cancelGesture()
		{
			finishGesture();
		}

		public void paintOnTop( Graphics2D g2 ) {}

		public void mouseEntered( MouseEvent e ) {}
		public void mouseExited( MouseEvent e ) {}

		public void mousePressed( MouseEvent e )
		{
			Point2D			ptMouse			= e.getPoint();
			Point2D			ptReceiver;
			Point2D			ptReceiverTrns;
			Receiver		rcv;
			Rectangle2D		clipRect, clipRect2;
			double			distanceSq;
			double			hitDistSq		= 51.0;		// Mouse must be as close at 5 pixels delta-h and delta-v
			Receiver		hitReceiver		= null;
			int				i;
			java.util.List  coll;
			UndoableEdit	edit;
			java.util.List	collRcv;

			e.getComponent().requestFocus();	// otherwise keyboard shortcuts etc. don't work

			if( !doc.bird.attemptShared( Session.DOOR_RCV | Session.DOOR_GRP, 250 )) return;
			try {
				collRcv = doc.activeReceivers.getAll();
				for( i = 0; i < collRcv.size(); i++ ) {
					rcv = (Receiver) collRcv.get( i );
					ptReceiver = rcv.getAnchor();
					ptReceiverTrns = virtualToScreen( ptReceiver );
					distanceSq = ptReceiverTrns.distanceSq( ptMouse );
					if( distanceSq < hitDistSq ) {
						hitDistSq   = distanceSq;
						hitReceiver	= rcv;
					}
				}

				// prepare DnD
				if( hitReceiver != null ) {
					dndFirstEvent   = e;		// mouseDragged() will detect this
					dndDragging		= false;	// a follow-up mouse move is still required to start the drag
				} else {
					dndFirstEvent   = null;
				}

				// manage (de)selection
				if( e.isShiftDown() || e.isMetaDown() ) {		// multi-selection by holding down the shift or cmd key
					if( hitReceiver != null ) {					// selection changed indeed
						coll = doc.selectedReceivers.getAll();
						if( !coll.contains( hitReceiver )) {	// add object to selection
							coll.add( hitReceiver );
						} else {								// remove object from selection
							coll.remove( hitReceiver );
						}
						edit = new EditSetSessionObjects( this, doc, doc.selectedReceivers,
																   coll, Session.DOOR_RCV );
						doc.getUndoManager().addEdit( edit );
						updateReceiverShapes();
						redrawImage();
						repaint( virtualToScreenClip( hitReceiver.getBounds() ));
					}
				} else {				// single-selection
					coll = doc.selectedReceivers.getAll();
					if( (hitReceiver == null && !coll.isEmpty()) ||
						(hitReceiver != null && !coll.contains( hitReceiver ))) {  // selection changed indeed
						
						clipRect = getUnionRect( coll );
						coll.clear();
						if( hitReceiver != null ) {
							coll.add( hitReceiver );
							clipRect2 = hitReceiver.getBounds();
						} else {
							clipRect2 = null;
						}
						edit = new EditSetSessionObjects( this, doc, doc.selectedReceivers,
																   coll, Session.DOOR_RCV );
						doc.getUndoManager().addEdit( edit );
						updateReceiverShapes();
						redrawImage();
						efficientUpdateAndRepaint( clipRect, clipRect2, false );
					}
				}
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_RCV | Session.DOOR_GRP );
			}
		} // mousePressed( MouseEvent e )

		public void mouseReleased( MouseEvent e )
		{
			finishGesture();
		} // mouseReleased( MouseEvent e )

		public void mouseClicked( MouseEvent e )
		{
			Receiver		rcv;
			Rectangle2D		clipRect;
			ReceiverEditor  rcvEdit;
			AbstractWindow	rcvEditFrame;
			java.util.List  coll;
			UndoableEdit	edit;

			if( !doc.bird.attemptShared( Session.DOOR_RCV, 250 )) return;
			try {
				if( doc.selectedReceivers.isEmpty() ) {
					if( e.getClickCount() == 2 ) {  // double click creates new Receiver
						rcv = createReceiver( screenToVirtual( e.getPoint() ));
						if( rcv != null ) {
							coll = doc.selectedReceivers.getAll();
							coll.add( rcv );
							edit = new EditSetSessionObjects( this, doc, doc.selectedReceivers,
																	   coll, Session.DOOR_RCV );
							doc.getUndoManager().addEdit( edit );
							clipRect = rcv.getBounds();
							updateSurfacePaneImage( clipRect );
							updateReceiverShapes();
							redrawImage();
							repaint( virtualToScreenClip( clipRect ));
						}
					}
					
				} else { // items have been selected
					if( e.getClickCount() == 2 && doc.selectedReceivers.size() == 1 ) {  // double click opens editor
						rcv		= (Receiver) doc.selectedReceivers.get( 0 );
						final Class clz = rcv.getDefaultEditor();
						final Constructor cons = clz.getConstructor( new Class[] { Session.class });
						rcvEdit = (ReceiverEditor) cons.newInstance( new Object[] { doc });	// XXX deligate to SurfacePaneFrame
						rcvEdit.init( rcv );
						rcvEditFrame = rcvEdit.getView();
						rcvEditFrame.setVisible( true );
						rcvEditFrame.toFront();
						showCursorTab();
					}
				}
			}
			catch( InstantiationException e1 ) {
				System.err.println( e1.getLocalizedMessage() );
			}
			catch( IllegalAccessException e2 ) {
				System.err.println( e2.getLocalizedMessage() );
			}
			catch( InvocationTargetException e3 ) {
				System.err.println( e3.getLocalizedMessage() );
			}
			catch( NoSuchMethodException e4 ) {
				System.err.println( e4.getLocalizedMessage() );
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_RCV );
			}
		} // mouseClicked( MouseEvent e )

		public void mouseMoved( MouseEvent e ) {}

		public void mouseDragged( MouseEvent e )
		{
//			showCursorInfo( screenToVirtual( e ));

			if( dndFirstEvent == null ) return;		// not a valid drag gesture

			Point2D			ptDeltaMouse	= new Point2D.Double( e.getPoint().getX() - dndFirstEvent.getPoint().getX(),
																  e.getPoint().getY() - dndFirstEvent.getPoint().getY() );
			Point2D			ptMouseTrns, ptAnchor;
			Rectangle2D		dndCurrentRect;
			int				i;
			Receiver		rcv;
			java.util.List  dndColl;
			UndoableEdit	edit;
			
			if( !dndDragging ) {	// test if mouse move was sufficient
				if( ptDeltaMouse.getX()*ptDeltaMouse.getX()+ptDeltaMouse.getY()*ptDeltaMouse.getY() <= 25.0 ) return;
			}

			if( !doc.bird.attemptShared( Session.DOOR_RCV, 250 )) {
				finishGesture();
				return;
			}
			try {
				dndColl = doc.selectedReceivers.getAll();
				if( !dndDragging ) {
					dndDragging		= true;
					dndRecentRect   = getUnionRect( dndColl );
					dndAnchorRef.clear();
					for( i = 0; i < dndColl.size(); i++ ) {
						rcv = (Receiver) dndColl.get( i );
						dndAnchorRef.put( rcv, rcv.getAnchor() );
					}
				}
				ptMouseTrns = screenToVirtual( ptDeltaMouse );
				for( i = 0; i < dndColl.size(); i++ ) {
					rcv			= (Receiver) dndColl.get( i );
					ptAnchor	= (Point2D) dndAnchorRef.get( rcv );
					if( ptAnchor != null ) {
// XXX need to exclude our own anchor in the snap method() !
//						rcv.setAnchor( snap( new Point2D.Double(
//							Math.max( 0.0, Math.min( 1.0, ptAnchor.getX() + ptMouseTrns.getX() )), 
//							Math.max( 0.0, Math.min( 1.0, ptAnchor.getY() + ptMouseTrns.getY() ))), true ));
						ptAnchor = new Point2D.Double(
							Math.max( 0.0, Math.min( 1.0, ptAnchor.getX() + ptMouseTrns.getX() )), 
							Math.max( 0.0, Math.min( 1.0, ptAnchor.getY() + ptMouseTrns.getY() )));
						edit	 = new EditSetReceiverAnchor( this, doc, rcv, ptAnchor );
						doc.getUndoManager().addEdit( edit );
					}
				}
				dndCurrentRect  = getUnionRect( dndColl );
				updateReceiverShapes();
				efficientUpdateAndRepaint( dndRecentRect, dndCurrentRect, true );
				dndRecentRect   = dndCurrentRect;
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_RCV );
			}
		} // mouseDragged( MouseEvent e )
	} // class SurfacePanePointerTool

// ---------------- Line Tool ---------------- 

	private class SurfaceLineTool
	extends AbstractSurfaceGeomTool
	{
		private Line2D  lineShape   = new Line2D.Double();
		// function evaluation
		private float   f_sx, f_sy, f_dx, f_dy;		// start point, delta x, delta y
	
		private SurfaceLineTool( Main root, Session doc, VirtualSurface s )
		{
			super( root, doc, s, 2 );
		}
		
		protected void initControlPoints( Point2D[] ctrlPoints )
		{
			// no additional control points
		}

		protected Shape createBasicShape( Point2D[] ctrlPoints )
		{
			lineShape.setLine( ctrlPoints[0], ctrlPoints[1] );
			return lineShape;
		}

		protected Shape createControlledShape( Point2D[] ctrlPoints )
		{
			return createBasicShape( ctrlPoints );
		}

		protected boolean initFunctionEvaluation( Point2D[] ctrlPoints )
		{
			f_sx	= (float) ctrlPoints[0].getX();
			f_sy	= (float) ctrlPoints[0].getY();
			f_dx	= (float) (ctrlPoints[1].getX() - ctrlPoints[0].getX());
			f_dy   = (float) (ctrlPoints[1].getY() - ctrlPoints[0].getY());
			return true;
		}
		
		// f_x(t) = f_sx + t * f_dx; f_y(t) = f_sy + t * f_dy
		protected void evaluateFunction( float[] argBuf, float[][] resultBuf, int len )
		{
			int		i;
			float   t;
			float[] x   = resultBuf[0];
			float[] y   = resultBuf[1];
			
			for( i = 0; i < len; i++ ) {
				t		= argBuf[i];
				x[i]	= f_sx + t * f_dx;
				y[i]	= f_sy + t * f_dy;
			}
		}
	}

// ---------------- Curve Tool ---------------- 

	private class SurfaceCurveTool
	extends AbstractSurfaceGeomTool
	{
		private Line2D			lineShape   = new Line2D.Double();
		private CubicCurve2D	curveShape  = new CubicCurve2D.Double();
		// function evaluation
		// = start point, endpoint, ctrl pt1 + 2
		private float   f_p1x, f_p1y, f_p2x, f_p2y, f_cp1x, f_cp1y, f_cp2x, f_cp2y;
	
		private SurfaceCurveTool( Main root, Session doc, VirtualSurface s )
		{
			super( root, doc, s, 4 );
		}
		
		protected void initControlPoints( Point2D[] ctrlPoints )
		{
			double  r1  = 0.25 / Math.PI;
			double  r2  = 1.0 - r1;
		
			ctrlPoints[2]   = new Point2D.Double( ctrlPoints[0].getX() * r2 + ctrlPoints[1].getX() * r1,
												  ctrlPoints[0].getY() * r2 + ctrlPoints[1].getY() * r1 );
			ctrlPoints[3]   = new Point2D.Double( ctrlPoints[0].getX() * r1 + ctrlPoints[1].getX() * r2,
												  ctrlPoints[0].getY() * r1 + ctrlPoints[1].getY() * r2 );
		}

		protected Shape createBasicShape( Point2D[] ctrlPoints )
		{
			lineShape.setLine( ctrlPoints[0], ctrlPoints[1] );
			return lineShape;
		}

		protected Shape createControlledShape( Point2D[] ctrlPoints )
		{
			// the curve control points (point idx 1 and 2) are
			// extrapolated by factor 3.14 because that makes it
			// easiert do create big curves without dragging
			// the control points off the surface
			Point2D ctrlPt1 = new Point2D.Double( ctrlPoints[0].getX() + Math.PI * (ctrlPoints[2].getX() - ctrlPoints[0].getX()),
												  ctrlPoints[0].getY() + Math.PI * (ctrlPoints[2].getY() - ctrlPoints[0].getY()));
			Point2D ctrlPt2 = new Point2D.Double( ctrlPoints[1].getX() + Math.PI * (ctrlPoints[3].getX() - ctrlPoints[1].getX()),
												  ctrlPoints[1].getY() + Math.PI * (ctrlPoints[3].getY() - ctrlPoints[1].getY()));
		
//			curveShape.setCurve( ctrlPoints[0], ctrlPoints[2], ctrlPoints[3], ctrlPoints[1] );
			curveShape.setCurve( ctrlPoints[0], ctrlPt1, ctrlPt2, ctrlPoints[1] );
			return curveShape;
		}

		protected boolean initFunctionEvaluation( Point2D[] ctrlPoints )
		{
			f_p1x   = (float) ctrlPoints[0].getX();
			f_p1y   = (float) ctrlPoints[0].getY();
			f_p2x   = (float) ctrlPoints[1].getX();
			f_p2y   = (float) ctrlPoints[1].getY();
			f_cp1x  = (float) (ctrlPoints[0].getX() + Math.PI * (ctrlPoints[2].getX() - ctrlPoints[0].getX()));
			f_cp1y  = (float) (ctrlPoints[0].getY() + Math.PI * (ctrlPoints[2].getY() - ctrlPoints[0].getY()));
			f_cp2x  = (float) (ctrlPoints[1].getX() + Math.PI * (ctrlPoints[3].getX() - ctrlPoints[1].getX()));
			f_cp2y  = (float) (ctrlPoints[1].getY() + Math.PI * (ctrlPoints[3].getY() - ctrlPoints[1].getY()));
//			f_cp1x  = (float) ctrlPoints[2].getX();
//			f_cp1y  = (float) ctrlPoints[2].getY();
//			f_cp2x  = (float) ctrlPoints[3].getX();
//			f_cp2y  = (float) ctrlPoints[3].getY();
			return true;
		}
		
		// p(t) = (1 - 3t + 3t^2 - t^3) p1 + (3t - 6t^2 + 3t^3) cp1 + (3t^2 - 3t^3) cp2 + t^3 p2
		protected void evaluateFunction( float[] argBuf, float[][] resultBuf, int len )
		{
			int		i;
			float   t, t3, tt, tt3, ttt, ttt3 /*, ttt2 */;
			float   coeff1, coeff2, coeff3, coeff4;
			float[] x   = resultBuf[0];
			float[] y   = resultBuf[1];
			
			for( i = 0; i < len; i++ ) {
				t		= argBuf[i];
				t3		= t*3;
				tt		= t*t;
				tt3		= tt*3;
				ttt		= tt*t;
				ttt3	= ttt*3;
//				ttt2	= ttt*2;
				
				coeff1  = 1.0f - t3 + tt3 - ttt;
				coeff2  = t3 - 6*tt + ttt3;
				coeff3  = tt3 - ttt3;
				coeff4  = ttt;

				x[i]	= coeff1 * f_p1x + coeff2 * f_cp1x + coeff3 * f_cp2x + coeff4 * f_p2x;
				y[i]	= coeff1 * f_p1y + coeff2 * f_cp1y + coeff3 * f_cp2y + coeff4 * f_p2y;
			}
		}
	}

// ---------------- Arc Tool ---------------- 

	private class SurfaceArcTool
	extends AbstractSurfaceGeomTool
	{
		private Ellipse2D.Double	ellipseShape	= new Ellipse2D.Double();
		private Arc2D				arcShape		= new Arc2D.Double();
		private Rectangle2D			frame			= new Rectangle2D.Double();
		private Rectangle2D			recentFrame		= new Rectangle2D.Double();
		private Point2D[]			recentCtrlPt	= new Point2D[3];
		private double[]			ctrlPtAngle		= new double[3];
		// function evaluation
		// = start angle, delta angle, x radius, y radius, center point
		private float   f_sa, f_da, f_rx, f_ry, f_cx, f_cy;
	
		private SurfaceArcTool( Main root, Session doc, VirtualSurface s )
		{
			super( root, doc, s, 5 );
		}
		
		protected void initControlPoints( Point2D[] ctrlPoints )
		{
			double  cx, cy, rx, ry;
			int		i;

			calcFrame( ctrlPoints );
			rx				= frame.getWidth()/2;
			ry				= frame.getHeight()/2;
			cx				= frame.getX() + rx;
			cy				= frame.getY() + ry;
			arcShape.setFrame( frame );
			recentFrame.setFrame( frame );
			ctrlPtAngle[0]  = Math.PI * 0.75;
			ctrlPtAngle[1]  = Math.PI * 1.75;
			ctrlPtAngle[2]  = Math.PI * 1.25;
			for( i = 0; i < 3; i++ ) {
				ctrlPoints[i+2] = new Point2D.Double( cx + Math.cos( ctrlPtAngle[i] ) * rx,
													  cy + Math.sin( ctrlPtAngle[i] ) * ry );
				recentCtrlPt[i] = ctrlPoints[i+2];
			}
			arcShape.setAngleStart( Math.toDegrees( -ctrlPtAngle[0] ));
			arcShape.setAngleExtent( Math.toDegrees( ctrlPtAngle[1] - ctrlPtAngle[0] ));
		}

		protected Shape createBasicShape( Point2D[] ctrlPoints )
		{
			calcFrame( ctrlPoints );
			ellipseShape.setFrame( frame );
			return ellipseShape;
		}

		protected Shape createControlledShape( Point2D[] ctrlPoints )
		{
			double		cx, cy, rx, ry;
			boolean		orient;
			int			i;
			double[]	ang = new double[3];

			calcFrame( ctrlPoints );

			rx		= frame.getWidth()/2;
			ry		= frame.getHeight()/2;
			cx		= frame.getX() + rx;
			cy		= frame.getY() + ry;
			for( i = 0; i < 3; i++ ) {
				ang[i]  = Math.atan2( (ctrlPoints[i+2].getY() - cy) / ry, (ctrlPoints[i+2].getX() - cx) / rx );
			}
			if( ang[1] < ang[0] ) ang[1] += MathUtil.PI2;
			if( ang[2] < ang[0] ) ang[2] += MathUtil.PI2;
			orient  = (ang[1] - ang[0]) / (ang[2] - ang[0]) < 1.0;

			if( recentFrame.equals( frame )) {
				for( i = 0; i < 3; i++ ) {
					if( !ctrlPoints[i+2].equals( recentCtrlPt[i] )) {
						ctrlPtAngle[i]	= ang[i];
						ctrlPoints[i+2].setLocation( cx + Math.cos( ctrlPtAngle[i] ) * rx,
													 cy + Math.sin( ctrlPtAngle[i] ) * ry );
						recentCtrlPt[i]	= ctrlPoints[i+2];
					}
				}
				if( orient ) {
					arcShape.setAngleStart( Math.toDegrees( -ang[0] ));
					arcShape.setAngleExtent( 360.0 - Math.toDegrees( ang[1] - ang[0] ));
				} else {
					arcShape.setAngleStart( Math.toDegrees( -ang[1] ));
					arcShape.setAngleExtent( Math.toDegrees( ang[1] - ang[0] ));
				}
			} else {
				arcShape.setFrame( frame );
				recentFrame.setFrame( frame );
				for( i = 0; i < 3; i++ ) {
					ctrlPoints[i+2].setLocation( cx + Math.cos( ctrlPtAngle[i] ) * rx,
												 cy + Math.sin( ctrlPtAngle[i] ) * ry );
					recentCtrlPt[i]	= ctrlPoints[i+2];
				}
			}
			
			return arcShape;
		}
		
//		public boolean canRotate()
//		{
//			return true;
//		}
		
		private void calcFrame( Point2D[] ctrlPoints )
		{
			frame.setFrame( Math.min( ctrlPoints[0].getX(), ctrlPoints[1].getX() ),
							Math.min( ctrlPoints[0].getY(), ctrlPoints[1].getY() ),
							Math.abs( ctrlPoints[1].getX() - ctrlPoints[0].getX() ),
							Math.abs( ctrlPoints[1].getY() - ctrlPoints[0].getY() ));
		}

		protected boolean initFunctionEvaluation( Point2D[] ctrlPoints )
		{
			int			i;
			double[]	ang   = new double[3];
			boolean		orient;
			double		cx, cy, rx, ry;
		
			rx		= Math.abs( ctrlPoints[1].getX() - ctrlPoints[0].getX() ) / 2;
			ry		= Math.abs( ctrlPoints[1].getY() - ctrlPoints[0].getY() ) / 2;
			cx		= Math.min( ctrlPoints[0].getX(), ctrlPoints[1].getX() ) + rx;
			cy		= Math.min( ctrlPoints[0].getY(), ctrlPoints[1].getY() ) + ry;

			for( i = 0; i < 3; i++ ) {
				ang[i]  = Math.atan2( (ctrlPoints[i+2].getY() - cy) / ry, (ctrlPoints[i+2].getX() - cx) / rx );
			}
			while( ang[1] < ang[0] ) ang[1] += MathUtil.PI2;
			while( ang[2] < ang[0] ) ang[2] += MathUtil.PI2;
			orient  = (ang[1] - ang[0]) / (ang[2] - ang[0]) < 1.0;

//System.err.println( "ang[0] "+(180/Math.PI*ang[0])+" ; ang[1] "+(180/Math.PI*ang[1])+" ; ang[2] "+(180/Math.PI*ang[2])+" ; orient "+orient );

			f_rx	= (float) rx;
			f_ry	= (float) ry;
			f_cx	= (float) cx;
			f_cy	= (float) cy;
			f_sa	= (float) ang[1];
			if( orient ) {
				f_da	= (float) (ang[1] - ang[0]);
				// don't ask me why this is working
				if( f_da < ang[2] - ang[0] ) f_da = (float) (Math.PI * 2 - f_da);
			} else {
				f_da	= (float) (ang[0] - ang[1]);
			}
			return true;
		}
		
		// f_x(t) = f_cx + cos( f_sa + t * f_da ) * f_rx; f_y(t) = f_cy + sin( f_sa + t * f_da ) * f_ry;
		protected void evaluateFunction( float[] argBuf, float[][] resultBuf, int len )
		{
			int		i;
			float   angle;
			float[] x   = resultBuf[0];
			float[] y   = resultBuf[1];
			
			for( i = 0; i < len; i++ ) {
				angle   = f_sa + argBuf[i] * f_da;
				x[i]	= f_cx + f_rx * (float) Math.cos( angle );
				y[i]	= f_cy + f_ry * (float) Math.sin( angle ); 
			}
		}
	}

// ---------------- Pencil Tool ---------------- 

	// XXX @todo  : outer class should release mouse motion listener
	// since we are using our own when dragging ?
	private class SurfacePencilTool
	extends AbstractTool
	implements ProcessingThread.Client, TrajectoryGenerator
	{
		// --- drag-and-drop ---
		
		private MouseEvent	dndFirstEvent   = null;		// event from the initial mouse button press; if null it's not a valid drag gesture
		private boolean		dndDragging		= false;	// true when mouse has been moved to a reasonable amount
//		private boolean		dndVelocity		= false;	// true when adjusting the velocity
		private Rectangle2D	dndRecentRect;				// for graphics rendering update
//		private Line2D		dndLine			= new Line2D.Double();
//		private GeneralPath dndFreehand		= new GeneralPath();
		private Vector		dndFreehandPoints = new Vector();
		private PointInTime dndLatest;
		private int			dndLatestIdx;
		private ProcessingThread renderThread	= null;
		
		// calculation of the velocity area
//		private double		lineAngle;								// of the drawn line
//		private GeneralPath vShape			= new GeneralPath();	// velocity visualization
		private double		vStart, vStop;							// velocity
//		private final double normedRadius   = 0.15;
	
		// function evaluation
		private float		f_scale, f_trans, f_diffx, f_diffy, f_begx, f_begy;
		private	int			f_idx;
		private	PointInTime f_pit, f_pit2;
		
		private final boolean	previewOnly;
		
		// painting
		private static final int PNT_SHADES = 8;
		private int			pntNum			= 0;
		private double[]	pntLocX			= new double[ PNT_SHADES ];
		private double[]	pntLocY			= new double[ PNT_SHADES ];
		
		// when the user presses the mouse and transport is not
		// running, we'll start it automatically ; in this case
		// weStartedTheTransport will be set to 'true' in order
		// to remember to stop the transport automatically when
		// the mouse button is released
		private boolean	weStartedTheTransport;

		// when the user presses the mouse, a trajectory replacement
		// will be installed that overrides the harddisk trajectory
		// with the current mouse position, until drag is completed or aborted
		private RealtimeProducer.TrajectoryReplacement trajRplc	= null;

		private Point2D	ptCurrentMouse;		// always the last recognized drag in virtual space

		private final Color[]   fadeAway	= {
			new Color( 0x00, 0x00, 0xFF, 0xFF ), new Color( 0x00, 0x00, 0xFF, 0xE0 ),
			new Color( 0x00, 0x00, 0xFF, 0xC0 ), new Color( 0x00, 0x00, 0xFF, 0xA0 ),
			new Color( 0x00, 0x00, 0xFF, 0x80 ), new Color( 0x00, 0x00, 0xFF, 0x60 ),
			new Color( 0x00, 0x00, 0xFF, 0x40 ), new Color( 0x00, 0x00, 0xFF, 0x20 )
		};

		private final Object[]				msgArgs		= new Object[3];
		private final MessageFormat  msgSingleSense		= new MessageFormat( getResourceString(
																	"forkToolSingleSenseMsg" ), Locale.US );   // XXX
		private final MessageFormat  msgSumSense		= new MessageFormat( getResourceString(
																	"forkToolSumSenseMsg" ), Locale.US );   // XXX
		
		private SurfacePencilTool( boolean previewOnly )
		{
			super();
			this.previewOnly = previewOnly;
		}
		
		public void toolAcquired( Component c )
		{
			super.toolAcquired( c );

			// essential inits already in finishGesture()
		}

		public void toolDismissed( Component c )
		{
			finishGesture( false );
		
			// wait for rendering to be completed
			while( renderThread != null && renderThread.isRunning() ) {
				renderThread.sync();
				renderThread = null;
			}

			super.toolDismissed( c );
		}
		
		private void finishGesture( boolean success )
		{
			if( trajRplc != null ) {
				transport.removeTrajectoryReplacement( trajRplc );
				trajRplc = null;
			}
			cursorInfo[2]	= EMPTY_STR;
			cursorInfo[3]	= EMPTY_STR;
			cursorInfo[4]	= EMPTY_STR;

			// clean up after DnD
			dndFirstEvent = null;
			if( dndDragging ) {
				dndDragging = false;
//				dndVelocity = false;
				repaint( virtualToScreenClip( dndRecentRect ));
				if( success && !previewOnly ) {
//					renderThread = new ProcessingThread( this, root, root, doc,
//					             						AbstractApplication.getApplication().getResourceString( "toolWriteTransmitter" ),
//					             						null, Session.DOOR_TIMETRNSMTE );
					renderThread = new ProcessingThread( this, root,
						AbstractApplication.getApplication().getResourceString( "toolWriteTransmitter" ));
					renderThread.start();
				}
			}
		}

		public void paintOnTop( Graphics2D g2 )
		{
			if( !dndDragging ) return;
			
			AffineTransform trnsRecent = g2.getTransform();
			int				i;
			Point2D			pt;
			
//			if( dndVelocity ) {
//				g2.setColor( colrAdjusting );
//				g2.fill( vShape );
//			}
			g2.setStroke( strkLine );
			
			if( pntNum < PNT_SHADES ) pntNum++;
			System.arraycopy( pntLocX, 0, pntLocX, 1, pntNum - 1 );
			System.arraycopy( pntLocY, 0, pntLocY, 1, pntNum - 1 );
			pt = (Point2D) dndFreehandPoints.get( dndLatestIdx );
			pntLocX[ 0 ] = pt.getX();
			pntLocY[ 0 ] = pt.getY();
			
			for( i = 0; i < pntNum; i++ ) {
				g2.setColor( fadeAway[ i ]);
				g2.translate( pntLocX[ i ], pntLocY[ i ]);
				g2.fill( shpCrossHair );
				g2.setTransform( trnsRecent );
			}
		}

//		private void updateVShape()
//		{
//			double beta1, beta2;
//			Rectangle2D dndCurrentRect;
//
//			vShape.reset();
//			beta1   = lineAngle - Math.PI/2;
//			beta2   = beta1 + Math.PI;
//			vShape.moveTo( (float) (dndLine.getX1() + normedRadius * vStart * Math.cos( beta1 )),
//						   (float) (dndLine.getY1() + normedRadius * vStart * Math.sin( beta1 )));
//			vShape.lineTo( (float) (dndLine.getX2() + normedRadius * vStop  * Math.cos( beta1 )),
//						   (float) (dndLine.getY2() + normedRadius * vStop  * Math.sin( beta1 )));
//			vShape.lineTo( (float) (dndLine.getX2() + normedRadius * vStop  * Math.cos( beta2 )),
//						   (float) (dndLine.getY2() + normedRadius * vStop  * Math.sin( beta2 )));
//			vShape.lineTo( (float) (dndLine.getX1() + normedRadius * vStart * Math.cos( beta2 )),
//						   (float) (dndLine.getY1() + normedRadius * vStart * Math.sin( beta2 )));
//			vShape.closePath();
//			dndCurrentRect  = vShape.getBounds2D();
//			efficientUpdateAndRepaint( dndRecentRect, dndCurrentRect, false );
//			dndRecentRect   = dndCurrentRect;
//		}

		// sync: caller must have on RCV, GRP
		private void calcCursorInfo()
		{
			int			rcvIdx, i;
			int			numRcv = doc.activeReceivers.size();
			float		f1 = 0.0f, f2 = 0.0f, f3 = 0.0f, f4;
			String		loudest1 = null, loudest2 = null;
			Receiver	rcv;
			float[]		sense	= new float[1];
			float[][]	points	= new float[2][1];
			
			points[0][0] = (float) ptCurrentMouse.getX();
			points[1][0] = (float) ptCurrentMouse.getY();
			
			for( rcvIdx = 0; rcvIdx < numRcv; rcvIdx++ ) {
				rcv = (Receiver) doc.activeReceivers.get( rcvIdx );
				rcv.getSensitivities( points, sense, 0, 1, 1 );
				f4  = sense[0];
				if( f4 > f1 ) {
					f2			= f1;
					loudest2	= loudest1;
					f1			= f4;
					loudest1	= rcv.getName();
				} else if( f4 > f2 ) {
					f2			= f4;
					loudest2	= rcv.getName();
				}
				if( prefRcvEqP ) {
					f3 += f4*f4;
				} else {
					f3 += f4;
				}
			}
			if( prefRcvEqP ) {
				f3 = (float) Math.sqrt( f3 );
			}

			i = 2;
			if( loudest1 != null ) {
				msgArgs[0]		= loudest1;
				msgArgs[1]		= new Double( f1 );
				msgArgs[2]		= new Double( MathUtil.linearToDB( f1 ));
				cursorInfo[i++]	= msgSingleSense.format( msgArgs );
			}
			if( loudest2 != null ) {
				msgArgs[0]		= loudest2;
				msgArgs[1]		= new Double( f2 );
				msgArgs[2]		= new Double( MathUtil.linearToDB( f2 ));
				cursorInfo[i++]	= msgSingleSense.format( msgArgs );
			}
			if( numRcv > 0 ) {
				msgArgs[1]		= new Double( f3 );
				msgArgs[2]		= new Double( MathUtil.linearToDB( f3 ));
				cursorInfo[i++]	= msgSumSense.format( msgArgs );
			}
			while( i < cursorInfo.length ) cursorInfo[ i++ ] = EMPTY_STR;
			
			showCursorInfo( ptCurrentMouse );
		}

		public void mouseEntered( MouseEvent e ) {}
		public void mouseExited( MouseEvent e ) {}

		// sync: this method attempts on time, trns and grp
		public void mousePressed( MouseEvent e )
		{
			if( renderThread != null && renderThread.isRunning() ) {
				renderThread.sync();
				renderThread = null;
			}
		
			long			when;
			java.util.List	collTrns;
			
			e.getComponent().requestFocus();

			// prepare DnD
			dndFirstEvent   = e;		// mouseDragged() will detect this
			dndDragging		= false;	// a follow-up mouse move is still required to start the drag
			dndFreehandPoints.clear();
			ptCurrentMouse	= screenToVirtual( snap( dndFirstEvent.getPoint(), false ));

			// check replacement
			if( trajRplc == null ) {
				if( doc.bird.attemptShared( Session.DOOR_TIMETRNSRCV | Session.DOOR_GRP, 250 )) {
					try {
						collTrns = doc.activeTransmitters.getAll();
						collTrns.retainAll( doc.selectedTransmitters.getAll() );
						trajRplc = new RealtimeProducer.TrajectoryReplacement(
										this, new Span( 0, doc.timeline.getLength() ), collTrns );
						transport.addTrajectoryReplacement( trajRplc );
						calcCursorInfo();
					} finally {
						doc.bird.releaseShared( Session.DOOR_TIMETRNSRCV | Session.DOOR_GRP );
					}
				}
			}

			// check auto-step-mode
			weStartedTheTransport = !(transport.isRunning() || previewOnly);
			if( weStartedTheTransport ) {
				rt_pos		= doc.timeline.getPosition();
				transport.goPlay();
			}
			
			when			= rt_pos;
			dndFreehandPoints.add( new PointInTime( ptCurrentMouse, when ));
			vStart			= 1.0;
			vStop			= 1.0;
			pntNum			= 0;
		} // mousePressed( MouseEvent e )

		public void mouseReleased( MouseEvent e )
		{
//			Point2D pit, pit2;
//		
//			if( dndDragging && e.isAltDown() ) {
//				pit			= (Point2D) dndFreehandPoints.firstElement();
//				pit2		= (Point2D) dndFreehandPoints.lastElement();
//				dndLine.setLine( pit.getX(), pit.getY(), pit2.getX(), pit2.getY() );
//				if( GraphicsUtil.getLineLength( dndLine ) > 0.0 ) {
//					dndVelocity	= true;
//					lineAngle	= Math.atan2( dndLine.getY2() - dndLine.getY1(), dndLine.getX2() - dndLine.getX1() );
//					vShape.reset();
//					updateVShape();
//				} else {
//					finishGesture( true );
//				}
//			} else {
				if( weStartedTheTransport ) transport.stopAndWait();	// need to wait so rt_pos is correct
				mouseDragged( e );	// add a last point for the current timeline pos!
				finishGesture( true );
//			}
		} // mouseReleased( MouseEvent e )

		public void mouseClicked( MouseEvent e )
		{
		} // mouseClicked( MouseEvent e )

		public void mouseMoved( MouseEvent e )
		{
//			ptCurrentMouse	= screenToVirtual( e.getPoint() );
//			
//			if( dndVelocity ) { // adjusting velocity shape
//				Point2D ptProjection	= GraphicsUtil.projectPointOntoLine( ptCurrentMouse, dndLine );
//
//				if( ptProjection.getX() >= Math.min( dndLine.getX1(), dndLine.getX2() ) &&
//					ptProjection.getX() <= Math.max( dndLine.getX1(), dndLine.getX2() )) {  // point lines inside line segm.
//				
//					vStart  = Math.min( 2.0, Math.max( 0.0, 2 * ptProjection.distance( dndLine.getX1(), dndLine.getY1() ) /
//																					   GraphicsUtil.getLineLength( dndLine )));
//					vStop   = 2.0 - vStart;
//				
//				} else {
//					if( Math.abs( ptProjection.getX() - dndLine.getX1() ) < Math.abs( ptProjection.getX() - dndLine.getX2() )) {
//						vStart  = 0.0;
//						vStop   = 2.0;
//					} else {
//						vStart  = 2.0;
//						vStop   = 0.0;
//					}
//				}
//				
//				updateVShape();
//			}
		} // mouseMoved( MouseEvent e )

		public void mouseDragged( MouseEvent e )
		{
			if( dndFirstEvent == null ) return;		// not a valid drag gesture

			long		when, delta, delta2;
			PointInTime pit, pit2, recentLatest;
			int			i;
			double		d1;
			Span		span;

			if( !dndDragging ) {					// test if mouse move was sufficient
				dndDragging		= true;
				dndRecentRect   = new Rectangle2D.Double();
				dndLatest		= (PointInTime) dndFreehandPoints.firstElement();
				dndLatestIdx	= 0;
			}

			ptCurrentMouse  = screenToVirtual( e.getPoint() );
			when		= rt_pos;
			recentLatest= dndLatest;
			dndLatest   = new PointInTime( ptCurrentMouse, when );
			pit			= (PointInTime) dndFreehandPoints.lastElement();
			pit2		= (PointInTime) dndFreehandPoints.firstElement();
			if( recentLatest.getWhen() < when ) {	// add after recent latest
				dndLatestIdx++;
				for( i = dndLatestIdx; i < dndFreehandPoints.size(); i++ ) {
					if( ((PointInTime) dndFreehandPoints.get( i )).getWhen() > when ) break;
				}
				while( i > dndLatestIdx ) {
					dndFreehandPoints.remove( --i );
				}
				dndFreehandPoints.add( dndLatestIdx, dndLatest );
				
			} else if( recentLatest.getWhen() == when ) {	// replace recent latest
				dndFreehandPoints.set( dndLatestIdx, dndLatest );
				
			} else {	// loop has restarted
				dndLatestIdx = 0;
				for( i = 0; i < dndFreehandPoints.size(); i++ ) {
					if( ((PointInTime) dndFreehandPoints.get( i )).getWhen() > when ) break;
				}
				while( i > 0 ) {
					dndFreehandPoints.remove( --i );
				}
				span = doc.timeline.getSelectionSpan();
				if( !span.isEmpty() && when > span.getStart() ) {
					delta   = span.getStop() - pit.getWhen();
					delta2  = pit2.getWhen() - span.getStart();
					if( delta > 0 && delta2 >= 0 ) {
						if( delta2 > 0 && when > span.getStart() ) {  // add interpolated point at loop start
							d1  = (double) delta / (double) (delta + delta2);
							pit	= new PointInTime( pit.getX() + (pit2.getX() - pit.getX()) * d1,
												   pit.getY() + (pit2.getY() - pit.getY()) * d1, span.getStart() );
							dndFreehandPoints.add( dndLatestIdx++, pit );
						}
						if( delta > 1 && when < span.getStop() - 1 ) {  // add interpolated point at loop end
							d1  = (double) (delta - 1) / (double) (delta + delta2);
							pit	= new PointInTime( pit.getX() + (pit2.getX() - pit.getX()) * d1,
												   pit.getY() + (pit2.getY() - pit.getY()) * d1, span.getStop() - 1 );
							dndFreehandPoints.add( pit );
						}
					}
				}
				dndFreehandPoints.add( dndLatestIdx, dndLatest );
			} // if( pit.getWhen() < when )

			if( doc.bird.attemptShared( Session.DOOR_RCV | Session.DOOR_GRP, 250 )) {
				try {
					calcCursorInfo();
				}
				finally {
					doc.bird.releaseShared( Session.DOOR_RCV | Session.DOOR_GRP );
				}
			}
		} // mouseDragged( MouseEvent e )

		protected void cancelGesture()
		{
			finishGesture( false );
		}

		/**
		 *  Write the pencil movement
		 *
		 *  time warp is performed as follows:
		 *  t'(t) = v_start * t + (v_end - v_start)/2T * t^2 , v_start = (0...2)/T, v_end = 2/T - v_start 
		 */
		public int processRun( ProcessingThread context )
		{
			Transmitter						trns;
			MultirateTrackEditor			mte;
			float[][]						interpBuf;
			float[]							warpedTime;
			int								i, j, len;
			long							t, tt;
			BlendSpan						bs;
			// interpLen entspricht 'T' in der Formel (Gesamtzeit), interpOff entspricht 't' (aktueller Zeitpunkt)
			long							start, interpOff, interpLen, progressLen;
			long							progress	= 0;
			Span							span;
			float							v_start_norm, dv_norm;
			double							t_norm;
			SyncCompoundSessionObjEdit	edit;
			java.util.List					collTransmitters;
			PointInTime						pit, pit2;
			boolean							success		= false;
			BlendContext					bc			= root.getBlending();

			if( dndFreehandPoints.size() < 2 ) return DONE;
			pit		= (PointInTime) dndFreehandPoints.firstElement();
			pit2	= (PointInTime) dndFreehandPoints.lastElement();
			span	= new Span( pit.getWhen(), pit2.getWhen() );
			if( span.getLength() < 2 ) return DONE;
				
			interpLen   = span.getLength();
			warpedTime  = new float[(int) Math.min( interpLen, 4096 )];
			interpBuf   = new float[2][ warpedTime.length ];

			t_norm			= 1.0 / (interpLen - 1);
			v_start_norm	= (float) (vStart * t_norm);
			dv_norm			= (float) ((vStop - vStart) / 2 * t_norm * t_norm);

			initFunctionEvaluation();
			
			collTransmitters	= doc.selectedTransmitters.getAll();
			progressLen			= interpLen*collTransmitters.size();
			edit				= new SyncCompoundSessionObjEdit( this, doc, collTransmitters,
											Transmitter.OWNER_TRAJ, null, null, Session.DOOR_TIMETRNSMTE );

			try {
				for( i = 0; i < collTransmitters.size(); i++ ) {
					trns	= (Transmitter) collTransmitters.get( i );
					mte		= trns.getTrackEditor();

					bs = mte.beginOverwrite( span, bc, edit );
					for( start = span.getStart(), interpOff = 0; start < span.getStop();
						 start += len, interpOff += len ) {
						 
						len = (int) Math.min( 4096, span.getStop() - start );
						for( j = 0, t = interpOff; j < len; j++, t++ ) {
							tt				= t*t;
							warpedTime[j]   = v_start_norm * t + dv_norm * tt;
//							warpedTime[j]   = (v_start_norm * t + dv_norm * tt) * 1.5f - 0.25f;  // extrap. 
						}
						evaluateFunction( warpedTime, interpBuf, len );
						mte.continueWrite( bs, interpBuf, 0, len );
						progress += len;
						context.setProgression( (float) progress / (float) progressLen );
					}
					mte.finishWrite( bs, edit );
				} // for( i = 0; i < collTransmitters.size(); i++ )
				
				edit.end(); // fires doc.tc.modified()
				doc.getUndoManager().addEdit( edit );
				success = true;
			}
			catch( IOException e1 ) {
				edit.cancel();
				context.setException( e1 );
			}
			
			return success ? DONE : FAILED;
		} // run()

		public void processFinished( ProcessingThread context ) {}
		public void processCancel( ProcessingThread context ) {}

		private void initFunctionEvaluation()
		{
			PointInTime pit, pit2;

			pit		= (PointInTime) dndFreehandPoints.firstElement();
			pit2	= (PointInTime) dndFreehandPoints.lastElement();
		
			f_scale = (float) (pit2.getWhen() - pit.getWhen());
			f_trans = (float) pit.getWhen();
			f_idx   = 1;
			f_pit   = pit;
			f_pit2  = (PointInTime) dndFreehandPoints.get( 1 );
			f_diffx = (float) ((f_pit2.getX() - f_pit.getX()) / (f_pit2.getWhen() - f_pit.getWhen()));
			f_diffy = (float) ((f_pit2.getY() - f_pit.getY()) / (f_pit2.getWhen() - f_pit.getWhen()));
			f_begx  = (float) f_pit.getX();
			f_begy  = (float) f_pit.getY();
		}
		
		// p(t) = (1 - 3t + 3t^2 - t^3) p1 + (3t - 6t^2 + 3t^3) cp1 + (3t^2 - 3t^3) cp2 + t^3 p2
		private void evaluateFunction( float[] argBuf, float[][] resultBuf, int len )
		{
			float   t, off;
			float[] x   = resultBuf[0];
			float[] y   = resultBuf[1];
			int		i;
		
			for( i = 0; i < len; i++ ) {
				t   = argBuf[i] * f_scale + f_trans;
				while( t > f_pit2.getWhen() ) {
					f_pit   = f_pit2;
					f_idx++;
					f_pit2  = (PointInTime) dndFreehandPoints.get( f_idx );
					f_diffx = (float) ((f_pit2.getX() - f_pit.getX()) / (f_pit2.getWhen() - f_pit.getWhen()));
					f_diffy = (float) ((f_pit2.getY() - f_pit.getY()) / (f_pit2.getWhen() - f_pit.getWhen()));
					f_begx  = (float) f_pit.getX();
					f_begy  = (float) f_pit.getY();
				}
				off		= t - f_pit.getWhen();
				x[i]	= f_begx + off * f_diffx;
				y[i]	= f_begy + off * f_diffy;
			}
		}
		
	// ---------- TrajectoryGenerator interface ----------

		public void read( Span span, float[][] frames, int off )
		throws IOException
		{
			int		stop	= (int) (span.getLength() + off);
			float	x		= (float) ptCurrentMouse.getX();
			float	y		= (float) ptCurrentMouse.getY();
			
			for( ; off < stop; off++ ) {
				frames[ 0 ][ off ] = x;
				frames[ 1 ][ off ] = y;
			}
		}
	} // class SurfacePanePencilTool
} // class SurfacePane