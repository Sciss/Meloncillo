/*
 *  RealtimeProducer.java
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
 *		22-Jul-04   created
 *		28-Jul-04   fixed even/odd to work exactly as supercollider realtime phasor
 *		01-Sep-04	commented
 */

package de.sciss.meloncillo.realtime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.sciss.app.LaterInvocationManager;
import de.sciss.io.Span;
import de.sciss.meloncillo.receiver.Receiver;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.transmitter.TrajectoryGenerator;
import de.sciss.meloncillo.transmitter.Transmitter;

/**
 *	The RealtimeProducer is the "factory" of
 *	the realtime engine. Requests for stream data
 *	production can be made synchronously and
 *	asynchronously. The data is kept in a public
 *	instance variable <code>source</code>. Data
 *	is produced for alternating buffer halfs
 *	(first half or second half) such that only
 *	one buffer field is needed but future data
 *	can be produced in advance. The requests are
 *	usually made from the transport running thread.
 *	The transport also requests adding and removal
 *	of consumers. The RealtimeProducer creates an
 *	internal representation of the union of all
 *	consumer requests such that the minimum frame
 *	step is determinated and only necessary data is
 *	produced. Whenever a request is completed,
 *	the host's (transport's) <code>notifyConsumed</code>
 *	method is called.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 15-Jul-08
 *
 *	@see	Transport
 *	@see	RealtimeProducer#RealtimeProducer( Main, Session, RealtimeHost )
 */
public class RealtimeProducer
{
	/**
	 *	Request type: stream data generation
	 */
	public static final int TYPE_PRODUCE		= 0;
	/**
	 *	Request type: adding one or several consumers
	 */
	public static final int TYPE_ADDCONFIG		= 1;
	/**
	 *	Request type: removing one or several consumers
	 */
	public static final int TYPE_REMOVECONFIG	= 2;
	/**
	 *	Request type: add a trajectory replacement
	 */
	public static final int TYPE_ADDREPLACEMENT		= 3;
	/**
	 *	Request type: remove a trajectory replacement
	 */
	public static final int TYPE_REMOVEREPLACEMENT	= 4;

	/**
	 *	The source contains the stream data
	 *	buffer and the curr
	 */
	public RealtimeProducer.Source source;
	
	private final List	collInfos			= new ArrayList();  // synced because always in event thread
	private final List	collReplacements	= new ArrayList();  // synced because always in event thread
	
	private final Session		doc;
	private final RealtimeHost	host;
	
	private final float[][] offhandTempBuf  = new float[2][1];
	private final float[]	offhandTempBuf2 = new float[1];
	
	/**
	 *	Creates a new RealtimeProducer. This is only
	 *	done once when initializing the transport.
	 *
	 *	@param	root	Application root
	 *	@param	doc		Session document
	 *	@param	host	usually the Transport
	 */
	public RealtimeProducer( Session doc, RealtimeHost host )
	{
		this.doc	= doc;
		this.host   = host;
	}
	
	/**
	 *	Requests are fulfilled here
	 */
	public void process( Request r )
	{
//		Request r			= (Request) o;
		long	now			= System.currentTimeMillis();
		long	patience	= (now - r.deadline) >> 1;
		
		switch( r.type ) {
		case TYPE_PRODUCE:
			if( patience <= 0 ) {
				System.err.println( "drop!" );
				return;
			}
			produce( r.blockSpan, r.even, patience );
			break;

		case TYPE_ADDCONFIG:
			collInfos.addAll( r.requests );
			reConfig();
			break;

		case TYPE_REMOVECONFIG:
			boolean result = collInfos.removeAll( r.requests );
			if( result ) {
				reConfig();
			} else {
				System.err.println( "obsolete rt consumer requests found:" );
				for( int i = 0; i < r.requests.size(); i++ ) {
					System.err.println( ((RealtimeConsumerRequest) r.requests.get( i )).consumer.getClass().getName() );
				}
			}
			break;

		case TYPE_ADDREPLACEMENT:
			collReplacements.add( (TrajectoryReplacement) r.obj );
			reConfigReplacements( source );
			break;
		
		case TYPE_REMOVEREPLACEMENT:
			collReplacements.remove( (TrajectoryReplacement) r.obj );
			reConfigReplacements( source );
			break;
			
		default:
			assert false : r.type;
			break;
		}
		
		if( host != null ) host.notifyConsumed( r );
	}
	
