/*
 *  Session.java
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
 *		29-Jul-04   cleaned and commented.
 *		03-Aug-04   added filetype string
 *		15-Jan-05	neues XML file format
 *		02-Feb-05	moved to package 'session'
 *		04-Apr-05	filters frame bounds prefs if recall-frames is disabled
 *		23-Apr-05	bugfix in fromXML()
 *		26-May-05	implements de.sciss.app.Documents
 */

package de.sciss.meloncillo.session;

import java.awt.EventQueue;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.BackingStoreException;

import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.sciss.timebased.Trail;
import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Flag;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.app.Application;
import de.sciss.common.BasicDocument;
import de.sciss.common.BasicWindowHandler;
import de.sciss.common.ProcessingThread;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.MenuAction;
import de.sciss.gui.ParamField;
import de.sciss.gui.PathField;
import de.sciss.gui.ProgressComponent;
import de.sciss.gui.SpringPanel;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.IOUtil;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.edit.BasicCompoundEdit;
import de.sciss.meloncillo.edit.EditSetTimelineLength;
import de.sciss.meloncillo.edit.TimelineVisualEdit;
import de.sciss.meloncillo.gui.BlendingAction;
import de.sciss.meloncillo.gui.MainFrame;
import de.sciss.meloncillo.io.AudioTrail;
import de.sciss.meloncillo.io.BlendContext;
import de.sciss.meloncillo.io.MarkerTrail;
import de.sciss.meloncillo.io.XMLRepresentation;
import de.sciss.meloncillo.realtime.Transport;
import de.sciss.meloncillo.timeline.MarkerTrack;
import de.sciss.meloncillo.timeline.Timeline;
import de.sciss.meloncillo.timeline.TimelineFrame;
import de.sciss.meloncillo.timeline.Track;
import de.sciss.meloncillo.transmitter.Transmitter;
import de.sciss.meloncillo.util.LockManager;
import de.sciss.meloncillo.util.MapManager;
import de.sciss.meloncillo.util.PrefsUtil;

/**
 *  This is the core 'document' of Meloncillo
 *  describing the receivers and transmitters
 *  and the timeline, thus the 'session' objects.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class Session
extends BasicDocument
implements SessionGroup, FilenameFilter, EntityResolver, de.sciss.app.Document
{
	public static final int					EDIT_INSERT		= 0;
	public static final int					EDIT_OVERWRITE	= 1;
	public static final int					EDIT_MIX		= 2;

	private	String		name;
	private MapManager	map		= new MapManager( this, new HashMap() );

	private int								editMode		= EDIT_INSERT;

	/**
	 *	Denotes the path to this session or
	 *	<code>null</code> if not yet saved
	 */
//	public static final String MAP_KEY_PATH	= "path";
	
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
	public final Timeline timeline			= new Timeline( this );

	private final BasicSessionCollection	tracks			= new BasicSessionCollection();	// should be tracking audioTracks automatically
	private final BasicSessionCollection	selectedTracks	= new BasicSessionCollection();	// should be tracking audioTracks automatically
	
	private final BasicSessionCollection	receivers		= new BasicSessionCollection();
	private final SessionCollection			transmitters;
	private final BasicSessionCollection	groups			= new BasicSessionCollection();

	private final BasicSessionCollection	selectedReceivers		= new BasicSessionCollection();
	private final SessionCollection			selectedTransmitters;
	private final BasicSessionCollection	selectedGroups			= new BasicSessionCollection();

	// group view
	private final SessionCollection			activeReceivers;
	private final SessionCollection			activeTransmitters;
	
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

//	private final ActionSave		actionSave;
	
	private File					file			 = null;
	
	private final Transport			transport;
	protected ProcessingThread		pt				= null;
	
	public final MarkerTrail		markers;
	public final MarkerTrack		markerTrack;
	
	// --- actions ---
	
	private final ActionLoad			actionLoad;
	private final ActionCut				actionCut;
	protected final ActionCopy			actionCopy;
	private final ActionPaste			actionPaste;
	private final ActionDelete			actionDelete;
	private final ActionSilence			actionSilence;
	private final ActionTrim			actionTrim;
	
	private final BlendingAction		blending;

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

        transport	= new Transport( this );
//		actionSave	= new ActionSave();

		markerTrack			= new MarkerTrack( this );
		markers				= (MarkerTrail) markerTrack.getTrail();
// EEE
//		markers.copyFromAudioFile( afds[ 0 ]);	// XXX
		tracks.add( null, markerTrack );
//		selectedTracks.add( this, markerTrack );
		final SessionCollectionView.Filter trnsFilter = new SessionCollectionView.Filter() {
			public boolean select( SessionObject so )
			{
//				System.out.println( "checking " + so + " -> " + (so instanceof Transmitter) );
				return( so instanceof Transmitter );
			}
		};
		transmitters		= new SessionCollectionView( tracks, trnsFilter ); 
		selectedTransmitters= new SessionCollectionView( selectedTracks, trnsFilter );
//		activeReceivers		= new SessionUnionCollection( this, selectedGroups, SessionUnionCollection.RECEIVERS );
//		activeTransmitters	= new SessionUnionCollection( this, selectedGroups, SessionUnionCollection.TRANSMITTERS );
		activeReceivers		= new SessionCollectionUnionView( receivers, SessionCollectionUnionView.RECEIVERS, true, selectedGroups );
		activeTransmitters	= new SessionCollectionUnionView( transmitters, SessionCollectionUnionView.TRANSMITTERS, true, selectedGroups );
		
