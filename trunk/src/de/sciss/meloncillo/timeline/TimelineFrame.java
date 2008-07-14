/*
 *  TimelineFrame.java
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
 *		01-Aug-04   moved trns.listener registration from dyn.list to constructor
 *		12-Aug-04   commented. some clean ups.
 *      24-Dec-04   support for intruding-grow-box prefs
 *		26-Mar-05	uses separate tools and tool bar; new keyboard shortcuts
 */

/**
 *  TO-DOs : remove all the 'root' variables and redesign window
 *  registration and access
 */
 
package de.sciss.meloncillo.timeline;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.event.MouseInputAdapter;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.DynamicListening;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.GraphicsHandler;
import de.sciss.app.LaterInvocationManager;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.BasicWindowHandler;
import de.sciss.common.ProcessingThread;
import de.sciss.common.ShowWindowAction;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.ComponentBoundsRestrictor;
import de.sciss.gui.ComponentHost;
import de.sciss.gui.CoverGrowBox;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.GradientPanel;
import de.sciss.gui.MenuAction;
import de.sciss.gui.MenuRoot;
import de.sciss.gui.ModificationButton;
import de.sciss.gui.ProgressComponent;
import de.sciss.gui.StretchedGridLayout;
import de.sciss.gui.TopPainter;
import de.sciss.gui.TreeExpanderButton;
import de.sciss.gui.VectorSpace;
import de.sciss.io.Marker;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.edit.BasicCompoundEdit;
import de.sciss.meloncillo.edit.TimelineVisualEdit;
import de.sciss.meloncillo.gui.AbstractTool;
import de.sciss.meloncillo.gui.Axis;
import de.sciss.meloncillo.gui.GraphicsUtil;
import de.sciss.meloncillo.gui.MainFrame;
import de.sciss.meloncillo.gui.MenuFactory;
import de.sciss.meloncillo.gui.ObserverPalette;
import de.sciss.meloncillo.gui.ToolAction;
import de.sciss.meloncillo.gui.ToolActionEvent;
import de.sciss.meloncillo.gui.ToolActionListener;
import de.sciss.meloncillo.gui.WaveformView;
import de.sciss.meloncillo.io.AudioTrail;
import de.sciss.meloncillo.io.DecimatedTrail;
import de.sciss.meloncillo.io.DecimatedWaveTrail;
import de.sciss.meloncillo.io.DecimationInfo;
import de.sciss.meloncillo.io.TrackList;
import de.sciss.meloncillo.realtime.RealtimeConsumer;
import de.sciss.meloncillo.realtime.RealtimeConsumerRequest;
import de.sciss.meloncillo.realtime.RealtimeContext;
import de.sciss.meloncillo.realtime.RealtimeProducer;
import de.sciss.meloncillo.realtime.Transport;
import de.sciss.meloncillo.realtime.TransportListener;
import de.sciss.meloncillo.session.DocumentFrame;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionCollection;
import de.sciss.meloncillo.session.SessionObject;
import de.sciss.meloncillo.transmitter.Transmitter;
import de.sciss.meloncillo.transmitter.TransmitterEditor;
import de.sciss.meloncillo.util.PrefsUtil;
import de.sciss.meloncillo.util.TransferableCollection;
import de.sciss.timebased.Trail;

/**
 *  One of the core GUI elements: The
 *  horizontal timeline display with all
 *  the transmitter trajectory tracks.
 *  <p>
 *  The frame contains a scroll pane with
 *  instances of the transmitter's default
 *  <code>TransmitterEditor</code>s.
 *  left row header contains <code>TransmitterRowHeader</code>
 *  objects, mainly labels for the transmitter's names, but
 *  also means of selecting or deselecting transmitters.
 *  The scroll pane's column header
 *  (an element which never gets scrolled) is a <code>TimelineAxis</code>,
 *  a ruler for displaying the timeline indices and allowing to position
 *  and select the timeline.
 *  <p>
 *  Global keyboard commands (Ctrl+Cursor-Keys) are registered
 *  to zoom into the selected tracks (vertically) or into the
 *  timeline (horizontally), amongst others.
 *  <p>
 *  A special inner class view port object is installed in the scroll bar
 *  to allow fast graphics display when the transport is running. In this
 *  case the timeline frame installs a realtime consumer and listens to
 *  the transport notification ticks. Because the timeline position hairline
 *  has to be painted over all tracks, this is done in the view port's
 *  <code>paintChildren</code> method. This requires the view port to be
 *  set to <code>BACKINGSTORE_SCROLL_MODE</code> so the children's views
 *  are recalled from a backing store image -- repainting the vector editors
 *  all the time is much too slow. Unfortunately this produces some graphics
 *  artefacts if the vertical scrollbar is smoothly dragged.
 *	<p><pre>
 *	keyb.shortcuts:	
 *	</pre>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 19-Jun-08
 *
 *  @see	de.sciss.meloncillo.transmitter.TransmitterRowHeader
 *  @see	de.sciss.meloncillo.transmitter.TransmitterEditor
 *  @see	de.sciss.meloncillo.timeline.TimelineAxis
 *  @see	de.sciss.meloncillo.timeline.TimelineScroll
 *  @see	javax.swing.JViewport#setScrollMode( int )
 *
 *  @todo	the VM 1.4.2_05 introduces a new bug which causes
 *			the timeline display in RT playback to flicker and besides
 *			makes the whole graphics update really slow. We should think
 *			about creating our own backing store image.
 *	@todo	when resizing the window while transport is playing there's
 *			a null pointer exception in the paintDirty method since the
 *			backing store image seems to have been invalidated
 *	@todo	deal with the viewport graphics artefacts.
 *	@todo	Paste + Clear : use blending
 *	@todo	pointer tool : ctrl+drag doesn't work.
 */
public class TimelineFrame
extends DocumentFrame
implements  TimelineListener, ToolActionListener,
			DecimatedWaveTrail.AsyncListener,
			TransportListener, PreferenceChangeListener,
			DynamicListening, ClipboardOwner
{
	protected final Session					doc;
	
    private final TimelineAxis				timeAxis;
    protected final MarkerAxis				markAxis;
    protected final TrackRowHeader			markAxisHeader;
	protected final TimelineScroll			scroll;
	protected final Transport				transport;
	
	protected Span							timelineSel;
	protected Span							timelineVis;
	protected long							timelinePos;
	protected long							timelineLen;
	protected double						timelineRate;

	private final JPanel					ggTrackPanel;
	protected final WaveformView			waveView;
	protected final ComponentHost			wavePanel;
	private final JPanel					waveHeaderPanel;
	protected final JPanel					channelHeaderPanel;
	private final JPanel					flagsPanel;
	private final JPanel					rulersPanel;
//	private final JPanel					metersPanel;
	private final List						collChannelHeaders		= new ArrayList();
	protected final List					collChannelRulers		= new ArrayList();
//	private final List						collChannelMeters		= new ArrayList();
//	private PeakMeter[]						channelMeters			= new PeakMeter[ 0 ];
	
//	private final JLabel					lbSRC;
	protected final TreeExpanderButton		ggTreeExp;
	
	private DecimatedTrail					asyncTrail				= null;

	// --- tools ---
	
	private final   Map						tools					= new HashMap();
	private			AbstractTool			activeTool				= null;
	private final	TimelinePointerTool		pointerTool;

	// --- actions ---
	private final static String				plugInPackage			= "de.sciss.eisenkraut.render.";
	private final static String				fscapePackage			= "de.sciss.fscape.render.";

//	private final ActionRevealFile			actionRevealFile;
//	private final ActionSelectAll			actionSelectAll;
//	private final MenuAction				actionProcess, actionFadeIn, actionFadeOut, actionGain,
//											actionInvert, // actionMix,
//											actionReverse, actionRotateChannels, // actionSilence, 
//											actionFScNeedlehole,
//											actionDebugDump, actionDebugVerify, actionInsertRec;
//	protected final ActionProcessAgain		actionProcessAgain;

	private final ActionSpanWidth			actionIncHoriz, actionDecHoriz;
	protected final ActionScroll			actionZoomAllOut;
//	private final ActionVerticalZoom		actionIncVertMax, actionDecVertMax;
//	private final ActionVerticalZoom		actionIncVertMin, actionDecVertMin;

//	private final AbstractWindow.Adapter	winListener;

	private final JLabel					lbWriteProtected;
	private boolean							writeProtected			= false;
	protected boolean						wpHaveWarned			= false;
	
//	private final ShowWindowAction			actionShowWindow;
		
	private static final String smpPtrn			= "ch.{3} @ {0,number,0}";
	private static final String timePtrn		= "ch.{3} @ {1,number,integer}:{2,number,00.000}";
	protected final MessageFormat msgCsr1		= new MessageFormat( timePtrn, Locale.US );
	protected final MessageFormat msgCsr2PCMFloat = new MessageFormat( "{4,number,0.000} ({5,number,0.00} dBFS)", Locale.US );
	protected final MessageFormat msgCsr3PCMInt	= new MessageFormat( "= {6,number,0} @ {7,number,integer}-bit int", Locale.US );
	protected final MessageFormat msgCsr2Peak	= new MessageFormat( "peak {4,number,0.000} ({5,number,0.00} dBFS)", Locale.US );
	protected final MessageFormat msgCsr3RMS	= new MessageFormat( "eff {6,number,0.000} ({7,number,0.00} dBFS)", Locale.US );
	protected int					csrInfoBits;
	protected boolean				csrInfoIsInt;
	protected static final double TWENTYDIVLOG10 = 20 / Math.log( 10 );

	private final Color colrClear				= new Color( 0xA0, 0xA0, 0xA0, 0x00 );
	
	// --------- former viewport ---------
	// --- painting ---
	private final Color colrSelection			= GraphicsUtil.colrSelection;
	private final Color colrSelection2			= new Color( 0x00, 0x00, 0x00, 0x20 );  // selected timeline span over unselected trns
	protected final Color colrPosition			= new Color( 0xFF, 0x00, 0x00, 0x7F );
	protected final Color colrZoom				= new Color( 0xA0, 0xA0, 0xA0, 0x7F );
	protected Rectangle	vpRecentRect			= new Rectangle();
	protected int		vpPosition				= -1;
	private Rectangle   vpPositionRect			= new Rectangle();
	protected final ArrayList vpSelections		= new ArrayList();
	protected final ArrayList vpSelectionColors	= new ArrayList();
	protected Rectangle	vpSelectionRect			= new Rectangle();
	
	private Rectangle   vpUpdateRect			= new Rectangle();
	protected Rectangle	vpZoomRect				= null;
	private float[]		vpDash					= { 3.0f, 5.0f };
	private float		vpScale;

	protected final Stroke[] vpZoomStroke			= {
		new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, vpDash, 0.0f ),
		new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, vpDash, 4.0f ),
		new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, vpDash, 6.0f ),
	};
	protected int		vpZoomStrokeIdx			= 0;

	protected boolean	waveExpanded			= true;	// XXX should keep that in some prefs
	protected boolean	viewMarkers;
	protected boolean	markVisible;
	private boolean		chanMeters				= false;
	private boolean		forceMeters				= false;
	
	protected final TimelineToolBar			timeTB;
//	private final TransportToolBar			transTB;

	// --- progress bar ---
	
//	private final JTextField				ggAudioFileDescr;
//	private final ProgressPanel				pProgress;
//	private final CrossfadePanel			pOverlay;

	private final boolean					internalFrames;

	protected final BasicApplication		app;
