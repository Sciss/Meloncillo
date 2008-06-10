/*
 *  MenuAction.java
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
 *		31-Jul-04   commented
 */

package de.sciss.meloncillo.gui;

import java.awt.event.*;
import javax.swing.*;

/**
 *  A simple extension of <code>AbstractAction</code>
 *  that puts a <code>KeyStroke</code> into its
 *  <code>ACCELERATOR_KEY</code> field. This field
 *  is read when the action is attached to a
 *  <code>JMenuItem</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	javax.swing.JMenuItem#setAccelerator( KeyStroke )
 *  @see	javax.swing.JMenuItem#configurePropertiesFromAction( Action )
 *  @see	javax.swing.AbstractButton#setAction( Action )
 */
public abstract class MenuAction
extends AbstractAction
{
	/**
	 *  Constructs a new <code>MenuAction</code> with the given
	 *  text and accelerator shortcut which will be
	 *  used when the action is attached to a <code>JMenuItem</code>.
	 *
	 *  @param  text		text to display in the menu item
	 *  @param  shortcut	<code>KeyStroke</code> for the
	 *						menu item's accelerator or <code>null</code>
	 */
	public MenuAction( String text, KeyStroke shortcut )
	{
		super( text );
		if( shortcut != null ) putValue( ACCELERATOR_KEY, shortcut );
	}

	/**
	 *  Constructs a new <code>MenuAction</code>
	 *  without accelerator key.
	 *
	 *  @param  text		text to display in the menu item
	 */
	public MenuAction( String text )
	{
		super( text );
	}

	public abstract void actionPerformed( ActionEvent e );
}
