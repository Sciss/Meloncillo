/*
 *  AdvancedJatha.java
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
 *		07-Jul-04	created
 *		04-Aug-04   commented
 *		30-Dec-04	ConcatPrimitive removed because Jatha 2.3 offers its own
 *					(concatenate) function.
 *		17-Apr-05	added PI constant to symbol table
 */
 
package de.sciss.meloncillo.lisp;

import java.util.*;
import java.util.regex.*;

import org.jatha.*;
import org.jatha.compile.*;
import org.jatha.dynatype.*;

/**
 *  A subclass of <code>org.jatha.Jatha</code>
 *  that adds some useful methods
 *  and custom lisp functions.
 *  <p>
 *  Each instance maintaines a
 *  java object list which is accessible
 *  from certain custom lisp functions
 *  (e.g. audio-file-open) by a hash key identifier.
 *  Since normal lisp functions cannot
 *  deal with these objects (files, sockets,
 *  buffers) directly anyway, there's little
 *  use in wrapping them inside a <code>StandardLispJavaObject</code>
 *  class. Besides accessing these objects is
 *  fast through the global hashtable and 
 *  these objects are designed for a limited lifetime
 *  during plug-in processing, hence after the processing
 *  is finished they can be easily deallocated even if
 *  the lisp code fails.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		AudioFileOpenPrimitive
 */
