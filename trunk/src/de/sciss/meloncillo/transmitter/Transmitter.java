/*
 *  Transmitter.java
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
 *		02-Sep-04	commented
 */

package de.sciss.meloncillo.transmitter;

import de.sciss.meloncillo.io.*;
import de.sciss.meloncillo.session.*;

/**
 *	A transmitter is one of the two main
 *	objects contained in a session. It is
 *	maintained by the session's <code>TransmitterCollection</code>.
 *	A transmitter represents a dynamic point object
 *	that has a trajectory attached describing the movement
 *	in time.
 *  <p>
 *	The <code>Transmitter</code> interface is a
 *	general description of a transmitter,
 *	giving it a name
 *	and providing a method for querying
 *	a trajectory editor. Additional
 *	methods deal with data storage
 *	(<code>set/getDirectory</code>).
 *  <p>
 *  All coordinates are defined for
 *  the virtual space (0,0) ... (1,1)
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	de.sciss.meloncillo.session.SessionCollection
 *  @see	de.sciss.meloncillo.gui.VirtualSurface
 */
public interface Transmitter
extends SessionObject
{
	public static final int OWNER_TRAJ	=	0x3000;

	/**
	 *  Gets the editor used to maintain a transmitter's
	 *	trajectory. Operations on the editor should be
	 *	blocked in exclusive sync on the session's <code>DOOR_MTE</code>.
	 *
	 *  @return the editor for the transmitter's trajectory data
	 *
	 *	@todo	this should be replaced by a interface named TrackEditor
	 *			to keep data structures as abstract as possible. In the
	 *			future a mte might have three channels for example and
	 *			allow empty intermitting sections.
	 */
	public MultirateTrackEditor getTrackEditor();
}