/*
 *  MathUtil.java
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
 *		31-May-04	created
 *		04-Aug-04   commented
 */

package de.sciss.meloncillo.math;

/**
 *  This is a helper class containing utility static functions
 *  for common math operations and constants
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 04-Aug-04
 */
public class MathUtil
{
	/**
	 *  2 * PI (Outline of the unit circle)
	 */
	public static final double PI2  = Math.PI * 2;
	/**
	 *  logarithmus naturalis of 2
	 */
	public static final double LN2  = Math.log( 2 );
	/**
	 *  logarithmus naturalis of 10
	 */
	public static final double LN10 = Math.log( 10 );

	private MathUtil() {}

	/**
	 *  Decibel-to-Linear conversion.
	 *
	 *  @param  dB  volume in decibels
	 *  @return volume linear, such that
	 *			dBToLinear( -6 ) returns c. 0.5
	 */
	public static double dBToLinear( double dB )
	{
		return Math.exp( dB / 20 * LN10 );
	}
	
	/**
	 *  Linear-to-Decibel conversion
	 *
	 *  @param  linear  volume linear
	 *  @return volume in decibals, such that
	 *			linearToDB( 2.0 ) returns c. +6
	 */
	public static double linearToDB( double linear )
	{
		return Math.log( linear ) * 20 / LN10;
	}
}
