/*
 *  Meloncillo.java
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
 *		06-Jun-04   prefs.createDefaults()
 *		24-Jul-04   createDefaults checks preferences version
 *		29-Jul-04   cleaned and commented
 *		03-Aug-04   added creator string
 *		14-Aug-04   minor changes
 *      24-Dec-04   added support for look-and-feel, bugfixes for win xp
 *		15-Mar-05	receiver types are StringItems now, added SectorReceiver
 *		27-Mar-05	added plugInManager;
 *		29-Apr-05	resBundle not public any more; use separate method getResourceString()
 *					which catches exceptions
 *		26-May-05	extends de.sciss.app.AbstractApplication
 */

package de.sciss.meloncillo;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import de.sciss.app.DocumentHandler;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicDocument;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.BasicWindowHandler;
import de.sciss.common.ProcessingThread;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpFrame;
import de.sciss.gui.ProgressComponent;
import de.sciss.gui.StringItem;
import de.sciss.meloncillo.gui.MainFrame;
import de.sciss.meloncillo.gui.MenuFactory;
import de.sciss.meloncillo.gui.MeterFrame;
import de.sciss.meloncillo.gui.ObserverPalette;
import de.sciss.meloncillo.gui.PrefsFrame;
import de.sciss.meloncillo.io.BlendContext;
import de.sciss.meloncillo.realtime.RealtimeFrame;
import de.sciss.meloncillo.realtime.Transport;
import de.sciss.meloncillo.realtime.TransportPalette;
import de.sciss.meloncillo.render.BounceDialog;
import de.sciss.meloncillo.render.FilterDialog;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.surface.SurfaceFrame;
import de.sciss.meloncillo.timeline.TimelineEvent;
import de.sciss.meloncillo.timeline.TimelineFrame;
import de.sciss.meloncillo.timeline.TimelineListener;
import de.sciss.meloncillo.util.MapManager;
import de.sciss.meloncillo.util.PrefsUtil;
import de.sciss.util.Flag;

/**
 *  The <code>Main</code> class contains the java VM
 *  startup static <code>main</code> method which
 *  creates a new instance of <code>Main</code>. This instance
 *  will initialize (in this order) up-to-date localized strings
 *  <code>resBundle</code>, <code>prefs</code>, global
 *  <code>undo</code> manager, the global session
 *  (<code>Session</code>) <code>doc</code>, the global
 *  <code>transport</code>, the <code>menuFactory</code>
 *  object (a prototype of the applications menu and its
 *  actions).
 *  <p>
 *  All common components are created and registered:
 *  <code>ToolPalette</code>, <code>TransportPalette</code>,
 *  <code>SurfaceFrame</code>, <code>ObserverPalette</code>,
 *  <code>Timeline Frame</code>, <code>RealtimeFrame</code>
 *  and <code>MainFrame</code>.
 *  Window settings and visibilities are restored
 *  from prefs or calculated from defaults.
 *  <p>
 *  The <code>Main</code> class is also the main instance for
 *  keeping track of the <code>BlendContext</code>, updating
 *  main window's title and querying/ registration
 *  of components. It extends the <code>Application</code>
 *  class from the <a href="http://www.roydesign.net/mrjadapter/">MRJAdapter</a>
 *  kit.
 *  <p>
 *  The <code>Main</code> class implements the
 *  <code>ProgressComponent</code> interface for easy access
 *  to the <code>MainFrame</code>. All calls are simply
 *  forwarded to the <code>MainFrame</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class Main
extends BasicApplication
implements PreferenceChangeListener, TimelineListener, ProgressComponent
{
//	/**
//	 *  Enables / disables event dispatching debugging
//	 */
//	public static final boolean DEBUG_EVENTS	= false;

//	/*
//	 *  This ResourceBundle contains all of the strings used in this application.
//	 *  These are english at the moment.
//	 */
//	private static final ResourceBundle resBundle = ResourceBundle.getBundle( "LocaleStrings", Locale.getDefault() );

	private static final String APP_NAME	= "Meloncillo";

	/*
	 *  Current version of Meloncillo. This is stored
	 *  in the preferences file.
	 *
	 *  @todo   should be saved in the session file as well
	 */
	private static final double APP_VERSION		= 0.74;

	/*
	 *  The MacOS file creator string.
	 *  (registered at apple dev con, 30-Jul-04)
	 */
	private static final String CREATOR  = "IchG";

