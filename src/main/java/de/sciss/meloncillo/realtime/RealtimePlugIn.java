/*
 *  RealtimePlugIn.java
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
 *		24-Jul-04   created
 *		01-Sep-04	commented
 */

package de.sciss.meloncillo.realtime;

import java.io.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.plugin.*;

/**
 *	A simple extension to the <code>RealtimePlugIn</code>
 *	interface that adds methods for
 *	enabling and disabling the plug-in.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@see	Transport
 *	@see	RealtimeProducer#RealtimeProducer( Main, Session, RealtimeHost )
 */
public interface RealtimePlugIn
extends PlugIn
{
	/**
	 *	Called when the user enables the plug-in
	 *	by pressing the realtime button on the
	 *	transport palette.
	 *
	 *	@param	context		the currently valid realtime context.
	 *	@param	transport	the realtime host. plug-ins usually
	 *						attach a realtime consumer to this transport
	 *	@return	whether enabling was successful or not
	 *
	 *	@throws	IOException	when i/o or network based errors occur
	 */
	public boolean realtimeEnable( RealtimeContext context, Transport transport )
	throws IOException;
	
	/**
	 *	Called when the user disables the plug-in
	 *	by releasing the realtime button on the
	 *	transport palette.
	 *
	 *	@param	context		the realtime context used for the plug-in
	 *	@param	transport	the realtime host. plug-ins usually
	 *						remove their realtime consumer from this transport
	 *	@return	whether errors occured while disabling the plug-in
	 *
	 *	@throws	IOException	when i/o or network based errors occur
	 */
	public boolean realtimeDisable( RealtimeContext context, Transport transport )
	throws IOException;
}
