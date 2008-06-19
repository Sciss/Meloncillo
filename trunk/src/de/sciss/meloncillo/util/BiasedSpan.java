/**
 *  BiasedSpan.java
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
 *		09-Aug-04   commented
 */

package de.sciss.meloncillo.util;

import de.sciss.io.*;

/**
 *  A span object that
 *  contains an additional bias.
 *  This is used for subsampled
 *  trajectory tracks. It means
 *  that though the normal span
 *  start and stop are accurate,
 *  the datastructure referenced
 *  by this span, has some coarse
 *  raster which produces roundoff
 *  errors (bias). In order to
 *  avoid the roundoff errors to
 *  grow, a biased span is used.
 *  This allows the object to keep
 *  the maximum bias within the limit
 *  of its coarsity.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @todo   this is not very well designed,
 *			because SubsampledTrackEditor has
 *			to create new BiasedSpan objects
 *			each time a method is called. It would
 *			be easier if relationships were reversed,
 *			i.e. Span was a subclass of BiasedSpan.
 *			However this would cause unnecessary RAM overhead.
 *			We really have to think about this issue.
 */
public class BiasedSpan
extends Span
{
	private int startBias, stopBias;

	/**
	 *  Creates a new empty unbiased span
	 */
	public BiasedSpan()
	{
		this( 0, 0, 0, 0 );
	}

	/**
	 *  Creates a new unbiased span
	 *
	 *  @param  start   accurate start point
	 *  @param  stop	accurate end point
	 */
	public BiasedSpan( long start, long stop )
	{
		this( start, stop, 0, 0 );
	}

	/**
	 *  Creates a new biased span
	 *
	 *  @param  start		accurate start point
	 *  @param  stop		accurate end point
	 *  @param  startBias   relative bias of the internal coarse start
	 *						when compared to the accurate start
	 *  @param  stopBias	relative bias of the internal coarse stop
	 *						when compared to the accurate stop
	 */
	public BiasedSpan( long start, long stop, int startBias, int stopBias )
	{
		super( start, stop );
		this.startBias  = startBias;
		this.stopBias   = stopBias;
	}

	/**
	 *  Creates a new biased span by copying
	 *  a given span.
	 *
	 *  @param  span		the span to copy. including its bias
	 */
	public BiasedSpan( BiasedSpan span )
	{
		this( span.getStart(), span.getStop(), span.getStartBias(), span.getStopBias() );
	}

	/**
	 *  Creates a new biased span by copying
	 *  a given span.
	 *
	 *  @param  span		the span to copy. start and stop bias will be zero.
	 */
	public BiasedSpan( Span span )
	{
		this( span.getStart(), span.getStop(), 0, 0 );
	}
	
	/**
	 *  Replaces the bias values
	 *
	 *  @param  startBias   new relative start bias
	 *  @param  stopBias	new relative stop bias
	 */
	public void setBias( int startBias, int stopBias )
	{
		this.startBias  = startBias;
		this.stopBias   = stopBias;
	}

	/**
	 *  Gets the absolute biased start
	 *
	 *  @return the biased start as given by the accurate start
	 *			plus the start bias
	 */
    public long getBiasedStart()
    {
        return( start + startBias );
    }
    
	/**
	 *  Gets the absolute biased stop
	 *
	 *  @return the biased stop as given by the accurate stop
	 *			plus the stop bias
	 */
    public long getBiasedStop()
    {
        return( stop + stopBias );
    }

	/**
	 *  Gets the relative start bias
	 *
	 *  @return difference between the coarse start and the accurate start
	 */
    public int getStartBias()
    {
        return( startBias );
    }
    
	/**
	 *  Gets the relative stop bias
	 *
	 *  @return difference between the coarse stop and the accurate stop
	 */
    public int getStopBias()
    {
        return( stopBias );
    }

	/**
	 *  Gets the length of the coarse (biased) span.
	 *
	 *  @return length as calcuated by <code>getBiasedStart() - getBiasedStop()</code>
	 */
    public long getBiasedLength()
    {
        return( (stop + stopBias) - (start + startBias) );
    }
}