/*
 *  ReceiverEditor.java
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
 *		14-Aug-04   commented. ReceiverEditorException replaced
 *					by the runtime IllegalArgumentException.
 */

package de.sciss.meloncillo.receiver;

import de.sciss.app.AbstractWindow;
import de.sciss.meloncillo.*;
import de.sciss.meloncillo.session.*;

/**
 *  The <code>ReceiverEditor</code> interface is a
 *  general description of a GUI component that
 *  can display essential data of certain receivers
 *  and allows the user to adjust this data, such
 *  as the receivers sensitivity model. The view
 *  of the editor, returned by <code>getView</code> can
 *  in fact be a different object, but usually the
 *  editor itself is a subclass of <code>JFrame</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public interface ReceiverEditor
{
	/**
	 *  Returns the <code>Receiver</code> that is being edited
	 *  or <code>null</code> if there's no accociated <code>Receiver</code>
	 *
	 *  @return the receiver currently handled by the editor
	 */
	public Receiver getReceiver();

	/**
	 *  Sets the <code>Receiver</code> that shall be edited
	 *
	 *  @param  root	application root
	 *  @param  doc		session document containing the receiver
	 *  @param  rcv		the receiver to edit
	 *
	 *  @throws IllegalArgumentException	in case the passed <code>Receiver</code>
	 *										cannot be handled by this editor
	 *  @see	#canHandle( Receiver )
	 */
	public void init( Main root, Session doc, Receiver rcv );

	/**
	 *  Tests if the editor can handle a certain <code>Receiver</code>.
	 *
	 *  @param  rcv	the receiver to test for compatibility
	 *  @return		<code>true</code>, if that type of <code>Receiver</code> can be handled
	 */
	public boolean canHandle( Receiver rcv );

	/**
	 *  Returns a list of all classes of <code>Receiver</code>s that
	 *  can be handled by this editor.
	 *
	 *  @return a list whose elements are of type <code>class</code>.
	 *			these classes implement the <code>Receiver</code> interface
	 *			and are compatible with this editor.
	 */
	public java.util.List getHandledReceivers();

	/**
	 *  Returns the associated frame that can be added
	 *  to the main application window
	 *
	 *  @return		a frame which is the editor's GUI.
	 *				the caller is responsible for making the
	 *				component visible.
	 */
	public AbstractWindow getView();
}