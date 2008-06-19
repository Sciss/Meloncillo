/*
 *  RealtimeHost.java
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
 *		23-Jul-04   created
 *		24-Jul-04   subclasses PlugInHost
 *		01-Sep-04	commented
 */

package de.sciss.meloncillo.realtime;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.plugin.*;

/**
 *	A simple extension to the <code>PlugInHost</code>
 *	interface that adds a method for
 *	communication with a <code>RealtimeProducer</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.67, 01-Sep-04
 *
 *	@see	Transport
 *	@see	RealtimeProducer#RealtimeProducer( Main, Session, RealtimeHost )
 */
public interface RealtimeHost
extends PlugInHost
{
	/**
	 *	A callback function that gets called by the
	 *	<code>RealtimeProducer</code> when a request
	 *	such as adding a consumer or producing a block
	 *	of data has been completed.
	 *
	 *	@param	r	the request that was completed
	 */
	public void notifyConsumed( RealtimeProducer.Request r );
}