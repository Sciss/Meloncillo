/*
 *  PathConcatPrimitive.java
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
 *		13-Jul-04		created
 *		04-Aug-04		commented
 */
 
package de.sciss.meloncillo.lisp;

import java.io.*;

import org.jatha.dynatype.*;
import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Concatenates a parent file and a
 *  child file into a new string which
 *  denotates the absolute path. Function call:
 *  <pre>
 *  (path-concat <var>&lt;parent&gt;</var> <var>&lt;child&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>parent</code> -   a lisp string with a parent folder path</li>
 *  <li><code>child</code> -   a lisp string with a child path (file or folder}
 *								relative to the parent.</li>
 *  </ul>
 *  Returns a properly concatenated new path string using the native file
 *  separator string between path components. Furthermore tries to find the absolute
 *  path denoted by the new string.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		java.io.File#File( String, String )
 *  @see		java.io.File#getAbsolutePath()
 *  @see		PathSplitPrimitive
 */
public class PathConcatPrimitive
extends BasicLispPrimitive
{
	public PathConcatPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "PATH-CONCAT", 2, 2 );		// <parent> <child>
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue child		= machine.S.pop();
		LispValue parent	= machine.S.pop();
		
		machine.S.push( f_lisp.makeString( new File(
			parent.toStringSimple(), child.toStringSimple() ).getAbsolutePath() ));
		machine.C.pop();
	}
}