//	private final SuperColliderClient		superCollider;
//	private final PeakMeterManager			lmm;

	protected boolean						disposed		= false;
	
	private final Timer						playTimer;
	private double							playRate		= 1.0;
	
	protected final ComponentBoundsRestrictor cbr;
	
	private static Point					lastLeftTop		= new Point();
	private static final String				KEY_TRACKSIZE	= "tracksize";
	
	private int								verticalScale;
	
	protected static final Cursor[]			zoomCsr;
	
	static {
		final Toolkit tk		= Toolkit.getDefaultToolkit();
		final Point   hotSpot	= new Point( 6, 6 );
		zoomCsr					= new Cursor[] {
			tk.createCustomCursor( tk.createImage(
			    ToolAction.class.getResource( "zoomin.png" )), hotSpot, "zoom-in" ),
			tk.createCustomCursor( tk.createImage(
				ToolAction.class.getResource( "zoomout.png" )), hotSpot, "zoom-out" )
		};
	}

	/**
	 *  Constructs a new timeline window with
	 *  all the sub elements. Installs the
	 *  global key commands. (a TimelineFrame
	 *  should be created only once in the application).
	 *
	 *  @param  doc		session document
	 */
	public TimelineFrame( final Session doc )
	{
		super( doc );

		app					= (BasicApplication) AbstractApplication.getApplication();
		setTitle( app.getResourceString( "frameTimeline" ));
		
		this.doc			= doc;
		transport			= doc.getTransport();
		timelinePos			= doc.timeline.getPosition();
		timelineSel			= doc.timeline.getSelectionSpan();
		timelineVis			= doc.timeline.getVisibleSpan();
		timelineRate		= doc.timeline.getRate();
		timelineLen			= doc.timeline.getLength();
		
//		superCollider		= SuperColliderClient.getInstance();
//
//		lmm					= new PeakMeterManager( superCollider.getMeterManager() );

		final Container					cp			= getContentPane();
		final InputMap					imap		= getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
		final ActionMap					amap		= getActionMap();
		final AbstractButton			ggAudioInfo, ggRevealFile;
		final int						myMeta		= BasicMenuFactory.MENU_SHORTCUT == InputEvent.CTRL_MASK ?
			InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK : BasicMenuFactory.MENU_SHORTCUT;	// META on Mac, CTRL+SHIFT on PC
		final TopPainter				trackPainter;
		final MenuRoot					mr;
		final JPanel					topPane		= GUIUtil.createGradientPanel();
		final Trail.Listener			waveTrailListener;
		Box								box;

		internalFrames		= app.getWindowHandler().usesInternalFrames();

		timeTB		= new TimelineToolBar( doc );
//		transTB		= new TransportToolBar( doc );

		wavePanel			= new ComponentHost();
        timeAxis			= new TimelineAxis( doc, wavePanel );
		markAxis			= new MarkerAxis( doc, wavePanel );
		viewMarkers			= app.getUserPrefs().getBoolean( PrefsUtil.KEY_VIEWMARKERS, false );
		markVisible			= viewMarkers && waveExpanded;
		markAxisHeader		= new TrackRowHeader( doc.markerTrack, doc.getTracks(), doc.getMutableSelectedTracks(), doc.getUndoManager() );
		markAxisHeader.setPreferredSize( new Dimension( 63, markAxis.getPreferredSize().height ));	// XXX
		markAxisHeader.setMaximumSize( new Dimension( 128, markAxis.getMaximumSize().height ));		// XXX
		if( markVisible ) {
			markAxis.startListening();
		} else {
			markAxis.setVisible( false );
			markAxisHeader.setVisible( false );
		}
		flagsPanel			= new JPanel( new StretchedGridLayout( 0, 1, 1, 1 ));
//		metersPanel			= new JPanel( new StretchedGridLayout( 0, 1, 1, 1 )); // SpringPanel( 0, 0, 1, 1 );
		rulersPanel			= new JPanel( new StretchedGridLayout( 0, 1, 1, 1 ));
//		lmm.setDynamicComponent( metersPanel );
		waveHeaderPanel		= new JPanel( new BorderLayout() );
		channelHeaderPanel	= new JPanel();
		channelHeaderPanel.setLayout( new BoxLayout( channelHeaderPanel, BoxLayout.X_AXIS ));
final Box bbb = Box.createVerticalBox();
final GradientPanel gp = GUIUtil.createGradientPanel();
gp.setBottomBorder( true );
gp.setLayout( null );
gp.setPreferredSize( new Dimension( 0, timeAxis.getPreferredSize().height ));
bbb.add( gp );
bbb.add( markAxisHeader );
		waveHeaderPanel.add( bbb, BorderLayout.NORTH );
		channelHeaderPanel.add( flagsPanel );
//		channelHeaderPanel.add( metersPanel );
		channelHeaderPanel.add( rulersPanel );
		waveHeaderPanel.add( channelHeaderPanel, BorderLayout.CENTER );

		waveView			= new WaveformView( doc, wavePanel );
		wavePanel.setLayout( new BoxLayout( wavePanel, BoxLayout.Y_AXIS ));
		wavePanel.add( timeAxis );
		wavePanel.add( markAxis );
		wavePanel.add( waveView );

        scroll				= new TimelineScroll( doc );
		ggTrackPanel		= new JPanel( new BorderLayout() );
		ggTrackPanel.add( wavePanel, BorderLayout.CENTER );
		ggTrackPanel.add( waveHeaderPanel, BorderLayout.WEST );
		ggTrackPanel.add( scroll, BorderLayout.SOUTH );

		lbWriteProtected	= new JLabel();
// EEE
//		ggAudioInfo			= new ModificationButton( ModificationButton.SHAPE_INFO );
//		ggAudioInfo.setAction( new ActionAudioInfo() );
//		ggRevealFile		= new ModificationButton( ModificationButton.SHAPE_REVEAL );
//		actionRevealFile	= new ActionRevealFile();
//		ggRevealFile.setAction( actionRevealFile );
//		ggAudioFileDescr	= new JTextField( 32 );
//		ggAudioFileDescr.setEditable( false );
//		ggAudioFileDescr.setFocusable( false );
//		ggAudioFileDescr.setBackground( null );
//		ggAudioFileDescr.setBorder( null );

//		lbSRC				= new JLabel( getResourceString( "buttonSRC" ));
//		lbSRC.setForeground( colrClear );
		box					= Box.createHorizontalBox();
		box.add( Box.createHorizontalStrut( 4 ));
		box.add( lbWriteProtected );
// EEE
//		box.add( ggAudioInfo );
//		box.add( ggRevealFile );
//		box.add( Box.createHorizontalStrut( 4 ));
//
//		pProgress			= new ProgressPanel();
//		pOverlay			= new CrossfadePanel();
//		pOverlay.setComponentA( ggAudioFileDescr );
//		pOverlay.setComponentB( pProgress );
//		box.add( pOverlay );
//		
//		box.add( Box.createHorizontalStrut( 4 ));
//		box.add( lbSRC );
		box.add( CoverGrowBox.create( 2, 0 ));
//
//		updateAFDGadget();
//		updateCursorFormat();
//
// ----- afr export -----
//		final JButton ggExportAFR = new JButton( getResourceString( "buttonDragRegion" ), new ImageIcon( getClass().getResource( "dragicon.png" )));
//		ggExportAFR.setTransferHandler( new AFRTransferHandler() );
//		final MouseInputAdapter expAFRmia = new MouseInputAdapter() {
//			private MouseEvent dndInit = null;
//			private boolean dndStarted = false;
//
//			public void mousePressed( MouseEvent e )
//			{
//				dndInit		= e;
//				dndStarted	= false;
//			}
//			
//			public void mouseReleased( MouseEvent e )
//			{
//				dndInit		= null;
//				dndStarted	= false;
//			}
//			
//			public void mouseDragged( MouseEvent e )
//			{
//				if( !dndStarted && (dndInit != null) &&
//					((Math.abs( e.getX() - dndInit.getX() ) > 5) ||
//					 (Math.abs( e.getY() - dndInit.getY() ) > 5))) {
//			
//					JComponent c = (JComponent) e.getSource();
//					c.getTransferHandler().exportAsDrag( c, e, TransferHandler.COPY );
//					dndStarted = true;
//				}
//			}
//		};
//		
//		ggExportAFR.addMouseListener( expAFRmia );
//		ggExportAFR.addMouseMotionListener( expAFRmia );
//
//		timeTB.add( Box.createHorizontalStrut( 4 ));
//		timeTB.addButton( ggExportAFR );
// ----------
		
		topPane.setBorder( BorderFactory.createEmptyBorder( 2, 2, 2, 2 ));
		timeTB.setOpaque( false );
		topPane.add( timeTB );
//		transTB.setOpaque( false );
//		topPane.add( transTB );
		topPane.add( Box.createHorizontalGlue() );
		cbr			= new ComponentBoundsRestrictor();
		ggTreeExp	= new TreeExpanderButton();
		ggTreeExp.setExpandedToolTip( getResourceString( "buttonExpWaveTT" ));
		ggTreeExp.setCollapsedToolTip( getResourceString( "buttonCollWaveTT" ));
		ggTreeExp.setExpanded( true );
		ggTreeExp.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				final Dimension d	= getSize();
				
				waveExpanded	= ggTreeExp.isExpanded();
				markVisible		= viewMarkers && waveExpanded;
				
				if( waveExpanded ) {
					cbr.remove( getWindow() );
					waveView.setVisible( true );
					channelHeaderPanel.setVisible( true );
					if( viewMarkers ) {
						markAxis.setVisible( true );
						markAxisHeader.setVisible( true );
					}
					scroll.setVisible( true );
					timeTB.setVisible( true );
					pack();

				} else {
					checkDecimatedTrails();
					setPreferredSize( getSize() );

					waveView.setVisible( false );
					channelHeaderPanel.setVisible( false );
					markAxis.setVisible( false );
					markAxisHeader.setVisible( false );
					scroll.setVisible( false );
					timeTB.setVisible( false );
					actionZoomAllOut.perform();

					final int h = d.height - (waveView.getHeight() + scroll.getHeight() +
					 	(viewMarkers ? markAxis.getHeight() : 0));
					setSize( new Dimension( d.width - timeTB.getWidth(), h ));
					cbr.setMinimumHeight( h );
					cbr.setMaximumHeight( h );
					cbr.add( getWindow() );
				}
			}
		});
		topPane.add( ggTreeExp );
		
		gp.setGradientShift( 0, topPane.getPreferredSize().height );
		
		cp.add( topPane, BorderLayout.NORTH );
		cp.add( ggTrackPanel, BorderLayout.CENTER );
		cp.add( box, BorderLayout.SOUTH );
		
		// --- Tools ---
		
		pointerTool = new TimelinePointerTool();
		tools.put( new Integer( ToolAction.POINTER ), pointerTool );
		tools.put( new Integer( ToolAction.ZOOM ), new TimelineZoomTool() );

		// ---- TopPainter ----

		trackPainter	= new TopPainter() {
			public void paintOnTop( Graphics2D g2 )
			{
				Rectangle r;

				r = new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() ); // getViewRect();
				if( !vpRecentRect.equals( r )) {
					recalcTransforms( r );
				}

				for( int i = 0; i < vpSelections.size(); i++ ) {
					r = (Rectangle) vpSelections.get( i );
					g2.setColor( (Color) vpSelectionColors.get( i ));
					g2.fillRect( vpSelectionRect.x, r.y - vpRecentRect.y, vpSelectionRect.width, r.height );
				}
				
				if( markVisible ) {
					markAxis.paintFlagSticks( g2, vpRecentRect );
				}
				
				g2.setColor( colrPosition );
				g2.drawLine( vpPosition, 0, vpPosition, vpRecentRect.height );

				if( vpZoomRect != null ) {
					g2.setColor( colrZoom );
					g2.setStroke( vpZoomStroke[ vpZoomStrokeIdx ]);
					g2.drawRect( vpZoomRect.x, vpZoomRect.y, vpZoomRect.width, vpZoomRect.height );
				}
			}
		};
		wavePanel.addTopPainter( trackPainter );

		// ---- listeners ----

		doc.timeline.addTimelineListener( this );
//		doc.addListener( this );
		
//		checkDecimatedTrails();

		waveTrailListener = new Trail.Listener() {
			public void trailModified( Trail.Event e )
			{
				if( !waveExpanded || !e.getAffectedSpan().touches( timelineVis )) return;
			
				updateOverviews( false, false );
			}
		};
// EEE
//		doc.getAudioTrail().addListener( waveTrailListener );
		
// EEE
//		doc.audioTracks.addListener( new SessionCollection.Listener() { ... });
		doc.getActiveTransmitters().addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
//System.out.println( "YOOOOOOOOOOOOOOOOO" );
				documentUpdate();
				updateSelectionAndRepaint();
				final List coll = e.getCollection();
				switch( e.getModificationType() ) {
				case SessionCollection.Event.ACTION_ADDED:
					for( int i = 0; i < coll.size(); i++ ) {
						((Transmitter) coll.get( i )).getAudioTrail().addListener( waveTrailListener );
					}
					break;
					
				case SessionCollection.Event.ACTION_REMOVED:
					for( int i = 0; i < coll.size(); i++ ) {
						((Transmitter) coll.get( i )).getAudioTrail().removeListener( waveTrailListener );
					}
					break;
				
				default:
					break;
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) { /* ignored */ }

			public void sessionObjectChanged( SessionCollection.Event e )
			{
				// nothing
			}
		});
		
		doc.getSelectedTracks().addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				updateSelectionAndRepaint();
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) { /* ignore */ }
			public void sessionObjectChanged( SessionCollection.Event e ) { /* ignore */ }
		});
	
		transport.addTransportListener( this );

		doc.markers.addListener( new Trail.Listener() {
			public void trailModified( Trail.Event e )
			{
				repaintMarkers( e.getAffectedSpan() );
			}
		});
		                                
//		winListener = new AbstractWindow.Adapter() {
//			public void windowClosing( AbstractWindow.Event e ) {
//				actionClose.perform();
//			}
//
//			public void windowActivated( AbstractWindow.Event e )
//			{
//				// need to check 'disposed' to avoid runtime exception in doc handler if document was just closed
//				if( !disposed ) {
//					app.getDocumentHandler().setActiveDocument( DocumentFrame.this, doc );
//					((BasicWindowHandler) app.getWindowHandler()).setMenuBarBorrower( DocumentFrame.this );
//				}
//			}
//		};
//		this.addListener( winListener );

		waveView.addComponentListener( new ComponentAdapter() {
			public void componentResized( ComponentEvent e )
			{
				updateSelectionAndRepaint();
			}
		});
		
		timeTB.addToolActionListener( this );
		timeTB.selectTool( ToolAction.POINTER );
		
		playTimer = new Timer( 33, new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				timelinePos = transport.getCurrentFrame();
				updatePositionAndRepaint();
				scroll.setPosition( timelinePos, 50, TimelineScroll.TYPE_TRANSPORT );
			}
		});
		
		// --- Actions ---
//		actionNewFromSel	= new ActionNewFromSel();
//		actionClose			= new ActionClose();
//		actionSave			= new ActionSave();
//		actionSaveAs		= new ActionSaveAs( false, false );
//		actionSaveCopyAs	= new ActionSaveAs( true, false );
//		actionSaveSelectionAs = new ActionSaveAs( true, true );
//		actionSelectAll		= new ActionSelectAll();
//		actionInsertRec		= new ActionInsertRec();

//		actionProcess		= new ActionProcess();
//		actionProcessAgain	= new ActionProcessAgain();
//		actionFadeIn		= new ActionPlugIn( plugInPackage + "FadeIn" );
//		actionFadeOut		= new ActionPlugIn( plugInPackage + "FadeOut" );
//		actionGain			= new ActionPlugIn( plugInPackage + "Gain" );
//		actionInvert		= new ActionPlugIn( plugInPackage + "Invert" );
//		actionReverse		= new ActionPlugIn( plugInPackage + "Reverse" );
//		actionRotateChannels = new ActionPlugIn( plugInPackage + "RotateChannels" );
//		actionFScNeedlehole	= new ActionPlugIn( fscapePackage + "Needlehole" );
//
//		actionDebugDump		= new ActionDebugDump();
//		actionDebugVerify	= new ActionDebugVerify();
//
//		actionIncVertMax	= new ActionVerticalMax( 2.0f, 6f );
//		actionDecVertMax	= new ActionVerticalMax( 0.5f, -6f );
//		actionIncVertMin	= new ActionVerticalMin( 6f );
//		actionDecVertMin	= new ActionVerticalMin( -6f );
		actionIncHoriz		= new ActionSpanWidth( 2.0f );
		actionDecHoriz		= new ActionSpanWidth( 0.5f );
		actionZoomAllOut	= new ActionScroll( SCROLL_ENTIRE_SESSION );