//	private final de.sciss.app.DocumentHandler	docHandler	= new de.sciss.meloncillo.session.DocumentHandler();
//	private final de.sciss.app.WindowHandler	winHandler	= new de.sciss.meloncillo.gui.WindowHandler( this );

//	/**
//	 *  The main preferences. subclasses can add listeners etc.
//	 *
//	 *  @warning	if we ever implements a multidocument version of this program,
//	 *				the prefs cannot be static any more
//	 */
//	public static final Preferences prefs	= Preferences.userNodeForPackage( Main.class );

	/**
	 *  The plug-in manager is used to announce the activation
	 *	and deactivation of plug-ins. This is done by using <code>putValue</code>
	 *	and <code>removeValue</code> respectively. It's really more a Set than a Map,
	 *	because the keys are the names of the plug-ins (dynamic.toString()), while the values
	 *	have no meaning at the moment other than indicating that the plug-in is active
	 *	(if <code>getValue</code> returns non-null).
	 *
	 *	@see MapManager#putValue( Object, String, Object )
	 *	@see MapManager#getValue( String )
	 */
	public final MapManager plugInManager = new MapManager( this, new HashMap() );
	
	/**
	 *  Instance for getting copies of the global menu
	 */
//	public final MenuFactory menuFactory;
	/**
	 *  Global transport object
	 */
	public final Transport transport;

	/**
	 *  Key for getReceiver/Transmitter/RenderModuleTypes() : a human
	 *  readable name for displaying to the user
	 *
	 *  @see	#getReceiverTypes()
	 */
	public static final Object  KEY_HUMANREADABLENAME   = new Integer( 0 );
	/**
	 *  Key for getReceiver/Transmitter/RenderModuleTypes() : a full
	 *  qualifying class name for instantiating an object.
	 *
 	 *  @see	#getReceiverTypes()
	 */
	public static final Object  KEY_CLASSNAME			= new Integer( 1 );

	// ------------------------------------------------------------------------------------------------

	/**
	 *  Value for add/getComponent(): the main frame
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.gui.MainFrame
	 */
	public static final Object COMP_MAIN			= Main.class.getName();
	/**
	 *  Value for add/getComponent(): the surface frame
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.surface.SurfaceFrame
	 */
	public static final Object COMP_SURFACE			= SurfaceFrame.class.getName();
	/**
	 *  Value for add/getComponent(): the timeline frame
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.timeline.TimelineFrame
	 */
	public static final Object COMP_TIMELINE		= TimelineFrame.class.getName();
	/**
	 *  Value for add/getComponent(): the preferences frame
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.gui.PrefsFrame
	 */
	public static final Object COMP_PREFS			= PrefsFrame.class.getName();
	/**
	 *  Value for add/getComponent(): the observer palette
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.gui.ObserverPalette
	 */
	public static final Object COMP_OBSERVER		= ObserverPalette.class.getName();
	/**
	 *  Value for add/getComponent(): the meter display
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.gui.MeterFrame
	 */
	public static final Object COMP_METER			= MeterFrame.class.getName();
	/**
	 *  Value for add/getComponent(): the transport palette
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.realtime.TransportPalette
	 */
	public static final Object COMP_TRANSPORT		= TransportPalette.class.getName();
	/**
	 *  Value for add/getComponent(): the bounce-to-disk dialog
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.render.BounceDialog
	 */
	public static final Object COMP_BOUNCE			= BounceDialog.class.getName();
	/**
	 *  Value for add/getComponent(): the filter-trajectories dialog
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.render.FilterDialog
	 */
	public static final Object COMP_FILTER			= FilterDialog.class.getName();
	/**
	 *  Value for add/getComponent(): the realtime settings frame
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.realtime.RealtimeFrame
	 */
	public static final Object COMP_REALTIME		= RealtimeFrame.class.getName();
	/**
	 *  Value for add/getComponent(): the online help display frame
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.meloncillo.gui.HelpFrame
	 */
	public static final Object COMP_HELP    		= HelpFrame.class.getName();

	// ------------------------------------------------------------------------------------------------

//	/**
//	 *  Clipboard (global, systemwide)
//	 */
//	public static final Clipboard clipboard	= Toolkit.getDefaultToolkit().getSystemClipboard();
	
	// --- frames ---
