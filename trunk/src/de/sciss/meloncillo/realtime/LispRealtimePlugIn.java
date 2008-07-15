/*
 *  LispRealtimePlugIn.java
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
 *		24-Jul-04   created
 *		22-Aug-04	trajectory request implemented
 *		01-Sep-04	commented
 *		25-Apr-08	fixed to work with current NetUtil version
 */

package de.sciss.meloncillo.realtime;

import java.awt.Point;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.DatagramChannel;
import java.util.List;

import javax.swing.JOptionPane;

import org.jatha.dynatype.LispNumber;
import org.jatha.dynatype.LispValue;

import de.sciss.app.AbstractApplication;
import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;
import de.sciss.io.IOUtil;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.lisp.AdvancedJatha;
import de.sciss.meloncillo.plugin.LispPlugIn;
import de.sciss.meloncillo.plugin.PlugInContext;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.util.PrefsUtil;
import de.sciss.net.OSCListener;
import de.sciss.net.OSCMessage;
import de.sciss.net.OSCReceiver;

/**
 *  A realtime plug-in driven by lisp scripts.
 *	GUI creation is managed by the superclass.
 *	This class deals with the source requests
 *	and sends out streaming data through OSC.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo		when the realtime context is changed
 *				the plug-in needs to be disabled and
 *				re-enabled manually, otherwise the internal
 *				info object is invalid and may cause
 *				arrayindexoutofbounds exceptions! (createRequest!)
 *	@todo		transportQuit should be used to
 *				cleanup
 *	@todo		session object property changes are not
 *				tracked, e.g. soloing / muteing, hence
 *				requiring the plug-in to be deactived and re-activated
 */
