/*
 *  RenderContext.java
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
 *		23-May-04   created
 *		24-Jul-04   subclasses PlugInContext
 *		02-Sep-04	additional comments
 */

package de.sciss.meloncillo.render;

import de.sciss.meloncillo.plugin.*;

import de.sciss.io.*;

/**
 *	A simple extension to the 
 *	<code>PlugInContext</code> interface
 *	that adds no really anything but
 *	a key for rate resampling.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class RenderContext
extends PlugInContext
{
	/**
	 *  Key: Request resampling of the sense data
	 *  Value: Double of target rate
	 */
	public static final Object KEY_TARGETRATE   = "targetrate";

	/**
	 *  Constructs a new RenderContext.
	 *
	 *  @param  host				the object responsible for hosting
	 *								the rendering process
	 *  @param  collReceivers		the receivers involved in the rendering
	 *  @param  collTransmitters	the transmitters involved in the rendering
	 *  @param  time				the time span to render
	 *  @param  sourceRate			the source sense data rate
	 */
	public RenderContext( PlugInHost host, java.util.List collReceivers,
						  java.util.List collTransmitters, Span time, int sourceRate )
	{
		super( host, collReceivers, collTransmitters, time, sourceRate );
	}
}