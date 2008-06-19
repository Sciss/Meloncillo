/*
 *  SimpleTransmitterEditor.java
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
 *		14-Jun-04   supports cursor info
 *		25-Jul-04   frameBuf mit leerem Array initialisiert, bugfix in showCursorInfo
 *		01-Aug-04   loadFrames moved from init to startListening
 *		02-Sep-04	commented
 */

package de.sciss.meloncillo.transmitter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.gui.TimeFormat;
import de.sciss.gui.VectorSpace;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.edit.SyncCompoundSessionObjEdit;
import de.sciss.meloncillo.gui.ObserverPalette;
import de.sciss.meloncillo.gui.ToolActionEvent;
import de.sciss.meloncillo.gui.ToolActionListener;
import de.sciss.meloncillo.gui.VectorDisplay;
import de.sciss.meloncillo.gui.VectorEditor;
import de.sciss.meloncillo.gui.VirtualSurface;
import de.sciss.meloncillo.io.BlendContext;
import de.sciss.meloncillo.io.BlendSpan;
import de.sciss.meloncillo.io.MultirateTrackEditor;
import de.sciss.meloncillo.io.SubsampleInfo;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionCollection;
import de.sciss.meloncillo.timeline.TimelineEvent;
import de.sciss.meloncillo.timeline.TimelineListener;

/**
 *  An editor suitable for <code>SimpleTransmitter</code>s.
 *  Name edits are left to the
 *  <code>ObserverPalette</code>. This simply
 *  provides two <code>VectorEditor</code>s for
 *  the x and y trajectory coordinates respectively.
 *  Copy and paste functionality is provided by the TimelineFrame
 *	but could go here some day.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		SimpleTransmitter
 *
 *  @todo		disposal not explicitely handled.
 *				MouseMotionListener is invoked too early sometimes, i.e. before frameBuf has
 *				been updated.
 *
 *	@todo		BUG: when drawing directly into the vector editors,
 *				writing out the new trajectories will shift the contents
 *				a bit to the left each time
 */
