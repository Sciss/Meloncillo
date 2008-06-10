/*
 *  BlendingAction.java
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
 *		25-Mar-05	extracted from SurfaceToolBar
 */

package de.sciss.meloncillo.gui;

import java.awt.event.*;
import java.util.prefs.*;
import javax.swing.*;

import de.sciss.meloncillo.util.*;

import de.sciss.app.*;

/**
 *	A class implementing the <code>Action</code> interface
 *	which deals with the blending setting. Each instance
 *	generates a toggle button suitable for attaching to a tool bar;
 *	this button reflects the blending preferences settings and
 *	when alt+pressed will prompt the user to alter the blending settings.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class BlendingAction
extends AbstractAction
implements LaterInvocationManager.Listener
{
	private final JToggleButton b;
	private final Preferences	prefs;

	/**
	 *	Creates a new instance of an action
	 *	that tracks blending changes
	 */
	public BlendingAction( Preferences prefs )
	{
		super();
		this.prefs	= prefs;
		b			= new JToggleButton( this );
		GraphicsUtil.setToolIcons( b, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_BLENDING ));
		new DynamicAncestorAdapter( new DynamicPrefChangeManager( prefs,
			new String[] { PrefsUtil.KEY_BLENDING }, this )).addTo( b );
	}
	
	/**
	 *	Returns the toggle button
	 *	which is connected to this action.
	 *
	 *	@return	a toggle button which is suitable for tool bar display
	 */
	public JToggleButton getButton()
	{
		return b;
	}

	private void updateButtonState()
	{
		b.setSelected( prefs.getBoolean( PrefsUtil.KEY_BLENDING, false ));
	}
	
	public void actionPerformed( ActionEvent e )
	{
		if( (e.getModifiers() & ActionEvent.ALT_MASK) != 0 ) {
			String	result;
			double	length		= 0.0;

			result  = JOptionPane.showInputDialog( b,
						AbstractApplication.getApplication().getResourceString( "inputDlgSetBlendSpan" ),
						   String.valueOf( prefs.getDouble( PrefsUtil.KEY_BLENDTIME, 0.0 )));	// XXX localized number?
			if( result != null ) {
				try {
					length = Double.parseDouble( result );
				}
				catch( NumberFormatException e1 ) {
					System.err.println( e1.getLocalizedMessage() );
				}
			}
			if( length <= 0.0 ) {
				prefs.putBoolean( PrefsUtil.KEY_BLENDING, false );
				return;
			} else {
				prefs.putBoolean( PrefsUtil.KEY_BLENDING, true );
				prefs.putDouble( PrefsUtil.KEY_BLENDTIME, length );
			}
			updateButtonState();	// prefsChangeEvent not guaranteed!!
		} else {
			prefs.putBoolean( PrefsUtil.KEY_BLENDING, b.isSelected() );
		}
	}

	// o instanceof PreferenceChangeEvent
	public void laterInvocation( Object o )
	{
		updateButtonState();
	}
}
