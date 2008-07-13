/*
 *  PrefsUtil.java
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
 *		06-Jun-04 : createDefaults() replaces static defaults initializer.
 *		24-Jul-04 : createDefaults checks preferences version. Many items
 *					have been renamed and/or moved to the plugin node.
 *		08-Aug-04 : new keys
 *      01-Jan-05 : new keys for look-and-feel + help keystroke
 *		23-Apr-05 : minor improvements in createDefaults()
 *					(on MacOS looks for creator codes of SC and MacCSound,
 *					on Windows looks in common places)
 */
 
package de.sciss.meloncillo.util;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.io.IOUtil;
import de.sciss.meloncillo.session.Session;

import net.roydesign.mac.MRJAdapter;

/**
 *	A helper class for programme preferences. It
 *	contains the globally known preferences keys,
 *	adds support for default preferences generation
 *	and some geometric object (de)serialization.
 *	Has utility methods for clearing preferences trees
 *	and importing and exporting from/to XML.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 19-Jun-08
 */
public class PrefsUtil
{
	/**
	 *	used for restoring prefs from XML : the
	 *	value is a <code>java.util.Set</code>
	 *	which contains all keys that should not be restored (i.e. omitted).
	 *	This is used to remove window bounds from the shared
	 *	node prefs when the <code>KEY_RECALLFRAMES</code> prefs
	 *	is set to <code>false.</code>
	 *
	 *	@see	#KEY_RECALLFRAMES
	 #	@see	java.util.Set
	 *	@see	#fromXML( Preferences, Document, Element, Map )
	 */
	public static final String	OPTIONS_KEY_FILTER	= "filter";

	// ------------ root node level ------------

	/**
	 *  Value: Double representing the application
	 *  version of Meloncillo last time prefs was saved.<br>
	 *  Has default value: no!<br>
	 *  Node: root
	 */
	public static final String KEY_VERSION	= "version";	// double : app version

	/**
	 *  Value: String representing a list of paths
	 *  of the recently used session files. See
	 *  PathList and MenuFactory.actionOpenRecentClass.<br>
	 *  Has default value: no!<br>
	 *  Node: root
	 */
	public static final String KEY_OPENRECENT= "recent";	// string: path list
	/**
	 *  Value: Boolean stating whether frame bounds
	 *  should be recalled a session file when it's
	 *  loaded. Has default value: yes!<br>
	 *  Node: root
	 */
	public static final String KEY_RECALLFRAMES = "recallframes";	// boolean : recall frame bounds from session
	/**
	 *  Value: String representing the look-and-feel class
	 *  for swing. Has default value: no!<br>
	 *  Node: root
	 */
	public static final String KEY_LOOKANDFEEL = "lookandfeel";	
	/**
	 *  Value: Boolean stating whether frame size (grow) boxes
     *  intrude into the frame's pane. Has default value: yes!<br>
	 *  Node: root
	 */
	public static final String KEY_INTRUDINGSIZE = "intrudingsize";
	
	/**
	 *  Value: Integer indicating the dislayed time format.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_TIMEUNITS	= "timeunits";		// integer (TIME_SAMPLES, TIME_MINSECS)
	/**
	 *  Value: Integer indicating the dislayed amplitude scaling.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_VERTSCALE	= "vertscale";		// integer (VSCALE_AMP_LIN etc.)

	/**
	 *  Value: Boolean indicating whether a vertical
	 *  (amplitude range) ruler should be display for each channel or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_VIEWVERTICALRULERS	= "viewverticalrulers";		// boolean
	/**
	 *  Value: Boolean indicating whether a marker
	 *	axis and flagsticks should be painted in the overviews or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_VIEWMARKERS	= "viewmarkers";		// boolean
	/**
	 *  Value: Boolean indicating whether timeline position is
	 *  adjusted after transport stop or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_INSERTIONFOLLOWSPLAY	= "insertionfollowsplay";		// boolean

	public static final int TIME_SAMPLES		= 0;
	public static final int TIME_MINSECS		= 1;

	public static final int VSCALE_AMP_LIN		= 0;
	public static final int VSCALE_AMP_LOG		= 1;
	public static final int VSCALE_FREQ_SPECT	= 2;
	
	// ------------ plugin node level ------------

	/**
	 *  Child node of global prefs
	 */
	public static final String NODE_PLUGINS	= "plugins";

