/*
 *  AbstractPlugInFrame.java
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
 *		24-Jul-04   created
 *		31-Jul-04   DynamicAncestorAdapter replaces DynamicComponentAdapter
 *		01-Sep-04	commented. slight clean up
 *      24-Dec-04   extends BasicPalette
 */

package de.sciss.meloncillo.plugin;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.prefs.*;

import javax.swing.*;
import javax.swing.border.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.math.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.common.AppWindow;
import de.sciss.gui.*;

/**
 *  A basic superclass for all plug-in related
 *	GUI frames. Provides a common look for all
 *	different plug-ins and offers a few flags
 *	for standard GUI elements. Creates a
 *	class preferences suitable for saving plug-in
 *	related information.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo		settings menu actions not yet implemented
 */
public abstract class AbstractPlugInFrame
extends AppWindow
implements PreferenceChangeListener
{
	/**
	 *  Application reference
	 */
	protected final Main				root;
	/**
	 *  Session document reference
	 */
	protected final Session				doc;
	/**
	 *	The central panel of the plug-in window.
	 *	Subclasses should set the pane's view
	 *	to display their GUI elements.
	 */
	protected JScrollPane				ggSettingsPane;
	/**
	 *	Default to be used for the GUI elements.
	 */
	protected static final Font			fnt		= GraphicsUtil.smallGUIFont;
	/**
	 *	The class preference are located as a separate
	 *	subnode of the shared prefs, i.e. all keys in here
	 *	are stored both in the session files and the
	 *	program preferences. The resampling and selection-only
	 *	gadgets (KEY_RESAMPLING and KEY_SELECTIONONLY) will
	 *	store their values here. Subclasses can add their
	 *	specific prefs.
	 */
	protected final Preferences			classPrefs;

//	private final JComponent			glassPane;
//	private final FocusTraversalPolicy	focusAlive;
//	private final FocusTraversalPolicy	focusFrozen;

	private JComponent			ggSettingsMenu;
	private JPanel				topPanel;
	private JComponent			bottomPanel;
	private PrefComboBox		ggPlugIn, ggResampling;
	private PrefCheckBox		ggSelectionOnly;
	
	/*
	 *  Value: String representing the class name
	 *  of the current render producer.<br>
	 */
	private static final String KEY_PLUGIN  = "plugin";
	/**
	 *	Key of the resampling gadget in the class prefs.<p>
	 *	Value: String representing the class name
	 *  of the current resampling algorithm.<br>
	 */
	protected static final String KEY_RESAMPLING  = "resampling";
	/**
	 *	Key of the selection-only gadget in the class prefs.<p>
	 *  Value: Boolean representing the state of
	 *  the selection-only gadget.<br>
	 */
	protected static final String KEY_SELECTIONONLY  = "selectiononly";

	/*
	 *	Array of items which will form the choices
	 *	of the resampling gadget. 
	 */
	private final StringItem[] rsmpItems;
		
	/**
	 *  Constructor Flag : create gadget for resampling algorithm
	 */
	protected static final int GADGET_RESAMPLING	=   0x01;
	/**
	 *  Constructor Flag : create gadget for "selection only"
	 */
	protected static final int GADGET_SELECTION		=   0x02;

	/**
	 *  Constructs a new plug in window
	 *	and creates some gadget elements depending
	 *	on the flag settings.
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 *	@param	title	frame title string
	 *	@param	flags	OR'ed gui element creation flags such as
	 *					GADGET_RESAMPLING or GADGET_SELECTION
	 */
	protected AbstractPlugInFrame( Main root, Session doc, String title, int flags )
	{
		super( SUPPORT );
		setTitle( title );
		
		this.root   = root;
		this.doc	= doc;
		
		final de.sciss.app.Application	app	= AbstractApplication.getApplication();
		
		rsmpItems	= new StringItem[] {
			new StringItem( NearestNeighbour.class.getName(),
							app.getResourceString( "pluginResamplingNearest" )),
			new StringItem( LinearInterpolation.class.getName(),
							app.getResourceString( "pluginResamplingLinear" )),
			new StringItem( BandLimitedResampling.class.getName(),
							app.getResourceString( "pluginResamplingBandlimited" ))
		};
		
		String className	= getClass().getName();
		classPrefs			= app.getUserPrefs().node( PrefsUtil.NODE_SHARED ).node(
								className.substring( className.lastIndexOf( '.' ) + 1 ));
								
		// --- GUI creation ---
		
		Container cp = getContentPane();
		cp.setLayout( new BorderLayout() );
		
		createSettingsMenu();
		createGadgets( flags );
		
//		glassPane   = new HibernationGlassPane( ggSettingsPane, getContentPane() );
//		setGlassPane( glassPane );
//		focusAlive  = getFocusTraversalPolicy();
//		focusFrozen = new NoFocusTraversalPolicy();
		// EEE

		JPanel toptopPanel = new JPanel( new BorderLayout() );
		toptopPanel.add( topPanel, BorderLayout.CENTER );
		toptopPanel.add( ggSettingsMenu, BorderLayout.NORTH );

		cp.add( toptopPanel, BorderLayout.NORTH );
		cp.add( ggSettingsPane, BorderLayout.CENTER );
		if( bottomPanel != null ) cp.add( bottomPanel, BorderLayout.SOUTH );
		
		AbstractWindowHandler.setDeepFont( cp );
		
		// --- Listener ---
//		addListener( new AbstractWindow.Adapter() {
//			public void windowClosing( AbstractWindow.Event e )
//			{
//				dispose();
//			}
//		});
//		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE ); // window listener see above!

// SUBCLASSES NEED TO DO THIS
//		init();
	}
	
	public void init()
	{
        addDynamicListening( new DynamicPrefChangeManager( classPrefs,
                                               			new String[] { KEY_PLUGIN, KEY_SELECTIONONLY }, this ));
        super.init();
	}

	/**
	 *	Subclasses must provide a list of
	 *	plug-ins. They get listed in a selection combo box
	 *	on the GUI so the user can switch between different
	 *	plug-ins. When a plug-in is switched, <code>switchPlugIn</code>
	 *	is called.
	 *
	 *	@return	a list of plug-ins; elements must implement the Map
	 *			interface. For each element, the Map should contain
	 *			at least fields for KEY_CLASSNAME and KEY_HUMANREADABLENAME
	 *
	 *	@see	#switchPlugIn( String )
	 *	@see	de.sciss.meloncillo.Main#KEY_CLASSNAME
	 *	@see	de.sciss.meloncillo.Main#KEY_HUMANREADABLENAME
	 */
	protected abstract java.util.List getProducerTypes();

	/**
	 *	Subclasses must react to a plug-in change.
	 *	Usually they'll instantiate the new plug-in
	 *	and want to update the GUI
	 *	or perform initializations.
	 *
	 *	@param	className	the plug-in to use.
	 *
	 *	@see	java.lang.Class#newInstance()
	 */
	protected abstract void switchPlugIn( String className );

	protected boolean autoUpdatePrefs()
	{
		return true;
	}
	
	/**
	 *	Requests the display to "freeze",
	 *	i.e. paint in a ghosted mode and not
	 *	react to user input. This is useful if
	 *	they user mustn't modify parameters during
	 *	processing.
	 *
	 *	@param	freeze	<code>true</code> to set the ghosting
	 *					glass pane, <code>false</code> to revive
	 *					the normal display.
	 */
	protected void hibernation( boolean freeze )
	{
//		glassPane.setVisible( freeze );
//		setFocusTraversalPolicy( freeze ? focusFrozen : focusAlive );
//		if( freeze ) glassPane.requestFocus();
// EEE
	}

	private void settingsStoreSession()
	{
		// XXX
//		((XMLRepresentation) prod.toXML();
	}

	private void settingsRecallSession()
	{
		// XXX
//		((XMLRepresentation) prod.fromXML();
//		PrefsUtil.fromXML( Main.prefs.node( PrefsUtil.NODE_SESSION ), domDoc, (Element) nl.item( 0 ));
	}

	private void createSettingsMenu()
	{
		JMenuItem						mi;
		Action							a;
		JPopupMenu						popSettings;
		final de.sciss.app.Application	app = AbstractApplication.getApplication();

		popSettings		= new JPopupMenu();
		a				= new MenuAction( app.getResourceString( "menuSettingsStoreSession" )) {
			public void actionPerformed( ActionEvent e )
			{
				settingsStoreSession();
			}		
		};
		mi				= new JMenuItem( a );
		popSettings.add( mi );
		a				= new MenuAction( app.getResourceString( "menuSettingsRecallSession" )) {
			public void actionPerformed( ActionEvent e )
			{
				settingsRecallSession();
			}		
		};
		mi				= new JMenuItem( a );
		popSettings.add( mi );

		ggSettingsMenu	= new JLabel( app.getResourceString( "menuSettings" ));
		ggSettingsMenu.setBorder( new CompoundBorder( new EtchedBorder(), new EmptyBorder( 0, 16, 1, 16 )));
// XXX
//		ggSettingsMenu.addMouseListener( new MenuPopupListener( popSettings ));
	}

	/**
	 *	Subclasses provide a panel shown at the frame's
	 *	bottom. This can contain activation or render and
	 *	cancel gadgets.
	 *
	 *	@param	flags	flags as passed directly from the constructor
	 *	@return			a component which will be attached to frame's bottom.
	 *					<code>null</code> is allowed if no bottom panel
	 *					should be displayed.
	 */
	protected abstract JComponent createBottomPanel( int flags );

	/**
	 *	Gets called when the abstract frame thinks the
	 *	plug in context might have changed. This happens for
	 *	example when the user toggles the selection-only gadget.
	 *	Subclasses may take actions to adjust their GUI or
	 *	ignore the call if they don't think they are affected.
	 */
	protected abstract void checkReContext();

	private void createGadgets( int flags )
	{
		final java.util.List			coll	= getProducerTypes();
		int								rows;
		Map								map;
		LayoutManager					lay;
		JLabel							lb;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
		
		bottomPanel = createBottomPanel( flags );
		
		topPanel	= new JPanel();
		lay			= new SpringLayout();
		topPanel.setLayout( lay );
		rows		= 0;

		// plug-in gadget
		ggPlugIn	= new PrefComboBox();
		for( int i = 0; i < coll.size(); i++ ) {
			map = (Map) coll.get(i);
			ggPlugIn.addItem( new StringItem( map.get( Main.KEY_CLASSNAME ).toString(),
												map.get( Main.KEY_HUMANREADABLENAME )));
		}   
		ggPlugIn.setPreferences( classPrefs, KEY_PLUGIN );
		lb  = new JLabel( app.getResourceString( "plugin" ));
		topPanel.add( lb );
		topPanel.add( ggPlugIn );
		rows++;

		// resampling gadget
		if( (flags & GADGET_RESAMPLING) != 0 ) {
			ggResampling	= new PrefComboBox();
			for( int i = 0; i < rsmpItems.length; i++ ) {
				ggResampling.addItem( rsmpItems[i] );
			}
			ggResampling.setSelectedIndex( 1 );
			ggResampling.setPreferences( classPrefs, KEY_RESAMPLING );
			lb = new JLabel( app.getResourceString( "pluginResampling" ));
			topPanel.add( lb );
			topPanel.add( ggResampling );
			rows++;
		}

		// selection-only gadget
		if( (flags & GADGET_SELECTION) != 0 ) {
			ggSelectionOnly	= new PrefCheckBox();
			ggSelectionOnly.setSelected( true );
			ggSelectionOnly.setPreferences( classPrefs, KEY_SELECTIONONLY );
			lb = new JLabel( app.getResourceString( "pluginSelectionOnly" ));
			topPanel.add( lb );
			topPanel.add( ggSelectionOnly );
			rows++;
		}
		
		GUIUtil.makeCompactSpringGrid( topPanel, rows, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		
		ggSettingsPane = new JScrollPane( JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
										  JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
	}

// ---------------- LaterInvocationManager.Listener interface ---------------- 

	// o instanceof PreferenceChangeEvent
	/**
	 *	Handles preference changes
	 */
	public void preferenceChange( PreferenceChangeEvent pce)
	{
		String  key		= pce.getKey();
		String  value	= pce.getNewValue();

		if( key.equals( KEY_PLUGIN )) {
			switchPlugIn( value );
		} else if( key.equals( KEY_SELECTIONONLY )) {
			checkReContext();
		}
	}
} // class AbstractPlugInFrame
