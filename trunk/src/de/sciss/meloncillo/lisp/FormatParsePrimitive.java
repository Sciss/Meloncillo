/*
 *  FormatParsePrimitive.java
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
 *		11-Jul-04		created
 *		04-Aug-04		commented
 */

package de.sciss.meloncillo.lisp;

import java.text.*;
import java.util.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Parses a string by comparing it to a pattern and extracting the patterns placeholders.
 *  Function call:
 *  <pre>
 *  (format-parse <var>&lt;pattern&gt;</var> <var>&lt;str&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>pattern</code> -	a lisp string describing the pattern and the placeholders.
 *								the pattern format is the one specified in
 *								<code>java.text.MessageFormat</code></li>
 *  <li><code>str</code>   -	a formatted string which must match the pattern</li>
 *  </ul>
 *  The function returns the parsed arguments as a list whose element indices
 *  correspond to the placeholder indices in the pattern, or NIL if an error occurs.
 *  Doubles become lisp reals, integers become lisp integer, strings become lisp strings.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		java.text.MessageFormat
 *  @see		java.text.MessageFormat#parse( String )
 *
 *  @todo		uses hardcoded <code>Locale.US</code> at the moment.
 */
public class FormatParsePrimitive
extends BasicLispPrimitive
{
	public FormatParsePrimitive( AdvancedJatha lisp )
	{
		super( lisp, "FORMAT-PARSE", 2, 2 );		// <pattern> <realization>
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue   seqVal  = machine.S.pop();
		LispValue   ptrnVal	= machine.S.pop();
		LispValue   list	= f_lisp.NIL;
		Object[]	parsed;
		Object		o;
		LispValue   lo;
		int			i;
				
		try {
			parsed = new MessageFormat( ptrnVal.toStringSimple(), Locale.US ).parse( seqVal.toStringSimple() );
			for( i = 0; i < parsed.length; i++ ) {
				o		= parsed[ i ];
				if( o == null ) {
					lo  = f_lisp.NIL;
				} else if( o instanceof Number ) {
					if( o instanceof Integer ) {
						lo = f_lisp.makeInteger( ((Number) o).intValue() );
					} else if( o instanceof Long ) {
						lo = f_lisp.makeInteger( ((Number) o).longValue() );
					} else if( o instanceof Float ) {
						lo = f_lisp.makeReal( ((Number) o).floatValue() );
					} else {
						lo = f_lisp.makeReal( ((Number) o).doubleValue() );
					}
				} else {
					lo  = f_lisp.makeString( o.toString() );
				}
				list	= list.append( f_lisp.makeList( lo ));
			}
		}
		catch( IllegalArgumentException e1 ) {
			System.err.println( e1 );
		}
		catch( ParseException e2 ) {
			System.err.println( e2 );
		}
		
		machine.S.push( list );
		machine.C.pop();
	}
}
