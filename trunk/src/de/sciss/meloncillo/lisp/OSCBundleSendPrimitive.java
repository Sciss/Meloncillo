/*
 *  OSCBundleSendPrimitive.java
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
 *		 7-Jul-04		created
 *		24-Jul-04		not abstract any more; deals with files and datagram channels.
 *		04-Aug-04		commented
 */

package de.sciss.meloncillo.lisp;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

import de.sciss.app.*;
import de.sciss.net.*;

/**
 *  Custom Lisp function:
 *  Sends a list of OSC commands as a bundle. Function call:
 *  <pre>
 *  (osc-bundle-send <var>&lt;target-id&gt;</var> <var>&lt;when&gt;</var> <var>&lt;cmd-list&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>target-id</code> - a lisp symbol (usually number or string) identifier for
 *								internal hash table storage. this method will lookup the
 *								java object channel using the identifier. allowed objects
 *								are datagram-channels (sends an udp packet) and random-
 *								access-files created using <code>(file-open)</code>, in
 *								which case a 32bit int giving the packet size is written
 *								followed by the binary OSC packet.</li>
 *  <li><code>when</code> -		bundle time. if integer, it's meant as a system time in
 *								millisecs. if real, it's an offset in a non-realtime-file
 *								in seconds.</li>
 *  <li><code>cmd-list</code> - a list of osc commands. each osc command itself is a list
 *								whose first element is the command name string and the
 *								rest of the arguments are the message arguments.</li>
 *  </ul>
 *  The time tag <code>0.0</code> is interpreted by SuperCollider as a special command:
 *  execute the bundle just as it arrives. For absolute time values, <code>(current-time-millis)</code>
 *  is convenient.
 *  <p>
 *  An example of a command list is:
 *  <pre>
 *    (list (list "/n_run" 1001 1) (list "/sync" 3))
 *  </pre>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		OSCBundleSendWaitPrimitive
 *  @see		DatagramChOpenPrimitive
 *  @see		FileOpenPrimitive
 *  @see		de.sciss.meloncillo.net.OSCBundle
 */
public class OSCBundleSendPrimitive
extends BasicLispPrimitive
{
	private AdvancedJatha   lisp;
	private ByteBuffer		byteBuf	= ByteBuffer.allocateDirect( 8192 );

	public OSCBundleSendPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "OSC-BUNDLE-SEND", 3, 3 );		// <target id> <when> <command-list>
		
		this.lisp   = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		final LispValue					args	= machine.S.pop();
		final LispValue					when	= machine.S.pop();
		final Object					id		= machine.S.pop().toJava();
		final Object					o		= lisp.getObject( id );
		WritableByteChannel				ch;
		boolean							prependSize;
		LispValue						result  = f_lisp.NIL;

		try {
			if( o != null ) {
				if( o instanceof RandomAccessFile ) {
					ch			= ((RandomAccessFile) o).getChannel();
					prependSize = true;
				} else if( o instanceof DatagramChannel ) {
					ch			= (DatagramChannel) o;
					prependSize = false;
				} else {
					System.err.println( getResourceString( "errLispWrongObjType" ) + " : "+functionName );
					return;
				}
			} else {
				System.err.println( getResourceString( "errLispObjNotFound" ) + " \""+functionName+ "\" : " + id );
				return;
			}
			
			if( send( ch, prependSize, when, args, byteBuf )) {
				result = f_lisp.T;
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

	protected static boolean send( WritableByteChannel ch, boolean prependSize, LispValue when,
						    LispValue args, ByteBuffer byteBuf )
	throws OSCException, IOException
	{
		OSCBundle						bnd;
		LispValue						cmd;
		OSCMessage						msg;

		if( args.basic_listp() && when.basic_numberp() ) {
			if( when.basic_integerp() ) {
				bnd = new OSCBundle( ((LispNumber) when).getLongValue() );
			} else {
				bnd = new OSCBundle( ((LispNumber) when).getDoubleValue() );
			}
			for( cmd = args.pop(); !cmd.basic_null(); cmd = args.pop() ) {
				if( cmd.basic_listp() ) {
					msg = makeOSCMsg( cmd );
					bnd.addPacket( msg );
				} else {
					System.err.println( AbstractApplication.getApplication().getResourceString( "errLispWrongArgType" ));
					return false;
				}
			}
			writePacket( ch, prependSize, bnd, byteBuf );
			return true;

		} else {
			System.err.println( AbstractApplication.getApplication().getResourceString( "errLispWrongArgType" ));
			return false;
		}
	}

	/**
	 *  Send out a message to the OSC server
	 */
	private static void writePacket( WritableByteChannel ch, boolean prependSize, OSCPacket p, ByteBuffer byteBuf )
	throws OSCException, IOException
	{
		try {
			byteBuf.clear();
			if( prependSize ) byteBuf.position( 4 );
			p.encode( byteBuf );
			byteBuf.flip();
			if( prependSize ) byteBuf.putInt( byteBuf.limit() - 4 ).rewind();
			ch.write( byteBuf );
		}
		catch( BufferOverflowException e1 ) {
			throw new OSCException( OSCException.BUFFER,
				p instanceof OSCMessage ? ((OSCMessage) p).getName() : p.getClass().getName() );
		}
	}

	private static OSCMessage makeOSCMsg( LispValue args )
	{
		LispValue   cmd		= args.car();
		args				= args.cdr();
		Iterator	iter	= args.iterator();
		int			numArgs = args.basic_length();
		int			i;
		Object[]	javaArgs;

		if( !cmd.basic_stringp() ) {
			System.err.println( AbstractApplication.getApplication().getResourceString( "errLispWrongArgType" ));
			return null;
		}

		javaArgs = new Object[ numArgs ];
		for( i = 0; i < numArgs; i++ ) {
			javaArgs[i] = ((LispValue) iter.next()).toJava();
		}

		return new OSCMessage( ((LispString) cmd).getValue(), javaArgs );
	}
}