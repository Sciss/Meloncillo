/**
 *  Transport.java
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
 *		22-Jul-04   moved to realtime package. Renamed to
 *					Transport.
 *		23-Jul-04   implements RealtimeHost; widely rewritten.
 *		25-Jul-04   catches exceptions in the transport listeners
 *		28-Jul-04   tracks prefs changes
 *		01-Aug-04   bugfix in removeRealtimeConsumer
 *		16-Aug-04   debug dump action
 *		01-Sep-04	additional comments. consumer's active state now fixed
 *		02-Feb-05	bugfix : before calling offhandProduction, ensure that
 *					a realtime context exists!
 */

package de.sciss.meloncillo.realtime;

import java.awt.event.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.undo.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.receiver.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.timeline.*;
import de.sciss.meloncillo.transmitter.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.io.*;

/**
 *	The realtime "motor" or "clock". The transport
 *	deals with realtime playback of the timeline.
 *	It provides means for registering and unregistering
 *	realtime consumers and communicates with a
 *	RealtimeProducer which is responsible for the
 *	actual data production. Transort clocking is
 *	performed within an extra thread from within
 *	the consumer's methods are called and registered
 *	transport listeners are informed about actions.
 * 
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo	stop shouldn't use new EditSetTimelinePosition
 *			because it makes Redos impossible.
 *	@todo	the methods for adding and removing consumers should
 *			be moved to the realtime host interface?
 */
public class Transport
extends Thread
implements RealtimeHost, TimelineListener, LaterInvocationManager.Listener
{
	private static final int	CMD_IGNORE	= 0;
	private static final int	CMD_STOP	= 1;
	private static final int	CMD_PLAY	= 2;
	private static final int	CMD_QUIT	= -1;

	private static final int	CMD_CONFIG_PAUSE	= 4;	// pause but don't tell the listeners
	private static final int	CMD_CONFIG_RESUME	= 5;	// go on where we stopped but don't tell the listeners
	private static final int	CMD_POSITION		= 6;	// go on at the context's timespan start

	// low level threading
    private boolean threadRunning   = false;
    private	boolean looping			= false;
	private int		rt_command		= CMD_IGNORE;
	
	private final Main		root;
    private final Session   doc;
	private long			loopStart, loopStop;
	
	// high level listeners
	private final ArrayList	collTransportListeners  = new ArrayList();

	// realtime control
	private RealtimeContext				rt_context;
	private RealtimeProducer			rt_producer;
	private RealtimeConsumer[]			rt_consumers		= new RealtimeConsumer[ 4 ];	// array will grow
	private RealtimeConsumerRequest[]	rt_requests			= new RealtimeConsumerRequest[ 4 ]; // ...automatically
	private int							rt_numConsumers		= 0;
	private int							rt_notifyTickStep;
	private long						rt_startFrame;
	private long						rt_stopFrame;
	private long						rt_pos;
	private int							rt_senseBufSize;
	
	private final RealtimeContext		fakeContext = new RealtimeContext( this, new Vector(), new Vector(),
																	   new Span(), 1000 );
	
	private final Preferences			plugInPrefs;

	// sync : call in event thread!
	/**
	 *	Creates a new transport. The thread will
	 *	be started and set to pause to await
	 *	transport commands.
	 *
	 *	@param	root	Application root
	 *	@param	doc		Session document
	 */
    public Transport( Main root, Session doc )
    {
        super( "Transport" );
        
		this.root   = root;
        this.doc    = doc;
		
		rt_producer		= new RealtimeProducer( root, doc, this );
		rt_context		= null;
//		calcSenseBufSize();
//		createContext();
        
		final Transport enc_this = this;
		
		// listeners
		doc.transmitters.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				synchronized( enc_this ) {
					createContext();
				}
			}
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.getModificationType() == Transmitter.OWNER_TRAJ ) {
					synchronized( enc_this ) {
						if( !isRunning() ) {
							if( rt_context == null ) {
								createContext();	// will invoke offhandProduction!
							} else {
								offhandProduction();
							}
						}
					}
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		});

		doc.receivers.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				synchronized( enc_this ) {
					createContext();
				}
			}
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				switch( e.getModificationType() ) {
				case Receiver.OWNER_SENSE:
				case SessionObject.OWNER_VISUAL:
					synchronized( enc_this ) {
						if( !isRunning() ) {
							if( rt_context == null ) {
								createContext();	// will invoke offhandProduction!
							} else {
								offhandProduction();
							}
						}
					}
					break;
				
				default:
					break;
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		});
		
		doc.timeline.addTimelineListener( this );
		plugInPrefs = AbstractApplication.getApplication().getUserPrefs().node( PrefsUtil.NODE_PLUGINS );
		// fires KEY_SENSEBUFSIZE and thus causes calcSenseBufSize() and createContext() to be invoked
		new DynamicPrefChangeManager( plugInPrefs, new String[] { PrefsUtil.KEY_RTSENSEBUFSIZE },
											this ).startListening();
	
        this.setDaemon( true );
        this.setPriority( getPriority() + 1 );
		this.start();
