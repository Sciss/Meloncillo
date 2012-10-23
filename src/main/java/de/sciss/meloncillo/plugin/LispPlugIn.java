/*
 *  LispPlugIn.java
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
 *		24-Jul-04   created from former LispRenderPlugIn. Reload refills GUI.
 *		26-Aug-04	creates hash entries for rcv anchor + size
 *		01-Sep-04	commented
 *		18-Apr-05	supports help attribute in synthcontrollist
 */

// XXX TO-DO: DISKBUFSIZE hash entry should be removed ?
// should listen to preference changes (e.g. CSOUNDAPP)

package de.sciss.meloncillo.plugin;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jatha.compile.CompilerException;
import org.jatha.compile.LispCompiler;
import org.jatha.dynatype.LispHashTable;
import org.jatha.dynatype.LispNumber;
import org.jatha.dynatype.LispString;
import org.jatha.dynatype.LispValue;
import org.jatha.machine.SECDMachine;
import org.jatha.read.LispParser;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.EventManager;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.PrefCheckBox;
import de.sciss.gui.PrefComboBox;
import de.sciss.gui.StringItem;
import de.sciss.io.IOUtil;
import de.sciss.meloncillo.lisp.AdvancedJatha;
import de.sciss.meloncillo.lisp.BasicLispPrimitive;
import de.sciss.meloncillo.lisp.ExecutePrimitive;
import de.sciss.meloncillo.lisp.GadgetMakePrimitive;
import de.sciss.meloncillo.lisp.SessionPropAddPrimitive;
import de.sciss.meloncillo.lisp.TempFileMakePrimitive;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionGroup;
import de.sciss.meloncillo.session.SessionObject;
import de.sciss.meloncillo.util.MapManager;
import de.sciss.meloncillo.util.PrefsUtil;

/**
 *  A basic superclass for all lisp code executing
 *	plug-ins. It provides a lisp environment and
 *	methods for internalizing custom functions and symbols
 *	to this environment. It also provides a common
 *	GUI surface and handles loading and displaying
 *	of lisp source codes. Internal classes provide
 *	extra lisp functions for requesting source data
 *	and announcing target data.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo		preference changes are not
 *				automatically forwarded to the lisp
 *				hash tables
 */
public abstract class LispPlugIn
extends JPanel
implements PlugIn, PreferenceChangeListener
{
	/**
	 *	The class preference are located as a separate
	 *	subnode of the session prefs, i.e. all keys in here
	 *	are stored in the session files.
	 *	The lisp source code and verbosity gadgets (KEY_VERBOSE) will
	 *	store their values here. GUI elements created with
	 *	the lisp source function (make-gadget) will also
	 *	store their values here. Subclasses can add their
	 *	specific prefs.
	 */
	protected Preferences			classPrefs;
	
	/**
	 *	This field will be valid after calling initJatha()
	 *	and contains a lisp environment featuring the custom
	 *	functions and symbols.
	 */
	protected AdvancedJatha			jatha					= null;

	/**
	 *	This hashtable will be internalized in the
	 *	lisp environment after calling initJatha().
	 *	Keys are mapping the most important plug-in
	 *	context objects like transmitters and receivers.
	 */
	protected LispHashTable			cilloHash;
	
	/**
	 *	This hashtable will be internalized in the
	 *	lisp environment after calling initJatha().
	 *	Keys are mapping the plug-in preference pane
	 *	settings like supercollider osc port or csound
	 *	application path.
	 */
	protected LispHashTable			prefsHash;

	private Session					doc;

	// ------- RenderSource types -------
	/**
	 *	Source/target request type: lisp script wants
	 *	or offers trajectory data
	 */
	protected static final int	REQUEST_TRAJ		= 0;
	/**
	 *	Source request type: lisp script wants trajectory data
	 */
	protected static final int	REQUEST_SENSE		= 1;
	/**
	 *	Request type: lisp script wants data
	 *	to be resampled before supply
	 */
	protected static final int	REQUEST_RESAMPLE	= 2;
	/**
	 *	Target request type: lisp script provides
	 *	synchronization object
	 */
	protected static final int	REQUEST_SYNC		= 3;
	
	/**
	 *	Array of known request types
	 *	(strings in the lisp function call)
	 */
	protected static final String[] requestKeyNames = {
		"TRAJ", "SENSE", "RESAMPLE", "SYNC"
	};

	// class prefs
	private static final String		KEY_LISPSOURCE			= "lispsource";
	/**
	 *	Key of the verbose gadget in the class prefs.<p>
	 *	Value: Boolean representing the gadget's state.<br>
	 */
	protected static final String	KEY_VERBOSE				= "verbose";
//	private static final String		NODE_LISP				= "lispprefs";  // lisp preference sub-node

	private PrefComboBox			ggLispSource;
	private PrefCheckBox			ggVerbose;
	private JButton					ggReload;
	private JPanel					panelLispGUI			= null;

	// ----------- lisp related -----------
	private File					synthControlListFile	= null;
	private List					collSynthControls		= new ArrayList();

	private String					lispSourceName			= null;
	private boolean					contextKnown			= false;

	private LRPGadgetMakePrimitive		gadgetMakePrimitive;
	private LRPTempFileMakePrimitive	tempFileMakePrimitive;
	private LRPExecutePrimitive			executePrimitive;
	private	SessionPropAddPrimitive	sessionPropertyAddPrimitive;

	/**
	 *	Instance of a source request lisp function
	 *	which can be queryied for a list of requests.
	 */
	protected LRPSourceRequestPrimitive	sourceRequestPrimitive;
	/**
	 *	Instance of a target request lisp function
	 *	which can be queryied for a list of requests.
	 */
	protected LRPTargetRequestPrimitive targetRequestPrimitive;
	
	private static final int	DISKBUFSIZE	= 32768; // 4194304; // 32768 XXX
	
	
	private static final String KEY_CILLO			= "CILLO";
	private static final String KEY_RECEIVERS		= "RECEIVERS";
	private static final String KEY_TRANSMITTERS	= "TRANSMITTERS";
	private static final String KEY_GROUPS			= "GROUPS";
	private static final String KEY_TIMELINE		= "TIMELINE";
	private static final String KEY_PREFERENCES		= "PREFERENCES";
	
	/**
	 *	Empty constructor called 
	 *	through Class.newInstance(). Basic
	 *	initialization is done a separate call to init()
	 */
	public LispPlugIn()
	{
		super();
	}
	
	public void init( Session doc )
	{
		this.doc	= doc;
		
		final de.sciss.app.Application	app			= AbstractApplication.getApplication();
		final String					className	= getClass().getName();

		classPrefs	= app.getUserPrefs().node( PrefsUtil.NODE_SESSION ).node(
						className.substring( className.lastIndexOf( '.' ) + 1 ));
		createSettingsView();

		// --- Listener ---
        new DynamicAncestorAdapter( new DynamicPrefChangeManager( app.getUserPrefs().node(
			PrefsUtil.NODE_PLUGINS ), new String[] { getSourceListKey() }, this )).addTo( this );
        new DynamicAncestorAdapter( new DynamicPrefChangeManager( classPrefs,
			new String[] { KEY_LISPSOURCE }, this )).addTo( this );
	}
	
	/**
	 *  Return the key in the plug-ins
	 *  node that represents the synth control
	 *  list we're interested in listening to
	 */
	protected abstract String getSourceListKey();
	
	// --- GUI Presentation ---
	
	// sync : attempts on door_grp
	public JComponent getSettingsView( PlugInContext context )
	{
		contextKnown = false;
	
		if( !doc.bird.attemptShared( Session.DOOR_GRP, 250 )) return null;
		try {
			initLispSymbols( context );
			contextKnown = true;
			fillLispGUI();
		}
		catch( IOException e1 ) {
			GUIUtil.displayError( this, e1, AbstractApplication.getApplication().getResourceString(
				"errLispLoadSource" ));
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_GRP );
		}
		return this;
	}
	
	private void createSettingsView()
	{
		int								rows;
		JLabel							lab;
		JPanel							panelUs;
		final LispPlugIn				lp		= this;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
	
		this.setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ));
		panelUs			= new JPanel( new SpringLayout() );
		rows			= 0;

		ggLispSource	= new PrefComboBox();
		ggLispSource.setPreferences( classPrefs, KEY_LISPSOURCE );
		lab				= new JLabel( app.getResourceString( "labelLispSource" ));
		ggReload		= new JButton( app.getResourceString( "labelReload" ));
		ggReload.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				try {
					String oldSourceName	= lispSourceName;
					loadLispSource( null );
					loadLispSource( oldSourceName );
				} catch( Exception e1 ) {
					GUIUtil.displayError( lp, e1, app.getResourceString( "errLispLoadSource" ));
				}
			}
		});
		panelUs.add( lab );
		panelUs.add( ggLispSource );
		panelUs.add( ggReload );
		rows++;

		ggVerbose		= new PrefCheckBox();
		ggVerbose.setPreferences( classPrefs, KEY_VERBOSE );
		lab				= new JLabel( app.getResourceString( "labelVerbose" ));
		panelUs.add( lab );
		panelUs.add( ggVerbose );
		panelUs.add( new JLabel() );
		rows++;

		GUIUtil.makeCompactSpringGrid( panelUs, rows, 3, 4, 2, 4, 2 );	// #row #col initx inity padx pady

		panelLispGUI = new JPanel( new SpringLayout() );
		