	/**
	 *	Tells the realtime producer that the realtime context
	 *	changed. Note that calling this method will result
	 *	in a removal of all current request, hence they have to
	 *	be readded after this method returns.
	 *
	 *	@param	c	the new context from which the source
	 *				is re-constructed
	 *
	 *	@see	#addConsumerRequestsNow( java.util.List )
	 *
	 *  @synchronization	call only in the event thread!
	 */
	public void changeContext( RealtimeContext c )
	{
		java.util.List  coll;
		Source			s	= new Source();
		int				i;
	
		collInfos.clear();
	
		s.numTrns		= c.getTransmitters().size();
		s.numRcv		= c.getReceivers().size();
		coll			= c.getTransmitters();
		s.transmitters  = new Transmitter[ coll.size() ];
		for( i = 0; i < coll.size(); i++ ) {
			s.transmitters[ i ] = (Transmitter) coll.get( i );
		}
		coll			= c.getReceivers();
		s.receivers		= new Receiver[ coll.size() ];
		for( i = 0; i < coll.size(); i++ ) {
			s.receivers[ i ] = (Receiver) coll.get( i );
		}
		s.bufSize		= c.getSourceBlockSize();
		s.bufSizeH		= s.bufSize >> 1;
		s.senseRequest  = new boolean[ s.numTrns ][ s.numRcv ];
		s.senseBlockBuf = new float[ s.numTrns ][ s.numRcv ][ s.bufSize ];
		s.senseOffhand  = new float[ s.numTrns ][ s.numRcv ];
		s.trajRequest   = new boolean[ s.numTrns ];
		s.trajRplc		= new int[ s.numTrns ];
		s.trajBlockBuf  = new float[ s.numTrns ][ 2 ][ s.bufSize ];
		s.trajOffhand   = new float[ s.numTrns ][ 2 ];
		s.trnsRequest   = new boolean[ s.numTrns ];
		s.minSenseStep  = s.bufSizeH;   // max allowed

		reConfigReplacements( s );
		this.source		= s;
	}
	
	private void reConfig()
	{
		int						trnsIdx, rcvIdx, cfIdx;
		RealtimeConsumerRequest	cf;
		boolean					makeSense, makeTraj;

		// reset requests
		for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
			source.trnsRequest[ trnsIdx ] = false;
			source.trajRequest[ trnsIdx ] = false;
			for( rcvIdx = 0; rcvIdx < source.numRcv; rcvIdx++ ) {
				source.senseRequest[ trnsIdx ][ rcvIdx ] = false;
			}
		}
		source.doors		= 0;
		source.minSenseStep = source.bufSizeH;   // max allowed
		
		// sum individual requests
		for( cfIdx = 0; cfIdx < collInfos.size(); cfIdx++ ) {
			cf		= (RealtimeConsumerRequest) collInfos.get( cfIdx );

			// cf.frameStep must be a power of 2 and greater or equal 1
			assert (cf.frameStep > 0) && (cf.frameStep <= source.bufSizeH) &&
				   ((cf.frameStep | (cf.frameStep - 1)) == (cf.frameStep + cf.frameStep - 1)) : cf.frameStep;
				   
			for( trnsIdx = 0, makeSense = false, makeTraj = false; trnsIdx < source.numTrns; trnsIdx++ ) {
				if( cf.trajRequest[ trnsIdx ]) {
					source.trnsRequest[ trnsIdx ]   = true;
					source.trajRequest[ trnsIdx ]   = true;
					makeTraj						= true;
				}
				for( rcvIdx = 0; rcvIdx < source.numRcv; rcvIdx++ ) {
					if( cf.senseRequest[ trnsIdx ][ rcvIdx ]) {
						source.trnsRequest[ trnsIdx ]				= true;
						source.senseRequest[ trnsIdx ][ rcvIdx ]	= true;
						makeSense									= true;
					}
				}
			}
			if( makeSense ) {
				source.minSenseStep = Math.min( source.minSenseStep, cf.frameStep );
				source.doors |= Session.DOOR_TRNSMTE | Session.DOOR_RCV;
			}
			if( makeTraj ) {
				source.doors |= Session.DOOR_TRNSMTE;
			}
		}
		
