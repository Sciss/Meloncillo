/**
 *  Dimension2DDouble.java
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
 *  The Java developers forgot the implement
 *  concrete floating point classes of <code>java.awt.geom.Dimension2D</code>.
 *  This is one for double precision values,
 *  in analogy to <code>Point2D</code> and <code>Point2D.Double</code>.
 *	It's rarely used and should be completely removed!
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.61, 09-Aug-04
 *
 *  @see	de.sciss.meloncillo.receiver.SigmaReceiver#getSize()
 *  @see	java.awt.geom.Point2D.Double
 *
 *	@todo	delete the remaining few uses of this class
 */
public class Dimension2DDouble
extends Dimension2D
{
	private double width, height;

	/**
	 *  Constructs a new empty dimension
	 *  (width and height are zero).
	 */
	public Dimension2DDouble()
	{
		setSize( 0.0, 0.0 );
	}

	/**
	 *  Constructs a new empty dimension
	 *  with the given extent.
	 *
	 *  @param  width   the dimension's initial width
	 *  @param  height  the dimension's initial height
	 */
	public Dimension2DDouble( double width, double height )
	{
		setSize( width, height );
	}

	/**
	 *  Constructs a new empty dimension
	 *  with the given extent.
	 *
	 *  @param  size	the dimension's initial extent
	 *					is copied from this object
	 */
	public Dimension2DDouble( Dimension2D size )
	{
		setSize( size );
	}
	
	public double getWidth()
	{
		return width;
	}
	
	public double getHeight()
	{
		return height;
	}
	
	public void setSize( Dimension2D size )
	{
		width   = size.getWidth();
		height  = size.getHeight();
	}

	public void setSize( double width, double height )
	{
		this.width   = width;
		this.height  = height;
	}
}
