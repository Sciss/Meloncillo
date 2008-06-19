/*
 *  ByteBufferFreePrimitive.java
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
 *		25-Jul-04		created
 *		04-Aug-04		commented
 */

package de.sciss.meloncillo.lisp;

import java.nio.*;

import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Deallocates a byte buffer. Call:
 *  <pre>
 *  (byte-buffer-free <var>&lt;id&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>id</code> -		a lisp symbol (usually number or string) identifier for
 *								internal hash table storage. this method will clear the
 *								hashtable entry.</li>
 *  </ul>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		ByteBufferAllocPrimitive
 *  @see		OboundpPrimitive
 */
public class ByteBufferFreePrimitive
extends BasicLispPrimitive
{
	private AdvancedJatha lisp;

	public ByteBufferFreePrimitive( AdvancedJatha lisp )
	{
		super( lisp, "BYTE-BUFFER-FREE", 1, 1 );  // <id>
		
		this.lisp   = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		Object  id  = machine.S.pop().toJava();
		Object	o   = lisp.removeObject( id );

		if( o == null ) {
			System.err.println( getResourceString( "errLispObjNotFound" ) + " \""+functionName+ "\" : " + id );
		} else if( !(o instanceof ByteBuffer) ) {
			System.err.println( getResourceString( "errLispWrongObjType" ) + " : "+functionName );
		}

		machine.S.push( f_lisp.NIL );
		machine.C.pop();
	}
}