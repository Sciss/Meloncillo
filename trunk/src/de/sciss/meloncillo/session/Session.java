/*
 *  Session.java
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
 *		29-Jul-04   cleaned and commented.
 *		03-Aug-04   added filetype string
 *		15-Jan-05	neues XML file format
 *		02-Feb-05	moved to package 'session'
 *		04-Apr-05	filters frame bounds prefs if recall-frames is disabled
 *		23-Apr-05	bugfix in fromXML()
 *		26-May-05	implements de.sciss.app.Documents
 */

package de.sciss.meloncillo.session;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.prefs.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.io.*;
import de.sciss.meloncillo.timeline.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.io.IOUtil;

/**
 *  This is the core 'document' of Meloncillo
 *  describing the receivers and transmitters
 *  and the timeline, thus the 'session' objects.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class Session
extends SessionGroup
implements FilenameFilter, EntityResolver, de.sciss.app.Document
{
	/**
	 *	Denotes the path to this session or
	 *	<code>null</code> if not yet saved
	 */
	public static final String MAP_KEY_PATH	= "path";
	
	// used for restoring groups in fromXML()
	protected static final String	OPTIONS_KEY_SESSION	= "session";

	/**
	 *  Use this <code>LockManager</code> to gain access to
	 *  <code>receiverCollection</code>, <code>transmitterCollection</code>,
	 *  <code>timeline</code> and a transmitter's <code>MultirateTrackEditor</code>.
	 */
	public final LockManager bird = new LockManager( 5 );
	
	/**
	 *  Bitmask for putting a lock on the <code>receiverCollection</code>
	 */
	public  static final int DOOR_RCV	= 0x01;
	/**
	 *  Bitmask for putting a lock on the <code>transmitterCollection</code>
	 */
	public  static final int DOOR_TRNS	= 0x02;
	/**
	 *  Bitmask for putting a lock on the <code>timeline</code>
	 */
	public  static final int DOOR_TIME	= 0x04;
	/**
	 *  Bitmask for putting a lock on a <code>MultirateTrackEditor</code>
	 */
	public  static final int DOOR_MTE		= 0x08;
	/**
	 *  Bitmask for putting a lock on a the group collection
	 */
	public  static final int DOOR_GRP		= 0x10;

	/**
	 *  Convenient combination of Transmitter + MTE locking
	 */
	public  static final int DOOR_TRNSMTE		= DOOR_TRNS | DOOR_MTE;
	/**
	 *  Convenient combination of Timeline + Transmitter + MTE locking
	 */
	public  static final int DOOR_TIMETRNSMTE	= DOOR_TIME | DOOR_TRNS | DOOR_MTE;
	/**
	 *  Convenient combination of Timeline + Transmitter locking
	 */
	public  static final int DOOR_TIMETRNS		= DOOR_TIME | DOOR_TRNS;
	/**
	 *  Convenient combination of Timline + Transmitter + Receiver locking
	 */
	public  static final int DOOR_TIMETRNSRCV   = DOOR_TIME | DOOR_TRNS | DOOR_RCV;
	/**
	 *  Combination of all locking doors (Timeline, Transmitters, Receivers, MTE).
	 */
	public  static final int DOOR_ALL			= DOOR_TIME | DOOR_TRNS | DOOR_MTE | DOOR_RCV | DOOR_GRP;

	/**
	 *  The timeline object of a session
	 */
	public final Timeline timeline			= new Timeline();

	public final SessionCollection	selectedReceivers		= new SessionCollection();
	public final SessionCollection	selectedTransmitters	= new SessionCollection();
	public final SessionCollection	selectedGroups			= new SessionCollection();
	public final SessionCollection	activeReceivers			= new SessionUnionCollection( this, selectedGroups,
																	SessionUnionCollection.RECEIVERS,
																	bird, DOOR_RCV | DOOR_GRP );
	public final SessionCollection	activeTransmitters		= new SessionUnionCollection( this, selectedGroups,
																	SessionUnionCollection.TRANSMITTERS,
																	bird, DOOR_TRNS | DOOR_GRP );
	
	public static final String			RCV_NAME_PREFIX	= "R";
	public static final String			TRNS_NAME_PREFIX= "T";
	public static final String			GRP_NAME_PREFIX	= "G";
	public static final String			RCV_NAME_SUFFIX	= "";
	public static final String			TRNS_NAME_SUFFIX= "";
	public static final String			GRP_NAME_SUFFIX	= "";
	public static final MessageFormat	SO_NAME_PTRN	= new MessageFormat( "{1}{0,number,integer}{2}", Locale.US );

	public static final String  XML_ROOT		= "ichnogram";

	/**
	 *  The default file extension for storing sessions
	 *  is .llo for the extended markup language.
	 */
	public static final String FILE_EXTENSION   = ".llo";
	/**
	 *  The MacOS file type string
	 */
	public static final String MACOS_FILE_TYPE  = "IchS";
	
	/**
	 *	System ID for XML session files
	 */
	public static final String ICHNOGRAM_DTD	= "ichnogram.dtd";
	
	private static final double FILE_VERSION	= 1.0;
	
	private static final String XML_ATTR_VERSION		= "version";
	private static final String XML_ATTR_COMPATIBLE		= "compatible";
	private static final String XML_ATTR_PLATFORM		= "platform";
	private static final String XML_ELEM_PREFS			= "prefs";
	private static final String XML_ELEM_NODE			= "node";

	private final de.sciss.app.UndoManager undo	= new de.sciss.app.UndoManager( this );
	private boolean dirty = false;

	/**
	 *  Creates a new Session. This should be invoked only once at
	 *  the application startup. Subsequent session loading and clearing
	 *  is performed on the this instance using the <code>clear</code>,
	 *  <code>fromXML</code> etc. methods. Since many objects and windows
	 *  store the ichnogram, creating new instances would be fatal.
	 *
	 *  @see	#clear()
	 */
	public Session()
	{
//		super( new SessionCollection(), new SessionCollection(), new SessionCollection() );
		super();
		clear();
	}

	/**
	 *  Creates message format arguments for a generic
	 *  session object name pattern found in <code>SO_NAME_PTRN</code>.
	 *	This method seeks for the first occurance of an integer
	 *	number inside the concrete <code>realization</code> and puts
	 *	the prefix in <code>args[1]</code> and the suffix in <code>args[2]</code>.
	 *	The integer itself is saved as an <code>Integer</code> instance in
	 *	<code>args[0]</code>. If no integer is found, a default value of 1
	 *	is taken.
	 * 
	 *	@param	realization		a concrete version from which the pattern should
	 *							be derived, such as &quot;Lautsprecher-13-oben&quot;
	 *	@param	args			array of length greater or equal three whose
	 *							elements will be replaced by this method, i.e. for
	 *							the example above, <code>args[0]</code> will become
	 *							<code>new Integer( 13 )</code>, <code>args[1]</code>
	 *							will become &quot;Lautsprecher-&quot; and <code>args[2]</code>
	 *							will become &quot;-oben&quot;.
	 *
	 *	@see	SessionCollection#createUniqueName( MessageFormat, Object[], java.util.List )
	 *	@see	#SO_NAME_PTRN
	 */
	public static void makeNamePattern( String realization, Object[] args )
	{
		int		i, j;
		String	numeric = "0123456789";
		
		for( i = 0, j = realization.length(); i < realization.length(); i++ ) {
			if( numeric.indexOf( realization.charAt( i )) > 0 ) {
				for( j = i + 1; j < realization.length(); j++ ) {
					if( numeric.indexOf( realization.charAt( j )) == -1 ) break;
				}
				break;
			}
		}
		
		args[ 1 ] = realization.substring( 0, i );
		args[ 2 ] = realization.substring( j );
		if( j > i ) {
			args[ 0 ]	= new Integer( Integer.parseInt( realization.substring( i, j )));
		} else {
			args[ 0 ]	= new Integer( 1 );
		}
	}

	/**
	 *  Clears the document. All receivers
	 *  and transmitters are removed, the timeline
	 *  is reset, and session preferences are all cleared!
	 */
	public void clear()
	{
		final de.sciss.app.Application	app = AbstractApplication.getApplication();
	
		try {
			bird.waitExclusive( DOOR_ALL );

			super.clear();
			setName( app.getResourceString( "frameUntitled" ));
			selectedReceivers.clear( this );
			selectedTransmitters.clear( this );
			selectedGroups.clear( this );
			timeline.clear( this );
		}
		finally {
			bird.releaseExclusive( DOOR_ALL );
		}
		
		// clear session prefs
		try {
			PrefsUtil.removeAll( app.getUserPrefs().node( PrefsUtil.NODE_SESSION ), true );
		}
		catch( BackingStoreException e1 ) {
			System.err.println( app.getResourceString( "errSavePrefs" ) +
								" - " + e1.getLocalizedMessage() );
		}
	}
	
