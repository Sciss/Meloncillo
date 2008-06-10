/*
 *  DatagramChWritePrimitive.java
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
 *		25-Jul-04	created
 *		04-Aug-04	commented
 *		26-May-05	renamed to DatagramChWritePrimitive for 31-characters filename limit
 */

package de.sciss.meloncillo.lisp;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Writes data to a new datagram channel. Function call:
 *  <pre>
 *  (datagram-channel-open <var>&lt;id&gt;</var> <var>&lt;object&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>id</code> -		a lisp symbol (usually number or string) identifier for
 *								internal hash table storage. this method will lookup the
 *								datagram channel using the identifier.</li>
 *  <li><code>object</code> -	object to write. this must be a bytebuffer identifier at
 *								the moment.</li>
 *  </ul>
 *  The byte buffer is completely written out, independant from the current buffer offset.
 *  A future version might provide options for offset and length.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		ByteBufferAllocPrimitive
 *  @see		ByteBufferWritePrimitive
 *  @see		DatagramChOpenPrimitive
 *  @see		OboundpPrimitive
 */
public class DatagramChWritePrimitive
extends BasicLispPrimitive
{
	AdvancedJatha lisp;

	public DatagramChWritePrimitive( AdvancedJatha lisp )
	{
		super( lisp, "DATAGRAM-CHANNEL-WRITE", 2, 2 );		//  <dch id> <bb id>

		this.lisp = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		Object		o		= lisp.getObject( machine.S.pop().toJava() );
		Object		dch		= lisp.getObject( machine.S.pop().toJava() );
		LispValue   result  = f_lisp.NIL;
		ByteBuffer  b;
		
		try {
			if( o != null && dch != null ) {
				if( o instanceof ByteBuffer && dch instanceof DatagramChannel ) {
					b = (ByteBuffer) o;
					b.clear();
					((DatagramChannel) dch).write( b );
					result = f_lisp.makeInteger( b.limit() );
				} else {
					System.err.println( getResourceString( "errLispWrongObjType" ) + " : "+functionName );
				}
			} else {
				System.err.println( getResourceString( "errLispObjNotFound" ) + " \""+functionName+ "\"" );
			}
		}
		catch( IOException e1 ) {
			System.err.println( e1 );
		}
		finally {
			machine.S.push( result );
			machine.C.pop();
		}
	}
}