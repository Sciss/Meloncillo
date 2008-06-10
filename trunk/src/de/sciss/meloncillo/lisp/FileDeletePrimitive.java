/*
 *  FileDeletePrimitive.java
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
 *		16-Jul-04		created
 *		04-Aug-04		commented
 */

package de.sciss.meloncillo.lisp;

import java.io.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Deletes a file. Function call:
 *  <pre>
 *  (file-delete <var>&lt;pathname&gt;</var>)
 *  </pre>
 *  Tries to delete the file denoted by the pathname lisp string. Returns
 *  T on success, NIL on failure.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		TempFileMakePrimitive
 */
public class FileDeletePrimitive
extends BasicLispPrimitive
{
	public FileDeletePrimitive( AdvancedJatha lisp )
	{
		super( lisp, "FILE-DELETE", 1, 1 );		//  <path>
	}
	
	public void Execute( SECDMachine machine )
	{
		String				path	= machine.S.pop().toStringSimple();
		File				f		= new File( path );
		LispValue			result  = f.delete() ? f_lisp.T : f_lisp.NIL;

		machine.S.push( result );
		machine.C.pop();
	}
}