//		actionShowWindow	= new ShowWindowAction( this );

//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, InputEvent.CTRL_MASK ), "incvmax" );
//		amap.put( "incvmax", actionIncVertMax );
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, InputEvent.CTRL_MASK ), "decvmax" );
//		amap.put( "decvmax", actionDecVertMax );
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, InputEvent.CTRL_MASK | InputEvent.ALT_MASK ), "incvmin" );
//		amap.put( "incvmin", actionIncVertMin );
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, InputEvent.CTRL_MASK | InputEvent.ALT_MASK ), "decvmin" );
//		amap.put( "decvmin", actionDecVertMin );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, InputEvent.CTRL_MASK ), "inch" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_OPEN_BRACKET, BasicMenuFactory.MENU_SHORTCUT ), "inch" );
		amap.put( "inch", actionIncHoriz );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, InputEvent.CTRL_MASK ), "dech" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_CLOSE_BRACKET, BasicMenuFactory.MENU_SHORTCUT ), "dech" );
		amap.put( "dech", actionDecHoriz );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, myMeta ), "samplvl" );
		amap.put( "samplvl", new ActionSpanWidth( 0.0f ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0 ), "retn" );
		amap.put( "retn", new ActionScroll( SCROLL_SESSION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, 0 ), "left" );
		amap.put( "left", new ActionScroll( SCROLL_SELECTION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, 0 ), "right" );
		amap.put( "right", new ActionScroll( SCROLL_SELECTION_STOP ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_F, InputEvent.ALT_MASK ), "fit" );
		amap.put( "fit", new ActionScroll( SCROLL_FIT_TO_SELECTION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_A, InputEvent.ALT_MASK ), "entire" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, myMeta ), "entire" );
		amap.put( "entire", actionZoomAllOut );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK ), "seltobeg" );
		amap.put( "seltobeg", new ActionSelect( SELECT_TO_SESSION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK + InputEvent.ALT_MASK ), "seltoend" );
		amap.put( "seltoend", new ActionSelect( SELECT_TO_SESSION_END ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, 0 ), "postoselbegc" );
		amap.put( "postoselbegc", doc.timeline.getPosToSelAction( true, true ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, 0 ), "postoselendc" );
		amap.put( "postoselendc", doc.timeline.getPosToSelAction( false, true ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, InputEvent.ALT_MASK ), "postoselbeg" );
		amap.put( "postoselbeg", doc.timeline.getPosToSelAction( true, false ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, InputEvent.ALT_MASK ), "postoselend" );
		amap.put( "postoselend", doc.timeline.getPosToSelAction( false, false ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_M, 0 ), "dropmark" );
		amap.put( "dropmark", new ActionDropMarker() );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, 0 ), "selnextreg" );
		amap.put( "selnextreg", new ActionSelectRegion( SELECT_NEXT_REGION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, InputEvent.ALT_MASK ), "selprevreg" );
		amap.put( "selprevreg", new ActionSelectRegion( SELECT_PREV_REGION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, InputEvent.SHIFT_MASK ), "extnextreg" );
		amap.put( "extnextreg", new ActionSelectRegion( EXTEND_NEXT_REGION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, InputEvent.ALT_MASK + InputEvent.SHIFT_MASK ), "extprevreg" );
		amap.put( "extprevreg", new ActionSelectRegion( EXTEND_PREV_REGION ));
		
		setFocusTraversalKeysEnabled( false ); // we want the tab! we gotta have that tab! ouwe!

		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
				
		// ---- menus and actions ----
		mr = app.getMenuBarRoot();
		
//		mr.putMimic( "file.new.fromSelection", this, actionNewFromSel );
//		mr.putMimic( "file.close", this, actionClose );
//		mr.putMimic( "file.save", this, actionSave );
//		mr.putMimic( "file.saveAs", this, actionSaveAs );
//		mr.putMimic( "file.saveCopyAs", this, actionSaveCopyAs );
//		mr.putMimic( "file.saveSelectionAs", this, actionSaveSelectionAs );
//
//		mr.putMimic( "edit.undo", this, doc.getUndoManager().getUndoAction() );
//		mr.putMimic( "edit.redo", this, doc.getUndoManager().getRedoAction() );
//		mr.putMimic( "edit.cut", this, doc.getCutAction() );
//		mr.putMimic( "edit.copy", this, doc.getCopyAction() );
//		mr.putMimic( "edit.paste", this, doc.getPasteAction() );
//		mr.putMimic( "edit.clear", this, doc.getDeleteAction() );
//		mr.putMimic( "edit.selectAll", this, actionSelectAll );

		mr.putMimic( "timeline.insertSilence", this, doc.getSilenceAction() );
//		mr.putMimic( "timeline.insertRecording", this, actionInsertRec );
		mr.putMimic( "timeline.trimToSelection", this, doc.getTrimAction() );

//		mr.putMimic( "process.again", this, actionProcessAgain );
//		mr.putMimic( "process.fadeIn", this, actionFadeIn );
//		mr.putMimic( "process.fadeOut", this, actionFadeOut );
//		mr.putMimic( "process.gain", this, actionGain );
//		mr.putMimic( "process.invert", this, actionInvert );
//		mr.putMimic( "process.reverse", this, actionReverse );
//		mr.putMimic( "process.rotateChannels", this, actionRotateChannels );
//		mr.putMimic( "process.fscape.needlehole", this, actionFScNeedlehole );
//
//		mr.putMimic( "debug.dumpRegions", this, actionDebugDump );
//		mr.putMimic( "debug.verifyRegions", this, actionDebugVerify );
		
		updateEditEnabled( false );

		AbstractWindowHandler.setDeepFont( cp, Collections.singletonList( timeTB ));
		GUIUtil.setDeepFont( timeTB, app.getGraphicsHandler().getFont( GraphicsHandler.FONT_SYSTEM | GraphicsHandler.FONT_MINI ));
//		app.getMenuFactory().addToWindowMenu( actionShowWindow );	// MUST BE BEFORE INIT()!!

		init();
		app.addComponent( Main.COMP_TIMELINE, this );
//		updateTitle();
		documentUpdate();

		addDynamicListening( new DynamicPrefChangeManager( app.getUserPrefs(), new String[] {
			PrefsUtil.KEY_VIEWNULLLINIE, PrefsUtil.KEY_VIEWVERTICALRULERS, PrefsUtil.KEY_VIEWMARKERS,
			PrefsUtil.KEY_TIMEUNITS, PrefsUtil.KEY_VERTSCALE /*, PrefsUtil.KEY_VIEWCHANMETERS */},
			this ));

// EEE
//		initBounds();	// be sure this is after documentUpdate!

		setVisible( true );
		toFront();

