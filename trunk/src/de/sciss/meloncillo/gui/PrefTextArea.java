/*
 *  PrefTextArea.java
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
 *		27-Jul-04   created
 *		31-Jul-04   commented
 *		14-Aug-04   added defaultValue
 *		01-Jan-05	bugfix: removePreferenceListener() was not called
 */

package de.sciss.meloncillo.gui;

import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;

import de.sciss.app.*;

/**
 *  Equips a normal JTextArea with
 *  preference storing / recalling capabilities.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class PrefTextArea
extends JTextArea
implements  DynamicListening, PreferenceChangeListener,
			LaterInvocationManager.Listener, PreferenceEntrySync
{
	private boolean listening				= false;
	private Preferences prefs				= null;
	private String key						= null;
	private final LaterInvocationManager lim= new LaterInvocationManager( this );
	private DocumentListener listener;
	
	private String defaultValue				= null;

	/**
	 *  Creates a new empty <code>PrefTextArea</code>
	 *  with plain text document model and no preferences
	 *  initially set
	 */
	public PrefTextArea()
	{
		super();
		init();
	}

	/**
	 *  Creates a new <code>PrefTextArea</code>
	 *  with plain text document model and no preferences
	 *  initially set
	 *
	 *  @param  text	the initial gadget's content
	 */
	public PrefTextArea( String text )
	{
		super( text );
		init();
	}

	/**
	 *  Creates a new empty <code>PrefTextArea</code>
	 *  with plain text document model and no preferences
	 *  initially set.
	 *
	 *  @param  rows	number of rows for the area
	 *  @param  columns number of columns for the area
	 */
	public PrefTextArea( int rows, int columns )
	{
		super( rows, columns );
		init();
	}

	/**
	 *  Creates a new <code>PrefTextArea</code>
	 *  with plain text document model and no preferences
	 *  initially set.
	 *
	 *  @param  text	the initial gadget's content
	 *  @param  rows	number of rows for the area
	 *  @param  columns number of columns for the area
	 */
	public PrefTextArea( String text, int rows, int columns )
	{
		super( text, rows, columns );
		init();
	}

	private void init()
	{
		new DynamicAncestorAdapter( this ).addTo( this );
		listener = new DocumentListener() {

			public void insertUpdate( DocumentEvent e )  { schoko(); }
			public void removeUpdate( DocumentEvent e )  { schoko(); }
			public void changedUpdate( DocumentEvent e ) { schoko(); }

			private void schoko()
			{
				if( EventManager.DEBUG_EVENTS ) System.err.println( "@area doc change : "+key+" --> "+getText() );
				updatePrefs( getText() );
			}
		};
		this.getDocument().addDocumentListener( listener );
	}
	
	private void updatePrefs( String guiValue )
	{
		if( prefs != null ) {
			String prefsValue = prefs.get( key, null );
			if( EventManager.DEBUG_EVENTS ) System.err.println( "@area updatePrefs : "+this.key+"; old = "+prefsValue+" --> "+guiValue );
			if( (prefsValue == null && guiValue != null) ||
				(prefsValue != null && guiValue == null) ||
				(prefsValue != null && guiValue != null && !prefsValue.equals( guiValue ))) {

				prefs.put( key, guiValue );
			}
		}
	}

	public void setPreferences( Preferences prefs, String key )
	{
		if( this.prefs == null ) {
			defaultValue = getText();
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
			laterInvocation( new PreferenceChangeEvent( prefs, key, prefs.get( key, null )));
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
			if( defaultValue != null ) updatePrefs( defaultValue );
			return;
		}
		String guiValue		= getText();

		if( guiValue == null || (guiValue != null && !prefsValue.equals( guiValue ))) {

			// though we filter out events when preferences effectively
			// remain unchanged, it's more clean and produces less
			// overhead to temporarily remove our ActionListener
			// so we don't produce potential loops
			this.getDocument().removeDocumentListener( listener );
			if( EventManager.DEBUG_EVENTS ) System.err.println( "@area setText" );
			setText( prefsValue );
//			fireActionPerformed();
			this.getDocument().addDocumentListener( listener );
		}
	}
	
	public void preferenceChange( PreferenceChangeEvent e )
	{
		if( e.getKey().equals( key )) {
			if( EventManager.DEBUG_EVENTS ) System.err.println( "@area preferenceChange : "+key+" --> "+e.getNewValue() );
			lim.queue( e );
		}
	}
}
