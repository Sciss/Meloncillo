/*
 *  AbstractTransmitter.java
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
 *		02-Sep-04   commented
 */

package de.sciss.meloncillo.transmitter;

import java.io.IOException;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.sciss.meloncillo.session.AbstractSessionObject;

/**
 *  A simple implementation of the <code>Transmitter</code>
 *  interface that does not yet make assumptions
 *  about the data structure but provides some
 *  common means useful for all transmitters.
 *  It provides the basic mechanism for XML import and
 *  export, it handles all methods except
 *  <code>getTrackEditor</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public abstract class AbstractTransmitter
extends AbstractSessionObject
implements Transmitter
{
	protected static final String	SUBDIR			= "trns";

//	private static final String	MAP_KEY_AUDIOBUS	= "audiobus";
//	private static final String	MAP_KEY_AUDIOFILE	= "audiofile";

	/**
	 *  Constructs a new empty transmitter.
	 *  Basic initialization is achieved by
	 *  adding a preexisting file to the track editor,
	 *  calling <code>setName</code> etc. methods.
	 */
	protected AbstractTransmitter()
	{
		super();
//
//		NumberSpace			spcBus		= NumberSpace.createIntSpace( 0, 4096 );
//		MapManager			map			= getMap();
//
//		map.putContext( MAP_KEY_AUDIOBUS, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//														  MapManager.Context.TYPE_INTEGER, spcBus, "labelAudioBus" ));
//		map.putContext( MAP_KEY_AUDIOFILE, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
//					  MapManager.Context.TYPE_FILE, new Integer( PathField.TYPE_INPUTFILE ), "labelAudioFile" ));
//		map.putValue( this, MAP_KEY_AUDIOBUS, new Integer( 0 ));
//		map.putValue( this, MAP_KEY_AUDIOFILE, new File( "" ));
	}

// ---------------- XMLRepresentation interface ---------------- 

	/**
	 *  Saves the Transmitter into the provided node.
	 *  Subclasses should call the super's method first and then
	 *  attach specific attributes, e.g.
	 *  <pre>
	 *    super.toXML( domDoc, node );
	 *    // addtional attributes, sub-nodes etc. are here
	 *  </pre>
	 *
	 *  @param  domDoc  the document containing the node
	 *  @param  node	the root note of this transmitter
	 */
	public void toXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		super.toXML( domDoc, node, options );
	}

	/**
	 *  Restores the Transmitter object from the provided node.
	 *  Subclasses should call the super's method first and then
	 *  restore specific attributes, e.g.
	 *  <pre>
	 *    super.fromXML( domDoc, node );
	 *    // addtional attributes, sub-nodes etc. are examined here
	 *  </pre>
	 *
	 *  @param  domDoc  the document containing the node
	 *  @param  node	the root note of this transmitter
	 */
	public void fromXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		super.fromXML( domDoc, node, options );
	}
}