// System.err.println( "transport pri : "+getPriority() );
    }

	/**
	 *	Registers a new transport listener
	 *
	 *	@param	listener	the listener to register for information
	 *						about transport actions such as play or stop
	 */
	public void addTransportListener( TransportListener listener )
	{
		synchronized( this ) {
			if( !collTransportListeners.contains( listener )) collTransportListeners.add( listener );
		}
	}

	/**
	 *	Unregisters a transport listener
	 *
	 *	@param	listener	the listener to remove from the event dispatching
	 */
	public void removeTransportListener( TransportListener listener )
	{
		synchronized( this ) {
			collTransportListeners.remove( listener );
		}
	}

	// sync: to be called inside synchronized( this ) !
	private void dispatchStop( long pos )
	{
		for( int i = 0; i < collTransportListeners.size(); i++ ) {
			try {
				((TransportListener) collTransportListeners.get( i )).transportStop( pos );
			}
			catch( Exception e1 ) {
				System.err.println( "[@"+getName()+"]" + e1.getLocalizedMessage() );
			}
		}
	}

	// sync: to be called inside synchronized( this ) !
	private void dispatchPosition( long pos )
	{
		for( int i = 0; i < collTransportListeners.size(); i++ ) {
			try {
				((TransportListener) collTransportListeners.get( i )).transportPosition( pos );
			}
			catch( Exception e1 ) {
				System.err.println( "[@"+getName()+"]" + e1.getLocalizedMessage() );
			}
		}
	}

	// sync: to be called inside synchronized( this ) !
	private void dispatchPlay( long pos )
	{
		for( int i = 0; i < collTransportListeners.size(); i++ ) {
			try {
				((TransportListener) collTransportListeners.get( i )).transportPlay( pos );
			}
			catch( Exception e1 ) {
				System.err.println( "[@"+getName()+"]" + e1.getLocalizedMessage() );
			}
		}
	}

	// sync: to be called inside synchronized( this ) !
	private void dispatchQuit()
	{
		for( int i = 0; i < collTransportListeners.size(); i++ ) {
			try {
				((TransportListener) collTransportListeners.get( i )).transportQuit();
			}
			catch( Exception e1 ) {
				System.err.println( "[@"+getName()+"]" + e1.getLocalizedMessage() );
			}
		}
	}
	
	public void addTrajectoryReplacement( RealtimeProducer.TrajectoryReplacement tr )
	{
		rt_producer.requestAddTrajectoryReplacement( tr );
	}

	public void removeTrajectoryReplacement( RealtimeProducer.TrajectoryReplacement tr )
	{
		rt_producer.requestRemoveTrajectoryReplacement( tr );
	}
	
	/**
	 *	Registers a new realtime consumer.
	 *	If transport is running, it will be interrupted briefly
	 *	and the realtime producer is reconfigured on the fly.
	 *
	 *	@param	consumer	the consumer to add to the realtime process.
	 *						it's <code>createRequest</code> will be
	 *						called to query the profile.
	 *
	 *	@synchronization	to be called from event thread
	 *
	 *	@see	RealtimeConsumer#createRequest( RealtimeContext )
	 *	@see	RealtimeProducer#requestAddConsumerRequest( RealtimeConsumerRequest )
	 */
	public void addRealtimeConsumer( RealtimeConsumer consumer )
	{
		int						i;
		RealtimeConsumerRequest[]  oldRequests;
		RealtimeConsumer[]		oldConsumers;
		RealtimeConsumerRequest	request;
		boolean					wasPlaying;

		synchronized( this ) {
			for( i = 0; i < rt_numConsumers; i++ ) {
				if( rt_consumers[ i ] == consumer ) return;
			}

			// pause
			wasPlaying			= isRunning();
			if( wasPlaying ) {
				rt_command		= CMD_CONFIG_PAUSE;
				threadRunning   = false;
				notifyAll();
				if( isAlive() ) {
					try {
						wait();
					}
					catch( InterruptedException e1 ) {}
				}
			}
			
			// add
			if( rt_numConsumers == rt_consumers.length ) {
				oldConsumers	= rt_consumers;
				rt_consumers	= new RealtimeConsumer[ rt_numConsumers + 5 ];
				System.arraycopy( oldConsumers, 0, rt_consumers, 0, rt_numConsumers );
				oldRequests		= rt_requests;
				rt_requests		= new RealtimeConsumerRequest[ rt_numConsumers + 5 ];
				System.arraycopy( oldRequests, 0, rt_requests, 0, rt_numConsumers );
			}
			rt_consumers[ rt_numConsumers ] = consumer;

			if( rt_context != null ) {  // add new request on the fly
				request	= consumer.createRequest( rt_context );
				rt_requests[ rt_numConsumers ] = request;
				if( request.notifyTicks ) {
					rt_notifyTickStep = Math.min( rt_notifyTickStep, request.notifyTickStep );
				}
				rt_producer.requestAddConsumerRequest( request );
			} else {
				rt_requests[ rt_numConsumers ] = null;
			}
			
			rt_numConsumers++;

			// resume
			if( wasPlaying ) {
				rt_command		= CMD_CONFIG_RESUME;
				threadRunning   = true;
				if( isAlive() ) {
					notifyAll();
				} else {
					goPlay();
				}
			}
		} // synchronized( this )
	}

	/**
	 *	Unregisters a realtime consumer.
	 *	If transport is running, it will be interrupted briefly
	 *	and the realtime producer is reconfigured on the fly.
	 *
	 *	@param	consumer	the consumer to remove from the realtime process.
	 *
	 *	@synchronization	to be called from event thread
	 *
	 *	@see	RealtimeProducer#requestRemoveConsumerRequest( RealtimeConsumerRequest )
	 */
	public void removeRealtimeConsumer( RealtimeConsumer consumer )
	{
		int						i;
		RealtimeConsumerRequest	request;
		boolean					wasPlaying;

		synchronized( this ) {
			for( i = 0; i < rt_numConsumers; i++ ) {
				if( rt_consumers[ i ] == consumer ) break;
			}
			if( i == rt_numConsumers ) return;
			
			// pause
			wasPlaying			= isRunning();
			if( wasPlaying ) {
				rt_command		= CMD_CONFIG_PAUSE;
				threadRunning   = false;
				notifyAll();
				if( isAlive() ) {
					try {
						wait();
					}
					catch( InterruptedException e1 ) {}
				}
			}
			
			// remove
			request	= rt_requests[ i ];
			System.arraycopy( rt_consumers, i + 1, rt_consumers, i, rt_numConsumers - i - 1 );
			System.arraycopy( rt_requests,  i + 1, rt_requests,  i, rt_numConsumers - i - 1 );

			rt_numConsumers--;
			rt_consumers[ rt_numConsumers ] = null;
			rt_requests[ rt_numConsumers ]  = null;

			if( rt_context != null ) {
				// eventuell wieder hoeher gehen
				if( request.notifyTicks && request.notifyTickStep <= rt_notifyTickStep ) {
					rt_notifyTickStep = rt_producer.source.bufSizeH;
					for( i = 0; i < rt_numConsumers; i++ ) {
						if( rt_requests[ i ].notifyTicks ) {
							rt_notifyTickStep = Math.min( rt_notifyTickStep, rt_requests[ i ].notifyTickStep );
						}
					}
				}
				rt_producer.requestRemoveConsumerRequest( request );
			}

			// resume
			if( wasPlaying ) {
				rt_command		= CMD_CONFIG_RESUME;
				threadRunning   = true;
				if( isAlive() ) {
					notifyAll();
				} else {
					goPlay();
				}
			}
		} // synchronized( this )
	}
	
	private void calcSenseBufSize()
	{
		int optimum, above, below;
		
		optimum = Math.max( 16, (int) (((double) plugInPrefs.getInt( PrefsUtil.KEY_RTSENSEBUFSIZE, 0 )
										/ 1000.0) * doc.timeline.getRate() + 0.5) );
		// muss 2er potenz sein
		for( above = 2; above < optimum; above <<= 1 ) ;
		below	= above >> 1;
	
		if( (double) above / optimum <= (double) optimum / below ) {
			rt_senseBufSize = above;
		} else {
			rt_senseBufSize = below;
		}
	}

	// will sync shared on timetrnsrcv
	// to be called inside synchronized( this ) block!
	// to be called in event thread
	private void createContext()
	{
		int						i;
		boolean					wasPlaying;
		RealtimeConsumerRequest	request;
		ArrayList				collRequests;
//		RealtimeContext			newContext;
	
		if( !doc.bird.attemptShared( Session.DOOR_TIMETRNSRCV, 250 )) {
			destroyContext();
			return;
		}
		try {
//			// check if new context is needed
//			newContext	= new RealtimeContext( this, doc.receivers.getAll(),
//											   doc.transmitterCollection.getAll(),
//											   new Span( 0, doc.timeline.getLength() ),
//											   doc.timeline.getRate() );
//			if( rt_context != null &&
//				newContext.getTransmitters().equals( rt_context.getTransmitter() ) &&
//				newContext.getReceivers().equals( rt_context.getReceivers() ) &&
//				newContext.getTimeSpan().equals( rt_context.getTimeSpan() ) &&
//				newContext.getSourceRate().equals( rt_context.getSourceRate() )
//			}
		
			// pause
			wasPlaying			= isRunning();
			if( wasPlaying ) {
				rt_command		= CMD_CONFIG_PAUSE;
				threadRunning   = false;
				notifyAll();
				if( isAlive() ) {
					try {
						wait();
					}
					catch( InterruptedException e1 ) {}
				}
			}

			// ------------------------- recontext ------------------------- 
			rt_context		= new RealtimeContext( this, doc.receivers.getAll(),
												   doc.transmitters.getAll(),
												   new Span( 0, doc.timeline.getLength() ),
												   doc.timeline.getRate() );
			rt_context.setSourceBlockSize( rt_senseBufSize );
			rt_producer.changeContext( rt_context );
			rt_notifyTickStep   = rt_producer.source.bufSizeH;
			collRequests		= new ArrayList( rt_numConsumers );
			for( i = 0; i < rt_numConsumers; i++ ) {
				request			= rt_consumers[ i ].createRequest( rt_context );
				rt_requests[ i ]= request;
				if( request.notifyTicks ) {
					rt_notifyTickStep = Math.min( rt_notifyTickStep, request.notifyTickStep );
				}
				collRequests.add( request );
			}
			if( wasPlaying ) {
				rt_producer.requestAddConsumerRequests( collRequests );
			} else {
				rt_producer.addConsumerRequestsNow( collRequests );
				activateConsumers( collRequests );
				offhandProduction();
			}

			// resume
			if( wasPlaying ) {
				rt_command		= CMD_CONFIG_RESUME;
				threadRunning   = true;
				if( isAlive() ) {
					notifyAll();
				} else {
					goPlay();
				}
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIMETRNSRCV );
		}
	}
	
	// sync : to be called within synchronized( this )
	// to be called in event thread
	private void offhandProduction()
	{
		long	prodStart;
		int		i;
		
		prodStart = doc.timeline.getPosition();
		rt_producer.produceOffhand( prodStart );
		for( i = 0; i < rt_numConsumers; i++ ) {
			if( rt_requests[ i ].notifyOffhand ) {
				rt_consumers[ i ].offhandTick( rt_context, rt_producer.source, prodStart );
			}
		}
	}

