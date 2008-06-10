/*
 *  ExecutePrimitive.java
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

import de.sciss.app.*;

/**
 *  Custom Lisp function:
 *  Executes a shell command with optional arguments and environment variables
 *  The function call is:
 *  <pre>
 *  (execute <var>&lt;callback&gt;</var> <var>&lt;cmd-list&gt;</var> <var>&lt;env-list&gt;</var> <var>&lt;cwd&gt;</var>)
 *  </pre>
 *  the arguments are:
 *  <ul>
 *  <li><code>callback</code> - a lisp function which will be evaluated each time the
 *								the executed command outputs text on the stderr or stdout
 *								stream. the function is passed one argument which is a
 *								complete line of text. can be NIL if you don't want to use
 *								the callback. the execute function is special, it does not
 *								evaluate the first argument, thus it must not be quoted.</li>
 *  <li><code>cmd-list</code> -	a list whose first element is the shell command path (string) followed by
 *								optional string arguments which are passed to the command</li>
 *  <li><code>env-list</code> -	optional list of environment variables (or NIL). each element
 *								must be a cons cell whose car is the var name (string) and whose cdr
 *								is the var value (string)</li>
 *  <li><code>cwd</code> -		current work directory (string) in which the command will
 *								be executed.</li>
 *  </ul>
 *  The command is executed synchronously and returns when the command exits. It returns the
 *  return value (integer) of the command, where zero usually means the command was successful.
 *  During the execution the callback function is called whenever new terminated string lines
 *  are available from the standard error or output stream. It is not possible to interrupt
 *  the execution from within the callback function. This might be the case in a future version.
 *  However in a particular context, e.g. in a rendering process, when the user cancels the
 *  lisp execution by pressing the stop button, the command process is killed and the function
 *  returns -1.
 *  <p>
 *  This class is abstract since particular interaction mechanism are depending on the 
 *  context of the lisp environment. <code>LispPlugIn</code> uses a concrete (private)
 *  subclass of <code>ExecutePrimitive</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		de.sciss.meloncillo.plugin.LispPlugIn
 */
public abstract class ExecutePrimitive
extends BasicLispPrimitive
{
	public ExecutePrimitive( AdvancedJatha lisp )
	{
		super( lisp, "EXECUTE", 4, 4 );		// <parse-output-func> <cmd-list> <env-list> <work-dir>
	}
	
	public void Execute( SECDMachine machine )
	{
		LispValue   workDir		= machine.S.pop();
		LispValue   envList		= machine.S.pop();
		LispValue   cmdList		= machine.S.pop();
		LispValue   parseFunc	= machine.S.pop();
		int			result		= -1;
		int			i;
		String[]	cmdArray;
		String[]	envArray	= null;

		try {
			if( !(parseFunc.basic_null() || (parseFunc.fboundp() == f_lisp.T)) ||
				!(envList.basic_null() || envList.basic_listp()) ||
				!cmdList.basic_listp() ) {

				System.err.println( AbstractApplication.getApplication().getResourceString( "errLispWrongArgType" ) +
					" : "+functionName );
				return;
			}
			
			cmdArray	= new String[ cmdList.basic_length() ];
			for( i = 0; i < cmdArray.length; i++ ) {
				cmdArray[ i ]   = cmdList.car().toStringSimple();
				cmdList			= cmdList.cdr();
			}
			if( envList.basic_listp() ) {
				envArray	= new String[ envList.basic_length() ];
				for( i = 0; i < envArray.length; i++ ) {
					envArray[ i ]   = envList.car().toStringSimple();
					envList			= envList.cdr();
				}
			}
			result  = consumeExec( cmdArray, envArray, new File( workDir.toStringSimple() ),
								   parseFunc.basic_null() ? null : parseFunc );
		}
		catch( IOException e1 ) {
			System.err.println( e1 );
		}
		finally {
			machine.S.push( f_lisp.makeInteger( result ));
			machine.C.pop();
		}
	}

	public LispValue CompileArgs( LispCompiler compiler, SECDMachine machine, LispValue args,
								  LispValue valueList, LispValue code)
	throws CompilerException
	{
		// Don't evaluate the first arg. (load it as a constant)
		return( f_lisp.makeCons( machine.LDC, f_lisp.makeCons( args.first(),
				compiler.compileArgsLeftToRight( args.cdr(), valueList, code ))));
	}

	public abstract int consumeExec( String[] cmdArray, String[] envArray, File workDir, LispValue parseFunc )
	throws IOException;
}