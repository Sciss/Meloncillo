/*
 *  BlendContext.java
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
 *		01-Aug-04   commented
 */

package de.sciss.meloncillo.io;

/**
 *  Object describing the
 *  type of crossfading used
 *  in trajectory operations.
 *  This really looks like an overkill
 *  to create an extra class for
 *  hosting an integer field. However
 *  it is planned that a future version
 *  supports different kind of crossfade
 *  types or different blending at
 *  the beginning and ending of a span,
 *  even pre- and postroll.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	de.sciss.meloncillo.Main#getBlending()
 */
public class BlendContext
{
	/**
	 *  The length of the
	 *  blending crossfade in
	 *  sense rate frames
	 */
	public final int blendLen;

	/**
	 *  Create a new BlendContext with
	 *  the given length
	 *
	 *  @param  blendLen	length of the blending in sense frames
	 */
	public BlendContext( int blendLen )
	{
		this.blendLen   = blendLen;
	}
}