	/**
	 *  Value: String representing the pathname
	 *  of the lisp offline synthcontrol list file.<br>
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_LISPBOUNCELIST	= "lispbouncelist";	// string : pathname

	/**
	 *  Value: String representing the pathname
	 *  of the lisp traj filtering synthcontrol list file.<br>
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_LISPFILTERLIST	= "lispfilterlist";	// string : pathname

	/**
	 *  Value: String representing the pathname
	 *  of the lisp realtime synthcontrol list file.<br>
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_LISPREALTIMELIST	= "lisprealtimelist";	// string : pathname

	/**
	 *  Value: String representing the osc port
	 *  of the supercollider application.<br>
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_SUPERCOLLIDEROSC	= "supercolliderosc";	// string : "ip:port"

	/**
	 *  Value: String representing the pathname
	 *  of the supercollider application.<br>
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_SUPERCOLLIDERAPP	= "supercolliderapp";	// string : pathname
	/**
	 *  Value: String representing the pathname
	 *  of the csound application.<br>
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_CSOUNDAPP	= "csoundapp";		// string : pathname

	/**
	 *  Value: Integer representing the number
	 *  of input channels supplied by the audio hardware
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_AUDIOINPUTS = "audioinputs";		// integer : number of audio interface input ch
	/**
	 *  Value: Integer representing the number
	 *  of output channels supplied by the audio hardware
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_AUDIOOUTPUTS = "audiooutputs";	// integer : number of audio interface output ch
	/**
	 *  Value: Integer representing the sampling
	 *  rate of the audio hardware
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_AUDIORATE = "audiorate";			// integer : samplingrate of audio hardware

	/**
	 *  Value: Integer representing the size of
	 *  the realtime sense buffer in millisecs.
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_RTSENSEBUFSIZE = "rtsensebufsize";		// integer : sense buf size
	/**
	 *  Value: Integer representing the maximum rate
	 *  of realtime sense buffer in Hertz.
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_RTMAXSENSERATE = "rtmaxsenserate";	// integer : maximum sense rate

	/**
	 *  Value: Integer representing the size of
	 *  the offline sense buffer in millisecs.
	 *  Has default value: yes!<br>
	 *  Node: plugins
	 */
	public static final String KEY_OLSENSEBUFSIZE = "olsensebufsize";		// integer : sense buf size

	// ------------ session node level ------------

	/**
	 *  Preferences that are clearly session related are
	 *  stored in a child node with this name. The values
	 *  stored in this child node will be written to the
	 *  session file but not the user preferences file.
	 */
	public static final String NODE_SESSION		= "session";

	/**
	 *  Value: General use string for adding a
	 *  comment to a session.<br>
	 *  Has default value: no!<br>
	 *  Node: session
	 */
	public static final String KEY_COMMENT		= "comment";	// string: session comment

	// ------------ shared node level ------------

	/**
	 *  Preferences that are session relevant but whose
	 *  changes shall propagate through multiple sessions
	 *  are stored in a child node with this name. The values
	 *  stored in this child node will be written to the
	 *  session file and also to the user preferences file.
	 *  When a session gets loaded, the values are restored
	 *  from the session copy of the node. So when the user
	 *  clears the session or restarts the application,
	 *  those values are still available.
	 */
	public static final String NODE_SHARED		= "shared";

