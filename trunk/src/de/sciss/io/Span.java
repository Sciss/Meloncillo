/*
 *  Span.java
 *  de.sciss.io package
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
 *		21-May-05	created from de.sciss.eisenkraut.util.Span
 */

package de.sciss.io;

/**
 *  A struct class: a span between a start
 *  and end point in one dimensional
 *  space. The start point is
 *  considered inclusive while
 *  the end point is considered
 *  exclusive. In Melloncillo, it is
 *  mainly used to describe a time span
 *  in sense rate frames.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.10, 21-May-05
 *
 *  @see	de.sciss.meloncillo.io.MultirateTrackEditor#insert( Span, float[][] )
 *  @see	de.sciss.meloncillo.io.MultirateTrackEditor#remove( Span )
 *  @see	de.sciss.meloncillo.io.TrackSpan
 *  @see	de.sciss.meloncillo.timeline.Timeline#setVisibleSpan( Object, Span )
 */
public class Span
{
	/**
	 *  The span <code>start</code> should be treated
	 *  as if it was immutable!
	 */
	protected long start;
	/**
	 *  The span <code>start</code> should be treated
	 *  as if it was immutable!
	 */
	protected long stop;

	/**
	 *  Create a new empty span
	 *  whose start and stop are zero.
	 */
	public Span()
	{
		setSpan( 0, 0 );
	}

	/**
	 *  Creates a span with the given
	 *  start and stop points.
	 *  The caller has to ensure that
	 *  start <= stop (this is not checked)
	 *
	 *  @param  start   beginning of the span
	 *  @param  stop	end of the span
	 */
	public Span( long start, long stop )
	{
		setSpan( start, stop );
	}

	/**
	 *  Create a span with the
	 *  start and stop points copied
	 *  from another span.
	 *
	 *  @param  span	template span whose start and end are copied
	 */
	public Span( Span span )
	{
		setSpan( span );
	}
	
	/*
	 *  private because Span should be considered immutable
	 */
	private void setSpan( Span span )
	{
		start   = span.getStart();
		stop    = span.getStop();
	}

	private void setSpan( long start, long stop )
	{
		this.start  = start;
		this.stop   = stop;
	}
    
	/**
	 *  Checks if a position lies within the span.
	 *
	 *  @return		<code>true</code>, if <code>start <= postion < stop</code>
	 */
    public boolean contains( long position )
    {
        return( position >= start && position < stop );
    }

	/**
	 *  Checks if the span is empty.
	 *
	 *  @return		<code>true</code>, if <code>start == stop</code>
	 */
    public boolean isEmpty()
    {
        return( start == stop );
    }
    
	/**
	 *  Checks if this span is equal to an object.
	 *
	 *  @param  o   an object to compare to this span
	 *  @return		<code>true</code>, if <code>o</code> is a span with
	 *				the same start and end point
	 */
    public boolean equals( Object o )
    {
        return( o instanceof Span &&
				((Span) o).start == this.start && ((Span) o).stop == this.stop );
    }
    
	/**
	 *  Queries the span's start.
	 *
	 *  @return		the start point of the span
	 */
    public long getStart()
    {
        return start;
    }
    
	/**
	 *  Queries the span's end.
	 *
	 *  @return		the end point of the span
	 */
    public long getStop()
    {
        return stop;
    }
    
	/**
	 *  Queries the span's extent (duration, length etc.)
	 *
	 *  @return		length of the span, i.e. <code>stop - start</code>
	 */
    public long getLength()
    {
        return( stop - start );
    }
	
	public String toString()
	{
		return( String.valueOf( start ) + " ... " + String.valueOf( stop ));
	}
	
	/**
	 *  Union operation on two spans.
	 *
	 *  @param  span1   first span to fuse
	 *  @param  span2   second span to fuse
	 *  @return		a new span whose extension
	 *				covers both span1 and span2
	 */
	public static Span union( Span span1, Span span2 )
	{
		return new Span( Math.min( span1.start, span2.start ),
						 Math.max( span1.stop, span2.stop ));
	}
	
	public Span shift( long delta )
	{
		return new Span( start + delta, stop + delta );
	}
}