//		timelinePos			= doc.timeline.getPosition();
//		timelineSel			= doc.timeline.getSelectionSpan();
//		timelineVis			= doc.timeline.getVisibleSpan();
//		timelineRate		= doc.timeline.getRate();
//		timelineLen			= doc.timeline.getLength();
//
//		ttb			= new TimelineToolBar( root );
//		ttb.setOpaque( false );
//		gp.add( ttb );
//
//		lim			= new LaterInvocationManager( new LaterInvocationManager.Listener() {
//			// o egal
//			public void laterInvocation( Object o )
//			{
//				updatePositionAndRepaint();
//			}
//		});
//
////		rp.setPreferredSize( new Dimension( 640, 640 )); // XXX
//		
//        timeAxis        = new TimelineAxis( doc );
//		wavePanel		= new ComponentHost(); // new TimelineViewport();
//		waveView		= new WaveformView( doc, wavePanel ); // new TrackPanel();
//// produces weird scroll bars
////ggTrackPanel.setPreferredSize( new Dimension( 640, 320 ));
//		waveView.setOpaque( false );	// crucial for correct TimelineViewport() paint update calls!
//		waveView.setLayout( new SpringLayout() );
////		ggTrackRowHeaderPanel = new JPanel();
////		ggTrackRowHeaderPanel.setLayout( new SpringLayout() );
////		ggScrollPane= new JScrollPane();
////		wavePanel.setView( waveView );
////		ggScrollPane.setViewport( wavePanel );
////		ggScrollPane.setColumnHeaderView( timeAxis );
////		ggScrollPane.setRowHeaderView( ggTrackRowHeaderPanel );
//        scroll      = new TimelineScroll( doc );
//		box.add( scroll );
//        if( app.getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
//            box.add( Box.createHorizontalStrut( 16 ));
//        }
//        
////		cp.add( ggScrollPane, BorderLayout.CENTER );
//		cp.add( waveView, BorderLayout.CENTER );
//        cp.add( box, BorderLayout.SOUTH );
//		cp.add( gp, BorderLayout.NORTH );
//		
//		// --- Tools ---
//		
//		pointerTool = new TimelinePointerTool();
//		tools.put( new Integer( ToolAction.POINTER ), pointerTool );
//		tools.put( new Integer( ToolAction.ZOOM ),		new TimelineZoomTool() );
//
//		// ---- TopPainter ----
//
//		trackPainter	= new TopPainter() {
//			public void paintOnTop( Graphics2D g2 )
//			{
//				Rectangle r;
//
//				r = new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() ); // getViewRect();
//				if( !vpRecentRect.equals( r )) {
//					recalcTransforms( r );
//				}
//
//				for( int i = 0; i < vpSelections.size(); i++ ) {
//					r = (Rectangle) vpSelections.get( i );
//					g2.setColor( (Color) vpSelectionColors.get( i ));
//					g2.fillRect( vpSelectionRect.x, r.y - vpRecentRect.y, vpSelectionRect.width, r.height );
//				}
//				
////				if( markVisible ) {
////					markAxis.paintFlagSticks( g2, vpRecentRect );
////				}
//				
//				g2.setColor( colrPosition );
//				g2.drawLine( vpPosition, 0, vpPosition, vpRecentRect.height );
//
//				if( vpZoomRect != null ) {
//					g2.setColor( colrZoom );
//					g2.setStroke( vpZoomStroke[ vpZoomStrokeIdx ]);
//					g2.drawRect( vpZoomRect.x, vpZoomRect.y, vpZoomRect.width, vpZoomRect.height );
//				}
//			}
//		};
//		wavePanel.addTopPainter( trackPainter );
//
//		// --- Listener ---
//		addDynamicListening( this );
//
////		this.addMouseListener( new MouseAdapter() {
////			public void mousePressed( MouseEvent e )
////			{
////				showCursorTab();
////			}
////		});
//
//        doc.transmitters.addListener( new SessionCollection.Listener() {
//			public void sessionCollectionChanged( SessionCollection.Event e )
//			{
//				syncEditors();
//// EEE
////				wavePanel.updateAndRepaint();
//			}
//			
//			public void sessionObjectChanged( SessionCollection.Event e ) {}
//			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
//		});
//
//        doc.activeTransmitters.addListener( new SessionCollection.Listener() {
//			public void sessionCollectionChanged( SessionCollection.Event e )
//			{
//				java.util.List		coll;
//				boolean				visible		= false;
//				SessionObject		so;
//				TransmitterEditor	trnsEdit;
//				boolean				revalidate	= false;
//			
////System.err.println( "lala "+collTransmitterEditors.size()+" ; "+System.currentTimeMillis() );
//				switch( e.getModificationType() ) {
//				case SessionCollection.Event.ACTION_ADDED:
//					visible = true;
//					// THRU
//				case SessionCollection.Event.ACTION_REMOVED:
//					coll	= e.getCollection();
//collLp:				for( int i = 0; i < coll.size(); i++ ) {
//						so	= (SessionObject) coll.get( i );
//						for( int j = 0; j < collTransmitterEditors.size(); j++ ) {
//							trnsEdit = (TransmitterEditor) collTransmitterEditors.get( j );
//							if( trnsEdit.getTransmitter() == so ) {
//								trnsEdit.getView().setVisible( visible );
//								((JComponent) collTransmitterHeaders.get( j )).setVisible( visible );
////System.err.println( "setting "+so.getName()+(visible ? " visible" : " invisible") );
//								revalidate = true;
//								continue collLp;
//							}
//						}
//					}
//					if( revalidate ) {
//						revalidateView();
//					}
//					break;
//				default:
//					break;
//				}
////				syncEditors();
////				vpTrackPanel.updateAndRepaint();
////System.err.println( "gaga "+System.currentTimeMillis() );
//			}
//			
//			public void sessionObjectChanged( SessionCollection.Event e ) {}
//			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
//		});
//
//        doc.selectedTransmitters.addListener( new SessionCollection.Listener() {
//			public void sessionCollectionChanged( SessionCollection.Event e )
//			{
//// EEE
////				wavePanel.updateAndRepaint();
//			}
//			
//			public void sessionObjectChanged( SessionCollection.Event e ) {}
//			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
//		});
//		
//		ttb.addToolActionListener( this );
//		
//		rowHeightListener	= new ComponentAdapter() {
//			public void componentResized( ComponentEvent e ) {
//				updateSelectionAndRepaint();
//			}
//
//			public void componentShown( ComponentEvent e ) {
//				updateSelectionAndRepaint();
//			}
//
//			public void componentHidden( ComponentEvent e ) {
//				updateSelectionAndRepaint();
//			}
//		};
//
////		addListener( new AbstractWindow.Adapter() {
////			public void windowClosing( AbstractWindow.Event e )
////			{
////				dispose();
////			}
////		});
////		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE ); // window listener see above!
//
//		playTimer = new Timer( 33, new ActionListener() {
//			public void actionPerformed( ActionEvent e )
//			{
//// EEE
////				timelinePos = transport.getCurrentFrame();
//				updatePositionAndRepaint();
//				scroll.setPosition( timelinePos, 50, TimelineScroll.TYPE_TRANSPORT );
//			}
//		});
//
//		// --- Actions ---
//		actionClear			= new ActionDelete();
//		actionIncHeight		= new ActionRowHeight( 2.0f );
//		actionDecHeight		= new ActionRowHeight( 0.5f );
//		actionIncWidth		= new ActionSpanWidth( 2.0f );
//		actionDecWidth		= new ActionSpanWidth( 0.5f );
//
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, KeyEvent.CTRL_MASK ), "inch" );
//		amap.put( "inch", actionIncHeight );
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, KeyEvent.CTRL_MASK ), "dech" );
//		amap.put( "dech", actionDecHeight );
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, KeyEvent.CTRL_MASK ), "incw" );
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_OPEN_BRACKET, MenuFactory.MENU_SHORTCUT ), "incw" );
//		amap.put( "incw", actionIncWidth );
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, KeyEvent.CTRL_MASK ), "decw" );
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_CLOSE_BRACKET, MenuFactory.MENU_SHORTCUT ), "decw" );
//		amap.put( "decw", actionDecWidth );
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0 ), "retn" );
//		amap.put( "retn", new ActionScroll( SCROLL_SESSION_START ));
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, 0 ), "left" );
//		amap.put( "left", new ActionScroll( SCROLL_SELECTION_START ));
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, 0 ), "right" );
//		amap.put( "right", new ActionScroll( SCROLL_SELECTION_STOP ));
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_F, KeyEvent.ALT_MASK ), "fit" );
//		amap.put( "fit", new ActionScroll( SCROLL_FIT_TO_SELECTION ));
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_A, KeyEvent.ALT_MASK ), "entire" );
//		amap.put( "entire", new ActionScroll( SCROLL_ENTIRE_SESSION ));
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK ), "seltobeg" );
//		amap.put( "seltobeg", new ActionSelect( SELECT_TO_SESSION_START ));
//		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK + KeyEvent.ALT_MASK ), "seltoend" );
//		amap.put( "seltoend", new ActionSelect( SELECT_TO_SESSION_END ));
//
//		updateEditEnabled( false );
//		// -------
//
////	    HelpGlassPane.setHelp( getRootPane(), "TimelineFrame" );	// EEE
//		AbstractWindowHandler.setDeepFont( cp );
//		init();
	}

	protected void checkDecimatedTrails()
	{
		final DecimatedTrail dt;
		
		if( waveExpanded ) {
// EEE
//			if( verticalScale == PrefsUtil.VSCALE_FREQ_SPECT ) {
//				if( doc.getDecimatedSonaTrail() == null ) {
//					try {
//						final DecimatedSonaTrail dst = doc.createDecimatedSonaTrail();
//						// set initial freq bounds of waveview
//						waveView.setFreqMinMax( dst.getMinFreq(), dst.getMaxFreq() );
//					}
//					catch( IOException e1 ) {
//						e1.printStackTrace();
//					}
//				}
//				dt = doc.getDecimatedSonaTrail();
//			} else {
//				if( doc.getDecimatedWaveTrail() == null ) {
//					try {
//						doc.createDecimatedWaveTrail();
//					}
//					catch( IOException e1 ) {
//						e1.printStackTrace();
//					}
//				}
//				dt = doc.getDecimatedWaveTrail();
//			}
dt = null;
			if( dt != asyncTrail ) {
				if( asyncTrail != null ) asyncTrail.removeAsyncListener( this );
				asyncTrail = dt;
				if( asyncTrail != null ) asyncTrail.addAsyncListener( this );
			}
		}
	}

	public void addCatchBypass() { scroll.addCatchBypass(); }
	public void removeCatchBypass() { scroll.removeCatchBypass(); }

	public void repaintMarkers( Span affectedSpan )
	{
		if( !markVisible || !affectedSpan.touches( timelineVis )) return;
	
		final Span span	 = affectedSpan.shift( -timelineVis.start );
		final Rectangle updateRect = new Rectangle(
			(int) (span.start * vpScale), 0,
			(int) (span.getLength() * vpScale) + 2, wavePanel.getHeight() ).
				intersection( new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() ));
		if( !updateRect.isEmpty() ) {
			// update markAxis in any case, even if it's invisible
			// coz otherwise the flag stakes are not updated!
			wavePanel.update( markAxis );
			wavePanel.repaint( updateRect );
		}
	}
	
	// sync: attempts exclusive on MTE and shared on TIME!
	protected void updateOverviews( boolean justBecauseOfResize, boolean allTracks )
	{
//System.err.println( "update" );
		waveView.update( timelineVis );
		if( allTracks ) wavePanel.updateAll();
	}

	protected void documentUpdate()
	{
//		final List				collChannelMeters;
//		PeakMeter[]				meters;
		TrackRowHeader			chanHead;
		Track					t;
		int						oldNumWaveTracks, newNumWaveTracks;
		Axis					chanRuler;
//		PeakMeter				chanMeter;

		newNumWaveTracks	= doc.getActiveTransmitters().size(); // EEE doc.getDisplayDescr().channels;
		oldNumWaveTracks	= collChannelHeaders.size();
		
		System.out.println( "oldNumWaveTracks = " + oldNumWaveTracks + "; newNumWaveTracks = " + newNumWaveTracks );

//		meters				= channelMeters;
//		collChannelMeters	= new ArrayList( meters.length );
//		for( int ch = 0; ch < meters.length; ch++ ) {
//			collChannelMeters.add( meters[ ch ]);
//		}
	
		// first kick out editors whose tracks have been removed
		for( int ch = 0; ch < oldNumWaveTracks; ch++ ) {
			chanHead	= (TrackRowHeader) collChannelHeaders.get( ch );
			t			= chanHead.getTrack();

			if( !doc.getActiveTransmitters().contains( t )) {
System.out.println( "removing " + t );
				chanHead	= (TrackRowHeader) collChannelHeaders.remove( ch );
//				chanMeter	= (PeakMeter) collChannelMeters.remove( ch );
				chanRuler	= (Axis) collChannelRulers.remove( ch );
				oldNumWaveTracks--;
				// XXX : dispose trnsEdit (e.g. free vectors, remove listeners!!)
				flagsPanel.remove( chanHead );
//				metersPanel.remove( chanMeter );
				rulersPanel.remove( chanRuler );
				ch--;
				chanHead.dispose();
//				chanMeter.dispose();
				chanRuler.dispose();
			}
		}
		// next look for newly added transmitters and create editors for them

		System.out.println( "now oldNumWaveTracks = " + oldNumWaveTracks + "; collChannelHeaders.size = " + collChannelHeaders.size() );
		
// EEE
newLp:	for( int ch = 0; ch < newNumWaveTracks; ch++ ) {
			t = (Track) doc.getActiveTransmitters().get( ch );
			for( int ch2 = 0; ch2 < oldNumWaveTracks; ch2++ ) {
				chanHead = (TrackRowHeader) collChannelHeaders.get( ch2 );
				if( chanHead.getTrack() == t ) continue newLp;
			}
			
			chanHead = new TransmitterRowHeader( t, doc.getTracks(), doc.getMutableSelectedTracks(), doc.getUndoManager() );
			collChannelHeaders.add( chanHead );
			flagsPanel.add( chanHead, ch );

//			chanMeter = new PeakMeter();
//			collChannelMeters.add( chanMeter );
//			metersPanel.add( chanMeter, ch );

			chanRuler = new Axis( Axis.VERTICAL, Axis.FIXEDBOUNDS );
			collChannelRulers.add( chanRuler );
			rulersPanel.add( chanRuler, ch );
		}
		
//		meters	= new PeakMeter[ collChannelMeters.size() ];
//		for( int ch = 0; ch < meters.length; ch++ ) {
//			meters[ ch ] = (PeakMeter) collChannelMeters.get( ch );
//		}
//		channelMeters	= meters;
//		lmm.setView( new PeakMeterGroup( meters ));

		updateOverviews( false, true );
	}

	public void dispose()
	{
		playTimer.stop();

// EEE
//		app.getMenuFactory().removeFromWindowMenu( actionShowWindow );

		TrackRowHeader	chanHead;
		Axis			chanRuler;

// EEE
//		lmm.dispose();
		wavePanel.dispose();
		while( !collChannelHeaders.isEmpty() ) {
			chanHead = (TrackRowHeader) collChannelHeaders.remove( 0 );
			chanHead.dispose();
		}
		while( !collChannelRulers.isEmpty() ) {
			chanRuler = (Axis) collChannelRulers.remove( 0 );
			chanRuler.dispose();
		}
// EEE
//		for( int ch = 0; ch < channelMeters.length; ch++ ) {
//			channelMeters[ ch ].dispose();
//		}
//		channelMeters = new PeakMeter[ 0 ];
		markAxis.stopListening();
		markAxis.dispose();
		timeAxis.dispose();
		timeTB.dispose();
// EEE
//		transTB.dispose();
		
		AbstractApplication.getApplication().removeComponent( Main.COMP_TIMELINE );
		super.dispose();
	}

	private void updateEditEnabled( boolean enabled )
	{
		Action ma;
// EEE
//		ma			= doc.getCutAction();
//		if( ma != null ) ma.setEnabled( enabled );
//		ma			= doc.getCopyAction();
//		if( ma != null ) ma.setEnabled( enabled );
//		ma			= doc.getDeleteAction();
//		if( ma != null ) ma.setEnabled( enabled );
		ma			= doc.getTrimAction();
		if( ma != null ) ma.setEnabled( enabled );

//		actionProcess.setEnabled( enabled );
//		actionNewFromSel.setEnabled( enabled );
//		actionSaveSelectionAs.setEnabled( enabled );
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
		return new Point2D.Float( 0.95f, 0.25f );
	}

	/**
	 *  Only call in the Swing thread!
	 */
	protected void updatePositionAndRepaint()
	{
		boolean pEmpty, cEmpty;
		int		x, x2;
		
		pEmpty = (vpPositionRect.x + vpPositionRect.width < 0) || (vpPositionRect.x > vpRecentRect.width);
		if( !pEmpty ) vpUpdateRect.setBounds( vpPositionRect );

		if( vpScale > 0f ) {
			vpPosition	= (int) ((timelinePos - timelineVis.getStart()) * vpScale + 0.5f);
			// choose update rect such that even a paint manager delay of 200 milliseconds
			// will still catch the (then advanced) position so we don't see flickering!
			// XXX this should take playback rate into account, though
			vpPositionRect.setBounds( vpPosition, 0, Math.max( 1, (int) (vpScale * timelineRate * 0.2f) ), vpRecentRect.height );
		} else {
			vpPosition	= -1;
			vpPositionRect.setBounds( 0, 0, 0, 0 );
		}

		cEmpty = (vpPositionRect.x + vpPositionRect.width <= 0) || (vpPositionRect.x > vpRecentRect.width);
		if( pEmpty ) {
			if( cEmpty ) return;
			x   = Math.max( 0, vpPositionRect.x );
			x2  = Math.min( vpRecentRect.width, vpPositionRect.x + vpPositionRect.width );
			vpUpdateRect.setBounds( x, vpPositionRect.y, x2 - x, vpPositionRect.height );
		} else {
			if( cEmpty ) {
				x   = Math.max( 0, vpUpdateRect.x );
				x2  = Math.min( vpRecentRect.width, vpUpdateRect.x + vpUpdateRect.width );
				vpUpdateRect.setBounds( x, vpUpdateRect.y, x2 - x, vpUpdateRect.height );
			} else {
				x   = Math.max( 0, Math.min( vpUpdateRect.x, vpPositionRect.x ));
				x2  = Math.min( vpRecentRect.width, Math.max( vpUpdateRect.x + vpUpdateRect.width,
															vpPositionRect.x + vpPositionRect.width ));
				vpUpdateRect.setBounds( x, vpUpdateRect.y, x2 - x, vpUpdateRect.height );
			}
		}
		if( !vpUpdateRect.isEmpty() ) {
			wavePanel.repaint( vpUpdateRect );
		}
	}

	/**
	 *  Only call in the Swing thread!
	 */
	protected void updateSelectionAndRepaint()
	{
		final Rectangle r = new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() );
	
		vpUpdateRect.setBounds( vpSelectionRect );
		recalcTransforms( r );
		updateSelection();
		if( vpUpdateRect.isEmpty() ) {
			vpUpdateRect.setBounds( vpSelectionRect );
		} else if( !vpSelectionRect.isEmpty() ) {
			vpUpdateRect = vpUpdateRect.union( vpSelectionRect );
		}
		vpUpdateRect = vpUpdateRect.intersection( new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() ));
		if( !vpUpdateRect.isEmpty() ) {
			wavePanel.repaint( vpUpdateRect );
		}
	}
	
	/**
	 *  Only call in the Swing thread!
	 */
	private void updateTransformsAndRepaint( boolean verticalSelection )
	{
		final Rectangle r = new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() );

		vpUpdateRect = vpSelectionRect.union( vpPositionRect );
		recalcTransforms( r );
		if( verticalSelection ) updateSelection();
		vpUpdateRect = vpUpdateRect.union( vpPositionRect ).union( vpSelectionRect ).intersection( r );
		if( !vpUpdateRect.isEmpty() ) {
			wavePanel.repaint( vpUpdateRect );	// XXX ??
		}
	}
	
	protected void recalcTransforms( Rectangle newRect )
	{
		int x, w;
		
		vpRecentRect = newRect; // getViewRect();
	
		if( !timelineVis.isEmpty() ) {
			vpScale			= (float) vpRecentRect.width / (float) timelineVis.getLength(); // - 1;
			playTimer.setDelay( Math.min( (int) (1000 / (vpScale * timelineRate * playRate)), 33 ));
			vpPosition		= (int) ((timelinePos - timelineVis.getStart()) * vpScale + 0.5f);
			vpPositionRect.setBounds( vpPosition, 0, 1, vpRecentRect.height );
			if( !timelineSel.isEmpty() ) {
				x			= (int) ((timelineSel.getStart() - timelineVis.getStart()) * vpScale + 0.5f) + vpRecentRect.x;
				w			= Math.max( 1, (int) ((timelineSel.getStop() - timelineVis.getStart()) * vpScale + 0.5f) - x );
				vpSelectionRect.setBounds( x, 0, w, vpRecentRect.height );
			} else {
				vpSelectionRect.setBounds( 0, 0, 0, 0 );
			}
		} else {
			vpScale			= 0.0f;
			vpPosition		= -1;
			vpPositionRect.setBounds( 0, 0, 0, 0 );
			vpSelectionRect.setBounds( 0, 0, 0, 0 );
		}
	}

	// sync: caller must sync on timeline + grp + tc
	private void updateSelection()
	{
		Rectangle	r;
		Track		t;
		int			x, y;

		vpSelections.clear();
		vpSelectionColors.clear();
		if( !timelineSel.isEmpty() ) {
			x			= waveView.getX();
			y			= waveView.getY();
			vpSelections.add( timeAxis.getBounds() );
			vpSelectionColors.add( colrSelection );
			t			= doc.markerTrack;
			vpSelections.add( markAxis.getBounds() );
			vpSelectionColors.add( doc.getSelectedTracks().contains( t ) ? colrSelection : colrSelection2 );

			for( int i = 0; i < doc.getActiveTransmitters().size(); i++ ) {
//				r		= new Rectangle( waveView.rectForChannel( ch ));
				r		= new Rectangle( waveView.rectForTransmitter( i ));
				r.translate( x, y );
				t		= (Track) doc.getActiveTransmitters().get( i );
				vpSelections.add( r );
				vpSelectionColors.add( doc.getSelectedTracks().contains( t ) ? colrSelection : colrSelection2 );
			}
		}
	}

	protected void setZoomRect( Rectangle r )
	{
		vpZoomRect		= r;
		vpZoomStrokeIdx	= (vpZoomStrokeIdx + 1) % vpZoomStroke.length;

		wavePanel.repaint();
	}
	
