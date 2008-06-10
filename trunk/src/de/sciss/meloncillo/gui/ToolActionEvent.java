/*
 *  ToolActionEvent.java
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
 *		22-Jul-04   new method incorporate().
 *		01-Aug-04   commented
 */

package de.sciss.meloncillo.gui;

import de.sciss.app.*;

/**
 *  This kind of event is fired
 *  from a <code>ToolBar</code> when
 *  the user switched to a different tool.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		ToolBar#addToolActionListener( ToolActionListener )
 *  @see		ToolPalette#addToolActionListener( ToolActionListener )
 *  @see		ToolActionListener
 *  @see		ToolAction
 */
public class ToolActionEvent
extends BasicEvent
{
// --- ID values ---
	/**
	 *  returned by getID() : the tool was changed
	 */
	public static final int CHANGED		= 0;

	private final ToolAction toolAction;

	/**
	 *  Constructs a new <code>ToolActionEvent</code>
	 *
	 *  @param  source		who originated the action
	 *  @param  ID			<code>CHANGED</code>
	 *  @param  when		system time when the event occured
	 *  @param  toolAction	the new ToolAction to which was switched
	 */
	public ToolActionEvent( Object source, int ID, long when, ToolAction toolAction )
	{
		super( source, ID, when );
	
		this.toolAction = toolAction;
	}
	
	
	/**
	 *  Queries the new tool
	 *
	 *  @return the new tool action of the <code>ToolBar</code>
	 *			or <code>ToolPalette</code>.
	 */
	public ToolAction getToolAction()
	{
		return toolAction;
	}

	public boolean incorporate( BasicEvent oldEvent )
	{
		if( oldEvent instanceof ToolActionEvent &&
			this.getSource() == oldEvent.getSource() &&
			this.getID() == oldEvent.getID() ) {
			
			return true;

		} else return false;
	}
}