public class AdvancedJatha
extends Jatha
{
	private final HashMap mapIdsToObjects   = new HashMap();
	private static final Pattern escapePtrn	= Pattern.compile( "\\\\n|\\\\0x[0-9A-F]{2}" );
	private static final byte[] hex			= { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1, -1,
												10, 11, 12, 13, 14, 15 };

	/**
	 *  Creates a new Jatha environment.
	 *  It will automatically be initialized and started.
	 *  No GUI and no console are used.
	 *  All non-abstract primitives from <code>de.sciss.meloncillo.lisp</code>
	 *  will be registered.
	 */
	public AdvancedJatha()
	{
		super( false, false );
		init();
		start();
		
		LispValue val;
	
		// --------------- register custom primitives ---------------
		addPrimitive( new AudioFileOpenPrimitive( this ));
		addPrimitive( new ByteBufferAllocPrimitive( this ));
		addPrimitive( new ByteBufferFreePrimitive( this ));
		addPrimitive( new ByteBufferWritePrimitive( this ));
//		addPrimitive( new ConcatPrimitive( this ));
		addPrimitive( new CurrentTimeMillisPrimitive( this ));
		addPrimitive( new DatagramChClosePrimitive( this ));
		addPrimitive( new DatagramChOpenPrimitive( this ));
		addPrimitive( new DatagramChWritePrimitive( this ));
		addPrimitive( new FileClosePrimitive( this ));
		addPrimitive( new FileDeletePrimitive( this ));
		addPrimitive( new FileOpenPrimitive( this ));
		addPrimitive( new FileWritePrimitive( this ));
		addPrimitive( new FormatApplyPrimitive( this ));
		addPrimitive( new FormatParsePrimitive( this ));
		addPrimitive( new LogPrimitive( this ));
		addPrimitive( new LogANDPrimitive( this ));
		addPrimitive( new LogIORPrimitive( this ));
		addPrimitive( new LogXORPrimitive( this ));
		addPrimitive( new OboundpPrimitive( this ));
		addPrimitive( new OSCBundleSendPrimitive( this ));
		addPrimitive( new OSCBundleSendWaitPrimitive( this ));
		addPrimitive( new PathConcatPrimitive( this ));
		addPrimitive( new PathSplitPrimitive( this ));
		addPrimitive( new PowPrimitive( this ));
		addPrimitive( new PrintLnPrimitive( this ));
		addPrimitive( new RegexMatchPrimitive( this ));
		addPrimitive( new SubstringPrimitive( this ));

		// --------------- add useful variables ---------------
		// PI is already a public field in Jatha but not registered?!
//		intern( "PI", new StandardLispConstant( this, "PI", PI ));	// doesn't work
		val = makeConstant( "PI" );
		val.setf_symbol_value( makeReal( Math.PI ));
		EVAL.intern( (LispString) val.symbol_name(), val );
	}
	
	/**
	 *  Replaces escape characters
	 *  by corresponding ASCII chars,
	 *  at the moment <code>\n</code> for newline
	 *  and <code>\0xAB</code> for a generic ascii byte (A, B = <code>'0...9/A-F'</code>)
	 *  are supported.
	 *
	 *  @param  str		input string, possibly containing escape sequences
	 *  @return a new string with escape codes properly replaced or the input string
	 *			if it did not contain supported escape sequences
	 */
	public static String replaceEscapeChars( String str )
	{
		Matcher m   = escapePtrn.matcher( str );
		if( !m.find() ) return str;
		
		int				i		= 0;
		String			esc;
		StringBuffer	strBuf  = new StringBuffer( str.length() );
		
		do {
			strBuf.append( str.substring( i, m.start() ));
			i   = m.end();
			esc = str.substring( m.start(), i );
			if( esc.equals( "\\n" )) {
				strBuf.append( '\n' );
			} else {
				strBuf.append( (char) ((hex[ ((byte) esc.charAt( 3 )) - 48 ] << 4) |
									    hex[ ((byte) esc.charAt( 4 )) - 48 ]));
			}
		} while( m.find() );
		
		strBuf.append( str.substring( i ));
		
		return strBuf.toString();
	}
	
	/**
	 *  Adds a Java object to the
	 *  global object map.
	 *
	 *  @param  key		hash key to handle the object
	 *  @param  obj		arbitrary object value
	 *
	 *  @see	#getObject( Object )
	 *  @see	java.util.Map#put( Object, Object )
	 */
	public void addObject( Object key, Object obj )
	{
		mapIdsToObjects.put( key, obj );
	}

	/**
	 *  Queries a Java object from the
	 *  global object map.
	 *  
	 *  @param  key		hash identifier key
	 *  @return the mapped object or <code>null</code>
	 *			if the key isn't mapped to an object.
	 *
	 *  @see	#addObject( Object, Object )
	 *  @see	java.util.Map#get( Object )
	 */
	public Object getObject( Object key )
	{
		return mapIdsToObjects.get( key );
	}
	
	/**
	 *  Removes an object from the global
	 *  object map and return it.
	 *  
	 *  @param  key		hash identifier key
	 *  @return the mapped (removed) object or <code>null</code>
	 *			if the key isn't mapped to an object.
	 *
	 *  @see	#addObject( Object, Object )
	 *  @see	java.util.Map#remove( Object )
	 */
	public Object removeObject( Object key )
	{
		return mapIdsToObjects.remove( key );
	}
	
	/**
	 *  Removess all objects from the global
	 *  object map.
	 *  
	 *  @see	java.util.Map#clear()
	 */
	public void removeAllObjects()
	{
		mapIdsToObjects.clear();
	}
	
	/**
	 *  Dumps a listed of mapped objects
	 *  to the console (System.err).
	 */
	public void debugDump()
	{
		Iterator	it = mapIdsToObjects.keySet().iterator();
		Object		key, val;
		
		System.err.println( "Dumping AdvancedJatha object map:" );
		while( it.hasNext() ) {
			key = it.next();
			val = mapIdsToObjects.get( key );
			System.err.println( "key class = "+key.getClass().getName()+"; val = "+key.toString() );
			System.err.println( "val class = "+(val != null ? val.getClass().getName() : "---")+"; val = "+val.toString() );
		}
	}
	
	/**
	 *  Registers a new function
	 *  to the compiler/interpreter
	 *
	 *  @param  p   the custom function to make available to
	 *				the lisp compiler
	 */
	public void addPrimitive( LispPrimitive p )
	{
		COMPILER.Register( p );
	}
	
	/**
	 *  Registers a new symbol in the interpreter
	 *  environment.
	 *
	 *  @param  symbolName		the name under which the symbol is available
	 *							to the system
	 *  @param  symbolValue		the initial symbol value
	 *  @return					the newly created symbol
	 */
	public LispValue intern( String symbolName, LispValue symbolValue )
	{
		return EVAL.intern( symbolName ).setf_symbol_value( symbolValue );
	}

	/**
	 *  Creates a new empty Lisp-HashTable.
	 *
	 *  @param  compareType		test-type for getting hash values.
	 *							Use StandardLispHashTable.EQ, .EQUAL or .EQL.
	 *							EQ returns true if the objects have the
	 *							same memory location (i.e. like java's == operator).
	 *							EQUAL returns true if the text output of both
	 *							objects are equal (i.e. like java.lang.Object.equal()).
	 *  @param  initialSize		the initial size of the backing HashMap.
	 *							expansion ratio and threshold are set to jatha's default.
	 *  @return the newly created empty hash table
	 *
	 *  @see org.jatha.dynatype.StandardLispHashTable
	 */
	public LispHashTable makeHashTable( int compareType, int initialSize )
	{
		return new AdvancedLispHashTable( this, compareType, makeInteger( initialSize ), NIL, NIL );
	}

	/**
	 *  Creates a new empty Lisp-HashTable with a specified initial size.
	 *
	 *  @param  initialSize		the initial size of the backing HashMap.
	 *							expansion ratio and threshold are set to jatha's default.
	 *							the test-type is set to EQUAL.
	 *  @return the newly created empty hash table
	 *
	 *  @see org.jatha.dynatype.StandardLispHashTable
	 */
	public LispHashTable makeHashTable( int initialSize )
	{
		return makeHashTable( AdvancedLispHashTable.TYPE_EQUAL, initialSize );
	}

	/**
	 *  Creates a new empty Lisp-HashTable with default properties.
	 *  the test-type is set to EQUAL.
	 *
	 *  @return the newly created empty hash table
	 *
	 *  @see org.jatha.dynatype.StandardLispHashTable
	 */
	public LispHashTable makeHashTable()
	{
		// java's default hashmap size (jatha's default of >100 is ineffective)
		return makeHashTable( AdvancedLispHashTable.TYPE_EQUAL, 16 );
	}
	
	/**
	 *  Finds a function by its name
	 *
	 *  @param  token   uppercase java string with the function's name
	 *  @return the symbol to which the function is bound or <code>null</code> if the token
	 *			is unknown or doesn't represent a symbol with a bound function
	 */
	public LispValue findFunction( String token )
	{
		LispValue val = (LispValue) getSymbolTable().get( token );
		if( val != null && val.basic_symbolp() && val.fboundp() == T ) {
			return val;
		} else {
			return null;
		}
	}

	/**
	 *  Finds a symbol by its name
	 *
	 *  @param  token   uppercase java string with the symbol's name
	 *  @return		the symbol or <code>null</code> if the token is unknown or doesn't represent a symbol
	 */
	public LispValue findSymbol( String token )
	{
		LispValue val = (LispValue) getSymbolTable().get( token );
//		LispValue val = getEval().intern(token, (LispPackage) pkg)  PARSER.tokenToLispValue( token );
		if( val != null && val.basic_symbolp() ) {
			return val;
		} else {
			return null;
		}
	}
}
