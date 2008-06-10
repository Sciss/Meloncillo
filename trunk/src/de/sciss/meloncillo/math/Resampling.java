/*
 *  Resampling.java
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
 *		27-May-04	created
 *		04-Aug-04   commented
 */
 
package de.sciss.meloncillo.math;

/**
 *  An interface that describes
 *  the facility to resample a
 *  given data vector from a
 *  source to a target rate.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 04-Aug-04
 */
public abstract class Resampling
{
	/**
	 *  Returns the interpolator function's
	 *  length for a given resampling factor.
	 *  For sinc interpolation this is the
	 *  size of one wing of the windod
	 *  interpolation function, for linear
	 *  interpolation this is one, for quadratic
	 *  it would be two etc. This method
	 *  gives no clues about the differences
	 *  between the wing left and right to the
	 *  current sample offset. It just returns
	 *  the larged wing in order to allow the
	 *  caller to create appropriate padding
	 *  elements in its buffer (use the ceiling
	 *  integer). But in most cases
	 *  left and right wing have the same size.
	 *
	 *  @param  factor  target rate divided by source rate
	 *  @return interpolation functions scope for
	 *			each of the both wings wich are traversed
	 *			in the course of interpolating one target sample.
	 */
	public abstract double getWingSize( double factor );

	/**
	 *	Resamples a data vector.
	 *
	 *  @param  src			Original vector at source rate
	 *	@param	srcOff		where to begin in <code>src</code>.
	 *						This is a double value to allow
	 *						successive resampling of data blocks
	 *						with non integer resampling factors.
	 *						Note that to the left of srcOff there
	 *						must be at least getWingSize more samples
	 *						in the src buffer, otherwise an
	 *						ArrayIndexOutOfBoundsException will be thrown.
	 *	@param	length		number of <strong>target</strong> samples to calculate.
	 *						Note that the size of the src buffer must
	 *						exceed at least getWinSize more samples than
	 *						the stopping index which is srcOff + length / factor.
	 *	@param	factor		target rate divided by source rate
	 *  @return				the offset in src which would be the new offset
	 *						of a successive block resampling operation
	 */
	public abstract double resample( float src[], double srcOff, float dest[], int destOff,
									 int length, double factor );
}