//	private void offhandProduction()
//	{
//		long	prodStart;
//		Span	prodSpan;
//		int		i;
//		
//		prodStart = doc.timeline.getPosition();
//		if( rt_producer.source.firstHalf.contains( prodStart )) {
//			prodSpan = rt_producer.source.firstHalf;
//		} else if( rt_producer.source.secondHalf.contains( prodStart )) {
//			prodSpan = rt_producer.source.secondHalf;
//		} else {
//			prodStart = Math.max( 0, prodStart - (rt_producer.source.bufSizeH >> 1) );
//			prodSpan  = new Span( prodStart, prodStart + rt_producer.source.bufSizeH );
//		}
//		rt_producer.produceNow( prodSpan );
//		for( i = 0; i < rt_numConsumers; i++ ) {
//			rt_consumers[ i ].realtimeTick( rt_context, rt_producer.source, prodStart );
//		}
//	}

	private void destroyContext()
	{
		int i;
	
		synchronized( this ) {
			if( isRunning() ) stopAndWait();
			
			rt_producer.changeContext( fakeContext );
			rt_context = null;
			for( i = 0; i < rt_requests.length; i++ ) {
				rt_requests[ i ] = null;
			}
		}
	}

	/**
	 *	Returns the current realtime context.
	 *	If no such context exists, it will be
	 *	created adhoc.
	 *
	 *	@return	the current realtime context
	 */
	public RealtimeContext getContext()
	{
		synchronized( this ) {
			if( rt_context == null ) {
				createContext();
			}
			return rt_context;
		}
	}

	/**
	 *	The transport core is
	 *	executed within the thread's run method
	 */
    public void run()
    {
		// all initial values are just here to please the compiler
		// who doesn't know commandLp is exited only after at least
		// one CMD_PLAY (see assertion in CMD_CONFIG_RESUME)
        long			startTime = 0, sysTime;
        long			frameCount = 0, productionMask = 1, deadline = 0;
        int				currentRate, targetRate = 1, i;
		UndoableEdit	edit;
		RealtimeConsumerRequest	r;

		do {
			synchronized( this ) {
commandLp:		do {
					switch( rt_command ) {
					case CMD_CONFIG_PAUSE:
						notifyAll();
						break;
						
					case CMD_CONFIG_RESUME:
						assert startTime > 0 : startTime;
						notifyAll();
						break commandLp;
						
					case CMD_STOP:
						dispatchStop( rt_pos );
						// translate into a valid time offset
						if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 200 )) return;
						try {
							rt_pos	= Math.max( 0, Math.min( doc.timeline.getLength(), rt_pos ));
							edit	= new EditSetTimelinePosition( this, doc, rt_pos );
							doc.getUndoManager().addEdit( edit );
						}
						finally {
							doc.bird.releaseExclusive( Session.DOOR_TIME );
						}
						notifyAll();
						break;
						
					case CMD_PLAY:
					case CMD_POSITION:
						if( rt_command == CMD_PLAY ) {
							dispatchPlay( rt_startFrame );
						} else {
							dispatchPosition( rt_startFrame );
						}
						// THRU
						targetRate		= rt_context.getSourceRate();
						// e.g. bufSizeH == 512 --> 0x1FF . Maske fuer frameCount
						productionMask  = rt_producer.source.bufSizeH - 1;
						// wir geben dem producer einen halben halben buffer zeit (in millisec)
						// d.h. bei 1000 Hz und halber buffer size von 512 sind das 256 millisec.
						deadline		= 500 * rt_producer.source.bufSizeH / targetRate;
						startTime		= System.currentTimeMillis() - 1;   // division by zero vermeiden
						frameCount		= 0;
//						oldFrameCount	= frameCount;
						rt_pos			= rt_startFrame;
						notifyAll();
						break commandLp;
						
					case CMD_QUIT:
						dispatchQuit();
						notifyAll();
						return;
						
					default:
						assert rt_command == CMD_IGNORE : rt_command;
						break;
					}
					// sleep until next rt_command arrives
					try {
						wait();
					}
					catch( InterruptedException e1 ) {}
				} while( true );
			} // synchronized( this )
 
