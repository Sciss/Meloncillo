/*
 *  DecimationInfo.java
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
 *		07-Feb-05	created from de.sciss.meloncillo.io.DecimationInfo
 *		30-Jun-08	copied back from EisK
 */

package de.sciss.meloncillo.io;

import de.sciss.io.Span;

/**
 *  A context object for GUI elements
 *  that wish to load sense data at an
 *  economic data rate, i.e. by using
 *  an appropriate subsampled version of
 *  the sense data. This object is returned
 *  by the <code>getBestSubsample</code> method
 *  of <code>MultirateTrackEditor</code>. The
 *  calling instance should read the <code>sublength</code>
 *  field to determine the required buffer size
 *  and then pass this object to the <code>read</code> methods
 *  in <code>MultirateTrackEditor</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.65, 14-Jan-06
 *
 *  @todo		this class could be internal to mte
 */
public class DecimationInfo
{
	/**
	 *  Internal index for MultirateTrackEditor
	 */
	public final int	idx;
	protected final int	shift;
	protected final int	inlineDecim;
	/**
	 *  Time span (in fullrate frames) covered by this subsample
	 */
	public final Span	span;
	/**
	 *  Length (rounded) of the time span decimated through subsampling
	 */
	public final long	sublength;
	public final int	model;
	public final int	channels;

	/**
	 *  Creates a new <code>DecimationInfo</code>
	 *  data structure with the given decimation.
	 *
	 *  @param  idx			internal index for <code>MultirateTrackEditor</code>
	 *  @param  span		the originally covered time span
	 *  @param  sublength   the translated span length in decimated
	 *						frames (rounded to integer)
	 */
	protected DecimationInfo( Span span, long sublength, int channels,
							  int idx, int shift, int inlineDecim, int model )
	{
		this.span			= span;
		this.sublength		= sublength;
		this.channels		= channels;
		this.idx			= idx;
		this.shift			= shift;
		this.inlineDecim	= inlineDecim;
		this.model			= model;
	}

	/**
	 *  Returns the decimation
	 *  rate factor.
	 *
	 *  @return the factor by which the full rate is decimated,
	 *			that is, <code>decimatedRate = fullRate / returnedFactor</code>
	 */
	public int getDecimationFactor()
	{
		return( (1<<shift) * inlineDecim );
	}
	
	public long getTotalLength()
	{
		return( (sublength * inlineDecim) << shift );
	}
}