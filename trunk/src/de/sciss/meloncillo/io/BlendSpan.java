/*
 *  BlendSpan.java
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
 *		03-Aug-04   commented
 */

package de.sciss.meloncillo.io;

import java.io.*;

import de.sciss.io.*;

/**
 *  A <code>BlendSpan</code> is a substitute for <code>TrackSpan</code>
 *  in <code>MultirateTrackEditor</code> overwriting operations.
 *  It will automatically apply a blending crossfade
 *  while <code>continueWrite</code> is called.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	MultirateTrackEditor#beginOverwrite( Span, BlendContext, SyncCompoundEdit )
 *  @see	MultirateTrackEditor#continueWrite( BlendSpan, float[][], int offset, int length )
 *  @see	MultirateTrackEditor#finishWrite( BlendSpan, SyncCompoundEdit )
 */
public class BlendSpan
{
	/**
	 *  The track span wrapped by the
	 *  this class. think of BlendSpan
	 *  as a hybrid subclass and context
	 *  of/for track span.
	 */
	protected final TrackSpan				trackSpan;
	private final float[][]					buffer;
	private final int						bufSize;
	private final SubsampleTrackEditor		nte;
	private final float						leftWeight, rightWeight;
	private boolean							purity;			// true if we're in the non-blending middle part
	private boolean							leftWing;		// true if we're in the blending starting part
	private boolean							rightWing;		// true if we're in the blending ending part
	private long							framesWritten   = 0;
	private final long						purityStart, purityStop;
	private final TrackList					leftTL, rightTL;
	private final long						leftTLLength, rightTLLength, leftTLStart, rightTLStart;
	private int								leftTLidx, rightTLidx;

	/**
	 *  Construct a new <code>BlendSpan</code> emcompassing
	 *  a given <code>TrackSpan</code> and fade in and out <code>TrackList</code>s.
	 *
	 *  @param  bc		<code>BlendContext</code> describing the length and
	 *					shape of the crossfade to be applied at the begin and end
	 *					of <code>ts</code>
	 *  @param  leftTL  the <code>TrackList</code> describing the regions contained
	 *					in the span <code>ts.span.getStart() ... ts.span.getStart() +
	 *					bc.blendLen</code>
	 *  @param  rightTL the <code>TrackList</code> describing the regions contained
	 *					in the span <code>ts.span.getStop() - bc.blendLen ... ts.span.getStop()</code>
	 *  @param  ts		the <code>TrackSpan</code> covering the complete write operation.
	 *  @param  nte		the fullrate track editor whose <code>continueWrite</code> method will be
	 *					wrapped inside the <code>write</code> method of this class
	 */
	public BlendSpan( BlendContext bc, TrackList leftTL, TrackList rightTL, TrackSpan ts,
					  SubsampleTrackEditor nte )
	{
		this.trackSpan		= ts;
		this.nte			= nte;
		this.leftTL			= leftTL;
		this.rightTL		= rightTL;
		
		Span span;
		
		span				= leftTL.getSpan();
		leftTLLength		= span.getLength();
		leftTLStart			= span.getStart();
		span				= rightTL.getSpan();
		rightTLLength		= span.getLength();
		rightTLStart		= span.getStart();

		purityStart			= leftTLLength;
		purityStop			= ts.span.getLength() - rightTLLength;
		purity				= purityStart == 0;
		leftWing			= !purity;
		rightWing			= false;
		leftWeight			= 1.0f / (float) leftTLLength;
		rightWeight			= 1.0f / (float) rightTLLength;
		
		bufSize				= (int) Math.min( 1024, Math.max( leftTLLength, rightTLLength ));
		buffer				= new float[ nte.getChannelNum() ][ bufSize ];

//System.err.println( "blendspan length "+ts.span.getLength()+"; purity "+purityStart+" ... "+purityStop );
	}
	
