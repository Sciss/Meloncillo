/*
 *  TransmitterEditor.java
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
 *		02-Sep-04   commented. TransmitterEditorException replaced
 *					by the runtime IllegalArgumentException.
 */

package de.sciss.meloncillo.transmitter;

import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.session.*;

/**
 *  The <code>TransmitterEditor</code> interface is a
 *  general description of a GUI component that
 *  can display essential data of certain transmitters
 *  and allows the user to adjust this data, such
 *  as the transmitter trajectory. The view
 *  of the editor, returned by <code>getView</code> can
 *  in fact be a different object, but usually the
 *  editor itself is a subclass of <code>JComponent</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public interface TransmitterEditor
{
	/**
	 *  Returns the <code>Transmitter</code> that is being edited
	 *  or <code>null</code> if there's no accociated <code>Transmitter</code>
	 *
	 *  @return the transmitter currently handled by the editor
	 */
	public Transmitter getTransmitter();
	
	/**
	 *  Sets the <code>Transmitter</code> that shall be edited
	 *
	 *  @param  root	application root
	 *  @param  doc		session document containing the receiver
	 *  @param  trns	the transmitter to edit
	 *
	 *  @throws IllegalArgumentException	in case the passed <code>Transmitter</code>
	 *										cannot be handled by this editor
	 *  @see	#canHandle( Transmitter )
	 */
	public void init( Main root, Session doc, Transmitter trns );

	/**
	 *  Tests if the editor can handle a certain <code>Transmitter</code>.
	 *
	 *  @param  trns	the transmitter to test for compatibility
	 *  @return		<code>true</code>, if that type of <code>Transmitter</code> can be handled
	 */
	public boolean canHandle( Transmitter trns );
	
	/**
	 *  Returns a list of all classes of <code>Transmitter</code>s that
	 *  can be handled by this editor.
	 *
	 *  @return a list whose elements are of type <code>class</code>.
	 *			these classes implement the <code>Transmitter</code> interface
	 *			and are compatible with this editor.
	 */
	public java.util.List getHandledTransmitters();

	/**
	 *  Returns the associated JComponent that can be added
	 *  to the timeline frame scrollpane
	 *
	 *  @return		a <code>JComponent</code> which is the editor's GUI.
	 *				the caller is responsible for making the
	 *				component visible.
	 */
	public JComponent getView();
}