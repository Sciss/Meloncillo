/*
 *  SubstringPrimitive.java
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
 *		7-Jul-4		created
 */
 
package de.sciss.meloncillo.lisp;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  A simple function for Jatha
 *  for calling the already build-in <code>substring</code>
 *  method of a <code>LispValue</code>. Function call:
 *  <pre>
 *  (substring <var>&lt;str&gt;</var> <var>&lt;start&gt;</var> <var>&lt;stop&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>str</code> -		a lisp string.</li>
 *  <li><code>start</code> -	substring begin index (starting at 0 inclusive).</li>
 *  <li><code>stop</code> -		substring end index (starting at 0 exclusive).</li>
 *  </ul>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		org.jatha.dynatype.LispValue#substring( LispValue, LispValue )
 *  @see		java.lang.String#substring( int, int )
 */
public class SubstringPrimitive
extends BasicLispPrimitive
{
	public SubstringPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "SUBSTRING", 3, 3 );
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue stop  = machine.S.pop();
		LispValue start = machine.S.pop();
		LispValue str   = machine.S.pop();
		
		machine.S.push( str.substring( start, stop ));
		machine.C.pop();
	}
}
