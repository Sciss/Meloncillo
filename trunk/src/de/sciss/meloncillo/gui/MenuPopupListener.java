/*
 *  MenuPopupListener.java
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
 *		20-May-04   created
 *		31-Jul-04   commented
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 *  A helper class that can be registered
 *  to a component as a MouseListener.
 *  When the mouse is pressed, the
 *  popup menu specified in the constructor
 *  is displayed. Note that this is used
 *  for detached menus and not for context
 *  menus since for context menus we would
 *  have to check for the popup's
 *  <code>isPopupTrigger</code> result. For
 *  that case use a <code>PopupListener</code>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 31-Jul-04
 *
 *  @see		javax.swing.JPopupMenu#show( Component, int, int )
 *  @see		PopupListener
 */
public class MenuPopupListener
extends MouseAdapter
{
	private final JPopupMenu pop;

	/**
	 *  Creates a new <code>MenuPopupListener</code>
	 *  for the given popup.
	 *
	 *  @param  pop		the <code>JPopupMenu</code> that
	 *					will be shown on the component to
	 *					which this <code>MouseAdapter</code>
	 *					is attached, when the user presses
	 *					he mouse button
	 */
	public MenuPopupListener( JPopupMenu pop )
	{
		super();
		
		this.pop = pop;
	}
	
	/**
	 *  Show the popup menu on the component that
	 *  fired the event and at the location where
	 *  the mouse press occured.
	 */
	public void mousePressed( MouseEvent e )
	{
		pop.show( e.getComponent(), 1, e.getComponent().getHeight() - 1 );
	}
}