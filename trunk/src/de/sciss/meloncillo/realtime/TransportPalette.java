/*
 *  TransportPalette.java
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
 *  Change log:
 *		06-Jun-04	Shortcut for Play/Stop now Ctrl+Space, Shortcut for Loop is Ctrl+Shift+L
 *		13-Jun-04	Timeline Position field
 *		22-Jul-04	moved to realtime package.
 *		29-Jul-04	seems the StackOverflow bug was caused by the updateTimeLabel()
 *					method. Replaced by custom class TimeLabel which doesn't need
 *					to construct objects all the time. TimeLabel flashes red when dropout occur.
 *		31-Jul-04   DynamicAncestorAdapter replaces DynamicComponentAdapter
 *      24-Dec-04   extends BasicPalette
 *      27-Dec-04   added online help
 *		26-Mar-05	added cueing; new keyboard shortcuts
 *		16-Jul-05	fixed empty loop spans
 */

package de.sciss.meloncillo.realtime;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.text.*;
import java.util.*;
import javax.swing.*;
//import javax.swing.border.*;
import javax.swing.event.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.timeline.*;

import de.sciss.app.*;
import de.sciss.common.AppWindow;
import de.sciss.gui.*;
import de.sciss.io.*;

/**
 *	A GUI component showing
 *	basic transport gadgets. This class
 *	invokes the appropriate methods in the
 *	<code>Transport</code> class when these
 *	gadgets are clicked.
 *	<p><pre>
 *	Keyb.shortcuts :	space or numpad-0 : play / stop
 *						G : go to time
 *	</pre>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo		cueing sometimes uses an obsolete start position.
 *				idea: cue speed changes with zoom level
 */
public class TransportPalette
extends AppWindow
implements  TimelineListener, TransportListener, RealtimeConsumer,
			DynamicListening
{
	private final Session			doc;
	private final Transport			transport;
    
	private final JButton			ggPlay, ggStop;
	private final JToggleButton		ggLoop;
	private final actionLoopClass	actionLoop;

	private final ToolBar			toolBar;
	private final TimeLabel			lbTime;
	
	private int						rate;
	private int						customGroup		= 3;

	private static final MessageFormat   msgFormat =
		new MessageFormat( "{0,number,integer}:{1,number,00.000}", Locale.US );		// XXX US locale

	// forward / rewind cueing
	private boolean					isCueing		= false;
	private int						cueStep;
	private final javax.swing.Timer	cueTimer;
	private long					cuePos;

	/**
	 *	Creates a new transport palette. Other classes
	 *	may wish to add custom gadgets using <code>addButton</code>
	 *	afterwards.
	 *
	 *	@param	root	Application root
	 *	@param	doc		Session document
	 */
	public TransportPalette( final Main root, final Session doc )
	{
		super( PALETTE );
		setResizable( false );
		
		this.doc	= doc;
		transport   = doc.getTransport();

//        final int		ctrlShift   = KeyEvent.CTRL_MASK + KeyEvent.SHIFT_MASK;
        final Container	cp			= getContentPane();

		final AbstractAction	actionPlay, actionStop, actionGoToTime;
		final JButton			ggFFwd, ggRewind;
		final JPanel			gp	= GUIUtil.createGradientPanel();
		final Application		app	= AbstractApplication.getApplication();

		setTitle( app.getResourceString( "paletteTransport" ));

		toolBar			= new ToolBar( ToolBar.HORIZONTAL );
		toolBar.setOpaque( false );

        ggRewind		= new JButton();
		GraphicsUtil.setToolIcons( ggRewind, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_REWIND ));
		ggRewind.addChangeListener( new CueListener( ggRewind, -100 ));
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( ggRewind, KeyStroke.getKeyStroke(
//											  KeyEvent.VK_NUMPAD1, 0 )));

		actionStop		= new actionStopClass();
        ggStop			= new JButton( actionStop );
		GraphicsUtil.setToolIcons( ggStop, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_STOP ));

		actionPlay		= new actionPlayClass();
        ggPlay			= new JButton( actionPlay );
		GraphicsUtil.setToolIcons( ggPlay, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_PLAY ));

//		root.menuFactory.addGlobalKeyCommand( new actionTogglePlayStopClass( 
//											  KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, 0 )));
//		root.menuFactory.addGlobalKeyCommand( new actionTogglePlayStopClass( 
//											  KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD0, 0 )));
// EEE

        ggFFwd			= new JButton();
		GraphicsUtil.setToolIcons( ggFFwd, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_FASTFORWARD ));
		ggFFwd.addChangeListener( new CueListener( ggFFwd, 100 ));
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( ggFFwd, KeyStroke.getKeyStroke(
//											  KeyEvent.VK_NUMPAD2, 0 )));

		actionLoop		= new actionLoopClass();
		ggLoop			= new JToggleButton( actionLoop );
		GraphicsUtil.setToolIcons( ggLoop, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_LOOP ));
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( ggLoop, KeyStroke.getKeyStroke(
//											  KeyEvent.VK_DIVIDE, 0 )));
// EEE
		toolBar.addButton( ggRewind );
		toolBar.addButton( ggStop );
		toolBar.addButton( ggPlay );
		toolBar.addButton( ggFFwd );
		toolBar.addToggleButton( ggLoop, 2 );
//        HelpGlassPane.setHelp( toolBar, "TransportTools" );	// EEE
        
		actionGoToTime  = new actionGoToTimeClass( KeyStroke.getKeyStroke( KeyEvent.VK_G, 0 ));
		
		lbTime			= new TimeLabel();
		lbTime.setOpaque( false );
//      HelpGlassPane.setHelp( lbTime, "TransportPosition" );
		lbTime.setCursor( new Cursor( Cursor.HAND_CURSOR ));
		lbTime.addMouseListener( new MouseAdapter() {
			public void mouseClicked( MouseEvent e )
			{
				actionGoToTime.actionPerformed( null );
				lbTime.black();
			}
			
			public void mouseEntered( MouseEvent e )
			{
				lbTime.blue();
			}

			public void mouseExited( MouseEvent e )
			{
				lbTime.black();
			}
		});
//		root.menuFactory.addGlobalKeyCommand( actionGoToTime );	// EEE
		
		gp.add( toolBar );
		gp.add( Box.createHorizontalStrut( 8 ));
		gp.add( lbTime );
		cp.add( gp );
		
		// --- Listener ---
		addDynamicListening( this );

//		addListener( new AbstractWindow.Adapter() {
//			public void windowClosing( AbstractWindow.Event e )
//			{
//				dispose();
//			}
//		});
//		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE ); // window listener see above!

		cueTimer = new javax.swing.Timer( 25, new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 200 )) return;
				try {
					cuePos = Math.max( 0, Math.min( doc.timeline.getLength(), cuePos + (cueStep * rate) / 1000 ));
					doc.getUndoManager().addEdit( TimelineVisualEdit.position( this, doc, cuePos ).perform() );
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_TIME );
				}
			}
		});

		init();
		app.addComponent( Main.COMP_TRANSPORT, this );
	}

	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_OBSERVER );
		super.dispose();
	}

	/**
	 *	Causes the timeline position label
	 *	to blink red to indicate a dropout error
	 */
	public void blink()
	{
//		lbTime.blink();	// EEE
	}

	/**
	 *	Adds a new button to the transport palette
	 *
	 *	@param	b	the button to add
	 */
	public void addButton( AbstractButton b )
	{
		if( b instanceof JToggleButton ) {
			toolBar.addToggleButton( (JToggleButton) b, customGroup );
			customGroup++;
		} else {
			toolBar.addButton( b );
		}
		pack();
	}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		doc.timeline.addTimelineListener( this );
		transport.addTransportListener( this );
		transport.addRealtimeConsumer( this );
