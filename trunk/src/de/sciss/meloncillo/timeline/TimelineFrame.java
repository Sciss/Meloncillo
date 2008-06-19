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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.app.DynamicListening;
import de.sciss.app.LaterInvocationManager;
import de.sciss.common.ProcessingThread;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.MenuAction;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.edit.EditRemoveTimeSpan;
import de.sciss.meloncillo.edit.EditSetTimelineLength;
import de.sciss.meloncillo.edit.EditSetTimelinePosition;
import de.sciss.meloncillo.edit.EditSetTimelineScroll;
import de.sciss.meloncillo.edit.EditSetTimelineSelection;
import de.sciss.meloncillo.edit.SyncCompoundSessionObjEdit;
import de.sciss.meloncillo.gui.AbstractTool;
import de.sciss.meloncillo.gui.GraphicsUtil;
import de.sciss.meloncillo.gui.MenuFactory;
import de.sciss.meloncillo.gui.ToolAction;
import de.sciss.meloncillo.gui.ToolActionEvent;
import de.sciss.meloncillo.gui.ToolActionListener;
import de.sciss.meloncillo.io.MultirateTrackEditor;
import de.sciss.meloncillo.io.TrackList;
import de.sciss.meloncillo.io.TrackSpan;
import de.sciss.meloncillo.realtime.RealtimeConsumer;
import de.sciss.meloncillo.realtime.RealtimeConsumerRequest;
import de.sciss.meloncillo.realtime.RealtimeContext;
import de.sciss.meloncillo.realtime.RealtimeProducer;
import de.sciss.meloncillo.realtime.Transport;
import de.sciss.meloncillo.session.DocumentFrame;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionCollection;
import de.sciss.meloncillo.session.SessionObject;
import de.sciss.meloncillo.transmitter.Transmitter;
import de.sciss.meloncillo.transmitter.TransmitterEditor;
import de.sciss.meloncillo.transmitter.TransmitterRowHeader;
import de.sciss.meloncillo.util.PrefsUtil;
import de.sciss.meloncillo.util.TransferableCollection;


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
            DynamicListening, ClipboardOwner,
			RealtimeConsumer
{
	private final Main				root;
    private final TimelineAxis		axis;
    private final TimelineScroll	scroll;
	private final Transport			transport;
	
	private final TimelineToolBar	ttb;

	// --- collections ---
	private final List				collTransmitterEditors		= new ArrayList();
	private final List				collTransmitterHeaders		= new ArrayList();
	// maps transmitters (keys) to editors (values)
 	private final Map				hashTransmittersToEditors   = new HashMap();

	// --- table handling ---
	private final JScrollPane		ggScrollPane;
	private final JPanel			ggTrackPanel;
	private final JPanel			ggTrackRowHeaderPanel;
	private final TimelineViewport	vpTrackPanel;
	
	// --- tools ---
	
	private final   Map				tools						= new HashMap();
	private			AbstractTool	activeTool					= null;
	private final	TimelinePointerTool pointerTool;

	// --- actions ---
	private final Action			actionIncHeight, actionDecHeight;
	private final ActionSpanWidth	actionIncWidth, actionDecWidth;
	private final ActionDelete		actionClear;

	private long					currentPos					= 0;

	private final LaterInvocationManager  lim;
	
	private final ComponentListener	rowHeightListener;

/* mimic shortcuts from ProTools (6.4):
(ok)	left / right : scroll to selection start/end when selection exceeds window view
(ok)	return : return to start of session
(ok)	shift return : extends selection to start of session
(ok)	alt + shift return : extends selection to end of session
(ok)	meta + ] / [ horiz. zoom in / out !
(ok)	alt+f : fit window to selection
(ok)	alt+a : view entire session
(todo)	escape : cycle through edit tools
(n/a)	f1 / f2 / f3 / f4 : shuffle / slip / spot / grid
(rplcd)	f5 / f6 / f7 / f8 / f9 / f10 : zoomer / trimmer / selector / grabber / scrubber / pencil
(n/a)	tilde : cycle through edit modes
(todo)	timeline insertion follows playback : N
(n/a)	meta + D : duplicate
(rplcd)	numpad 4 : loop playback
(todo)	numpad 1 / 2 : rewind / ffwd
(ok)	numpad 0 : play / stop
*/
	/**
	 *  Constructs a new timeline window with
	 *  all the sub elements. Installs the
	 *  global key commands. (a TimelineFrame
	 *  should be created only once in the application).
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
	public TimelineFrame( Main root, final Session doc )
	{
		super( doc );

		this.root   = root;
		transport   = doc.getTransport();

		final Container			cp		= getContentPane();
		final InputMap			imap	= getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
		final ActionMap			amap	= getActionMap();
		final Box				box		= Box.createHorizontalBox();
		final Application		app		= AbstractApplication.getApplication();
		final JPanel			gp		= GUIUtil.createGradientPanel();

		setTitle( app.getResourceString( "frameTimeline" ));

		ttb			= new TimelineToolBar( root );
		ttb.setOpaque( false );
		gp.add( ttb );

		lim			= new LaterInvocationManager( new LaterInvocationManager.Listener() {
			// o egal
			public void laterInvocation( Object o )
			{
				vpTrackPanel.updatePositionAndRepaint();
			}
		});

//		rp.setPreferredSize( new Dimension( 640, 640 )); // XXX
		
        axis        = new TimelineAxis( root, doc );
		ggTrackPanel= new TrackPanel();
ggTrackPanel.setPreferredSize( new Dimension( 640, 320 ));
		ggTrackPanel.setOpaque( false );	// crucial for correct TimelineViewport() paint update calls!
		ggTrackPanel.setLayout( new SpringLayout() );
		ggTrackRowHeaderPanel = new JPanel();
		ggTrackRowHeaderPanel.setLayout( new SpringLayout() );
		ggScrollPane= new JScrollPane();
		vpTrackPanel= new TimelineViewport();
		vpTrackPanel.setView( ggTrackPanel );
		ggScrollPane.setViewport( vpTrackPanel );
		ggScrollPane.setColumnHeaderView( axis );
		ggScrollPane.setRowHeaderView( ggTrackRowHeaderPanel );
        scroll      = new TimelineScroll( root, doc );
		box.add( scroll );
        if( app.getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
            box.add( Box.createHorizontalStrut( 16 ));
        }
        
//		split.setLeftComponent( new SessionGroupTable( root, doc, doc, SessionGroupTable.VIEW_GROUPS ));
//		split.setRightComponent( ggScrollPane );
//		split.setOneTouchExpandable( true );
//		split.setContinuousLayout( false );
//		split.setLastDividerLocation( 80 );
//		split.setDividerLocation( 0 );
//		cp.add( split, BorderLayout.CENTER );
		cp.add( ggScrollPane, BorderLayout.CENTER );
        cp.add( box, BorderLayout.SOUTH );
		cp.add( gp, BorderLayout.NORTH );
		
		// --- Tools ---
		
		pointerTool = new TimelinePointerTool();
		tools.put( new Integer( ToolAction.POINTER ), pointerTool );
		tools.put( new Integer( ToolAction.ZOOM ),		new TimelineZoomTool() );

		// --- Listener ---
		addDynamicListening( this );

//		this.addMouseListener( new MouseAdapter() {
//			public void mousePressed( MouseEvent e )
//			{
//				showCursorTab();
//			}
//		});

        doc.transmitters.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				syncEditors();
				vpTrackPanel.updateAndRepaint();
			}
			
			public void sessionObjectChanged( SessionCollection.Event e ) {}
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		});

        doc.activeTransmitters.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				java.util.List		coll;
				boolean				visible		= false;
				SessionObject		so;
				TransmitterEditor	trnsEdit;
				boolean				revalidate	= false;
			
//System.err.println( "lala "+collTransmitterEditors.size()+" ; "+System.currentTimeMillis() );
				switch( e.getModificationType() ) {
				case SessionCollection.Event.ACTION_ADDED:
					visible = true;
					// THRU
				case SessionCollection.Event.ACTION_REMOVED:
					coll	= e.getCollection();
collLp:				for( int i = 0; i < coll.size(); i++ ) {
						so	= (SessionObject) coll.get( i );
						for( int j = 0; j < collTransmitterEditors.size(); j++ ) {
							trnsEdit = (TransmitterEditor) collTransmitterEditors.get( j );
							if( trnsEdit.getTransmitter() == so ) {
								trnsEdit.getView().setVisible( visible );
								((JComponent) collTransmitterHeaders.get( j )).setVisible( visible );
//System.err.println( "setting "+so.getName()+(visible ? " visible" : " invisible") );
								revalidate = true;
								continue collLp;
							}
						}
					}
					if( revalidate ) {
						revalidateView();
					}
					break;
				default:
					break;
				}
//				syncEditors();
//				vpTrackPanel.updateAndRepaint();
//System.err.println( "gaga "+System.currentTimeMillis() );
			}
			
			public void sessionObjectChanged( SessionCollection.Event e ) {}
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		});

        doc.selectedTransmitters.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				vpTrackPanel.updateAndRepaint();
			}
			
			public void sessionObjectChanged( SessionCollection.Event e ) {}
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		});
		
		ttb.addToolActionListener( this );
		
		rowHeightListener	= new ComponentAdapter() {
			public void componentResized( ComponentEvent e ) {
				vpTrackPanel.updateSelectionAndRepaint();
			}

			public void componentShown( ComponentEvent e ) {
				vpTrackPanel.updateSelectionAndRepaint();
			}

			public void componentHidden( ComponentEvent e ) {
				vpTrackPanel.updateSelectionAndRepaint();
			}
		};

//		addListener( new AbstractWindow.Adapter() {
//			public void windowClosing( AbstractWindow.Event e )
//			{
//				dispose();
//			}
//		});
//		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE ); // window listener see above!

		// --- Actions ---
		actionClear			= new ActionDelete();
		actionIncHeight		= new ActionRowHeight( 2.0f );
		actionDecHeight		= new ActionRowHeight( 0.5f );
		actionIncWidth		= new ActionSpanWidth( 2.0f );
		actionDecWidth		= new ActionSpanWidth( 0.5f );

		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, KeyEvent.CTRL_MASK ), "inch" );
		amap.put( "inch", actionIncHeight );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, KeyEvent.CTRL_MASK ), "dech" );
		amap.put( "dech", actionDecHeight );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, KeyEvent.CTRL_MASK ), "incw" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_OPEN_BRACKET, MenuFactory.MENU_SHORTCUT ), "incw" );
		amap.put( "incw", actionIncWidth );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, KeyEvent.CTRL_MASK ), "decw" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_CLOSE_BRACKET, MenuFactory.MENU_SHORTCUT ), "decw" );
		amap.put( "decw", actionDecWidth );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0 ), "retn" );
		amap.put( "retn", new ActionScroll( SCROLL_SESSION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, 0 ), "left" );
		amap.put( "left", new ActionScroll( SCROLL_SELECTION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, 0 ), "right" );
		amap.put( "right", new ActionScroll( SCROLL_SELECTION_STOP ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_F, KeyEvent.ALT_MASK ), "fit" );
		amap.put( "fit", new ActionScroll( SCROLL_FIT_TO_SELECTION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_A, KeyEvent.ALT_MASK ), "entire" );
		amap.put( "entire", new ActionScroll( SCROLL_ENTIRE_SESSION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK ), "seltobeg" );
		amap.put( "seltobeg", new ActionSelect( SELECT_TO_SESSION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK + KeyEvent.ALT_MASK ), "seltoend" );
		amap.put( "seltoend", new ActionSelect( SELECT_TO_SESSION_END ));

		// -------

//	    HelpGlassPane.setHelp( getRootPane(), "TimelineFrame" );	// EEE
		AbstractWindowHandler.setDeepFont( cp );
		init();
		app.addComponent( Main.COMP_TIMELINE, this );
	}

	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_TIMELINE );
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
		return new Point2D.Float( 0.95f, 0.25f );
	}

	private void revalidateView()
	{
		ggTrackRowHeaderPanel.setLayout( new SpringLayout() );
		ggTrackPanel.setLayout( new SpringLayout() );
		GUIUtil.makeCompactSpringGrid( ggTrackRowHeaderPanel, ggTrackRowHeaderPanel.getComponentCount(), 1, 0, 0, 1, 1 ); // initX, initY, padX, padY
		GUIUtil.makeCompactSpringGrid( ggTrackPanel, ggTrackPanel.getComponentCount(), 1, 0, 0, 1, 1 ); // initX, initY, padX, padY
		ggTrackRowHeaderPanel.revalidate();
		ggTrackPanel.revalidate();
	}
	
	/*
	 *  When transmitters are created or removed
	 *  this method sync's the transmitter collection
	 *  with the editor collection.
	 *
	 *  Sync: syncs to tc
	 */
	private void syncEditors()
	{
		int					rows;
		Transmitter			trns;
		TransmitterEditor   trnsEdit;
		TransmitterRowHeader trnsHead;
		boolean				revalidate = false;
	
		try {
			doc.bird.waitShared( Session.DOOR_TRNS | Session.DOOR_GRP );
			rows	= collTransmitterEditors.size();
			assert collTransmitterHeaders.size() == rows : collTransmitterHeaders.size();
			// first kick out editors whose tracks have been removed
			for( int row = 0; row < rows; row++ ) {
				trnsEdit	= (TransmitterEditor) collTransmitterEditors.get( row );
				trns		= trnsEdit.getTransmitter();
				if( !doc.transmitters.contains( trns )) {
					revalidate	= true;
					trnsEdit	= (TransmitterEditor) collTransmitterEditors.remove( row );
					trnsHead	= (TransmitterRowHeader) collTransmitterHeaders.remove( row );
					trnsHead.removeComponentListener( rowHeightListener );
					rows--;
                    // XXX : dispose trnsEdit (e.g. free vectors, remove listeners!!)
					hashTransmittersToEditors.remove( trns );
					ggTrackPanel.remove( trnsEdit.getView() );
					ggTrackRowHeaderPanel.remove( trnsHead );
					row--;
				}
			}
			// next look for newly added transmitters and create editors for them
			for( int i = 0; i < doc.transmitters.size(); i++ ) {
				trns		= (Transmitter) doc.transmitters.get( i );
				trnsEdit	= (TransmitterEditor) hashTransmittersToEditors.get( trns );
				if( trnsEdit == null ) {
					revalidate = true;
					try {
						trnsEdit = (TransmitterEditor) trns.getDefaultEditor().newInstance();	// XXX deligate to SurfaceFrame
						trnsEdit.init( root, doc, trns );
						trnsHead = new TransmitterRowHeader( root, doc, trns );
						trnsHead.addComponentListener( rowHeightListener );
						hashTransmittersToEditors.put( trns, trnsEdit );
						collTransmitterEditors.add( trnsEdit );
						collTransmitterHeaders.add( trnsHead );
						rows++;
						setRowHeight( trnsHead, 64 ); // XXX
						setRowHeight( trnsEdit.getView(), 64 ); // XXX
						ggTrackRowHeaderPanel.add( trnsHead, i );
						ggTrackPanel.add( trnsEdit.getView(), i );
					}
					catch( InstantiationException e1 ) {
						System.err.println( e1.getLocalizedMessage() );
					}
					catch( IllegalAccessException e2 ) {
						System.err.println( e2.getLocalizedMessage() );
					}
					catch( IllegalArgumentException e3 ) {
						System.err.println( e3.getLocalizedMessage() );
					}
				}
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_GRP );
		}
		
		if( revalidate ) {
			GUIUtil.makeCompactSpringGrid( ggTrackRowHeaderPanel, rows, 1, 0, 0, 1, 1 ); // initX, initY, padX, padY
			GUIUtil.makeCompactSpringGrid( ggTrackPanel, rows, 1, 0, 0, 1, 1 ); // initX, initY, padX, padY
			ggTrackRowHeaderPanel.revalidate();
			ggTrackPanel.revalidate();
		}

		if( activeTool != null ) {	// re-set tool to update mouse listeners
			activeTool.toolDismissed( ggTrackPanel );
			activeTool.toolAcquired( ggTrackPanel );
		}
	}

	private void setRowHeight( JComponent comp, int height )
	{
		comp.setMinimumSize(   new Dimension( comp.getMinimumSize().width,   height ));
		comp.setMaximumSize(   new Dimension( comp.getMaximumSize().width,   height ));
		comp.setPreferredSize( new Dimension( comp.getPreferredSize().width, height ));
	}

// ---------------- RealtimeConsumer interface ---------------- 

	/**
	 *  Requests 30 fps notification (no data block requests).
	 *  This is used to update the timeline position during transport
	 *  playback.
	 */
	public RealtimeConsumerRequest createRequest( RealtimeContext context )
	{
		RealtimeConsumerRequest request = new RealtimeConsumerRequest( this, context );
		// 30 fps is visually fluent
		request.notifyTickStep  = RealtimeConsumerRequest.approximateStep( context, 30 );
		request.notifyTicks		= true;
		request.notifyOffhand	= true;
		return request;
	}
	
	public void realtimeTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
	{
		this.currentPos = currentPos;
		lim.queue( this );
		scroll.setPosition( currentPos, 50, TimelineScroll.TYPE_TRANSPORT );
	}

	public void offhandTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
	{
		this.currentPos = currentPos;
		vpTrackPanel.updatePositionAndRepaint();
		scroll.setPosition( currentPos, 0, pointerTool.validDrag ?
			TimelineScroll.TYPE_DRAG : TimelineScroll.TYPE_UNKNOWN );
	}

	public void realtimeBlock( RealtimeContext context, RealtimeProducer.Source source, boolean even ) {}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
        doc.timeline.addTimelineListener( this );
		transport.addRealtimeConsumer( this );
//		syncEditors();
		vpTrackPanel.updateAndRepaint();
    }

    public void stopListening()
    {
        doc.timeline.removeTimelineListener( this );
		transport.removeRealtimeConsumer( this );
    }

