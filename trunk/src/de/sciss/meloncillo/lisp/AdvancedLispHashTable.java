/*
 *  AdvancedLispHashTable.java
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
 */
 
package de.sciss.meloncillo.lisp;

import org.jatha.*;
import org.jatha.dynatype.*;

/**
 *  A simple subclass of <code>StandardLispHashTable</code>
 *  that allows the easy choice of a test-type.
 *  <code>StandardLispHashTable</code>'s variables <code>EQ, EQL</code> etc.
 *  are not public and besides are not static and
 *  not initialized when the constructor is called.
 *  Because internally a <code>==</code> comparision is used,
 *  the only way to use them is to look up
 *  the <code>LispValue</code> in the keyword package...
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class AdvancedLispHashTable
extends StandardLispHashTable
{
	/**
	 *  test type : object identity (== operator)
	 */
	public static final int TYPE_EQ		= 0;
	/**
	 *  test type : not supported yet
	 */
	public static final int TYPE_EQL	= 1;
	/**
	 *  test type : representation identity (obj1.toString().equals( obj2.toString() ))
	 */
	public static final int TYPE_EQUAL  = 2;
	/**
	 *  test type : not supported yet
	 */
	public static final int TYPE_EQUALP	= 3;

	private final int testType;

	/**
	 *  Creates a new hashtable for the given
	 *  initial and growing table parameters and
	 *  the specified test type
	 *
	 *  @param  lisp				the Lisp environment to use
	 *  @param  testType			TYPE_EQ or TYPE_EQUAL
	 *  @param  sizeArg				hash table initial size
	 *  @param  rehashSizeArg		hash table growing size
	 *  @param  rehashThresholdArg  hash table growing threshold
	 */
	public AdvancedLispHashTable( Jatha lisp, int testType, LispValue sizeArg,
								  LispValue rehashSizeArg, LispValue rehashThresholdArg )
	{
		super( lisp, findTypeValue( lisp, testType ), sizeArg, rehashSizeArg, rehashThresholdArg );
		
		this.testType	= testType;
	}
	
	private static LispValue findTypeValue( Jatha lisp, int testType )
	{
		String keyword;
		
		switch( testType ) {
		case TYPE_EQ:		keyword = "EQ-HASH-TABLE";		break;
		case TYPE_EQL:		keyword = "EQL-HASH-TABLE";		break;
		case TYPE_EQUAL:	keyword = "EQUAL-HASH-TABLE";	break;
		case TYPE_EQUALP:   keyword = "EQUALP-HASH-TABLE";	break;
		default:			assert false : testType;		return null;
		}
		
		return lisp.EVAL.intern( keyword, (LispPackage) lisp.findPackage( "KEYWORD" ));
	}

	public LispValue hash_table_test()
	{
		return type;
	}

	public LispValue gethash( LispValue key, LispValue defawlt )
	{
		LispValue result = (LispValue) theHashTable.get( lispToJavaKey( key ));
		
		if( result == null ) {
			return defawlt;
		} else {
			return result;
		}
	}

	public LispValue remhash( LispValue key )
	{
		LispValue result = (LispValue) theHashTable.remove( lispToJavaKey( key ));
		
		if( result == null ) {
			return f_lisp.NIL;
		} else {
			return result;
		}
	}
	
	public LispValue setf_gethash( LispValue key, LispValue value )
	{
		theHashTable.put( lispToJavaKey( key ), value );
		
		return value;
	}

	private Object lispToJavaKey( LispValue lispKey )
	{
		switch( testType ) {
		case TYPE_EQ:
			return lispKey;
		case TYPE_EQUAL:
			return lispKey.toString();
		default:
			assert false : "Unsupported hash test-type: " + type.toString();
			return null;
		}
	}
}
