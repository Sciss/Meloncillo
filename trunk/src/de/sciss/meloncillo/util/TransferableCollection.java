/*
 *  TransferableCollection.java
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
 *		09-Aug-04   commented
 */

package de.sciss.meloncillo.util;

import java.awt.datatransfer.*;
import java.io.*;
import java.util.*;

/**
 *  A special transferable object which
 *  wraps a collection of individual transferable
 *  objects. For example, if the user selects
 *  a collection of receivers and chooses Cut from
 *  the edit menu, a <code>TransferableCollection</code>
 *  is created containing these receivers.
 *  <p>
 *  In the a pasting situation, the clipboard managing
 *  object obtains a <code>java.awt.List</code> object
 *  through the <code>getTransferData</code> method.
 *  It should traverse this list and check if an element
 *  is instanceof <code>Transferable</code> (this is guaranteed but
 *  checking maintains cleaner code). It can then check
 *  if the individual <code>Transferable</code> can
 *  provide a requested data format.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.61, 09-Aug-04
 */
public class TransferableCollection
implements Transferable
{
	/**
	 *  The flavor used to
	 *  identify a <code>TransferableCollection</code>
	 */
	public static final DataFlavor collectionFlavor = new DataFlavor( java.util.List.class, null );

	private final Vector		collTransferables;
	private final DataFlavor[]	collFlavors;

	/**
	 *  Constructs a new <code>TransferableCollection</code>
	 *  backup'ed by a list of <code>Transferable</code> objects.
	 *  The list elements are copied to a new list, hence it's safe
	 *  to modify the list afterwards.
	 *
	 *  @param  coll	a list containing elements which implement the
	 *					<code>Transferable</code> interface. Each element's
	 *					flavor is added to the flavor list which is checked
	 *					in a call to <code>isDataFlavorSupported</code>!
	 *
	 *  @throws IllegalArgumentException	if the list contains an object
	 *										which does not implement the Transferable
	 *										interface
	 */
	public TransferableCollection( java.util.List coll )
	{
		collTransferables = new Vector();
		
		int i, j, k;
		Object o;
		DataFlavor[] flavorArray;
		DataFlavor flavor;
		Vector v = new Vector();
		
		for( i = 0; i < coll.size(); i++ ) {
			o = coll.get( i );
			if( !(o instanceof Transferable) ) throw new IllegalArgumentException();
			flavorArray = ((Transferable) o).getTransferDataFlavors();
collFlavLp:	for( j = 0; j < flavorArray.length; j++ ) {
				flavor = flavorArray[j];
				for( k = 0; k < v.size(); k++ ) {
					if( ((DataFlavor) v.get( k )).equals( flavor )) continue collFlavLp;
				}
				v.add( flavor );
			}
			collTransferables.add( o );
		}
		v.add( collectionFlavor );
		
		collFlavors = new DataFlavor[ v.size() ];
		for( i = 0; i < v.size(); i++ ) {
			collFlavors[i] = (DataFlavor) v.get(i);
		}
	}

// ---------------- Transferable interface ---------------- 

	/**
	 *  Queries the available flavors.
	 *
	 *  @return an array of available flavors. This is a sum
	 *			of <code>collectionFlavor</code> and all
	 *			individual flavors of the list
	 */
	public DataFlavor[] getTransferDataFlavors()
	{
		return( collFlavors );
	}
	
	/**
	 *  Checks if a certain flavor is available.
	 *
	 *  @return <code>true</code>, if at least one of the list's
	 *			elements supports the given flavor, or if <code>flavor</code>
	 *			equals <code>collectionFlavor</code>.
	 */
	public boolean isDataFlavorSupported( DataFlavor flavor )
	{
		for( int i = 0; i < collFlavors.length; i++ ) {
			if( collFlavors[i].equals( flavor )) return true;
		}
		return false;
	}

	/**
	 *  Returns the transfer data. If <code>flavor</code> equals
	 *  <code>collectionFlavor</code>,
	 *  a <code>java.util.List</code> containing <code>Transferable</code> objects is
	 *  returned. Otherwise the list is traversed until <strong>one</strong>
	 *  transferable is found which supports the given flavor. This object is
	 *  returned.
	 *
	 *  @param  flavor  the transferable object(s) will be returned in this flavor.
	 *  @return either  a list if flavor is collectionFlavor, otherwise the transfer data
	 *					of the first list item supporting the given flavor
	 *  @throws UnsupportedFlavorException  if none of the items supports the given flavor
	 *  @throws IOException					if the data is no longer available in the requested flavor
	 */
	public Object getTransferData( DataFlavor flavor )
	throws UnsupportedFlavorException, IOException
	{
		if( flavor.equals( collectionFlavor )) return new Vector( collTransferables );
	
		Object o;
		int i;
	
		for( i = 0; i < collTransferables.size(); i++ ) {
			o = collTransferables.get( i );
			if( o instanceof Transferable ) {
				if( ((Transferable) o).isDataFlavorSupported( flavor )) {
					return( ((Transferable) o).getTransferData( flavor ));
				}
			}
		}
		throw new UnsupportedFlavorException( flavor );
	}
}