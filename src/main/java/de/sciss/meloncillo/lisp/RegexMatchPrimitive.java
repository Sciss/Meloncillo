/*
 *  RegexMatchPrimitive.java
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

import java.util.regex.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  The function takes a regular exporession pattern
 *  and a string to match. result is a list of cons
 *  describing the found groups (begin . end) or NIL.
 *  see java.util.regex.Matcher
 *  and java.util.regex.Pattern for details of operation.
 */
/**
 *  Custom Lisp function:
 *  Parses a string by comparing it to a regular expression and returning
 *  the found groups.
 *  Function call:
 *  <pre>
 *  (format-parse <var>&lt;pattern&gt;</var> <var>&lt;str&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>pattern</code> -	a lisp string describing the regular expression pattern.
 *								the pattern format is the one specified in
 *								<code>java.util.regex.Pattern</code></li>
 *  <li><code>str</code>   -	a formatted string which is matched to the pattern</li>
 *  </ul>
 *  The function returns a list of groups that match the pattern. Each group is a
 *  cons cell whose car is the beginning index in the string (starting at zero) and
 *  whose cdr is the stopping index in the string (exclusive), such that
 *  <code>(substring str (car (elt result group-index)) (cdr (elt result group-index)))</code>
 *  returns the substring corresponding to group <code><var>group-index</var></code>.
 *  The result may be NIL if no matches are found.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		java.util.regex.Pattern#compile( String )
 *  @see		java.util.regex.Pattern#matcher( CharSequence )
 *  @see		java.util.regex.Matcher#find()
 *
 *  @todo		uses hardcoded <code>Locale.US</code> at the moment.
 */
public class RegexMatchPrimitive
extends BasicLispPrimitive
{
	public RegexMatchPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "REGEX-MATCH", 2, 2 );		// <pattern> <realization>
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue   seqVal  = machine.S.pop();
		LispValue   ptrnVal = machine.S.pop();
		Matcher		ma;
		LispValue   list	= f_lisp.NIL;
				
		try {
			ma = Pattern.compile( ptrnVal.toStringSimple() ).matcher( seqVal.toStringSimple() );
			while( ma.find() ) {
				list = list.append( f_lisp.makeList( f_lisp.makeCons(
					f_lisp.makeInteger( ma.start() ), f_lisp.makeInteger( ma.end() ))));
			}
		}
		catch( PatternSyntaxException e1 ) {
			System.err.println( e1 );
		}
		
		machine.S.push( list );
		machine.C.pop();
	}
}