public class LispRealtimePlugIn
extends LispPlugIn
implements RealtimePlugIn, RealtimeConsumer, TransportListener, OSCListener,
		   EventManager.Processor
{
	private RealtimeInfo	rt_info		= null;
	private boolean			isPlaying   = false;
	
	private TransportPalette	tp;
	
	private static final int BT_SEND		= 0;
	private static final int BT_STREAM		= 1;
	private static final int BT_CONST		= 2;
	private static final int BT_VAR			= 3;
	private static final int BT_VAR_BUFOFF	= 0;
	
	private static final boolean	VERBOSEOSC	= false;
	
	private final EventManager	elm = new EventManager( this );
	
	private RealtimeProducer		rt_producer = null;
	private RealtimeConsumerRequest	rt_request	= null;
	
	/**
	 *	Empty constructor called 
	 *	through Class.newInstance(). Basic
	 *	initialization is done a separate call to init()
	 */
	public LispRealtimePlugIn()
	{
		super();
//        HelpGlassPane.setHelp( this, "RealtimeLisp" );	// EEE
	}

	public void init( Session doc )
	{
		super.init( doc );
		tp = (TransportPalette) AbstractApplication.getApplication().getComponent( Main.COMP_TRANSPORT );
		rt_producer = new RealtimeProducer( doc, null );
	}
	
	/**
	 *	Returns the preferences key
	 *	used to find out the path name
	 *	of an XML file storing a list
	 *	of registered lisp realtime source codes
	 *
	 *	@return	the prefs key for realtime lisp code
	 *
	 *	@see	de.sciss.meloncillo.util.PrefsUtil#KEY_LISPREALTIMELIST
	 */
	protected String getSourceListKey()
	{
		return PrefsUtil.KEY_LISPREALTIMELIST;
	}
	
	protected void createSpecialPrimitives( AdvancedJatha lisp )
	{
		// no special primitives
	}

	protected void createSpecialSymbols( AdvancedJatha lisp, PlugInContext context )
	{
		// no special symbols
	}
	
	private BufferTemplate createBufferTemplate( Request r )
	throws IOException
	{
		BufferTemplate  bt			= new BufferTemplate();
		LispValue		cmdListList	= r.mediumOptions;
		LispValue		cmdList, val;
		int				i;
		String			cmdStr;
		Object			o, id;
		
		bt.byteBuf	= (ByteBuffer) r.medium;
		bt.byteBuf.clear();
		bt.floatBuf = bt.byteBuf.asFloatBuffer();
		bt.numCmds  = r.mediumOptions.basic_length();
		bt.cmd		= new int[ bt.numCmds ];
		bt.constant	= new int[ bt.numCmds ];
		bt.offset	= new int[ bt.numCmds ];
		
		for( i = 0; i < bt.numCmds; i++ ) {
			cmdList		= cmdListList.car();
			cmdListList = cmdListList.cdr();
			cmdStr		= cmdList.first().toStringSimple().toUpperCase();
			
			if( cmdStr.equals( "SEND" )) {
				bt.cmd[ i ] = BT_SEND;
				id			= cmdList.second().toJava();
				o			= jatha.getObject( id);
				if( o == null ) {
					throw new IOException( getResourceString( "errLispWrongObjType" ) + " : " + id );
				}
				if( !(o instanceof DatagramChannel) ) {
					throw new IOException( getResourceString( "errLispWrongObjType" ) + " : " + id );
				}
				bt.dch  = (DatagramChannel) o;
			
			} else {
				val = cmdList.basic_length() == 3 ? cmdList.third() : cmdList.second();
				if( !val.basic_integerp() ) {
					throw new IOException( getResourceString( "errLispWrongArgType" ));
				}
				bt.offset[ i ] = (int) ((LispNumber) val).getLongValue();

				if( cmdStr.equals( "INT" )) {
					bt.cmd[ i ] = BT_CONST;
					val = cmdList.second();
					if( !val.basic_numberp() ) {
						throw new IOException( getResourceString( "errLispWrongArgType" ));
					}
					bt.constant[ i ] = (int) ((LispNumber) val).getLongValue();
					
				} else if( cmdStr.equals( "FLOAT" )) {
					bt.cmd[ i ] = BT_CONST;
					val = cmdList.second();
					if( !val.basic_numberp() ) {
						throw new IOException( getResourceString( "errLispWrongArgType" ));
					}
					bt.constant[ i ] = Float.floatToRawIntBits( (float) ((LispNumber) val).getDoubleValue() );
					
				} else if( cmdStr.equals( "VAR" )) {
					bt.cmd[ i ] = BT_VAR;
					cmdStr = cmdList.second().toStringSimple().toUpperCase();
					if( cmdStr.equals( "BUFOFF" )) {
						bt.constant[ i ] = BT_VAR_BUFOFF;
					} else {
						throw new IOException( getResourceString( "errLispWrongArgValue" ));
					}

				} else if( cmdStr.equals( "STREAM" )) {
					bt.cmd[ i ] = BT_STREAM;
				
				} else {
					throw new IOException( getResourceString( "errLispWrongArgValue" ));
				}
			}
		} // for( i = 0; i < bt.numCmds; i++ )
		return bt;
	}

	private static void processBufferTemplate( BufferTemplate bt, float[] streamBuf, int bufOff, int bufLength )
	throws IOException
	{
		for( int i = 0; i < bt.numCmds; i++ ) {
			switch( bt.cmd[ i ]) {
			case BT_SEND:
				bt.byteBuf.clear();
				bt.dch.write( bt.byteBuf );
				break;
			case BT_STREAM:
				bt.floatBuf.position( bt.offset[ i ] >> 2 );
				bt.floatBuf.put( streamBuf, 0, bufLength );
				break;
			case BT_CONST:
				bt.byteBuf.position( bt.offset[ i ]);
				bt.byteBuf.putInt( bt.constant[ i ]);
				break;
			case BT_VAR:
				bt.byteBuf.position( bt.offset[ i ]);
				switch( bt.constant[ i ]) {
				case BT_VAR_BUFOFF:
					bt.byteBuf.putInt( bufOff );
					break;
				default:
					assert false : bt.constant[ i ];
				}
				break;
			default:
				assert false : bt.cmd[ i ];
			}
		}
	}

	/**
	 *	Called by the realtime host when the plug-in is
	 *	enabled. This will create internal realtime information
	 *	objects, call the lisp scripts 'PREPARE' function
	 *	and deal with resulting source requests. It
	 *	installs a transport listener and launches a separate
	 *	thread responsible for dispatching the streaming OSC
	 *	packets.
	 *	<p>
	 *	A note about source-requests: For "TRAJ" and "SENSE" the
	 *	<code>medium</code> has to be a pre-filled byte-buffer and the
	 *	<code>param</code> a list of commands acting on the buffer
	 *	template each time a new buffer is to be dispatched. Each element
	 *	in the command list is a list itself whose first element is a
	 *	string command:
	 *	<UL>
	 *	<LI>"INT" -		second element is an integer that gets written to the buffer
	 *					(32bit int) at an index given by the third element</LI>
	 *	<LI>"FLOAT" -	second element is a real that gets written to the buffer
	 *					(32bit float) at an index given by the third element</LI>
	 *	<LI>"VAR" -		second element is a string specifying the variable to be written to the buffer
	 *					at an index given by the third element. The only possible variable at
	 *					the moment is "BUFOFF" which corresponds to 32bit int offset in the
	 *					the buffer (0 for odd packets, bufsize/2 for even packets).</LI>
	 *	<LI>"STREAM" -	stream data is copied to the buffer
	 *					(32bit floats) at an index given by the second element</LI>
	 *	<LI>"SEND" -	second element is an identifier of a datagram channel to which the buffer
	 *					gets send. This should be the last command</LI>
	 *	</UL>
	 *
	 *	@param	context		the current context of the realtime performance
	 *	@param	transport	realtime hosting transport
	 *	@return				<code>false</code> if initialization fails and plug-in
	 *						remains disabled
	 *
	 *	@synchronization	syncs on 'this' to prevent interference
	 *						with the TransportListener
	 */
	public boolean realtimeEnable( RealtimeContext context, Transport transport )
	throws IOException
	{
		if( !plugInPrepare( context )) return false;

		final double		maxRate;
		final double		senseRate;
		final boolean		wasRunning;
		int					frameStep, trnsIdx, rcvIdx;
		List				collRequests;
		LispPlugIn.Request  r;
		boolean				success		= false;

		try {
//			synchronized( this ) {
			 	rt_producer.changeContext( context );
			
				rt_info					= new RealtimeInfo( this, context );
				rt_info.sourceRate		= context.getSourceRate();
				rt_info.senseBufSizeH	= context.getSourceBlockSize() >> 1;
				maxRate					= Math.min( rt_info.sourceRate, Math.max( 2,
											AbstractApplication.getApplication().getUserPrefs().node(
											PrefsUtil.NODE_PLUGINS ).getInt( PrefsUtil.KEY_RTMAXSENSERATE, 0 )));
				for( frameStep = 1; rt_info.sourceRate / frameStep > maxRate; frameStep <<= 1 ) ;
				frameStep				= Math.min( frameStep, rt_info.senseBufSizeH );
				senseRate				= (double) rt_info.sourceRate / frameStep;
				rt_info.frameStep		= frameStep;
				rt_info.notifyTickStep	= Math.max( rt_info.frameStep, rt_info.senseBufSizeH >> 3 );	// XXX  TEST
				rt_info.notifyTicks		= true;
				rt_info.streamSenseBufSize= rt_info.senseBufSizeH / frameStep;
				rt_info.streamSenseBuf	= new float[ rt_info.numTrns ][ rt_info.numRcv][ 3 ][];
				rt_info.streamBufCopyIdx= 0;
				rt_info.streamTrajBuf	= new float[ rt_info.numTrns ][ 3 ][];
				rt_info.streamTrajBufSize= rt_info.streamSenseBufSize << 1;
//				rt_info.trigDur			= (double) rt_info.streamSenseBufSize / senseRate;
				rt_info.streamBufStart	= new long[] { -1, -1, -1 };
				rt_info.streamBufCreation= new long[3];
// BBB
//				rt_info.bufSendThread   = new BufferSenderThread();
				prefsHash.setf_gethash( jatha.makeString( "SENSERATE" ), jatha.makeReal( senseRate ));
				prefsHash.setf_gethash( jatha.makeString( "SENSEBUFSIZE" ),
										jatha.makeInteger( rt_info.streamSenseBufSize << 1 ));

				if( !executeLisp( "PREPARE" )) return false;
				
				// ------------------- source requests -------------------
				collRequests = sourceRequestPrimitive.getRequests();
				for( int i = 0; i < collRequests.size(); i++ ) {
					r = (LispPlugIn.Request) collRequests.get( i );
					switch( r.type ) {
					case REQUEST_TRAJ:
						trnsIdx = ((Number) r.params).intValue();
						if( trnsIdx < 0 || trnsIdx >= rt_info.numTrns ) {
							context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
								getResourceString( "errRenderSourceRequest" ) + " : " + trnsIdx );
							return false;
						}
						if( !(r.medium instanceof ByteBuffer) ) {
							context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
								getResourceString( "errRenderTargetObject" ) + " : " + r.medium );
							return false;
						}
						try {
							rt_info.btTrajTargets[ trnsIdx ] = createBufferTemplate( r );
						}
						catch( Exception e1 ) {
							throw IOUtil.map( e1 );
						}
						rt_info.trajRequest[ trnsIdx ]	= true;
						rt_info.notifyBlocks			= true;
						rt_info.streamTrajBuf[ trnsIdx ]= new float[ 3 ][ rt_info.streamTrajBufSize ]; // interleaved x y
	//					if( info.verbose ) System.out.println( "request input traj : trnsidx "+trnsIdx );
						break;
						
					case REQUEST_SENSE:
						trnsIdx = ((Point) r.params).x;
						rcvIdx  = ((Point) r.params).y;
						if( trnsIdx < 0 || trnsIdx >= rt_info.numTrns || rcvIdx < 0 || rcvIdx >= rt_info.numRcv ) {
							context.getHost().showMessage( JOptionPane.ERROR_MESSAGE, getResourceString(
								"errRenderSourceRequest" ) + " : " + trnsIdx + ", " + rcvIdx );
							return false;
						}
						if( !(r.medium instanceof ByteBuffer) ) {
							context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
								getResourceString( "errRenderTargetObject" ) + " : " + r.medium );
							return false;
						}
						try {
							rt_info.btSenseTargets[ trnsIdx ][ rcvIdx ] = createBufferTemplate( r );
						}
						catch( Exception e1 ) {
							throw IOUtil.map( e1 );
						}
						rt_info.senseRequest[ trnsIdx ][ rcvIdx ]	= true;
						rt_info.notifyBlocks						= true;
						rt_info.streamSenseBuf[ trnsIdx ][ rcvIdx ] = new float[ 3 ][ rt_info.streamSenseBufSize ];
	//					if( info.verbose ) System.out.println( "request input sense : trnsidx "+trnsIdx+"; rcvidx "+rcvIdx );
						break;
					
					default:
						context.getHost().showMessage( JOptionPane.ERROR_MESSAGE, getResourceString(
							"errRenderSourceRequest" ) + " : " +
							(r.type >= 0 && r.type < requestKeyNames.length ? requestKeyNames[ r.type ] :
							String.valueOf( r.type )));
						return false;
					}
				} // for collRequests.length

				// ------------------- target requests -------------------
				collRequests = targetRequestPrimitive.getRequests();
				for( int i = 0; i < collRequests.size(); i++ ) {
					r = (LispPlugIn.Request) collRequests.get( i );
					switch( r.type ) {
					case REQUEST_SYNC:
						if( !(r.medium instanceof DatagramChannel) ) {
							context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
								getResourceString( "errRenderTargetObject" ) + " : " + r.medium );
							return false;
						}
//						rt_info.syncOSC  = new OSCReceiver( (DatagramChannel) r.medium );
						rt_info.syncOSC  = OSCReceiver.newUsing( (DatagramChannel) r.medium );
						rt_info.syncOSC.addOSCListener( this );
						break;
					
					default:
						context.getHost().showMessage( JOptionPane.ERROR_MESSAGE, getResourceString(
							"errRenderTargetRequest" ) + " : " +
							(r.type >= 0 && r.type < requestKeyNames.length ? requestKeyNames[ r.type ] :
							String.valueOf( r.type )));
						return false;
					}
				} // for collRequests.length

				wasRunning = transport.isRunning();
				if( wasRunning ) {
					transport.stop();
				}
