/*
 *  SessionGroup.java
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
 *		22-Jan-05	created
 *		02-Feb-05	moved to package 'session'
 */

package de.sciss.meloncillo.session;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import de.sciss.gui.PathField;
import de.sciss.meloncillo.receiver.Receiver;
import de.sciss.meloncillo.transmitter.Transmitter;
import de.sciss.meloncillo.util.MapManager;


/**
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class BasicSessionGroup
extends AbstractSessionObject
implements SessionGroup
{
	private final SessionCollection	receivers;
	private final SessionCollection	transmitters;
//	private final SessionCollection	groups;

	public BasicSessionGroup( Session doc )
	{
		super();

		receivers = new SessionCollectionView( doc.getReceivers(), new SessionCollectionView.Filter() {
			public boolean select( SessionObject so )
			{
				return ((Receiver) so).getGroups().contains( BasicSessionGroup.this );
			}
		});
		
		transmitters = new SessionCollectionView( doc.getTransmitters(), new SessionCollectionView.Filter() {
			public boolean select( SessionObject so )
			{
				return ((Transmitter) so).getGroups().contains( BasicSessionGroup.this );
			}
		});
	}
	
	public SessionCollection getReceivers() { return receivers; }
	public SessionCollection getTransmitters() { return transmitters; }
//	public SessionCollection getGroups() { return groups; }
	
//	// sync : caller should deal with sync for session collections! (see Sesssion#clear())
//	protected void init()
//	{
//		super.init();
//	}

	protected void clear()
	{
		super.clear();
//		init();
		getMap().putContext( this, SessionGroup.MAP_KEY_USERIMAGE, new MapManager.Context(
		    MapManager.Context.FLAG_OBSERVER_DISPLAY, MapManager.Context.TYPE_FILE,
		    new Integer( PathField.TYPE_INPUTFILE ), "labelUserImage", null, new File( "" )));
//		groups.clear( this );
//		receivers.clear( this );
//		transmitters.clear( this );
	}

	public void dispose()
	{
		super.dispose();
		receivers.dispose();
		transmitters.dispose();
//		groups.dispose();
	}

	/**
	 *  This simply returns <code>null</code>!
	 */
	public Class getDefaultEditor()
	{
		return null;
	}

// ---------------- XMLRepresentation interface ---------------- 

	/**
	 *  Saves the Receiver into the provided node. This stores
	 *  the class name, receiver name, and bounds as attributes
	 *  of the node. The implementation may change in the future, though.
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

		Element			child, child2;
		int				i;
		SessionObject	so;

		child	= (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
		child.setAttribute( XML_ATTR_NAME, SessionGroup.XML_VALUE_RECEIVERS );
		for( i = 0; i < this.receivers.size(); i++ ) {
			so		= this.receivers.get( i );
			child2	= (Element) child.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
			child2.setAttribute( XML_ATTR_NAME, so.getName() );
		}
		child	= (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
		child.setAttribute( XML_ATTR_NAME, SessionGroup.XML_VALUE_TRANSMITTERS );
		for( i = 0; i < this.transmitters.size(); i++ ) {
			so		= this.transmitters.get( i );
			child2	= (Element) child.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
			child2.setAttribute( XML_ATTR_NAME, so.getName() );
		}
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

		Element				child, child2;
		NodeList			nl, nl2;
		String				val;
		Session				doc			= (Session) options.get( Session.OPTIONS_KEY_SESSION );
		SessionCollection	sc;
		SessionObject		so;
		List				soList		= new ArrayList();
		int					i, j;

		nl = node.getChildNodes();
		for( i = 0; i < nl.getLength(); i++ ) {
			child	= (Element) nl.item( i );
			val		= child.getTagName();
			if( !val.equals( XML_ELEM_COLL )) continue;
			
			val		= child.getAttribute( XML_ATTR_NAME );
			if( val.equals( SessionGroup.XML_VALUE_RECEIVERS )) {
				sc	= doc.getReceivers();
//				sc2	= this.receivers;
			} else if( val.equals( SessionGroup.XML_VALUE_TRANSMITTERS )) {
				sc	= doc.getTransmitters();
//				sc2	= this.transmitters;
			} else {
				System.err.println( "Warning: unknown session group type: '"+val+"'" );
				continue;
			}

			soList.clear();
			nl2	= child.getChildNodes();
			for( j = 0; j < nl2.getLength(); j++ ) {
				child2	= (Element) nl2.item( j );
				val		= child2.getTagName();
				if( !val.equals( XML_ELEM_OBJECT )) continue;
				
				val		= child2.getAttribute( XML_ATTR_NAME );
				so		= sc.findByName( val );
				if( so == null ) {
					System.err.println( "Warning: group element '"+val+"' not found" );
				} else {
					soList.add( so );
				}
			}
// EEE
//			sc2.addAll( this, soList );
		}
	}
}