// ---------------- Document interface 

	public de.sciss.app.Application getApplication()
	{
		return AbstractApplication.getApplication();
	}

	public de.sciss.app.UndoManager getUndoManager()
	{
		return undo;
	}

	public void dispose()
	{
		// XXX
	}

	public boolean isDirty()
	{
		return dirty;
	}

	public void setDirty( boolean dirty )
	{
		if( !this.dirty == dirty ) {
			this.dirty = dirty;
			MainFrame	mf = (MainFrame) AbstractApplication.getApplication().getComponent( Main.COMP_MAIN );
			if( mf != null ) mf.updateTitle();
		}
	}
	
// ---------------- XMLRepresentation interface ---------------- 

	/**
	 *  Encodes the session into XML format
	 *  for storing onto harddisc.
	 *
	 *  @param  domDoc		the document containing the XML code
	 *  @param  node		the root node to which the session
	 *						nodes ("ichnogram", "session/" and "shared/")
	 *						are attached
	 *  @throws IOException if an error occurs. XML related errors are
	 *						mapped to IOExceptions.
	 */
	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		Element							childElement, child2;
		SessionObject					so;
		final de.sciss.app.Application	app	= AbstractApplication.getApplication();

		try {
			bird.waitShared( DOOR_ALL );

			node.setAttribute( XML_ATTR_VERSION, String.valueOf( FILE_VERSION ));
			node.setAttribute( XML_ATTR_COMPATIBLE, String.valueOf( FILE_VERSION ));
			node.setAttribute( XML_ATTR_PLATFORM, System.getProperty( "os.name" ));
			
			// map
			childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_MAP ));
			getMap().toXML( domDoc, childElement, options );

			// timeline object
			childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
			timeline.toXML( domDoc, childElement, options );

			// receiver collection
			childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
			childElement.setAttribute( XML_ATTR_NAME, XML_VALUE_RECEIVERS );
			for( int i = 0; i < receivers.size(); i++ ) {
				so		= receivers.get( i );
				child2	= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
				if( so instanceof XMLRepresentation ) {
					((XMLRepresentation) so).toXML( domDoc, child2, options );
				}
			}
			
			// transmitter collection
			childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
			childElement.setAttribute( XML_ATTR_NAME, XML_VALUE_TRANSMITTERS );
			for( int i = 0; i < transmitters.size(); i++ ) {
				so		= transmitters.get( i );
				child2	= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
				if( so instanceof XMLRepresentation ) {
					((XMLRepresentation) so).toXML( domDoc, child2, options );
				}
			}

			// groups
			childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
			childElement.setAttribute( XML_ATTR_NAME, XML_VALUE_GROUPS );
			for( int i = 0; i < groups.size(); i++ ) {
				so		= groups.get( i );
				child2	= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
				if( so instanceof XMLRepresentation ) {
					((XMLRepresentation) so).toXML( domDoc, child2, options );
				}
			}
		}
		finally {
			bird.releaseShared( DOOR_ALL );
		}
		
		// now add the session related stuff from
		// the main preferences' session and shared nodes
		
		childElement	= (Element) node.appendChild( domDoc.createElement( XML_ELEM_PREFS ));
		child2			= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_NODE ));
		child2.setAttribute( XML_ATTR_NAME, PrefsUtil.NODE_SESSION );
		PrefsUtil.toXML( app.getUserPrefs().node( PrefsUtil.NODE_SESSION ), true, domDoc, child2, options );
		child2			= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_NODE ));
		child2.setAttribute( XML_ATTR_NAME, PrefsUtil.NODE_SHARED );
		PrefsUtil.toXML( app.getUserPrefs().node( PrefsUtil.NODE_SHARED ), true, domDoc, child2, options );
	}

	/**
	 *  Clears the sessions
	 *  and recreates its objects from the
	 *  passed XML root node.
	 *
	 *  @param  domDoc		the document containing the XML code
	 *  @param  node		the document root node ("ichnogram")
	 *  @throws IOException if an error occurs. XML related errors are
	 *						mapped to IOExceptions.
	 */
	public void fromXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		NodeList						nl;
		Element							elem, elem2;
		double							d1;
		String							val, val2;
		SessionObject					so;
		Object							o;
		final ArrayList					soList		= new ArrayList();
		final NodeList					rootNL		= node.getChildNodes();
		final de.sciss.app.Application	app			= AbstractApplication.getApplication();

		options.put( OPTIONS_KEY_SESSION, this );