// EEE
//				transport.addRealtimeConsumer( this );
				rt_request = createRequest( context );
				final RealtimeConsumerRequest request = rt_request;
				rt_producer.requestAddConsumerRequest( request );

				transport.addTransportListener( this );
// BBB
//				rt_info.bufSendThread.isRunning = true;
//				rt_info.bufSendThread.start();
				if( wasRunning ) {
					transport.play( 1.0 );
				}
				success = true;
//			} // synchronized( this )
		}
		finally {
			if( !success ) {
				realtimeDisable( context, transport );
			}
		}
		
		return success;
	}

	/**
	 *	Called by the realtime host when the plug-in is
	 *	disabled. This will remove the transport listener,
	 *	kill the osc dispatcher thread, call the lisp script's
	 *	'STOP' if transport is still playing, finally call
	 *	the script's 'CLEANUP' function.
	 *
	 *	@param	context		the current context of the realtime performance
	 *	@param	transport	realtime hosting transport
	 *	@return				<code>false</code> if an error occured during cleanup
	 *
	 *	@synchronization	syncs on 'this' to prevent interference
	 *						with the TransportListener
	 */
	public boolean realtimeDisable( RealtimeContext context, Transport transport )
	throws IOException
	{
		boolean success = false;
	
		try {
//			synchronized( this ) {
				transport.removeTransportListener( this );
// EEE
//				transport.removeRealtimeConsumer( this );
				if( rt_request != null ) {
					rt_producer.requestRemoveConsumerRequest( rt_request );
					rt_request = null;
				}
				if( isPlaying ) {
					transportStop( transport, 0 );
				}
				if( rt_info != null ) {
// BBB
//					rt_info.bufSendThread.isRunning = false;
//					rt_info.bufSendThread.interrupt();
					if( rt_info.syncOSC != null ) rt_info.syncOSC.removeOSCListener( this );
					rt_info.syncOSC			= null;
					rt_info.streamSenseBuf  = null;
					rt_info.streamTrajBuf   = null;
					rt_info					= null;
				}
				success = executeLisp( "CLEANUP" );
//			} // synchronized( this )
		}
		finally {
			plugInCleanUp( context );
		}
		
		return success;
	}

