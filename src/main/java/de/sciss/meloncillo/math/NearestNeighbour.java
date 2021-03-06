/*
 *  NearestNeighbour.java
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
 *		27-May-04	created
 *		04-Aug-04   commented
 */

package de.sciss.meloncillo.math;

/**
 *  Upsampling by simple sample offset
 *  truncation to nearest neighbour.
 *  Note that this method is not suitable
 *  for downsampling because there's no
 *  means of antialiasing filtering
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class NearestNeighbour
extends Resampling
{
	/**
	 *  Constructs a new resampling object
	 */
	public NearestNeighbour()
	{
	}
	
	public double getWingSize( double factor )
	{
		return 0.0;
	}
	
	public double resample( float src[], double srcOff, float dest[], int destOff, int length, double factor )
	{
		double	srcIncr		= 1.0 / factor;
		int		i;
		double	phase		= srcOff;

		for( i = 0; i < length; i++, phase = srcOff + i * srcIncr ) {
			dest[ destOff++ ] = src[ (int) phase ];
		}
		
		return phase;
	}
}