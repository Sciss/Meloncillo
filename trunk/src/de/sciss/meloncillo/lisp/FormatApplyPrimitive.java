/*
 *  FormatApplyPrimitive.java
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
 *		15-Jul-04		created
 *		04-Aug-04		commented
 */

package de.sciss.meloncillo.lisp;

import java.text.*;
import java.util.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

import de.sciss.app.*;

/**
 *  Custom Lisp function:
 *  Formats a message pattern using a pattern string and a list of arguments. Function call:
 *  <pre>
 *  (format-apply <var>&lt;pattern&gt;</var> <var>&lt;args&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>pattern</code> -	a lisp string describing the pattern and the placeholders.
 *								the pattern format is the one specified in
 *								<code>java.text.MessageFormat</code></li>
 *  <li><code>args</code>   -	a list of args which are referenced in the pattern, e.g.
 *								lisp numbers for a <code>{<var>n</var>,number}</code> pattern.</li>
 *  </ul>
 *  The function returns the formatted string or NIL if an error occurs.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		java.text.MessageFormat
 *  @see		java.text.MessageFormat#format( String, Object[] )
 *
 *  @todo		uses hardcoded <code>Locale.US</code> at the moment.
 */
public class FormatApplyPrimitive
extends BasicLispPrimitive
{
	public FormatApplyPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "FORMAT-APPLY", 2, 2 );	// <pattern> <args>
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue   objList = machine.S.pop();
		LispValue   ptrnVal	= machine.S.pop();
		LispValue   result	= f_lisp.NIL;
		Object[]	javaObj;
		int			i;
				
		try {
			if( !objList.basic_listp() ) {
				System.err.println( AbstractApplication.getApplication().getResourceString( "errLispWrongArgType" ) +
					" : "+functionName );
				return;
			}
			javaObj	= new Object[ objList.basic_length() ];
			for( i = 0; i < javaObj.length; i++ ) {
				javaObj[ i ]	= objList.car().toJava();
				objList			= objList.cdr();
			}

			result = f_lisp.makeString( new MessageFormat( ptrnVal.toStringSimple(), Locale.US ).format(
										javaObj, new StringBuffer(), null ).toString() );
		}
		catch( IllegalArgumentException e1 ) {
			System.err.println( e1 );
		}
		
		machine.S.push( result );
		machine.C.pop();
	}
}
