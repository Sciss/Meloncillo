/*
 *  AbstractReceiver.java
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
 *		14-Aug-04   commented
 *		15-Mar-05	removed size specific methods
 */

package de.sciss.meloncillo.receiver;

import java.awt.geom.*;
import java.io.*;
import java.util.*;
import org.w3c.dom.*;

import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.util.*;
import de.sciss.util.NumberSpace;

/**
 *  A simple implementation of the <code>Receiver</code>
 *  interface that does not yet make assumptions
 *  about the data structure but provides some
 *  common means useful for all receivers.
 *  It provides the basic mechanism for XML import and
 *  export, it handles all methods except
 *  <code>getSensitivities</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public abstract class AbstractReceiver
extends AbstractSessionObject
implements Receiver
{
//	private double	anchorX, anchorY;
	
	protected static final String	SUBDIR		= "rcv";

	/**
	 *  Constructs a new empty receiver.
	 *  Basic initialization is achieved by
	 *  calling the <code>setAnchor</code>,
	 *  <code>setName</code> etc. methods.
	 */
	protected AbstractReceiver()
	{
		super();
	}
	
	protected void init()
	{
		super.init();
		
//		final NumberSpace		spcCoord	= new NumberSpace( 0.0, 1.0, 0.0001, 0.5, 0.1 );
		final NumberSpace		spcCoord	= new NumberSpace( 0.0, 1.0, 0.0, 0, 4, 0.5 );
		final MapManager		map			= getMap();
		
		map.putContext( null, MAP_KEY_X, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY |
																 MapManager.Context.FLAG_VISUAL,
																 MapManager.Context.TYPE_DOUBLE, spcCoord, "labelX",
																 null, new Double( 0.0 )));
		map.putContext( null, MAP_KEY_Y, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY |
																 MapManager.Context.FLAG_VISUAL,
																 MapManager.Context.TYPE_DOUBLE, spcCoord, "labelY",
																 null, new Double( 0.0 )));
	}

	/**
	 *  This is a copying constructor used
	 *  for clipboard operations.
	 *
	 *  @param  orig	the template receiver whose
	 *					bounds, name and folder are copied.
	 */
	protected AbstractReceiver( AbstractReceiver orig )
	{
		super( orig );
//	
//		this.anchorX	= orig.anchorX;
//		this.anchorY	= orig.anchorY;
	}
	
// ---------------- Receiver interface ---------------- 

	public void setAnchor( Point2D newAnchor )
	{
//		anchorX = newAnchor.getX();
//		anchorY = newAnchor.getY();
		Map m	= new HashMap( 2 );
		m.put( MAP_KEY_X, new Double( newAnchor.getX() ));
		m.put( MAP_KEY_Y, new Double( newAnchor.getY() ));
		getMap().putAllValues( this, m );
	}

	public Point2D getAnchor()
	{
		return new Point2D.Double( ((Number) getMap().getValue( MAP_KEY_X )).doubleValue(),
								   ((Number) getMap().getValue( MAP_KEY_Y )).doubleValue() );
	}

// ---------------- MapManager.Listener interface ---------------- 

//	public void mapChanged( MapManager.Event e )
//	{
//		super.mapChanged( e );
//
//		Object source = e.getSource();
//		
//		if( source == this ) return;
//		
//		Set		keySet = e.getPropertyNames();
//		Object	val;
//		
//		if( keySet.contains( MAP_KEY_X )) {
//			val		= e.getMap().getValue( MAP_KEY_X );
//			if( val != null ) {
//				anchorX = ((Number) e.getMap().getValue( MAP_KEY_X )).doubleValue();
//			}
//		}
//		if( keySet.contains( MAP_KEY_Y )) {
//			val		= e.getMap().getValue( MAP_KEY_Y );
//			if( val != null ) {
//				anchorY = ((Number) val).doubleValue();
//			}
//		}
//	}

// ---------------- XMLRepresentation interface ---------------- 

	/**
	 *  Saves the Receiver into the provided node.
	 *  Subclasses should call the super's method first and then
	 *  attach specific attributes, e.g.
	 *  <pre>
	 *    super.toXML( domDoc, node );
	 *    // addtional attributes, sub-nodes etc. are here
	 *  </pre>
	 *
	 *  @param  domDoc  the document containing the node
	 *  @param  node	the root note of this receiver
	 */
	public void toXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		super.toXML( domDoc, node, options );
	}

	/**
	 *  Restores the Receiver object from the provided node.
	 *  Subclasses should call the super's method first and then
	 *  restore specific attributes, e.g.
	 *  <pre>
	 *    super.fromXML( domDoc, node );
	 *    // addtional attributes, sub-nodes etc. are examined here
	 *  </pre>
	 *
	 *  @param  domDoc  the document containing the node
	 *  @param  node	the root note of this receiver
	 */
	public void fromXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		super.fromXML( domDoc, node, options );
	}
}