// ------------- DecimatedTrail.AsyncListener interface -------------

	public void asyncFinished( DecimatedTrail.AsyncEvent e )
	{
		final DecimatedTrail dt = e.getDecimatedTrail();
		dt.removeAsyncListener( this );
		if( dt == asyncTrail ) asyncTrail = null;
		updateOverviews( false, true );
	}

	public void asyncUpdate( DecimatedTrail.AsyncEvent e )
	{
		updateOverviews( false, true );
	}	

	/*
	 *  When transmitters are created or removed
	 *  this method sync's the transmitter collection
	 *  with the editor collection.
	 *
	 *  Sync: syncs to tc
	 */
//	private void syncEditors()
//	{
//		int					rows;
//		Transmitter			trns;
//		TransmitterEditor   trnsEdit;
//		TransmitterRowHeader trnsHead;
//		boolean				revalidate = false;
//	
//		try {
//			doc.bird.waitShared( Session.DOOR_TRNS | Session.DOOR_GRP );
//			rows	= collTransmitterEditors.size();
//			assert collTransmitterHeaders.size() == rows : collTransmitterHeaders.size();
//			// first kick out editors whose tracks have been removed
//			for( int row = 0; row < rows; row++ ) {
//				trnsEdit	= (TransmitterEditor) collTransmitterEditors.get( row );
//				trns		= trnsEdit.getTransmitter();
//				if( !doc.transmitters.contains( trns )) {
//					revalidate	= true;
//					trnsEdit	= (TransmitterEditor) collTransmitterEditors.remove( row );
//					trnsHead	= (TransmitterRowHeader) collTransmitterHeaders.remove( row );
//					trnsHead.removeComponentListener( rowHeightListener );
//					rows--;
//                    // XXX : dispose trnsEdit (e.g. free vectors, remove listeners!!)
//					hashTransmittersToEditors.remove( trns );
//					waveView.remove( trnsEdit.getView() );
//// EEE
////					ggTrackRowHeaderPanel.remove( trnsHead );
//					row--;
//				}
//			}
//			// next look for newly added transmitters and create editors for them
//			for( int i = 0; i < doc.transmitters.size(); i++ ) {
//				trns		= (Transmitter) doc.transmitters.get( i );
//				trnsEdit	= (TransmitterEditor) hashTransmittersToEditors.get( trns );
//				if( trnsEdit == null ) {
//					revalidate = true;
//					try {
//						trnsEdit = (TransmitterEditor) trns.getDefaultEditor().newInstance();	// XXX deligate to SurfaceFrame
//						trnsEdit.init( root, doc, trns );
//						trnsHead = new TransmitterRowHeader( root, doc, trns );
//						trnsHead.addComponentListener( rowHeightListener );
//						hashTransmittersToEditors.put( trns, trnsEdit );
//						collTransmitterEditors.add( trnsEdit );
//						collTransmitterHeaders.add( trnsHead );
//						rows++;
//						setRowHeight( trnsHead, 64 ); // XXX
//						setRowHeight( trnsEdit.getView(), 64 ); // XXX
//// EEE
////						ggTrackRowHeaderPanel.add( trnsHead, i );
//						waveView.add( trnsEdit.getView(), i );
//					}
//					catch( InstantiationException e1 ) {
//						System.err.println( e1.getLocalizedMessage() );
//					}
//					catch( IllegalAccessException e2 ) {
//						System.err.println( e2.getLocalizedMessage() );
//					}
//					catch( IllegalArgumentException e3 ) {
//						System.err.println( e3.getLocalizedMessage() );
//					}
//				}
//			}
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_GRP );
//		}
//		
//		if( revalidate ) {
//// EEE
////			GUIUtil.makeCompactSpringGrid( ggTrackRowHeaderPanel, rows, 1, 0, 0, 1, 1 ); // initX, initY, padX, padY
////			GUIUtil.makeCompactSpringGrid( waveView, rows, 1, 0, 0, 1, 1 ); // initX, initY, padX, padY
////			ggTrackRowHeaderPanel.revalidate();
//			waveView.revalidate();
//		}
//
//		if( activeTool != null ) {	// re-set tool to update mouse listeners
//			activeTool.toolDismissed( waveView );
//			activeTool.toolAcquired( waveView );
//		}
//	}

	private void setRowHeight( JComponent comp, int height )
	{
		comp.setMinimumSize(   new Dimension( comp.getMinimumSize().width,   height ));
		comp.setMaximumSize(   new Dimension( comp.getMaximumSize().width,   height ));
		comp.setPreferredSize( new Dimension( comp.getPreferredSize().width, height ));
	}

	protected void updateVerticalRuler()
	{
		final VectorSpace	spc;
		final float			min, max;
		Axis				chanRuler;
		
		switch( waveView.getVerticalScale() ) {
		case PrefsUtil.VSCALE_AMP_LIN:
			min = waveView.getAmpLinMin() * 100;
			max = waveView.getAmpLinMax() * 100;
			spc = VectorSpace.createLinSpace( 0.0, 1.0, min, max, null, null, null, null );
			break;
		case PrefsUtil.VSCALE_AMP_LOG:
			min = waveView.getAmpLogMin();
			max = waveView.getAmpLogMax();
			spc = VectorSpace.createLinSpace( 0.0, 1.0, min, max, null, null, null, null );
			break;
		case PrefsUtil.VSCALE_FREQ_SPECT:
			min = waveView.getFreqMin();
			max = waveView.getFreqMax();
			spc = VectorSpace.createLinLogSpace( 0.0, 1.0, min, max, Math.sqrt( min * max ), null, null, null, null );
			break;
		default:
			assert false : waveView.getVerticalScale();
			spc = null;
		}

		for( int i = 0; i < collChannelRulers.size(); i++ ) {
			chanRuler	= (Axis) collChannelRulers.get( i );
			chanRuler.setSpace( spc );
		}
	}

// ---------------- RealtimeConsumer interface ---------------- 

//	/**
//	 *  Requests 30 fps notification (no data block requests).
//	 *  This is used to update the timeline position during transport
//	 *  playback.
//	 */
//	public RealtimeConsumerRequest createRequest( RealtimeContext context )
//	{
//		RealtimeConsumerRequest request = new RealtimeConsumerRequest( this, context );
//		// 30 fps is visually fluent
//		request.notifyTickStep  = RealtimeConsumerRequest.approximateStep( context, 30 );
//		request.notifyTicks		= true;
//		request.notifyOffhand	= true;
//		return request;
//	}
//	
//	public void realtimeTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
//	{
//		this.currentPos = currentPos;
//		lim.queue( this );
//		scroll.setPosition( currentPos, 50, TimelineScroll.TYPE_TRANSPORT );
//	}
//
//	public void offhandTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
//	{
//		this.currentPos = currentPos;
//		updatePositionAndRepaint();
//		scroll.setPosition( currentPos, 0, pointerTool.validDrag ?
//			TimelineScroll.TYPE_DRAG : TimelineScroll.TYPE_UNKNOWN );
//	}
//
//	public void realtimeBlock( RealtimeContext context, RealtimeProducer.Source source, boolean even ) {}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
        doc.timeline.addTimelineListener( this );
// EEE
//		transport.addRealtimeConsumer( this );
//		syncEditors();
// EEE
//		wavePanel.updateAndRepaint();
    }

    public void stopListening()
    {
        doc.timeline.removeTimelineListener( this );
// EEE
//		transport.removeRealtimeConsumer( this );
    }

// ---------------- ToolListener interface ---------------- 
 
	// sync: attemptShared DOOR_TRNS
	public void toolChanged( ToolActionEvent e )
	{
		Transmitter			trns;
		TransmitterEditor	trnsEdit;
	
		if( activeTool != null ) {
			activeTool.toolDismissed( waveView );
//			removeMouseMotionListener( cursorListener );
		}

// EEE
//		// forward event to all editors that implement ToolActionListener
//		if( !doc.bird.attemptShared( Session.DOOR_TRNS | Session.DOOR_GRP, 250 )) return;
//		try {
//			for( int i = 0; i < doc.activeTransmitters.size(); i++ ) {
//				trns		= (Transmitter) doc.activeTransmitters.get( i );
//				trnsEdit	= (TransmitterEditor) hashTransmittersToEditors.get( trns );
//				if( trnsEdit instanceof ToolActionListener ) {
//					((ToolActionListener) trnsEdit).toolChanged( e );
//				}
//			}
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_GRP );
//		}

		activeTool = (AbstractTool) tools.get( new Integer( e.getToolAction().getID() ));
		if( activeTool != null ) {
			waveView.setCursor( e.getToolAction().getDefaultCursor() );
			activeTool.toolAcquired( waveView );
//			showCursorTab();
//			addMouseMotionListener( cursorListener );
		} else {
			waveView.setCursor( null );
		}
	}

	// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		final boolean	wasEmpty = timelineSel.isEmpty();
		final boolean	isEmpty;
	
		timelineSel	= doc.timeline.getSelectionSpan();

		updateSelectionAndRepaint();
		isEmpty	= timelineSel.isEmpty();
		if( wasEmpty != isEmpty ) {
			updateEditEnabled( !isEmpty );
		}
    }

	// warning : don't call doc.setAudioFileDescr, it will restore the old markers!
	public void timelineChanged( TimelineEvent e )
    {
		timelineRate				= doc.timeline.getRate();
		timelineLen					= doc.timeline.getLength();
		playTimer.setDelay( Math.min( (int) (1000 / (vpScale * timelineRate * playRate)), 33 ));
// EEE
//		updateAFDGadget();
		updateOverviews( false, true );
    }

	public void timelinePositioned( TimelineEvent e )
	{
		timelinePos = doc.timeline.getPosition();
		
		updatePositionAndRepaint();
		scroll.setPosition( timelinePos, 0, pointerTool.validDrag ?
			TimelineScroll.TYPE_DRAG : TimelineScroll.TYPE_UNKNOWN );
	}

    public void timelineScrolled( TimelineEvent e )
    {
    	timelineVis	= doc.timeline.getVisibleSpan();

		updateOverviews( false, true );
		updateTransformsAndRepaint( false );
    }

// ---------------- TransportListener interface ---------------- 

	public void transportPlay( Transport t, long pos, double rate )
	{
		playRate = rate;
		playTimer.setDelay( Math.min( (int) (1000 / (vpScale * timelineRate * playRate)), 33 ));
		playTimer.restart();
	}
	
	public void transportStop( Transport t, long pos )
	{
		playTimer.stop();
	}

	public void transportPosition( Transport t, long pos, double rate ) { /* ignored */ }
	public void transportReadjust( Transport t, long pos, double rate ) { /* ignored */ }

	public void transportQuit( Transport t )
	{
		playTimer.stop();
	}

// ---------------- EditMenuListener interface ---------------- 

	/*
	 *  Copies the selected timespan of the selected
	 *  transmitters to the clipboard. Uses a
	 *  <code>TransferableCollection</code> whose
	 *  elements are <code>TrackList</code>s.
	 *
	 *  @see	de.sciss.meloncillo.io.MultirateTrackEditor#getTrackList( Span )
	 *  @see	de.sciss.meloncillo.io.TrackList
	 *  @see	de.sciss.meloncillo.util.TransferableCollection
	 */
	private boolean editCopy()
	{
		return false;
/* EEE
		Span							span;
		List							collAffectedTransmitters;
		final List						v		= new ArrayList();
		AudioTrail						at;
		boolean							success = false;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();

		try {
			doc.bird.waitShared( Session.DOOR_TIMETRNSMTE );
			span = doc.timeline.getSelectionSpan();
			if( span.isEmpty() ) return false;

			collAffectedTransmitters = doc.selectedTransmitters.getAll();
			if( collAffectedTransmitters.isEmpty() ) return false;

			for( int i = 0; i < collAffectedTransmitters.size(); i++ ) {
				at = ((Transmitter) collAffectedTransmitters.get( i )).getAudioTrail();
				v.add( at.getTrackList( span ));
			}
			if( !v.isEmpty() ) {
				app.getClipboard().setContents( new TransferableCollection( v ), this );
			}
			success = true;
		}
		catch( IllegalStateException e1 ) {
			System.err.println( app.getResourceString( "errClipboard" ));
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIMETRNSMTE );
		}

		return success;
*/
	}

