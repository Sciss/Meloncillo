/*
 *  RenderHost.java
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
 *		23-May-04   created
 *		24-Jul-04   subclasses PlugInHost
 *		02-Sep-04	commented
 */

package de.sciss.meloncillo.render;

import de.sciss.meloncillo.plugin.*;

/**
 *	A simple extension to
 *	the <code>PlugInHost</code>
 *	interface which adds support
 *	for progress bar and exception
 *	display.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public interface RenderHost
extends PlugInHost
{
	/**
	 *	Tells the host to update the progression bar.
	 *
	 *	@param	p	the new progression normalized
	 *				to 0.0 ... 1.0 . use -1 for
	 *				indeterminate mode
	 */
	public void setProgression( float p );
	
	/**
	 *	Saves the last internally caught exception.
	 *	This will be displayed when rendering aborts
	 *	with a failure.
	 *
	 *	@param	e	the recently caught exception
	 */
	public void setException( Exception e );
}