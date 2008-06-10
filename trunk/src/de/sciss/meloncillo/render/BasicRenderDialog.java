/*
 *  BasicRenderDialog.java
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
 *		14-Jul-04	created
 *		23-Jul-04   inherits to render specific aspects of
 *					the AbstractRenderDialog which was sucked off
 *					by AbstractPlugInFrame
 *		01-Aug-04   bugfix : didn't register dynamic listener
 *		02-Sep-04	commented
 */

package de.sciss.meloncillo.render;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.io.*;
import de.sciss.meloncillo.math.*;
import de.sciss.meloncillo.plugin.*;
import de.sciss.meloncillo.receiver.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.timeline.*;
import de.sciss.meloncillo.transmitter.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.gui.*;
import de.sciss.io.*;

/**
 *  A still abstract RenderDialog but
 *  with more functionality than <code>AbstractPlugInFrame</code>
 *  and suitable as superclass of a dialog
 *  for Bounce-to-Disk and Filter-Trajectories.
 *  Implements the <code>run</code> method so
 *	subclasses only need to implement some brief
 *	bodies for <code>invokeProducerRender</code> etc.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public abstract class BasicRenderDialog
extends AbstractPlugInFrame
implements  RenderHost, RunnableProcessing,
			TimelineListener, DynamicListening
{
	private RenderPlugIn			plugIn	= null;
	private ProcessingThread		pt;
	private RenderContext			context = null;
	private final ProgressComponent	pc;
	private	JButton					ggClose, ggRender;
	private	Action					actionClose, actionRender, actionCancel;

	private boolean renderingRunning = false;

	private final SessionCollectionListener receiversListener			= new SessionCollectionListener( false );
	private final SessionCollectionListener selectedReceiversListener	= new SessionCollectionListener( true );
	private final SessionCollectionListener transmittersListener		= receiversListener;
	private final SessionCollectionListener selectedTransmittersListener= selectedReceiversListener;

	/**
	 *	Constructs a new render dialog
	 *	using the given title and GUI flags
	 */
	protected BasicRenderDialog( Main root, Session doc, String title, int flags )
	{
		super( root, doc, title, flags );

		pc	= (ProgressComponent) root.getComponent( Main.COMP_MAIN );

        new DynamicAncestorAdapter( this ).addTo( getRootPane() );
	}

	/**
	 *	Subclasses must provide
	 *	a render context when this
	 *	method is called.
	 *
	 *	@return	a render context for the current setup / selection
	 */
	protected abstract RenderContext createRenderContext();

	/**
	 *	Subclasses must provide
	 *	a method that checks the passed context
	 *	for validity. Usually returning <code>false</code>
	 *	will result in an additional call of <code>createRenderContext</code>.
	 *
	 *	@param	context	a context to check for validity
	 *
	 *	@return	<code>true</code> if the provided context is still valid
	 */
	protected abstract boolean isRenderContextValid( RenderContext context );

	/**
	 *	Default implementation creates a new
	 *	instance of the plug-in class and initializes it.
	 */
	protected void switchPlugIn( String className )
	{
		boolean							success	= false;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();

		try {
			plugIn = null;
			if( className != null ) {
				try {
					plugIn	= (RenderPlugIn) Class.forName( className ).newInstance();
					plugIn.init( root, doc );
				}
				catch( InstantiationException e1 ) {
					GUIUtil.displayError( this, e1, app.getResourceString( "errInitPlugIn" ));
				}
				catch( IllegalAccessException e2 ) {
					GUIUtil.displayError( this, e2, app.getResourceString( "errInitPlugIn" ));
				}
				catch( ClassNotFoundException e3 ) {
					GUIUtil.displayError( this, e3, app.getResourceString( "errInitPlugIn" ));
				}
			}
			success = reContext();
		}
		finally {
			ggRender.setEnabled( success );
		}
	}
	
	/**
	 *	Default implementation creates a panel
	 *	with close and render buttons. A key shortcut
	 *	(escape) is attached to the close button.
	 */
	protected JComponent createBottomPanel( int flags )
	{
		JPanel							bottomPanel;
		final de.sciss.app.Application	app = AbstractApplication.getApplication();

		actionClose		= new actionCloseClass(  app.getResourceString( "buttonClose" ));
		actionCancel	= new actionCancelClass( app.getResourceString( "buttonCancel" ));
		actionRender	= new actionRenderClass( app.getResourceString( "buttonRender" ));
		
		bottomPanel		= new JPanel();
		bottomPanel.setLayout( new BorderLayout() );
		ggClose			= new JButton( actionClose );
		GUIUtil.createKeyAction( ggClose, KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ));
		ggRender		= new JButton( actionRender );
		ggRender.setEnabled( false );
		bottomPanel.add( ggClose, BorderLayout.WEST );
		bottomPanel.add( ggRender, BorderLayout.EAST );
		
		return bottomPanel;
	}

	private void progressStop()
	{
		renderingRunning = false;
		pt.sync();
	}
	
	// to be called in event thread
	private void progressStart()
	{
		if( context == null || plugIn == null || renderingRunning ) return;
	
		context.setOption( RenderContext.KEY_PLUGIN, plugIn );
		renderingRunning = true;
		pt  = new ProcessingThread( this, pc, root, doc, getTitle(), context, Session.DOOR_ALL );

		ggClose.setEnabled( false );
		ggRender.setAction( actionCancel );
		ggRender.requestFocus();
		hibernation( true );
	}

	/**
	 *	Default implementation calls <code>isRenderContextValid</code>.
	 *	If that returns <code>false</code>, a new context is
	 *	created and GUI is updated.
	 */
	protected void checkReContext()
	{
		if( context != null && !isRenderContextValid( context )) {
			boolean success = false;
			try {
				success = reContext();
			}
			finally {
				ggRender.setEnabled( success );
			}
		}
	}
	
	private boolean reContext()
	{
		JComponent	view;

		context = createRenderContext();
		if( context != null && plugIn != null ) {
			view	= plugIn.getSettingsView( context );
			GUIUtil.setDeepFont( view, fnt );
			ggSettingsPane.setViewportView( view );
			pack();
			return true;
		} else {
			ggSettingsPane.setViewportView( null );
			return false;
		}
	}