//		this.removeAll();
		this.add( panelUs );
		this.add( panelLispGUI );
	}

	// sync: call in event thread!
	private void initJatha()
	{
		if( jatha != null ) return;
		
		LispString  symbolName;
	
		jatha					= new AdvancedJatha();
		cilloHash				= jatha.makeHashTable();
		prefsHash				= jatha.makeHashTable();
		symbolName				= jatha.makeString( KEY_PREFERENCES );
		cilloHash.setf_gethash( symbolName, jatha.makeSymbol( symbolName ).setf_symbol_value( prefsHash ));
		jatha.intern( KEY_CILLO, cilloHash );

		createCommonPrimitives( jatha );
		createSpecialPrimitives( jatha );
	}

	private void createCommonPrimitives( AdvancedJatha lisp )
	{
		gadgetMakePrimitive			= new LRPGadgetMakePrimitive( lisp );
		tempFileMakePrimitive		= new LRPTempFileMakePrimitive( lisp );
		executePrimitive			= new LRPExecutePrimitive( lisp );
		sourceRequestPrimitive		= new LRPSourceRequestPrimitive( lisp );
		targetRequestPrimitive		= new LRPTargetRequestPrimitive( lisp );
		sessionPropertyAddPrimitive	= new SessionPropAddPrimitive( lisp, doc );

		lisp.addPrimitive( gadgetMakePrimitive );
		lisp.addPrimitive( tempFileMakePrimitive );
		lisp.addPrimitive( executePrimitive );
		lisp.addPrimitive( sourceRequestPrimitive );
		lisp.addPrimitive( targetRequestPrimitive );
		lisp.addPrimitive( sessionPropertyAddPrimitive );
	}
	
	/**
	 *  Gets called when the lisp environment is
	 *	initialized. This class provides hash entries
	 *	for the common context and preferences objects
	 *	such as a list of transmitters and receivers.
	 *	Subclasses may wish to add custom functions, hence
	 *	they shall implement this method.
	 *
	 *	@param	lisp	the lisp environment to which 
	 *					new functions (primitives) can be added
	 *
	 *	@see	AdvancedJatha#addPrimitive( LispPrimitive )
	 */
	protected abstract void createSpecialPrimitives( AdvancedJatha lisp );

	/*
	 *  Provided variables for the lisp code. The lisp code finds
	 *  a global symbol "CILLO" which is a hash table. Keys are strings:
	 *	<pre>
	 *  "TRANSMITTERS"  -> (list) transmitter-hashtables
	 *  "RECEIVERS"		-> (list) receiver-hashtables
	 *  "TIMELINE"		-> (hashtable) timeline-hashtable
	 *  "PREFERENCES"   -> (hashtable) preferences-subset-hashtable
	 *  
	 *  the trns-hashtable has string keys:
	 *  
	 *  "NAME"			-> (string) transmitter name
	 *  
	 *  the rcv-hashtable has string keys:
	 *  
	 *  "NAME"			-> (string) receiver name
	 *  "ANCHOR"		-> (cons cell with two reals) receiver anchor x, y
	 *  "SIZE"			-> (cons cell with two reals) receiver size w, h
	 *
	 *  the timeline-hashtable has string keys:
	 *  
	 *  "START"			-> (real) render start in seconds
	 *  "STOP"			-> (real) render stop in seconds
	 *  
	 *  the prefs-hashtable has string keys:
	 *  
	 *  "DISKBUFSIZE"	-> (integer) preferred size of diskin buffers in sample frames<br>
	 *  "SENSEBUFSIZE"	-> (integer) sense of sense data buffer in frames<br>
	 *  "SENSERATE"		-> (integer) rate of sense buffer in hertz<br>
	 *  "INPUTCHANNELS"	-> (integer) number of hardware audio interface inputs<br>
	 *  "OUTPUTCHANNELS"-> (integer) number of hardware audio interface outputs<br>
	 *  "AUDIORATE"		-> (integer) rate of audio hardware in hertz<br>
	 *  "BASEDIRECTORY"	-> (string)  pathname of the synth control base directory. !not created here but in
	 *								 loadLispSource()!
	 *  "SUPERCOLLIDERAPP" -> (string) pathname of the sc offline application
	 *  "CSOUNDAPP"		-> (string)  pathname of the csound application
	 *  "SUPERCOLLIDEROSC" -> (cons) sc realtime osc socket (host-string port-integer)
	 *	</pre>
	 *
	 *	@synchronization	caller must have shared sync on DOOR_GRP
	 */
	private void initLispSymbols( PlugInContext context )
	{
		initJatha();

		createCommonSymbols( jatha, context );
		createSpecialSymbols( jatha, context );
	}

	// XXX sync unklar ? SessionObject-map koennte parallel veraendert werden?
	private void createCommonSymbols( AdvancedJatha lisp, PlugInContext context )
	{
		LispHashTable			lispHash;
		java.util.List			coll, coll2, collGroups, collGroups2, collGroups3, collGroups4;
		int						i, j;
		double					d1;
		String					val;
		Preferences				prefs;
		SessionObject			so;
	
		// --------------- create lisp symbols ---------------
		// GROUPS : list (list elements : hashtable)
		collGroups	= doc.getGroups().getAll();
		collGroups2	= new ArrayList( collGroups.size() );
		collGroups3	= new ArrayList( collGroups.size() );
		collGroups4	= new ArrayList( collGroups.size() );
		for( i = 0; i < collGroups.size(); i++ ) {
			so			= (SessionObject) collGroups.get( i );
			lispHash	= jatha.makeHashTable();
			sessionObjectToLispHash( so, lispHash );
			collGroups2.add( lispHash );
			collGroups3.add( new ArrayList() );
			collGroups4.add( new ArrayList() );
		}

		// RECEIVERS : list (list elements : hashtable)
		coll		= context.getReceivers();
		coll2		= new ArrayList( coll.size() );
		for( i = 0; i < coll.size(); i++ ) {
			so			= (SessionObject) coll.get( i );
			lispHash	= jatha.makeHashTable();
			sessionObjectToLispHash( so, lispHash );
			coll2.add( lispHash );
			for( j = 0; j < collGroups.size(); j++ ) {
				if( ((SessionGroup) collGroups.get( j )).getReceivers().contains( so )) {
					((java.util.List) collGroups3.get( j )).add( lispHash );
				}
			}
		}
		cilloHash.setf_gethash( jatha.makeString( KEY_RECEIVERS ), jatha.makeList( coll2 ));

		// TRANSMITTERS : list (list elements : hashtable)
		coll		= context.getTransmitters();
		coll2		= new ArrayList( coll.size() );
		for( i = 0; i < coll.size(); i++ ) {
			so			= (SessionObject) coll.get( i );
			lispHash	= jatha.makeHashTable();
			sessionObjectToLispHash( so, lispHash );
			coll2.add( lispHash );
			for( j = 0; j < collGroups.size(); j++ ) {
				if( ((SessionGroup) collGroups.get( j )).getTransmitters().contains( so )) {
					((java.util.List) collGroups4.get( j )).add( lispHash );
				}
			}
		}
		cilloHash.setf_gethash( jatha.makeString( KEY_TRANSMITTERS ), jatha.makeList( coll2 ));

		// complete GROUPS list
		for( i = 0; i < collGroups.size(); i++ ) {
			lispHash	= (LispHashTable) collGroups2.get( i );
			lispHash.setf_gethash( jatha.makeString( KEY_RECEIVERS ), jatha.makeList( (java.util.List) collGroups3.get( i )));
			lispHash.setf_gethash( jatha.makeString( KEY_TRANSMITTERS ), jatha.makeList( (java.util.List) collGroups4.get( i )));
		}
		cilloHash.setf_gethash( jatha.makeString( KEY_GROUPS ), jatha.makeList( collGroups2 ));

		// TIMELINE : hashtable
		lispHash	= jatha.makeHashTable();
		d1			= (double) context.getTimeSpan().getStart() / context.getSourceRate();
		lispHash.setf_gethash( jatha.makeString( "START" ), jatha.makeReal( d1 ));
		d1			= (double) context.getTimeSpan().getStop() / context.getSourceRate();
		lispHash.setf_gethash( jatha.makeString( "STOP" ), jatha.makeReal( d1 ));
		cilloHash.setf_gethash( jatha.makeString( KEY_TIMELINE ), lispHash );

		// PREFERENCES : hashtable
		prefs		= AbstractApplication.getApplication().getUserPrefs().node( PrefsUtil.NODE_PLUGINS );
		prefsHash.setf_gethash( jatha.makeString( "DISKBUFSIZE" ), jatha.makeInteger( DISKBUFSIZE ));
		prefsHash.setf_gethash( jatha.makeString( "INPUTCHANNELS" ),
			jatha.makeInteger( prefs.getInt( PrefsUtil.KEY_AUDIOINPUTS, 0 )));
		prefsHash.setf_gethash( jatha.makeString( "OUTPUTCHANNELS" ),
			jatha.makeInteger( prefs.getInt( PrefsUtil.KEY_AUDIOOUTPUTS, 0 )));
		prefsHash.setf_gethash( jatha.makeString( "AUDIORATE" ),
			jatha.makeInteger( prefs.getInt( PrefsUtil.KEY_AUDIORATE, 0 )));
		prefsHash.setf_gethash( jatha.makeString( "SUPERCOLLIDERAPP" ),
			jatha.makeString( prefs.get( PrefsUtil.KEY_SUPERCOLLIDERAPP, null )));
		prefsHash.setf_gethash( jatha.makeString( "CSOUNDAPP" ),
			jatha.makeString( prefs.get( PrefsUtil.KEY_CSOUNDAPP, null )));
		val = prefs.get( PrefsUtil.KEY_SUPERCOLLIDEROSC, null );
		i	= val.indexOf( ':' );
		if( i >= 0 ) {
			prefsHash.setf_gethash( jatha.makeString( "SUPERCOLLIDEROSC" ),
				jatha.makeCons( jatha.makeString( val.substring( 0, i )),
								jatha.makeInteger( Integer.parseInt( val.substring( i + 1 )))));
		}
	}

	private void sessionObjectToLispHash( SessionObject so, LispHashTable h )
	{
		MapManager			m		= so.getMap();
		Iterator			iter	= m.keySet( MapManager.Context.ALL_INCLUSIVE, MapManager.Context.NONE_EXCLUSIVE ).iterator();
		String				key;
		Object				value;
		MapManager.Context	c;
		LispValue			lispValue;
	
		h.setf_gethash( jatha.makeString( "NAME" ), jatha.makeString( so.getName() ));
		while( iter.hasNext() ) {
			key		= iter.next().toString();
			c		= m.getContext( key );
			if( c == null ) continue;
			value	= m.getValue( key );
			switch( c.type ) {
			case MapManager.Context.TYPE_INTEGER:
				lispValue	= jatha.makeInteger( ((Number) value).intValue() );
				break;
			case MapManager.Context.TYPE_LONG:
				lispValue	= jatha.makeInteger( ((Number) value).longValue() );
				break;
			case MapManager.Context.TYPE_FLOAT:
				lispValue	= jatha.makeReal( ((Number) value).floatValue() );
				break;
			case MapManager.Context.TYPE_DOUBLE:
				lispValue	= jatha.makeReal( ((Number) value).doubleValue() );
				break;
			case MapManager.Context.TYPE_BOOLEAN:
				lispValue	= ((Boolean) value).booleanValue() ? jatha.T : jatha.NIL;
				break;
			case MapManager.Context.TYPE_STRING:
				lispValue	= jatha.makeString( value.toString() );
				break;
			case MapManager.Context.TYPE_FILE:
				lispValue	= jatha.makeString( ((File) value).getPath() );
				break;
			default:
				assert false : c.type;
				lispValue	= null;
			}
			h.setf_gethash( jatha.makeString( (key.indexOf( "lisp-" ) == 0 ? key.substring( 5 ) : key).toUpperCase() ), lispValue );
		}
	}

	/**
	 *  Gets called when the lisp environment is
	 *	initialized and each time the settings view
	 *	is queried (i.e. after the context changed).
	 *	This class provides hash entries
	 *	for the common context and preferences objects
	 *	such as a list of transmitters and receivers.
	 *	Subclasses may wish to add custom symbols, hence
	 *	they shall implement this method.
	 *
	 *	@param	lisp	the lisp environment to which 
	 *					new symbols can be added
	 *
	 *	@see	AdvancedJatha#intern( String, LispValue )
	 */
	protected abstract void createSpecialSymbols( AdvancedJatha lisp, PlugInContext context );

	/*
	 *  Load a lisp source file and compiles and evaluates it.
	 *
	 *  Inits jatha if necessary. Sets the "BASEDIRECTORY" prefs hash entry.
	 *
	 *  @param  f		the file denoting the lisp source (ascii text)
	 *  @param  name	the abstract (prefs entry) name of the source
	 *
	 *	@warning	should be invoked only by loadLispSource( String )
	 *				in order to manage plug-in unregistration and reregistration!
	 */
	private void loadLispSource( File f, String name )
	throws IOException
	{
//System.err.println( "loadLispSource '"+name+"' --> "+f );

		FileReader			fr;
		LispParser			parser;
		PushbackReader		pbr;
		LispValue			codeFragment, result;
		boolean				verbose = classPrefs.getBoolean( KEY_VERBOSE, false );
		
		fr				= new FileReader( f );  // throws FileNotFoundException
		try {
			pbr			= new PushbackReader( fr, 32 );
			initJatha();	// XXX we should remove all user variables and functions
			prefsHash.setf_gethash( jatha.makeString( "BASEDIRECTORY" ),
				jatha.makeString( f.getParent() ));
//System.err.println( "initJatha() done." );
			parser		= jatha.PARSER;		// XXX ? Jatha.getParser();
			parser.setInputReader( pbr );
			parser.setCaseSensitivity( LispParser.UPCASE );
			try {
				while( true ) {
//System.err.println( "reading a line from the file..." );
					codeFragment	= parser.read();  // consecutively parse all expressions in the file
//System.err.println( "evaluating..." );
					result			= jatha.eval( codeFragment );
					if( verbose ) {
						System.out.println( result );
					}
				}
			}
			catch( EOFException e1 ) {} // okay, reached end of source code
//System.err.println( "EOF reached." );
//			catch( CompilerException e2 ) {
//				throw IOUtil.map( e2 );
//			}
		}
		finally {
			fr.close();
		}
	}

	private void loadLispSource( String name )
	throws IOException
	{
		File			f;
		Element			node			= null;
		final String	oldSourceName   = lispSourceName;
	
		try {
			// unregister old plug-in
			if( lispSourceName != null ) {
				PlugInManager.getInstance().removeValue( this, "lisp-" + lispSourceName );
				sessionPropertyAddPrimitive.setDynamic( null );
			}
			if( name != null && synthControlListFile != null ) {
				for( int i = 0; i < collSynthControls.size(); i++ ) {
					node	= (Element) collSynthControls.get( i );
					if( node.getAttribute( "name" ).equals( name )) {
						lispSourceName  = null;
						f				= new File( synthControlListFile.getParentFile(), node.getAttribute( "file" ));
						loadLispSource( f, name );
						lispSourceName	= name;
						break;
					}
				}
			} else {
				lispSourceName = null;
			}
		}
		finally {
			// register new plug-in
			if( lispSourceName != null ) {
				sessionPropertyAddPrimitive.setDynamic( "lisp-" + lispSourceName );
			}
			// update gui
			if( (lispSourceName == null && oldSourceName != null) ||
				(lispSourceName != null && oldSourceName == null) ||
				(lispSourceName != null && oldSourceName != null && !lispSourceName.equals( oldSourceName )) ) {
				
				fillLispGUI();
//				HelpGlassPane.setHelp( panelLispGUI,
//					node == null || node.getAttribute( "help" ).length() == 0 ?
//					null : node.getAttribute( "help" ));
// EEE
			}
			// register new plug-in
			if( lispSourceName != null ) {
				PlugInManager.getInstance().putValue( this, "lisp-" + lispSourceName, new Object() );
			}
		}
	}
		
	// call in the event thread
	private void loadSynthControlList( String path )
	throws IOException
	{
		int						i;
		org.w3c.dom.Document	domDoc;
		DocumentBuilderFactory  builderFactory;
		DocumentBuilder			builder;
		NodeList				nl;
		Element					node;
		
		ggLispSource.removeAllItems();
		collSynthControls.clear();
		
		try {
			synthControlListFile = new File( path );
			builderFactory  = DocumentBuilderFactory.newInstance();
//				builderFactory.setValidating( true );
//				builderFactory.setIgnoringComments( true );
//				builderFactory.setIgnoringElementContentWhitespace( true );
			builder			= builderFactory.newDocumentBuilder();
			domDoc			= builder.parse( synthControlListFile );
			nl				= domDoc.getElementsByTagName( "synthcontrol" );
			for( i = 0; i < nl.getLength(); i++ ) {
				node		= (Element) nl.item( i );
				if( node.hasAttribute( "name" ) && node.hasAttribute( "screenname" )) {
					collSynthControls.add( node );
					ggLispSource.addItem( new StringItem( node.getAttribute( "name" ),
					                                      node.getAttribute( "screenname" )));
				}
			}
		}
		catch( SAXParseException e1 ) {
			throw IOUtil.map( e1 );
		}
		catch( SAXException e2 ) {
			throw IOUtil.map( e2 );
		}
		catch( ParserConfigurationException e3 ) {
			throw IOUtil.map( e3 );
		}
	}
	
	private void fillLispGUI()
	throws IOException
	{
		if( gadgetMakePrimitive.getGadgetCount() > 0 ) {
			gadgetMakePrimitive.clear();
			panelLispGUI.removeAll();
		}
		if( lispSourceName == null || jatha == null || !contextKnown ) return;

//System.err.println( "fillLispGUI(). lispSourceLoaded = "+lispSourceLoaded+"; jatha == null ? "+(jatha==null)+"; contextKnown = "+contextKnown+"; this.hashCode() = "+this.hashCode() );
		LispValue		lispFunc, lispResult;
		boolean			verbose		= classPrefs.getBoolean( KEY_VERBOSE, false );
		int				i, numGadgets;
		Window			ancestor;
		java.util.List  collGadgets;

		lispFunc	= jatha.findFunction( "CREATE-GUI" );
//		gadgetMakePrimitive.setPreferences( Main.prefs.node( PrefsUtil.NODE_SESSION ).node(
//			((Element) collSynthControls.get( lispSourceLoaded )).getAttribute( "name" )));
		gadgetMakePrimitive.setPreferences( classPrefs.node( lispSourceName ));
//System.err.println( "lispFunc = "+lispFunc );
		if( lispFunc != null ) {
			lispResult  = jatha.eval( jatha.makeCons( lispFunc, jatha.NIL )); // (cmd . args)
			if( verbose ) System.out.println( lispResult );
		}
		numGadgets  = gadgetMakePrimitive.getGadgetCount();
		collGadgets = gadgetMakePrimitive.getGadgets();
		panelLispGUI.setLayout( gadgetMakePrimitive.getLayout() );
		for( i = 0; i < numGadgets; i++ ) {
			panelLispGUI.add( (JComponent) collGadgets.get( i ));
		}
//		if( (numGadgets & 1) == 1 ) {   // fill to even number of gadgets because of two column display
//			panelLispGUI.add( new JLabel() );
//		}
//		GUIUtil.setDeepFont( panelLispGUI, fnt );
		AbstractWindowHandler.setDeepFont( panelLispGUI );
//		GUIUtil.makeCompactSpringGrid( panelLispGUI, numGadgets >> 1, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		ancestor = (Window) SwingUtilities.getAncestorOfClass( Window.class, this );
		if( ancestor != null ) ancestor.pack();
	}

	/**
	 *	Subclasses should call this before
	 *	they start to process. This will return
	 *	false if initialization fails, which happens
	 *	for example if no source code has been loaded
	 *	or no context is available. The method will
	 *	clear all source and target requests, thus should
	 *	be called before lisp code is executed.
	 *
	 *	@param	context		context to use for processing
	 *	@return	true if preparation succeeded, false elsewise
	 */
	protected boolean plugInPrepare( PlugInContext context )
	{
		if( lispSourceName == null || jatha == null || !contextKnown ) return false;

		executePrimitive.setRenderHost( context.getHost() );
		sourceRequestPrimitive.clearRequests();
		targetRequestPrimitive.clearRequests();
		
		return true;
	}

	/**
	 *	Subclasses should call this after
	 *	their processing finished or was cancelled.
	 *	The method will delete all temporary
	 *	files created by lisp code.
	 *
	 *	@param	context		context which was used for processing
	 *
	 *	@todo	requests should also be cleared here
	 *			to catalyze garbage collection
	 */
	protected void plugInCleanUp( PlugInContext context )
	{
		executePrimitive.setRenderHost( null );
		tempFileMakePrimitive.clear();		// try to delete all files
	}
	
	/**
	 *  Tries to find a defines lisp function
	 *  given by lispFuncName. If present, it
	 *  will be evaluated and resulting OSC packets
	 *  are appended to the OSC Command file.
	 *  The given bundleTime replaces the time tag
	 *  of the produced packets.
	 *
	 *  @param  lispFuncName	name of the symbol whose
	 *							function is to be executed
	 *  @return					true = success, false = failure
	 */
	protected boolean executeLisp( String lispFuncName, LispValue args )
	throws IOException
	{
		LispValue   lispFunc, lispResult;

		lispFunc	= jatha.findFunction( lispFuncName );
		if( lispFunc != null ) {
			lispResult  = jatha.eval( jatha.makeCons( lispFunc, args )); // (cmd . args)
			if( classPrefs.getBoolean( KEY_VERBOSE, false )) {
				System.out.println( lispFuncName + " -> " + lispResult );
//				jatha.debugDump();
			}
			return( lispResult == jatha.T );
		} else {
			return true;
		}
	}
	
	protected boolean executeLisp( String lispFuncName )
	throws IOException
	{
		return executeLisp( lispFuncName, jatha.NIL );
	}

	protected String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}	
	
