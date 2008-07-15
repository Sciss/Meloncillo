/**
 *  RealtimeTransport.java
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
 *		15-Jul-08	created from old cillo transport
 */

package de.sciss.meloncillo.realtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.gui.ProgressComponent;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.receiver.Receiver;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionCollection;
import de.sciss.meloncillo.session.SessionObject;
import de.sciss.meloncillo.timeline.TimelineEvent;
import de.sciss.meloncillo.timeline.TimelineListener;
import de.sciss.meloncillo.transmitter.Transmitter;
import de.sciss.meloncillo.util.PrefsUtil;

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
 *  @version	0.75, 15-Jul-08
 *
 *	@todo	stop shouldn't use new EditSetTimelinePosition
 *			because it makes Redos impossible.
 *	@todo	the methods for adding and removing consumers should
 *			be moved to the realtime host interface?
 */
public class RealtimeTransport
extends Thread
implements RealtimeHost, TimelineListener, PreferenceChangeListener, TransportListener
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
	
    private final Session   doc;
//	private long			loopStart, loopStop;
	
	// high level listeners
//	private final List	collTransportListeners  = new ArrayList();

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
	
	private final RealtimeContext		fakeContext = new RealtimeContext( this, Collections.EMPTY_LIST, Collections.EMPTY_LIST,
																	   new Span(), 1000 );
	
	private final Preferences			plugInPrefs;
	
	private final Object				sync	= new Object();
	
	private long timelineLen;

	// sync : call in event thread!
	/**
	 *	Creates a new transport. The thread will
	 *	be started and set to pause to await
	 *	transport commands.
	 *
	 *	@param	root	Application root
	 *	@param	doc		Session document
	 */
    public RealtimeTransport( Session doc )
    {
        super( "Transport" );
        
        this.doc    = doc;
		
		rt_producer		= new RealtimeProducer( doc, this );
		rt_context		= null;
       	
		timelineLen		= doc.timeline.getLength();
		
		// listeners
		doc.getTransmitters().addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				synchronized( sync ) {
					createContext();
				}
			}
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.getModificationType() == Transmitter.OWNER_TRAJ ) {
					synchronized( sync ) {
						if( !isRunning() ) {
							if( rt_context == null ) {
								createContext();	// will invoke offhandProduction!
// EEE
//							} else {
//								offhandProduction();
							}
						}
					}
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		});

		doc.getReceivers().addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				synchronized( sync ) {
					createContext();
				}
			}
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				switch( e.getModificationType() ) {
				case Receiver.OWNER_SENSE:
				case SessionObject.OWNER_VISUAL:
					synchronized( sync ) {
						if( !isRunning() ) {
							if( rt_context == null ) {
								createContext();	// will invoke offhandProduction!
// EEE
//							} else {
//								offhandProduction();
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
		doc.getTransport().addTransportListener( this );
		
		plugInPrefs = AbstractApplication.getApplication().getUserPrefs().node( PrefsUtil.NODE_PLUGINS );
		// fires KEY_SENSEBUFSIZE and thus causes calcSenseBufSize() and createContext() to be invoked
		new DynamicPrefChangeManager( plugInPrefs, new String[] { PrefsUtil.KEY_RTSENSEBUFSIZE },
											this ).startListening();
	
        this.setDaemon( true );
        this.setPriority( getPriority() + 1 );
		this.start();
    }

// EEE
//	/**
//	 *	Registers a new transport listener
//	 *
//	 *	@param	listener	the listener to register for information
//	 *						about transport actions such as play or stop
//	 */
//	public void addTransportListener( TransportListener listener )
//	{
//		synchronized( sync ) {
//			if( !collTransportListeners.contains( listener )) collTransportListeners.add( listener );
//		}
//	}
//
//	/**
//	 *	Unregisters a transport listener
//	 *
//	 *	@param	listener	the listener to remove from the event dispatching
//	 */
//	public void removeTransportListener( TransportListener listener )
//	{
//		synchronized( sync ) {
//			collTransportListeners.remove( listener );
//		}
//	}
//
//	// sync: to be called inside synchronized( sync ) !
//	private void dispatchStop( long pos )
//	{
//		for( int i = 0; i < collTransportListeners.size(); i++ ) {
//			try {
//				((TransportListener) collTransportListeners.get( i )).transportStop( pos );
//			}
//			catch( Exception e1 ) {
//				System.err.println( "[@"+getName()+"]" + e1.getLocalizedMessage() );
//			}
//		}
//	}
//
//	// sync: to be called inside synchronized( sync ) !
//	private void dispatchPosition( long pos )
//	{
//		for( int i = 0; i < collTransportListeners.size(); i++ ) {
//			try {
//				((TransportListener) collTransportListeners.get( i )).transportPosition( pos );
//			}
//			catch( Exception e1 ) {
//				System.err.println( "[@"+getName()+"]" + e1.getLocalizedMessage() );
//			}
//		}
//	}
//
//	// sync: to be called inside synchronized( sync ) !
//	private void dispatchPlay( long pos )
//	{
//		for( int i = 0; i < collTransportListeners.size(); i++ ) {
//			try {
//				((TransportListener) collTransportListeners.get( i )).transportPlay( pos );
//			}
//			catch( Exception e1 ) {
//				System.err.println( "[@"+getName()+"]" + e1.getLocalizedMessage() );
//			}
//		}
//	}
//
//	// sync: to be called inside synchronized( sync ) !
//	private void dispatchQuit()
//	{
//		for( int i = 0; i < collTransportListeners.size(); i++ ) {
//			try {
//				((TransportListener) collTransportListeners.get( i )).transportQuit();
//			}
//			catch( Exception e1 ) {
//				System.err.println( "[@"+getName()+"]" + e1.getLocalizedMessage() );
//			}
//		}
//	}
	
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

		synchronized( sync ) {
			for( i = 0; i < rt_numConsumers; i++ ) {
				if( rt_consumers[ i ] == consumer ) return;
			}

			// pause
			wasPlaying			= isRunning();
			if( wasPlaying ) {
				rt_command		= CMD_CONFIG_PAUSE;
				threadRunning   = false;
				sync.notifyAll();
				if( isAlive() ) {
					try {
						sync.wait();
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
					sync.notifyAll();
				} else {
//					goPlay();
					goPlay( doc.getTransport().getCurrentFrame() );
				}
			}
		} // synchronized( sync )
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
//		RealtimeConsumerRequest[]  oldRequests;
		RealtimeConsumerRequest	request;
		boolean					wasPlaying;

		synchronized( sync ) {
			for( i = 0; i < rt_numConsumers; i++ ) {
				if( rt_consumers[ i ] == consumer ) break;
			}
			if( i == rt_numConsumers ) return;
			
			// pause
			wasPlaying			= isRunning();
			if( wasPlaying ) {
				rt_command		= CMD_CONFIG_PAUSE;
				threadRunning   = false;
				sync.notifyAll();
				if( isAlive() ) {
					try {
						sync.wait();
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
					sync.notifyAll();
				} else {
//					goPlay();
					goPlay( doc.getTransport().getCurrentFrame() );
				}
			}
		} // synchronized( sync )
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
	// to be called inside synchronized( sync ) block!
	// to be called in event thread
	private void createContext()
	{
		final boolean			wasPlaying;
		final ArrayList			collRequests;
		RealtimeConsumerRequest	request;
//		RealtimeContext			newContext;
	
//		if( !doc.bird.attemptShared( Session.DOOR_TIMETRNSRCV, 250 )) {
//			destroyContext();
//			return;
//		}
//		try {
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
				sync.notifyAll();
				if( isAlive() ) {
					try {
						sync.wait();
					}
					catch( InterruptedException e1 ) {}
				}
			}

			// ------------------------- recontext ------------------------- 
			rt_context		= new RealtimeContext( this, doc.getReceivers().getAll(),
												   doc.getTransmitters().getAll(),
												   new Span( 0, doc.timeline.getLength() ),
												   doc.timeline.getRate() );
			rt_context.setSourceBlockSize( rt_senseBufSize );
			rt_producer.changeContext( rt_context );
			rt_notifyTickStep   = rt_producer.source.bufSizeH;
			collRequests		= new ArrayList( rt_numConsumers );
			for( int i = 0; i < rt_numConsumers; i++ ) {
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
// EEE
//				offhandProduction();
			}

			// resume
			if( wasPlaying ) {
				rt_command		= CMD_CONFIG_RESUME;
				threadRunning   = true;
				if( isAlive() ) {
					sync.notifyAll();
				} else {
//					goPlay();
					goPlay( doc.getTransport().getCurrentFrame() );
				}
			}
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIMETRNSRCV );
//		}
	}
	
// EEE
//	// sync : to be called within synchronized( sync )
//	// to be called in event thread
//	private void offhandProduction()
//	{
//		long	prodStart;
//		int		i;
//		
//		prodStart = doc.timeline.getPosition();
//		rt_producer.produceOffhand( prodStart );
//		for( i = 0; i < rt_numConsumers; i++ ) {
//			if( rt_requests[ i ].notifyOffhand ) {
//				rt_consumers[ i ].offhandTick( rt_context, rt_producer.source, prodStart );
//			}
//		}
//	}
	
	private void destroyContext()
	{
		int i;
	
		synchronized( sync ) {
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
		synchronized( sync ) {
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
        long			frameCount = 0, productionMask = 1;
// EEE
//      long			oldFrameCount	= 0;
        int				currentRate, i;
        double			targetRate	= 1.0; // was: int
        long			deadline	= 0; // was: long
// EEE
//		UndoableEdit	edit;
		RealtimeConsumerRequest	r;

		do {
			synchronized( sync ) {
commandLp:		do {
					switch( rt_command ) {
					case CMD_CONFIG_PAUSE:
						sync.notifyAll();
						break;
						
					case CMD_CONFIG_RESUME:
						assert startTime > 0 : startTime;
						sync.notifyAll();
						break commandLp;
						
					case CMD_STOP:
// EEE
//						dispatchStop( rt_pos );
						// translate into a valid time offset
//						if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 200 )) return;
//						try {
							rt_pos	= Math.max( 0, Math.min( timelineLen, rt_pos ));
// EEE
//							edit	= new EditSetTimelinePosition( this, doc, rt_pos );
//							doc.getUndoManager().addEdit( edit );
//						}
//						finally {
//							doc.bird.releaseExclusive( Session.DOOR_TIME );
//						}
						sync.notifyAll();
						break;
						
					case CMD_PLAY:
					case CMD_POSITION:
// EEE
//						if( rt_command == CMD_PLAY ) {
//							dispatchPlay( rt_startFrame );
//						} else {
//							dispatchPosition( rt_startFrame );
//						}
						// THRU
						targetRate		= rt_context.getSourceRate();
						// e.g. bufSizeH == 512 --> 0x1FF . Maske fuer frameCount
						productionMask  = rt_producer.source.bufSizeH - 1;
						// wir geben dem producer einen halben halben buffer zeit (in millisec)
						// d.h. bei 1000 Hz und halber buffer size von 512 sind das 256 millisec.
						deadline		= (long) (500 * rt_producer.source.bufSizeH / targetRate);
						startTime		= System.currentTimeMillis() - 1;   // division by zero vermeiden
						frameCount		= 0;
						rt_pos			= rt_startFrame;
						sync.notifyAll();
						break commandLp;
						
					case CMD_QUIT:
// EEE
//						dispatchQuit();
						sync.notifyAll();
						return;
						
					default:
						assert rt_command == CMD_IGNORE : rt_command;
						break;
					}
					// sleep until next rt_command arrives
					try {
						sync.wait();
					}
					catch( InterruptedException e1 ) {}
				} while( true );
			} // synchronized( sync )
 
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
// EEE
//					if( isLooping() ) {
//						rt_startFrame   = loopStart;
//						if( rt_startFrame >= rt_stopFrame ) {
//							goStop();
//							break rt_loop;
//						}
//// EEE
////						dispatchPosition( rt_startFrame );
//						rt_pos		= rt_startFrame;
//						startTime	= System.currentTimeMillis() - 1;
//						frameCount	= 0;
//						rt_producer.requestProduction(
//							new Span( rt_startFrame, rt_startFrame + rt_producer.source.bufSizeH ),
//							true, sysTime + deadline );
//						rt_producer.requestProduction(
//							new Span( rt_startFrame + rt_producer.source.bufSizeH,
//									  rt_startFrame + rt_producer.source.bufSize ),
//							false, sysTime + deadline );
//
//					} else {
						goStop();
						break rt_loop;
//					}
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
    
	/*
	 *  Requests the thread to start
	 *  playing. TransportListeners
	 *  are informed when the
	 *  playing really starts.
	 *
	 *  @synchronization	To be called in the event thread.
	 */
    private void goPlay( long position )
    {
		Span prodSpan;
	
        synchronized( sync ) {
			if( isRunning() ) return;
			
			if( rt_context == null ) {
				createContext();
			}
			// full buffer precalc
			rt_startFrame	= doc.timeline.getPosition();   // XXX sync?
// EEE
//			rt_stopFrame	= isLooping() && loopStop > rt_startFrame ? loopStop : doc.timeline.getLength();
			rt_stopFrame	= doc.timeline.getLength();
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
				sync.notifyAll();
			} else {
				System.err.println( "!! TRANSPORT DIED !!" );
//				start();
			}
        }
    }
    
//	/**
//	 *  Sets the loop span for playback
//	 *
//	 *  @param  loopSpan	Span describing the new loop start and stop.
//	 *						Passing null stops looping. 
//	 *
//	 *	@synchronization	If loopSpan != null, the caller must have sync on doc.timeline!
//	 */
//	public void setLoop( Span loopSpan )
//	{
//        synchronized( sync ) {
//			if( loopSpan != null ) {
//				loopStart   = loopSpan.getStart();
//				loopStop	= loopSpan.getStop();
//				looping		= true;
//				if( isRunning() && rt_pos < loopStop ) {
//					rt_stopFrame	= loopStop;
//				}
//			} else {
//				looping		= false;
//				if( isRunning() ) {
//					rt_stopFrame	= rt_context.getTimeSpan().getLength();
//				}
//			}
//		}
//	}
//
//	/**
//	 *  Returns whether looping
//	 *  is active or not
//	 *
//	 *	@return	<code>true</code> if looping is used
//	 */
//	public boolean isLooping()
//	{
//		return looping;
//	}
	
	/*
	 *  Requests the thread to stop
	 *  playing. TransportListeners
	 *  are informed when the
	 *  playing really stops.
	 */
    private void goStop()
    {
        synchronized( sync ) {
			if( !isRunning() ) return;
			
			rt_command		= CMD_STOP;
            threadRunning   = false;
            sync.notifyAll();
        }
    }

	/*
	 *  Requests the thread to stop
	 *  playing. Waits until transport
	 *  has really stopped.
	 */
    private void stopAndWait()
    {
		try {
			synchronized( sync ) {
				rt_command		= CMD_STOP;
				threadRunning   = false;
				sync.notifyAll();
				if( isAlive() ) sync.wait();
			}
		}
		catch( InterruptedException e1 ) {}
    }

	/*
	 *  Sends quit rt_command to the transport
	 *  returns only after the transport thread
	 *  stopped!
	 */
    private void quit()
    {
		try {
			synchronized( sync ) {
				rt_command		= CMD_QUIT;
				threadRunning   = false;
				sync.notifyAll();
				if( isAlive() ) sync.wait();
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
        synchronized( sync ) {
        	timelineLen = doc.timeline.getLength();
			calcSenseBufSize();
			createContext();
		}
	}

	public void timelinePositioned( TimelineEvent e )
	{
		synchronized( sync ) {
			if( isRunning() ) {
				rt_command		= CMD_CONFIG_PAUSE;
				threadRunning   = false;
				sync.notifyAll();
				if( isAlive() ) {
					try {
						sync.wait();
					}
					catch( InterruptedException e1 ) {}
				}
				// full buffer precalc
				rt_startFrame	= doc.timeline.getPosition();   // XXX sync?
// EEE
//				rt_stopFrame	= isLooping() && loopStop > rt_startFrame ? loopStop : doc.timeline.getLength();
				rt_stopFrame	= doc.timeline.getLength();
				rt_producer.produceNow( new Span( rt_startFrame,
												  rt_startFrame + rt_producer.source.bufSizeH ), true );
				rt_producer.produceNow( new Span( rt_startFrame + rt_producer.source.bufSizeH,
												  rt_startFrame + rt_producer.source.bufSize ), false );
				rt_command		= CMD_POSITION;
				threadRunning   = true;
				if( isAlive() ) {
					sync.notifyAll();
				} else {
					System.err.println( "!! TRANSPORT DIED !!" );
//					start();
				}
			} else {
				if( rt_context == null ) {
					createContext();	// will invoke offhandProduction!
// EEE
//				} else {
//					offhandProduction();
				}
			}
		} // synchronized( sync )
	}

	public void timelineSelected( TimelineEvent e ) {}
    public void timelineScrolled( TimelineEvent e ) {}

 // ------------------ TransportListener interface ------------------
    
	public void transportStop( Transport transport, long pos )
	{
		stopAndWait();
	}
	
	public void transportPosition( Transport transport, long pos, double rate )
	{
		if( rate != 1.0 ) {
			System.out.println( "WARNING: RealtimeTransport only plays at normal speed!" );
		}
		stopAndWait();
		goPlay( pos );
	}
	
	public void transportPlay( Transport transport, long pos, double rate )
	{
		if( rate != 1.0 ) {
			System.out.println( "WARNING: RealtimeTransport only plays at normal speed!" );
		}
		goPlay( pos );
	}
	
	public void transportQuit( Transport transport )
	{
		quit();
	}
	
	public void transportReadjust( Transport transport, long pos, double rate )
	{
		if( rate != 1.0 ) {
			System.out.println( "WARNING: RealtimeTransport only plays at normal speed!" );
		}
		// XXX WHAT TO DO ???
	}

// ------------------ PreferenceChangeListener interface ------------------

	// called when sensebufsize has changed
	/**
	 *	Invoked through preference changes
	 */
	public void preferenceChange( PreferenceChangeEvent pce )
	{
        synchronized( sync ) {
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
		((ProgressComponent) AbstractApplication.getApplication().getComponent( Main.COMP_MAIN )).showMessage( type, text );
	}
}