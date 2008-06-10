/*
 *  PointInTime.java
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
 *		09-Aug-04   commented
 */

package de.sciss.meloncillo.util;

import java.awt.geom.*;

/**
 *  Equips a normal Point2D.Double object with
 *  a time tag. This is used by the freehand
 *  surface pencil tool.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 09-Aug-04
 *
 *  @see		de.sciss.meloncillo.surface.SurfacePane
 */
public class PointInTime
extends Point2D.Double
{
	private final long when;

	/**
	 *  Creates a new <code>PointInTime</code>
	 *  with given coordinates and time tag.
	 *
	 *  @param  x		horizontal coordinate
	 *  @param  y		vertical coordinate
	 *  @param  when	time tag as returned by System.currentTimeMillis()
	 */
	public PointInTime( double x, double y, long when )
	{
		super( x, y );
		this.when = when;
	}

	/**
	 *  Creates a new <code>PointInTime</code>
	 *  with given coordinates and time tag.
	 *
	 *  @param  pt		point whose coordinates are copied to this instance.
	 *  @param  when	time tag as returned by System.currentTimeMillis()
	 */
	public PointInTime( Point2D pt, long when )
	{
		super( pt.getX(), pt.getY() );
		this.when = when;
	}
	
	/**
	 *  Queries to time tag
	 *
	 *  @return  the time tag used to construct this object
	 */
	public long getWhen()
	{
		return when;
	}
}