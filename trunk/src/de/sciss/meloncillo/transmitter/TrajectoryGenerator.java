/*
 *  TrajectoryGenerator.java
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
 *		04-Feb-05	created
 */

package de.sciss.meloncillo.transmitter;

import java.io.*;

import de.sciss.io.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public interface TrajectoryGenerator
{
	/**
	 *  Produces a block of trajectory frames
	 *
	 *  @param  span	the time span to read
	 *  @param  frames  to buffer to fill, where frames[0][] corresponds
	 *					to the X channel and frames[1][] to the Y channel.
	 *					the buffer length must be at least off + span.getLength()
	 *  @param  off		offset in frames, such that the first frame
	 *					will be placed in frames[][off]
	 *  @throws IOException if a read error occurs
	 */
    public void read( Span span, float[][] frames, int off ) throws IOException;
}