		// shall we produce now?
	}

	// sync: call in event thread
	private void reConfigReplacements( Source s )
	{
		int	trnsIdx, i;
		TrajectoryReplacement tr;
		Transmitter trns;

		// reset requests
trnsLp:	for( trnsIdx = 0; trnsIdx < s.numTrns; trnsIdx++ ) {
			trns = s.transmitters[ trnsIdx ];
			for( i = 0; i < collReplacements.size(); i++ ) {
				tr = (TrajectoryReplacement) collReplacements.get( i );
				if( tr.collTransmitters.contains( trns )) {
					s.trajRplc[ trnsIdx ] = i;
					continue trnsLp;
				}
			}
			s.trajRplc[ trnsIdx ] = -1;
		}
	}
	
	private void produce( Span blockSpan, boolean even, long patience )
	{

//System.out.println( "produce: blockSpan = " + blockSpan + "; even = " + even + "; patience = " + patience );

		int trnsIdx, rcvIdx, offStart, offStop;
		
		if( even ) {
			offStart			= 0;
			offStop				= (int) blockSpan.getLength();
			source.firstHalf	= blockSpan;
		} else {
			offStart			= source.bufSizeH;
			offStop				= offStart + (int) blockSpan.getLength();
			source.secondHalf	= blockSpan;
		}
		
//		if( !doc.bird.attemptShared( source.doors, patience )) {	// XXX MTE can't be shared
//			System.err.println( "busy!" );
//			return;
//		}
		try {
			for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
				if( !source.trnsRequest[ trnsIdx ]) continue;
				
				// --- read transmitter trajectory data ---
				if( source.trajRplc[ trnsIdx ] == -1 ) {
//					source.transmitters[ trnsIdx ].getAudioTrail().read(
//					                            						blockSpan, source.trajBlockBuf[ trnsIdx ], offStart );
					source.transmitters[ trnsIdx ].getAudioTrail().readFrames(
						source.trajBlockBuf[ trnsIdx ], offStart, blockSpan );
				} else {
					TrajectoryReplacement	tr;
					long					truncStart, truncStop, delta;
					Span					subSpan;
					
					tr			= (TrajectoryReplacement) collReplacements.get( source.trajRplc[ trnsIdx ]);
					truncStart	= Math.max( blockSpan.getStart(), tr.span.getStart() );
					truncStop	= Math.min( blockSpan.getStop(), tr.span.getStop() );
					
					if( truncStart >= truncStop ) {	// no intersection
//						source.transmitters[ trnsIdx ].getAudioTrail().read(
//						                        							blockSpan, source.trajBlockBuf[ trnsIdx ], offStart );
						source.transmitters[ trnsIdx ].getAudioTrail().readFrames(
							source.trajBlockBuf[ trnsIdx ], offStart, blockSpan );
					} else {						// ok, we have to split it up
						delta = truncStart - blockSpan.getStart();
						if( delta > 0 ) {	// beginning not replaced
							subSpan = new Span( blockSpan.getStart(), truncStart );
//							source.transmitters[ trnsIdx ].getAudioTrail().read(
//							                    								subSpan, source.trajBlockBuf[ trnsIdx ], offStart );
							source.transmitters[ trnsIdx ].getAudioTrail().readFrames(
								source.trajBlockBuf[ trnsIdx ], offStart, subSpan );
						}
						subSpan = new Span( truncStart, truncStop );
						tr.tg.read( subSpan, source.trajBlockBuf[ trnsIdx ], (int) (offStart + delta) );
						delta = blockSpan.getStop() - truncStop;
						if( delta > 0 ) {
							subSpan = new Span( truncStop, blockSpan.getStop() );
//							source.transmitters[ trnsIdx ].getAudioTrail().read( subSpan,
//							                     								source.trajBlockBuf[ trnsIdx ], (int) (offStart + truncStop - blockSpan.getStart()) );
							source.transmitters[ trnsIdx ].getAudioTrail().readFrames(
								source.trajBlockBuf[ trnsIdx ], (int) (offStart + truncStop - blockSpan.getStart()),
								subSpan );
						}
					}
				}
				// --- satisfy sensibilities requests ---
				for( rcvIdx = 0; rcvIdx < source.numRcv; rcvIdx++ ) {
					if( !source.senseRequest[ trnsIdx ][ rcvIdx ]) continue;

					source.receivers[ rcvIdx ].getSensitivities(
						source.trajBlockBuf[ trnsIdx ], source.senseBlockBuf[ trnsIdx ][ rcvIdx ],
						offStart, offStop, source.minSenseStep );
				} // for( rcvIdx = 0; rcvIdx < numRcv; rcvIdx++ )

			} // for( trnsIdx = 0; trnsIdx < numTrns; trnsIdx++ )
		}
		catch( IOException e1 ) {
			System.err.println( e1 );
		}