//		updateFileAttr( domDoc, node );
		try {
			bird.waitExclusive( DOOR_ALL );
			receivers.pauseDispatcher();
			selectedReceivers.pauseDispatcher();
			transmitters.pauseDispatcher();
			selectedTransmitters.pauseDispatcher();
			timeline.pauseDispatcher();
			clear();
			
//			super.fromXML( domDoc, node, options );	// parses optional map
			
			// check attributes
			d1 = Double.parseDouble( node.getAttribute( XML_ATTR_COMPATIBLE ));
			if( d1 > FILE_VERSION ) throw new IOException( app.getResourceString( "errIncompatibleFileVersion" ) + " : " + d1 );
			d1 = Double.parseDouble( node.getAttribute( XML_ATTR_VERSION ));
			if( d1 > FILE_VERSION ) options.put( XMLRepresentation.KEY_WARNING,
				app.getResourceString( "warnNewerFileVersion" ) + " : " + d1 );
			val = node.getAttribute( XML_ATTR_PLATFORM );
			if( !val.equals( System.getProperty( "os.name" ))) {
				o	 = options.get( XMLRepresentation.KEY_WARNING );
				val2 = (o == null ? "" : o.toString() + "\n") +
					   app.getResourceString( "warnDifferentPlatform" ) + " : " + val;
				options.put( XMLRepresentation.KEY_WARNING, val2 );
			}


			for( int k = 0; k < rootNL.getLength(); k++ ) {
				if( !(rootNL.item( k ) instanceof Element) ) continue;
				
				elem	= (Element) rootNL.item( k );
				val		= elem.getTagName();

				// zero or one "map" element
				if( val.equals( XML_ELEM_MAP )) {
					getMap().fromXML( domDoc, elem, options );

				// zero or more "object" elements
				} else if( val.equals( XML_ELEM_OBJECT )) {
					val		= elem.getAttribute( XML_ATTR_NAME );
					if( val.equals( Timeline.XML_OBJECT_NAME )) {
						timeline.fromXML( domDoc, elem, options );
					} else {
						System.err.println( "Warning: unknown session object type: '"+val+"'" );
					}
					
				} else if( val.equals( XML_ELEM_COLL )) {
					val		= elem.getAttribute( XML_ATTR_NAME );
					if( val.equals( XML_VALUE_RECEIVERS )) {
						soList.clear();
						nl = elem.getChildNodes();
						for( int m = 0; m < nl.getLength(); m++ ) {
							elem2	= (Element) nl.item( m );
							val		= elem2.getTagName();
							if( !val.equals( XML_ELEM_OBJECT )) continue;

							if( elem2.hasAttribute( XML_ATTR_CLASS )) {
								val	= elem2.getAttribute( XML_ATTR_CLASS );
							} else {	// #IMPLIED
								val = "de.sciss.meloncillo.receiver.SigmaReceiver";
							}
							so = (SessionObject) Class.forName( val ).newInstance();
							if( so instanceof XMLRepresentation ) {
								((XMLRepresentation) so).fromXML( domDoc, elem2, options );
							}
							receivers.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
															 MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
							soList.add( so );
						}
						receivers.addAll( this, soList );

					} else if( val.equals( XML_VALUE_TRANSMITTERS )) {
						soList.clear();
						nl = elem.getChildNodes();
						for( int m = 0; m < nl.getLength(); m++ ) {
							elem2	= (Element) nl.item( m );
							val		= elem2.getTagName();
							if( !val.equals( XML_ELEM_OBJECT )) continue;

							if( elem2.hasAttribute( XML_ATTR_CLASS )) {
								val	= elem2.getAttribute( XML_ATTR_CLASS );
							} else {	// #IMPLIED
								val = "de.sciss.meloncillo.transmitter.SimpleTransmitter";
							}
							so = (SessionObject) Class.forName( val ).newInstance();
							if( so instanceof XMLRepresentation ) {
								((XMLRepresentation) so).fromXML( domDoc, elem2, options );
							}
							transmitters.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
																MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
							soList.add( so );
						}
						transmitters.addAll( this, soList );

					} else if( val.equals( XML_VALUE_GROUPS )) {
						soList.clear();
						nl = elem.getChildNodes();
						for( int m = 0; m < nl.getLength(); m++ ) {
							elem2	= (Element) nl.item( m );
							val		= elem2.getTagName();
							if( !val.equals( XML_ELEM_OBJECT )) continue;

							if( elem2.hasAttribute( XML_ATTR_CLASS )) {
								val	= elem2.getAttribute( XML_ATTR_CLASS );
							} else {	// #IMPLIED
								val = "de.sciss.meloncillo.session.SessionGroup";
							}
							so = (SessionObject) Class.forName( val ).newInstance();
							if( so instanceof XMLRepresentation ) {
								((XMLRepresentation) so).fromXML( domDoc, elem2, options );
							}
							groups.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
														  MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
							soList.add( so );
						}
						groups.addAll( this, soList );

					} else {
						System.err.println( "Warning: unknown session collection type: '"+val+"'" );
					}

				// optional prefs
				} else if( val.equals( "prefs" )) {
					nl		= elem.getChildNodes();
					for( int i = 0; i < nl.getLength(); i++ ) {
						if( !(nl.item( i ) instanceof Element) ) continue;
						elem = (Element) nl.item( i );
						val	 = elem.getAttribute( "name" );
						if( val.equals( PrefsUtil.NODE_SESSION )) {
							PrefsUtil.fromXML( app.getUserPrefs().node( PrefsUtil.NODE_SESSION ), domDoc, elem, options );
						} else if( val.equals( PrefsUtil.NODE_SHARED )) {
							if( !app.getUserPrefs().getBoolean( PrefsUtil.KEY_RECALLFRAMES, false )) {	// install filter
								java.util.Set set = new HashSet( 3 );
								set.add( PrefsUtil.KEY_LOCATION );
								set.add( PrefsUtil.KEY_SIZE );
								set.add( PrefsUtil.KEY_VISIBLE );
								options.put( PrefsUtil.OPTIONS_KEY_FILTER, set );
							}
							PrefsUtil.fromXML( app.getUserPrefs().node( PrefsUtil.NODE_SHARED ), domDoc, elem, options );
						} else {
							System.err.println( "Warning: unknown preferences tree '"+val+"'" );
						}
					}
					
				// dtd doesn't allow other elements so we never get here
				} else {
					System.err.println( "Warning: unknown session node: '"+val+"'" );
				}
			} // for root-nodes
		}
		catch( ClassNotFoundException e1 ) {
			throw IOUtil.map( e1 );
		}
		catch( InstantiationException e2 ) {
			throw IOUtil.map( e2 );
		}
		catch( IllegalAccessException e3 ) {
			throw IOUtil.map( e3 );
		}
		catch( NumberFormatException e4 ) {
			throw IOUtil.map( e4 );
		}
		catch( ClassCastException e5 ) {
			throw IOUtil.map( e5 );
		}
		finally {
			receivers.resumeDispatcher();
			selectedReceivers.resumeDispatcher();
			transmitters.resumeDispatcher();
			selectedTransmitters.resumeDispatcher();
			timeline.resumeDispatcher();
			bird.releaseExclusive( DOOR_ALL );
		}
	}