// ---------------- DocumentFrame abstract methods ----------------
	
	protected Action getCutAction() { return doc.getCutAction(); /* new ActionCut() */ }
	protected Action getCopyAction() { return doc.getCopyAction(); /* new ActionCopy() */ }
	protected Action getPasteAction() { return doc.getPasteAction(); /* new ActionPaste() */ }
	protected Action getDeleteAction() { return doc.getDeleteAction(); /* new ActionDelete() */ }
	protected Action getSelectAllAction() { return new ActionSelectAll(); }
	
	// ---------------- PreferenceChangeListener interface ---------------- 

	public void preferenceChange( PreferenceChangeEvent e )
	{
		final String key = e.getKey();

		if( key == PrefsUtil.KEY_VIEWNULLLINIE ) {
			waveView.setNullLinie( e.getNode().getBoolean( e.getKey(), false ));
		} else if( key == PrefsUtil.KEY_VIEWVERTICALRULERS ) {
			final boolean visible = e.getNode().getBoolean( e.getKey(), false );
			rulersPanel.setVisible( visible );
// EEE
//		} else if( key == PrefsUtil.KEY_VIEWCHANMETERS ) {
//			chanMeters = e.getNode().getBoolean( e.getKey(), false );
//			showHideMeters();
		} else if( key == PrefsUtil.KEY_VIEWMARKERS ) {
			viewMarkers = e.getNode().getBoolean( e.getKey(), false );
			markVisible	= viewMarkers && waveExpanded;
			if( waveExpanded ) {
				markAxis.setVisible( markVisible );
				markAxisHeader.setVisible( markVisible );
				wavePanel.updateAll();
			}
			if( markVisible ) {
				markAxis.startListening();
			} else {
				markAxis.stopListening();
			}
		} else if( key == PrefsUtil.KEY_TIMEUNITS ) {
			final boolean timeSmps = e.getNode().getInt( key, PrefsUtil.TIME_SAMPLES ) == PrefsUtil.TIME_SAMPLES;
			msgCsr1.applyPattern( timeSmps ? smpPtrn : timePtrn );
		} else if( key == PrefsUtil.KEY_VERTSCALE) {
			verticalScale = e.getNode().getInt( key, PrefsUtil.VSCALE_AMP_LIN );
			checkDecimatedTrails(); // needs to be before setVert.scale / updateRuler!
			waveView.setVerticalScale( verticalScale );
			updateVerticalRuler();
		}
	}
	
// ---------------- ClipboardOwner interface ---------------- 

	public void lostOwnership( Clipboard clipboard, Transferable contents )
	{
		// XXX evtl. dispose() aufrufen
	}

// ---------------- internal action classes ---------------- 

/*
	private class ActionPaste
	extends MenuAction
	implements ProcessingThread.Client
	{
		private ActionPaste()
		{
			super();
		}
		
		public void actionPerformed( ActionEvent e )
		{
			Transferable	t;
			boolean			hasFlavor;
			java.util.List	coll;
			int				i;

			try {
				t = AbstractApplication.getApplication().getClipboard().getContents( this );
				if( t == null ) return;
				
				if( t.isDataFlavorSupported( TransferableCollection.collectionFlavor )) {
					coll = (java.util.List) t.getTransferData( TransferableCollection.collectionFlavor );
				} else {
					return;
				}

				// ---- see if there's anything to paste ----
				hasFlavor = false;
				for( i = 0; i < coll.size(); i++ ) {
					t = (Transferable) coll.get( i );
					if( t.isDataFlavorSupported( TrackList.trackListFlavor )) {
						hasFlavor = true;
						break;
					}
				}
				if( !hasFlavor ) return;
			}
			catch( IOException e11 ) {
				System.err.println( e11.getLocalizedMessage() );
				return;
			}
			catch( UnsupportedFlavorException e12 ) {
				System.err.println( e12.getLocalizedMessage() );
				return;
			}

			final ProcessingThread pt;
			final ProgressComponent pc = (MainFrame) AbstractApplication.getApplication().getComponent(  Main.COMP_MAIN );
			pt = new ProcessingThread( this, pc, getValue( NAME ).toString() );
			pt.putClientArg( "coll", coll );
			pt.start();
		}
		
		//
		//  This method is called by ProcessingThread
		//
		public int processRun( ProcessingThread context ) throws IOException
		{
			final List						coll		= (List) context.getClientArg( "coll" );
			List							collAffectedTransmitters;
			Transferable					t;
			int								i, j, numTrns;
			long							position, docLength, pasteLength, start;
			Transmitter						trns;
			TrackList						tl;
			AudioTrail						at;
			CompoundSessionObjEdit			edit;
			Span							oldSelSpan, newSelSpan, span;
			long[]							trnsLen;
			long							maxTrnsLen  = 0;
			float[][]						frameBuf	= new float[2][4096];
			int								progress, progressLen;
			boolean							success		= false;
			TrackSpan	ts;
			float							f1, f2;

			collAffectedTransmitters	= doc.activeTransmitters.getAll();
			numTrns						= collAffectedTransmitters.size();
			edit						= new CompoundSessionObjEdit( this, doc, collAffectedTransmitters,
											Transmitter.OWNER_TRAJ, null, null, Session.DOOR_TIMETRNSMTE );
			position					= doc.timeline.getPosition();
			oldSelSpan					= doc.timeline.getSelectionSpan();
			docLength					= doc.timeline.getLength();
			trnsLen						= new long[ numTrns ];

			progress					= 0;
			progressLen					= numTrns << 1;

			try {
				if( !oldSelSpan.isEmpty() ) { // deselect
					edit.addEdit( TimelineVisualEdit.select( this, doc, new Span() ));
//							position = oldSelSpan.getStart();
				}
				newSelSpan = new Span( position, position );

				// ---- first try to paste contents ----
				for( i = 0; i < collAffectedTransmitters.size(); i++ ) {
					trns		= (Transmitter) collAffectedTransmitters.get( i );
					trnsLen[i]  = docLength;
					if( doc.selectedTransmitters.contains( trns )) {
						at	= trns.getAudioTrail();
//									if( oldSelSpan != null && !oldSelSpan.isEmpty() ) { // remove old selected span
//										mte.remove( oldSelSpan, edit );
//										trnsLen[i] -= oldSelSpan.getLength();
//									}
clipboardLoop:			for( j = 0; j < coll.size(); j++ ) {
							t = (Transferable) coll.get( j );
							if( t.isDataFlavorSupported( TrackList.trackListFlavor )) {
								coll.remove( j );
								tl		= (TrackList) t.getTransferData( TrackList.trackListFlavor );
// tl.debugDump();
								at.insert( position, tl, edit ); // insert clipboard content
								pasteLength	= tl.getSpan().getLength();
								trnsLen[i] += pasteLength;
								newSelSpan	= new Span( newSelSpan.getStart(),
												Math.max( newSelSpan.getStop(),
														  newSelSpan.getStart() + pasteLength ));
								break clipboardLoop;
							} // if( t.isDataFlavorSupported( TrackList.trackListFlavor ))
						} // for( j = 0; j < coll.size(); j++ )
					} // if( doc.transmitterCollection.selectionContains( trns ))
					if( trnsLen[i] > maxTrnsLen ) {
						maxTrnsLen = trnsLen[i];
					}
					progress++;
					context.setProgression( (float) progress / (float) progressLen );
				} // for( i = 0; i < collAffectedTransmitters.size(); i++ )

				// ---- now ensure all tracks have the same length ----
				for( i = 0; i < collAffectedTransmitters.size(); i++ ) {
					if( trnsLen[i] < maxTrnsLen ) {
						trns	= (Transmitter) collAffectedTransmitters.get( i );
						at		= trns.getAudioTrail();
						if( trnsLen[i] > 0 ) {
							at.read( new Span( trnsLen[i] - 1, trnsLen[i] ), frameBuf, 0 );
							f1 = frameBuf[0][0];
							f2 = frameBuf[1][0];
						} else {
							f1	= 0.5f;
							f2	= 0.5f;
						}
						span = new Span( trnsLen[i], maxTrnsLen );
						for( j = 1; j < 4096; j++ ) {
							frameBuf[0][j] = f1;
							frameBuf[1][j] = f2;
						}
						ts		= at.beginInsert( span, edit );
						for( start = span.getStart(); start < span.getStop(); start += j ) {
							j		= (int) Math.min( 4096, span.getStop() - start );
							at.continueWrite( ts, frameBuf, 0, j );
						}
						at.finishWrite( ts, edit );

					} // if( trnsLen[i] < maxTrnsLen )
					progress++;
					context.setProgression( (float) progress / (float) progressLen );
				} // for( i = 0; i < collAffectedTransmitters.size(); i++ )
				
				if( maxTrnsLen != docLength ) {	// adjust timeline
					edit.addEdit( new EditSetTimelineLength( this, doc, maxTrnsLen ));
				}
				if( !newSelSpan.isEmpty() ) {
					edit.addEdit( TimelineVisualEdit.select( this, doc, newSelSpan ));
				}
				edit.end();
				doc.getUndoManager().addEdit( edit );
				success = true;
			}
			catch( IOException e1 ) {
				edit.cancel();
				context.setException( e1 );
			}
			catch( UnsupportedFlavorException e2 ) {
				edit.cancel();
				context.setException( e2 );
			}
			
			return success ? DONE : FAILED;
		}

		public void processFinished( ProcessingThread context ) {}
		public void processCancel( ProcessingThread context ) {}
	} // class actionPasteClass
*/

	private class ActionSelectAll
	extends MenuAction
	{
		protected ActionSelectAll() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			doc.timeline.editSelect( this, new Span( 0, timelineLen ));
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

/*
	private class ActionCut
	extends MenuAction
	{
		private final ActionDelete actionClear = new ActionDelete();
		
		protected ActionCut() {
			// empty
		}

		public void actionPerformed( ActionEvent e )
		{
			if( editCopy() ) actionClear.perform();
		}
	}
*/

/*
	private class ActionDelete
	extends MenuAction
	implements ProcessingThread.Client
	{
		private ActionDelete()
		{
			super( AbstractApplication.getApplication().getResourceString( "menuDelete" ));
		}
		
		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		protected void perform()
		{
			Span  span;

			try {
				doc.bird.waitShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP );
				span = doc.timeline.getSelectionSpan();
				if( span.isEmpty() || doc.getActiveTransmitters().isEmpty() ) return;
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP);
			}

//			new ProcessingThread( this, root, root, doc, getValue( NAME ).toString(), null, Session.DOOR_TIMETRNSMTE | Session.DOOR_GRP );
			final ProcessingThread pt;
			final ProgressComponent pc = (MainFrame) AbstractApplication.getApplication().getComponent(  Main.COMP_MAIN );
			pt = new ProcessingThread( this, pc, getValue( NAME ).toString() );
			pt.start();
		}
		
		//
		//  This method is called by ProcessingThread
		//
		public int processRun( ProcessingThread context )
		{
			Span							span, span2, span3;
			List							collAffectedTransmitters;
			List							collUnaffectedTransmitters;
			CompoundSessionObjEdit			edit;
			int								i, j;
			Transmitter						trns;
			AudioTrail						at;
			float[][]						frameBuf	= new float[2][4096];
			int								progress, progressLen;
			boolean							success		= false;
			float							f1, f2;
			long							start;
			TrackSpan	ts;

			span						= doc.timeline.getSelectionSpan();
			collAffectedTransmitters	= doc.selectedTransmitters.getAll();
			edit						= new CompoundSessionObjEdit( this, doc, collAffectedTransmitters,
											Transmitter.OWNER_TRAJ, null, null, Session.DOOR_TIMETRNSMTE );
			collUnaffectedTransmitters  = doc.transmitters.getAll();	// XYZ
			collUnaffectedTransmitters.removeAll( collAffectedTransmitters );
			progress					= 0;
			progressLen					= collAffectedTransmitters.size();
			try {
				// -------------------- all transmitters affected, remove timeline span --------------------
				if( collUnaffectedTransmitters.isEmpty() ) {
					edit.addEdit( new EditRemoveTimeSpan( this, doc, span ));
					for( i = 0; i < collAffectedTransmitters.size(); i++ ) {
						at = ((Transmitter) collAffectedTransmitters.get( i )).getAudioTrail();
						at.remove( span, edit );
						progress++;
						context.setProgression( (float) progress / (float) progressLen );
					}
				// -------------------- insert fillup, then remove span from selected transmitters --------------------
				} else {
					span2 = new Span( doc.timeline.getLength(), doc.timeline.getLength() + span.getLength() );
					span3 = new Span( doc.timeline.getLength() - 1, doc.timeline.getLength() );
					assert doc.timeline.getLength() > 0 : doc.timeline.getLength();

					edit.addEdit( TimelineVisualEdit.select( this, doc, new Span() ));
					for( i = 0; i < collAffectedTransmitters.size(); i++ ) {
						trns	= (Transmitter) collAffectedTransmitters.get( i );
						at		= trns.getAudioTrail();
						at.read( span3, frameBuf, 0 );
						f1		= frameBuf[0][0];
						f2		= frameBuf[1][0];
						for( j = 1; j < 4096; j++ ) {
							frameBuf[0][j] = f1;
							frameBuf[1][j] = f2;
						}
						ts		= at.beginInsert( span2, edit );
						for( start = span2.getStart(); start < span2.getStop(); start += j ) {
							j		= (int) Math.min( 4096, span2.getStop() - start );
							at.continueWrite( ts, frameBuf, 0, j );
						}
						at.finishWrite( ts, edit );
						at.remove( span, edit );
						progress++;
						context.setProgression( (float) progress / (float) progressLen );
					}
				}
				edit.end(); // fires doc.tc.modified()
				doc.getUndoManager().addEdit( edit );
				success = true;
			}
			catch( IOException e1 ) {
				edit.cancel();
				context.setException( e1 );
			}
			
			return success ? DONE : FAILED;
		} // run

		public void processFinished( ProcessingThread context ) {}
		public void processCancel( ProcessingThread context ) {}
	}
*/
	/**
	 *  Increase or decrease the height
	 *  of the rows of the selected transmitters
	 */
// EEE
//	private class ActionRowHeight
//	extends AbstractAction
//	{
//		private final float factor;
//		
//		/**
//		 *  @param  factor  factors > 1 increase the row height,
//		 *					factors < 1 decrease.
//		 */
//		private ActionRowHeight( float factor )
//		{
//			super();
//			this.factor = factor;
//		}
//		
//		public void actionPerformed( ActionEvent e )
//		{
//			int						row, rowHeight;
//			JComponent				trnsEditView;
//			TransmitterRowHeader	trnsHead;
//			boolean					revalidate  = false;
//			
//			if( !doc.bird.attemptShared( Session.DOOR_TIMETRNS, 250 )) return;
//			try {
//				for( row = 0; row < collTransmitterEditors.size(); row++ ) {
//					trnsEditView= ((TransmitterEditor) collTransmitterEditors.get( row )).getView();
//					trnsHead	= (TransmitterRowHeader) collTransmitterHeaders.get( row );
//					if( !trnsHead.isSelected() ) continue;
//					rowHeight   = Math.min( 512, Math.max( 32, (int) (trnsHead.getHeight() * factor + 0.5f)));
//					setRowHeight( trnsHead, rowHeight );
//					setRowHeight( trnsEditView, rowHeight );
//					revalidate  = true;
//				}
//				if( revalidate ) {
//// EEE
////					ggTrackRowHeaderPanel.revalidate();
//					waveView.revalidate();
//					// XXX need to update vpTrackPanel!
//				}
//			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_TIMETRNS );
//			}
//		}
//	} // class actionRowHeightClass

	/**
	 *  Increase or decrease the width
	 *  of the visible time span
	 */
	private class ActionSpanWidth
	extends AbstractAction
	{
		private final float factor;
		
		/**
		 *  @param  factor  factors > 1 increase the span width (zoom out)
		 *					factors < 1 decrease (zoom in).
		 *					special value 0.0 means zoom to sample level
		 */
		protected ActionSpanWidth( float factor )
		{
			super();
			this.factor = factor;
		}
		
		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		public void perform()
		{
			long	pos, visiLen, start, stop;
			Span	visiSpan;
			
			visiSpan	= timelineVis;
			visiLen		= visiSpan.getLength();
			pos			= timelinePos; // doc.timeline.getPosition();
			if( factor == 0.0f ) {				// to sample level
				start	= Math.max( 0, pos - (wavePanel.getWidth() >> 1) );
				stop	= Math.min( timelineLen, start + wavePanel.getWidth() );
			} else if( factor < 1.0f ) {		// zoom in
				if( visiLen < 4 ) return;
				// if timeline pos visible -> try to keep it's relative position constant
				if( visiSpan.contains( pos )) {
					start	= pos - (long) ((pos - visiSpan.getStart()) * factor + 0.5f);
					stop    = start + (long) (visiLen * factor + 0.5f);
				// if timeline pos before visible span, zoom left hand
				} else if( visiSpan.getStart() > pos ) {
					start	= visiSpan.getStart();
					stop    = start + (long) (visiLen * factor + 0.5f);
				// if timeline pos after visible span, zoom right hand
				} else {
					stop	= visiSpan.getStop();
					start   = stop - (long) (visiLen * factor + 0.5f);
				}
			} else {			// zoom out
				start   = Math.max( 0, visiSpan.getStart() - (long) (visiLen * factor/4 + 0.5f) );
				stop    = Math.min( timelineLen, start + (long) (visiLen * factor + 0.5f) );
			}
			visiSpan	= new Span( start, stop );
			if( !visiSpan.isEmpty() ) {
				doc.timeline.editScroll( this, visiSpan );
			}
		}
	} // class actionSpanWidthClass

	private static final int SCROLL_SESSION_START	= 0;
	private static final int SCROLL_SELECTION_START	= 1;
	private static final int SCROLL_SELECTION_STOP	= 2;
	private static final int SCROLL_FIT_TO_SELECTION= 3;
	private static final int SCROLL_ENTIRE_SESSION	= 4;

	private class ActionScroll
	extends AbstractAction
	{
		private final int mode;
	
		protected ActionScroll( int mode )
		{
			super();
			
			this.mode = mode;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		public void perform()
		{
			UndoableEdit	edit	= null;
			Span			selSpan, newSpan;
			long			start, stop;
		
			if( mode == SCROLL_SESSION_START && transport.isRunning() ) {
				transport.stop();
			}
			selSpan		= timelineSel; // doc.timeline.getSelectionSpan();
			
			switch( mode ) {
			case SCROLL_SESSION_START:
				if( timelinePos != 0 ) {
					edit	= TimelineVisualEdit.position( this, doc, 0 ).perform();
					if( !timelineVis.contains( 0 )) {
						final CompoundEdit ce	= new BasicCompoundEdit();
						ce.addEdit( edit );
						newSpan	= new Span( 0, timelineVis.getLength() );
						ce.addEdit( TimelineVisualEdit.scroll( this, doc, newSpan ).perform() );
						ce.end();
						edit	= ce;
					}
				}
				break;
				
			case SCROLL_SELECTION_START:
				if( selSpan.isEmpty() ) selSpan = new Span( timelinePos, timelinePos );
				if( timelineVis.contains( selSpan.getStart() )) {
					start = Math.max( 0, selSpan.getStart() - (timelineVis.getLength() >> 1) );
				} else {
					start = Math.max( 0, selSpan.getStart() - (timelineVis.getLength() >> 3) );
				}
				stop	= Math.min( timelineLen, start + timelineVis.getLength() );
				newSpan	= new Span( start, stop );
				if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
					edit	= TimelineVisualEdit.scroll( this, doc, newSpan ).perform();
				}
				break;

			case SCROLL_SELECTION_STOP:
				if( selSpan.isEmpty() ) selSpan = new Span( timelinePos, timelinePos );
				if( timelineVis.contains( selSpan.getStop() )) {
					stop = Math.min( timelineLen, selSpan.getStop() + (timelineVis.getLength() >> 1) );
				} else {
					stop = Math.min( timelineLen, selSpan.getStop() + (timelineVis.getLength() >> 3) );
				}
				start	= Math.max( 0, stop - timelineVis.getLength() );
				newSpan	= new Span( start, stop );
				if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
					edit	= TimelineVisualEdit.scroll( this, doc, newSpan ).perform();
				}
				break;

			case SCROLL_FIT_TO_SELECTION:
				newSpan		= selSpan;
				if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
					edit	= TimelineVisualEdit.scroll( this, doc, newSpan ).perform();
				}
				break;

			case SCROLL_ENTIRE_SESSION:
				newSpan		= new Span( 0, timelineLen );
				if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
					edit	= TimelineVisualEdit.scroll( this, doc, newSpan ).perform();
				}
				break;

			default:
				assert false : mode;
				break;
			}
			if( edit != null ) doc.getUndoManager().addEdit( edit );
		}
	} // class actionScrollClass
	
	private static final int SELECT_TO_SESSION_START	= 0;
	private static final int SELECT_TO_SESSION_END		= 1;

	private class ActionSelect
	extends AbstractAction
	{
		private final int mode;
	
		protected ActionSelect( int mode )
		{
			super();
			
			this.mode = mode;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			Span			selSpan, newSpan = null;
		
			selSpan		= timelineSel; // doc.timeline.getSelectionSpan();
			if( selSpan.isEmpty() ) {
				selSpan	= new Span( timelinePos, timelinePos );
			}
			
			switch( mode ) {
			case SELECT_TO_SESSION_START:
				if( selSpan.getStop() > 0 ){
					newSpan = new Span( 0, selSpan.getStop() );
				}
				break;

			case SELECT_TO_SESSION_END:
				if( selSpan.getStart() < timelineLen ){
					newSpan = new Span( selSpan.getStart(), timelineLen );
				}
				break;

			default:
				assert false : mode;
				break;
			}
			if( newSpan != null && !newSpan.equals( selSpan )) {
				doc.timeline.editSelect( this, newSpan );
//					doc.getUndoManager().addEdit( TimelineVisualEdit.select( this, doc, newSpan ));
			}
		}
	} // class actionSelectClass
			
	private static final int SELECT_NEXT_REGION	= 0;
	private static final int SELECT_PREV_REGION	= 1;
	private static final int EXTEND_NEXT_REGION	= 2;
	private static final int EXTEND_PREV_REGION	= 3;

	private class ActionSelectRegion
	extends AbstractAction
	{
		private final int mode;
	
		protected ActionSelectRegion( int mode )
		{
			super();
			
			this.mode = mode;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			Span			selSpan;
			UndoableEdit	edit;
			long			start, stop;
			Marker			mark;
			int				idx;

			if( !markVisible ) return;
		
			selSpan		= timelineSel; // doc.timeline.getSelectionSpan();
			if( selSpan.isEmpty() ) selSpan = new Span( timelinePos, timelinePos );
			
			start		= selSpan.getStart();
			stop		= selSpan.getStop();
			
			switch( mode ) {
			case SELECT_NEXT_REGION:
			case EXTEND_NEXT_REGION:
				idx		= doc.markers.indexOf( stop + 1 );	// XXX check
				if( idx < 0 ) idx = -(idx + 1);

				if(	idx == doc.markers.getNumStakes() ) {
					stop	= timelineLen;
				} else {
					mark	= doc.markers.get( idx );
					stop	= mark.pos;
				}
				// (-(insertion point) - 1)

				if( mode == SELECT_NEXT_REGION ) {
					idx		= doc.markers.indexOf( stop - 1 );	// XXX check
					if( idx < 0 ) idx = -(idx + 2);
					
					if( idx == -1 ) {
						start	= 0;
					} else {
						mark	= doc.markers.get( idx );
						start	= mark.pos;
					}
				}
				break;

			case SELECT_PREV_REGION:
			case EXTEND_PREV_REGION:
				idx		= doc.markers.indexOf( start - 1 );	// XXX check
				if( idx < 0 ) idx = -(idx + 2);

				if(	idx == -1 ) {
					start	= 0;
				} else {
					mark	= doc.markers.get( idx );
					start	= mark.pos;
				}
				
				if( mode == SELECT_PREV_REGION ) {
					idx		= doc.markers.indexOf( start + 1 );	// XXX check
					if( idx < 0 ) idx = -(idx + 1);
					
					if( idx == doc.markers.getNumStakes() ) {
						stop	= timelineLen;
					} else {
						mark	= doc.markers.get( idx );
						stop	= mark.pos;
					}
				}
				break;

			default:
				assert false : mode;
				break;
			}
			
			if( (start == selSpan.getStart()) && (stop == selSpan.getStop()) ) return;
			
			edit	= TimelineVisualEdit.select( this, doc, new Span( start, stop )).perform();
			doc.getUndoManager().addEdit( edit );
		}
	} // class actionSelectRegionClass
		
	private class ActionDropMarker
	extends AbstractAction
	{
		protected ActionDropMarker() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			if( markVisible ) {
				markAxis.addMarker( timelinePos );
			}
		}
	} // class actionDropMarkerClass