//		finally {
//			doc.bird.releaseShared( source.doors );
//		}
	}
	
	/**
	 *	Asks the realtime producer to produce some time
	 *	span of stream data. The actual production is
	 *	deferred to the event thread.
	 *
	 *	@param	blockSpan	the time span to produce
	 *	@param	even		<code>false</code> means the first
	 *						buffer half will be overwritten,
	 *						<code>true</code> means the second
	 *						buffer half will be overwritten.
	 *	@param	deadline	the request must be fulfilled before
	 *						the current system time reaches this
	 *						deadline which is an absolute time value.
	 *						if the deadline is missed, a warning text
	 *						will be printed to the console.
	 */
	public void requestProduction( Span blockSpan, boolean even, long deadline )
	{
		Request r   = new Request( TYPE_PRODUCE );
		r.blockSpan = blockSpan;
		r.even		= even;
		
//		lim.queue( r );
		process( r );
	}

	/**
	 *	Asks the realtime producer to produce some time
	 *	span of stream data immediately. When this method
	 *	returns, the data has been produced.
	 *
	 *	@param	blockSpan	the time span to produce
	 *	@param	even		<code>false</code> means the first
	 *						buffer half will be overwritten,
	 *						<code>true</code> means the second
	 *						buffer half will be overwritten.
	 *
	 *  @synchronization	call only in the event thread!
	 */
	public void produceNow( Span blockSpan, boolean even )
	{
		produce( blockSpan, even, 250 );
	}

	/**
	 *	Asks the realtime producer to produce one frame of
	 *	stream data for a certain time position. The data
	 *	will be stored in the <code>trajOffhand</code> and
	 *	<code>senseOffhand</code> fields of the source.
	 *	When the method eturns, the data has been produced.
	 *
	 *	@param	currentPos	the time position frame to produce
	 *
	 *  @synchronization	call only in the event thread!
	 */
	public void produceOffhand( long currentPos )
	{
/* EEE
		int trnsIdx, rcvIdx;
		Span miniSpan = new Span( currentPos, currentPos + 1 );
		
		if( !doc.bird.attemptShared( source.doors, 250 )) {	// XXX MTE can't be shared
			System.err.println( "busy!" );
			return;
		}
		try {
			for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
				if( !source.trnsRequest[ trnsIdx ]) continue;

				// --- read transmitter trajectory data ---
				if( source.trajRplc[ trnsIdx ] == -1 ) {
					source.transmitters[ trnsIdx ].getAudioTrail().read( miniSpan, offhandTempBuf, 0 );
				} else {
					TrajectoryReplacement tr = (TrajectoryReplacement) collReplacements.get( source.trajRplc[ trnsIdx ]);

					if( tr.span.getStart() > currentPos || tr.span.getStop() < currentPos ) {
						source.transmitters[ trnsIdx ].getAudioTrail().read( miniSpan, offhandTempBuf, 0 );
					} else {
						tr.tg.read( miniSpan, offhandTempBuf, 0 );
					}
				}
				
				// --- satisfy trajectory requests ---
				if( source.trajRequest[ trnsIdx ]) {
					source.trajOffhand[ trnsIdx ][0]	= offhandTempBuf[0][0];
					source.trajOffhand[ trnsIdx ][1]	= offhandTempBuf[1][0];
				}

				// --- satisfy sensibilities requests ---
				for( rcvIdx = 0; rcvIdx < source.numRcv; rcvIdx++ ) {
					if( !source.senseRequest[ trnsIdx ][ rcvIdx ]) continue;

					source.receivers[ rcvIdx ].getSensitivities(
						offhandTempBuf, offhandTempBuf2, 0, 1, 1 );
					source.senseOffhand[ trnsIdx ][ rcvIdx ] = offhandTempBuf2[ 0 ];
				} // for( rcvIdx = 0; rcvIdx < numRcv; rcvIdx++ )
			} // for( trnsIdx = 0; trnsIdx < numTrns; trnsIdx++ )
		}
		catch( IOException e1 ) {
			System.err.println( e1 );
		}
		finally {
			doc.bird.releaseShared( source.doors );
		}
*/
	}

	/**
	 *	Asks the realtime producer to integrate a new consumer's
	 *	request into the future production. The actual request is
	 *	deferred to the event thread.
	 *
	 *	@param	request		the request to integrate in terms
	 *						of data production and frame step.
	 */
	public void requestAddConsumerRequest( RealtimeConsumerRequest request )
	{
		Request r   = new Request( TYPE_ADDCONFIG );
		r.requests	= new ArrayList( 1 );
		r.requests.add( request );
//		lim.queue( r );
		process( r );
	}

	/**
	 *	Asks the realtime producer to integrate new consumer
	 *	requests into the future production. The actual request is
	 *	deferred to the event thread.
	 *
	 *	@param	requests	a list of <code>RealtimeConsumerRequest</code>s
	 *						to integrate in terms of data production and frame step.
	 */
	public void requestAddConsumerRequests( java.util.List requests )
	{
		Request r   = new Request( TYPE_ADDCONFIG );
		r.requests	= requests;
//		lim.queue( r );
		process( r );
	}

	public void requestAddTrajectoryReplacement( TrajectoryReplacement tr )
	{
		Request r   = new Request( TYPE_ADDREPLACEMENT );
		r.obj		= tr;
//		lim.queue( r );
		process( r );
	}

	public void requestRemoveTrajectoryReplacement( TrajectoryReplacement tr )
	{
		Request r   = new Request( TYPE_REMOVEREPLACEMENT );
		r.obj		= tr;
//		lim.queue( r );
		process( r );
	}
	
	/**
	 *	Asks the realtime producer to integrate new consumer
	 *	requests into the future production. The method performs
	 *	immediately and returns when the requests have been integrated.
	 *
	 *	@param	requests	a list of <code>RealtimeConsumerRequest</code>s
	 *						to integrate in terms of data production and frame step.
	 *
	 *  @synchronization	call only in the event thread!
	 */
	public void addConsumerRequestsNow( java.util.List requests )
	{
		collInfos.addAll( requests );
		reConfig();
	}

	/**
	 *	Asks the realtime producer to disintegrate a consumer's
	 *	request from the future production. The actual request is
	 *	deferred to the event thread.
	 *
	 *	@param	request		the request to disintegrate in terms
	 *						of data production and frame step.
	 */
	public void requestRemoveConsumerRequest( RealtimeConsumerRequest request )
	{
		Request r   = new Request( TYPE_REMOVECONFIG );
		r.requests	= new ArrayList( 1 );
		r.requests.add( request );
//		lim.queue( r );
		process( r );
	}

	/**
	 *	Struct class describing
	 *	a request made to the producer
	 */
	protected static class Request
	{
		/**
		 *	The deadline for the requests.
		 *	Requests arriving too late will
		 *	produce a warning message
		 */
		private long				deadline;
		/**
		 *	What was requested, such as TYPE_REMOVECONFIG
		 */
		protected int				type;
		/**
		 *	For production requests: the span to produce
		 */
		private Span				blockSpan;
		/**
		 *	For production requests: whether to
		 *	overwrite the first buffer half (false)
		 *	or the second buffer half (true)
		 */
		protected boolean			even;
		/**
		 *	For adding/removing consumer requests: 
		 *	a list of requests to be integrated
		 *	or disintegrated ;
		 */
		protected java.util.List	requests;

		/*
		 *	for adding/removing trajectory replacements
		 *	a t.r. instance
		 */
		private Object obj;
		 
		private Request( int type )
		{
			this.type   = type;
		}
	}

	/**
	 *	Struct class featuring
	 *	the stream data and
	 *	(privately) the requests
	 */
	public class Source
	{
		/**
		 *	Time span describing the
		 *	current first half of the stream buffers
		 */
		public Span				firstHalf		= new Span();
		/**
		 *	Time span describing the
		 *	current second half of the stream buffers
		 */
		public Span				secondHalf		= new Span();
		/**
		 *	Number of transmitters in the realtime context
		 */
		public int				numTrns;
		/**
		 *	The transmitters in the realtime context
		 */
		public Transmitter[]	transmitters;
		/**
		 *	Number of receivers in the realtime context
		 */
		public int				numRcv;
		/**
		 *	The receivers in the realtime context
		 */
		public Receiver[]		receivers;
		/**
		 *	Sense data covering the two time
		 *	spans as given by <code>firstHalf</code>
		 *	and <code>secondHalf</code>. Consumers
		 *	may only read indices which are a multiple
		 *	of their personal frameStep and only
		 *	array fields which they have requested!
		 *	Array indices are [numTrns][numRcv][bufSize]
		 */
		public float[][][]		senseBlockBuf;
		/**
		 *	Sense data covering the current offline
		 *	timeline position. Consumers
		 *	may only read
		 *	array fields which they have requested!
		 *	Array indices are [numTrns][numRcv]
		 */
		public float[][]		senseOffhand;
		/**
		 *	Trajectory data covering the two time
		 *	spans as given by <code>firstHalf</code>
		 *	and <code>secondHalf</code>. Consumers
		 *	may only read indices which are a multiple
		 *	of their personal frameStep and only
		 *	array fields which they have requested!
		 *	Array indices are [numTrns][ch][bufSize],
		 *	whery channel 0 is x and channel 1 is y coordinates.
		 */
		public float[][][]		trajBlockBuf;
		/**
		 *	Trajectory data covering  the current offline
		 *	timeline position. Consumers
		 *	may only read 
		 *	array fields which they have requested!
		 *	Array indices are [numTrns][ch],
		 *	whery channel 0 is x and channel 1 is y coordinates.
		 */
		public float[][]		trajOffhand;
		/**
		 *	Length of stream buffers
		 */
		public int				bufSize;
		/**
		 *	Length of each half of the stream buffers.
		 *	I.e. bufSize/2
		 */
		public int				bufSizeH;

		private boolean[][]		senseRequest;
		private boolean[]		trajRequest;
		private int[]			trajRplc;

		private boolean[]		trnsRequest;
		private int				minSenseStep;
		private int				doors			= 0;
	} // class Source

	public static class TrajectoryReplacement
	{
		private final java.util.List		collTransmitters;
		private final Span					span;
		private final TrajectoryGenerator	tg;
	
		public TrajectoryReplacement( TrajectoryGenerator tg, Span span, java.util.List collTransmitters )
		{
			this.collTransmitters	= collTransmitters;
			this.span				= span;
			this.tg					= tg;
		}
	}
}