activeTransmitters.addListener( new SessionCollection.Listener() {
	public void sessionCollectionChanged( SessionCollection.Event e ) {
		System.out.println( e.getModificationType() == SessionCollection.Event.ACTION_ADDED ? "ADDED:" : "REMOVED:" );
		final List coll = e.getCollection();
		for( int i = 0; i < coll.size(); i++ ) {
			System.out.println( "  #" + (i+1) + " -> " + coll.get( i ));
		}
	}
	
	public void sessionObjectChanged( SessionCollection.Event e ) {}
	public void sessionObjectMapChanged( SessionCollection.Event e ) {}
});

		actionLoad			= new ActionLoad();
		actionCut			= new ActionCut();
		actionCopy			= new ActionCopy();
		actionPaste			= new ActionPaste();
		actionDelete		= new ActionDelete();
		actionSilence		= new ActionSilence();
		actionTrim			= new ActionTrim();

		blending			= new BlendingAction( timeline, null );
		
		clear();
	}
	
	public Transport getTransport()
	{
		return transport;
	}
	
	public void setFile( File f )
	{
		file = f;
		setName( f == null ? null : f.getName() );
	}
	
	public File getFile()
	{
		return file;
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

	public BlendingAction getBlendingAction()
	{
		return blending;
	}

	public void setEditMode( int mode )
	{
		editMode = mode;
	}
	
	public int getEditMode()
	{
		return editMode;
	}

	public SessionCollection getReceivers() { return receivers; }
	public SessionCollection getTracks() { return tracks; }
	public SessionCollection getTransmitters() { return transmitters; }
	public SessionCollection getGroups() { return groups; }

	public SessionCollection getSelectedReceivers() { return selectedReceivers; }
	public SessionCollection getSelectedTracks() { return selectedTracks; }
	public SessionCollection getSelectedTransmitters() { return selectedTransmitters; }
	public SessionCollection getSelectedGroups() { return selectedGroups; }

	// !!! IMPORTANT !!! DON'T RETURN THEM IN A MUTABLE FORM !!!
	public SessionCollection getActiveReceivers() { return activeReceivers; }
	// !!! IMPORTANT !!! DON'T RETURN THEM IN A MUTABLE FORM !!!
	public SessionCollection getActiveTransmitters() { return activeTransmitters; }

	public MutableSessionCollection getMutableReceivers() { return receivers; }
	public MutableSessionCollection getMutableTracks() { return tracks; }
	public MutableSessionCollection getMutableGroups() { return groups; }
	public MutableSessionCollection getMutableSelectedReceivers() { return selectedReceivers; }
	public MutableSessionCollection getMutableSelectedTracks() { return selectedTracks; }
	public MutableSessionCollection getMutableSelectedGroups() { return selectedGroups; }

	/**
	 *  Clears the document. All receivers
	 *  and transmitters are removed, the timeline
	 *  is reset, and session preferences are all cleared!
	 */
	public void clear()
	{
		final Application	app = AbstractApplication.getApplication();
	
//		try {
//			bird.waitExclusive( DOOR_ALL );

//			super.clear();
			getMap().clearValues( this );
//			init();
			map.clearValues( this );
			map.putContext( null, MAP_KEY_FLAGS, new MapManager.Context( MapManager.Context.FLAG_LIST_DISPLAY,
																		 MapManager.Context.TYPE_INTEGER, null, null,
																		 null, new Integer( 0 )));
			map.putContext( this, SessionGroup.MAP_KEY_USERIMAGE, new MapManager.Context(
			    MapManager.Context.FLAG_OBSERVER_DISPLAY, MapManager.Context.TYPE_FILE,
			    new Integer( PathField.TYPE_INPUTFILE ), "labelUserImage", null, new File( "" )));
			
//			final List collTrns = transmitters.getAll();
			selectedReceivers.clear( this );
			selectedTracks.clear( this );
			selectedGroups.clear( this );
			groups.clear( this );
			receivers.clear( this );
			tracks.removeAll( this, transmitters.getAll() ); // not the marker track
			markerTrack.clear( this );
			selectedTracks.add(  this, markerTrack );

			setFile( null );
			timeline.clear( this );
//		}
//		finally {
//			bird.releaseExclusive( DOOR_ALL );
//		}
		
		// clear session prefs
		try {
			PrefsUtil.removeAll( app.getUserPrefs().node( PrefsUtil.NODE_SESSION ), true );
		}
		catch( BackingStoreException e1 ) {
			System.err.println( app.getResourceString( "errSavePrefs" ) +
								" - " + e1.getLocalizedMessage() );
		}
	}

	/**
	 *  This simply returns <code>null</code>!
	 */
	public Class getDefaultEditor()
	{
		return null;
	}

	public TimelineFrame getTimelineFrame()
	{
		return (TimelineFrame) AbstractApplication.getApplication().getComponent( Main.COMP_TIMELINE );
	}

	public MainFrame getFrame()
	{
//		return frame;
		return (MainFrame) AbstractApplication.getApplication().getComponent( Main.COMP_MAIN );
	}

	/**
	 * 	Checks if a process is currently running. This method should be called
	 * 	before launching a process using the <code>start()</code> method.
	 * 	If a process is ongoing, this method waits for a default timeout period
	 * 	for the thread to finish.
	 * 
	 *	@return	<code>true</code> if a new process can be launched, <code>false</code>
	 *			if a previous process is ongoing and a new process cannot be launched
	 *	@throws	IllegalMonitorStateException	if called from outside the event thread
	 *	@synchronization	must be called in the event thread
	 */
	public boolean checkProcess()
	{
		return checkProcess( 500 );
	}
	
	/**
	 * 	Checks if a process is currently running. This method should be called
	 * 	before launching a process using the <code>start()</code> method.
	 * 	If a process is ongoing, this method waits for a given timeout period
	 * 	for the thread to finish.
	 * 
	 * 	@param	timeout	the maximum duration in milliseconds to wait for an ongoing process
	 *	@return	<code>true</code> if a new process can be launched, <code>false</code>
	 *			if a previous process is ongoing and a new process cannot be launched
	 *	@throws	IllegalMonitorStateException	if called from outside the event thread
	 *	@synchronization	must be called in the event thread
	 */
	public boolean checkProcess( int timeout )
	{
//System.out.println( "checking..." );
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		if( pt == null ) return true;
		if( timeout == 0 ) return false;

//System.out.println( "sync " + timeout );
		pt.sync( timeout );
//System.out.println( "sync done" );
		return( (pt == null) || !pt.isRunning() );
	}
	
	public void cancelProcess( boolean sync )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		if( pt == null ) return;
		pt.cancel(  sync );
	}
	
	public String getProcessName()
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		if( pt == null ) return null;
		return pt.getName();
	}
	
	/**
	 * 	Starts a <code>ProcessingThread</code>. Only one thread
	 * 	can exist at a time. To ensure that no other thread is running,
	 * 	call <code>checkProcess()</code>.
	 * 
	 * 	@param	pt	the thread to launch
	 * 	@throws	IllegalMonitorStateException	if called from outside the event thread
	 * 	@throws	IllegalStateException			if another process is still running
	 * 	@see	#checkProcess()
	 * 	@synchronization	must be called in the event thread
	 */
	public void start( ProcessingThread process )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		if( this.pt != null ) throw new IllegalStateException( "Process already running" );
		
		pt = process;
		pt.addListener( new ProcessingThread.Listener() {
			public void processStarted( ProcessingThread.Event e ) { /* empty */ }
			public void processStopped( ProcessingThread.Event e )
			{
				pt = null;
			}
		});
		pt.start();
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
		discardEditsAndClipboard();
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

	// ---------------- SessionObject interface ---------------- 

	/**
	 *  Retrieves the property map manager of the session
	 *	object. This manager may be used to read and
	 *	write properties and register listeners.
	 *
	 *	@return	the property map manager that stores
	 *			all the properties of this session object
	 */
	public MapManager getMap()
	{
		return map;
	}

	public void setName( String newName )
	{
		name = newName;
	}
	
	public String getName()
	{
		return name;
	}
	
	public ProcessingThread initiateLoad( File f )
	{
		return actionLoad.initiate( f );
	}

	public BlendContext createBlendContext( long maxLeft, long maxRight, boolean hasSelectedAudio )
	{
		if( !hasSelectedAudio || ((maxLeft == 0L) && (maxRight == 0L)) ) {
			return null;
		} else {
			return blending.createBlendContext( maxLeft, maxRight );
		}
	}

	protected void discardEditsAndClipboard()
	{
		undo.discardAllEdits();
//		ClipboardTrackList.checkDispose( AbstractApplication.getApplication().getClipboard() );
		ClipboardTrackList.disposeAll( this );
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
		NodeList			nl;
		Element				elem, elem2;
		double				d1;
		String				val, val2;
		SessionObject		so;
		Object				o;
		final List			soList		= new ArrayList();
		final NodeList		rootNL		= node.getChildNodes();
		final Application	app			= AbstractApplication.getApplication();

		options.put( OPTIONS_KEY_SESSION, this );

//		updateFileAttr( domDoc, node );
		try {
//			bird.waitExclusive( DOOR_ALL );
			receivers.pauseDispatcher();
			selectedReceivers.pauseDispatcher();
			tracks.pauseDispatcher();
			selectedTracks.pauseDispatcher();
// EEE
//			transmitters.pauseDispatcher();
//			selectedTransmitters.pauseDispatcher();
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
						tracks.addAll( this, soList );
//						transmitters.addAll( this, soList );

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
			tracks.resumeDispatcher();
			selectedTracks.resumeDispatcher();
// EEE
//			transmitters.resumeDispatcher();
//			selectedTransmitters.resumeDispatcher();
			timeline.resumeDispatcher();
//			bird.releaseExclusive( DOOR_ALL );
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

	public ProcessingThread procDelete( String name, Span span, int mode )
	{
		return actionDelete.initiate( name, span, mode );
	}

// EEE
//	public ProcessingThread procSave( String name, Span span, AudioFileDescr[] targetAFDs,
//									  int[] channelMap, boolean saveMarkers, boolean asCopy )
//	{
//		return actionSave.initiate( name, span, targetAFDs, channelMap, saveMarkers, asCopy );
//	}
	
	public MenuAction getCutAction()
	{
		return actionCut;
	}

	public MenuAction getCopyAction()
	{
		return actionCopy;
	}

	public MenuAction getPasteAction()
	{
		return actionPaste;
	}

	public MenuAction getDeleteAction()
	{
		return actionDelete;
	}
	
	public MenuAction getSilenceAction()
	{
		return actionSilence;
	}
	
	public MenuAction getTrimAction()
	{
		return actionTrim;
	}
	
	public ProcessingThread insertSilence( long pos, long numFrames )
	{
		return actionSilence.initiate( pos, numFrames );
	}

	public ProcessingThread closeDocument( boolean force, Flag wasClosed )
	{
		return closeDocument( AbstractApplication.getApplication().getResourceString( "menuClose" ), force, wasClosed );
	}

	public ProcessingThread closeDocument( String name, boolean force, Flag wasClosed )
	{
		final DocumentFrame frame = (DocumentFrame) AbstractApplication.getApplication().getComponent( Main.COMP_MAIN );
		return frame.closeDocument( name, force, wasClosed );	// XXX should be in here not frame!!!
	}

	public ClipboardTrackList getSelectionAsTrackList()
	{
		return actionCopy.getSelectionAsTrackList();
	}
	
	public ProcessingThread pasteTrackList( ClipboardTrackList tl, long insertPos, String name, int mode )
	{
		return actionPaste.initiate( tl, insertPos, name, mode );
	}

	protected static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

	protected static boolean checkSyncedAudio( List tis, boolean changesTimeline, ProcessingThread context, Flag hasSelectedAudio )
	{
		Track.Info ti;

		hasSelectedAudio.set( false );
	
		for( int i = 0; i < tis.size(); i++ ) {
			ti = (Track.Info) tis.get( i );
			if( changesTimeline && !ti.getChannelSync() ) {
				if( context != null ) context.setException( new IllegalStateException( AbstractApplication.getApplication().getResourceString( "errAudioWillLooseSync" )));
				return false;
			}
			if( ((ti.trail instanceof AudioTrail) ||
			     (ti.trail instanceof Transmitter)) && ti.selected ) {
				
				hasSelectedAudio.set( true );
			}
		}
		return true;
	}

	// ---------------------- internal classes ----------------------
	
	private class ActionLoad
//	extends MenuAction
	implements ProcessingThread.Client
	{
		/**
		 *  Loads a new session file.
		 *  If transport is running, is will be stopped.
		 *  The console window is cleared an a <code>ProcessingThread</code>
		 *  started which loads the new session.
		 *
		 *  @param  path	the file of the session to be loaded
		 *  
		 *  @synchronization	this method must be called in event thread
		 */
		protected ProcessingThread initiate( File path )
		{
			final Main root = (Main) AbstractApplication.getApplication();
			getTransport().stop();
			((MainFrame) root.getComponent( Main.COMP_MAIN )).clearLog();
//			Map options = new HashMap();
//			options.put( "file", path );
//			return( new ProcessingThread( this, root, root, doc, text, options, Session.DOOR_ALL ));
			final ProcessingThread pt;
			final Map options = new HashMap();
			pt = new ProcessingThread( this, root, getResourceString( "menuOpen" ));
			options.put( "file", path );
			options.put( XMLRepresentation.KEY_BASEPATH, path.getParentFile() );
			pt.putClientArg( "options", options );
//			pt.start();
			
			final org.w3c.dom.Document		domDoc;
			final DocumentBuilderFactory	builderFactory;
			final DocumentBuilder			builder;

			builderFactory  = DocumentBuilderFactory.newInstance();
			builderFactory.setValidating( true );
			getUndoManager().discardAllEdits();

			try {
				builder	=   builderFactory.newDocumentBuilder();
				builder.setEntityResolver( Session.this );
				domDoc  =   builder.parse( path );
//				context.setProgression( -1f );
				fromXML( domDoc, domDoc.getDocumentElement(), options );
//				doc.getMap().putValue( this, Session.MAP_KEY_PATH, f );
//				doc.setName( f.getName() );
//				setFile( path );

//				context.setProgression( 1.0f );
//				success = true;
			}
			catch( ParserConfigurationException e1 ) {
				pt.putClientArg( "exception", e1 );
			}
			catch( SAXParseException e1 ) {
				pt.putClientArg( "exception", e1 );
			}
			catch( SAXException e1 ) {
				pt.putClientArg( "exception", e1 );
			}
			catch( IOException e1 ) {
				pt.putClientArg( "exception", e1 );
			}
		
			return pt;
		}

		// ...is very tricky. since timeline changes may only
		// be performed on the event thread, we do all in the
		// initiate method, and use the processRun merely to
		// generate a result
		public int processRun( ProcessingThread context )
		throws IOException
		{
			final Exception e = (Exception) context.getClientArg( "exception" );
			if( e != null ) context.setException( e );
			return e == null ? DONE : FAILED;
		} // run()

		public void processCancel( ProcessingThread context ) {}

		/**
		 *  When the sesion was successfully
		 *  loaded, its name will be put in the
		 *  Open-Recent menu. All frames' bounds will be
		 *  restored depending on the users preferences.
		 *  <code>setModified</code> will be called on
		 *  the <code>Main</code> class and the
		 *  main frame's title is updated
		 */
		public void processFinished( ProcessingThread context )
		{
			setDirty( false );
			if( context.getReturnCode() == ProgressComponent.DONE ) {
				final Map options = (Map) context.getClientArg( "options" );
				setFile( (File) options.get( "file" ));
			} else {
				setFile( null );
				clear();
			}
		}
	}
	private class ActionCut
	extends MenuAction
	{
		protected ActionCut() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		protected void perform()
		{
			final ProcessingThread proc; // = null;
			
			if( actionCopy.perform() ) {
//				if( !bird.attemptShared( Session.DOOR_TIME | Session.DOOR_MTE )) return;
//				try {
					proc = procDelete( getValue( NAME ).toString(), timeline.getSelectionSpan(), getEditMode() );
//				}
//				finally {
//					bird.releaseShared( Session.DOOR_TIME | Session.DOOR_MTE );
//				}
				if( proc != null ) start( proc );
			}
		}
	}

	private class ActionCopy
	extends MenuAction
	{
		protected ActionCopy() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}

		protected ClipboardTrackList getSelectionAsTrackList()
		{
			final Span span;
			
//			if( !bird.attemptShared( Session.DOOR_TIME | Session.DOOR_TRACKS, 250 )) return null;
//			try {
				span = timeline.getSelectionSpan();
				if( span.isEmpty() ) return null;

				return new ClipboardTrackList( Session.this );
//			}
//			finally {
//				bird.releaseShared( Session.DOOR_TIME | Session.DOOR_TRACKS );
//			}
		}

		protected boolean perform()
		{
			boolean						success	= false;
			final ClipboardTrackList	tl		= getSelectionAsTrackList();

System.out.println( "A" );
			if( tl == null ) return success;
System.out.println( "B" );

			try {
				AbstractApplication.getApplication().getClipboard().setContents( tl, tl );
				success = true;
			}
			catch( IllegalStateException e1 ) {
				System.err.println( getResourceString( "errClipboard" ));
			}

			return success;
		}
	}
	
	private class ActionPaste
	extends MenuAction
	implements ProcessingThread.Client
	{
		protected ActionPaste() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		protected void perform()
		{
			perform( getValue( NAME ).toString(), getEditMode() );
		}
		
		private void perform( String name, int mode )
		{
			final Transferable			t;
			final ClipboardTrackList	tl;

			try {
System.out.println( "C" );
				t = AbstractApplication.getApplication().getClipboard().getContents( this );
				if( t == null ) return;
System.out.println( "D" );
				
				if( !t.isDataFlavorSupported( ClipboardTrackList.trackListFlavor )) return;
System.out.println( "E" );
				tl = (ClipboardTrackList) t.getTransferData( ClipboardTrackList.trackListFlavor );
			}
			catch( IOException e11 ) {
				System.err.println( e11.getLocalizedMessage() );
				return;
			}
			catch( UnsupportedFlavorException e11 ) {
				System.err.println( e11.getLocalizedMessage() );
				return;
			}
			catch( IllegalStateException e11 ) {
				System.err.println( getResourceString( "errClipboard" ));
				return;
			}
			
			if( !checkProcess() ) return;
System.out.println( "F" );
			final ProcessingThread proc = initiate( tl, timeline.getPosition(), name, mode );	// XXX sync
			if( proc != null ) {
				start( proc );
			}
		}

		protected ProcessingThread initiate( ClipboardTrackList tl, long insertPos, String name, int mode )
		{
			if( !checkProcess() ) return null;
			
			if( (insertPos < 0) || (insertPos > timeline.getLength()) ) throw new IllegalArgumentException( String.valueOf( insertPos ));
			
			final ProcessingThread		proc;
			final Span					oldSelSpan, insertSpan, copySpan, cutTimelineSpan;
			final AbstractCompoundEdit	edit;
			final Flag					hasSelectedAudio;
			final List					tis;
			final boolean				expTimeline, cutTimeline;
			final long					docLength, pasteLength, preMaxLen, postMaxLen;
			final BlendContext			bcPre, bcPost;
			
			hasSelectedAudio	= new Flag( false );
			tis					= Track.getInfos( selectedTracks.getAll(), tracks.getAll() );
			if( !checkSyncedAudio( tis, mode == EDIT_INSERT, null, hasSelectedAudio )) return null;

			expTimeline			= (mode == EDIT_INSERT) && hasSelectedAudio.isSet();
			docLength			= timeline.getLength();
			pasteLength			= expTimeline ? tl.getSpan().getLength() :
				Math.min( tl.getSpan().getLength(), docLength - insertPos );
			if( pasteLength == 0 ) return null;
			
			if( mode == EDIT_INSERT ) {
				/*
				 *	before paste:
				 * 
				 *	 maxRight / post   maxLeft / pre
				 *
				 *	|                 |              |
				 *	|                 |              |
				 *	|                 |              |
				 *	|        A        |     B        |
				 *	+-----------------+--------------+
				 *	                  |
				 *	               insertPos
				 *
				 *	after paste:
				 * 
				 *	|                 | B #$$$$# A |              |
				 *	|                 |  ##$$$$##  |              |
				 *	|                 | ###$$$$### |              |
				 *	|        A        |####$$$$####|      B       |
				 *	+-----------------+------------+--------------+
				 *	                  |
				 *	               insertPos
				 */
				// note: now the discrepancy between postMaxLen and preMaxLen is
				// limited to 100%, so pasting at the very end or beginning of
				// a doc will not produce a single sided xfade any more
				// (answering bug 1922862)
				if( insertPos < (docLength - insertPos) ) {
					postMaxLen	= Math.min( insertPos, pasteLength >> 1 );
//					preMaxLen	= Math.min( docLength - insertPos, pasteLength - postMaxLen );
					preMaxLen	= Math.min( postMaxLen << 1, Math.min( docLength - insertPos, pasteLength - postMaxLen ));
//System.out.println( "A" );
				} else {
					preMaxLen	= Math.min( docLength - insertPos, pasteLength >> 1 );
					postMaxLen	= Math.min( preMaxLen << 1, Math.min( insertPos, pasteLength - preMaxLen ));
//System.out.println( "B" );
				}
			} else {
				preMaxLen	= pasteLength >> 1;	// note: pasteLength already clipped to be <= docLength - insertPos !
				postMaxLen	= pasteLength - preMaxLen;
//System.out.println( "C" );
			}
			bcPre			= createBlendContext( preMaxLen, 0, hasSelectedAudio.isSet() );
			bcPost			= createBlendContext( postMaxLen, 0, hasSelectedAudio.isSet() );
//System.out.println( "D ; preMaxLen = " + preMaxLen + "; postMaxLen = " + postMaxLen + "; bcPre.getLeftLen() = " + (bcPre == null ? null : String.valueOf( bcPre.getLeftLen())) + "; bcPre.getRightLen() = " + (bcPre == null ? null : String.valueOf( bcPre.getRightLen() )) + "; bcPost.getLeftLen() = " + (bcPost == null ? null : String.valueOf( bcPost.getLeftLen() )) + "; bcPost.getRightLen() = " + (bcPost == null ? null : String.valueOf( bcPost.getRightLen() )));

//			if( bcPre != null )  System.out.println( "bcPre  : " + bcPre.getLen() + ", " + bcPre.getLeftLen() + ", "+ bcPre.getRightLen() );
//			if( bcPost != null ) System.out.println( "bcPost : " + bcPost.getLen() + ", " + bcPost.getLeftLen() + ", "+ bcPost.getRightLen() );
			
			insertSpan			= new Span( insertPos, insertPos + pasteLength );
			copySpan			= new Span( tl.getSpan().start, tl.getSpan().start + pasteLength );
			cutTimeline			= (mode == EDIT_INSERT) && !hasSelectedAudio.isSet();
			cutTimelineSpan		= cutTimeline ? new Span( docLength, docLength + pasteLength ) : null;
			
			edit			= new BasicCompoundEdit( name );
			oldSelSpan		= timeline.getSelectionSpan();
			if( !oldSelSpan.isEmpty() ) { // deselect
				edit.addPerform( TimelineVisualEdit.select( this, Session.this, new Span() ));
			}

			proc	= new ProcessingThread( this, getFrame(), name );
			proc.putClientArg( "tl", tl );
			proc.putClientArg( "pos", new Long( insertPos ));
			proc.putClientArg( "mode", new Integer( mode ));
			proc.putClientArg( "tis", tis );
			proc.putClientArg( "pasteLen", new Long( pasteLength ));
			proc.putClientArg( "exp", new Boolean( expTimeline ));
			proc.putClientArg( "bcPre", bcPre );
			proc.putClientArg( "bcPost", bcPost );
			proc.putClientArg( "insertSpan", insertSpan );
			proc.putClientArg( "copySpan", copySpan );
			proc.putClientArg( "cut", new Boolean( cutTimeline ));
			proc.putClientArg( "cutSpan", cutTimelineSpan );
			proc.putClientArg( "edit", edit );

			return proc;
		}
		
		// --------- ProcessingThread.Client interface ---------

		/**
		 *  This method is called by ProcessingThread
		 */
		public int processRun( ProcessingThread context )
		throws IOException
		{
			final ClipboardTrackList		tl					= (ClipboardTrackList) context.getClientArg( "tl" );
			final long						insertPos			= ((Long) context.getClientArg( "pos" )).longValue();
			final int						mode				= ((Integer) context.getClientArg( "mode" )).intValue();
			final List						tis					= (List) context.getClientArg( "tis" );
			final AbstractCompoundEdit		edit				= (AbstractCompoundEdit) context.getClientArg( "edit" );
			final BlendContext				bcPre				= (BlendContext) context.getClientArg( "bcPre" );
			final BlendContext				bcPost				= (BlendContext) context.getClientArg( "bcPost" );
			final Span						insertSpan			= (Span) context.getClientArg( "insertSpan" );
			final Span						copySpan			= (Span) context.getClientArg( "copySpan" );
			final boolean					cutTimeline			= ((Boolean) context.getClientArg( "cut" )).booleanValue();
			final Span						cutTimelineSpan		= (Span) context.getClientArg( "cutSpan" );
			final long						delta				= insertPos - tl.getSpan().start;
			Track.Info						ti;
			Trail							srcTrail;
			AudioTrail						audioTrail;
			Transmitter						trns;
			boolean[]						trackMap;
			boolean							isAudio, isTrns, pasteAudio;

System.out.println( "paste to:" );
Track.debugDump( tis );

			for( int i = 0; i < tis.size(); i++ ) {
				ti		= (Track.Info) tis.get( i );
				if( ti.selected ) {	// ----------------- selected tracks -----------------
					try {
						ti.trail.editBegin( edit );
// EEE
//						isAudio	= ti.track instanceof AudioTrack;
isAudio = false;
						isTrns		= ti.tracks.get( 0 ) instanceof Transmitter;
						srcTrail	= tl.getSubTrail( ti.trail.getClass(), ti.trackIndex );
					
						if( isAudio ) {
							pasteAudio = (srcTrail != null) && (((AudioTrail) srcTrail).getChannelNum() > 0);
						} else if( isTrns ) {
							pasteAudio = (srcTrail != null);
						} else {
							pasteAudio = false;
						}
						
						if( mode == EDIT_INSERT ) {
							ti.trail.editInsert( this, insertSpan, edit );
							if( cutTimeline ) ti.trail.editRemove( this, cutTimelineSpan, edit );
						} else if( pasteAudio || ((mode == EDIT_OVERWRITE) && !(isAudio || isTrns)) ) { // Audio needs to be cleared even in Mix mode!
							ti.trail.editClear( this, insertSpan, edit );
						}
						
						if( pasteAudio ) {
							audioTrail	= (AudioTrail) ti.trail;
							trackMap	= tl.getChannelMap( srcTrail );
							
							final int[] trackMap2 = new int[ audioTrail.getChannelNum() ];
							for( int j = 0, k = 0; j < trackMap2.length; j++ ) {
								if( ti.channelMap[ j ]) {	// target track selected
									for( ; (k < trackMap.length) && !trackMap[ k ] ; k++ ) ;
									if( k < trackMap.length ) {	// source track exiting
										trackMap2[ j ] = k++;
									} else if( tl.getTrackNum( ti.trail.getClass() ) > 0 ) {		// ran out of source tracks, fold over (simple mono -> stereo par exemple)
										for( k = 0; !trackMap[ k ] ; k++ ) ;
										trackMap2[ j ] = k++;
									} else {
										trackMap2[ j ] = -1;		// there aren't any clipboard tracks ....
									}
								} else {							// target track not selected
									trackMap2[ j ] = -1;
								}
							}
							if( !audioTrail.copyRangeFrom( (AudioTrail) srcTrail, copySpan, insertPos, mode, this, edit, trackMap2, bcPre, bcPost )) return CANCELLED;

						} else if( (ti.tracks.size() == 1) && (tl.getTrackNum( ti.trail.getClass() ) == 1) ) {
							ti.trail.editAddAll( this, srcTrail.getCuttedRange(
								copySpan, true, srcTrail.getDefaultTouchMode(), delta ), edit );
						}
					}
					finally {
						ti.trail.editEnd( edit );
					}
				}
			}

			return DONE;
		}

		public void processFinished( ProcessingThread context )
		{
			final ProcessingThread.Client	doneAction	= (ProcessingThread.Client) context.getClientArg( "doneAction" );
			final AbstractCompoundEdit		edit		= (AbstractCompoundEdit) context.getClientArg( "edit" );
			final boolean					expTimeline	= ((Boolean) context.getClientArg( "exp" )).booleanValue();
			final long						pasteLength	= ((Long) context.getClientArg( "pasteLen" )).longValue();
			final Span						insertSpan	= (Span) context.getClientArg( "insertSpan" );
			
			if( (context.getReturnCode() == DONE) ) {
				if( expTimeline && (pasteLength != 0) ) {	// adjust timeline
					edit.addPerform( new EditSetTimelineLength( this, Session.this, timeline.getLength() + pasteLength ));
					if( timeline.getVisibleSpan().isEmpty() ) {
						edit.addPerform( TimelineVisualEdit.scroll( this, Session.this, insertSpan ));
					}
				}
				if( !insertSpan.isEmpty() ) {
					edit.addPerform( TimelineVisualEdit.select( this, Session.this, insertSpan ));
					edit.addPerform( TimelineVisualEdit.position( this, Session.this, insertSpan.stop ));
				}

				edit.perform();
				edit.end();
				getUndoManager().addEdit( edit );
			} else {
				edit.cancel();
			}

//			if( doneAction != null ) doneAction.processFinished( context, doc );
			if( doneAction != null ) doneAction.processFinished( context );
		}

		// mte will check pt.shouldCancel() itself
		public void processCancel( ProcessingThread context ) { /* ignored */ }
	} // class actionPasteClass

	/**
	 *	@todo	when a cutted region spans entire view,
	 *			selecting undo results in empty visible span
	 */
	private class ActionDelete
	extends MenuAction
	implements ProcessingThread.Client
	{
		protected ActionDelete() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		protected void perform()
		{		
			final Span				span	= timeline.getSelectionSpan(); // XXX sync
			if( span.isEmpty() ) return;
			
			final ProcessingThread	proc		= initiate( getValue( NAME ).toString(), span, getEditMode() );
			if( proc != null ) start( proc );
		}
		
		// XXX sync
		protected ProcessingThread initiate( String name, Span span, int mode )
		{
			if( !checkProcess() ) return null;

			final BlendContext			bc;
			final long					cutLength, docLength, newDocLength, maxLen;
			final Flag					hasSelectedAudio;
			final List					tis;
			final AbstractCompoundEdit	edit;
			final boolean 				cutTimeline;
			final Span					cutTimelineSpan, selSpan;
			Span						visiSpan;

			hasSelectedAudio	= new Flag( false );
			tis					= Track.getInfos( selectedTracks.getAll(), tracks.getAll() );
			if( !checkSyncedAudio( tis, mode == EDIT_INSERT, null, hasSelectedAudio )) return null;
			
			docLength			= timeline.getLength();
			cutLength			= span.getLength();
			if( mode == EDIT_INSERT ) {
				/*
				 *	before delete:
				 * 
				 *	|,,,,,,,,,,,,,,,,,|$$$$$$$#######|............|
				 *	|,,,,,,,,,,,,,,,,,|$$$$$$$#######|............|
				 *	|,,,,,,,,,,,,,,,,,|$$$$$$$#######|............|
				 *	|,,,,,,,,A,,,,,,,,|$$B1$$$###B2##|......C.....|
				 *	+-----------------+--------------+------------+
				 *	                  |     span     |
				 *
				 *	after delete:
				 *	              left right
				 *	|,,,,,,,,,,,,,    |            |
				 *	|,,,,,,,,,,,,,,,  |            |
				 *	|,,,,,,,,,,,,,,,,,|            |
				 *	|,,,,,,,,,,,,,,,,,|$$          |
				 *	|,,,,,,,,,,,,,,,,,|$$$$        |
				 *	|,,,,,,,,A,,,,,,,,|$B2$$$      |
				 *	+-----------------+------------+
				 *	                  |
				 *			plus
				 *	|                 |    ........|
				 *	|                 |  ..........|
				 *	|                 |............|
				 *	|               ##|............|
				 *	|             ####|............|
				 *	|           ###B2#|......C.....|
				 *	+-----------------+------------+
				 *	                  |
				 *	              span.start
				 */
				maxLen				= Math.min( cutLength, Math.min( span.start, docLength - span.stop ) << 1 );
				bc					= createBlendContext( maxLen >> 1, (maxLen + 1) >> 1, hasSelectedAudio.isSet() );
			} else {
				/*
				 *	after delete:
				 *                     blend-   blend-
				 *                     Len      Len
				 *	|,,,,,,,,,,,,,,,,,|$            #|............|
				 *	|,,,,,,,,,,,,,,,,,|$$          ##|............|
				 *	|,,,,,,,,,,,,,,,,,|$$$        ###|............|
				 *	|,,,,,,,,A,,,,,,,,|$B1$      #B2#|......C.....|
				 *	+-----------------+--------------+------------+
				 *	                  |     span     |
				 */
				maxLen				= cutLength >> 1;
				bc					= createBlendContext( maxLen, 0, hasSelectedAudio.isSet() );
			}
//			bc					= createBlendContext( Math.min( cutLength, span.start ), Math.min( cutLength, docLength - span.stop ), hasSelectedAudio );
			edit				= new BasicCompoundEdit( name );

//			if( bc != null )  System.out.println( "bc  : " + bc.getLen() + ", " + bc.getLeftLen() + ", "+ bc.getRightLen() );
			
			cutTimeline			= (mode == EDIT_INSERT) && hasSelectedAudio.isSet();
			newDocLength		= cutTimeline ? docLength - cutLength : docLength;
			cutTimelineSpan		= cutTimeline ? new Span( newDocLength, docLength ) : null;
			selSpan				= timeline.getSelectionSpan();
			
			if( (mode == EDIT_INSERT) && !selSpan.isEmpty() ) {
				edit.addPerform( TimelineVisualEdit.position( this, Session.this, span.start ));
				edit.addPerform( TimelineVisualEdit.select( this, Session.this, new Span() ));
			}
			if( cutTimeline ) {
				visiSpan = timeline.getVisibleSpan();
				if( visiSpan.stop > span.start ) {
					if( visiSpan.stop > newDocLength ) {
						visiSpan = new Span( Math.max( 0, newDocLength - visiSpan.getLength() ), newDocLength );
						TimelineVisualEdit tve = TimelineVisualEdit.scroll( this, Session.this, visiSpan );
						edit.addPerform( tve );
					} // else visiSpan untouched
				}
				edit.addPerform( new EditSetTimelineLength( this, Session.this, newDocLength ));
			}

			final ProcessingThread proc = new ProcessingThread( this, getFrame(), name );
			proc.putClientArg( "span", span );
			proc.putClientArg( "mode", new Integer( mode ));
			proc.putClientArg( "tis", tis );
			proc.putClientArg( "edit", edit );
			proc.putClientArg( "bc", bc );
			proc.putClientArg( "cut", new Boolean( cutTimeline ));
			proc.putClientArg( "cutSpan", cutTimelineSpan );
			return proc;
		}

		// --------- ProcessingThread.Client interface ---------
		
		/**
		 *  This method is called by ProcessingThread
		 */
		public int processRun( ProcessingThread context )
		throws IOException
		{
			final Span						span				= (Span) context.getClientArg( "span" );
			final int						mode				= ((Integer) context.getClientArg( "mode" )).intValue();
			final List						tis					= (List) context.getClientArg( "tis" );
			final AbstractCompoundEdit		edit				= (AbstractCompoundEdit) context.getClientArg( "edit" );
			final BlendContext				bc					= (BlendContext) context.getClientArg( "bc" );
			final long						left				= bc == null ? 0L : bc.getLeftLen();
			final long						right				= bc == null ? 0L : bc.getRightLen();
			final boolean					cutTimeline			= ((Boolean) context.getClientArg( "cut" )).booleanValue();
			final Span						cutTimelineSpan		= (Span) context.getClientArg( "cutSpan" );
			AudioTrail						audioTrail;
			Track.Info						ti;
			boolean							isAudio;

			for( int i = 0; i < tis.size(); i++ ) {
				ti		= (Track.Info) tis.get( i );
				try {
					ti.trail.editBegin( edit );
					isAudio = ti.trail instanceof AudioTrail;
					if( ti.selected ) {
						if( mode == EDIT_INSERT ) {
							if( isAudio ) {
								if( bc == null ) {
									ti.trail.editRemove( this, span, edit );
								} else {
									ti.trail.editRemove( this, new Span( span.start - left, span.stop + right ), edit );
									ti.trail.editInsert( this, new Span( span.start - left, span.start + right ), edit );
								}
								audioTrail = (AudioTrail) ti.trail;
								audioTrail.clearRange( span, EDIT_INSERT, this, edit, ti.channelMap, bc );
							} else {
								ti.trail.editRemove( this, span, edit );
							}
						} else {
							ti.trail.editClear( this, span, edit );
							if( isAudio ) {
								audioTrail = (AudioTrail) ti.trail;
								audioTrail.clearRange( span, EDIT_OVERWRITE, this, edit, ti.channelMap, bc );
							}
						}
					} else if( cutTimeline ) {
						ti.trail.editRemove( this, cutTimelineSpan, edit );
					}
				}
				finally {
					ti.trail.editEnd( edit );
				}
			}
			return DONE;
		} // run

		public void processFinished( ProcessingThread context )
		{
			final AbstractCompoundEdit edit = (AbstractCompoundEdit) context.getClientArg( "edit" );

			if( context.getReturnCode() == DONE ) {
				edit.perform();
				edit.end();
				getUndoManager().addEdit( edit );
			} else {
				edit.cancel();
			}
		}

		// mte will check pt.shouldCancel() itself
		public void processCancel( ProcessingThread context ) { /* ignore */ }
	} // class actionDeleteClass

	private class ActionTrim
	extends MenuAction
	{
		protected ActionTrim() { /* empty */ }

		// performs inplace (no runnable processing) coz it's always fast
		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		protected void perform()
		{
			final Span					selSpan, deleteBefore, deleteAfter;
			final BasicCompoundEdit		edit;
			final List					tis;
			Track.Info					ti;
			boolean						success	= false;

			edit			= new BasicCompoundEdit( getValue( NAME ).toString() );
			
			try {
				selSpan			= timeline.getSelectionSpan();
//				if( selSpan.isEmpty() ) return;
				tis				= Track.getInfos( selectedTracks.getAll(), tracks.getAll() );
				deleteBefore	= new Span( 0, selSpan.start );
				deleteAfter		= new Span( selSpan.stop, timeline.getLength() );

				// deselect
				edit.addPerform( TimelineVisualEdit.select( this, Session.this, new Span() ));
				edit.addPerform( TimelineVisualEdit.position( this, Session.this, 0 ));

				if( !deleteAfter.isEmpty() || !deleteBefore.isEmpty() ) {
					for( int i = 0; i < tis.size(); i++ ) {
						ti = (Track.Info) tis.get( i );
						ti.trail.editBegin( edit );
						try {
							if( !deleteAfter.isEmpty() ) ti.trail.editRemove( this, deleteAfter, edit );
							if(	!deleteBefore.isEmpty() ) ti.trail.editRemove( this, deleteBefore, edit );
						}
						finally {
							ti.trail.editEnd( edit );
						}
					}
				}

				edit.addPerform( new EditSetTimelineLength( this, Session.this, selSpan.getLength() ));
				edit.addPerform( TimelineVisualEdit.select( this, Session.this, selSpan.shift( -selSpan.start )));

				edit.perform();
				edit.end();
				getUndoManager().addEdit( edit );
				success = true;
			}
			finally {
				if( !success ) edit.cancel();
			}
		}
	} // class actionTrimClass

	/**
	 *	@todo	when edit mode != EDIT_INSERT, audio tracks are cleared which should be bypassed and vice versa
	 *	@todo	waveform display not automatically updated when edit mode != EDIT_INSERT
	 */
	private class ActionSilence
	extends MenuAction
	implements ProcessingThread.Client
	{
		private Param		value = null;
		private ParamSpace	space = null;
	
		protected ActionSilence() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		private void perform()
		{
			final SpringPanel			msgPane;
			final int					result;
			final ParamField			ggDuration;
			final Param					durationSmps;
			final DefaultUnitTranslator	timeTrans;
			
			msgPane			= new SpringPanel( 4, 2, 4, 2 );
			timeTrans		= new DefaultUnitTranslator();
			ggDuration		= new ParamField( timeTrans );
			ggDuration.addSpace( ParamSpace.spcTimeHHMMSS );
			ggDuration.addSpace( ParamSpace.spcTimeSmps );
			ggDuration.addSpace( ParamSpace.spcTimeMillis );
			ggDuration.addSpace( ParamSpace.spcTimePercentF );
			msgPane.gridAdd( ggDuration, 0, 0 );
			msgPane.makeCompactGrid();
			GUIUtil.setInitialDialogFocus( ggDuration );

			timeTrans.setLengthAndRate( timeline.getLength(), timeline.getRate() );

			if( value == null ) {
				ggDuration.setValue( new Param( 60.0, ParamSpace.TIME | ParamSpace.SECS ));
			} else {
				ggDuration.setSpace( space );
				ggDuration.setValue( value );
			}

			final JOptionPane op = new JOptionPane( msgPane, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION );
//			result = JOptionPane.showOptionDialog( getFrame() == null ? null : getFrame().getWindow(), msgPane, getValue( NAME ).toString(),
//				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null );
			result = BasicWindowHandler.showDialog( op, getTimelineFrame() == null ? null : getTimelineFrame().getWindow(), getValue( NAME ).toString() );

			if( result == JOptionPane.OK_OPTION ) {
				value			= ggDuration.getValue();
				space			= ggDuration.getSpace();
				durationSmps	= timeTrans.translate( value, ParamSpace.spcTimeSmps );
				if( durationSmps.val > 0.0 ) {
					final ProcessingThread proc;
					
					proc = initiate( timeline.getPosition(), (long) durationSmps.val );
					if( proc != null ) start( proc );
				}
			}
		}

		public ProcessingThread initiate( long pos, long numFrames )
		{
			if( !checkProcess() || (numFrames == 0) ) return null;
			
			if( numFrames < 0 ) throw new IllegalArgumentException( String.valueOf( numFrames ));
			if( (pos < 0) || (pos > timeline.getLength()) ) throw new IllegalArgumentException( String.valueOf( pos ));

			final ProcessingThread 		proc;
			final AbstractCompoundEdit	edit;
			final Span					oldSelSpan, insertSpan;

			proc = new ProcessingThread( this, getFrame(), getValue( NAME ).toString() );

			edit		= new BasicCompoundEdit( proc.getName() );
			oldSelSpan	= timeline.getSelectionSpan();
			insertSpan	= new Span( pos, pos + numFrames );

			if( !oldSelSpan.isEmpty() ) { // deselect
				edit.addPerform( TimelineVisualEdit.select( this, Session.this, new Span() ));
			}

			proc.putClientArg( "tis", Track.getInfos( selectedTracks.getAll(), tracks.getAll() ));
			proc.putClientArg( "edit", edit );
			proc.putClientArg( "span", insertSpan );
			return proc;
		}

		/**
		 *  This method is called by ProcessingThread
		 */
		public int processRun( ProcessingThread context )
		throws IOException
		{
			final List					tis			= (List) context.getClientArg( "tis" );
			final AbstractCompoundEdit	edit		= (AbstractCompoundEdit) context.getClientArg( "edit" );
			final Span					insertSpan	= (Span) context.getClientArg( "span" );
			Track.Info					ti;
			AudioTrail					audioTrail;

			for( int i = 0; i < tis.size(); i++ ) {
				ti = (Track.Info) tis.get( i );
				ti.trail.editBegin( edit );
				try {
					ti.trail.editInsert( this, insertSpan, edit );
					if( ti.trail instanceof AudioTrail ) {
						audioTrail			= (AudioTrail) ti.trail;							
						audioTrail.editAdd( this, audioTrail.allocSilent( insertSpan ), edit );
					}
				}
				finally {
					ti.trail.editEnd( edit );
				}
			}
			return DONE;
		}

		public void processFinished( ProcessingThread context )
		{
			final AbstractCompoundEdit	edit		= (AbstractCompoundEdit) context.getClientArg( "edit" );
			final Span					insertSpan	= (Span) context.getClientArg( "span" );

			if( context.getReturnCode() == DONE ) {
				if( !insertSpan.isEmpty() ) {	// adjust timeline
					edit.addPerform( new EditSetTimelineLength( this, Session.this, timeline.getLength() + insertSpan.getLength() ));
					if( timeline.getVisibleSpan().isEmpty() ) {
						edit.addPerform( TimelineVisualEdit.scroll( this, Session.this, insertSpan ));
					}
				}
				if( !insertSpan.isEmpty() ) {
					edit.addPerform( TimelineVisualEdit.select( this, Session.this, insertSpan ));
					edit.addPerform( TimelineVisualEdit.position( this, Session.this, insertSpan.stop ));
				}
				edit.perform();
				edit.end();
				getUndoManager().addEdit( edit );
			} else {
				edit.cancel();
			}
		}

		// mte will check pt.shouldCancel() itself
		public void processCancel( ProcessingThread context ) { /* ignore */ }
	} // class actionSilenceClass
}