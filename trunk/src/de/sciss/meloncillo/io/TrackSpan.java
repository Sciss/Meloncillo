/*
 *  TrackSpan.java
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
 *		03-Aug-04   commented
 *		28-Jan-05	added shift()
 *		31-Jan-05	fused with BiasedTrackSpan
 */

package de.sciss.meloncillo.io;

import de.sciss.io.*;

/**
 *  A track span describes a portion
 *  of floating point data linked to a time tag.
 *  The data is provided by an <code>InterleavedStreamFile</code>.
 *  The span's start offset corresponds to an arbitrary
 *  frame offset in the harddisc file. This is the
 *  basis of nonlinear nondestructive track editing.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		SubsampleTrackEditor
 *  @see		de.sciss.meloncillo.util.Span
 */
public class TrackSpan
{
	public final int startBias, stopBias;

	/**
	 *  A time span referencing the virtual
	 *  non-fragmented track in the application
	 */
	public final Span	span;   // re virtual track
	/**
	 *  A file holding a portion of the virtual track
	 */
	public final InterleavedStreamFile   f;
	/**
	 *  The offset in the file corresponding to
	 *  the time offset span.getStart
	 */
	public final long	offset; // re iff (corresponds to span.start)
	
//	/**
//	 *  Constructs a new <code>TrackSpan</code>
//	 *
//	 *  @param  span	the time span covered by this track
//	 *  @param  f		the file which contains the tracks data
//	 *  @param  offset  the file offset corresponding to span.getStart()
//	 */
//	private TrackSpan( Span span, InterleavedStreamFile f, long offset )
//	{
//		this.span		= span;
//		this.f			= f;
//		this.offset		= offset;
//		this.startBias	= 0;
//		this.stopBias	= 0;
//	}        
		
//	/**
//	 *  Constructs a new TrackSpan whose offset
//	 *  equals the current seek position of the passed file
//	 *
//	 *  @param  span	the time span covered by this track
//	 *  @param  f		the file which contains the tracks data
//	 *					and whose read/write position is used
//	 *					as the track span's file offset.
//	 */
//	private TrackSpan( Span span, InterleavedStreamFile f )
//	throws IOException
//	{
//		this.span		= span;
//		this.f			= f;
//		this.offset		= f.getFramePosition();
//		this.startBias	= 0;
//		this.stopBias	= 0;
//	}        

	public TrackSpan( Span span, InterleavedStreamFile f, long offset, int startBias, int stopBias )
	{
		this.span		= span;
		this.f			= f;
		this.offset		= offset;
		this.startBias	= startBias;
		this.stopBias	= stopBias;
	}
	
	/**
	 *  Constructs a new TrackSpan which is
	 *  identical to the passed argument
	 *
	 *  @param  orig	template whose entries will be
	 *					copied to the new track span
	 */
	public TrackSpan( TrackSpan orig )
	{
		this.span		= orig.span;
		this.f			= orig.f;
		this.offset		= orig.offset;
		this.startBias	= orig.startBias;
		this.stopBias	= orig.stopBias;
	}

	public TrackSpan shiftVirtual( long delta )
	{
		return new TrackSpan( this.span.shift( delta ), this.f, this.offset,
									this.startBias, this.stopBias );
	}
	
	public long getBiasedLength()
	{
		return( (span.getStop() + stopBias) - (span.getStart() + startBias) );
	}

	public long getBiasedStart()
	{
		return( span.getStart() + startBias );
	}
	
	public long getBiasedStop()
	{
		return( span.getStop() + stopBias );
	}
} // class TrackSpan