//	private final Hashtable	collComponents		= new Hashtable();

	// --- types ---
	private static final java.util.List collReceiverTypes			= new ArrayList();
	private static final java.util.List collTransmitterTypes		= new ArrayList();
	
	private final Session	doc;

	// BlendContext : if null, it means it should be recalculated
	// when it's being used the next time. For example a preference
	// change would reset it to null.
	private			BlendContext	bc			= null;
	
	private final MainFrame mainFrame;

	private final ProcessingThread.Listener		quitAfterSaveListener;

	private static final String[] ANNA_BLUME = {
		"Ich lege mich m\u00FCde ins k\u00FChlende Grab",
		"Ferne Du",
		"Silberklang",
		"Seri\u00F6ser Herr, welcher sehr gut Klavier spielt, findet dauerndes sehr behagliches HEIM bei einz. \u00E4lterer DAME in sch\u00F6ner Wohnlage.",
		"Eins acht.",
		"Dada c'est la grande vague sacr\u00E9e qui va de Dada \u00E0 Dada.",
		"Vliegende vonken",
		"Ein kleines rotes Muttermal",
		"Da klopft in den Spalten / Ein fl\u00FCsternder Klang",
		"Gr\u00FCn soll sein Wasser sein.",
		"Siehst Du die Soldaten da am Wald?",
		"Ah, Du meine Puppe",
		"The aim is hurting day and night",
		"Still they remained limited / limited / limited.",
		"Wissen Sie, was Kunst ist? EIN REIHENSCHEISSHAUS, das ist Kunst.",
		"Machen Sie die Stra\u00DFe frei / F\u00FCr die Ordnungspolizei",
		"Steht eine Mauer aus Beton.",
		"wand",
		"nnt ker zel",
		"f\u00FCmmsb\u00F6w\u00F6t\u00E4\u00E4 / b\u00F6w\u00F6r\u00F6t\u00E4\u00E4",
		"L\u00FCmpff t\u00FCmpff trill",
		"/ tsee koo krii --/ / tsee koo k\u00FC\u00FC -- /",
		"Dark is the light / Alabaster is white",
		"RIBBLE RIBBLE RIBBLE RIBBLE RIBBLE RIBBLE RIBBLE RIBBLE  pipimlico"
	};

	/**
	 *  java VM starting method. does some
	 *  static initializations and then creates
	 *  an instance of <code>Main</code>.
	 *
	 *  @param  args	are not parsed.
	 */
	public static void main( final String[] args )
	{
		// --- run the main application ---
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		SwingUtilities.invokeLater( new Runnable() {
			public void run()
			{
				new Main( args );
			}
		});
	}

	/**
	 *  Set up the application, its
	 *  components and windows. This is invoked
	 *  by the static <code>main</code> method.
	 */
	public Main( String[] args )
	{
		super( Main.class, APP_NAME );

		final java.util.List warnings;
		final Preferences prefs	= getUserPrefs();

		Map m;
		collReceiverTypes.add( new StringItem( "de.sciss.meloncillo.receiver.SigmaReceiver", 
											   getResourceString( "SigmaReceiver" )));
		collReceiverTypes.add( new StringItem( "de.sciss.meloncillo.receiver.SectorReceiver", 
											   getResourceString( "SectorReceiver" )));
		m = new HashMap( 2 );
		m.put( KEY_CLASSNAME, "de.sciss.meloncillo.transmitter.SimpleTransmitter" );
		m.put( KEY_HUMANREADABLENAME, "Simple Transmitter" );
		collTransmitterTypes.add( m );

		// ---- init prefs ----

		final double prefsVersion = prefs.getDouble( PrefsUtil.KEY_VERSION, 0.0 );
		if( prefsVersion < APP_VERSION ) {
			warnings = PrefsUtil.createDefaults( prefs, prefsVersion );
		} else {
			warnings = null;
		}
        
        // ---- init look-and-feel
		String className = prefs.get( PrefsUtil.KEY_LOOKANDFEEL, null );
//System.err.println( "args[0] == "+args[0]+"; args[1] = "+args[1] );
		if( args.length >= 3 && args[ 0 ].equals( "-laf" )) {
			UIManager.installLookAndFeel( args[ 1 ], args[ 2 ]);
			if( className == null ) className = args[ 2 ];
		}
        lookAndFeelUpdate( className );

		// ---- init infrastructure ----
        
		doc			= new Session();
        transport	= new Transport( this, doc );
//		menuFactory	= new MenuFactory( this, doc );

        init();

		// ---- listeners ----

		doc.timeline.addTimelineListener( this );
		prefs.addPreferenceChangeListener( this );
		prefs.node( PrefsUtil.NODE_SHARED ).addPreferenceChangeListener( this );

		quitAfterSaveListener = new ProcessingThread.Listener() {
			public void processStarted( ProcessingThread.Event e ) { /* empty */ }

			// if the saving was successfull, we will call closeAll again
			public void processStopped( ProcessingThread.Event e )
			{
				if( e.isDone() ) {
					quit();
				}
			}
		};

		// ---- component views ----

		new TransportPalette( this, doc );
		new SurfaceFrame( this, doc );
		new ObserverPalette( this, doc );
		new MeterFrame( this, doc );
		new TimelineFrame( this, doc );
		new RealtimeFrame( this, doc );

		mainFrame   = new MainFrame( this, doc );

		// ----
// EEE
		
		if( prefsVersion == 0.0 ) { // means no preferences found, so display splash screen
//			layoutWindows( getStandardLayout(), true );
    		new de.sciss.meloncillo.debug.WelcomeScreen( this );
		}

		if( warnings != null ) {
			for( int i = 0; i < warnings.size(); i++ ) {
				System.err.println( warnings.get( i ));
			}
		}
		
		System.out.println( "\n" + ANNA_BLUME[ (int) ((System.currentTimeMillis() / 3600000) % 24) ] + "\n" );
	}

	/*
	 *  Creates a map whose elements
	 *  map components (such as <code>COMP_MAIN</code>)
	 *  to Maps which describe the
	 *  default layout (window sizes and
	 *  location on screen, visibility) of
	 *  that component. The keys used in
	 *  the map are those in <code>SpringDescr</code>,
	 *  e.g. <code>SpringDescr.NORTH</code>,
	 *  <code>SpringDescr.VISIBLE</code>...
	 *  Values are <code>SpringDescr</code> objects or
	 *  <code>Boolean</code> (for visibility).
	 *
	 *  @return		a map being usually parsed by <code>BasicFrame</code>.
	 *  @see		de.sciss.meloncillo.gui.SpringDescr
	 *  @see		de.sciss.meloncillo.gui.BasicFrame:layoutWindows( Main, Map, boolean )
	 */