	/**
	 *  Value: String representing a Point object
	 *  describing a windows location. Use stringToPoint.<br>
	 *  Has default value: no!<br>
	 *  Node: multiple occurences in shared -> (Frame-class)
	 */
	public static final String KEY_LOCATION		= "location";   // point
	/**
	 *  Value: String representing a Dimension object
	 *  describing a windows size. Use stringToDimension.<br>
	 *  Has default value: no!<br>
	 *  Node: multiple occurences in shared -> (Frame-class)
	 */
	public static final String KEY_SIZE			= "size";		// dimension
	/**
	 *  Value: Boolean stating wheter a window is
	 *  shown or hidden.<br>
	 *  Has default value: no!<br>
	 *  Node: multiple occurences in shared -> (Frame-class)
	 */
	public static final String KEY_VISIBLE		= "visible";	// boolean
	/**
	 *  Value: Boolean indicating whether blending
	 *  is active or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: shared
	 */
	public static final String KEY_BLENDING		= "blending";   // boolean : blending on/off
	/**
	 *  Value: Boolean indicating whether timeline position catch
	 *  is active or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: shared
	 */
	public static final String KEY_CATCH		= "catch";		// boolean : catch on/off
	/**
	 *  Value: Double indicating the time span
	 *  of blending in seconds.<br>
	 *  Has default value: yes!<br>
	 *  Node: shared
	 */
	public static final String KEY_BLENDTIME	= "blendtime";  // double: blending time in seconds
	/**
	 *  Value: Boolean indicating whether object
	 *  snapping is active or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: shared
	 */
	public static final String KEY_SNAP			= "snap";		// boolean: snap mouse to objects

	/**
	 *  Value: Boolean indicating whether the
	 *  surface should display receiver sense background image.<br>
	 *  Has default value: yes!<br>
	 *  Node: shared
	 */
	public static final String KEY_VIEWRCVSENSE	= "viewrcvsense";

	/**
	 *  Value: Boolean indicating whether the
	 *  surface should display receiver sense in equal power mode.<br>
	 *  Has default value: yes!<br>
	 *  Node: shared
	 */
	public static final String KEY_VIEWEQPRECEIVER = "vieweqprcv";

	/**
	 *  Value: Boolean indicating whether the
	 *  surface should display the selected transmitters' trajectories.<br>
	 *  Has default value: yes!<br>
	 *  Node: shared
	 */
	public static final String KEY_VIEWTRNSTRAJ = "viewtrnstraj";

	/**
	 *  Value: Boolean indicating whether the
	 *  surface should display the selected transmitters' trajectories.<br>
	 *  Has default value: yes!<br>
	 *  Node: shared
	 */
	public static final String KEY_VIEWUSERIMAGES = "viewuserimages";

	/**
	 *  Value: Boolean indicating whether the
	 *  surface should display rulers.<br>
	 *  Has default value: yes!<br>
	 *  Node: shared
	 */
	public static final String KEY_VIEWRULERS = "viewrulers";

	// from java.util.prefs.AbstractPreferences doc
//	private static final String PREFS_DTD_URI   = "http://java.sun.com/dtd/preferences.dtd";
//	private static final String PREFS_DTD		= 
//		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
//		"<!ELEMENT preferences (root)>"+
//		"<!ATTLIST preferences EXTERNAL_XML_VERSION CDATA \"0.0\" >  "+
//		"<!ELEMENT root (map, node*) >"+
//		"<!ATTLIST root"+
//		"          type (system|user) #REQUIRED >"+
//		"<!ELEMENT node (map, node*) >"+
//		"<!ATTLIST node"+
//		"          name CDATA #REQUIRED >"+
//		"<!ELEMENT map (entry*) >"+
//		"<!ELEMENT entry EMPTY >"+
//		"<!ATTLIST entry"+
//		"          key   CDATA #REQUIRED"+
//		"          value CDATA #REQUIRED >";
			  
