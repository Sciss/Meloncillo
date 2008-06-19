/*
 *  CurrentTimeMillisPrimitive.java
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
 *		25-Jul-04   created
 *		04-Aug-04   commented
 */
 
package de.sciss.meloncillo.lisp;

import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Gets the current system time in milliseconds. Call:
 *  <pre>
 *  (current-time-millis)
 *  </pre>
 *  The returned value is a <code>LispInteger</code> with
 *  milliseconds as returned by <code>System.currentTimeMillis()</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		OSCBundleSendPrimitive
 *  @see		System#currentTimeMillis()
 */
public class CurrentTimeMillisPrimitive
extends BasicLispPrimitive
{
	public CurrentTimeMillisPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "CURRENT-TIME-MILLIS", 0, 0 );
	}
	
	public void Execute( SECDMachine machine )
	{
		machine.S.push( f_lisp.makeInteger( System.currentTimeMillis() ));
		machine.C.pop();
	}
}
