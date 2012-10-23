/*
 *  LispRenderPlugIn.java
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
 *		19-Jul-04   created from original LispBounce,
 *					adds support for a consumer providing host.
 *		24-Jul-04   extends LispPlugIn
 *		02-Sep-04	commented
 *		01-Jan-05	added online help
 */

// XXX TO-DO : changing prefs (senserate or bufsize) between fillGUI + beginRender might not
//				be recognized!
// DISKBUFSIZE hash entry should be removed ?
// should listen to preference changes (e.g. CSOUNDAPP)

package de.sciss.meloncillo.render;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JOptionPane;

import org.jatha.dynatype.LispNumber;
import org.jatha.dynatype.LispValue;
import org.jatha.machine.SECDMachine;

import de.sciss.app.AbstractApplication;
import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.Span;
import de.sciss.meloncillo.lisp.AdvancedJatha;
import de.sciss.meloncillo.lisp.BasicLispPrimitive;
import de.sciss.meloncillo.plugin.LispPlugIn;
import de.sciss.meloncillo.plugin.PlugInContext;
import de.sciss.meloncillo.util.PrefsUtil;

/**
 *  Common Lisp Script driven rendering
 *	plug-in. This handles the producer
 *	methods but adds some abstract methods
 *	<code>invokeLisp...</code> which subclasses
 *	can use to implement specific behaviour.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public abstract class LispRenderPlugIn
extends LispPlugIn
implements RenderPlugIn
{
	private LRPProgressionSetPrimitive	progressionSetPrimitive;

	private static final String	KEY_RENDERINFO	= LispRenderPlugIn.class.getName();
	
	/**
	 *	Simply calls the superclass constructor
	 */
	protected LispRenderPlugIn()
	{
		super();
	}
	
	/**
	 *	Adds new specific lisp functions
	 *	to the environment: "(progression-set)"
	 *	to update the progress bar.
	 */
	protected void createSpecialPrimitives( AdvancedJatha lisp )
	{
		progressionSetPrimitive = new LRPProgressionSetPrimitive( lisp );
		lisp.addPrimitive( progressionSetPrimitive );
	}

	/**
	 *	No additional symbols are added at the moment
	 */
	protected void createSpecialSymbols( AdvancedJatha lisp, PlugInContext context )
	{
		// no special symbols
	}

	// --- Bouncing ---
	
	/**
	 *	Calls <code>plugInPrepare</code>, then
	 *	<code>invokeLispPrepare</code> and scans
	 *	all resulting stream data source requests.
	 *	If the subclass was setting the <code>KEY_CONSUMER</code>
	 *	option in the context, it also deals with target requests.
	 */
	public boolean producerBegin( RenderContext context, RenderSource source )
	throws IOException
	{
		if( !plugInPrepare( context )) return false;
	
		int							trnsIdx, rcvIdx, senseBufSize;
		double						senseRate;
		final RenderInfo			info		= new RenderInfo();
		List						collRequests;
		Request						r;
		boolean						success		= false;
		de.sciss.app.Application	app			= AbstractApplication.getApplication();
		
		context.moduleMap.put( KEY_RENDERINFO, info );
		progressionSetPrimitive.setRenderHost( (RenderHost) context.getHost() );
		info.verbose	= classPrefs.getBoolean( KEY_VERBOSE, false );
		info.consumer   = (RenderConsumer) context.getOption( RenderContext.KEY_CONSUMER );

		if( info.consumer != null ) {
			info.produceWeight  = 0.2f;
			info.consumeWeight  = 0.2f;
		} else {
			info.produceWeight  = 0.25f;
			info.consumeWeight  = 0.0f;
		}
		info.consumeOffset		= 1.0f - info.produceWeight - info.consumeWeight;
		progressionSetPrimitive.setOffsetAndWeight( info.produceWeight, info.consumeOffset );

		senseRate		= context.getSourceRate();
		senseBufSize	= Math.max( 16, (int) (((double) app.getUserPrefs().node( PrefsUtil.NODE_PLUGINS ).getInt(
									PrefsUtil.KEY_OLSENSEBUFSIZE, 0 ) / 1000.0) *
									senseRate + 0.5) ) & ~1;  // muss durch zwei teilbar sein!
		prefsHash.setf_gethash( jatha.makeString( "SENSEBUFSIZE" ), jatha.makeInteger( senseBufSize ));
		prefsHash.setf_gethash( jatha.makeString( "SENSERATE" ), jatha.makeReal( senseRate ));

		try {
			if( !invokeLispPrepare( context, source, info )) return false;
			// ---- satisfy requests ----
			info.senseRate  = (double) context.getSourceRate(); // overriden by RESAMPLE request below
			// in the first pass all sense data is written to a temporary
			// audio file which then can be read by Lisp
			// in pass two.
			info.afTrajTargets		= new AudioFile[ source.numTrns ];
			info.afSenseTargets		= new AudioFile[ source.numTrns ][ source.numRcv ];
			collRequests			= sourceRequestPrimitive.getRequests();
			for( int i = 0; i < collRequests.size(); i++ ) {
				r = (Request) collRequests.get( i );
				switch( r.type ) {
				case REQUEST_RESAMPLE:
					context.setOption( RenderContext.KEY_TARGETRATE, r.params );
					info.senseRate	= ((Number) r.params).doubleValue();
					if( info.verbose ) System.out.println( "request resample : "+info.senseRate );
					break;
				
				case REQUEST_TRAJ:
					trnsIdx = ((Number) r.params).intValue();
					if( trnsIdx < 0 || trnsIdx >= source.numTrns ) {
						context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
							app.getResourceString( "errRenderSourceRequest" ) + " : " + trnsIdx );
						return false;
					}
					if( !(r.medium instanceof AudioFile) ) {
						context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
							app.getResourceString( "errRenderTargetObject" ) + " : " + r.medium );
						return false;
					}
					
					info.afTrajTargets[ trnsIdx]	= (AudioFile) r.medium;
					source.trajRequest[ trnsIdx ]   = true;
					if( info.verbose ) System.out.println( "request input traj : trnsidx "+trnsIdx );
					break;
					
				case REQUEST_SENSE:
					trnsIdx = ((Point) r.params).x;
					rcvIdx  = ((Point) r.params).y;
					if( trnsIdx < 0 || trnsIdx >= source.numTrns || rcvIdx < 0 || rcvIdx >= source.numRcv ) {
						context.getHost().showMessage( JOptionPane.ERROR_MESSAGE, app.getResourceString(
							"errRenderSourceRequest" ) + " : " + trnsIdx + ", " + rcvIdx );
						return false;
					}
					if( !(r.medium instanceof AudioFile) ) {
						context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
							app.getResourceString( "errRenderTargetObject" ) + " : " + r.medium );
						return false;
					}
					
					info.afSenseTargets[ trnsIdx][ rcvIdx ]	 = (AudioFile) r.medium;
					source.senseRequest[ trnsIdx ][ rcvIdx ] = true;
					if( info.verbose ) System.out.println( "request input sense : trnsidx "+trnsIdx+"; rcvidx "+rcvIdx );
					break;
				
				default:
					context.getHost().showMessage( JOptionPane.ERROR_MESSAGE, app.getResourceString(
						"errRenderSourceRequest" ) + " : " +
						(r.type >= 0 && r.type < requestKeyNames.length ? requestKeyNames[ r.type ] :
						String.valueOf( r.type )));
					return false;
				}
			} // for collRequests.length

			// populate RenderInfo fields
