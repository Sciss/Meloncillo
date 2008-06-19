/*
 *  EditMenuListener.java
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
 *		31-Jul-04   commented
 */

package de.sciss.meloncillo.gui;

import java.awt.event.*;

import de.sciss.meloncillo.*;

/**
 *  Listeners get informed about
 *  the common edit operations
 *  usually invoked through items
 *  in the edit menu.
 *  <p>
 *  Note that a rather uncommon way
 *  is used to invoke the listener:
 *  Unlike other listeners, an
 *  <code>EditMenuListener</code> isn't
 *  registered somewhere using an
 *  <code>addEditMenuListener</code> method.
 *  Instead, since the edit menu is part
 *  of the global menu and thus added to all
 *  application windows, the actions responsible
 *  for edit / cut / copy / paste / clear will
 *  determine if the window to which the copy
 *  of the edit menu is attached, implements
 *  the <code>EditMenuListener</code> interface
 *  and if so, calls one of its methods.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	de.sciss.meloncillo.surface.SurfaceFrame
 *  @see	Main#clipboard
 */
public interface EditMenuListener
{
	/**
	 *  Called when cut was chosen
	 *  from the edit menu.
	 */
	public void editCut( ActionEvent e );
	/**
	 *  Called when copy was chosen
	 *  from the edit menu.
	 */
	public void editCopy( ActionEvent e );
	/**
	 *  Called when paste was chosen
	 *  from the edit menu.
	 */
	public void editPaste( ActionEvent e );
	/**
	 *  Called when clear or delete was chosen
	 *  from the edit menu.
	 */
	public void editClear( ActionEvent e );
	/**
	 *  Called when select-all was chosen
	 *  from the edit menu.
	 */
	public void editSelectAll( ActionEvent e );
}