// ---------------- ToolListener interface ---------------- 
 
	// sync: attemptShared DOOR_TRNS
	public void toolChanged( ToolActionEvent e )
	{
		Transmitter			trns;
		TransmitterEditor	trnsEdit;
	
		if( activeTool != null ) {
			activeTool.toolDismissed( ggTrackPanel );
//			removeMouseMotionListener( cursorListener );
		}

		// forward event to all editors that implement ToolActionListener
		if( !doc.bird.attemptShared( Session.DOOR_TRNS | Session.DOOR_GRP, 250 )) return;
		try {
			for( int i = 0; i < doc.activeTransmitters.size(); i++ ) {
				trns		= (Transmitter) doc.activeTransmitters.get( i );
				trnsEdit	= (TransmitterEditor) hashTransmittersToEditors.get( trns );
				if( trnsEdit instanceof ToolActionListener ) {
					((ToolActionListener) trnsEdit).toolChanged( e );
				}
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_GRP );
		}

		activeTool = (AbstractTool) tools.get( new Integer( e.getToolAction().getID() ));
		if( activeTool != null ) {
			ggTrackPanel.setCursor( e.getToolAction().getDefaultCursor() );
			activeTool.toolAcquired( ggTrackPanel );
//			showCursorTab();
//			addMouseMotionListener( cursorListener );
		} else {
			ggTrackPanel.setCursor( null );
		}
	}

// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		vpTrackPanel.updateSelectionAndRepaint();
    }
    
	public void timelineChanged( TimelineEvent e )
    {
		vpTrackPanel.updateAndRepaint();
    }

	/**
	 *  Nothing here, real stuff
	 *  is in offhandTick()!
	 */
	public void timelinePositioned( TimelineEvent e ) {}

    public void timelineScrolled( TimelineEvent e )
    {
		vpTrackPanel.updateAndRepaint();
    }

 // ---------------- EditMenuListener interface ---------------- 

	/*
	 *  Selects the whole timeline, does not
	 *  alter the transmitter selection
	 */
	private void editSelectAll()
	{
		Span			span;
		UndoableEdit	edit;
	
		if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
		try {
			span	= new Span( 0, doc.timeline.getLength() );
			edit	= new EditSetTimelineSelection( this, doc, span );
			doc.getUndoManager().addEdit( edit );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}

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
		Span							span;
		java.util.List					collAffectedTransmitters;
		final java.util.List			v		= new ArrayList();
		MultirateTrackEditor			mte;
		boolean							success = false;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();

		try {
			doc.bird.waitShared( Session.DOOR_TIMETRNSMTE );
			span = doc.timeline.getSelectionSpan();
			if( span.isEmpty() ) return false;

			collAffectedTransmitters = doc.selectedTransmitters.getAll();
			if( collAffectedTransmitters.isEmpty() ) return false;

			for( int i = 0; i < collAffectedTransmitters.size(); i++ ) {
				mte = ((Transmitter) collAffectedTransmitters.get( i )).getTrackEditor();
				v.add( mte.getTrackList( span ));
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
	}

// ---------------- DocumentFrame abstract methods ----------------
	
	protected Action getCutAction() { return new ActionCut(); }
	protected Action getCopyAction() { return new ActionCopy(); }
	protected Action getPasteAction() { return new ActionPaste(); }
	protected Action getDeleteAction() { return actionClear; }
	protected Action getSelectAllAction() { return new ActionSelectAll(); }
	
// ---------------- ClipboardOwner interface ---------------- 

	public void lostOwnership( Clipboard clipboard, Transferable contents )
	{
		// XXX evtl. dispose() aufrufen
	}

// ---------------- internal action classes ---------------- 

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
			pt = new ProcessingThread( this, root, getValue( NAME ).toString() );
			pt.putClientArg( "coll", coll );
			pt.start();
		}
		
		/**
		 *  This method is called by ProcessingThread
		 */
		public int processRun( ProcessingThread context ) throws IOException
		{
			final List						coll		= (List) context.getClientArg( "coll" );
			List							collAffectedTransmitters;
			Transferable					t;
			int								i, j, numTrns;
			long							position, docLength, pasteLength, start;
			Transmitter						trns;
			TrackList						tl;
			MultirateTrackEditor			mte;
			SyncCompoundSessionObjEdit	edit;
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
			edit						= new SyncCompoundSessionObjEdit( this, doc, collAffectedTransmitters,
											Transmitter.OWNER_TRAJ, null, null, Session.DOOR_TIMETRNSMTE );
			position					= doc.timeline.getPosition();
			oldSelSpan					= doc.timeline.getSelectionSpan();
			docLength					= doc.timeline.getLength();
			trnsLen						= new long[ numTrns ];

			progress					= 0;
			progressLen					= numTrns << 1;

			try {
				if( !oldSelSpan.isEmpty() ) { // deselect
					edit.addEdit( new EditSetTimelineSelection( this, doc, new Span() ));
//							position = oldSelSpan.getStart();
				}
				newSelSpan = new Span( position, position );

				// ---- first try to paste contents ----
				for( i = 0; i < collAffectedTransmitters.size(); i++ ) {
					trns		= (Transmitter) collAffectedTransmitters.get( i );
					trnsLen[i]  = docLength;
					if( doc.selectedTransmitters.contains( trns )) {
						mte	= trns.getTrackEditor();
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
								mte.insert( position, tl, edit ); // insert clipboard content
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
						mte		= trns.getTrackEditor();
						if( trnsLen[i] > 0 ) {
							mte.read( new Span( trnsLen[i] - 1, trnsLen[i] ), frameBuf, 0 );
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
						ts		= mte.beginInsert( span, edit );
						for( start = span.getStart(); start < span.getStop(); start += j ) {
							j		= (int) Math.min( 4096, span.getStop() - start );
							mte.continueWrite( ts, frameBuf, 0, j );
						}
						mte.finishWrite( ts, edit );

					} // if( trnsLen[i] < maxTrnsLen )
					progress++;
					context.setProgression( (float) progress / (float) progressLen );
				} // for( i = 0; i < collAffectedTransmitters.size(); i++ )
				
				if( maxTrnsLen != docLength ) {	// adjust timeline
					edit.addEdit( new EditSetTimelineLength( this, doc, maxTrnsLen ));
				}
				if( !newSelSpan.isEmpty() ) {
					edit.addEdit( new EditSetTimelineSelection( this, doc, newSelSpan ));
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

	private class ActionSelectAll
	extends MenuAction
	{
		protected ActionSelectAll() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			editSelectAll();
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

	private class ActionCut
	extends MenuAction
	{
		protected ActionCut() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			if( editCopy() ) actionClear.perform();
		}
	}

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
				if( span.isEmpty() || doc.activeTransmitters.isEmpty() ) return;
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP);
			}

//			new ProcessingThread( this, root, root, doc, getValue( NAME ).toString(), null, Session.DOOR_TIMETRNSMTE | Session.DOOR_GRP );
			final ProcessingThread pt;
			pt = new ProcessingThread( this, root, getValue( NAME ).toString() );
			pt.start();
		}
		
		/**
		 *  This method is called by ProcessingThread
		 */
		public int processRun( ProcessingThread context )
		{
			Span							span, span2, span3;
			java.util.List					collAffectedTransmitters;
			java.util.List					collUnaffectedTransmitters;
			SyncCompoundSessionObjEdit	edit;
			int								i, j;
			Transmitter						trns;
			MultirateTrackEditor			mte;
			float[][]						frameBuf	= new float[2][4096];
			int								progress, progressLen;
			boolean							success		= false;
			float							f1, f2;
			long							start;
			TrackSpan	ts;

			span						= doc.timeline.getSelectionSpan();
			collAffectedTransmitters	= doc.selectedTransmitters.getAll();
			edit						= new SyncCompoundSessionObjEdit( this, doc, collAffectedTransmitters,
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
						mte = ((Transmitter) collAffectedTransmitters.get( i )).getTrackEditor();
						mte.remove( span, edit );
						progress++;
						context.setProgression( (float) progress / (float) progressLen );
					}
				// -------------------- insert fillup, then remove span from selected transmitters --------------------
				} else {
					span2 = new Span( doc.timeline.getLength(), doc.timeline.getLength() + span.getLength() );
					span3 = new Span( doc.timeline.getLength() - 1, doc.timeline.getLength() );
					assert doc.timeline.getLength() > 0 : doc.timeline.getLength();

					edit.addEdit( new EditSetTimelineSelection( this, doc, new Span() ));
					for( i = 0; i < collAffectedTransmitters.size(); i++ ) {
						trns	= (Transmitter) collAffectedTransmitters.get( i );
						mte		= trns.getTrackEditor();
						mte.read( span3, frameBuf, 0 );
						f1		= frameBuf[0][0];
						f2		= frameBuf[1][0];
						for( j = 1; j < 4096; j++ ) {
							frameBuf[0][j] = f1;
							frameBuf[1][j] = f2;
						}
						ts		= mte.beginInsert( span2, edit );
						for( start = span2.getStart(); start < span2.getStop(); start += j ) {
							j		= (int) Math.min( 4096, span2.getStop() - start );
							mte.continueWrite( ts, frameBuf, 0, j );
						}
						mte.finishWrite( ts, edit );
						mte.remove( span, edit );
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

	/**
	 *  Increase or decrease the height
	 *  of the rows of the selected transmitters
	 */
	private class ActionRowHeight
	extends AbstractAction
	{
		private final float factor;
		
		/**
		 *  @param  factor  factors > 1 increase the row height,
		 *					factors < 1 decrease.
		 */
		private ActionRowHeight( float factor )
		{
			super();
			this.factor = factor;
		}
		
		public void actionPerformed( ActionEvent e )
		{
			int						row, rowHeight;
			JComponent				trnsEditView;
			TransmitterRowHeader	trnsHead;
			boolean					revalidate  = false;
			
			if( !doc.bird.attemptShared( Session.DOOR_TIMETRNS, 250 )) return;
			try {
				for( row = 0; row < collTransmitterEditors.size(); row++ ) {
					trnsEditView= ((TransmitterEditor) collTransmitterEditors.get( row )).getView();
					trnsHead	= (TransmitterRowHeader) collTransmitterHeaders.get( row );
					if( !trnsHead.isSelected() ) continue;
					rowHeight   = Math.min( 512, Math.max( 32, (int) (trnsHead.getHeight() * factor + 0.5f)));
					setRowHeight( trnsHead, rowHeight );
					setRowHeight( trnsEditView, rowHeight );
					revalidate  = true;
				}
				if( revalidate ) {
					ggTrackRowHeaderPanel.revalidate();
					ggTrackPanel.revalidate();
					// XXX need to update vpTrackPanel!
				}
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIMETRNS );
			}
		}
	} // class actionRowHeightClass

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
		 */
		private ActionSpanWidth( float factor )
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
			Span			visiSpan;
			long			pos, visiLen, start, stop;
			UndoableEdit	edit;
			
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				visiSpan	= doc.timeline.getVisibleSpan();
				visiLen		= visiSpan.getLength();
				pos			= doc.timeline.getPosition();
				if( factor < 1.0f ) {		// zoom in
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
					stop    = Math.min( doc.timeline.getLength(), start + (long) (visiLen * factor + 0.5f) );
				}
				visiSpan	= new Span( start, stop );
				if( !visiSpan.isEmpty() ) {
					edit	= new EditSetTimelineScroll( this, doc, new Span( start, stop ));
					doc.getUndoManager().addEdit( edit );
				}
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
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
	
		private ActionScroll( int mode )
		{
			super();
			
			this.mode = mode;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			UndoableEdit	edit	= null;
			Span			selSpan, visiSpan, newSpan;
			long			start, stop;
		
			if( mode == SCROLL_SESSION_START && transport.isRunning() ) {
				transport.stopAndWait();
			}
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				selSpan		= doc.timeline.getSelectionSpan();
				visiSpan	= doc.timeline.getVisibleSpan();
				
				switch( mode ) {
				case SCROLL_SESSION_START:
					if( doc.timeline.getPosition() != 0 ) {
						edit	= new EditSetTimelinePosition( this, doc, 0 );
					}
					break;
					
				case SCROLL_SELECTION_START:
					if( !selSpan.isEmpty() && !visiSpan.contains( selSpan.getStart() )) {
						start	= Math.max( 0, selSpan.getStart() - (visiSpan.getLength() >> 3) );
						stop	= Math.min( doc.timeline.getLength(), start + visiSpan.getLength() );
						newSpan	= new Span( start, stop );
						if( !visiSpan.equals( newSpan ) && !newSpan.isEmpty() ) {
							edit	= new EditSetTimelineScroll( this, doc, newSpan );
						}
					}
					break;

				case SCROLL_SELECTION_STOP:
					if( !selSpan.isEmpty() && !visiSpan.contains( selSpan.getStop() )) {
						stop	= Math.min( doc.timeline.getLength(), selSpan.getStop() + (visiSpan.getLength() >> 3) );
						start	= Math.max( 0, stop - visiSpan.getLength() );
						newSpan	= new Span( start, stop );
						if( !visiSpan.equals( newSpan ) && !newSpan.isEmpty() ) {
							edit	= new EditSetTimelineScroll( this, doc, newSpan );
						}
					}
					break;

				case SCROLL_FIT_TO_SELECTION:
					newSpan		= selSpan;
					if( !visiSpan.equals( newSpan ) && !newSpan.isEmpty() ) {
						edit	= new EditSetTimelineScroll( this, doc, newSpan );
					}
					break;

				case SCROLL_ENTIRE_SESSION:
					newSpan		= new Span( 0, doc.timeline.getLength() );
					if( !visiSpan.equals( newSpan ) && !newSpan.isEmpty() ) {
						edit	= new EditSetTimelineScroll( this, doc, newSpan );
					}
					break;

				default:
					assert false : mode;
					break;
				}
				if( edit != null ) doc.getUndoManager().addEdit( edit );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	} // class actionScrollClass
	
	private static final int SELECT_TO_SESSION_START	= 0;
	private static final int SELECT_TO_SESSION_END		= 1;

	private class ActionSelect
	extends AbstractAction
	{
		private final int mode;
	
		private ActionSelect( int mode )
		{
			super();
			
			this.mode = mode;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			Span			selSpan, newSpan = null;
		
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				selSpan		= doc.timeline.getSelectionSpan();
				if( selSpan.isEmpty() ) {
					selSpan	= new Span( currentPos, currentPos );
				}
				
				switch( mode ) {
				case SELECT_TO_SESSION_START:
					if( selSpan.getStop() > 0 ){
						newSpan = new Span( 0, selSpan.getStop() );
					}
					break;

				case SELECT_TO_SESSION_END:
					if( selSpan.getStart() < doc.timeline.getLength() ){
						newSpan = new Span( selSpan.getStart(), doc.timeline.getLength() );
					}
					break;

				default:
					assert false : mode;
					break;
				}
				if( newSpan != null && !newSpan.equals( selSpan )) {
					doc.getUndoManager().addEdit( new EditSetTimelineSelection( this, doc, newSpan ));
				}
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	} // class actionSelectClass

// ---------------- the viewport component---------------- 

	private class TimelineViewport
	extends JViewport
	{
		// --- painting ---
		private final Color colrSelection		= GraphicsUtil.colrSelection;
		private final Color colrSelection2		= new Color( 0xA0, 0xA0, 0xA0, 0x3F );  // selected timeline span over unselected trns
		private final Color colrPosition		= new Color( 0xFF, 0x00, 0x00, 0x4F );
		private final Color colrZoom			= new Color( 0xA0, 0xA0, 0xA0, 0x7F );
//		private final Color colrPosition		= Color.red;
		private Rectangle   recentRect			= new Rectangle();
		private int			position			= -1;
		private Rectangle   positionRect		= new Rectangle();
		private final ArrayList	selections		= new ArrayList();
		private final ArrayList	selectionColors	= new ArrayList();
		private Rectangle	selectionRect		= new Rectangle();
		
		private long		timelinePos;
		private Span		timelineVis			= new Span();
		private Span		timelineSel			= null;
		
		private Rectangle   updateRect			= new Rectangle();
		private Rectangle   zoomRect			= null;
		private float[]		dash				= { 3.0f, 5.0f };

		private final Stroke[] zoomStroke			= {
			new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, dash, 0.0f ),
			new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, dash, 4.0f ),
			new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, dash, 6.0f ),
		};
		private int			zoomStrokeIdx		= 0;
	
		public TimelineViewport()
		{
			super();
			setScrollMode( BACKINGSTORE_SCROLL_MODE );
		}
	
		/**
		 *  See paint( Graphics ) for details
		 */
		public void paintChildren( Graphics g )
		{
			super.paintChildren( g );

			int			i;
			Rectangle   r;

			for( i = 0; i < selections.size(); i++ ) {
				r = (Rectangle) selections.get( i );
				g.setColor( (Color) selectionColors.get( i ));
				g.fillRect( selectionRect.x, r.y - recentRect.y, selectionRect.width, r.height );
			}
			
			if( zoomRect != null ) {
				g.setColor( colrZoom );
				((Graphics2D) g).setStroke( zoomStroke[ zoomStrokeIdx ]);
				g.drawRect( zoomRect.x, zoomRect.y, zoomRect.width, zoomRect.height );
			}
		}

		public void setZoomRect( Rectangle r )
		{
			zoomRect		= r;
			zoomStrokeIdx	= (zoomStrokeIdx + 1) % zoomStroke.length;
			repaint();
		}

		/**
		 *  Custom painting. This is really tricky... It seems that JViewport
		 *  saves its new backing store image, at the end of the paint() call.
		 *  Therefore, we first call super.paint() which ensures that our
		 *  song position line isn't painted in the backing store image. This is
		 *  because paintChildren() is hell slow so for realtime playback update
		 *  of the song position line we call paintDirty() directly and instead
		 *  of calling paintChildren() we redraw the dirty part of the
		 *  backing store image. To speed things up, the paining of the selected
		 *  areas is performed at the end of the paintChildren() method and
		 *  therefore is "frozen" in the backing store image.
		 */
		public void paint( Graphics g )
		{
			Rectangle currentRect = getViewRect();

			if( !recentRect.equals( currentRect )) {
				recalcTransforms();
			}

			super.paint( g );

			g.setColor( colrPosition );
			g.drawLine( position, 0, position, recentRect.height );
		}

		/**
		 *  See paint( Graphics ) for details
		 */
		private void paintDirty( Graphics g, Rectangle updateRect )
		{
			Image img = backingStoreImage;
			if( img != null ) {
				g.drawImage( img, updateRect.x, updateRect.y,
							 updateRect.width + updateRect.x, updateRect.height + updateRect.y,
							 updateRect.x, updateRect.y,
							 updateRect.width + updateRect.x, updateRect.height + updateRect.y,
							 this );
			}
			g.setColor( colrPosition );
			g.drawLine( position, 0, position, recentRect.height );
		}

		/**
		 *  Only call in the Swing thread!
		 */
		private void updatePositionAndRepaint()
		{
			boolean pEmpty, cEmpty;
			int		x, x2;
			
			pEmpty = (positionRect.x + positionRect.width < 0) || (positionRect.x > recentRect.width);
			if( !pEmpty ) updateRect.setBounds( positionRect );
			if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return; // XXX get the whole sync outa here
			try {
				timelineVis = doc.timeline.getVisibleSpan();	
//				timelinePos = doc.timeline.getPosition();
timelinePos = currentPos;
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIME );
			}
			recalcTransforms();
			cEmpty = (positionRect.x + positionRect.width < 0) || (positionRect.x > recentRect.width);
			if( pEmpty ) {
				if( cEmpty ) return;
				x   = Math.max( 0, positionRect.x );
				x2  = Math.min( recentRect.width, positionRect.x + positionRect.width );
				updateRect.setBounds( x, positionRect.y, x2 - x, positionRect.height );
			} else {
				if( cEmpty ) {
					x   = Math.max( 0, updateRect.x );
					x2  = Math.min( recentRect.width, updateRect.x + updateRect.width );
					updateRect.setBounds( x, updateRect.y, x2 - x, updateRect.height );
				} else {
					x   = Math.max( 0, Math.min( updateRect.x, positionRect.x ));
					x2  = Math.min( recentRect.width, Math.max( updateRect.x + updateRect.width,
																positionRect.x + positionRect.width ));
					updateRect.setBounds( x, updateRect.y, x2 - x, updateRect.height );
				}
			}
//			if( !updateRect.isEmpty() ) repaint( updateRect );
			Graphics g = getGraphics();
			if( g != null ) {
				paintDirty( g, updateRect );
				g.dispose();
			}
		}

		/**
		 *  Only call in the Swing thread!
		 */
		private void updateSelectionAndRepaint()
		{
			updateRect.setBounds( selectionRect );
			if( !doc.bird.attemptShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP, 250 )) return;
			try {
				updateSelection();
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP );
			}
			recalcTransforms();
			if( updateRect.isEmpty() ) {
				updateRect.setBounds( selectionRect );
			} else if( !selectionRect.isEmpty() ) {
				updateRect = updateRect.union( selectionRect );
			}
			if( !updateRect.isEmpty() ) repaint( updateRect );
