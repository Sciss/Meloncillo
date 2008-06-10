/*
 *  ByteBufferWritePrimitive.java
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
 *		25-Jul-04   created
 *		04-Aug-04   commented
 */

package de.sciss.meloncillo.lisp;

import java.nio.*;

import org.jatha.compile.*;
import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Writes data to a byte buffer. Call:
 *  <pre>
 *  (byte-buffer-write <var>&lt;id&gt;</var> <var>&lt;object&gt;</var> [<var>&lt;repeats&gt;</var>])
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>id</code> -		a lisp symbol (usually number or string) identifier for
 *								internal hash table storage. this method will lookup the
 *								hashtable for the entry.</li>
 *  <li><code>size</code> -		object to write</li>
 *  <li><code>repeats</code> -	write the object this many times in succession</li>
 *  </ul>
 *  Buffer writes start at zero offset when the buffer is allocated. Successive writes
 *  continuously increase the offset. In a future version, there might be an additional
 *  seek command.
 *  <p>
 *  Supported objects to write are lists of integers whose int values are truncated
 *  to 8bit and written as a series of raw bytes; integer values are written as 32bit ints
 *  ints, reals are written as 32bit floats, everything else is converted its string
 *  representation and converted to a byte array. Strings can contain escape sequences.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		ByteBufferAllocPrimitive
 *  @see		DatagramChWritePrimitive
 *  @see		AdvancedJatha#replaceEscapeChars( String )
 */
public class ByteBufferWritePrimitive
extends BasicLispPrimitive
{
	AdvancedJatha lisp;

	public ByteBufferWritePrimitive( AdvancedJatha lisp )
	{
		super( lisp, "BYTE-BUFFER-WRITE", 2, 3 );		//  <id> <object> [<repeats>]

		this.lisp = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue			args	= machine.S.pop();
		Object				id		= args.first().toJava();
		Object				o		= lisp.getObject( id );
		LispValue			objVal  = args.second();
		LispValue			elem;
		ByteBuffer			b;
		int					i, j, len, repeats;
		float				f1;
		LispValue			result  = f_lisp.NIL;
		byte[]				buf;
		
		try {
			if( args.basic_length() == 3 ) {
				elem = args.third();
				if( !elem.basic_integerp() ) {
					System.err.println( getResourceString( "errLispWrongArgType" ) + " : "+functionName );
					return;
				}
				repeats = (int) ((LispNumber) elem).getLongValue();
			} else {
				repeats = 1;
			}
		
			if( o != null ) {
				if( o instanceof ByteBuffer ) {
					b = (ByteBuffer) o;
					if( objVal.basic_listp() ) {		// list of ints are written as a sequence of bytes
						len = objVal.basic_length();
						buf = new byte[ len ];
						for( i = 0; i < len; i++ ) {
							elem	= objVal.car();
							objVal  = objVal.cdr();
							if( !elem.basic_integerp() ) {
								System.err.println( getResourceString( "errLispWrongArgType" ) + " : "+functionName );
								return;
							}
							buf[i] = (byte) ((LispNumber) elem).getLongValue();
						}
						for( i = 0; i < repeats; i++ ) {
							b.put( buf );
						}
					} else if( objVal.basic_numberp() ) {
						if( objVal.basic_integerp() ) {		// integers are written as ints
							j = (int) ((LispNumber) objVal).getLongValue();
							for( i = 0; i < repeats; i++ ) {
								b.putInt( j );
							}
							len = 4;
						} else {							// reals are written as floats
							f1  = (float) ((LispNumber) objVal).getDoubleValue();
							for( i = 0; i < repeats; i++ ) {
								b.putFloat( f1 );
							}
							len = 4;
						}
					} else {								// everything else is written as string
						buf	= AdvancedJatha.replaceEscapeChars( objVal.toStringSimple() ).getBytes();
						for( i = 0; i < repeats; i++ ) {
							b.put( buf );
						}
						len = buf.length;
					}
					result  = f_lisp.makeInteger( len * repeats );
				} else {
					System.err.println( getResourceString( "errLispWrongObjType" ) + " : "+functionName );
				}
			} else {
				System.err.println( getResourceString( "errLispObjNotFound" ) + " \""+functionName+ "\" : " + id );
			}
		}
		catch( BufferOverflowException e1 ) {
			System.err.println( e1 );
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