/*
 *  TransportListener.java
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
 *		06-May-04   created
 *		22-Jul-04   moved to realtime package
 *		01-Sep-04	additional comments
 */

package de.sciss.meloncillo.realtime;

/**
 *  The transport listener methods
 *  are invoked
 *  right before the corresponding
 *  action is started. Methods can
 *  use this feature to make
 *  essential initializations, for
 *  example synchronize external
 *  programs. The transport action
 *  is only performed after returning
 *  from all transport listeners.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.67, 01-Sep-04
 *
 *	@synchronization	all methods are invoked from
 *						the transport thread so beware!
 */
public interface TransportListener
{
	/**
	 *	Invoked when the transport is about to stop.
	 *
	 *	@param	pos	the position of the timeline after stopping
	 */
	public void transportStop( long pos );
	/**
	 *	Invoked when the transport position was altered,
	 *	for example when jumping backward in loop mode.
	 *
	 *	@param	pos	the new position of the timeline at which
	 *			transport will continue to play
	 */
	public void transportPosition( long pos );
	/**
	 *	Invoked when the transport is about to start.
	 *
	 *	@param	pos	the position of the timeline when starting to play
	 */
	public void transportPlay( long pos );
	/**
	 *	Invoked when the transport was running when the
	 *	application was about to quit. Vital cleanup should
	 *	be performed in here.
	 */
	public void transportQuit();
}