	/**
	 *  Continue to write frames to fill the underyling track span.
	 *  This method is called from the <code>continueWrite</code> method
	 *  of the hosting <code>MultirateTrackEditor</code>.
	 *
	 *  @param  frames	data to be written where <code>frames[0][]</code> is the first channel's
	 *					data, <code>frames[1][]</code> is the second channel's data etc.
	 *  @param  offset  frame offset in <code>frames</code>, such that the first frame to be
	 *					written will be polled from <code>frames[ch][offset]</code>
	 *  @param  length  number of continuous frames to write
	 *
	 *  @see	MultirateTrackEditor#continueWrite( BlendSpan, float[][], int offset, int length )
	 */
	public void write( float[][] frames, int offset, int length )
	throws IOException
	{
		if( purity ) {				// -------------- writing middle part --------------
			if( framesWritten + length < purityStop ) {
				nte.continueWrite( trackSpan, frames, offset, length );
				framesWritten += length;
			} else {
				int len = (int) (purityStop - framesWritten);
				nte.continueWrite( trackSpan, frames, offset, len );
				offset		  += len;
				length		  -= len;
				framesWritten += len;
				purity		   = false;
				rightWing      = true;
				if( length > 0 ) this.write( frames, offset, length );   // recursive
			}
			
		} else if( leftWing ) {		// -------------- writing left wing --------------
			long		oldPos, len2, oldTSoffset, oldTSmaxLen;
			int			i, j, ch, len, interpOff;
			float		interp;
			TrackSpan   oldTS;
			
			len2	= Math.min( length, purityStart - framesWritten );
			do {
				leftTLidx--;
				do {
					leftTLidx++;
					oldTS		= leftTL.get( leftTLidx );
					oldTSoffset = framesWritten - (oldTS.span.getStart() - leftTLStart);
					oldTSmaxLen = oldTS.span.getLength() - oldTSoffset;
				} while( oldTSmaxLen <= 0 );
				
				len			= (int) Math.min( Math.min( oldTSmaxLen, len2 ), bufSize );
				oldPos		= oldTS.f.getFramePosition();
				oldTS.f.seekFrame( oldTSoffset + oldTS.offset );
				oldTS.f.readFrames( buffer, 0, len );
				oldTS.f.seekFrame( oldPos );
				
				interpOff   = (int) framesWritten;
				for( i = 0, j = offset; i < len; i++, j++, interpOff++ ) {
					interp = interpOff * leftWeight;
					for( ch = 0; ch < buffer.length; ch++ ) {
						buffer[ch][i]   = buffer[ch][i] * (1.0f - interp) + frames[ch][j] * interp;
					}
				}
				nte.continueWrite( trackSpan, buffer, 0, len );
				len2			-= len;
				offset			+= len;
				length			-= len;
				framesWritten   += len;
			} while( len2 > 0 );
			
			if( length > 0 ) {  // enter purity part
				leftWing	= false;
				purity		= true;
				this.write( frames, offset, length );   // recursive
			}
			
		} else {					// -------------- writing right wing --------------
			assert rightWing;

			long		oldPos, oldTSoffset, oldTSmaxLen;
			int			i, j, ch, len, interpOff;
			float		interp;
			TrackSpan   oldTS;
			
			do {
				rightTLidx--;
				do {
					rightTLidx++;
					oldTS		= rightTL.get( rightTLidx );
					oldTSoffset = (framesWritten - purityStop) - (oldTS.span.getStart() - rightTLStart);
					oldTSmaxLen = oldTS.span.getLength() - oldTSoffset;
				} while( oldTSmaxLen <= 0 );

				len			= (int) Math.min( Math.min( oldTSmaxLen, length ), bufSize );
				oldPos		= oldTS.f.getFramePosition();
				oldTS.f.seekFrame( oldTSoffset + oldTS.offset );
				oldTS.f.readFrames( buffer, 0, len );
				oldTS.f.seekFrame( oldPos );

				interpOff = (int) (framesWritten - purityStop);
				for( i = 0, j = offset; i < len; i++, j++, interpOff++ ) {
					interp = interpOff * rightWeight;
					for( ch = 0; ch < buffer.length; ch++ ) {
						buffer[ch][i]   = buffer[ch][i] * interp + frames[ch][j] * (1.0f - interp);
					}
				}
				nte.continueWrite( trackSpan, buffer, 0, len );
				offset			+= len;
				length			-= len;
				framesWritten   += len;
			} while( length > 0 );
		}
	} // write()
}