	/**
	 *	Creates default preferences.
	 *	It doesn't overwrite existing preferences.
	 *	It tries to intelligently determine some settings,
	 *	for example path names. finally writes
	 *	the current prefs version to KEY_VERSION.
	 *
	 *	@param	mainPrefs	the preferences root node
	 *	@param	lastVersion	which was the last saved version (or 0.0 if no previously saved prefs exist)
	 *	@return	a list of warning or error messages to be printed in the console. this cannot be done
	 *			in this method because the main log pane is not yet created.
	 */
	public static List createDefaults( Preferences mainPrefs, double lastVersion )
	{
		File				f;	
		String				value;
		Preferences			childPrefs;
        final boolean		isMacOS		= System.getProperty( "os.name" ).indexOf( "Mac OS" ) >= 0;
        final boolean		isWindows	= System.getProperty( "os.name" ).indexOf( "Windows" ) >= 0;
		final String		fs			= File.separator;
		final List			warnings	= new ArrayList();
		final Application	app			= AbstractApplication.getApplication();

		putDontOverwrite( IOUtil.getUserPrefs(), IOUtil.KEY_TEMPDIR, System.getProperty( "java.io.tmpdir" ));

		// general
		putBooleanDontOverwrite( mainPrefs, KEY_RECALLFRAMES, true );
		putDontOverwrite( mainPrefs, KEY_LOOKANDFEEL, UIManager.getSystemLookAndFeelClassName() );
		putBooleanDontOverwrite( mainPrefs, KEY_INTRUDINGSIZE, isMacOS );
		putBooleanDontOverwrite( mainPrefs, KEY_INSERTIONFOLLOWSPLAY, true );
//		putBooleanDontOverwrite( mainPrefs, KEY_VIEWCHANMETERS , true );
		putBooleanDontOverwrite( mainPrefs, KEY_VIEWMARKERS , true );
//		putBooleanDontOverwrite( mainPrefs, KEY_VIEWNULLLINIE , true );
		putBooleanDontOverwrite( mainPrefs, KEY_VIEWVERTICALRULERS, true );
		putBooleanDontOverwrite( mainPrefs, KEY_CATCH, true );
		
		putIntDontOverwrite( mainPrefs, KEY_TIMEUNITS, TIME_MINSECS );
		putIntDontOverwrite( mainPrefs, KEY_VERTSCALE, VSCALE_AMP_LIN );

		putBooleanDontOverwrite( mainPrefs, KEY_BLENDING, false );
		putDoubleDontOverwrite( mainPrefs, KEY_BLENDTIME, 0.250 );
		putBooleanDontOverwrite( mainPrefs, KEY_CATCH, true );

		childPrefs  = mainPrefs.node( NODE_SHARED );
		putBooleanDontOverwrite( childPrefs, KEY_SNAP, true );
		putBooleanDontOverwrite( childPrefs, KEY_VIEWRCVSENSE, true );
		putBooleanDontOverwrite( childPrefs, KEY_VIEWEQPRECEIVER, true );
		putBooleanDontOverwrite( childPrefs, KEY_VIEWTRNSTRAJ, true );
		putBooleanDontOverwrite( childPrefs, KEY_VIEWUSERIMAGES, false );
		putBooleanDontOverwrite( childPrefs, KEY_VIEWRULERS, true );

		// plug-ins
		f			= new File( new File( new File( "" ).getAbsoluteFile(), "plug-ins" ), "lisp" );
		childPrefs  = mainPrefs.node( NODE_PLUGINS );
		putIntDontOverwrite( childPrefs, KEY_AUDIOINPUTS, 8 );
		putIntDontOverwrite( childPrefs, KEY_AUDIOOUTPUTS, 8 );
		putIntDontOverwrite( childPrefs, KEY_AUDIORATE, 44100 );
		putIntDontOverwrite( childPrefs, KEY_RTSENSEBUFSIZE, 512 );
		putIntDontOverwrite( childPrefs, KEY_RTMAXSENSERATE, 690 );
		putIntDontOverwrite( childPrefs, KEY_OLSENSEBUFSIZE, 512 );
		putDontOverwrite( childPrefs, KEY_LISPBOUNCELIST, new File( f, "bouncelist.xml" ).getPath() );
		putDontOverwrite( childPrefs, KEY_LISPFILTERLIST, new File( f, "filterlist.xml" ).getPath() );
		putDontOverwrite( childPrefs, KEY_LISPREALTIMELIST, new File( f, "realtimelist.xml" ).getPath() );
	
		if( childPrefs.get( KEY_SUPERCOLLIDEROSC, null ) == null ) {
//			try {
//				value   = InetAddress.getLocalHost().getHostName() + ":57110";
//			}
//			catch( IOException e1 ) {
//				warnings.add( e1.toString() );
				value   = "127.0.0.1:57110";
//			}
			putDontOverwrite( childPrefs, KEY_SUPERCOLLIDEROSC, value );
		}
		
		// sc app
		if( childPrefs.get( KEY_SUPERCOLLIDERAPP, null ) == null ) {
			f	= findFile( isWindows ? "scsynth.exe" : "scsynth", new String[] {
				fs + "Applications" + fs + "SuperCollider_f",
				fs + "Applications" + fs + "SuperCollider",
				fs + "usr" + fs + "local" + fs + "bin",
				fs + "usr" + fs + "bin",
				"C:\\Program Files\\SuperCollider_f",
				"C:\\Program Files\\PsyCollider"
			});
			if( f == null ) {
				if( isMacOS ) {
					try {
						f = MRJAdapter.findApplication( "SCjm" );
						if( f != null ) f = new File( f.getParentFile(), "scsynth" );
					}
					catch( IOException e1 ) { /* ignore */ }
				}
				if( f == null ) {
					warnings.add( AbstractApplication.getApplication().getResourceString( "errSCSynthAppNotFound" ));
				}
			}
			if( f != null ) putDontOverwrite( childPrefs, KEY_SUPERCOLLIDERAPP, f.getAbsolutePath() );
		}

		// csound app
		if( childPrefs.get( KEY_CSOUNDAPP, null ) == null ) {
			f	= findFile( isWindows ? "consound.exe" : "csound", new String[] {
				fs + "usr" + fs + "local" + fs + "bin",
				fs + "usr" + fs + "bin",
				"C:\\Program Files\\CSound"
			});
			if( f == null ) {
				if( isMacOS ) {
					try {
						f = MRJAdapter.findApplication( "ma++" );	// MacCSound is bundles with command line csound
						f = f.getParentFile();
						if( f != null ) {
							f = new File( new File( f, "Commandline Version" ), "csound" );
							if( !f.exists() ) f = null;
						}
					}
					catch( IOException e1 ) {}
				}
				if( f == null ) {
					warnings.add( app.getResourceString( "errCSoundAppNotFound" ));
				}
			}
			if( f != null ) putDontOverwrite( childPrefs, KEY_CSOUNDAPP, f.getAbsolutePath() );
		}
		
		// save current version
		mainPrefs.putDouble( KEY_VERSION, AbstractApplication.getApplication().getVersion() );
		
		return warnings;
	}

