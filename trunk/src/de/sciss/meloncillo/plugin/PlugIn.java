/*
 *  PlugIn.java
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
 *		24-Jul-04   created
 *		01-Sep-04	additional comments.
 */

package de.sciss.meloncillo.plugin;

import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.session.*;

/**
 *  A simple interface for communication
 *  between a host and an object that can produce
 *  any kind of output (implementing this
 *  interface). The host calls the getSettingsView()
 *  to present a GUI to the user.
 *  Communication is mainly provided through a
 *  PlugInContext object.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public interface PlugIn
{
	/**
	 *  This gets called right after the
	 *  instantiation of a new render module
	 *
	 *	@param	root	Application root
	 *	@param	doc		Session document
	 */
	public void init( Main root, Session doc );

	/**
	 *  Asks the plugin to return a component
	 *  suitable for presenting to the user
	 *  in order to allow parameter adjustments
	 *  The provided PlugInContext can be used
	 *  to query the number of receivers, transmitters
	 *  etc. Since the user might change the
	 *  transmitter or receiver collection etc.
	 *  before the actual start of the plugin processing,
	 *  this method might be called several times,
	 *  asking the plugin to re-adapt to the new
	 *  values in the context.
	 *
	 *	@param	context		the context which may serve as
	 *						a hint on how to display the GUI.
	 *	@return	a component containing the plug-in specific
	 *			GUI elements which will be attached to the
	 *			host frame.
	 */
	public JComponent getSettingsView( PlugInContext context );
}