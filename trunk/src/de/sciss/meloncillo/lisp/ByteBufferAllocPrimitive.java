/*
 *  ByteBufferAllocPrimitive.java
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

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Allocates a byte buffer. Call:
 *  <pre>
 *  (byte-buffer-alloc <var>&lt;id&gt;</var> <var>&lt;size&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>id</code> -		a lisp symbol (usually number or string) identifier for
 *								internal hash table storage. this method will create the
 *								new hashtable entry.</li>
 *  <li><code>size</code> -		buffer size in bytes</li>
 *  </ul>
 *  Use <code>(byte-buffer-write)</code> to fill the buffer and
 *  <code>(byte-buffer-free)</code> to free the buffer's memory.
 *  The buffer can be send to an OSC port using the <code>(datagram-channel-write)</code>
 *  function.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		ByteBufferWritePrimitive
 *  @see		ByteBufferFreePrimitive
 *  @see		DatagramChWritePrimitive
 *  @see		OboundpPrimitive
 */
public class ByteBufferAllocPrimitive
extends BasicLispPrimitive
{
	private final AdvancedJatha lisp;

	public ByteBufferAllocPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "BYTE-BUFFER-ALLOC", 2, 2 );  // <id> <size>
		
		this.lisp   = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue   sizeVal	= machine.S.pop();
		LispValue	idVal	= machine.S.pop();
		Object		id		= idVal.toJava();

		try {
			if( !sizeVal.basic_integerp() ) {
				System.err.println( getResourceString( "errLispWrongArgType" ) + " : "+functionName );
				return;
			}
			
			lisp.addObject( id, ByteBuffer.allocateDirect( (int) ((LispNumber) sizeVal).getLongValue() ));
		}
		finally {
			machine.S.push( idVal );
			machine.C.pop();
		}
	}
}