//	private Map getStandardLayout()
//	{
//		final HashMap	compMap		= new HashMap( 10 );
//		HashMap			springMap;
//		
//		springMap = new HashMap( 6 );
//		springMap.put( SpringDescr.NORTH, new SpringDescr( SpringDescr.NORTH, null ));
//		springMap.put( SpringDescr.WEST, new SpringDescr( SpringDescr.WEST, null ));
//		springMap.put( SpringDescr.VISIBLE, Boolean.TRUE );
//		compMap.put( COMP_MAIN, springMap );
//
//		springMap = new HashMap( 6 );
//		springMap.put( SpringDescr.NORTH, new SpringDescr( SpringDescr.SOUTH, COMP_MAIN ));
//		springMap.put( SpringDescr.WEST, new SpringDescr( SpringDescr.WEST, null ));
////		springMap.put( SOUTH, new SpringDescr( SOUTH, null, -150 ));
//		springMap.put( SpringDescr.VISIBLE, Boolean.TRUE );
//		compMap.put( COMP_SURFACE, springMap );
//
//		springMap = new HashMap( 6 );
//		springMap.put( SpringDescr.NORTH, new SpringDescr( SpringDescr.NORTH, COMP_SURFACE ));
//		springMap.put( SpringDescr.WEST, new SpringDescr( SpringDescr.EAST, COMP_SURFACE ));
//		springMap.put( SpringDescr.VISIBLE, Boolean.TRUE );
//		compMap.put( COMP_OBSERVER, springMap );
//
//		springMap = new HashMap( 6 );
//		springMap.put( SpringDescr.NORTH, new SpringDescr( SpringDescr.SOUTH, COMP_OBSERVER ));
//		springMap.put( SpringDescr.WEST, new SpringDescr( SpringDescr.EAST, COMP_SURFACE ));
////		springMap.put( EAST, new SpringDescr( EAST, null, -150 ));
//		springMap.put( SpringDescr.VISIBLE, Boolean.TRUE );
//		compMap.put( COMP_TIMELINE, springMap );
//
//		springMap = new HashMap( 6 );
//		springMap.put( SpringDescr.NORTH, new SpringDescr( SpringDescr.NORTH, null ));
//		springMap.put( SpringDescr.WEST, new SpringDescr( SpringDescr.EAST, COMP_MAIN ));
//		springMap.put( SpringDescr.VISIBLE, Boolean.TRUE );
//		compMap.put( COMP_TRANSPORT, springMap );
//
//		springMap = new HashMap( 6 );
//		springMap.put( SpringDescr.NORTH, new SpringDescr( SpringDescr.NORTH, null ));
//		springMap.put( SpringDescr.WEST, new SpringDescr( SpringDescr.EAST, COMP_TRANSPORT ));
//		springMap.put( SpringDescr.VISIBLE, Boolean.FALSE );
//		compMap.put( COMP_REALTIME, springMap );
//		
//		springMap = new HashMap( 6 );
//		springMap.put( SpringDescr.SOUTH, new SpringDescr( SpringDescr.NORTH, COMP_TIMELINE ));
//		springMap.put( SpringDescr.WEST, new SpringDescr( SpringDescr.WEST, COMP_TRANSPORT ));
//		springMap.put( SpringDescr.VISIBLE, Boolean.FALSE );
//		compMap.put( COMP_METER, springMap );
//
//		return compMap;
//	}