//			// warten um division by zero zu vermeiden
//			while( System.currentTimeMillis() == startTime ) {
//				yield();
//			}
 
rt_loop:	while( threadRunning ) {
				frameCount += rt_notifyTickStep;
				rt_pos	   += rt_notifyTickStep;
				sysTime		= System.currentTimeMillis();
				currentRate = (int) (1000 * frameCount / (sysTime - startTime));
				while( currentRate > targetRate ) { // wir sind der zeit voraus
					yield();
					sysTime		= System.currentTimeMillis();
					currentRate = (int) (1000 * frameCount / (sysTime - startTime));
				}

				// handle stop + loop
				if( rt_pos >= rt_stopFrame ) {
					if( isLooping() ) {
						rt_startFrame   = loopStart;
						if( rt_startFrame >= rt_stopFrame ) {
							goStop();
							break rt_loop;
						}
						dispatchPosition( rt_startFrame );
						rt_pos		= rt_startFrame;
						startTime	= System.currentTimeMillis() - 1;
						frameCount	= 0;
						rt_producer.requestProduction(
							new Span( rt_startFrame, rt_startFrame + rt_producer.source.bufSizeH ),
							true, sysTime + deadline );
						rt_producer.requestProduction(
							new Span( rt_startFrame + rt_producer.source.bufSizeH,
									  rt_startFrame + rt_producer.source.bufSize ),
							false, sysTime + deadline );

					} else {
						goStop();
						break rt_loop;
					}
				}
				
				for( i = 0; i < rt_numConsumers; i++ ) {
					// XXX performativer mit bitshifted mask + AND ?
					r = rt_requests[ i ];
					if( r.active && r.notifyTicks && (frameCount % r.notifyTickStep == 0) ) {
						rt_consumers[ i ].realtimeTick( rt_context, rt_producer.source, rt_pos );
					}
				}

				// this has to be called after the consumer's business
				// because the rt thread might be yielded and the
				// the producer will be faster so that the last frame
				// of each buffer half gets read in a wrong way
  				if( (frameCount & productionMask) == 0 ) {
					// (frameCount & rt_producer.source.bufSizeH) != 0 entspricht
					// ((frameCount+rt_producer.source.bufSizeH) & rt_producer.source.bufSizeH) == 0
					// d.h. dem zukuenftigen evenOdd!
					rt_producer.requestProduction( new Span( rt_pos + rt_producer.source.bufSizeH,
															 rt_pos + rt_producer.source.bufSize ),
												   (frameCount & rt_producer.source.bufSizeH) != 0,
												   sysTime + deadline );
				}
				
//				yield();
				try {
					sleep( 0, 1 );
				} catch( InterruptedException e1 ) {}
			} // while( threadRunning )
		} while( true );
    }
    
	/**
	 *  Requests the thread to start
	 *  playing. TransportListeners
	 *  are informed when the
	 *  playing really starts.
	 *
	 *  @synchronization	To be called in the event thread.
	 */
    public void goPlay()
    {
		Span prodSpan;
	
        synchronized( this ) {
			if( isRunning() ) return;
			
			if( rt_context == null ) {
				createContext();
			}
			// full buffer precalc
			rt_startFrame	= doc.timeline.getPosition();   // XXX sync?
			rt_stopFrame	= isLooping() && loopStop > rt_startFrame ? loopStop : doc.timeline.getLength();
			prodSpan		= new Span( rt_startFrame, rt_startFrame + rt_producer.source.bufSizeH );
			rt_producer.produceNow( prodSpan, true );
			invokeRealtimeBlock( true );
			prodSpan		= new Span( rt_startFrame + rt_producer.source.bufSizeH,
									    rt_startFrame + rt_producer.source.bufSize );
			rt_producer.produceNow( prodSpan, false );
			invokeRealtimeBlock( false );
			rt_command		= CMD_PLAY;
			threadRunning   = true;
			if( isAlive() ) {
				notifyAll();
			} else {
				System.err.println( "!! TRANSPORT DIED !!" );
//				start();
			}
        }
    }
    
	/**
	 *  Sets the loop span for playback
	 *
	 *  @param  loopSpan	Span describing the new loop start and stop.
	 *						Passing null stops looping. 
	 *
	 *	@synchronization	If loopSpan != null, the caller must have sync on doc.timeline!
	 */
	public void setLoop( Span loopSpan )
	{
        synchronized( this ) {
			if( loopSpan != null ) {
				loopStart   = loopSpan.getStart();
				loopStop	= loopSpan.getStop();
				looping		= true;
				if( isRunning() && rt_pos < loopStop ) {
					rt_stopFrame	= loopStop;
				}
			} else {
				looping		= false;
				if( isRunning() ) {
					rt_stopFrame	= rt_context.getTimeSpan().getLength();
				}
			}
		}
	}

	/**
	 *  Returns whether looping
	 *  is active or not
	 *
	 *	@return	<code>true</code> if looping is used
	 */
	public boolean isLooping()
	{
		return looping;
	}
	
	/**
	 *  Requests the thread to stop
	 *  playing. TransportListeners
	 *  are informed when the
	 *  playing really stops.
	 */
    public void goStop()
    {
        synchronized( this ) {
			if( !isRunning() ) return;
			
			rt_command		= CMD_STOP;
            threadRunning   = false;
            notifyAll();
        }
    }

	/**
	 *  Requests the thread to stop
	 *  playing. Waits until transport
	 *  has really stopped.
	 */
    public void stopAndWait()
    {
		try {
			synchronized( this ) {
				rt_command		= CMD_STOP;
				threadRunning   = false;
				notifyAll();
				if( isAlive() ) wait();
			}
		}
		catch( InterruptedException e1 ) {}
    }

	/**
	 *  Sends quit rt_command to the transport
	 *  returns only after the transport thread
	 *  stopped!
	 */
    public void quit()
    {
		try {
			synchronized( this ) {
				rt_command		= CMD_QUIT;
				threadRunning   = false;
				notifyAll();
				if( isAlive() ) wait();
//System.err.println( "transport stopped" );
			}
		}
		catch( InterruptedException e1 ) {}
    }

	// invoke in event thread!
	private void invokeRealtimeBlock( boolean even )
	{
		for( int i = 0; i < rt_numConsumers; i++ ) {
			if( rt_requests[ i ].notifyBlocks ) {
				rt_consumers[ i ].realtimeBlock( rt_context, rt_producer.source, even );
			}
		}
	}
	
	/**
	 *  Gets an Action object that will dump the
	 *  current realtime consumers to the console.
	 *
	 *  @return <code>Action</code> suitable for attaching to a <code>JMenuItem</code>.
	 */
	public Action getDebugDumpAction()
	{
		return new actionDebugDump();
	}
	
	// call in event thread
	private void activateConsumers( java.util.List requests )
	{
		int i, j;
		RealtimeConsumerRequest	rcr;
		
		for( j = 0; j < requests.size(); j++ ) {
			rcr = (RealtimeConsumerRequest) requests.get( j );
ourLp:		for( i = 0; i < rt_numConsumers; i++ ) {
				if( rt_requests[ i ] == rcr ) {
					rt_requests[ i ].active = true;
					break ourLp;
				}
			}
		}
	}
	