// ---------------- OSCListener interface ---------------- 

	/**
	 *	Filters out SuperCollider's <code>"/tr"</code>
	 *	messages and schedules a new buffer send
	 *	depending on the third OSC command
	 *	(trigger value). the requested frame offset
	 *	is calculated as startFrame + triggerValue * senseBufSize/2
	 */
	public void messageReceived( OSCMessage msg, SocketAddress sender, long time )
	{
		elm.dispatchEvent( new OSCEvent( sender, 0, time, msg ));
	}

// ---------------- RealtimeConsumer interface ---------------- 

	// called in event thread, no sync needed
	/**
	 *	Returns the request created in the realtimeEnable
	 *	method or a fake one if the plug-in was not enabled
	 *
	 *	@todo	this should check against changes and re-init the plug-in
	 */
	public RealtimeConsumerRequest createRequest( RealtimeContext context )
	{
		if( rt_info != null ) return rt_info;
		
		return new RealtimeConsumerRequest( this, context );	// fake
	}
	
	/**
	 *	Not used
	 */
	public void offhandTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos ) {}

	/**
	 *	Called at a rate of 1/8 sense rate, this
	 *	method frequently checks if new sync triggers
	 *	have been send. If so, tries to find the correct
	 *	buffer and passes it to the OSC dispatcher thread.
	 *	If not, sets the transport palette's timeline label
	 *	to red colour to indicate drop outs.
	 */
	public void realtimeTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
	{
		RealtimeInfo myInfo = rt_info;

		if( myInfo == null ) return;

		int		i, myTrigger;
		long	remoteFrame;

		myTrigger = myInfo.trigToServe;
		if( myTrigger != myInfo.trigServed ) {
			if( myTrigger - myInfo.trigServed > 1 ) {
//				System.err.println( " miss? "+(myInfo.trigServed+1)+" ... "+(myTrigger-1) );
				tp.blink();
			}
			remoteFrame = myInfo.startFrame + myTrigger * myInfo.senseBufSizeH;
			// check if we can serve the request
			for( i = 0; i < 3; i++ ) {
				if( myInfo.streamBufStart[ i ] == remoteFrame ) {
// BBB
//					synchronized( myInfo.bufSendThread ) {
//						if( myInfo.bufSendThread.bufferToSend != -1 ) {
//							try {
//								myInfo.bufSendThread.wait();	// wait for the bufSendThread to be finished
//							}
//							catch( InterruptedException e1 ) {}
//						}
//						myInfo.bufSendThread.even			= (myTrigger & 1) == 0;
//						myInfo.bufSendThread.bufferToSend   = i;
//						myInfo.bufSendThread.notifyAll();
//						myInfo.trigServed = myTrigger;
//						return;
//					}
				}
			}
		}
	}

	// called in event thread so it's safe to use rt_info
	/**
	 *	Saves the new block data
	 *	in one of the internal three buffers.
	 *
	 *	@todo	check if three buffers are really still necessary?
	 */
	public void realtimeBlock( RealtimeContext context, RealtimeProducer.Source source, boolean even )
	{
System.out.println( "lisp realtimeBlock : even = " + even + "; span = " + (even ? source.firstHalf : source.secondHalf) );
		
		RealtimeInfo myInfo = rt_info;

		if( myInfo == null ) return;

		int		i, j, trnsIdx, rcvIdx, bufOff;
		float[] convBuf1, convBuf2, convBuf3;
		long	frameStart;

		if( even ) {
			bufOff		= 0;
			frameStart	= source.firstHalf.getStart();
		} else {
			bufOff		= source.bufSizeH;
			frameStart	= source.secondHalf.getStart();
		}

		for( trnsIdx = 0; trnsIdx < myInfo.numTrns; trnsIdx++ ) {
			if( myInfo.trajRequest[ trnsIdx ]) {
//				processBufferTemplate( myInfo.btTrajTargets[ trnsIdx ], source.trajBlockBuf[ trnsIdx ],
//									   myInfo.streamBuf, bufOff, myInfo.frameStep, myInfo.streamSenseBufSize );
				convBuf1 = myInfo.streamTrajBuf[ trnsIdx ][ myInfo.streamBufCopyIdx ];
				convBuf2 = source.trajBlockBuf[ trnsIdx ][0];
				convBuf3 = source.trajBlockBuf[ trnsIdx ][1];
				for( i = 0, j = bufOff; i < myInfo.streamTrajBufSize; j += myInfo.frameStep ) {
					convBuf1[ i++ ] = convBuf2[ j ];	// x
					convBuf1[ i++ ] = convBuf3[ j ];	// y
				}
			}
			for( rcvIdx = 0; rcvIdx < myInfo.numRcv; rcvIdx++ ) {
				if( myInfo.senseRequest[ trnsIdx ][ rcvIdx ]) {
					convBuf1 = myInfo.streamSenseBuf[ trnsIdx ][ rcvIdx ][ myInfo.streamBufCopyIdx ];
					convBuf2 = source.senseBlockBuf[ trnsIdx ][ rcvIdx ];
					for( i = 0, j = bufOff; i < myInfo.streamSenseBufSize; i++, j += myInfo.frameStep ) {
						convBuf1[ i ] = convBuf2[ j ];
					}
				}
			}
		}
		
//		synchronized( this ) {
//			this.notifyAll();   // wakes up messageReceived()
//		}
		myInfo.streamBufStart[ myInfo.streamBufCopyIdx ] = frameStart;
		myInfo.streamBufCreation[ myInfo.streamBufCopyIdx ] = System.currentTimeMillis() - myInfo.startTime;
		myInfo.streamBufCopyIdx = (myInfo.streamBufCopyIdx + 1) % 3;
	}
	