//	/**
//	 *  Retrieves a specific frame of the application
//	 *  
//	 *  @param  componentID e.g. <code>COMP_SURFACE</code>
//	 *  @return				the requested component or <code>null</code> if absent or unknown
//	 */
//	public Object getComponent( Object componentID )
//	{
//		return( collComponents.get( componentID ));
//	}

//	/**
//	 *  Adds a newly created component (a specific frame) to the application
//	 *  
//	 *  @param	componentID		e.g. <code>COMP_SURFACE</code>
//	 *  @param  component		the GUI component identified by the <code>componentID</code>
//	 *							or null.
//	 */
//	public void addComponent( Object componentID, Object component )
//	{
//		if( component != null ) {
//			collComponents.put( componentID, component );
//		} else {
//			collComponents.remove( componentID );
//		}
//	}

	/**
	 *  Returns a <code>List</code> of all known
	 *  <code>Receiver</code> classes.
	 *  the list elements are of type <code>StringItem</code> whose
	 *	key is the class name and value is the human readable string.
	 *  <p>
	 *  Objects instantiated are of class <code>Receiver</code>.
	 *
	 *  @return		a list with the elements being
	 *				StringItems as described above
	 *  @see		de.sciss.meloncillo.receiver.Receiver
	 *  @see		de.sciss.meloncillo.util.StringItem
	 */
	public static java.util.List getReceiverTypes()
	{
		return collReceiverTypes;
	}

	/**
	 *  Returns a <code>List</code> of all known
	 *  <code>Transmitter</code> classes.
	 *  the list elements are of type <code>Map</code> and
	 *  contain at least the two keys
	 *  <p><ul>
	 *  <li><code>KEY_CLASSNAME</code> - (to use for getting the class and instantiation)</li>
	 *  <li><code>KEY_HUMANREADABLENAME</code> - (to use for GUI presentation to the user)</li>
	 *  </ul>
	 *  <p>
	 *  Objects instantiated are of class <code>Receiver</code>.
	 *
	 *  @return		a list with the elements being
	 *				maps as described above
	 *  @see		de.sciss.meloncillo.transmitter.Transmitter
	 *
	 *	@todo		should return StringItems
	 */
	public static java.util.List getTransmitterTypes()
	{
		return collTransmitterTypes;
	}

	private boolean forcedQuit = false;

	public synchronized void quit()
	{
		final Flag				confirmed	= new Flag( false );
		final ProcessingThread	pt			= getMenuFactory().closeAll( forcedQuit, confirmed );

		if( pt != null ) {
			pt.addListener( quitAfterSaveListener );
			((BasicDocument) pt.getClientArg( "doc" )).start( pt );
		} else if( confirmed.isSet() ) {
//			OSCRoot.getInstance().quit();
//			SuperColliderClient.getInstance().quit();
			super.quit();
		}
	}