// ---------------- TimelineListener interface ---------------- 

	public void timelineChanged( TimelineEvent e )
	{
        synchronized( this ) {
			calcSenseBufSize();
			createContext();
		}
	}

	public void timelinePositioned( TimelineEvent e )
	{
		synchronized( this ) {
			if( isRunning() ) {
				rt_command		= CMD_CONFIG_PAUSE;
				threadRunning   = false;
				notifyAll();
				if( isAlive() ) {
					try {
						wait();
					}
					catch( InterruptedException e1 ) {}
				}
				// full buffer precalc
				rt_startFrame	= doc.timeline.getPosition();   // XXX sync?
				rt_stopFrame	= isLooping() && loopStop > rt_startFrame ? loopStop : doc.timeline.getLength();
				rt_producer.produceNow( new Span( rt_startFrame,
												  rt_startFrame + rt_producer.source.bufSizeH ), true );
				rt_producer.produceNow( new Span( rt_startFrame + rt_producer.source.bufSizeH,
												  rt_startFrame + rt_producer.source.bufSize ), false );
				rt_command		= CMD_POSITION;
				threadRunning   = true;
				if( isAlive() ) {
					notifyAll();
				} else {
					System.err.println( "!! TRANSPORT DIED !!" );
//					start();
				}
			} else {
				if( rt_context == null ) {
					createContext();	// will invoke offhandProduction!
				} else {
					offhandProduction();
				}
			}
		} // synchronized( this )
	}

	public void timelineSelected( TimelineEvent e ) {}
    public void timelineScrolled( TimelineEvent e ) {}

