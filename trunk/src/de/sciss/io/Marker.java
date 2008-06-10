/*
 *  Marker.java
 *  de.sciss.io package
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
 *		21-May-05	created from de.sciss.eisenkraut.util.Marker
 */

package de.sciss.io;

import java.util.ArrayList;
import java.util.List;

/**
 *  A struct class: marker in
 *  an audio file. (copied from FScape).
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.10, 21-May-05
 *
 *  @see	de.sciss.meloncillo.io.AudioFileDescr#KEY_MARKERS
 */
public class Marker
{
// -------- public Variablen --------
	/**
	 *  A marker's position in sample frames
	 */
	public final long	pos;
	/**
	 *  A marker's name
	 */
	public final String	name;

// -------- public Methoden --------

	/**
	 *  Constructs a new immutable marker
	 *
	 *  @param  pos		position in sample frames
	 *  @param  name	marker's name
	 */
	public Marker( long pos, String name )
	{
		this.pos	= pos;
		this.name	= name;
	}
	
	/**
	 *  Constructs a new immutable marker
	 *  identical to a given marker.
	 *
	 *  @param  orig	the marker to copy
	 */
	public Marker( Marker orig )
	{
		this.pos	= orig.pos;
		this.name	= orig.name;
	}		

	public Object clone()
	{
		return new Marker( this );
	}
	
	/**
	 *	Sorts markers chronologically.
	 *
	 *  @param  markers a vector whose elements are
	 *			instanceof Marker.
	 *	@return	sorted marker list. Each marker
	 *			is guaranteed to have a position
	 *			less or equal its successor
	 */
	public static java.util.List sort( java.util.List markers )
	{
		java.util.List sorted = new ArrayList( markers.size() );
		for( int i = 0; i < markers.size(); i++ ) {
			add( sorted, (Marker) markers.get( i ));
		}
		return sorted;
	}

	/**
	 *	Adds marker chronologically to
	 *  a pre-sorted list.
	 *
	 *  @param  markers		a chronological marker list
	 *  @param  marker		the marker to insert such that
	 *						its predecessor has a position
	 *						less or equal this marker's position
	 *						and the marker's successor has a position
	 *						greater than this marker's position.
	 *	@return	marker index in vector at which it was inserted
	 */
	public static int add( java.util.List markers, Marker marker )
	{
		int i;
		for( i = 0; i < markers.size(); i++ ) {
			if( ((Marker) markers.get( i )).pos > marker.pos ) break;
		}
		markers.add( i, marker );
		return i;
	}

	/**
	 *	Gets the index for specific marker in a list.
	 *
	 *  @param  markers		a vector whose elements are
	 *						instanceof Marker.
	 *	@param	name		marker name to find
	 *	@param	startIndex	where to begin
	 *	@return	The list index of the first occurance (beginning
	 *			at <code>startIndex</code>) of a marker whose name equals
	 *			the given name.
	 */	
	public static int find( java.util.List markers, String name, int startIndex )
	{
		for( int i = startIndex; i < markers.size(); i++ ) {
			if( ((Marker) markers.get( i )).name.equals( name )) return i;
		}
		return -1;
	}
} // class Marker