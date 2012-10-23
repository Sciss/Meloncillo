/*
 *  FileWritePrimitive.java
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
 *		16-Jul-04		created
 *		04-Aug-04		commented
 */

package de.sciss.meloncillo.lisp;

import java.io.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Writes data to a random access file. Call:
 *  <pre>
 *  (file-write <var>&lt;id&gt;</var> <var>&lt;object&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>id</code> -		a lisp symbol (usually number or string) identifier for
 *								internal hash table storage. this method will lookup the
 *								hashtable for the entry.</li>
 *  <li><code>size</code> -		object to write</li>
 *  </ul>
 *  File writing starts at zero offset when the file is opened. Successive writes
 *  continuously increase the offset. In a future version, there might be an additional
 *  seek command.
 *  <p>
 *  Supported objects to write are lists of integers whose int values are truncated
 *  to 8bit and written as a series of raw bytes; integer values are written as 32bit ints
 *  ints, reals are written as 32bit floats, everything else is converted its string
 *  representation and converted to a byte array. Strings can contain escape sequences.
 *  <p>
 *  Note that you can use the file identifier in <code>(osc-bundle-send)</code> in order
 *  to append a supercollider format osc message to the file.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		OSCBundleSendPrimitive
 *  @see		FileOpenPrimitive
 */
public class FileWritePrimitive
extends BasicLispPrimitive
{
	AdvancedJatha lisp;

	public FileWritePrimitive( AdvancedJatha lisp )
	{
		super( lisp, "FILE-WRITE", 2, 2 );		//  <id> <object>

		this.lisp = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue			objVal	= machine.S.pop();
		Object				id		= machine.S.pop().toJava();
		Object				o;
		LispValue			elem;
		RandomAccessFile	raf;
		String				str;
		int					i, len;
		LispValue			result  = f_lisp.NIL;
		
		try {
			o   = lisp.getObject( id );
			if( o != null ) {
				if( o instanceof RandomAccessFile ) {
					raf = (RandomAccessFile) o;
					if( objVal.basic_listp() ) {		// list of ints are written as a sequence of bytes
						len = objVal.basic_length();
						for( i = 0; i < len; i++ ) {
							elem	= objVal.car();
							objVal  = objVal.cdr();
							if( !elem.basic_integerp() ) {
								System.err.println( getResourceString( "errLispWrongArgType" ) + " : "+functionName );
								return;
							}
							raf.write( (byte) ((LispNumber) elem).getLongValue() );
						}
					} else if( objVal.basic_numberp() ) {
						if( objVal.basic_integerp() ) {		// integers are written as ints
							raf.writeInt( (int) ((LispNumber) objVal).getLongValue() );
							len = 4;
						} else {							// reals are written as floats
							raf.writeFloat( (float) ((LispNumber) objVal).getDoubleValue() );
							len = 4;
						}
					} else if( objVal == f_lisp.NIL || objVal == f_lisp.T ) {   // T and NIL are written as boolean
						raf.writeBoolean( objVal == f_lisp.T );
						len = 1;
					} else {								// everything else is written as string
						str		= AdvancedJatha.replaceEscapeChars( objVal.toStringSimple() );
						raf.writeBytes( str );
						len = str.length();
					}
					result  = f_lisp.makeInteger( len );
				} else {
					System.err.println( getResourceString( "errLispWrongObjType" ) + " : "+functionName );
				}
			} else {
				System.err.println( getResourceString( "errLispObjNotFound" ) + " \""+functionName+ "\" : " + id );
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