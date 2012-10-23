/*
 *  AudioFileOpenPrimitive.java
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
 *		16-Jul-04		created
 *		04-Aug-04		commented
 */

package de.sciss.meloncillo.lisp;

import java.io.*;

import org.jatha.compile.*;
import org.jatha.dynatype.*;
import org.jatha.machine.*;

import de.sciss.gui.*;
import de.sciss.io.*;

/**
 *  Custom Lisp function:
 *  Opens an audio file. The function call is:
 *  <pre>
 *  (audio-file-open <var>&lt;id&gt;</var> <var>&lt;path&gt;</var> [<var>&lt;type&gt;</var> <var>&lt;res&gt;</var> <var>&lt;rate&gt;</var> <var>&lt;channels&gt;</var>])
 *  </pre>
 *  where the two argument version opens the file for reading and
 *  the six argument version for writing. the arguments are:
 *  <ul>
 *  <li><code>id</code> -		a lisp symbol (usually number or string) identifier for
 *								internal hash table storage. this method will create the
 *								new hashtable entry.</li>
 *  <li><code>path</code> -		file name string
 *  <li><code>type</code> -		file format string, one of "AIFF", "IRCAM" or "AU"</li>
 *  <li><code>res</code>  -		sample resolution string, one of "int16", "int24", "int32" or "float32"</li>
 *  <li><code>rate</code> -		sample rate lisp number in hertz</li>
 *  <li><code>channels</code> - number of channels lisp integer</li>
 *  </ul>
 *  Use <code>(file-close)</code> to close the file.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		FileClosePrimitive
 *  @see		TempFileMakePrimitive
 *  @see		OboundpPrimitive
 *  @see		de.sciss.meloncillo.io.AudioFile
 */
public class AudioFileOpenPrimitive
extends BasicLispPrimitive
{
	private final AdvancedJatha lisp;

	public AudioFileOpenPrimitive( AdvancedJatha lisp )
	{
		super( lisp, "AUDIO-FILE-OPEN", 2, 6 );		//  <id> <path> [<type={AIFF|IRCAM|AU}> <res={int16|int24|int32|float32}> <rate> <channels>]
		
		this.lisp = lisp;
	}

	public void Execute( SECDMachine machine )
	{
		AudioFile			af;
		LispValue			args			= machine.S.pop();
		Object				id				= args.first().toJava();
		String				path			= args.second().toStringSimple();
		File				f				= new File( path );
		LispValue			arg;
		AudioFileDescr		afd;
		String				str;
		StringItem[]		formats;
		int					i;
		
		try {
			if( args.basic_length() == 2 ) {
				af							= AudioFile.openAsRead( f );
			} else {
				afd							= new AudioFileDescr();
				afd.channels				= 1;							// default
				afd.rate					= 44100f;						// default
				afd.bitsPerSample			= 24;							// default
				afd.sampleFormat			= AudioFileDescr.FORMAT_INT;	// default
				str							= args.third().toStringSimple().toLowerCase();
				formats						= AudioFileDescr.getFormatItems();
				for( i = 0; i < formats.length; i++ ) {
					if( formats[i].getKey().equals( str )) break;
				}
				if( i == formats.length ) {
					System.err.println( getResourceString( "errLispWrongArgValue" ) + " : "+functionName );
					return;
				}
				afd.type					= i;

				if( args.basic_length() >= 4 ) {
					str						= args.fourth().toStringSimple().toLowerCase();
					if( str.equals( "int16" )) {
						afd.bitsPerSample   = 16;
						afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
					} else if( str.equals( "int24" )) {
						afd.bitsPerSample   = 24;
						afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
					} else if( str.equals( "int32" )) {
						afd.bitsPerSample   = 32;
						afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
					} else if( str.equals( "float32" )) {
						afd.bitsPerSample   = 32;
						afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
					} else {
						System.err.println( getResourceString( "errLispWrongArgValue" ) + " : "+functionName );
						return;
					}
					
					if( args.basic_length() >= 5 ) {
						arg					= args.fifth();
						if( arg.basic_numberp() ) {
							afd.rate		= (float) ((LispNumber) arg).getDoubleValue();
						} else {
							System.err.println( getResourceString( "errLispWrongArgValue" ) + " : "+functionName );
							return;
						}
						
						if( args.basic_length() >= 6 ) {
							arg				= args.sixth();
							if( arg.basic_numberp() ) {
								afd.channels= (int) ((LispNumber) arg).getLongValue();
							} else {
								System.err.println( getResourceString( "errLispWrongArgValue" ) + " : "+functionName );
								return;
							}
						}
					}
				}
						
				afd.file					= f;
				af							= AudioFile.openAsWrite( afd );
			}
			lisp.addObject( id, af );
		}
		catch( IOException e1 ) {
			System.err.println( e1 );
		}
		finally {
			machine.S.push( args.first() );
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