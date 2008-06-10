/*
 *  FileOpenPrimitive.java
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
 */

package de.sciss.meloncillo.lisp;

import java.io.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Opens a file for low level data i/o. Function call:
 *  <pre>
 *  (file-open <var>&lt;id&gt;</var> <var>&lt;path&gt;</var> <var>&lt;mode&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>id</code> -		a lisp symbol (usually number or string) identifier for
 *								internal hash table storage. this method will create the
 *								new hashtable entry.</li>
 *  <li><code>path</code> -		pathname (string)</li>
 *  <li><code>mode</code> -		file mode (string)</li>
 *  </ul>
 *  Filemode can be "r" for read-only access, "rw" for read-and-write access, and
 *  "w" for read-and-write access which will delete a preexisting file when the function
 *  opens the file. <code>(temp-file-make)</code> is a convenient way to create
 *  temporary path names whose associated files will automatically be deleted when the
 *  lisp interpreter quits.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		FileWritePrimitive
 *  @see		FileClosePrimitive
 *  @see		TempFileMakePrimitive
 *  @see		OboundpPrimitive
 */
public class FileOpenPrimitive
extends BasicLispPrimitive
{
	AdvancedJatha lisp;

	public FileOpenPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "FILE-OPEN", 3, 3 );		//  <id> <path> <mode>
		
		this.lisp = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		RandomAccessFile	raf;
		String				mode	= machine.S.pop().toStringSimple();
		String				path	= machine.S.pop().toStringSimple();
		Object				id		= machine.S.pop().toJava();
		File				f		= new File( path );
		LispValue			result  = f_lisp.NIL;
		
		try {
			if( mode.equals( "w" )) {
				mode	= "rw";
				if( f.exists() ) f.delete();
			}
			raf		= new RandomAccessFile( f, mode );
			lisp.addObject( id, raf );
		}
		catch( IOException e1 ) {
			System.err.println( e1 );
		}
		catch( IllegalArgumentException e2 ) {
			System.err.println( e2 );
		}
		finally {
			machine.S.push( result );
			machine.C.pop();
		}
	}
}