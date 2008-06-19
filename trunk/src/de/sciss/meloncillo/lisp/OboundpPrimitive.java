/*
 *  OboundpPrimitive.java
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
 *		17-Jul-04		created
 *		04-Aug-04		commented
 */

package de.sciss.meloncillo.lisp;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Checks if an object stored in the global special objects hash exists. Function call:
 *  <pre>
 *  (oboundp <var>&lt;id&gt;</var>)
 *  </pre>
 *  See <code>AdvancedJatha</code> for a brief description of the global
 *  java objects hash. Functions that create java objects include
 *  <code>(file-open)</code> and <code>(byte-buffer-alloc)</code> and
 *  <code>(datagram-channel-open)</code>. These functions store their
 *  newly created object in the global object table of the advanced lisp
 *  environment and use a specified idenfier as the hashing key. Using
 *  this function, you can check if the hashtable contains an object
 *  for a given key. Note that the objects are removed by certain
 *  functions, for example when closing a file or freeing a buffer
 *  using <code>(file-close)</code> and <code>(buffer-free)</code>,
 *  the hash entries are cleared automatically. Hence, by using the
 *  <code>(oboundp)</code> function, you can find out if an object
 *  was already properly disposed or not. Returns <code>T</code> if
 *  the object exists, otherwise <code>NIL</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		FileOpenPrimitive
 *  @see		ByteBufferAllocPrimitive
 *  @see		AdvancedJatha#getObject( Object )
 */
public class OboundpPrimitive
extends BasicLispPrimitive
{
	private AdvancedJatha lisp;

	public OboundpPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "OBOUNDP", 1, 1 );		//  <id>
		
		this.lisp = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		Object		id		= machine.S.pop().toJava();
		LispValue	result  = lisp.getObject( id ) == null ? f_lisp.NIL : f_lisp.T;

		machine.S.push( result );
		machine.C.pop();
	}
}