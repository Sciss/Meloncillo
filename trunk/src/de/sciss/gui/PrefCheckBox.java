/*
 *  PrefCheckBox.java
 *  de.sciss.gui package
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
 *		20-May-05	created from de.sciss.meloncillo.gui.PrefCheckBox
 */

package de.sciss.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.Action;
import javax.swing.JCheckBox;

import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.EventManager;
import de.sciss.app.LaterInvocationManager;
import de.sciss.app.PreferenceEntrySync;

/**
 *  Equips a normal JCheckBox with
 *  preference storing / recalling capabilities.
 *  To preserve maximum future compatibility,
 *  we decided to not override setSelected()
 *  and the like but to install an internal
 *  ActionListener. Thus, there are two ways
 *  to alter the gadget state, either by invoking
 *  the doClick() methods (DON'T USE setSelected()
 *  because it doesn't fire events) or by
 *  changing the associated preferences.
 *  The whole mechanism would be much simpler
 *  if we reduced listening to the preference
 *  changes, but a) this wouldn't track user
 *  GUI activities, b) the PrefCheckBox can
 *  be used with preferences set to null.
 *  When a preference change occurs, the
 *  doClick() method is called, allowing
 *  clients to add ActionListeners to the
 *  gadget in case they don't want to deal
 *  with preferences. However, when possible
 *  it is recommended to use PreferenceChangeListener
 *  mechanisms.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.10, 20-May-05
 *
 *  @see		java.util.prefs.PreferenceChangeListener
 */
public class PrefCheckBox
extends JCheckBox
implements  DynamicListening, PreferenceChangeListener,
			LaterInvocationManager.Listener, PreferenceEntrySync
{
	private boolean listening				= false;
	private Preferences prefs				= null;
	private String key						= null;
	private final LaterInvocationManager lim= new LaterInvocationManager( this );
	private ActionListener listener;
	
	private boolean defaultValue;

	/**
	 *  Constructs a new <code>PrefCheckBox</code>
	 *  with no initial preferences set.
	 */
	public PrefCheckBox()
	{
		super();
		init();
	}

	/**
	 *  Constructs a new <code>PrefCheckBox</code>
	 *  with a given text label and no preferences set.
	 *
	 *  @param  text	label of the checkbox
	 */
	public PrefCheckBox( String text )
	{
		super( text );
		init();
	}

	/**
	 *  Constructs a new <code>PrefCheckBox</code>
	 *  with a given action and no initial preferences set.
	 *
	 *  @param  a   action to attach to the checkbox
	 */
	public PrefCheckBox( Action a )
	{
		super( a );
		init();
	}

	private void init()
	{
		new DynamicAncestorAdapter( this ).addTo( this );
		listener = new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				if( EventManager.DEBUG_EVENTS ) System.err.println( "@chbx actionPerformed : "+key+" --> "+isSelected() );
				updatePrefs( isSelected() );
			}
		};
		this.addActionListener( listener );
	}
	
//	public void setSelected( boolean state )
//	{
//		if( EventManager.DEBUG_EVENTS ) System.err.println( "@chbx setSelected : "+key+" --> "+state );
//		super.setSelected( state );
//		updatePrefs( state );
//	}
	
	private void updatePrefs( boolean guiState )
	{
		if( prefs != null ) {
			boolean prefsState = prefs.getBoolean( key, !guiState );
			if( EventManager.DEBUG_EVENTS ) System.err.println( "@chbx updatePrefs : "+this.key+"; old = "+prefsState+" --> "+guiState );
			if( prefsState != guiState ) {
				prefs.putBoolean( key, guiState );
			}
		}
	}

	public void setPreferences( Preferences prefs, String key )
	{   
		if( this.prefs == null ) {
			defaultValue = isSelected();
		}
		if( listening ) {
			stopListening();
			this.prefs  = prefs;
			this.key	= key;
			startListening();
		} else {
			this.prefs  = prefs;
			this.key	= key;
		}
	}

	public Preferences getPreferenceNode() { return prefs; }
	public String getPreferenceKey() { return key; }

	public void startListening()
	{
		if( prefs != null ) {
			prefs.addPreferenceChangeListener( this );
			listening	= true;
			laterInvocation( new PreferenceChangeEvent( prefs, key, prefs.get( key, null ) ));
		}
	}

	public void stopListening()
	{
		if( prefs != null ) {
			prefs.removePreferenceChangeListener( this );
			listening = false;
		}
	}
	
	// o instanceof PreferenceChangeEvent
	public void laterInvocation( Object o )
	{
		String prefsValue   = ((PreferenceChangeEvent) o).getNewValue();
		if( prefsValue == null ) {
			updatePrefs( defaultValue );
			return;
		}
		boolean prefsState;
		boolean guiState	= isSelected();

		prefsState  = new Boolean( prefsValue ).booleanValue();
		if( prefsState != guiState ) {
			// thow we filter out events when preferences effectively
			// remain unchanged, it's more clean and produces less
			// overhead to temporarily remove our ActionListener
			// so we don't produce potential loops
			this.removeActionListener( listener );
			if( EventManager.DEBUG_EVENTS ) System.err.println( "@chbx doClick" );
			doClick(); // setSelected( b );
			this.addActionListener( listener );
		}
	}
	
	public void preferenceChange( PreferenceChangeEvent e )
	{
		if( e.getKey().equals( key )) {
			if( EventManager.DEBUG_EVENTS ) System.err.println( "@chbx preferenceChange : "+key+" --> "+e.getNewValue() );
			lim.queue( e );
		}
	}
}