	// --- custom put/get methods ---

	private static boolean putDontOverwrite( Preferences prefs, String key, String value )
	{
		boolean overwrite = prefs.get( key, null ) == null;
		
		if( overwrite ) {
			prefs.put( key, value );
		}
		
		return overwrite;
	}
	
	private static boolean putIntDontOverwrite( Preferences prefs, String key, int value )
	{
		boolean overwrite = prefs.get( key, null ) == null;
		
		if( overwrite ) {
			prefs.putInt( key, value );
		}
		
		return overwrite;
	}
	
	private static boolean putBooleanDontOverwrite( Preferences prefs, String key, boolean value )
	{
		boolean overwrite = prefs.get( key, null ) == null;
		
		if( overwrite ) {
			prefs.putBoolean( key, value );
		}
		
		return overwrite;
	}

	private static boolean putDoubleDontOverwrite( Preferences prefs, String key, double value )
	{
		boolean overwrite = prefs.get( key, null ) == null;
		
		if( overwrite ) {
			prefs.putDouble( key, value );
		}
		
		return overwrite;
	}
	
	public static Rectangle stringToRectangle( String value )
	{
		Rectangle		rect	= null;
		StringTokenizer tok;
		
		if( value != null ) {
			try {
				tok		= new StringTokenizer( value );
				rect	= new Rectangle( Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ),
										 Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ));
			}
			catch( NoSuchElementException e1 ) {}
			catch( NumberFormatException e2 ) {}
		}
		return rect;
	}

	public static Point stringToPoint( String value )
	{
		Point			pt	= null;
		StringTokenizer tok;
		
		if( value != null ) {
			try {
				tok		= new StringTokenizer( value );
				pt		= new Point( Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ));
			}
			catch( NoSuchElementException e1 ) {}
			catch( NumberFormatException e2 ) {}
		}
		return pt;
	}

	public static Dimension stringToDimension( String value )
	{
		Dimension		dim	= null;
		StringTokenizer tok;
		
		if( value != null ) {
			try {
				tok		= new StringTokenizer( value );
				dim		= new Dimension( Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ));
			}
			catch( NoSuchElementException e1 ) {}
			catch( NumberFormatException e2 ) {}
		}
		return dim;
	}
	
	/**
	 *  Rectangle, z.B. von Frame.getBounds() in
	 *  einen String konvertieren, der als Prefs
	 *  gespeichert werden kann. Bei Fehler wird
	 *  null zurueckgeliefert. 'value' darf null sein.
	 */
	public static String rectangleToString( Rectangle value )
	{
		return( value != null ? (value.x + " " + value.y + " " + value.width + " " + value.height) : null );
	}
	
	public static String pointToString( Point value )
	{
		return( value != null ? (value.x + " " + value.y) : null );
	}
	
	public static String dimensionToString( Dimension value )
	{
		return( value != null ? (value.width + " " + value.height) : null );
	}
	