// ------------------ LaterInvocationManager.Listener interface ------------------

	// called when sensebufsize has changed
	/**
	 *	Invoked through preference changes
	 */
	public void laterInvocation( Object o )
	{
        synchronized( this ) {
			calcSenseBufSize();
			createContext();
		}
	}

// ------------------ RealtimeHost interface ------------------

	public void notifyConsumed( RealtimeProducer.Request r )
	{
		switch( r.type ) {
		case RealtimeProducer.TYPE_PRODUCE:		// XXX sync ?
			invokeRealtimeBlock( r.even );
			break;
		case RealtimeProducer.TYPE_ADDCONFIG:
			activateConsumers( r.requests );
			break;
		case RealtimeProducer.TYPE_REMOVECONFIG:
			// nothing to do
			break;
		default:
			break;
		}
	}

// --------------- RealtimeHost interface ---------------

	/**
	 *  Returns whether the
	 *  thread is currently playing
	 *
	 *	@return	<code>true</code> if the transport is currently playing
	 */
	public boolean isRunning()
	{
		return( isAlive() && threadRunning );
	}

	public void	showMessage( int type, String text )
	{
		((ProgressComponent) root.getComponent( Main.COMP_MAIN )).showMessage( type, text );
	}

// --------------- internal actions ---------------

	private class actionDebugDump extends AbstractAction
	{
		private actionDebugDump()
		{
			super( "Dump Realtime Config" );
		}

		public void actionPerformed( ActionEvent e )
		{
			int						i, trnsIdx, rcvIdx;
			boolean					requestSense, requestTraj;
			RealtimeConsumerRequest req;

			synchronized( root.transport ) {
				System.err.println( "List of realtime consumers:\n" );
				for( i = 0; i < rt_numConsumers; i++ ) {
					System.err.println( "  "+rt_consumers[ i ].getClass().getName() );
					requestSense	= false;
					requestTraj		= false;
					req				= rt_requests[ i ];
schoko:				for( trnsIdx = 0; trnsIdx < req.numTrns; trnsIdx++ ) {
						for( rcvIdx = 0; rcvIdx < req.numRcv; rcvIdx++ ) {
							if( req.senseRequest[ trnsIdx ][ rcvIdx ]) {
								requestSense = true;
								break schoko;
							}
						}
					}
					for( trnsIdx = 0; trnsIdx < req.numTrns; trnsIdx++ ) {
						if( req.trajRequest[ trnsIdx ]) {
							requestTraj = true;
							break;
						}
					}
					System.err.print( "    "+(requestTraj ? "traj " : "")+(requestSense ? "sense " : "") );
					if( requestTraj || requestSense ) System.err.print( "frameStep="+req.frameStep+" " );
					if( req.notifyTicks )   System.err.print( "notifyTickStep="+req.notifyTickStep+" " );
					if( req.notifyBlocks )  System.err.print( "blocks " );
					if( req.notifyOffhand )	System.err.print( "offhand " );
					if( !req.active )		System.err.print( "[inactive] " );
					System.err.println();
				}
				
				System.err.println( "\ntotal notifyTickStep = "+rt_notifyTickStep+"; senseBufSize = "+rt_senseBufSize );
			} // synchronized( root.transport )
		}
	}
}