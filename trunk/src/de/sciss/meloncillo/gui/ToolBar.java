/*
 *  ToolBar.java
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
 *		16-Apr-04   created
 *		02-Sep-04   commented
 *      24-Dec-04   support for intruding-grow-box prefs
 *		15-Jan-05	extends JToolBar, new default group mode DESELECTION_AUTO,
 *					ActionListener moved to ToggleGroup
 *		27-Mar-05	added buttons have their focusable property set to false automatically
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;

/**
 *  Extension of <code>JPanel</code> that
 *	allows to add tool icons in a horizontal
 *	or vertical row. Listeners can be added to
 *	be informed about tool changes. Icons can
 *	be mutually exclusive grouped.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class ToolBar
extends JToolBar
implements EventManager.Processor
{
	/**
	 *	group mode : it is allowed that no
	 *	button of a group is selected
	 */
	public static final int DESELECTION_ALLOWED		= 0;

	/**
	 *	group mode : in a group there must
	 *	be always one active button
	 */
	public static final int DESELECTION_FORBIDDEN	= 1;

	/**
	 *	group mode : deselection is allowed when
	 *	the group consists only of one element,
	 *	otherwise deselection is forbidden
	 */
	public static final int DESELECTION_AUTO		= -1;

	private final HashMap   groups  = new HashMap();  // maps group numbers (Integer) to ToggleGroups

	private final EventManager elm = new EventManager( this );

	/**
	 *	Creates an empty toolbar with the given orientation
	 *
	 *	@param	orient	only <code>HORIZONTAL</code> at the moment
	 */
	public ToolBar( Main root, int orient )
	{
		super( orient );
        if( AbstractApplication.getApplication().getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
            setBorder( BorderFactory.createEmptyBorder( 0, 0, 0, 16 )); // account for intruding grow box
        }
		setFloatable( false );
    }
	
	/**
	 *	Adds a (non grouped) trigger button to the bar.
	 *	The buttons focusable property will be set to <code>false</code>.
	 *
	 *	@param	button	the button to add next to the last element
	 */
	public void addButton( AbstractButton button )
	{
		button.setFocusable( false );
		add( button );
	}

	/**
	 *	Changes the selection mode of a group.
	 *
	 *	@param	group	group ID
	 *	@param	mode	either <code>DESELECTION_ALLOWED</code>,
	 *					<code>DESELECTION_FORBIDDEN</code> or <code>DESELECTION_AUTO</code>.
	 *
	 *	@see	#DESELECTION_ALLOWED
	 *	@see	#DESELECTION_FORBIDDEN
	 */
	public void setGroupMode( int group, int mode )
	{
		Integer		key = new Integer( group );
		ToggleGroup tg  = (ToggleGroup) groups.get( key );
		
		if( tg != null ) tg.setGroupMode( mode );
	}

	/**
	 *	Adds a (grouped) toggle button to the bar.
	 *	Selection mode depends on the
	 *	group mode; the default is <code>DESELECTION_AUTO</code>.
	 *	The buttons focusable property will be set to <code>false</code>.
	 *
	 *	@param	button	the button to add next to the last element
	 *	@param	group	the logical group ID for the button. All
	 *					buttons of the group are mutual exclusively
	 *					selectable.
	 *
	 *	@see	#setGroupMode( int, int )
	 */
	public void addToggleButton( JToggleButton button, int group )
	{
		Integer		key = new Integer( group );
		ToggleGroup tg  = (ToggleGroup) groups.get( key );
		
		if( tg == null ) {
			tg = new ToggleGroup();
			groups.put( key, tg );
		}

		button.setFocusable( false );
		add( button );
		tg.addToggleButton( button );
	}
	
	/**
	 *	Adds a small spacing next to the last element to
	 *	visually separate groups of buttons.
	 */
	public void addSeparator()
	{
		addSeparator( new Dimension( 8, 8 ));
	}
	
	/**
	 *	Registers a listener for receiving
	 *	information about group selection changes.
	 *	Note that only grouped toggle buttons are traced
	 *	by the tool bar. The listener is informed if
	 *	a button of a mutual group is toggled.
	 *
	 *	@param	listener	the listener who wishes to be informed
	 */
	public void addToolActionListener( ToolActionListener listener )
	{
		elm.addListener( listener );
	}

	/**
	 *	Unregisters a listener from receiving
	 *	information about group selection changes.
	 *
	 *	@param	listener	the listener who wishes to be removed from
	 *						the event dispatcher
	 */
	public void removeToolActionListener( ToolActionListener listener )
	{
		elm.removeListener( listener );
	}
	
	/**
	 *  This is called by the EventManager
	 *  if new events are to be processed
	 */
	public void processEvent( BasicEvent e )
	{
		ToolActionListener listener;
		int i;
		
		for( i = 0; i < elm.countListeners(); i++ ) {
			listener = (ToolActionListener) elm.getListener( i );
			switch( e.getID() ) {
			case ToolActionEvent.CHANGED:
				listener.toolChanged( (ToolActionEvent) e );
				break;
			default:
				assert false : e.getID();
				break;
			}
		} // for( i = 0; i < elm.countListeners(); i++ )
	}

	// utility function to create and dispatch a ToolActionEvent
	private void dispatchChange( Object source, ToolAction action )
	{
		ToolActionEvent e = new ToolActionEvent( source, ToolActionEvent.CHANGED,
												 System.currentTimeMillis(), action );
		elm.dispatchEvent( e );
	}

	private class ToggleGroup
	implements ActionListener
	{
		private int				mode	= DESELECTION_AUTO;
		private final ArrayList	buttons = new ArrayList();

		private void addToggleButton( AbstractButton button )
		{
			button.addActionListener( this );
			buttons.add( button );
		}
		
//		private void removeToggleButton( AbstractButton button )
//		{
//			button.removeActionListener( this );
//			buttons.remove( button );
//		}
//		
		private void setGroupMode( int mode )
		{
			this.mode	= mode;
		}

		public void actionPerformed( ActionEvent e )
		{
			AbstractButton  button  = (AbstractButton) e.getSource();
			int				i;
			AbstractButton  button2;
			Action			action  = button.getAction();
			boolean			promote = true;
			
			if( button.isSelected() ) {
				for( i = 0; i < buttons.size(); i++ ) {
					button2 = (AbstractButton) buttons.get( i );
					if( button2 != button && button2.isSelected() ) {
						button2.setSelected( false );
					}
				}
			} else {
				if( (mode == DESELECTION_FORBIDDEN) ||
					((mode == DESELECTION_AUTO) && (buttons.size() > 1)) ) {
					
					button.setSelected( true );
				}
				promote = false;
			}
			
			if( promote && action != null && action instanceof ToolAction ) {
				dispatchChange( button, (ToolAction) action );
			}
		}
	}
}