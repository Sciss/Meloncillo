/*
 *  NumberEvent.java
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
 *		07-Jun-04   created
 *		22-Jul-04   new method incorporate()
 *		31-Jul-04   commented
 */

package de.sciss.meloncillo.gui;

import de.sciss.app.*;

/**
 *  This kind of event is fired
 *  from a <code>NumberField</code> gadget when
 *  the user modified its contents.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	NumberField#addNumberListener( NumberListener )
 *  @see	NumberListener
 *  @see	java.lang.Number
 */
public class NumberEvent
extends BasicEvent
{
// --- ID values ---
	/**
	 *  returned by getID() : the number changed
	 */
	public static final int CHANGED		= 0;

	private final Number	number;

	/**
	 *  Constructs a new <code>NumberEvent</code>
	 *
	 *  @param  source  who originated the action
	 *  @param  ID		<code>CHANGED</code>
	 *  @param  when	system time when the event occured
	 *  @param  number  the new number
	 */
	public NumberEvent( Object source, int ID, long when, Number number )
	{
		super( source, ID, when );
	
		this.number		= number;
	}
	
	/**
	 *  Queries the new number
	 *
	 *  @return the new <code>Number</code> of the
	 *			<code>NumberField</code>. This is either
	 *			an <code>Long</code> or a <code>Double</code>
	 *			depening of the <code>NumberField</code>'s
	 *			<code>NumberSpace</code>.
	 *
	 *  @see	de.sciss.meloncillo.math.NumberSpace#isInteger()
	 */
	public Number getNumber()
	{
		return number;
	}

	public boolean incorporate( BasicEvent oldEvent )
	{
		if( oldEvent instanceof NumberEvent &&
			this.getSource() == oldEvent.getSource() &&
			this.getID() == oldEvent.getID() ) {
			
			return true;

		} else return false;
	}
}
