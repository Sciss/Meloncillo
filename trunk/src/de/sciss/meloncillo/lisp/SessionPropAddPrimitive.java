/*
 *  SessionPropAddPrimitive.java
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
 *		27-Mar-05	created
 *		26-May-05	renamed to SessionPropAddPrimitive for 31-characters filename limit
 */

package de.sciss.meloncillo.lisp;

import java.io.*;

import org.jatha.compile.*;
import org.jatha.dynatype.*;
import org.jatha.machine.*;

import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.util.*;
import de.sciss.util.NumberSpace;

import de.sciss.gui.*;

/**
 *  A custom lisp function for creating additional properties for
 *	session collection objects (receivers, transmitters, groups)
 *	which will be displayed in the observer palette.
 *  <p>
 *  The lisp function is called as follows:<br>
 *  <pre>
 *	(session-property-add <var>&lt;object-group&gt;</var> <var>&lt;label&gt;</var> <var>&lt;key&gt;</var> <var>&lt;type&gt;</var> [<var>&lt;default-value&gt;</var> [<var>&lt;options&gt;</var>]])
 *  </pre><br>
 *	<code>object-group</code> is one of the following strings: "RECEIVERS", "TRANSMITTERS",
 *	"GROUPS". <code>label</code> is a string that will be displayed
 *	in the palette. <code>key</code> is a string that can be used to query the property value
 *	from the session-object's hash table. <code>type</code> can be one of the following
 *	"INTEGER", "LONG", "FLOAT", "DOUBLE", "BOOLEAN", "STRING", "FILE". <code>options</code>
 *	specifies constraints for value display.
 *	<p>
 *	Note that the actual map-key has an additional prefix "-lisp" to
 *	avoid naming conflicts in a flat hierarchy. As a lisp programmer you
 *	never have to deal with this, you'll only recognize in the session xml file.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	de.sciss.meloncillo.util.MapManager
 */