// ---------------- TransportListener interface ---------------- 

	// syncs on this
	/**
	 *	Will call the script's "STOP" function
	 *	and stop listining to SC triggers
	 */
	public void transportStop( Transport t, long pos )
	{
//		synchronized( this ) {
            isPlaying = false;
			if( rt_info != null && rt_info.syncOSC != null ) {
                try {
                    rt_info.syncOSC.stopListening();
                }
                catch( IOException e1 ) {
                    System.err.println( e1.getLocalizedMessage() );
                }
            }
			try {
				executeLisp( "STOP", lispPosition( pos ));
			}
			catch( IOException e2 ) {
				System.err.println( e2.getLocalizedMessage() );
			}
//		} // synchronized( this )
	}
	
	// syncs on 'this'
	/**
	 *	Will call the script's "POSITION" function
	 */
	public void transportPosition( Transport t, long pos, double rate )
	{
		boolean success = false;

//		synchronized( this ) {
			try {
				success				= executeLisp( "POSITION", lispPosition( pos ));
				rt_info.startFrame  = pos;
				rt_info.startTime   = System.currentTimeMillis();
			}
			catch( IOException e1 ) {
				System.err.println( e1 );
			}
			finally {
				if( !success ) transportStop( t, pos );
			}
//		} // synchronized( this )
	}

	public void transportReadjust( Transport t, long pos, double rate )
	{
		transportPosition( t, pos, rate );
	}
	
	// syncs on 'this'
	/**
	 *	Will call the script's "PLAY" function
	 *	and start listining to SC triggers
	 *
	 *	@todo	start listening is called after the lisp code
	 *			and could (in theory) miss the first trigger?
	 */
	public void transportPlay( Transport t, long pos, double rate )
	{
		boolean success = false;
		
//		synchronized( this ) {
			if( rt_info == null ) return;
			try {
				success				= executeLisp( "PLAY", lispPosition( pos ));
				isPlaying			= success;
				rt_info.startFrame  = pos;
				rt_info.startTime   = System.currentTimeMillis();
				if( success && rt_info.syncOSC != null ) {
					rt_info.syncOSC.startListening();
				}
			}
			catch( IOException e1 ) {
				System.err.println( e1 );
			}
			finally {
				if( !success ) transportStop( t, pos );
			}
//		} // synchronized( this )
	}
	
	public void transportQuit( Transport t ) { /* ignore */ }

	// call w/ sync on 'this' and valid rt_info
	private LispValue lispPosition( long pos )
	{
		return( jatha.makeList( jatha.makeReal( (double) pos / rt_info.sourceRate )));
	}

