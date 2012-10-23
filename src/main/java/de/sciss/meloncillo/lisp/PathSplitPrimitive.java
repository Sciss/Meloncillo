/*
 *  PathSplitPrimitive.java
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

import org.jatha.machine.*;

/**
 *  Custom Lisp function:
 *  Splits a path into a cons cell
 *  consisting of the parent and the child file.
 *  Function call:
 *  <pre>
 *  (path-split <var>&lt;pathname&gt;</var>)
 *  </pre>
 *  Returns a cons cell whose car is the parent path string and
 *  whose cdr is the last part of the pathname (file or folder) relative
 *  to the parent path. So if, say, you have a pathstring and want
 *  to go up one directory and down to a new subdirectory "test", you'd
 *  write: <code>(path-concat (car (path-split aPathString)) "test")</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		java.io.File#getParent()
 *  @see		java.io.File#getName()
 *  @see		PathConcatPrimitive
 */
public class PathSplitPrimitive
extends BasicLispPrimitive
{
	public PathSplitPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "PATH-SPLIT", 1, 1 );
	}
	
	public void Execute( SECDMachine machine )
	{
		File f	= new File( machine.S.pop().toStringSimple() );
		
		machine.S.push( f_lisp.makeCons(
			f_lisp.makeString( f.getParent() ), f_lisp.makeString( f.getName() )));
		machine.C.pop();
	}
}