// ---------------- FilenameFilter interface ---------------- 

	/**
	 *  Filter Session Files for Open or Save Dialog.
	 *  Practically it checks the file name's suffix
	 *
	 *  @param  dir		parent dir of the passed file
	 *  @param  name	filename to check
	 *  @return			true, if the filename is has
	 *					valid XML extension
	 */
	public boolean accept( File dir, String name )
	{
		return( name.endsWith( FILE_EXTENSION ));
	}

// ---------------- EntityResolver interface ---------------- 

	/**
	 *  This Resolver can be used for loading documents.
	 *	If the required DTD is the Meloncillo session DTD
	 *	("ichnogram.dtd"), it will return this DTD from
	 *	a java resource.
	 *
	 *  @param  publicId	ignored
	 *  @param  systemId	system DTD identifier
	 *  @return				the resolved input source for
	 *						the Meloncillo session DTD or <code>null</code>
	 *
	 *	@see	javax.xml.parsers.DocumentBuilder#setEntityResolver( EntityResolver )
	 */
	public InputSource resolveEntity( String publicId, String systemId )
	throws SAXException
	{
// System.err.println( "systemID = "+systemId );
		if( systemId.endsWith( ICHNOGRAM_DTD )) {	// replace our dtd with java resource
			InputStream dtdStream = getClass().getClassLoader().getResourceAsStream( ICHNOGRAM_DTD );
			InputSource is = new InputSource( dtdStream );
			is.setSystemId( ICHNOGRAM_DTD );
			return is;
		}
		return null;	// unknown DTD, use default behaviour
	}
}