/*
 *  DatagramChOpenPrimitive.java
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
 *		24-Jul-04   created
 *		04-Aug-04   commented
 *		26-May-05	renamed to DatagramChOpenPrimitive for 31-characters filename limit
 */

package de.sciss.meloncillo.lisp;

import java.io.*;
import java.net.*;
import java.nio.channels.*;

import org.jatha.compile.*;
import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Opens a new datagram channel for sending/receiving
 *  UDP packets. Function call:
 *  <pre>
 *  (datagram-channel-open <var>&lt;id&gt;</var> <var>&lt;host&gt;</var> [<var>&lt;port&gt;</var>])
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>id</code> -		a lisp symbol (usually number or string) identifier for
 *								internal hash table storage. this method will create the
 *								new hashtable entry.</li>
 *  <li><code>size</code> -		buffer size in bytes</li>
 *  </ul>
 *  If the two argument version is used, host must be a socket address string containing
 *  an IP address followed by a colon and a port number, such as "127.0.0.1:57110". In the
 *  three argument version, host is just the host name, such as "127.0.0.1" or "localhost"
 *  and port is a LispInteger with the socket's port number. Use the
 *  <code>(datagram-channel-write)</code> function to send bytebuffers to the channel,
 *  or use the object id in a <code>(osc-bundle-send)</code> call.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		OSCBundleSendPrimitive
 *  @see		DatagramChWritePrimitive
 *  @see		DatagramChClosePrimitive
 *  @see		OboundpPrimitive
 */
public class DatagramChOpenPrimitive
extends BasicLispPrimitive
{
	AdvancedJatha lisp;

	public DatagramChOpenPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "DATAGRAM-CHANNEL-OPEN", 2, 3 );		//  <id> <hostname or socket addr> [<port>]
		
		this.lisp = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue		args	= machine.S.pop();
		Object			id		= args.first().toJava();
		LispValue		addrVal	= args.second();
		LispValue		portVal	= args.basic_length() == 3 ? args.third() : null;
		LispValue		result  = f_lisp.NIL;
		int				port, i;
		String			hostName;
		DatagramChannel dch		= null;

		try {
			hostName = addrVal.toStringSimple();
			if( portVal == null ) {
				i			= hostName.indexOf( ':' );
				if( i == -1 ) {
					System.err.println( getResourceString( "errLispWrongArgValue" ) + " : "+functionName );
					return;
				}
				port		= Integer.parseInt( hostName.substring( i + 1 ));
				hostName	= hostName.substring( 0, i );
			} else {
				if( !portVal.basic_integerp() ) {
					System.err.println( getResourceString( "errLispWrongArgType" ) + " : "+functionName );
					return;
				}
				port = (int) ((LispNumber) portVal).getLongValue();
			}
			dch		= DatagramChannel.open();
			dch.configureBlocking( true );
			dch.connect( new InetSocketAddress( hostName, port ));
			result  = f_lisp.makeCons( f_lisp.makeString( dch.socket().getLocalAddress().getHostAddress() ),
									   f_lisp.makeInteger( dch.socket().getLocalPort() ));

			lisp.addObject( id, dch );
		}
		catch( IOException e1 ) {
			System.err.println( e1 );
		}
		catch( IllegalArgumentException e2 ) {  // includes NumberFormatException
			System.err.println( e2 );
		}
		finally {
			if( result == f_lisp.NIL && dch != null && dch.isOpen() ) {
				try {
					dch.disconnect();
					dch.close();
				}
				catch( IOException e3 ) {
					System.err.println( e3 );
				}
			}
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