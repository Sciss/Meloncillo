/*
 *  TempFileMakePrimitive.java
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
 *		13-Jul-04		created
 *		04-Aug-04		commented
 */

package de.sciss.meloncillo.lisp;

import java.io.*;

import org.jatha.compile.*;
import org.jatha.dynatype.*;
import org.jatha.machine.*;

import de.sciss.io.IOUtil;

/**
 *  Custom Lisp function:
 *  Creates a new temporary file name. Function call:
 *  <pre>
 *  (temp-file-make [<var>&lt;suffix&gt;</var> [<var>&lt;prefix&gt;</var>]])
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>suffix</code> -	optional file name suffix (string), should begin with a colon
 *								and not be longer than four characters.</li>
 *  <li><code>prefix</code> -	optional prefix for the pathname (string)</li>
 *  </ul>
 *  The function returns a pathname string suitable for passing to <code>(file-open)</code>
 *  or similar functions. The file is marked temporary and will be deleted automatically
 *  when the lisp interpreter quits. However it is good programming style to take
 *  responsibility for explicit file deletion within the lisp script, using
 *  <code>(file-delete)</code>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		FileOpenPrimitive
 *  @see		FileDeletePrimitive
 */
public abstract class TempFileMakePrimitive
extends BasicLispPrimitive
{
	public TempFileMakePrimitive( AdvancedJatha lisp )
	{
		super( lisp, "TEMP-FILE-MAKE", 0, 2 );  // [<suffix> [<prefix>]]
	}
	
	public void Execute( SECDMachine machine )
	{
		File		f;
		LispValue   result  = f_lisp.NIL;
		LispValue   args	= machine.S.pop();

		try {
			switch( args.basic_length() ) {
			case 0:
				f	= IOUtil.createTempFile();
				break;
			case 1:
				f	= IOUtil.createTempFile( "cillo", args.first().toStringSimple() );
				break;
			case 2:
				f	= IOUtil.createTempFile( args.second().toStringSimple(), args.first().toStringSimple() );
				break;
			default:
				assert false : args.basic_length();
				return;
			}
			consumeFile( f );
			result  = f_lisp.makeString( f.getAbsolutePath() );
		}
		catch( IOException e1 ) {
			System.err.println( e1 );
		}
		finally {
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

	public abstract void consumeFile( File f ) throws IOException;
}