// -------- EventManager.Processor --------
	
	public void processEvent( BasicEvent e )
	{
		final OSCMessage msg = ((OSCEvent) e).msg;
		if( VERBOSEOSC ) System.err.println( "got OSC: " + msg.getName() );
        if( !(msg.getName().equals( "/tr" ) && (msg.getArgCount() >= 3)) ) return;
		if( rt_info == null ) return;

		final int		myTrigger	= ((Number) msg.getArg( 2 )).intValue();
		final long		remoteFrame = rt_info.startFrame + myTrigger * rt_info.senseBufSizeH;
		final boolean	even		= (myTrigger & 1) == 0;
		
		rt_info.trigToServe  = myTrigger;
		if( VERBOSEOSC ) {
			System.err.println( " tr: "+myTrigger+ " = "+remoteFrame );
		}
		
		rt_producer.produceNow( new Span( remoteFrame, remoteFrame + rt_info.senseBufSizeH ), even );
//		rt_producer.produceNow( new Span( rt_pos + rt_producer.source.bufSizeH,
//										  rt_pos + rt_producer.source.bufSize ),
//									   (frameCount & rt_producer.source.bufSizeH) != 0 );

		final long						frameStart;
		final int						bufOff;
		final RealtimeProducer.Source	source	= rt_producer.source;
//		final float[][]					convBuf1;
		float[]							convBuf1, convBuf2, convBuf3;
		
		if( even ) {
			bufOff		= 0;
			frameStart	= source.firstHalf.getStart();
		} else {
			bufOff		= source.bufSizeH;
			frameStart	= source.secondHalf.getStart();
		}

		for( int trnsIdx = 0; trnsIdx < rt_info.numTrns; trnsIdx++ ) {
			if( rt_info.trajRequest[ trnsIdx ]) {
//				processBufferTemplate( myInfo.btTrajTargets[ trnsIdx ], source.trajBlockBuf[ trnsIdx ],
//									   myInfo.streamBuf, bufOff, myInfo.frameStep, myInfo.streamSenseBufSize );
				convBuf1 = rt_info.streamTrajBuf[ trnsIdx ][ rt_info.streamBufCopyIdx ];
				convBuf2 = source.trajBlockBuf[ trnsIdx ][0];
				convBuf3 = source.trajBlockBuf[ trnsIdx ][1];
				for( int i = 0, j = bufOff; i < rt_info.streamTrajBufSize; j += rt_info.frameStep ) {
					convBuf1[ i++ ] = convBuf2[ j ];	// x
					convBuf1[ i++ ] = convBuf3[ j ];	// y
				}
			}
			for( int rcvIdx = 0; rcvIdx < rt_info.numRcv; rcvIdx++ ) {
				if( rt_info.senseRequest[ trnsIdx ][ rcvIdx ]) {
					convBuf1 = rt_info.streamSenseBuf[ trnsIdx ][ rcvIdx ][ rt_info.streamBufCopyIdx ];
					convBuf2 = source.senseBlockBuf[ trnsIdx ][ rcvIdx ];
					for( int i = 0, j = bufOff; i < rt_info.streamSenseBufSize; i++, j += rt_info.frameStep ) {
						convBuf1[ i ] = convBuf2[ j ];
					}
				}
			}
		}
		
		rt_info.streamBufStart[ rt_info.streamBufCopyIdx ] = frameStart;
//		rt_info.streamBufCreation[ rt_info.streamBufCopyIdx ] = System.currentTimeMillis() - rt_info.startTime;
//		rt_info.streamBufCopyIdx = (rt_info.streamBufCopyIdx + 1) % 3;
		
		assert rt_info.streamBufStart[ 0 ] == remoteFrame : rt_info.streamBufStart[ 0 ];
		final int myBufToSend = 0;

		rt_info.trigServed = myTrigger;

		// ok go and process the buffer templates
		try {
			for( int trnsIdx = 0; trnsIdx < rt_info.numTrns; trnsIdx++ ) {
				if( rt_info.trajRequest[ trnsIdx ]) {
					processBufferTemplate( rt_info.btTrajTargets[ trnsIdx ],
					                       rt_info.streamTrajBuf[ trnsIdx ][ myBufToSend ],
										   even ? 0 : rt_info.streamTrajBufSize,
										   rt_info.streamTrajBufSize );
				}
				for( int rcvIdx = 0; rcvIdx < rt_info.numRcv; rcvIdx++ ) {
					if( rt_info.senseRequest[ trnsIdx ][ rcvIdx ]) {
						processBufferTemplate( rt_info.btSenseTargets[ trnsIdx ][ rcvIdx ],
						                       rt_info.streamSenseBuf[ trnsIdx ][ rcvIdx ][ myBufToSend ],
											   even ? 0 : rt_info.streamSenseBufSize,
											   rt_info.streamSenseBufSize );
					}
				}
			}
		}
		catch( IOException e1 ) {
			System.err.println( "[@"+getName()+"]" + e1.getLocalizedMessage() );
		}
			
//		// check if we can serve the request
//		for( int i = 0; i < 3; i++ ) {
//			if( rt_info.streamBufStart[ i ] == remoteFrame ) {
//				synchronized( rt_info.bufSendThread ) {
//					if( rt_info.bufSendThread.bufferToSend != -1 ) {
//						try {
//							rt_info.bufSendThread.wait();	// wait for the bufSendThread to be finished
//						}
//						catch( InterruptedException e1 ) {}
//					}
//					rt_info.bufSendThread.even			= (myTrigger & 1) == 0;
//					rt_info.bufSendThread.bufferToSend   = i;
//					rt_info.bufSendThread.notifyAll();
//					rt_info.trigServed = myTrigger;
//					return;
//				}
//			}
//		}
	}