//	public static String classToNodeName( Class c )
//	{
//		return( "/" + c.getName().replace( '.', '/' ));
//	}

	/**
	 *  Converts a a key stroke's string representation as
	 *	from preference storage into a KeyStroke object.
	 *
	 *  @param		prefsValue		a string representation of the form &quot;modifiers keyCode&quot;
	 *								or <code>null</code>
	 *	@return		the KeyStroke parsed from the prefsValue or null if the string was
	 *				invalid or <code>null</code>
	 */
	public static final KeyStroke prefsToStroke( String prefsValue )
	{
		if( prefsValue == null ) return null;
		int i = prefsValue.indexOf( ' ' );
		KeyStroke prefsStroke = null;
		try {
			if( i < 0 ) return null;
			prefsStroke = KeyStroke.getKeyStroke( Integer.parseInt( prefsValue.substring( i+1 )),
												  Integer.parseInt( prefsValue.substring( 0, i )));
		}
		catch( NumberFormatException e1 ) {}
		return prefsStroke;
	}
	
	/**
	 *  Converts a KeyStroke into a string representation for
	 *	preference storage.
	 *
	 *  @param		prefsStroke	the KeyStroke to convert
	 *	@return		a string representation of the form &quot;modifiers keyCode&quot;
	 *				or <code>null</code> if the prefsStroke is invalid or <code>null</code>
	 */
	public static final String strokeToPrefs( KeyStroke prefsStroke )
	{
		if( prefsStroke == null ) return null;
		else return String.valueOf( prefsStroke.getModifiers() ) + ' ' +
					String.valueOf( prefsStroke.getKeyCode() );
	}

	/**
	 *  Traverse a preference node and optionally all
	 *  children nodes and remove any keys found.
	 */
	public static void removeAll( Preferences prefs, boolean deep )
	throws BackingStoreException
	{
		String[]	keys;
		String[]	children;
		int			i;

		keys = prefs.keys();
		for( i = 0; i < keys.length; i++ ) {
			prefs.remove( keys[i] );
		}
		if( deep ) {
			children	= prefs.childrenNames();
			for( i = 0; i < children.length; i++ ) {
				removeAll( prefs.node( children[i] ), deep );
			}
		}
	}
	
	private static File findFile( String fileName, String[] folders )
	{
		File f;
	
		for( int i = 0; i < folders.length; i++ ) {
			f = new File( folders[ i ], fileName );
			if( f.exists() ) return f;
		}
		return null;
	}

	/**
	 *  Similar to the XMLRepresentation interface,
	 *  this method will append an XML representation
	 *  of some preferences to an existing node.
	 *	The <code>OPTIONS_KEY_FILTER</code> option is
	 *	recognized and can be used to omit entries.
	 *
	 *  @param  prefs   the preferences node to write out.
	 *  @param  deep	- true to include a subtree with all
	 *					child preferences nodes.
	 *  @param  domDoc  the document in which the node will reside.
	 *  @param  node	the node to which a child is applied.
	 *
	 *	@see	#OPTIONS_KEY_FILTER
	 */
	public static void toXML( Preferences prefs, boolean deep, org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		String[]			keys;
		String[]			children;
		Element				childElement, entry;
		String				value;
		final java.util.Set	filterSet	= (java.util.Set) options.get( OPTIONS_KEY_FILTER );

//System.err.println( "node = "+prefs.name() );
		try {
			keys			= prefs.keys();
			childElement	= (Element) node.appendChild( domDoc.createElement( "map" ));
			for( int i = 0; i < keys.length; i++ ) {
				value   = prefs.get( keys[ i ], null );
//System.err.println( "  key = "+keys[i]+"; value = "+value );
				if( (value == null) || ((filterSet != null) && filterSet.contains( keys[ i ]))) continue;
				entry = (Element) childElement.appendChild( domDoc.createElement( "entry" ));
				entry.setAttribute( "key", keys[ i ]);
				entry.setAttribute( "value", value );
			}
			if( deep ) {
				children	= prefs.childrenNames();
				for( int i = 0; i < children.length; i++ ) {
					childElement = (Element) node.appendChild( domDoc.createElement( "node" ));
					childElement.setAttribute( "name", children[i] );
					toXML( prefs.node( children[i] ), deep, domDoc, childElement, options );
				}
			}
		} catch( DOMException e1 ) {
			throw IOUtil.map( e1 );
		} catch( BackingStoreException e2 ) {
			throw IOUtil.map( e2 );
		}
	}

	/**
	 *  Similar to the XMLRepresentation interface,
	 *  this method will parse an XML representation
	 *  of some preferences and restore it's values.
	 *	The <code>OPTIONS_KEY_FILTER</code> option is
	 *	recognized and can be used to omit entries.
	 *
	 *  @param  prefs		the preferences node to import to.
	 *  @param  domDoc		the document in which the node resides.
	 *  @param  rootElement	the node whose children to parse.
	 *
	 *	@see	#OPTIONS_KEY_FILTER
	 */
	public static void fromXML( Preferences prefs, org.w3c.dom.Document domDoc, Element rootElement, Map options )
	throws IOException
	{
		NodeList			nl, nl2;
		Element				childElement, entry;
		Node				node;
		String				key;
		int					nodeIdx;
		final java.util.Set	filterSet	= (java.util.Set) options.get( OPTIONS_KEY_FILTER );

		try {
			nl	= rootElement.getChildNodes();
			for( nodeIdx = 0; nodeIdx < nl.getLength(); nodeIdx++ ) {
				node			= nl.item( nodeIdx );
				if( !(node instanceof Element) ) continue;
				childElement	= (Element) node;
				nl2				= childElement.getElementsByTagName( "entry" );
				for( int j = 0; j < nl2.getLength(); j++ ) {
					entry		= (Element) nl2.item( j );
					key			= entry.getAttribute( "key" );
					if( (filterSet != null) && filterSet.contains( key )) continue;
					prefs.put( key, entry.getAttribute( "value" ));
//System.err.println( "auto : node = "+(prefs.name() )+"; key = "+entry.getAttribute( "key" )+"; val = "+entry.getAttribute( "value" ) );
				}
				break;
			}
			for( ; nodeIdx < nl.getLength(); nodeIdx++ ) {
				node			= nl.item( nodeIdx );
				if( !(node instanceof Element) ) continue;
				childElement	= (Element) node;
				fromXML( prefs.node( childElement.getAttribute( "name" )), domDoc, childElement, options );
			}
		} catch( DOMException e1 ) {
			throw IOUtil.map( e1 );
		}
	}
	
	/**
	 *  Get an Action object that will dump the
	 *  structure of the MultiTrackEditors of
	 *  all selected transmitters
	 */
	public static Action getDebugDumpAction( Session doc )
	{
		AbstractAction a = new AbstractAction( "Dump preferences tree" ) {

			public void actionPerformed( ActionEvent e )
			{
				debugDump( AbstractApplication.getApplication().getUserPrefs() );
			}
			
			private void debugDump( Preferences prefs )
			{
				System.err.println( "------- debugDump prefs : "+prefs.name()+" -------" );
				String[]	keys;
				String[]	children;
				String		value;
				int			i;
				
				try {
					keys		= prefs.keys();
					for( i = 0; i < keys.length; i++ ) {
						value   = prefs.get( keys[i], null );
						System.err.println( "  key = '"+keys[i]+"' ; value = '"+value+"'" );
					}
					children	= prefs.childrenNames();
					for( i = 0; i < children.length; i++ ) {
						debugDump( prefs.node( children[i] ));
					}
				} catch( BackingStoreException e1 ) {
					System.err.println( e1.getLocalizedMessage() );
				}
			}
		};
		return a;
	}
}