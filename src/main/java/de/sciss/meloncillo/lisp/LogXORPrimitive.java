/*
 *  LogXORPrimitive.java
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
 *		14-Mar-05	created
 */
 
package de.sciss.meloncillo.lisp;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Calculates the bitwise exclusive OR or two integers. Call:
 *  <pre>
 *  (logxor <var>&lt;int1&gt;</var> <var>&lt;int2&gt;</var>)
 *  </pre>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class LogXORPrimitive
extends BasicLispPrimitive
{
	public LogXORPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "LOGXOR", 2, 2 );
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue   second	= machine.S.pop();
		LispValue   first	= machine.S.pop();
		LispValue   result  = f_lisp.NIL;

		try {
			if( !first.basic_integerp() || (second != null && !first.basic_integerp()) ) {
				System.err.println( getResourceString( "errLispWrongArgType" ) + " : "+functionName );
				return;
			}
			
			result  = f_lisp.makeInteger( ((LispNumber) first).getLongValue() ^ ((LispNumber) second).getLongValue() );
		}
		finally {
			machine.S.push( result );
			machine.C.pop();
		}
	}
}