//			info.senseBufSize	= Math.max( 16, (int) (((double)
//					app.getUserPrefs().node( PrefsUtil.NODE_PLUGINS ).getInt( PrefsUtil.KEY_OLSENSEBUFSIZE, 0 )
//					/ 1000.0) * info.senseRate + 0.5) ) & ~1;  // muss durch zwei teilbar sein!
//			info.senseBufSizeH	= info.senseBufSize >> 1;
//			info.startPos		= context.getTimeSpan().getStart();
			info.convBuf		= new float[1][];
			info.outLength		= (long) ((double) context.getTimeSpan().getLength() *
													(info.senseRate / (double) context.getSourceRate()) + 0.5);
			info.progOff		= 0;
			info.consumeWeight /= info.outLength;
			info.produceWeight /= info.outLength;
			
			// ---------- Consumer part ----------
			if( info.consumer != null ) {
				info.afTrajSources	= new AudioFile[ source.numTrns ];
				info.fTrajSources	= new File[ source.numTrns ];
				info.consumerSource = new RenderSource( source.numTrns, source.numRcv );
				collRequests		= targetRequestPrimitive.getRequests();
				for( int i = 0; i < collRequests.size(); i++ ) {
					r = (Request) collRequests.get( i );
					switch( r.type ) {
					case REQUEST_TRAJ:
						trnsIdx = ((Number) r.params).intValue();
						if( trnsIdx < 0 || trnsIdx >= source.numTrns ) {
							context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
								app.getResourceString( "errRenderTargetRequest" ) + " : " + trnsIdx );
							return false;
						}
						if( !(r.medium instanceof File) ) {
							context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
								app.getResourceString( "errRenderSourceObject" ) + " : " + r.medium );
							return false;
						}
						
						info.fTrajSources[ trnsIdx]					= (File) r.medium;
						info.consumerSource.trajRequest[ trnsIdx ]  = true;
						if( info.verbose ) System.out.println( "request output traj : trnsidx "+trnsIdx );
						break;

					default:
						context.getHost().showMessage( JOptionPane.ERROR_MESSAGE, app.getResourceString(
							"errRenderTargetRequest" ) + " : " +
							(r.type >= 0 && r.type < requestKeyNames.length ? requestKeyNames[ r.type ] :
							String.valueOf( r.type )));
						return false;
					}
				} // for collRequests.length
			} // if( info.isConsumed )
			
			success = true;
		}
		finally {
			if( !success ) cleanUp( context, source, info );
		}
		
		return success;
	}

	/**
	 *	Handles a block of source data
	 *	for rendering, by writing it to
	 *	the source-request media given by
	 *	the lisp script.
	 */
	public boolean producerRender( RenderContext context, RenderSource source )
	throws IOException
	{
		RenderInfo		info			= (RenderInfo) context.moduleMap.get( KEY_RENDERINFO );
		int				rcvIdx, trnsIdx;
		boolean			success			= false;

		try {
			for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
				if( source.trajRequest[ trnsIdx ]) {
					info.afTrajTargets[ trnsIdx ].writeFrames(
						source.trajBlockBuf[ trnsIdx ], source.blockBufOff, source.blockBufLen );
				}
				for( rcvIdx = 0; rcvIdx < source.numRcv; rcvIdx++ ) {
					if( source.senseRequest[ trnsIdx ][ rcvIdx ]) {
						info.convBuf[ 0 ] = source.senseBlockBuf[ trnsIdx ][ rcvIdx ];
						info.afSenseTargets[ trnsIdx ][ rcvIdx ].writeFrames(
							info.convBuf, source.blockBufOff, source.blockBufLen );
					}
				}
			}
			info.progOff   += source.blockBufLen;
			((RenderHost) context.getHost()).setProgression( info.produceWeight * (float) info.progOff );
			success			= true;
		}
		finally {
			if( !success ) cleanUp( context, source, info );
		}

		return success;
	}

	/**
	 *	If a consumer was specified to write data
	 *	back to the session, it is initialized here
	 *	(<code>consumerBegin</code>).
	 *	<code>invokeLispRender</code> is called and
	 *	in the consumer case, the data is consumed
	 *	using <code>consumerRender</code> and <code>consumerFinish</code>.
	 */
	public boolean producerFinish( RenderContext context, RenderSource source )
	throws IOException
	{
		int					trnsIdx, i, j, blockBufSize;
		RenderInfo			info		= (RenderInfo) context.moduleMap.get( KEY_RENDERINFO );
		Number				num;
		AudioFileDescr		afd;
		long				startPos;
		boolean				success		= false;
	
		try {
			// ---------- Consumer part ----------
			if( info.consumer != null ) {
				if( !info.consumer.consumerBegin( context, info.consumerSource )) return false;
			}

			// write synth control render stuff
			if( !invokeLispRender( context, source, info )) return false;
			if( !context.getHost().isRunning() ) return false;

			// ---------- Consumer part ----------
			if( info.consumer != null ) {

				num					= (Integer) context.getOption( RenderContext.KEY_MINBLOCKSIZE );
				i					= num == null ? 2 : num.intValue();
				num					= (Integer) context.getOption( RenderContext.KEY_MAXBLOCKSIZE );
				j					= num == null ? 0x7FFFFFFF : num.intValue();
				num					= (Integer) context.getOption( RenderContext.KEY_PREFBLOCKSIZE );
				blockBufSize		= num == null ? Math.max( i, Math.min( j, 1024 )) : num.intValue();
				info.consumerSource.trajBlockBuf= new float[ source.numTrns ][][];
				info.consumerSource.blockBufOff	= 0;
				info.consumerSource.blockSpan	= new Span( context.getTimeSpan().getStart(),
															context.getTimeSpan().getStart() );

				// prepare audio input files
				for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
					if( info.consumerSource.trajRequest[ trnsIdx ]) {
						info.afTrajSources[ trnsIdx ]   = AudioFile.openAsRead( info.fTrajSources[ trnsIdx ]);
						afd								= info.afTrajSources[ trnsIdx ].getDescr();
						if( afd.channels != 2 ) {
							context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
								AbstractApplication.getApplication().getResourceString( "errRenderTargetChannels" ) +
									" :\n\"" + info.fTrajSources[ trnsIdx ].getPath() + "\" (" + afd.channels + ")" );
							return false;
						}
						info.consumerSource.trajBlockBuf[ trnsIdx ] = new float[ 2 ][ blockBufSize ];
					}
				}
				
				// feed consumer with chunks from the audio files
				for( startPos = 0; startPos < info.outLength && context.getHost().isRunning(); ) {
					info.consumerSource.blockBufLen = (int) Math.min( blockBufSize, info.outLength - startPos );
					for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
						if( info.consumerSource.trajRequest[ trnsIdx ]) {
							info.afTrajSources[ trnsIdx ].readFrames( info.consumerSource.trajBlockBuf[ trnsIdx ],
																	  0, info.consumerSource.blockBufLen );
						}
					}

					info.consumerSource.blockSpan = new Span( info.consumerSource.blockSpan.getStop(),
															  info.consumerSource.blockSpan.getStop() +
															  info.consumerSource.blockBufLen );

					if( !info.consumer.consumerRender( context, info.consumerSource )) return false;

					info.progOff   += info.consumerSource.blockBufLen;
					startPos	   += info.consumerSource.blockBufLen;
					((RenderHost) context.getHost()).setProgression(
						info.consumeOffset + info.consumeWeight * (float) info.progOff );
				}
				if( !context.getHost().isRunning() ) return false;

				success = info.consumer.consumerFinish( context, info.consumerSource );
			} // if( info.consumer != null )
			else {
				success = true;
			}
		}
		finally {
			if( !success && (info.consumer != null) ) {
				try {
					info.consumer.consumerCancel( context, info.consumerSource );
				}
				catch( IOException e1 ) {
					((RenderHost) context.getHost()).setException( e1 );
				}
			}
			cleanUp( context, source, info );
		}

		((RenderHost) context.getHost()).setProgression( 1.0f );
		return success;
	}

	/**
	 *	Performs clean up and 
	 *	calls the <code>invokeLispCleanUp</code> method,
	 *	finally calls <code>plugInCleanUp</code>
	 *
	 *	@see	#invokeLispCleanUp( RenderContext, RenderSource, LispRenderPlugIn.RenderInfo )
	 */
	public void producerCancel( RenderContext context, RenderSource source )
	throws IOException
	{
		RenderInfo info = (RenderInfo) context.moduleMap.get( KEY_RENDERINFO );

		cleanUp( context, source, info );

		return;
	}
	
	private void cleanUp( RenderContext context, RenderSource source, RenderInfo info )
	{
		progressionSetPrimitive.setRenderHost( null );
// XXX		sourceRequestPrimitive.setRenderSource( null );		// will delete all associated temp files

		info.afTrajTargets  = null;
		info.afSenseTargets = null;
		info.convBuf		= null;
		info.fTrajSources   = null;
		info.afTrajSources  = null;
		
		try {
			invokeLispCleanUp( context, source, info );
		}
		catch( Exception e1 ) {
			System.err.println( e1 );
		}
		plugInCleanUp( context );
	}

	/**
	 *	Subclasses should call the lisp script's
	 *	prepare function and can perform additional
	 *	initializations. If they wish to write the
	 *	rendered data back to the session, they shall
	 *	set the <code>KEY_CONSUMER</code> option.
	 *
	 *	@param	context	the rendering context
	 *	@param	source	requests fields should be filled in here
	 *	@param	info	ignore at the moment
	 *
	 *	@see	RenderContext#KEY_CONSUMER
	 */
	protected abstract boolean invokeLispPrepare( RenderContext context,
												  RenderSource source, RenderInfo info )
	throws IOException;

	/**
	 *	Subclasses should call the lisp script's
	 *	render function.
	 */
	protected abstract boolean invokeLispRender( RenderContext context,
												 RenderSource source, RenderInfo info )
	throws IOException;

	/**
	 *	Subclasses should call the lisp script's
	 *	cleanup function. They can perform additional clean ups.
	 */
	protected abstract boolean invokeLispCleanUp( RenderContext context,
												  RenderSource source, RenderInfo info )
	throws IOException;