// ---------------- internal classes ---------------- 

	private abstract class TimelineTool
	extends AbstractTool
	{
		private final List	collObservedComponents	= new ArrayList();
	
		private boolean adjustCatchBypass	= false;
		
		protected TimelineTool() { /* empty */ }

		public void toolAcquired( Component c )
		{
			super.toolAcquired( c );
			
			if( c instanceof Container ) addMouseListeners( (Container) c );
		}
		
		// additionally installs mouse input listeners on child components
		private void addMouseListeners( Container c )
		{
			Component	c2;
			
			for( int i = 0; i < c.getComponentCount(); i++ ) {
				c2 = c.getComponent( i );
				collObservedComponents.add( c2 );
				c2.addMouseListener( this );
				c2.addMouseMotionListener( this );
				if( c2 instanceof Container ) addMouseListeners( (Container) c2 );	// recurse
			}
		}
		
		// additionally removes mouse input listeners from child components
		private void removeMouseListeners()
		{
			Component	c;
		
			while( !collObservedComponents.isEmpty() ) {
				c	= (Component) collObservedComponents.remove( 0 );
				c.removeMouseListener( this );
				c.removeMouseMotionListener( this );
			}
		}

		public void toolDismissed( Component c )
		{
			super.toolDismissed( c );

			removeMouseListeners();
			
			if( adjustCatchBypass ) {
				adjustCatchBypass = false;
				removeCatchBypass();
			}
		}
		
		public void mousePressed( MouseEvent e ) 
		{
			adjustCatchBypass = true;
			addCatchBypass();
			
			super.mousePressed( e );
		}
		
		public void mouseReleased( MouseEvent e )
		{
			adjustCatchBypass = false;
			removeCatchBypass();
			
			super.mouseReleased( e );
		}
	}
	
	/*
	 *	Keyboard modifiers are consistent with Bias Peak:
	 *	Shift+Click = extend selection, Meta+Click = select all,
	 *	Alt+Drag = drag timeline position; double-click = Play
	 */
	private class TimelinePointerTool
	extends TimelineTool
	{
		private boolean shiftDrag, ctrlDrag, dragStarted = false;
		protected boolean validDrag = false;
		private long startPos;
		private int startX;

		private final Object[] argsCsr	= new Object[8];
		private final String[] csrInfo	= new String[3];
	
		protected TimelinePointerTool() { /* empty */ }

		public void paintOnTop( Graphics2D g )
		{
			// not necessary
		}
		
		protected void cancelGesture()
		{
			dragStarted = false;
			validDrag	= false;
		}
		
		public void mousePressed( MouseEvent e )
		{
			super.mousePressed( e );
		
			if( e.isMetaDown() ) {
				selectRegion( e );
				dragStarted = false;
				validDrag	= false;
			} else {
				shiftDrag	= e.isShiftDown();
				ctrlDrag	= e.isControlDown();
				dragStarted = false;
				validDrag	= true;
				startX		= e.getX();
				processDrag( e, false ); 
			}
		}

		public void mouseDragged( MouseEvent e )
		{
			final ObserverPalette observer;
			
			super.mouseDragged( e );

			if( validDrag ) {
				if( !dragStarted ) {
					if( shiftDrag || ctrlDrag || Math.abs( e.getX() - startX ) > 2 ) {
						dragStarted = true;
					} else return;
				}
				processDrag( e, true );
			}
			
			// cursor information
			observer = (ObserverPalette) app.getComponent( Main.COMP_OBSERVER );
			if( (observer != null) && observer.isVisible() && (observer.getShownTab() == ObserverPalette.CURSOR_TAB) ) {				
				showCursorInfo( SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), waveView ));
			}
		}
		
		private void showCursorInfo( Point screenPt )
		{
			final ObserverPalette	observer;
			
			final int				ch		= waveView.channelForPoint( screenPt );
			if( ch == -1 ) return;

			final DecimationInfo	info	= waveView.getDecimationInfo();
			if( info == null ) return;

			final long				pos		= timelineVis.getStart() + (long) 
										((double) screenPt.x / (double) waveView.getWidth() *
										 timelineVis.getLength());
			if( (pos < 0) || (pos >= timelineLen) ) return;
		
			final String				chName	= ""; // EEE doc.audioTracks.get( ch ).getName();
			final double				seconds	= pos / timelineRate;
			final AudioTrail 			at;
			final DecimatedWaveTrail	dt;
			final float[][]				data;
			final float[]				frame;
			float						f1;
			
			argsCsr[3]		= chName;
			argsCsr[0]		= new Long( pos );
			argsCsr[1]		= new Integer( (int) (seconds / 60) );
			argsCsr[2]		= new Float( seconds % 60 );
			
			csrInfo[0]		= msgCsr1.format( argsCsr );
			
			switch( info.model ) {
			case DecimatedTrail.MODEL_PCM:
// EEE
//				at			= doc.getAudioTrail();
//				data		= new float[ at.getChannelNum() ][];
//				data[ ch ]	= new float[ 1 ];
//				try {
//					at.readFrames( data, 0, new Span( pos, pos + 1 ));
//				}
//				catch( IOException e1 ) { return; }
//				f1			= data[ ch ][ 0 ];
//				argsCsr[4]	= new Float( f1 );
//				argsCsr[5]	= new Float( Math.log( Math.abs( f1 )) * TWENTYDIVLOG10 );
//				csrInfo[1]	= msgCsr2PCMFloat.format( argsCsr );
//				if( csrInfoIsInt ) {
//					argsCsr[6]	= new Long( (long) (f1 * (1L << (csrInfoBits - 1))) );
//					argsCsr[7]	= new Integer( csrInfoBits );
//					csrInfo[2]	= msgCsr3PCMInt.format( argsCsr );
//				} else {
//					csrInfo[2]	= "";
//				}
				break;
				
			case DecimatedTrail.MODEL_FULLWAVE_PEAKRMS:
// EEE
//				dt			= doc.getDecimatedWaveTrail();
//				if( dt == null ) return;
//				frame		= new float[ dt.getNumModelChannels() ];
//				try {
//					dt.readFrame( Math.min( dt.getNumDecimations() - 1, info.idx + 1 ), pos, ch, frame );
//				}
//				catch( IOException e1 ) { return; }
//				f1			= Math.max( frame[ 0 ], -frame[ 1 ] );	// peak pos/neg
//				argsCsr[4]	= new Float( f1 );
//				argsCsr[5]	= new Float( Math.log( f1 ) * TWENTYDIVLOG10 );
//				f1			= (float) Math.sqrt( frame[ 2 ]);	// mean sqr pos/neg
//				argsCsr[6]	= new Float( f1 );
//				argsCsr[7]	= new Float( Math.log( f1 ) * TWENTYDIVLOG10 );
//				csrInfo[1]	= msgCsr2Peak.format( argsCsr );
//				csrInfo[2]	= msgCsr3RMS.format( argsCsr );
				break;
				
			default:
				return;
			}

			observer = (ObserverPalette) app.getComponent( Main.COMP_OBSERVER );
			if( observer != null ) observer.showCursorInfo( csrInfo );
		}
		
		private void selectRegion( MouseEvent e )
		{
			final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), wavePanel );

			Span			span, span2;
			long			pos, start, stop;
			UndoableEdit	edit;
			int				idx;
			Marker			mark;

			span        = timelineVis; // doc.timeline.getVisibleSpan();
			span2		= timelineSel; // doc.timeline.getSelectionSpan();
			pos			= span.getStart() + (long) (pt.getX() / getComponent().getWidth() *
													span.getLength());
			pos			= Math.max( 0, Math.min( timelineLen, pos ));

			stop		= timelineLen;
			start		= 0;

			if( markVisible ) {
				idx		= doc.markers.indexOf( pos + 1 );	// XXX check
				if( idx < 0 ) idx = -(idx + 1);
				if(	idx < doc.markers.getNumStakes() ) {
					mark	= doc.markers.get( idx );
					stop	= mark.pos;
				}
				idx		= doc.markers.indexOf( stop - 1 );	// XXX check
				if( idx < 0 ) idx = -(idx + 2);
				if( idx >= 0 ) {
					mark	= doc.markers.get( idx );
					start	= mark.pos;
				}
			}
			
			// union with current selection
			if( e.isShiftDown() && !span2.isEmpty() ) {
				start	= Math.min( start, span2.start );
				stop	= Math.max( stop, span2.stop );
			}
			
			span	= new Span( start, stop );
			if( span.equals( span2 )) {
				span	= new Span( 0, timelineLen );
			}
			if( !span.equals( span2 )) {
				edit = TimelineVisualEdit.select( this, doc, span ).perform();
				doc.getUndoManager().addEdit( edit );
			}
		}

		private void processDrag( MouseEvent e, boolean hasStarted )
		{
			final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), wavePanel );
			
			Span			span, span2;
			long			position;
			UndoableEdit	edit;
		   
			span        = timelineVis; // doc.timeline.getVisibleSpan();
			span2		= timelineSel; // doc.timeline.getSelectionSpan();
			position    = span.getStart() + (long) (pt.getX() / getComponent().getWidth() *
													span.getLength());
			position    = Math.max( 0, Math.min( timelineLen, position ));
			if( !hasStarted && !ctrlDrag ) {
				if( shiftDrag ) {
					if( span2.isEmpty() ) {
						span2 = new Span( timelinePos, timelinePos );
					}
					startPos = Math.abs( span2.getStart() - position ) >
							   Math.abs( span2.getStop() - position ) ?
									span2.getStart() : span2.getStop();
					span2	= new Span( Math.min( startPos, position ),
										Math.max( startPos, position ));
					edit	= TimelineVisualEdit.select( this, doc, span2 ).perform();
				} else {
					startPos = position;
					if( span2.isEmpty() ) {
						edit = TimelineVisualEdit.position( this, doc, position ).perform();
					} else {
						edit = new CompoundEdit();
						edit.addEdit( TimelineVisualEdit.select( this, doc, new Span() ).perform() );
						edit.addEdit( TimelineVisualEdit.position( this, doc, position ).perform() );
						((CompoundEdit) edit).end();
					}
				}
			} else {
				if( ctrlDrag ) {
					edit	= TimelineVisualEdit.position( this, doc, position ).perform();
				} else {
					span2	= new Span( Math.min( startPos, position ),
										Math.max( startPos, position ));
					edit	= TimelineVisualEdit.select( this, doc, span2 ).perform();
				}
			}
			doc.getUndoManager().addEdit( edit );
		}

		public void mouseReleased( MouseEvent e )
		{
			super.mouseReleased( e );

			Span span2;

			// resets the position to selection start if (and only if) the selection was
			// made anew, ctrl key is not pressed and transport is not running
			if( dragStarted && !shiftDrag && !ctrlDrag && !transport.isRunning() ) {
				span2 = timelineSel; // doc.timeline.getSelectionSpan();
				if( !span2.isEmpty() && timelinePos != span2.getStart() ) {
					doc.timeline.editPosition( this, span2.getStart() );
				}
			}
			
			dragStarted = false;
			validDrag	= false;
		}

		public void mouseClicked( MouseEvent e )
		{
			super.mouseClicked( e );

			if( (e.getClickCount() == 2) && !e.isMetaDown() && !transport.isRunning() ) {
				transport.play( 1.0f );
			}
		}

		// on Mac, Ctrl+Click is interpreted as
		// popup trigger by the system which means
		// no successive mouseDragged calls are made,
		// instead mouseMoved is called ...
		public void mouseMoved( MouseEvent e )
		{
			super.mouseMoved( e );

			mouseDragged( e );
		}
	}

	private class TimelineZoomTool
	extends TimelineTool
	{
		private boolean					validDrag	= false, dragStarted = false;
		private long					startPos;
		private Point					startPt;
		private long					position;
		private final javax.swing.Timer	zoomTimer;
		protected final Rectangle		zoomRect	= new Rectangle();
		private MenuAction actionZoomIn		= null;
		private MenuAction actionZoomOut	= null;

		protected TimelineZoomTool()
		{
			zoomTimer = new javax.swing.Timer( 250, new ActionListener() {
				public void actionPerformed( ActionEvent e )
				{
					setZoomRect( zoomRect );
				}
			});
		}

		public void toolAcquired( final Component c )
		{
			super.toolAcquired( c );
			c.setCursor( zoomCsr[ 0 ]);
			if( c instanceof JComponent ) {
				final JComponent jc = (JComponent) c;
				if( actionZoomOut == null ) actionZoomOut = new MenuAction( "zoomOut",
				  KeyStroke.getKeyStroke( KeyEvent.VK_ALT, InputEvent.ALT_DOWN_MASK, false )) {
					public void actionPerformed( ActionEvent e ) {
						c.setCursor( zoomCsr[ 1 ]);
					}
				};
				if( actionZoomIn == null ) actionZoomIn = new MenuAction( "zoomIn",
				 	KeyStroke.getKeyStroke( KeyEvent.VK_ALT, 0, true )) {
					public void actionPerformed( ActionEvent e ) {
						c.setCursor( zoomCsr[ 0 ]);
					}
				};
				actionZoomOut.installOn( jc, JComponent.WHEN_IN_FOCUSED_WINDOW );
				actionZoomIn.installOn( jc, JComponent.WHEN_IN_FOCUSED_WINDOW );
			}
		}

		public void toolDismissed( Component c )
		{
			super.toolDismissed( c );
			if( c instanceof JComponent ) {
				final JComponent jc = (JComponent) c;
				if( actionZoomOut != null ) actionZoomOut.deinstallFrom( jc, JComponent.WHEN_IN_FOCUSED_WINDOW );
				if( actionZoomIn != null ) actionZoomIn.deinstallFrom( jc, JComponent.WHEN_IN_FOCUSED_WINDOW );
			}
		}

		public void paintOnTop( Graphics2D g )
		{
			// not necessary
		}
		
		public void mousePressed( MouseEvent e )
		{
			super.mousePressed( e );
		
			if( e.isAltDown() ) {
				dragStarted = false;
				validDrag	= false;
				clickZoom( 2.0f, e );
			} else {
				dragStarted = false;
				validDrag	= true;
				processDrag( e, false ); 
			}
		}

		public void mouseDragged( MouseEvent e )
		{
			super.mouseDragged( e );

			if( validDrag ) {
				if( !dragStarted ) {
					if( Math.abs( e.getX() - startPt.x ) > 2 ) {
						dragStarted = true;
						zoomTimer.restart();
					} else return;
				}
				processDrag( e, true );
			}
		}

		protected void cancelGesture()
		{
			zoomTimer.stop();
			setZoomRect( null );
			dragStarted = false;
			validDrag	= false;
		}

		public void mouseReleased( MouseEvent e )
		{
			super.mouseReleased( e );

			Span span;

			if( dragStarted ) {
				cancelGesture();
				span = new Span( Math.min( startPos, position ),
								 Math.max( startPos, position ));
				if( !span.isEmpty() ) {
					doc.timeline.editScroll( this, span );
				}
			}
			
			validDrag	= false;
		}

		// zoom to mouse position
		public void mouseClicked( MouseEvent e )
		{
			super.mouseClicked( e );

			if( !e.isAltDown() ) clickZoom( 0.5f, e );
		}
		
		private void clickZoom( float factor, MouseEvent e )
		{
			long	pos, visiLen, start, stop;
			Span	visiSpan;
			
			visiSpan	= timelineVis;
			visiLen		= visiSpan.getLength();
			pos			= visiSpan.getStart() + (long) ((double) e.getX() / (double) getComponent().getWidth() *
													visiSpan.getLength());
			visiLen		= (long) (visiLen * factor + 0.5f);
			if( visiLen < 2 ) return;
			
			start		= Math.max( 0, Math.min( timelineLen, pos - (long) ((pos - visiSpan.getStart()) * factor + 0.5f) ));
			stop		= start + visiLen;
			if( stop > timelineLen ) {
				stop	= timelineLen;
				start	= Math.max( 0, stop - visiLen );
			}
			visiSpan	= new Span( start, stop );
			if( !visiSpan.isEmpty() ) {
				doc.timeline.editScroll( this, visiSpan );
			}
		}

		private void processDrag( MouseEvent e, boolean hasStarted )
		{
			final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), wavePanel );
			
			Span	span;
			int		zoomX;
		   
			span        = timelineVis;
			position    = span.getStart() + (long) (pt.getX() / getComponent().getWidth() *
													span.getLength());
			position    = Math.max( 0, Math.min( timelineLen, position ));
			if( !hasStarted ) {
				startPos= position;
				startPt	= pt;
			} else {
				zoomX	= Math.min( startPt.x, pt.x );
				zoomRect.setBounds( zoomX, waveView.getY() + 6, Math.abs( startPt.x - pt.x ), waveView.getHeight() - 12 );
				setZoomRect( zoomRect );
			}
		}
	}
}