/*
 *  DatagramChClosePrimitive.java
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
 *		23-Jul-04		created
 *		04-Aug-04		commented
 *		26-May-05		renamed to DatagramChClosePrimitive for 31-characters filename limit
 */

package de.sciss.meloncillo.lisp;

import java.io.*;
import java.nio.channels.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Closes a new datagram channel. Function call:
 *  <pre>
 *  (datagram-channel-close <var>&lt;id&gt;</var>)
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
 *  @see		DatagramChOpenPrimitive
 *  @see		OboundpPrimitive
 */
public class DatagramChClosePrimitive
extends BasicLispPrimitive
{
	AdvancedJatha lisp;

	public DatagramChClosePrimitive( AdvancedJatha lisp )
	{
		super( lisp, "DATAGRAM-CHANNEL-CLOSE", 1, 1 );		//  <id>

		this.lisp = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue	idVal   = machine.S.pop();
		Object		id		= idVal.toJava();
		Object		o;
		
		try {
			o   = lisp.removeObject( id );
			if( o != null ) {
				if( o instanceof DatagramChannel ) {
					((DatagramChannel) o).disconnect();
					((DatagramChannel) o).close();
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
			machine.S.push( idVal );
			machine.C.pop();
		}
	}
}