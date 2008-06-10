/*
 *  TrackList.java
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
 *		03-Aug-04   commented
 */

package de.sciss.meloncillo.io;

import java.awt.datatransfer.*;
import java.io.*;
import java.util.*;

import de.sciss.io.*;

/**
 *  A <code>TrackList</code> describes a portion
 *  of a virtual track. while the track
 *  is virtually coherent for a given
 *  time span, it may physically consist
 *  of a number of fragmented data portions
 *  in one or several files, that is to say
 *  it can consist of several <code>TrackSpan</code>s.
 *  A track list is basically a collection
 *  of adjectant <code>TrackSpan</code>s, but also adds
 *  a special <code>DataFlavor</code> to allow clipboard
 *  operations.
 *  <p>
 *  A <code>TrackList</code> is usually obtained
 *  by calling the <code>getTrackList</code> method
 *  of a <code>MultirateTrackEditor</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		MultirateTrackEditor#getTrackList( Span )
 *  @see		TrackSpan
 *  @see		java.awt.datatransfer.Clipboard
 *  @see		java.awt.datatransfer.DataFlavor
 */
public class TrackList
implements Transferable
{
	public static final DataFlavor trackListFlavor		= new DataFlavor( TrackList.class, null );

	private Vector						collTrackSpans;
	private static final DataFlavor[]	flavors			= { trackListFlavor };

	/**
	 *  Constructs a new empty <code>TrackList</code>. Add items
	 *  using the <code>add</code> method.
	 */
	public TrackList()
	{
		collTrackSpans = new Vector();
	}

	/**
	 *  Constructs a <code>TrackList</code> whose elemenst
	 *  are copied from a template <code>TrackList</code>.
	 *  
	 *  @param  orig	template list whose elements (TrackSpans)
	 *					are copied. The new spans are independant
	 *					and modifying the template after this constructor
	 *					is called will not affect the newly created list.
	 */
	public TrackList( TrackList orig )
	{
		collTrackSpans = new Vector( orig.size() );
		
		for( int i = 0; i < orig.size(); i++ ) {
			collTrackSpans.add( new TrackSpan( orig.get( i )));
		}
	}
	
	/**
	 *  Adds a new <code>TrackSpan</code> to the end of the list.
	 *  Note that all successive calls to this method
	 *  should provide <code>TrackSpans</code> that are
	 *  direct successors of each other, i.e.
	 *  tsSucc.span.getStart() == tsPrev.span.getStop() !!
	 *
	 *  @param  ts  the new track span to add to the tail of the list
	 *  @throws IllegalArgumentException if the track span does not
	 *			directly succeed the previous one
	 */
	public void add( TrackSpan ts )
	{
		if( !collTrackSpans.isEmpty() ) {
			if( ((TrackSpan) collTrackSpans.lastElement()).span.getStop() != ts.span.getStart() ) {
				throw new IllegalArgumentException();
			}
		}
		collTrackSpans.add( ts );
	}
	
	/**
	 *  Returns a sub element of the list
	 *
	 *  @param  index   which track span to get, must
	 *					be >= 0 and < size()
	 *  @return the track span at the given index
	 */
	public TrackSpan get( int index )
	{
		return( (TrackSpan) collTrackSpans.get( index ));
	}

	/**
	 *  Returns the number of TrackSpans that have been
	 *  added to the list
	 *
	 *  @return  number of track span elements in the list
	 */
	public int size()
	{
		return( collTrackSpans.size() );
	}

	/**
	 *  Returns a span that comprises the list of all
	 *  added TrackSpans, i.e. returns a span whose
	 *  start equals the start of the first added TrackSpan
	 *  and whose stop equals the stop of the last
	 *  added TrackSpan. Returns an empty Span if
	 *  no items have been added to the list.
	 *
	 *  @return the encompassing time span of the list or
	 *			an empty span if the list is empty.
	 */
	public Span getSpan()
	{
		if( collTrackSpans.isEmpty() ) return new Span();

		TrackSpan   firstTS, lastTS;
		
		firstTS = (TrackSpan) collTrackSpans.firstElement();
		lastTS  = (TrackSpan) collTrackSpans.lastElement();
	
		return( new Span( firstTS.span.getStart(), lastTS.span.getStop() ));
	}
	
	/**
	 *  Debugging help: dumps the track span elements
	 *  to the console
	 */
	public void debugDump()
	{
		TrackSpan ts;
	
		System.err.println( "---------- dump of tracklist ----------" );
	
		for( int i = 0; i < collTrackSpans.size(); i++ ) {
			ts = (TrackSpan) collTrackSpans.get( i );
			System.err.println( "ts #"+i+" : "+ts.span.getStart()+" ... "+ts.span.getStop()+" ; file off "+ts.offset );
		}
		
		System.err.println( "total span : "+getSpan().getStart()+" ... "+getSpan().getStop() );
	}

// ---------------- Transferable interface ---------------- 

	public DataFlavor[] getTransferDataFlavors()
	{
		return flavors;
	}

	public boolean isDataFlavorSupported( DataFlavor flavor )
	{
		for( int i = 0; i < flavors.length; i++ ) {
			if( flavor.equals( flavors[i] )) return true;
		}
		return false;
	}

	/**
	 *  Returns the transfer data which is a TrackList
	 *  whose contents is equal to this object's list.
	 *  It's safe to manipulate the returned TrackList.
	 *
	 *  @param  flavor  must be <code>trackListFlavor</code>
	 *  @throws UnsupportedFlavorException  if the flavor is not <code>trackListFlavor</code>
	 *  @throws IOException when data cannot be transferred
	 */
	public Object getTransferData( DataFlavor flavor )
	throws UnsupportedFlavorException, IOException
	{
		if( flavor.equals( trackListFlavor )) {
			return new TrackList( this );
		} else {
			throw new UnsupportedFlavorException( flavor );
		}
	}
}