//	public synchronized void quit()
//	{
//		if( !forcedQuit && !getMenuFactory().confirmUnsaved( null, getResourceString( "menuQuit" ))) return;
//
//		try {
//			transport.quit();
//			// clear session prefs
//			PrefsUtil.removeAll( getUserPrefs().node( PrefsUtil.NODE_SESSION ), true );
//			// XXX disconnect OSC ...
//		}
//		catch( BackingStoreException e1 ) {
//			GUIUtil.displayError( null, e1,
//				getResourceString( "errSavePrefs" ));
//		}
//		finally {
//			super.quit();
//		}
//	}
	
	public void forceQuit()
	{
		forcedQuit = true;
		this.quit();
	}

	/**
	 *  @return				a <code>BlendContext</code> object.
	 *						If the blending was changed, in which case
	 *						the context is internally to <code>null</code>,
	 *						calling this method will recalc the context.
	 *
	 *  @synchronization	waitShared on DOOR_TIME
	 */
	public BlendContext getBlending()
	{
		// create a copy of the reference because bc might be set to null in the meantime
  		BlendContext myBC = bc;
		
		if( myBC != null ) return myBC;

		int					blendLen;
		final Preferences	prefs	= getUserPrefs();

		if( prefs.node( PrefsUtil.NODE_SHARED ).getBoolean( PrefsUtil.KEY_BLENDING, false )) {
			try {
				doc.bird.waitShared( Session.DOOR_TIME );
				blendLen = (int) (doc.timeline.getRate() *
					Math.max( 0.0, prefs.node( PrefsUtil.NODE_SHARED ).getDouble(
										PrefsUtil.KEY_BLENDTIME, 0.0 )) + 0.5);
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIME );
			}
		} else {
			blendLen = 0;
		}
		myBC	= new BlendContext( blendLen );
		bc		= myBC;
		
		// return a copy of the reference because bc might be set to null in the meantime
  		return( myBC );
	}
    
	private void lookAndFeelUpdate( String className )
	{
		if( className != null ) {
			try {
				UIManager.setLookAndFeel( className );
//				BasicFrame.lookAndFeelUpdate();	// EEE
			}
			catch( Exception e1 ) {
				if( mainFrame != null ) {
					GUIUtil.displayError( mainFrame.getWindow(), e1, null );
				} else {
					System.err.println( e1.getLocalizedMessage() );
				}
			}
		}
    }
	
 // ------------ Application interface ------------
	
	public String getMacOSCreator()
	{
		return CREATOR;
	}

	public double getVersion()
	{
		return APP_VERSION;
	}
	
//	public de.sciss.app.WindowHandler getWindowHandler()
//	{
//		return winHandler;
//	}

	public BasicWindowHandler createWindowHandler()
	{
		return new de.sciss.meloncillo.gui.WindowHandler( this );
	}

//	public de.sciss.app.DocumentHandler getDocumentHandler()
//	{
//		return docHandler;
//	}
	
	public DocumentHandler createDocumentHandler()
	{
		return new de.sciss.meloncillo.session.DocumentHandler();
	}
	
	public BasicMenuFactory createMenuFactory()
	{
		return new MenuFactory( this, doc );
	}
   
// ---------------- ProgressComponent interface ---------------- 

	public Component getComponent()
	{
		return mainFrame.getComponent();
	}
	
	public void resetProgression()
	{
		mainFrame.resetProgression();
	}
	
	public void setProgression( float p )
	{
		mainFrame.setProgression( p );
	}
	
	public void	finishProgression( int type)
	{
		mainFrame.finishProgression( type );
	}
	
	public void setProgressionText( String text )
	{
		mainFrame.setProgressionText( text );
	}
	
	public void showMessage( int type, String text )
	{
		mainFrame.showMessage( type, text );
	}
	
	public void displayError( Exception e, String processName )
	{
		mainFrame.displayError( e, processName );
	}
	
	public void addCancelListener( ActionListener al )
	{
		mainFrame.addCancelListener( al );
	}

	public void removeCancelListener( ActionListener al )
	{
		mainFrame.removeCancelListener( al );
	}

// ---------------- TimelineListener interface ---------------- 

	public void timelineChanged( TimelineEvent e )
	{
		bc = null;
	}

	public void timelineSelected( TimelineEvent e ) {}
	public void timelinePositioned( TimelineEvent e ) {}
    public void timelineScrolled( TimelineEvent e ) {}

