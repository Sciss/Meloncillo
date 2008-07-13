/*
 *  TransportPalette.java
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
 *  Change log:
 *		06-Jun-04	Shortcut for Play/Stop now Ctrl+Space, Shortcut for Loop is Ctrl+Shift+L
 *		13-Jun-04	Timeline Position field
 *		22-Jul-04	moved to realtime package.
 *		29-Jul-04	seems the StackOverflow bug was caused by the updateTimeLabel()
 *					method. Replaced by custom class TimeLabel which doesn't need
 *					to construct objects all the time. TimeLabel flashes red when dropout occur.
 *		31-Jul-04   DynamicAncestorAdapter replaces DynamicComponentAdapter
 *      24-Dec-04   extends BasicPalette
 *      27-Dec-04   added online help
 *		26-Mar-05	added cueing; new keyboard shortcuts
 *		16-Jul-05	fixed empty loop spans
 */

package de.sciss.meloncillo.realtime;

import java.awt.Container;
import java.awt.geom.Point2D;

import javax.swing.AbstractButton;
import javax.swing.JPanel;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.common.AppWindow;
import de.sciss.gui.GUIUtil;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.session.Session;

/**
 *	A GUI component showing
 *	basic transport gadgets. This class
 *	invokes the appropriate methods in the
 *	<code>Transport</code> class when these
 *	gadgets are clicked.
 *	<p><pre>
 *	Keyb.shortcuts :	space or numpad-0 : play / stop
 *						G : go to time
 *	</pre>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 13-Jul-08
 *
 *	@todo		cueing sometimes uses an obsolete start position.
 *				idea: cue speed changes with zoom level
 */
public class TransportPalette
extends AppWindow
{
	private final TransportToolBar	tb;

	/**
	 *	Creates a new transport palette. Other classes
	 *	may wish to add custom gadgets using <code>addButton</code>
	 *	afterwards.
	 *
	 *	@param	root	Application root
	 *	@param	doc		Session document
	 */
	public TransportPalette( final Session doc )
	{
		super( PALETTE );
		setResizable( false );
		
        final Container		cp	= getContentPane();
		final JPanel		gp	= GUIUtil.createGradientPanel();
		final Application	app	= AbstractApplication.getApplication();

		setTitle( app.getResourceString( "paletteTransport" ));

		tb			= new TransportToolBar( doc );
		tb.setOpaque( false );
		gp.add( tb );
		cp.add( gp );

		init();
		app.addComponent( Main.COMP_TRANSPORT, this );
	}

	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_TRANSPORT );
		super.dispose();
	}

	/**
	 *	Causes the timeline position label
	 *	to blink red to indicate a dropout error
	 */
	public void blink()
	{
//		lbTime.blink();	// EEE
	}

	/**
	 *	Adds a new button to the transport palette
	 *
	 *	@param	b	the button to add
	 */
	public void addButton( AbstractButton b )
	{
//		if( b instanceof JToggleButton ) {
//			tb.addToggleButton( (JToggleButton) b, customGroup );
//			customGroup++;
//		} else {
			tb.addButton( b );
//		}
		pack();
	}

	protected boolean autoUpdatePrefs()
	{
		return true;
	}

	protected Point2D getPreferredLocation()
	{
		return new Point2D.Float( 0.65f, 0.05f );
	}
}