public class SessionPropAddPrimitive
extends BasicLispPrimitive
{
	private final Session			doc;
	private String					dynamic	= null;

	private static final String[]	TYPE_STR	= {
		"INTEGER", "LONG", "FLOAT", "DOUBLE", "BOOLEAN", "STRING", "FILE"
	};

	public SessionPropAddPrimitive( AdvancedJatha lisp, Session doc )
	{
		super( lisp, "SESSION-PROPERTY-ADD", 4, 6 );
		
		this.doc		= doc;
	}

	public void setDynamic( String dynamic )
	{
		this.dynamic	= dynamic;
	}
	
	// sync : wait exclusive on the appropriate doors
	public void Execute( SECDMachine machine )
	{
		// (session-property-add <object-group> <label> <key> <type> [<default-value> [<options>]])
		final LispValue					args			= machine.S.pop();
		final String					objGrp			= args.first().toStringSimple().toUpperCase();
		final String					label			= args.second().toStringSimple();
		final String					key				= "lisp-" + args.third().toStringSimple();
		final String					typeStr			= args.fourth().toStringSimple().toUpperCase();
		final LispValue					defaultLisp		= args.fifth();
		LispValue						options			= args.sixth();
		LispValue						option;
		SessionObject					so;
		SessionCollection				sc;
		MapManager.Context				mc;
		int								type			= -1;
		int								doors;
		Object							constraints		= null;
		Object							defaultJava		= null;
	
		try {
			if( dynamic == null ) {
				System.err.println( getResourceString( "errLispWrongContext" )+" : "+functionName );
				return;
			}
		
			if( objGrp.equals( "RECEIVERS" )) {
				sc		= doc.receivers;
				doors	= Session.DOOR_RCV;
			} else if( objGrp.equals( "TRANSMITTERS" )) {
				sc		= doc.transmitters;
				doors	= Session.DOOR_TRNS;
			} else if( objGrp.equals( "GROUPS" )) {
				sc		= doc.groups;
				doors	= Session.DOOR_GRP;
			} else {
				System.err.println( functionName+" : "+getResourceString( "errLispWrongArgValue" )+" : "+objGrp );
				return;
			}

			for( int i = 0; i < TYPE_STR.length; i++ ) {
				if( typeStr.equals( TYPE_STR[ i ])) {
					type = i;
					break;
				}
			}
			if( type == -1 ) {
				System.err.println( functionName+" : "+getResourceString( "errLispWrongArgValue" )+" : "+typeStr );
				return;
			}
			switch( type ) {
			case MapManager.Context.TYPE_INTEGER:
			case MapManager.Context.TYPE_LONG:
			case MapManager.Context.TYPE_FLOAT:
			case MapManager.Context.TYPE_DOUBLE:
				if( defaultLisp.basic_numberp() ) {
					switch( type ) {
					case MapManager.Context.TYPE_INTEGER:
						defaultJava = new Integer( (int) ((LispNumber) defaultLisp).getLongValue() );
						break;
					case MapManager.Context.TYPE_LONG:
						defaultJava = new Long( ((LispNumber) defaultLisp).getLongValue() );
						break;
					case MapManager.Context.TYPE_FLOAT:
						defaultJava = new Float( ((LispNumber) defaultLisp).getDoubleValue() );
						break;
					case MapManager.Context.TYPE_DOUBLE:
						defaultJava = new Double( ((LispNumber) defaultLisp).getDoubleValue() );
						break;
					}
				}

				if( options.basic_length() >= 3 ) {	// NumberSpace from (min max quant)
					double min, max, quant;
					
					option  = options.car();
					options = options.cdr();
					if( option.basic_numberp() ) {
						min = ((LispNumber) option).getDoubleValue();
						option  = options.car();
						options = options.cdr();
						if( option.basic_numberp() ) {
							max = ((LispNumber) option).getDoubleValue();
							option  = options.car();
							options = options.cdr();
							if( option.basic_numberp() ) {
								quant		= ((LispNumber) option).getDoubleValue();
								constraints	= new NumberSpace( min, max, quant );
							}
						}
					}
				}
				break;

			case MapManager.Context.TYPE_BOOLEAN:
				if( defaultLisp.basic_numberp() ) {
					defaultJava = new Boolean( defaultLisp.zerop() == f_lisp.NIL );
				}
				break;

			case MapManager.Context.TYPE_STRING:
				if( defaultLisp.basic_stringp() ) {
					defaultJava = defaultLisp.toStringSimple();
					if( options.basic_listp() && options.basic_length() > 0 ) {
						StringItem[] items = new StringItem[ options.basic_length() ];
						for( int i = 0; options.basic_length() > 0; i++ ) {
							option  = options.car();
							options = options.cdr();
							if( option.basic_consp() ) {
								items[ i ] = new StringItem( option.car().toStringSimple(),
															 option.cdr().toStringSimple() );
							} else {
								System.err.println( functionName+" : "+getResourceString( "errLispWrongArgType" )+
													" : "+option.toStringSimple() );
								return;
							}
						}
						constraints = items;
					}
				}
				break;

			case MapManager.Context.TYPE_FILE:
				if( defaultLisp.basic_stringp() ) {
					defaultJava = new File( defaultLisp.toStringSimple() );
				}

				int		flags = 0;
				String	s;

				while( options.basic_length() > 0 ) {	// list of flags
					option  = options.car();
					options = options.cdr();
					if( option.basic_stringp() ) {
						s = option.toStringSimple().toUpperCase();
						if( s.equals( "INPUT" )) {
							flags = (flags & ~PathField.TYPE_BASICMASK) | PathField.TYPE_INPUTFILE;
						} else if( s.equals( "OUTPUT" )) {
							flags = (flags & ~PathField.TYPE_BASICMASK) | PathField.TYPE_OUTPUTFILE;
						} else if( s.equals( "FOLDER" )) {
							flags = (flags & ~PathField.TYPE_BASICMASK) | PathField.TYPE_FOLDER;
						} else if( s.equals( "FORMAT" )) {
							flags |= PathField.TYPE_FORMATFIELD;
						}
					}
				}
				
				if( flags != 0 ) constraints = new Integer( flags );
				break;

			default:
				assert false : type;
				break;
			}
			
			if( defaultLisp != f_lisp.NIL && defaultJava == null ) {
				System.err.println( functionName+" : "+getResourceString( "errLispWrongArgValue" )+
									" : "+defaultLisp.toStringSimple() );
				return;
			}

			mc = new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY |
										 MapManager.Context.FLAG_DYNAMIC,
										 type, constraints, label, dynamic, defaultJava );
			try {
				doc.bird.waitExclusive( doors );
				for( int i = 0; i < sc.size(); i++ ) {
					so = sc.get( i );
					so.getMap().putContext( this, key, mc );
				}
			}
			finally {
				doc.bird.releaseExclusive( doors );
			}
		}
		finally {
			machine.S.push( f_lisp.NIL );
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