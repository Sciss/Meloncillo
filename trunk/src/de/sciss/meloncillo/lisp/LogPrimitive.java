/*
 *  LogPrimitive.java
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
 *		17-Jul-04		created
 *		04-Aug-04		commented
 */
 
package de.sciss.meloncillo.lisp;

import org.jatha.compile.*;
import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Calculates the logarithm a given number. Call:
 *  <pre>
 *  (log <var>&lt;number&gt;</var> [<var>&lt;base&gt;</var>])
 *  </pre>
 *  <ul>
 *  <li><code>number</code> -	number of which the logarithm shall be calculated</li>
 *  <li><code>base</code> -		base of the logarithm. if not specified, the
 *								natural logarithm will be calculated.</li>
 *  </ul>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		java.lang.Math#log( double )
 */
public class LogPrimitive
extends BasicLispPrimitive
{
	public LogPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "LOG", 1, 2 );
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue   args	= machine.S.pop();
		LispValue   first   = args.first();
		LispValue   second  = args.basic_length() == 2 ? args.second() : null;
		LispValue   result  = f_lisp.NIL;
		
		try {
			if( !first.basic_numberp() || (second != null && !first.basic_numberp()) ) {
				System.err.println( getResourceString( "errLispWrongArgType" ) + " : "+functionName );
				return;
			}
			
			if( second == null ) {
				result  = f_lisp.makeReal( Math.log( ((LispNumber) first).getDoubleValue() ));
			} else {
				result  = f_lisp.makeReal( Math.log( ((LispNumber) first).getDoubleValue() ) /
										   Math.log( ((LispNumber) second).getDoubleValue() ));
			}
		}
		finally {
			machine.S.push( result );
			machine.C.pop();
		}
	}

	// Variable number of evaluated args.
	public LispValue CompileArgs( LispCompiler compiler, SECDMachine machine, LispValue args,
								  LispValue valueList, LispValue code )
    throws CompilerException
	{
		return compiler.compileArgsLeftToRight( args, valueList, f_lisp.makeCons(
												machine.LIS, f_lisp.makeCons( args.length(), code )));
	}
}