//			if( !updateRect.isEmpty() ) {
//				Graphics g = getGraphics();
//				if( g != null ) {
//					paintDirty( g, updateRect );
//				}
//				g.dispose();
//			}
		}
		
		/**
		 *  Only call in the Swing thread!
		 */
		private void updateAndRepaint()
		{
			updateRect = selectionRect.union( positionRect );
			if( !doc.bird.attemptShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP )) return;
			try {
				timelineVis = doc.timeline.getVisibleSpan();
//				timelinePos = doc.timeline.getPosition();
timelinePos = currentPos;
				updateSelection();
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP );
			}
			recalcTransforms();
			updateRect = updateRect.union( positionRect ).union( selectionRect );
			if( !updateRect.isEmpty() ) repaint( updateRect );
		}
		
		private void recalcTransforms()
		{
			int x, w;
			double scale;
			
			recentRect = getViewRect();
		
			if( !timelineVis.isEmpty() ) {
				scale           = (double) recentRect.width / (double) timelineVis.getLength();
				position		= (int) ((timelinePos - timelineVis.getStart()) * scale + 0.5);
				positionRect.setBounds( position, 0, 1, recentRect.height );
				if( timelineSel != null ) {
					x			= (int) ((timelineSel.getStart() - timelineVis.getStart()) * scale + 0.5) + recentRect.x;
					w			= Math.max( 1, (int) (timelineSel.getLength() * scale + 0.5));
					selectionRect.setBounds( x, 0, w, recentRect.height );
				} else {
					selectionRect.setBounds( 0, 0, 0, 0 );
				}
			} else {
				position		= -1;
				positionRect.setBounds( 0, 0, 0, 0 );
				selectionRect.setBounds( 0, 0, 0, 0 );
			}
		}

		// sync: caller must sync on timeline + grp + tc
		private void updateSelection()
		{
			int					i;
			TransmitterEditor   edit;
			Transmitter			trns;

			timelineSel = doc.timeline.getSelectionSpan();

			selections.clear();
			selectionColors.clear();
			if( !timelineSel.isEmpty() ) {
				for( i = 0; i < doc.activeTransmitters.size(); i++ ) {
					trns	= (Transmitter) doc.activeTransmitters.get( i );
					edit	= (TransmitterEditor) hashTransmittersToEditors.get( trns );
					if( edit == null ) continue;
					selections.add( edit.getView().getBounds() );
					selectionColors.add( doc.selectedTransmitters.contains( trns ) ? colrSelection : colrSelection2 );
				}
			}
		}
	}

	private class TrackPanel
	extends JPanel
	{
		private TrackPanel()
		{
			super();
			
//			enableEvents( AWTEvent.MOUSE_EVENT_MASK );
		}
		
//		protected void processMouseEvent( MouseEvent e )
//		{
//			super.processMouseEvent( e );
//		
//			System.err.println( "processMouseEvent : "+e.getID() );
//		}
	}

	private abstract class TimelineTool
	extends AbstractTool
	{
		private final List	collObservedComponents	= new ArrayList();
//		private final List	collOldCursors			= new ArrayList();
	
		public void toolAcquired( Component c )
		{
			super.toolAcquired( c );
			
			if( c instanceof Container ) addMouseListeners( (Container) c );
		}
		
		// additionally installs mouse input listeners on child components
		private void addMouseListeners( Container c )
		{
			Component	c2;
//			Cursor		csr	= c.getCursor();
			
			for( int i = 0; i < c.getComponentCount(); i++ ) {
				c2 = c.getComponent( i );
				collObservedComponents.add( c2 );
//				collOldCursors.add( csr );
				c2.addMouseListener( this );
				c2.addMouseMotionListener( this );
//				c2.setCursor( c.getCursor() );
				if( c2 instanceof Container ) addMouseListeners( (Container) c2 );	// recurse
			}
		}
		
		// additionally removes mouse input listeners from child components
		private void removeMouseListeners()
		{
			Component	c;
//			Cursor		csr;
		
			while( !collObservedComponents.isEmpty() ) {
				c	= (Component) collObservedComponents.remove( 0 );
//				csr	= (Cursor) collOldCursors.remove( 0 );
				c.removeMouseListener( this );
				c.removeMouseMotionListener( this );
//				c.setCursor( csr );
			}
		}

		public void toolDismissed( Component c )
		{
			super.toolDismissed( c );

			removeMouseListeners();
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
		private boolean shiftDrag, ctrlDrag, validDrag = false, dragStarted = false;
		private long startPos;
		private int startX;
	
		public void paintOnTop( Graphics2D g )
		{
			// not necessary
		}
		
		public void mousePressed( MouseEvent e )
		{
			if( e.isMetaDown() ) {
				editSelectAll();
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
		
//			selectionStart  = -1;
//			dragTimelinePosition( e );
		}

		public void mouseDragged( MouseEvent e )
		{
			if( validDrag ) {
				if( !dragStarted ) {
					if( shiftDrag || ctrlDrag || Math.abs( e.getX() - startX ) > 2 ) {
						dragStarted = true;
					} else return;
				}
				processDrag( e, true );
			}
		}
		
		private void processDrag( MouseEvent e, boolean hasStarted )
		{
			final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), ggTrackPanel );
			
			Span			span, span2;
			long			position;
			UndoableEdit	edit;
		   
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				span        = doc.timeline.getVisibleSpan();
				span2		= doc.timeline.getSelectionSpan();
				position    = span.getStart() + (long) ((double) pt.getX() / (double) getComponent().getWidth() *
														(double) span.getLength());
				position    = Math.max( 0, Math.min( doc.timeline.getLength(), position ));
				if( !hasStarted && !ctrlDrag ) {
					if( shiftDrag ) {
						if( span2.isEmpty() ) {
							span2 = new Span( currentPos, currentPos );
						}
						startPos = Math.abs( span2.getStart() - position ) >
								   Math.abs( span2.getStop() - position ) ?
										span2.getStart() : span2.getStop();
						span2	= new Span( Math.min( startPos, position ),
											Math.max( startPos, position ));
						edit	= new EditSetTimelineSelection( this, doc, span2 );
					} else {
						startPos = position;
						if( span2.isEmpty() ) {
							edit = new EditSetTimelinePosition( this, doc, position );
						} else {
							edit = new CompoundEdit();
							edit.addEdit( new EditSetTimelineSelection( this, doc, new Span() ));
							edit.addEdit( new EditSetTimelinePosition( this, doc, position ));
							((CompoundEdit) edit).end();
						}
					}
				} else {
					if( ctrlDrag ) {
						edit	= new EditSetTimelinePosition( this, doc, position );
//System.err.println( "setting to "+position );
					} else {
						span2	= new Span( Math.min( startPos, position ),
											Math.max( startPos, position ));
						edit	= new EditSetTimelineSelection( this, doc, span2 );
					}
				}
				doc.getUndoManager().addEdit( edit );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}

		public void mouseReleased( MouseEvent e )
		{
			Span span2;

			if( dragStarted && !shiftDrag && !ctrlDrag ) {
				if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
				try {
					span2 = doc.timeline.getSelectionSpan();
					if( !span2.isEmpty() && doc.timeline.getPosition() != span2.getStart() ) {
						doc.getUndoManager().addEdit( new EditSetTimelinePosition( this, doc, span2.getStart() ));
					}
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_TIME );
				}
			}
			
			dragStarted = false;
			validDrag	= false;
		}

		public void mouseClicked( MouseEvent e )
		{
			if( (e.getClickCount() == 2) && !transport.isRunning() ) {
				transport.goPlay();
			}
		}

		// on Mac, Ctrl+Click is interpreted as
		// popup trigger by the system which means
		// no successive mouseDragged calls are made,
		// instead mouseMoved is called ...
		public void mouseMoved( MouseEvent e )
		{
			mouseDragged( e );
		}

		public void mouseEntered( MouseEvent e ) {}
		public void mouseExited( MouseEvent e ) {}

		protected void cancelGesture()
		{
			dragStarted = false;
			validDrag	= false;
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
		private final Rectangle			zoomRect	= new Rectangle();

		private TimelineZoomTool()
		{
			zoomTimer = new javax.swing.Timer( 250, new ActionListener() {
				public void actionPerformed( ActionEvent e )
				{
					vpTrackPanel.setZoomRect( zoomRect );
				}
			});
		}

		public void paintOnTop( Graphics2D g )
		{
			// not necessary
		}
		
		public void mousePressed( MouseEvent e )
		{
			if( e.isAltDown() ) {
				dragStarted = false;
				validDrag	= false;
				actionIncWidth.perform();
			} else {
				dragStarted = false;
				validDrag	= true;
				processDrag( e, false ); 
			}
		}

		public void mouseDragged( MouseEvent e )
		{
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

		public void mouseReleased( MouseEvent e )
		{
			Span span;

			if( dragStarted ) {
				zoomTimer.stop();
				vpTrackPanel.setZoomRect( null );
				if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
				try {
					span = new Span( Math.min( startPos, position ),
									 Math.max( startPos, position ));
					if( !span.isEmpty() ) {
						doc.getUndoManager().addEdit( new EditSetTimelineScroll( this, doc, span ));
					}
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_TIME );
				}
			}
			
			dragStarted = false;
			validDrag	= false;
		}

		private void processDrag( MouseEvent e, boolean hasStarted )
		{
			final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), ggTrackPanel );
			
			Span	span;
			int		zoomX, zoomY;
		   
			if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
			try {
				span        = doc.timeline.getVisibleSpan();
				position    = span.getStart() + (long) ((double) pt.getX() / (double) getComponent().getWidth() *
														(double) span.getLength());
				position    = Math.max( 0, Math.min( doc.timeline.getLength(), position ));
				if( !hasStarted ) {
					startPos= position;
					startPt	= pt;
				} else {
					zoomX	= Math.min( startPt.x, pt.x );
					zoomY	= Math.min( startPt.y, pt.y );
					zoomRect.setBounds( zoomX, zoomY, Math.abs( startPt.x - pt.x ),
													  Math.abs( startPt.y - pt.y ));
					vpTrackPanel.setZoomRect( zoomRect );
				}
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIME );
			}
		}

		public void mouseEntered( MouseEvent e ) {}
		public void mouseExited( MouseEvent e ) {}
		public void mouseMoved( MouseEvent e ) {}
		public void mouseClicked( MouseEvent e ) {}

		protected void cancelGesture()
		{
			zoomTimer.stop();
			vpTrackPanel.setZoomRect( null );
			dragStarted = false;
			validDrag	= false;
		}
	}
}