// -------- internal classes --------
//
//	private class BufferSenderThread
//	extends Thread
//	{
//		private boolean isRunning;
//		private int		bufferToSend	= -1;
//		private boolean even;
//	
//		private BufferSenderThread()
//		{
//			super( "BufferSender" );
//			setDaemon( true );
//		}
//		
//		public void run()
//		{
//			// copying the reference is more performative and
//			// equally safe compared to synchronization
//			final RealtimeInfo myInfo  = rt_info;
//			if( myInfo == null ) return;
//			
//			int		trnsIdx, rcvIdx, myBufToSend;
//			boolean myEven;
//
//			synchronized( this ) {
//				myEven		= even;
//				myBufToSend = bufferToSend;
//			}
//						
//			try {
//				while( isRunning ) {
//					if( myBufToSend >= 0 ) {
//						// ok go and process the buffer templates
//						try {
//							for( trnsIdx = 0; trnsIdx < myInfo.numTrns; trnsIdx++ ) {
//								if( myInfo.trajRequest[ trnsIdx ]) {
//									processBufferTemplate( myInfo.btTrajTargets[ trnsIdx ],
//														   myInfo.streamTrajBuf[ trnsIdx ][ myBufToSend ],
//														   myEven ? 0 : myInfo.streamTrajBufSize,
//														   myInfo.streamTrajBufSize );
//								}
//								for( rcvIdx = 0; rcvIdx < myInfo.numRcv; rcvIdx++ ) {
//									if( myInfo.senseRequest[ trnsIdx ][ rcvIdx ]) {
//										processBufferTemplate( myInfo.btSenseTargets[ trnsIdx ][ rcvIdx ],
//															   myInfo.streamSenseBuf[ trnsIdx ][ rcvIdx ][ myBufToSend ],
//															   myEven ? 0 : myInfo.streamSenseBufSize,
//															   myInfo.streamSenseBufSize );
//									}
//								}
//							}
////System.err.println( " sent: "+myInfo.streamBufStart[ myBufToSend ]);
//						}
//						catch( Exception e1 ) {
//							System.err.println( "[@"+getName()+"]" + e1.getLocalizedMessage() );
//						}
//					} // if( bufferToSend >= 0 )
//					
//					synchronized( this ) {
//						bufferToSend = -1;
//						this.notifyAll();
//						this.wait();
//						myEven		= even;
//						myBufToSend = bufferToSend;
//					}
//				} // while( isRunning )
//			}
//			catch( InterruptedException e2 ) {}
//		}
//	}

	private class BufferTemplate
	{
		private ByteBuffer		byteBuf;
		private FloatBuffer		floatBuf;
		private DatagramChannel dch;
		private int				numCmds;
		private int[]			cmd;
		private int[]			constant;
		private int[]			offset;
	}
	
	private static class OSCEvent
	extends BasicEvent
	{
		private final OSCMessage msg;
		
		private OSCEvent( Object source, int id, long when, OSCMessage msg )
		{
			super( source, id, when );
			this.msg = msg;
		}
		
		public boolean incorporate( BasicEvent e )
		{
			return false;
		}
	}
	
	private static class RealtimeInfo
	extends RealtimeConsumerRequest
	{
		private BufferTemplate[]		btTrajTargets;
		private BufferTemplate[][]		btSenseTargets;
		private OSCReceiver				syncOSC;
		private long					startFrame;
		private long					startTime;
		private double					sourceRate;
//		private double					trigDur;
		private float[][][]				streamTrajBuf;
		private int						streamTrajBufSize;
		private float[][][][]			streamSenseBuf;
		private int						streamSenseBufSize;
		private long[]					streamBufStart;
		private long[]					streamBufCreation;
		private int						streamBufCopyIdx;
		private int						senseBufSizeH;
		private int						trigToServe, trigServed;
// BBB
//		private BufferSenderThread		bufSendThread;

		private RealtimeInfo( RealtimeConsumer massa, RealtimeContext context )
		{
			super( massa, context );
	
			btTrajTargets   = new BufferTemplate[ numTrns ];
			btSenseTargets  = new BufferTemplate[ numTrns ][ numRcv ];
		}
	}
}