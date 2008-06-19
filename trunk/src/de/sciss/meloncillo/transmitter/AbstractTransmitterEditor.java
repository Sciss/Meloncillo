/*
 *  AbstractTransmitterEditor.java
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
 *		02-Sep-04   commented. slight changes
 *      27-Dec-04   added online help
 */

package de.sciss.meloncillo.transmitter;

import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.session.*;

import de.sciss.app.*;

/**
 *  A simple implementation of the <code>TransmitterEditor</code>
 *  interface that does not yet make assumptions
 *  about the supported transmitters but provides some
 *  common means useful for all transmitters.
 *  It handles initialization.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public abstract class AbstractTransmitterEditor
extends JPanel
implements TransmitterEditor
{
	protected Transmitter	trns	= null;
	protected Session		doc		= null;
	protected Main			root	= null;

	/**
	 *  Creates a new editor. Initialization
	 *  is performed by calling the <code>init</code>
	 *  method.
	 */
	protected AbstractTransmitterEditor()
	{
		super();
//        HelpGlassPane.setHelp( this, "TransmitterEditor" );	// EEE
    }

	/**
	 *  This implementation will call
	 *  <code>getHandledTransmitters</code> and
	 *  scan the list for the queried transmitter
	 *  class.
	 */
	public boolean canHandle( Transmitter trns )
	{
		java.util.List  coll = getHandledTransmitters();
		int				i;
		Class			trnsClass;
		
		for( i = 0; i < coll.size(); i++ ) {
			trnsClass	= (Class) coll.get( i );
			if( trnsClass.isInstance( trns )) return true;
		}
		return false;
	}

	public Transmitter getTransmitter()
	{
		return trns;
	}

	public JComponent getView()
	{
		return this;
	}

	/**
	 *  Subclasses should override this and
	 *  call the superclass implementation
	 *  at first
	 */
	public void init( Main root, Session doc, Transmitter trns )
	{
		if( this.canHandle( trns )) {
			this.trns	= trns;
			this.root   = root;
			this.doc	= doc;
		} else {
			throw new IllegalArgumentException( trns.getClass().getName() +
				AbstractApplication.getApplication().getResourceString( "transmitterExceptionNotHandled" ));
		}
	}
}