//		updateTimeLabel();
		rate = doc.timeline.getRate();	// sync ? XXX
    }

    public void stopListening()
    {
		doc.timeline.removeTimelineListener( this );
		transport.removeTransportListener( this );
		transport.removeRealtimeConsumer( this );
    }
    
	protected boolean autoUpdatePrefs()
	{
		return true;
	}

	protected Point2D getPreferredLocation()
	{
		return new Point2D.Float( 0.65f, 0.05f );
	}

// ---------------- RealtimeConsumer interface ---------------- 

	/**
	 *  Requests 15 fps notification (no data block requests).
	 *  This is used to update the timeline position label during transport
	 *  playback.
	 */
	public RealtimeConsumerRequest createRequest( RealtimeContext context )
	{
		RealtimeConsumerRequest request = new RealtimeConsumerRequest( this, context );
		// 15 fps is enough for text update
		request.notifyTickStep  = RealtimeConsumerRequest.approximateStep( context, 15 );
		request.notifyTicks		= true;
		request.notifyOffhand	= true;
		return request;
	}
	
	public void realtimeTick( RealtimeContext context, RealtimeProducer.Source source, long pos )
	{
//		lbTime.wahrnehmungsApparillo( (int) ((double) (pos * 1000) / context.getSourceRate() + 0.5 ));
		lbTime.setTime( new Double( (double) pos / context.getSourceRate() ));
	}

	public void offhandTick( RealtimeContext context, RealtimeProducer.Source source, long pos )
	{
//		lbTime.wahrnehmungsApparillo( (int) ((double) (pos * 1000) / context.getSourceRate() + 0.5 ));
		lbTime.setTime( new Double( (double) pos / context.getSourceRate() ));
		if( !isCueing ) cuePos = pos;
	}

	public void realtimeBlock( RealtimeContext context, RealtimeProducer.Source source, boolean even ) {}

// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
	{
		if( ggLoop.isSelected() ) {
			actionLoop.updateLoop();
		}
    }

	public void timelineChanged( TimelineEvent e )
	{
		try {
			doc.bird.waitShared( Session.DOOR_TIME );
			rate = doc.timeline.getRate();
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
	}
	
    public void timelineScrolled( TimelineEvent e ) {}

	public void timelinePositioned( TimelineEvent e ) {}

// ---------------- TransportListener interface ---------------- 

	public void transportStop( long pos )
	{
		ggPlay.setSelected( false );
		if( isCueing ) {
			cuePos = pos;
			cueTimer.restart();
		}
	}
	
	public void transportPlay( long pos )
	{
		ggPlay.setSelected( true );
		if( cueTimer.isRunning() ) cueTimer.stop();
	}
	
	public void transportQuit()
	{
		if( cueTimer.isRunning() ) cueTimer.stop();
	}
	
	public void transportPosition( long pos ) {}

// ---------------- actions ---------------- 

	private class actionGoToTimeClass
	extends KeyedAction
	{
		private int defaultValue = 0;   // millisecs
	
		private actionGoToTimeClass( KeyStroke stroke )
		{
			super( stroke );
		}
		
		protected void validActionPerformed( ActionEvent e )
		{
			String			result;
			int				min;
			Object[]		msgArgs		= new Object[2];
			Object[]		resultArgs;

			min			= defaultValue / 60000;
			msgArgs[0]  = new Integer( min );
			msgArgs[1]  = new Double( (double) (defaultValue % 60000) / 1000 );

			result  = JOptionPane.showInputDialog( null, AbstractApplication.getApplication().getResourceString( "inputDlgGoToTime" ),
												   msgFormat.format( msgArgs ));

			if( result == null || !doc.bird.attemptExclusive( Session.DOOR_TIME, 1000 )) return;
			try {
				resultArgs		= msgFormat.parse( result );
				if( !(resultArgs[0] instanceof Number) || !(resultArgs[1] instanceof Number) ) return;
				defaultValue	= (int) ((((Number) resultArgs[1]).doubleValue() + 60 *
										  ((Number) resultArgs[0]).intValue()) * 1000 + 0.5);  // millisecs
				doc.timeline.setPosition( this, Math.max( 0, Math.min( doc.timeline.getLength(),
										  (long) ((double) defaultValue / 1000 * doc.timeline.getRate() + 0.5 ))));
			}
			catch( ParseException e1 ) {
				System.err.println( e1.getLocalizedMessage() );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
        }
	} // class actionGoToTimeClass

	private class actionPlayClass
	extends AbstractAction
	{
		private actionPlayClass()
		{
			super();
		}
		
		public void actionPerformed( ActionEvent e )
		{
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 200 )) return;
			try {
				if( doc.timeline.getPosition() == doc.timeline.getLength() ) {
					doc.timeline.setPosition( this, 0 );
				}
				transport.goPlay();
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
        }
	} // class actionPlayClass

	private class actionStopClass
	extends AbstractAction
	{
		private actionStopClass()
		{
			super();
		}
		
		public void actionPerformed( ActionEvent e )
		{
			transport.goStop();
        }
	} // class actionStopClass
	
//	private class actionTogglePlayStopClass
//	extends KeyedAction
//	{
//		private actionTogglePlayStopClass( KeyStroke stroke )
//		{
//			super( stroke );
//		}
//
//		protected void validActionPerformed( ActionEvent e )
//		{
//			if( transport.isRunning() ) {
//				ggStop.doClick();
//			} else {
//				ggPlay.doClick();
//			}
//		}
//	} // class actionTogglePlayStop

	private class actionLoopClass
	extends AbstractAction
	{	
		private actionLoopClass()
		{
			super();
		}

		public void actionPerformed( ActionEvent e )
		{
			if( ((AbstractButton) e.getSource()).isSelected() ) {
				if( doc.bird.attemptShared( Session.DOOR_TIME, 200 )) {
					try {
						updateLoop();
					}
					finally {
						doc.bird.releaseShared( Session.DOOR_TIME );
					}
				} else {
					((AbstractButton) e.getSource()).setSelected( false );
				}
			} else {
				transport.setLoop( null );
			}
        }
		
		private void updateLoop()
		{
			Span span;

			try {
				doc.bird.waitShared( Session.DOOR_TIME );
				span = doc.timeline.getSelectionSpan();
				transport.setLoop( span.isEmpty() ? null : span );
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIME );
			}
		}
	} // class actionLoopClass
	
	private class CueListener
	implements ChangeListener
	{
		private final ButtonModel	bm;
		private boolean				transportWasRunning	= false;
		private final int			step;
	
		// step = in millisecs, > 0 = fwd, < = rwd
		private CueListener( AbstractButton b, int step )
		{
			bm			= b.getModel();
			this.step	= step;
		}

		public void stateChanged( ChangeEvent e )
		{
			if( isCueing && !bm.isArmed() ) {
				isCueing	= false;
				cueTimer.stop();
				if( transportWasRunning ) {
					transport.goPlay();
				}
			} else if( !isCueing && bm.isArmed() ) {
				transportWasRunning = transport.isRunning();
				cueStep		= step;
				isCueing	= true;
				if( transportWasRunning ) {
					transport.goStop();
				} else {
					cueTimer.restart();
				}
			}
		}
	}
}