public class SimpleTransmitterEditor
extends AbstractTransmitterEditor
implements ToolActionListener, VectorDisplay.Listener, TimelineListener, DynamicListening
{
	private static Vector				collTransmitterTypes;

	private final VectorEditor			xEditor;
	private final VectorEditor			yEditor;
	
	private final float[][]				frameBuf		= new float[2][0];
	private int							rate;
	private SubsampleInfo				info			= null;

	private ObserverPalette				observer		= null;
	private final MouseMotionListener	cursorListener;
	private final String[]				cursorInfo		= new String[3];
	private final Object[]				msgArgs			= new Object[2];
	private final TimeFormat			msgCursorTime;
	private final MessageFormat			msgCursorX;
	private final MessageFormat			msgCursorY;
	private final SessionCollection.Listener	transmittersListener;

	static {
		collTransmitterTypes = new Vector( 1 );
		collTransmitterTypes.addElement( SimpleTransmitter.class );
	}

	/**
	 *	Constructs a new transmitter editor with empty display.
	 *	Attach the transmitter using <code>init</code>.
	 */
	public SimpleTransmitterEditor()
	{
		super();
		
		final Container					c		= this;
		JPanel							padPanel1, padPanel2;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
		
		msgCursorTime	= new TimeFormat( 0, app.getResourceString( "simpleTrnsEditTimePrefix" ), null, 3, Locale.US );
		msgCursorX		= new MessageFormat( app.getResourceString( "simpleTrnsEditXMsg" ), Locale.US );   // XXX
		msgCursorY		= new MessageFormat( app.getResourceString( "simpleTrnsEditYMsg" ), Locale.US );   // XXX

//		setOpaque( false );

		xEditor = new VectorEditor();
		xEditor.setSpace( null, VectorSpace.createLinSpace( 0.0, 1.0, 0.0, 1.0,
								app.getResourceString( "labelX" ), null, null, null ));
		xEditor.setFillArea( false );
		xEditor.addListener( this );    // we don't use DynamicListening because of being only rendered in the JTable
		padPanel1 = new JPanel();
		padPanel1.setLayout( new BorderLayout() );
		padPanel1.add( BorderLayout.CENTER, xEditor );
		padPanel1.setBorder( BorderFactory.createMatteBorder( 2, 0, 2, 0, Color.white ));   // top left bottom right
//		padPanel1.setOpaque( false );

		yEditor = new VectorEditor();
		yEditor.setSpace( null, VectorSpace.createLinSpace( 0.0, 1.0, 0.0, 1.0,
								app.getResourceString( "labelY" ), null, null, null ));
		yEditor.setFillArea( false );
		yEditor.addListener( this );    // we don't use DynamicListening because of being only rendered in the JTable
		padPanel2 = new JPanel();
		padPanel2.setLayout( new BorderLayout() );
		padPanel2.add( BorderLayout.CENTER, yEditor );
		padPanel2.setBorder( BorderFactory.createMatteBorder( 2, 0, 4, 0, Color.white ));
//		padPanel2.setOpaque( false );

		c.setLayout( new BoxLayout( c, BoxLayout.Y_AXIS ));
		c.add( padPanel1 );
		c.add( padPanel2 );

		// --- Listener ---
        new DynamicAncestorAdapter( this ).addTo( this );
		this.addComponentListener( new ComponentAdapter() {
			public void componentResized( ComponentEvent e )
			{
				loadFrames( true );
			}
		});

		cursorListener = new MouseMotionAdapter() {
			public void mouseMoved( MouseEvent e )
			{
				showCursorInfo( ((VirtualSurface) e.getSource()).screenToVirtual( e.getPoint() ));
			}

			public void mouseDragged( MouseEvent e )
			{
				showCursorInfo( ((VirtualSurface) e.getSource()).screenToVirtual( e.getPoint() ));
			}
		};
		
		transmittersListener = new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e ) {}	// XXX could dispose
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.collectionContains( trns ) && e.getModificationType() == Transmitter.OWNER_TRAJ ) {
					loadFrames( false );
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		};
 	}

	/**
	 *  @synchronization	Caller should have sync on timeline, tc and mte?
	 */
	public void init( Main root, Session doc, Transmitter trns )
	{
		super.init( root, doc, trns );
		observer = (ObserverPalette) root.getComponent( Main.COMP_OBSERVER );
	}

	public java.util.List getHandledTransmitters()
	{
		return collTransmitterTypes;
	}

	// Sync: syncs to tl / tc / mte
    private void loadFrames( boolean justBecauseOfResize )
    {
        Span					span;
		MultirateTrackEditor	mte		= trns.getTrackEditor();
        
        if( trns != null ) {
			if( !doc.bird.attemptShared( Session.DOOR_TIMETRNSMTE, 200 )) return;
			try {
                span = doc.timeline.getVisibleSpan();
				// wir fordern eine subsample version des zeitausschnitts
				// an, die mindestens 3/2 frames der aktuellen darstellungsbreite
				// enthaelt. dies ist ein guter kompromiss zwischen
				// darstellungsgenauigkeit und -geschwindigkeit
				rate = doc.timeline.getRate();
				info = mte.getBestSubsample( span, getWidth() * 3 / 2 );
//	info = mte.getBestSubsample( span, (getWidth() * 3 / 2) / (2 * doc.transmitterCollection.indexOf( trns ) + 1) );
				if( info.sublength != frameBuf[0].length ) {
					frameBuf[0] = new float[(int) info.sublength];
					frameBuf[1] = new float[(int) info.sublength];
				} else {
					if( justBecauseOfResize ) return;   // info.sublength didn't change
				}
				try {
// System.err.println( "read subsample idx  "+info.idx+" , len = "+info.span.getLength()+"; view = "+viewWidth );
					mte.read( info, frameBuf, 0 );
				}
				catch( IOException e1 ) {
					System.err.println( e1.getLocalizedMessage() );
					info = null;
				}
				xEditor.setVector( null, frameBuf[0] );
				yEditor.setVector( null, frameBuf[1] );
				
            }
			finally {
				doc.bird.releaseShared( Session.DOOR_TIMETRNSMTE );
			}
        }
    }

	private void showCursorInfo( Point2D pt )
	{
		int x;
	
		if( observer != null && observer.isVisible() && info != null && info.sublength > 0 ) {
			x				= Math.max( 0, Math.min( (int) info.sublength - 1, (int) (pt.getX() * info.sublength + 0.5) ));
			msgArgs[0]		= new Double( frameBuf[0][x] );
			msgArgs[1]		= new Double( frameBuf[1][x] );
			cursorInfo[0]   = msgCursorX.format( msgArgs );
			cursorInfo[1]   = msgCursorY.format( msgArgs );
			cursorInfo[2]   = msgCursorTime.formatTime( new Double( (pt.getX() * info.span.getLength() +
																	info.span.getStart()) / rate ));
//System.err.println( "cursorInfo[0] = "+cursorInfo[0]+"; cursorInfo[1] = "+cursorInfo[1] );
			observer.showCursorInfo( cursorInfo );
		}
	}

