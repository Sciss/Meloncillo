/*
 *  PrintLnPrimitive.java
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
 *		16-Jul-04		created
 *		04-Aug-04		commented
 */
 
package de.sciss.meloncillo.lisp;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Convenient print method
 *  that doesn't put the strange
 *  quotes around Strings like
 *  <code>(print)</code> does and, what's more important,
 *  it puts a newline at the <strong>end</strong> of
 *  the string. Function call:
 *  <pre>
 *  (println <var>&lt;object&gt;</var>)
 *  </pre>
 *  The object is converted into its string representation and
 *  escape sequences are replaced if found. This string is then
 *  printed to the console. The non-escaped string is returned
 *  for convenience.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		java.io.PrintStream#println( String )
 *  @see		AdvancedJatha#replaceEscapeChars( String )
 */
public class PrintLnPrimitive
extends BasicLispPrimitive
{
	public PrintLnPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "PRINTLN", 1, 1 );
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue   gaga	= machine.S.pop();
	
		System.out.println( AdvancedJatha.replaceEscapeChars( gaga.toStringSimple() ));
		
		machine.S.push( gaga );
		machine.C.pop();
	}
}