// -------- internal lisp primitive class --------

	private class LRPProgressionSetPrimitive
	extends BasicLispPrimitive
	{
		private RenderHost  host	= null;
		private float		offset, weight;
	
		private LRPProgressionSetPrimitive( AdvancedJatha lisp )
		{
			super( lisp, "PROGRESSION-SET", 1, 1 );
		}

		public void Execute( SECDMachine machine )
		{
			LispValue   progVal	= machine.S.pop();

			try {
				if( host != null && progVal.basic_numberp() ) {
					host.setProgression( offset + weight * (float) ((LispNumber) progVal).getDoubleValue() );
				}
			}
			finally {
				machine.S.push( f_lisp.NIL );
				machine.C.pop();
			}
		}
		
		private void setRenderHost( RenderHost host )
		{
			this.host = host;
		}
		
		/*
		 *  Sets the offset and length
		 *  of the progression bar,
		 *  i.e. rescales values passed
		 *  to the lisp function such
		 *  that prog( arg ) = offset + weight * arg
		 */
		private void setOffsetAndWeight( float offset, float weight )
		{
			this.offset = offset;
			this.weight = weight;
		}
	}
	
// -------- RenderInfo internal class --------
	/**
	 *	Struct class carrying
	 *	rendering information
	 *	for internal use by the lisp
	 *	render plug-in
	 */
	protected class RenderInfo
	{
		private double				senseRate;
		private long				outLength, progOff;
		private AudioFile[]			afTrajTargets;
		private AudioFile[][]		afSenseTargets;
		private File[]				fTrajSources;
		private AudioFile[]			afTrajSources;
		private RenderSource		consumerSource;
		private RenderConsumer		consumer;
		private float[][]			convBuf;
		private boolean				verbose;
		private float				produceWeight, consumeWeight, consumeOffset;
	}
}