// ---------------- RenderHost interface ---------------- 

	public void	showMessage( int type, String text )
	{
		pc.showMessage( type, text );
	}
	
	public void setProgression( float p )
	{
		pc.setProgression( p );
	}
	
	public void setException( Exception e )
	{
		pt.setException( e );
	}

	public boolean isRunning()
	{
		return renderingRunning;
	}

// ---------------- RunnableProcessing interface ---------------- 

/**
 *  RunnableProcessing interface core of data processing.
 *	The default implementation will handle all stream data
 *	requests. Subclasses don't usually need to overwrite this
 *	method and instead implement the methods
 *	<code>invokeProducerBegin</code>, <code>invokeProducerRender</code>,
 *	<code>invokeProducerCancel</code> and <code>invokeProducerFinish</code>.
 *	<p>
 *  If resampling is active, here's the scheme of the
 *  buffer handling:<br>
 *  <PRE>
 *		structure of the inTrnsFrames buffer:
 *
 *		(initially empty)
 *
 *		+--------+----------------------+--------+--------+
 *		| fltLenI|          >= 0        | fltLenI| fltLenI|
 *		+--------+----------------------+--------+--------+
 *										|<--overlapLen--->|
 *										|=overlapOff
 *				 |<-------trnsInside------------>|
 *
 *		first buffer read (mte.read()):
 *
 *				 %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
 *		+--------+----------------------+--------+--------+
 *		| fltLen |                      | fltLen | fltLen |
 *		+--------+----------------------+--------+--------+
 *
 *		// begin loop //
 *
 *		resampling:
 *
 *				 %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
 *		+--------+----------------------+--------+--------+
 *		| fltLen |                      | fltLen | fltLen |
 *		+--------+----------------------+--------+--------+
 *
 *		overlap handling:
 *
 *					 +----------------  %%%%%%%%%%%%%%%%%% (source)
 *					 V
 *		%%%%%%%%%%%%%%%%%% (destination)
 *		+--------+----------------------+--------+--------+
 *		| fltLen |                      | fltLen | fltLen |
 *		+--------+----------------------+--------+--------+
 *
 *		sucessive reads:
 *
 *		%%%%%%%%%%%%%%%%%% (old overlap)
 *						  %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% (new read)
 *		+--------+----------------------+--------+--------+
 *		| fltLen |                      | fltLen | fltLen |
 *		+--------+----------------------+--------+--------+
 *
 *		// end loop //
 *  </PRE>
 *
 *	@see	#invokeProducerBegin( ProcessingThread, RenderContext, RenderSource, RenderPlugIn )
 *	@see	#invokeProducerCancel( ProcessingThread, RenderContext, RenderSource, RenderPlugIn )
 *	@see	#invokeProducerRender( ProcessingThread, RenderContext, RenderSource, RenderPlugIn )
 *	@see	#invokeProducerFinish( ProcessingThread, RenderContext, RenderSource, RenderPlugIn )
 */
	public boolean run( ProcessingThread pt, Object argument )
	{
		final RenderContext				context		= (RenderContext) argument;
		final RenderPlugIn				plugIn		= (RenderPlugIn) context.getOption( RenderContext.KEY_PLUGIN );
		RenderSource					source;
		final de.sciss.app.Application	app			= AbstractApplication.getApplication();

		float[][]						inTrnsFrames, outTrnsFrames;
		int								minBlockSize, maxBlockSize, prefBlockSize;
		int								i, numTrns, numRcv, trnsIdx, rcvIdx, readLen, writeLen;
		Transmitter						trns;
		MultirateTrackEditor			mte;
		boolean[]						trnsRequest;
		Object							val;
		long							readOffset, remainingRead, remainingWrite;
		Set								newOptions;
		String							className;
		boolean							success		= false;	// pessimist bitch™
		
		// --- resampling related ---
		int						inOff, inTrnsLen, outTrnsLen;
		int						fltLenI		= 0;
		int						overlapLen  = 0;
		int						overlapOff  = 0;
		int						trnsInside  = 0;
		Resampling				rsmp		= null;
		double					rsmpFactor  = 1.0;
		double					inPhase		= 0.0;
		double					newInPhase  = 0.0;
		double					fltLen		= 0.0;
		float[][][]				trnsOverlaps= null;

		// --- init ---

		readOffset		= context.getTimeSpan().getStart();
		numRcv			= context.getReceivers().size();
		numTrns			= context.getTransmitters().size();
		source			= new RenderSource( numTrns, numRcv );

		try {
			if( !invokeProducerBegin( pt, context, source, plugIn )) return false;
			remainingRead		= context.getTimeSpan().getLength();
			newOptions			= context.getModifiedOptions();
			if( newOptions.contains( RenderContext.KEY_MINBLOCKSIZE )) {
				val				= context.getOption( RenderContext.KEY_MINBLOCKSIZE );
				minBlockSize	= ((Integer) val).intValue();
			} else {
				minBlockSize	= 1;
			}
			if( newOptions.contains( RenderContext.KEY_MAXBLOCKSIZE )) {
				val				= context.getOption( RenderContext.KEY_MAXBLOCKSIZE );
				maxBlockSize	= ((Integer) val).intValue();
			} else {
				maxBlockSize	= 0x7FFFFF;
			}
			if( newOptions.contains( RenderContext.KEY_PREFBLOCKSIZE )) {
				val				= context.getOption( RenderContext.KEY_PREFBLOCKSIZE );
				prefBlockSize	= ((Integer) val).intValue();
			} else {
				prefBlockSize   = Math.max( minBlockSize, Math.min( maxBlockSize, 1024 ));
			}
			assert minBlockSize <= maxBlockSize : "minmaxblocksize";
			
			if( newOptions.contains( RenderContext.KEY_TARGETRATE )) {
			// ---- prepare resampling ----
				val				= context.getOption( RenderContext.KEY_TARGETRATE );
				rsmpFactor		= ((Double) val).doubleValue() / (double) context.getSourceRate();
				className		= classPrefs.get( KEY_RESAMPLING, null );
				if( className == null ) {
					className   = NearestNeighbour.class.getName(); // RSMP_ITEMS[ 0 ].getKey();
					showMessage( JOptionPane.WARNING_MESSAGE, app.getResourceString( "errResamplingClass" ) +
									" : " + val.toString() );
				}
				try {
					rsmp		= (Resampling) Class.forName( className ).newInstance();
				}
				catch( InstantiationException e1 ) {
					pt.setException( e1 );
					return false;
				}
				catch( IllegalAccessException e2 ) {
					pt.setException( e2 );
					return false;
				}
				catch( ClassNotFoundException e3 ) {
					pt.setException( e3 );
					return false;
				}
				finally {
					if( rsmp == null ) {
						showMessage( JOptionPane.ERROR_MESSAGE,
									 app.getResourceString( "errResamplingClass" ) + " : " + className );
					}
				}
				
				fltLen			= rsmp.getWingSize( rsmpFactor );
				fltLenI			= (int) fltLen + 1;
				inOff			= fltLenI;
				overlapLen		= fltLenI << 1;
				if( rsmpFactor > 1.0 ) {
					outTrnsLen  = prefBlockSize;
					i			= (int) (outTrnsLen / rsmpFactor);
					inTrnsLen   = i + overlapLen;
				} else {
					inTrnsLen   = Math.max( prefBlockSize, fltLenI + overlapLen );
					i			= inTrnsLen - overlapLen;
					outTrnsLen  = (int) (i * rsmpFactor) + 1;
				}
				overlapOff		= inTrnsLen - overlapLen;
				trnsInside		= inTrnsLen - fltLenI - fltLenI;
				trnsOverlaps	= new float[ numTrns ][2][ overlapLen ];
				inTrnsFrames	= new float[2][ inTrnsLen ];
				outTrnsFrames   = new float[2][ outTrnsLen ];
				remainingWrite  = (long) (remainingRead * rsmpFactor + 0.5);
//System.err.println( "fltLen "+fltLen+"; inOff "+inOff+"; overlapLen "+overlapLen+"; inTrnsLen "+inTrnsLen+"; outTrnsLen "+outTrnsLen+"; rsmpFactor "+rsmpFactor );
			} else {
				inTrnsLen		= prefBlockSize;
				outTrnsLen		= inTrnsLen;
				inTrnsFrames	= new float[2][ inTrnsLen ];
				outTrnsFrames   = inTrnsFrames;
				inOff			= 0;
				remainingWrite  = remainingRead;
			}

			// --- responding to RenderSource requests ---
			trnsRequest			= new boolean[ numTrns ];   // all false by default
			
			for( trnsIdx = 0; trnsIdx < numTrns; trnsIdx++ ) {
				if( source.trajRequest[ trnsIdx ]) {
					source.trajBlockBuf[ trnsIdx ]  = new float[ 2 ][ outTrnsLen ];
					trnsRequest[ trnsIdx ]			= true;
				}
				for( rcvIdx = 0; rcvIdx < numRcv; rcvIdx++ ) {
					if( source.senseRequest[ trnsIdx ][ rcvIdx ]) {
						source.senseBlockBuf[ trnsIdx ][ rcvIdx ] = new float[ outTrnsLen ];
						trnsRequest[ trnsIdx ]		= true;
					}
				}
			}

			// --- rendering loop ---

			while( isRunning() && remainingWrite > 0 ) {
				readLen				= (int) Math.min( inTrnsLen - inOff, remainingRead );
				source.blockSpan	= new Span( readOffset, readOffset + readLen );
				if( rsmp != null ) {
					inPhase			= newInPhase;
					writeLen		= (int) Math.min( Math.ceil( (trnsInside - inPhase) * rsmpFactor ), remainingWrite );
				} else {
					writeLen		= readLen;
				}
				source.blockBufLen  = writeLen;

				for( trnsIdx = 0; trnsIdx < numTrns; trnsIdx++ ) {
					if( !trnsRequest[ trnsIdx ]) continue;
					
					// --- read transmitter trajectory data ---
					trns		= (Transmitter) context.getTransmitters().get( trnsIdx );
					mte			= trns.getTrackEditor();
					mte.read( source.blockSpan, inTrnsFrames, inOff );
					for( i = inOff + readLen; i < inTrnsLen; i++ ) {
						inTrnsFrames[0][i] = 0.0f;		// zero pad in the end 
						inTrnsFrames[1][i] = 0.0f;		// XXX actually the last sample should be repeated!
					}
					
					// --- resampling ---
					if( rsmp != null ) {
						System.arraycopy( trnsOverlaps[trnsIdx][0], 0, inTrnsFrames[0], 0, inOff );
						System.arraycopy( trnsOverlaps[trnsIdx][1], 0, inTrnsFrames[1], 0, inOff );
						rsmp.resample( inTrnsFrames[0], fltLenI + inPhase, outTrnsFrames[0], 0, writeLen, rsmpFactor );
						rsmp.resample( inTrnsFrames[1], fltLenI + inPhase, outTrnsFrames[1], 0, writeLen, rsmpFactor );
						System.arraycopy( inTrnsFrames[0], overlapOff, trnsOverlaps[trnsIdx][0], 0, overlapLen );
						System.arraycopy( inTrnsFrames[1], overlapOff, trnsOverlaps[trnsIdx][1], 0, overlapLen );
					}
					
					// --- satisfy trajectory requests ---
					if( source.trajRequest[ trnsIdx ]) {
						System.arraycopy( outTrnsFrames[0], 0, source.trajBlockBuf[ trnsIdx ][0], 0, writeLen );
						System.arraycopy( outTrnsFrames[1], 0, source.trajBlockBuf[ trnsIdx ][1], 0, writeLen );
					}
					
					// --- satisfy sensibilities requests ---
					for( rcvIdx = 0; rcvIdx < numRcv; rcvIdx++ ) {
						if( !source.senseRequest[ trnsIdx ][ rcvIdx ]) continue;

						((Receiver) context.getReceivers().get( rcvIdx )).getSensitivities(
							outTrnsFrames, source.senseBlockBuf[ trnsIdx ][ rcvIdx ],
							0, writeLen, 1 );
					} // for( rcvIdx = 0; rcvIdx < numRcv; rcvIdx++ )
				} // for( trnsIdx = 0; trnsIdx < numTrns; trnsIdx++ )

				if( rsmp != null ) {
					inOff		= overlapLen;
					newInPhase  = (inPhase + writeLen / rsmpFactor) - trnsInside;
//					newInPhase  = (inPhase + writeLen / rsmpFactor) % 1.0;
				}
				remainingRead -= readLen;

				// --- handle thread ---
				if( !isRunning() ) {
					return false;
				}

				// --- producer rendering ---
				if( !invokeProducerRender( pt, context, source, plugIn )) return false;

				remainingWrite -= writeLen;
				readOffset     += readLen;
			} // while( isRunning() && remainingWrite > 0 )

			// --- finishing ---
			if( !isRunning() ) {
				invokeProducerCancel( pt, context, source, plugIn );
				success = true;
			} else {
				success = invokeProducerFinish( pt, context, source, plugIn );
			}
		}
		catch( IOException e1 ) {
			pt.setException( e1 );
		}
		finally {
			if( !success ) {	// on failure cancel rendering and undo edits
				try {
					invokeProducerCancel( pt, context, source, plugIn );
				}
				catch( IOException e2 ) {
					pt.setException( e2 );
				}
			}
		}

		return success;
	}
	
	/**
	 *	Re-enables the frame components.
	 */
	public void finished( ProcessingThread context, Object argument, boolean success )
	{
		renderingRunning = false;
		ggClose.setEnabled( true );
		ggRender.setAction( actionRender );
		hibernation( false );
	}

	/**
	 *  Additional inits. This should call
	 *  <code>plugIn.producerBegin()</code>. Returning
	 *  false signals error and aborts processing.
	 *
	 *	@param	pt			the thread used during processing
	 *	@param	context		the render context. initial options
	 *						should be set here.
	 *	@param	source		the stream data structure. requests should
	 *						be made here.
	 *	@param	plugIn		the currently active plug-in
	 *	@return	<code>true</code> for success, <code>false</code> upon
	 *			failure which will cause the rendering to stop
	 *
	 *	@throws	IOException	if a file or net error occurs
	 *
	 *	@see	RenderPlugIn#producerBegin( RenderContext, RenderSource )
	 */
	protected abstract boolean invokeProducerBegin( ProcessingThread pt, RenderContext context,
													RenderSource source, RenderPlugIn plugIn )
	throws IOException;

	/**
	 *  This gets called when the processing is
	 *  cancelled. It should call <code>plugIn.producerCancel()</code>.
	 *
	 *	@param	pt			the thread used during processing
	 *	@param	context		the render context
	 *	@param	source		the stream data structure
	 *	@param	plugIn		the currently active plug-in
	 *	@return	<code>true</code> for success, <code>false</code> upon
	 *			failure
	 *
	 *	@throws	IOException	if a file or net error occurs
	 *
	 *	@see	RenderPlugIn#producerCancel( RenderContext, RenderSource )
	 */
	protected abstract void invokeProducerCancel( ProcessingThread pt, RenderContext context,
												  RenderSource source, RenderPlugIn plugIn )
	throws IOException;

	/**
	 *  Render a block of data. This should call
	 *  <code>plugIn.producerRender()</code>. Returning
	 *  false signals error and aborts processing.
	 *
	 *	@param	pt			the thread used during processing
	 *	@param	context		the render context
	 *	@param	source		the stream data structure
	 *	@param	plugIn		the currently active plug-in
	 *	@return	<code>true</code> for success, <code>false</code> upon
	 *			failure which will cause the rendering to stop
	 *
	 *	@throws	IOException	if a file or net error occurs
	 *
	 *	@see	RenderPlugIn#producerRender( RenderContext, RenderSource )
	 */
	protected abstract boolean invokeProducerRender( ProcessingThread pt, RenderContext context,
													 RenderSource source, RenderPlugIn plugIn )
	throws IOException;

	/**
	 *  Additional render stuff. This should call
	 *  <code>plugIn.producerFinish()</code>. Returning
	 *  false signals error and aborts processing.
	 *
	 *	@param	pt			the thread used during processing
	 *	@param	context		the render context
	 *	@param	source		the stream data structure
	 *	@param	plugIn		the currently active plug-in
	 *	@return	<code>true</code> for success, <code>false</code> upon
	 *			failure which will cause the rendering to stop
	 *
	 *	@throws	IOException	if a file or net error occurs
	 *
	 *	@see	RenderPlugIn#producerFinish( RenderContext, RenderSource )
	 */
	protected abstract boolean invokeProducerFinish( ProcessingThread pt, RenderContext context,
												     RenderSource source, RenderPlugIn plugIn )
	throws IOException;

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		doc.timeline.addTimelineListener( this );
		doc.transmitters.addListener( transmittersListener );
		doc.selectedTransmitters.addListener( selectedTransmittersListener );
		doc.receivers.addListener( receiversListener );
		doc.selectedReceivers.addListener( selectedReceiversListener );
		checkReContext();
    }

    public void stopListening()
    {
		doc.timeline.removeTimelineListener( this );
		doc.transmitters.removeListener( transmittersListener );
		doc.selectedTransmitters.removeListener( selectedTransmittersListener );
		doc.receivers.removeListener( receiversListener );
		doc.selectedReceivers.removeListener( selectedReceiversListener );
    }
    
// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		checkReContext();
     }
    
	public void timelineChanged( TimelineEvent e )
    {
		checkReContext();
    }

	public void timelinePositioned( TimelineEvent e ) {}
    public void timelineScrolled( TimelineEvent e ) {}

// ---------------- Action objects ---------------- 

	private class actionCloseClass
	extends AbstractAction
	{
		private actionCloseClass( String text )
		{
			super( text );
		}

		public void actionPerformed( ActionEvent e )
		{
			hide();		// setVisbile( false ) doesn't fire componentHidden event!
			dispose();
		}
	}

	private class actionRenderClass extends AbstractAction
	{
		private actionRenderClass( String text )
		{
			super( text );
		}

		public void actionPerformed( ActionEvent e )
		{
			progressStart();
		}
	}

	private class actionCancelClass
	extends AbstractAction
	{
		private actionCancelClass( String text )
		{
			super( text );
		}

		public void actionPerformed( ActionEvent e )
		{
			progressStop();
		}
	}
	
	private class SessionCollectionListener
	implements SessionCollection.Listener
	{
		private final boolean watchSelection;
		
		private SessionCollectionListener( boolean watchSelection )
		{
			this.watchSelection = watchSelection;
		}
		
		public void sessionCollectionChanged( SessionCollection.Event e )
		{
			handleEvent( e );
		}

		public void sessionObjectChanged( SessionCollection.Event e )
		{
			handleEvent( e );
		}

		public void sessionObjectMapChanged( SessionCollection.Event e )
		{
			handleEvent( e );
		}

		private void handleEvent( SessionCollection.Event e )
		{
			boolean isSelectionOnly = classPrefs.getBoolean( KEY_SELECTIONONLY, false );

			if( isSelectionOnly == watchSelection ) {
				checkReContext();
			}
		}
	}
}