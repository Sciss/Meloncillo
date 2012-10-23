/*
 *  OSCBundleSendWaitPrimitive.java
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
 *		 7-Jul-04	created
 *		24-Jul-04	not abstract any more; deals with files and datagram channels.
 *		27-Jul-04	now deals with incoming bundles.
 *		04-Aug-04	commented
 *		26-May-05	renamed to OSCBundleSendWaitPrimitive for 31-characters filename limit
 *		25-Apr-08	fixed to work with current NetUtil version
 */

package de.sciss.meloncillo.lisp;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.regex.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

import de.sciss.net.*;

/**
 *  Custom Lisp function:
 *  Sends a list of OSC commands as a bundle and waits for a reply.
 *  This function does the same as <code>(osc-bundle-send)</code>, but
 *  takes two addional arguments: a response string and
 *  a timeout integer in milliseconds. The target-id must
 *  be linked to ta datagram channel. After the packet is send
 *  to the channels target, this function waits on the channel's
 *  local reply port for a maximum time span given by the timeout
 *  value. If an incoming OSC packet contains a message whose command
 *  string matches the given response string, the function returns
 *  immediately with the received message as a list result. If the
 *  timeout passes, the function returns NIL. Note that the response
 *  string can be a regular expression, so each incoming message
 *  satisfying the pattern will successfully terminate the function.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		OSCBundleSendPrimitive
 *  @see		DatagramChOpenPrimitive
 *  @see		de.sciss.meloncillo.net.OSCBundle
 *  @see		de.sciss.meloncillo.net.OSCReceiver
 */
public class OSCBundleSendWaitPrimitive
extends BasicLispPrimitive
{
	private AdvancedJatha   lisp;
	private byte[]			buf		= new byte[ 8192 ];
	private ByteBuffer		byteBuf	= ByteBuffer.wrap( buf );

	public OSCBundleSendWaitPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "OSC-BUNDLE-SEND-AND-WAIT", 5, 5 );	// <target id> <when> <command-list> <response> <timeout>
		
		this.lisp   = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue			timeout		= machine.S.pop();
		LispValue			response	= machine.S.pop();

		LispValue			args		= machine.S.pop();
		LispValue			when		= machine.S.pop();
		Object				id			= machine.S.pop().toJava();
		Object				o			= lisp.getObject( id );
		DatagramChannel		dch;
		DatagramSocket		ds;
		DatagramPacket		dp;
		long				startTime, stopTime;
		Pattern				ptrn;
		OSCMessage			msg;
		OSCPacket			p;
		LispValue			arg;
		int					i;
		LispValue			result		= f_lisp.NIL;

		try {
			if( o != null ) {
				if( o instanceof DatagramChannel ) {
					dch	= (DatagramChannel) o;
				} else {
					System.err.println( getResourceString( "errLispWrongObjType" ) + " : "+functionName );
					return;
				}
			} else {
				System.err.println( getResourceString( "errLispObjNotFound" ) + " \""+functionName+ "\" : " + id );
				return;
			}
			if( !timeout.basic_integerp() ) {
				System.err.println( getResourceString( "errLispWrongArgType" ) + " : "+functionName );
				return;
			}

			ptrn = Pattern.compile( response.toStringSimple() );
			
			if( !OSCBundleSendPrimitive.send( dch, false, when, args, byteBuf )) return;
			
			startTime   = System.currentTimeMillis();
			stopTime	= ((LispNumber) timeout).getLongValue() + startTime;
			while( stopTime > startTime ) {
				ds = dch.socket();
				ds.setSoTimeout( (int) (stopTime - startTime) );
//System.err.println( "socket.isConnected() ? "+ds.isConnected()+"; isBound() ? "+ds.isBound()+"; isClosed() ? "+ds.isClosed());
				byteBuf.clear();
				dp = new DatagramPacket( buf, buf.length );
				ds.receive( dp );
//System.err.println( "received!" );
				p   = OSCPacket.decode( byteBuf );
				msg = findMessage( p, ptrn );
				if( msg != null ) {
					args	= f_lisp.NIL;
					for( i = msg.getArgCount() - 1; i >= 0; i-- ) {
						o = msg.getArg( i );
						if( o instanceof Integer ) {
							arg = f_lisp.makeInteger( ((Integer) o).intValue() );
						} else if( o instanceof Float ) {
							arg = f_lisp.makeReal( ((Float) o).floatValue() );
						} else if( o instanceof String ) {
							arg = f_lisp.makeString( (String) o );
						} else if( o instanceof Double ) {
							arg = f_lisp.makeReal( ((Double) o).doubleValue() );
						} else if( o instanceof Long ) {
							arg = f_lisp.makeInteger( ((Long) o).longValue() );
						} else {
							arg = f_lisp.NIL;
						}
						args = f_lisp.makeCons( arg, args );
					}
					result = f_lisp.makeCons( f_lisp.makeString( msg.getName() ), args );
					return;
				}
				startTime = System.currentTimeMillis();
			} // while( stopTime > startTime )
		}
		catch( SocketTimeoutException e11 ) {}
		catch( IOException e1 ) {
			System.err.println( e1 );
		}
		catch( PatternSyntaxException e2 ) {
			System.err.println( e2 );
		}
		finally {
			machine.S.push( result );
			machine.C.pop();
		}
	}

	private static OSCMessage findMessage( OSCPacket p, Pattern ptrn )
	throws IOException
	{
		OSCMessage msg;
		
		if( p instanceof OSCMessage ) {
			msg = (OSCMessage) p;
			if( ptrn.matcher( msg.getName() ).matches() ) return msg;
		} else {
			OSCBundle bndl = (OSCBundle) p;
			for( int i = 0; i < bndl.getPacketCount(); i++ ) {
				msg = findMessage( bndl.getPacket( i ), ptrn );
				if( msg != null ) return msg;
			}
		}
		return null;
	}
}