// ---------------- VectorDisplay.Listener interface ---------------- 

	/**
	 *	Called after the user was drawing
	 *	inside the trajectory panels.
	 *	This will linearily interpolate the
	 *	data to full rate and update the
	 *	trajectory files
	 *
	 *	@synchronization	attemptShared on DOOR_TIME, attemptExclusive on DOOR_TRNSMTE
	 */
	public void vectorChanged( VectorDisplay.Event e )
	{
// System.out.println( "vector changed" );
		MultirateTrackEditor			mte;
		Span							changedSpan = (Span) e.getActionObject();
		Span							writeSpan;
		int								i, j, k, factor, ch, start, stop, maxSource, len, interpLen;
		BlendSpan						bs;
		float[][]						interpBuf;
		float[]							chBuf;
		float							f1, f2, interpWeight;
		SyncCompoundSessionObjEdit		edit;
		Vector							collTrns;
		BlendContext					bc			= root.getBlending();

		if( trns == null || info == null ) return;

		if( !doc.bird.attemptShared( Session.DOOR_TIME, 200 )) return;
		try {
			if( !doc.bird.attemptExclusive( Session.DOOR_TRNSMTE, 200 )) return;
			collTrns= new Vector( 1 );
			collTrns.add( trns );
			edit	= new SyncCompoundSessionObjEdit( this, doc, collTrns, Transmitter.OWNER_TRAJ,
													      null, null, Session.DOOR_TIMETRNSMTE );
			try {
				mte		= trns.getTrackEditor();
				factor	= info.getDecimationFactor();
				if( factor == 1 ) {   // fullrate buffer can be written directly
					writeSpan   = new Span( changedSpan.getStart() + info.span.getStart(),
											changedSpan.getStop()  + info.span.getStart() );
					bs			= mte.beginOverwrite( writeSpan, bc, edit );
					mte.continueWrite( bs, frameBuf, (int) changedSpan.getStart(), (int) changedSpan.getLength() );
					mte.finishWrite( bs, edit );
				} else {				// subrate buffers must be upsampled
					start		= (int) changedSpan.getStart();
					stop		= (int) changedSpan.getStop() - 1;
					if( start > stop ) return;
					writeSpan   = new Span( (start * factor) + info.span.getStart(),
											(stop  * factor) + info.span.getStart() + 1 );
					bs			= mte.beginOverwrite( writeSpan, bc, edit );
					maxSource   = 4096 / factor;
					if( maxSource > 0 ) {
						interpBuf   = new float[2][4096];
					} else {
						maxSource   = 1;
						interpBuf   = new float[2][maxSource * factor];
					}
	//System.err.println( "begin overwrite "+writeSpan.getLength()+" maxSource = "+maxSource );
	//long xxx=0;
					
					// we precalculate the linear interpolation weights
					// to improve performance
					interpWeight = 1.0f / (float) factor;
					while( start < stop ) {
						len			= Math.min( stop - start, maxSource );
						interpLen   = len * factor;
						for( ch = 0; ch < 2; ch++ ) {
							for( i = start, k = 0; k < interpLen; ) {
								f1		= frameBuf[ch][i++];
								f2		= (frameBuf[ch][i] - f1) * interpWeight;
								chBuf   = interpBuf[ch];
								for( j = 0; j < factor; j++ ) {
									chBuf[k++] = (float) j * f2 + f1; 
								}
							}
						}
						mte.continueWrite( bs, interpBuf, 0, interpLen );
//xxx+=(len << shift);
//System.out.println( "  write "+interpLen );
						start += len;
					}
					
					mte.continueWrite( bs, frameBuf, stop, 1 );  // last sample not interpolated
//xxx++;
//System.out.println( "  write 1" );
					
					mte.finishWrite( bs, edit );
//System.out.println( "finished "+xxx );
				} // if( info.idx == 0 )
				
				edit.end();		// fires doc.tc.modified()
				doc.getUndoManager().addEdit( edit );
			}
			catch( IOException e1 ) {
				edit.cancel();
				System.err.println( e1.getLocalizedMessage() );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TRNSMTE );
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
    }

	public void vectorSpaceChanged( VectorDisplay.Event e ) {}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		doc.timeline.addTimelineListener( this );
		doc.transmitters.addListener( transmittersListener );
		xEditor.addMouseMotionListener( cursorListener );
		yEditor.addMouseMotionListener( cursorListener );

		loadFrames( false );
//System.err.println( "simpletransmittereditor starts listening" );
    }

    public void stopListening()
    {
		doc.timeline.removeTimelineListener( this );
		doc.transmitters.removeListener( transmittersListener );
		xEditor.removeMouseMotionListener( cursorListener );
		yEditor.removeMouseMotionListener( cursorListener );
//System.err.println( "simpletransmittereditor stops listening" );
    }
 
// ---------------- ToolListener interface ---------------- 

	/**
	 *	Invoked by the <code>TimelineFrame</code>'s
	 *	tool bar. Note that the listener is registered by <code>TimelineFrame</code>
	 *	automatically if it encounteres a <code>TransmitterEditor</code> implementing this interface!
	 *	This method directly calls the <code>toolChanged</code> method of the vector editors.
	 */
	public void toolChanged( ToolActionEvent e )
	{
		xEditor.toolChanged( e );
		yEditor.toolChanged( e );
	}
 
// ---------------- TimelineListener interface ---------------- 

	public void timelineChanged( TimelineEvent e )
    {
        loadFrames( false );
		// repaint();
    }

	public void timelineScrolled( TimelineEvent e )
    {
        loadFrames( false );   // XXX should check if we need update all parts
		// repaint();
    }

	public void timelineSelected( TimelineEvent e ) {}
	public void timelinePositioned( TimelineEvent e ) {}
}