// ---------------- PreferenceChangeListener interface ---------------- 
	
	public void preferenceChange( PreferenceChangeEvent e )
	{
		final String	key = e.getKey();

		if( key.equals( PrefsUtil.KEY_BLENDING ) || key.equals( PrefsUtil.KEY_BLENDTIME )) {
			bc = null;
		} else if( key.equals( PrefsUtil.KEY_LOOKANDFEEL )) {
            lookAndFeelUpdate( e.getNewValue() );
        }
 	}
	
// --------------------------------------------------------------------
	
//	private static final String[] edges = { SpringLayout.NORTH, SpringLayout.SOUTH,
//		SpringLayout.WEST, SpringLayout.EAST };
//	private LayoutContainer 			springContainer		= null;
//	private SpringLayout				springLayout		= null;
//	private Map							mapLayoutComponents	= null;
//
//	/*
//	 *  This method is invoked by the <code>Main</code>
//	 *  class upon application launch. It is declared
//	 *  <code>public</code> for the mere reason that
//	 *  <code>Main</code> belongs to a different package,
//	 *  and should be considered private otherwise.
//	 *  <p>
//	 *  This method lays out all frames according
//	 *  to a description map and using an internal
//	 *  <code>SpringLayout</code>.
//	 */
//	private void layoutWindows( Map mapSpringMaps, boolean usePrefs )
//	{
//		int							i, j;
//		LayoutComponent				springComp;
//		AppWindow					bf;
//		Point						loc, loc2;
//		Dimension					size;
//		SpringDescr					sd;
//		Map							springMap;
//		Boolean						visible;
//		Component					refComp;
//		SpringLayout.Constraints	cons;
//		Spring						maxWidthSpring  = Spring.constant( 0 );
//		Spring						maxHeightSpring = Spring.constant( 0 );
//		
//		springContainer		= new LayoutContainer();
//		springLayout		= new SpringLayout();
//		mapLayoutComponents	= new HashMap();
//		
////System.err.println( "--- "+springContainer.getClass().getName()+" ---" );
////GUIUtil.printSizes( springContainer );
//
//		for( i = 0; i < springContainer.getComponentCount(); i++ ) {
//			springComp	= (LayoutComponent) springContainer.getComponent( i );
//			cons		= springLayout.getConstraints( springComp );
//			bf			= springComp.getRealOne();
//			loc			= null; // PrefsUtil.stringToPoint( bf.classPrefs.get( PrefsUtil.KEY_LOCATION, null ));
//			size		= null; // PrefsUtil.stringToDimension( bf.classPrefs.get( PrefsUtil.KEY_SIZE, null ));
//
//			// if the caller asks us to use preferences and
//			// the prefs contains values for either size or
//			// location, apply those values, otherwise rely
//			// on the spring layout descriptions
//			if( usePrefs && (loc != null || size != null) ) {
//				if( loc != null ) {
//					cons.setConstraint( SpringLayout.WEST, Spring.constant( loc.x - springContainer.getX() ));
//					cons.setConstraint( SpringLayout.NORTH, Spring.constant( loc.y - springContainer.getY() ));
//					bf.setLocation( loc );
//				}
//				if( size != null ) {
//					if( loc == null ) loc = bf.getLocation();
//					cons.setConstraint( SpringLayout.EAST, Spring.constant( loc.x + size.width - springContainer.getX() ));
//					cons.setConstraint( SpringLayout.SOUTH, Spring.constant( loc.y + size.height - springContainer.getY() ));
//					bf.setSize( size );
//				}
//				springComp.setBounds( bf.getBounds() );
//				springComp.setVisible( false ); // bf.classPrefs.getBoolean( PrefsUtil.KEY_VISIBLE, false ));
//			} else {
//				springMap   = (Map) mapSpringMaps.get( bf.getClass().getName() );
//				springComp.setBounds( bf.getBounds() );
//				if( springMap != null ) {
//					for( j = 0; j < edges.length; j++ ) {
//						sd	= (SpringDescr) springMap.get( edges[j] );
//						if( sd != null ) {
//							refComp = sd.ref == null ? springContainer : getLayoutComponent( (AppWindow) getComponent( sd.ref ));
//							if( refComp != null ) {
//								springLayout.putConstraint( edges[j], springComp, sd.pad, sd.refEdge, refComp );
//							}
//						}
//					}
//					visible = (Boolean) springMap.get( SpringDescr.VISIBLE );
//					if( visible != null ) {
//						springComp.setVisible( visible.booleanValue() );
//					}
//				}
//			}
//			maxWidthSpring  = Spring.max( maxWidthSpring, cons.getConstraint( SpringLayout.EAST ));
//			maxHeightSpring = Spring.max( maxHeightSpring, cons.getConstraint( SpringLayout.SOUTH));
//		}
//		cons = springLayout.getConstraints( springContainer );
//		cons.setConstraint( SpringLayout.EAST,  Spring.sum( maxWidthSpring, Spring.constant( 150 )));
//		cons.setConstraint( SpringLayout.SOUTH, Spring.sum( maxHeightSpring, Spring.constant( 150 )));
////		cons.setConstraint( SpringLayout.EAST,  Spring.constant( springContainer.getWidth() ));
////		cons.setConstraint( SpringLayout.SOUTH, Spring.constant( springContainer.getHeight() ));
//
//		// the whole verification of the layout is very tricky
//		// after these two calls, the virtual component's bounds
//		// can be investigated and transferred to the real windows
//		springLayout.invalidateLayout( springContainer );
//		springLayout.layoutContainer( springContainer );
//
//		loc2 = springContainer.getLocation();
//		for( i = 0; i < springContainer.getComponentCount(); i++ ) {
//			springComp	= (LayoutComponent) springContainer.getComponent( i );
//			bf			= springComp.getRealOne();
//			loc			= null; // PrefsUtil.stringToPoint( bf.classPrefs.get( PrefsUtil.KEY_LOCATION, null ));
//			size		= null; // PrefsUtil.stringToDimension( bf.classPrefs.get( PrefsUtil.KEY_SIZE, null ));
//			if( usePrefs && (loc != null || size != null) ) {
//				if( loc != null ) {
//					bf.setLocation( loc );
//				}
//				if( size != null ) {
//					bf.setSize( size );
//				}
//			} else {
//				loc		= springComp.getLocation();
//				loc.translate( loc2.x, loc2.y );
//				bf.setLocation( loc );
//				bf.setSize( springComp.getSize() );
//			}
//			bf.setVisible( springComp.isVisible() );
//		}
//		
//		springContainer 	= null;
//		springLayout		= null;
//		mapLayoutComponents	= null;
//	}
//
//	private Component getLayoutComponent( AppWindow w )
//	{
//		LayoutComponent cmp = (LayoutComponent ) mapLayoutComponents.get( w );
//		if( cmp == null ) {
//			cmp = new LayoutComponent( w);
//			springContainer.add( cmp );
//			mapLayoutComponents.put( w, cmp );
//		}
//		return cmp;
//	}
//	// --------------- internal classes ---------------
//
//	/*
//	 *  A doppelgaenger class for representing
//	 *  a frame in the virtual container, passing
//	 *  minimum, maximum and preferred size directly
//	 *  from the real component.
//	 */  
//	private static class LayoutComponent
//	extends Component
//	{
//		private AppWindow realOne;
//
//		private LayoutComponent( AppWindow realOne )
//		{
//			this.realOne = realOne;
//		}
//		
//		private AppWindow getRealOne()
//		{
//			return realOne;
//		}
//		
//		public Dimension getMinimumSize()		{ return realOne.getWindow().getMinimumSize(); }
//		public Dimension getMaximumSize()		{ return realOne.getWindow().getMaximumSize(); }
//		public Dimension getPreferredSize()		{ return realOne.getWindow().getPreferredSize(); }
//	}
//	
//	/*
//	 *  A container as a simulation
//	 *  of the screen space with the
//	 *  BasicFrames represented as LayoutComponent
//	 *  objects.
//	 */
//	private static class LayoutContainer
//	extends Container
//	{
//		private Rectangle bounds;
//		
//		private LayoutContainer()
//		{
//			super();
//			bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
//			setBounds( bounds );
//		}
//		
//		public Dimension getMaximumSize()
//		{
//			return bounds.getSize();
//		}
//
//		public Dimension getMinimumSize()
//		{
//			return getMaximumSize();
//		}
//
//		public Dimension getPreferredSize()
//		{
//			return getMaximumSize();
//		}
//	}
}