// ---------------- LaterInvocationManager.Listener interface ---------------- 

	// o instanceof PreferenceChangeEvent
	/**
	 *	Handles preference changes
	 */
	public void preferenceChange( PreferenceChangeEvent pce)
	{
		final String					key		= pce.getKey();
		final String					value	= pce.getNewValue();
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();

		if( EventManager.DEBUG_EVENTS ) System.err.println( "@LispPlugIn li. key = "+key+"; value = "+value );
		
		if( key.equals( getSourceListKey() )) {
			if( value != null ) {
				try {
					loadSynthControlList( value );
				}
				catch( IOException e1 ) {
					GUIUtil.displayError( this, e1, app.getResourceString( "oscLoadSynthControlList" ));
				}
				try {
					loadLispSource( classPrefs.get( KEY_LISPSOURCE, null ));
				}
				catch( IOException e2 ) {
					GUIUtil.displayError( this, e2, app.getResourceString( "errLispLoadSource" ));
				}
			}
			
		} else if( key.equals( KEY_LISPSOURCE )) {
			try {
				loadLispSource( value );
			}
			catch( IOException e1 ) {
				GUIUtil.displayError( this, e1, app.getResourceString( "errLispLoadSource" ));
			}
		}
	}

// -------- internal lisp primitive class --------

	private class LRPGadgetMakePrimitive
	extends GadgetMakePrimitive
	{
		private final ArrayList  collGadgets = new ArrayList();
	
		public LRPGadgetMakePrimitive( AdvancedJatha lisp )
		{
			super( lisp );
		}
		
		public void freeGadgets()
		{
			collGadgets.clear();
		}
		
		public GridBagConstraints getDefaultConstraints()
		{
			GridBagConstraints cons = new GridBagConstraints();
			cons.insets				= new Insets( 2, 4, 2, 4 );
			cons.anchor				= GridBagConstraints.WEST;
			cons.fill				= GridBagConstraints.HORIZONTAL;
			
			return cons;
		}
		
		public void consumeGadget( JComponent c )
		{
			collGadgets.add( c );
		}
		
		public int getGadgetCount()
		{
			return collGadgets.size();
		}

		public java.util.List getGadgets()
		{
			return new ArrayList( collGadgets );
		}
	}

	private class LRPTempFileMakePrimitive
	extends TempFileMakePrimitive
	{
		private final ArrayList collFiles		= new ArrayList();
		private final HashMap	mapNamesToFiles = new HashMap();
	
		public LRPTempFileMakePrimitive( AdvancedJatha lisp )
		{
			super( lisp );
		}
		
		public void clear()
		{
			for( int i = 0; i < collFiles.size(); i++ ) {
				((File) collFiles.get( i )).delete();
			}
			collFiles.clear();
			mapNamesToFiles.clear();
		}
		
		public void consumeFile( File f )
		throws IOException
		{
			collFiles.add( f );
			mapNamesToFiles.put( f.getAbsolutePath(), f );
		}
	}

	/*
	 *  XXX TO-DO : using jatha.eval() for the
	 *  console output callback is not elegant.
	 *  the function should be precompiled and
	 *  the opcode directly executed.
	 *  (see EvalPrimitive)
	 */
	private class LRPExecutePrimitive
	extends ExecutePrimitive
	{
		private PlugInHost  host	= null;

		public LRPExecutePrimitive( AdvancedJatha lisp )
		{
			super( lisp );
		}

		public int consumeExec( String[] cmdArray, String[] envArray, File workDir, LispValue parseFunc )
		throws IOException
		{
			Process			p;
			int				resultCode  = -1;
			boolean			pRunning	= true;
			InputStream		inStream, errStream;
			byte[]			inBuf		= new byte[128];
			int				inBufOff	= 0;
			byte[]			errBuf		= new byte[128];
			int				errBufOff	= 0;
			LispValue		oldS, oldE, oldC, oldD;

			p			= Runtime.getRuntime().exec( cmdArray, envArray, workDir );
			// XXX "Implementation note: It is a good idea for the input stream to be buffered."
			inStream	= p.getInputStream(); // new BufferedInputStream( p.getInputStream() );
			errStream	= p.getErrorStream(); // new BufferedInputStream( p.getErrorStream() );
			oldS		= f_lisp.MACHINE.S.value();		// the registers need to be saved
			oldE		= f_lisp.MACHINE.E.value();		// ...because we'll invoke jatha.eval()
			oldC		= f_lisp.MACHINE.C.value();		// ...to print out console stream, which calls
			oldD		= f_lisp.MACHINE.D.value();		// ...MACHINE.Execute() which replaces the registers
			try {
				while( (host == null || host.isRunning()) && pRunning ) {
					try {
						try {
//System.err.println( "task" );
							Thread.sleep( 500 );   // a kind of cheesy way to wait for the program to end
						}
						catch( InterruptedException e2 ) {}

						inBufOff	= handleConsole( inStream, inBuf, inBufOff, parseFunc );
						errBufOff   = handleConsole( errStream, errBuf, errBufOff, parseFunc );
//						while( inStream.available() > 0 ) {
//							i		= Math.min( inBuf.length, inStream.available() );
//							inStream.read( inBuf, 0, i );
//							System.out.write( inBuf, 0, i );
//						}
//						while( errStream.available() > 0 ) {
//							i		= Math.min( errBuf.length, errStream.available() );
//							errStream.read( errBuf, 0, i );
//							System.err.write( errBuf, 0, i );
//						}

						resultCode	= p.exitValue();
						pRunning   = false;
					}
					// gets thrown if we call exitValue() while sc still running
					catch( IllegalThreadStateException e1 ) {}
				} // while( pRunning )
			}
			finally {
				f_lisp.MACHINE.S.assign( oldS );
				f_lisp.MACHINE.E.assign( oldE );
				f_lisp.MACHINE.C.assign( oldC );
				f_lisp.MACHINE.D.assign( oldD );

				p.destroy();
			}
		
			return resultCode;
		} // consumeExec()

		public void setRenderHost( PlugInHost host )
		{
			this.host = host;
		}

		// console umlenken
		private int handleConsole( InputStream stream, byte[] buf, int bufOff, LispValue parseFunc )
		throws IOException
		{
			int				i, j;
			LispValue		parseArg;

			while( stream.available() > 0 ) {
//System.err.println( "stream.available() : "+stream.available()+"; buf = "+new String( buf, 0, bufOff ));
				i = Math.min( buf.length - bufOff, stream.available() );
				stream.read( buf, bufOff, i );
				bufOff += i;
				for( j = 0, i = j; i < bufOff; i++ ) {
					if( buf[ i ] == 0x0A || buf[ i ] == 0x0C ) {	// find newline
// System.err.println( new String( buf, j, i ));

						parseArg= f_lisp.makeString( new String( buf, j, i ));   // convert to string
						
						if( parseFunc != null ) {
							/* lispResult  =*/ f_lisp.eval( f_lisp.makeCons( parseFunc,
																	    f_lisp.makeList( parseArg ))); // (cmd . arg-list)
//try {
//							evalu( parseFunc, jatha.makeList( parseArg ));
//} catch( CompilerException e99 ) {}
						}
						j = i + 1;
						break;
					}
				}
				if( j > 0 ) {
					bufOff -= j;
					System.arraycopy( buf, j, buf, 0, bufOff );
				}

				if( bufOff == buf.length ) {	// buffer overflow
					System.out.write( buf, 0, buf.length );
					bufOff = 0;
				}
			}
			return bufOff;
		}
	} // class LRPExecutePrimitive

	/**
	 *	A custom lisp function that records source
	 *	data requests. Call:
	 *  <pre>
	 *  (source-request <var>&lt;source&gt;</var> [<var>&lt;params&gt;</var> [<var>&lt;medium&gt;</var> [<var>&lt;options&gt;</var>]]])
	 *  </pre>
	 *  the arguments are:
	 *  <ul>
	 *  <li><code>source</code> -		a type string, one of "TRAJ", "SENSE", "RESAMPLE"</li>
	 *  <li><code>params</code> -		depending on type</li>
	 *  <li><code>medium</code> -		medium to which the data will be transferred</li>
	 *  <li><code>options</code> -		additional options regarding the medium</li>
	 *  </ul>
	 *	For "TRAJ", <code>params</code> is an integer specifying the
	 *	transmitter whose index corresponds to the "TRNS" list given by the "CILLO" hashtable.
	 *	For "SENSE", <code>params</code> is a cons cell whose car is an integer specifying the
	 *	transmitter and whose cdr is the receiver index corresponding to the "TRNS" resp. "RCV"
	 *	lists given by the "CILLO" hashtable.
	 *	For "RESAMPE", <code>params</code> is a number specifying the target sampling rate.
	 *	<p>
	 *	Medium depends on the subclass of LispPlugIn. In offline mode this can be
	 *	an audio file for example.
	 */
	protected class LRPSourceRequestPrimitive
	extends BasicLispPrimitive
	{
		private final ArrayList		collRequests	= new ArrayList();
		private final AdvancedJatha	lisp;
	
		private LRPSourceRequestPrimitive( AdvancedJatha lisp )
		{
			super( lisp, "SOURCE-REQUEST", 1, 4 );		// <source> <params> <medium> <medium-options>
			
			this.lisp   = lisp;
		}

		public void Execute( SECDMachine machine )
		{
			final Request					request		= new Request();
			final LispValue					args		= machine.S.pop();
			final String					sourceName	= args.first().toStringSimple().toUpperCase();
			final LispValue					params		= args.basic_length() >= 2 ? args.second() : f_lisp.NIL;
			LispValue						trnsArg, rcvArg;
			final de.sciss.app.Application	app			= AbstractApplication.getApplication();

			try {
				request.medium			= args.basic_length() >= 3 ? lisp.getObject( args.third().toJava() ) : null;
				request.mediumOptions   = args.basic_length() >= 4 ? args.fourth() : f_lisp.NIL;
//System.err.println( "request.medium "+request.medium+"; request.mediumOptions "+request.mediumOptions );
				if( sourceName.equals( requestKeyNames[ REQUEST_SENSE ])) {
					request.type	= REQUEST_SENSE;
					if( params.basic_consp() ) {
						trnsArg = params.car();
						rcvArg  = params.cdr();
						if( trnsArg.basic_numberp() && rcvArg.basic_numberp() ) {
							request.params = new Point(
								(int) ((LispNumber) trnsArg).getLongValue(),
								(int) ((LispNumber) rcvArg).getLongValue()
							);
// System.err.println( ((Point) request.params).x +" , "+((Point) request.params).y );
						} else {
							System.err.println( app.getResourceString( "errLispWrongArgType" ) + " : "+functionName );
							return;
						}
					} else {
						System.err.println( app.getResourceString( "errLispWrongArgType" ) + " : "+functionName );
						return;
					}
				} else if( sourceName.equals( requestKeyNames[ REQUEST_TRAJ ])) {
					request.type	= REQUEST_TRAJ;
					if( params.basic_integerp() ) {
						request.params = new Integer( (int) ((LispNumber) params).getLongValue() );
					} else {
						System.err.println( app.getResourceString( "errLispWrongArgType" ) + " : "+functionName );
						return;
					}
				} else if( sourceName.equals( requestKeyNames[ REQUEST_RESAMPLE ])) {
					request.type	= REQUEST_RESAMPLE;
					if( params.basic_numberp() ) {
						request.params = new Double( ((LispNumber) params).getDoubleValue() );
					} else {
						System.err.println( app.getResourceString( "errLispWrongArgType" ) + " : "+functionName );
						return;
					}
				} else {
					System.err.println( app.getResourceString( "errLispWrongArgValue" ) + " : "+functionName );
					return;
				}
				
				collRequests.add( request );
			}
			finally {
				machine.S.push( args.last().car() );   // that's just convenient
				machine.C.pop();
			}
		}

		// Variable number of evaluated args.
		public LispValue CompileArgs( LispCompiler compiler, SECDMachine machine, LispValue args,
									  LispValue valueList, LispValue code )
			throws CompilerException
		{
			return compiler.compileArgsLeftToRight( args, valueList, f_lisp.makeCons(
													machine.LIS, f_lisp.makeCons( args.length(), code )));
		}
		
		/**
		 *	Returns a list of Request objects
		 *	describing the data requests made
		 *	after the last clearRequests() call.
		 *	Note that medium and mediumOptions
		 *	have not been checked since they
		 *	highly depend on subclasses of LispPlugIn.
		 *	medium has been looked up in the lisp's
		 *	object list, mediumOptions is the
		 *	plain lisp primitive data type.
		 *
		 *	@return	a list of Requests
		 *
		 *	@see	LispPlugIn.Request
		 */
		public java.util.List getRequests()
		{
			return new ArrayList( collRequests );
		}

		/**
		 *	Clears all requests made so fare.
		 *	This is usually called before the
		 *	initial function of the lisp code
		 *	is executed.
		 */
		public void clearRequests()
		{
			collRequests.clear();
		}
	} // class LRPSourceRequestPrimitive
	
	/**
	 *	A custom lisp function that records source
	 *	data requests. Call:
	 *  <pre>
	 *  (source-request <var>&lt;source&gt;</var> [<var>&lt;params&gt;</var> [<var>&lt;medium&gt;</var>]])
	 *  </pre>
	 *  the arguments are:
	 *  <ul>
	 *  <li><code>source</code> -	a type string, one of "TRAJ", "SENSE", "RESAMPLE", "SYNC"</li>
	 *  <li><code>params</code> -	depending on type</li>
	 *  <li><code>medium</code> -	medium to which the data will be transferred</li>
	 *  </ul>
	 *	The parameters are identical to the source-request version. There's an additional
	 *	source called "SYNC" which states that the processing should be synchronized using
	 *	the medium. In this case <code>params</code> is ignored but should be set to an integer
	 *	zero for future compatibility.
	 *
	 *	@todo		should share some methods with source request by using a common superclass;
	 *				a fourth parameter mediumOptions should be added for reasons of symmetry
	 */
	protected class LRPTargetRequestPrimitive
	extends BasicLispPrimitive
	{
		private ArrayList		collRequests	= new ArrayList();
		private AdvancedJatha   lisp;
	
		public LRPTargetRequestPrimitive( AdvancedJatha lisp )
		{
			super( lisp, "TARGET-REQUEST", 1, 3 );		// <source> <params> <target>
			
			this.lisp   = lisp;
		}

		public void Execute( SECDMachine machine )
		{
			final LispValue					args		= machine.S.pop();
			final String					sourceName	= args.first().toStringSimple().toUpperCase();
			final LispValue					params		= args.basic_length() >= 2 ? args.second() : f_lisp.NIL;
			final LispValue					target		= args.basic_length() >= 3 ? args.third() : null;
			LispValue						trnsArg, rcvArg;
			final Request					request		= new Request();
			final de.sciss.app.Application	app			= AbstractApplication.getApplication();

			try {
				if( sourceName.equals( requestKeyNames[ REQUEST_SENSE ])) {
					request.type	= REQUEST_SENSE;
					request.medium	= target != null ? new File( target.toStringSimple() ) : null;
					if( params.basic_consp() ) {
						trnsArg = params.car();
						rcvArg  = params.cdr();
						if( trnsArg.basic_numberp() && rcvArg.basic_numberp() ) {
							request.params = new Point(
								(int) ((LispNumber) trnsArg).getLongValue(),
								(int) ((LispNumber) rcvArg).getLongValue()
							);
						} else {
							System.err.println( app.getResourceString( "errLispWrongArgType" ) + " : "+functionName );
							return;
						}
					} else {
						System.err.println( app.getResourceString( "errLispWrongArgType" ) + " : "+functionName );
						return;
					}
				} else if( sourceName.equals( requestKeyNames[ REQUEST_TRAJ ])) {
					request.type	= REQUEST_TRAJ;
					request.medium	= target != null ? new File( target.toStringSimple() ) : null;
					if( params.basic_integerp() ) {
						request.params = new Integer( (int) ((LispNumber) params).getLongValue() );
					} else {
						System.err.println( app.getResourceString( "errLispWrongArgType" ) + " : "+functionName );
						return;
					}
				} else if( sourceName.equals( requestKeyNames[ REQUEST_RESAMPLE ])) {
					request.type	= REQUEST_RESAMPLE;
					if( params.basic_numberp() ) {
						request.params = new Double( ((LispNumber) params).getDoubleValue() );
					} else {
						System.err.println( app.getResourceString( "errLispWrongArgType" ) + " : "+functionName );
						return;
					}
				} else if( sourceName.equals( requestKeyNames[ REQUEST_SYNC ])) {
					request.type	= REQUEST_SYNC;
					request.medium	= target != null ? lisp.getObject( target.toJava() ) : null;
				} else {
					System.err.println( app.getResourceString( "errLispWrongArgValue" ) + " : "+functionName );
					return;
				}
				
				collRequests.add( request );
			}
			finally {
				machine.S.push( args.last().car() );   // that's just convenient
				machine.C.pop();
			}
		}

		// Variable number of evaluated args.
		public LispValue CompileArgs( LispCompiler compiler, SECDMachine machine, LispValue args,
									  LispValue valueList, LispValue code )
		throws CompilerException
		{
			return compiler.compileArgsLeftToRight( args, valueList, f_lisp.makeCons(
													machine.LIS, f_lisp.makeCons( args.length(), code )));
		}
		
		/**
		 *	Returns a list of Request objects
		 *	describing the data requests made
		 *	after the last clearRequests() call.
		 *	Note that medium and mediumOptions
		 *	have not been checked since they
		 *	highly depend on subclasses of LispPlugIn.
		 *	medium has been looked up in the lisp's
		 *	object list, mediumOptions are not yet
		 *	supported and are null!
		 *
		 *	@return	a list of Requests
		 *
		 *	@see	LispPlugIn.Request
		 */
		public java.util.List getRequests()
		{
			return new ArrayList( collRequests );
		}
		
		/**
		 *	Clears all requests made so fare.
		 *	This is usually called before the
		 *	initial function of the lisp code
		 *	is executed.
		 */
		public void clearRequests()
		{
			collRequests.clear();
		}
	} // class LRPTaretRequestPrimitive

// -------- Request internal class --------
	
	/**
	 *	Brief helper struct class
	 *	for source and target requests
	 *	made by the lisp script.
	 */
	protected class Request
	{
		/**
		 *	Request type such as REQUEST_TRAJ
		 *
		 *	@see	LispPlugIn#REQUEST_TRAJ
		 */
		public int			type;
		/**
		 *	Type specific parameters. For TRAJ it's an Integer, for
		 *	SENSE it's a Point whose x is the transmitter index
		 *	and whose y is the receiver index,
		 *	for RESAMPLE its a Double
		 */
		public Object		params;
		/**
		 *	Medium object as found in the lisp
		 *	environment's object list, or null
		 */
		public Object		medium;
		/**
		 *	Medium options as passed directly
		 *	from the lisp call, or null if
		 *	unspecified.
		 */
		public LispValue	mediumOptions;
	}
}