/*
 *  TimelineToolBar.java
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
 *		25-Mar-05	created from SurfaceToolBar
 */

package de.sciss.meloncillo.timeline;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.AbstractButton;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

import de.sciss.app.AbstractApplication;
import de.sciss.gui.GUIUtil;
import de.sciss.meloncillo.gui.BlendingAction;
import de.sciss.meloncillo.gui.CatchAction;
import de.sciss.meloncillo.gui.ToolAction;
import de.sciss.meloncillo.gui.ToolBar;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.util.PrefsUtil;
import de.sciss.util.Disposable;

/**
 *	A palette of tools for editing
 *	objects in the timline frame. Window
 *	key commands are installed: F1 to F6
 *	for the tools and capslock for toggling
 *	the blending option.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class TimelineToolBar
extends ToolBar
implements Disposable
{
	private final Map					mapToolButtons	= new HashMap();

	/**
	 *	Creates a tool palette with
	 *	default buttons for editing the timeline frame.
	 *
	 *	@param	root	Application root
	 */
	public TimelineToolBar( Session doc )
	{
		super( ToolBar.HORIZONTAL );

		ToolAction			toolAction;
		BlendingAction		actionBlending;
		CatchAction			actionCatch;
		JToggleButton		toggle;
		final Preferences	prefs = AbstractApplication.getApplication().getUserPrefs();

		actionCatch		= new CatchAction( prefs.node( PrefsUtil.NODE_SHARED ));
		toggle			= actionCatch.getButton();
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_V, 0 )));
// EEE
//        HelpGlassPane.setHelp( toggle, "ToolCatch" );	// EEE
        this.addToggleButton( toggle, 2 );
		this.addSeparator();

		toolAction		= new ToolAction( ToolAction.POINTER );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 ));
//        HelpGlassPane.setHelp( toggle, "TimelineToolPointer" );	// EEE
  		this.addToggleButton( toggle, 0 );
		mapToolButtons.put( new Integer( toolAction.getID() ), toggle );
        
		toolAction		= new ToolAction( ToolAction.LINE );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F2, 0 ));
//        HelpGlassPane.setHelp( toggle, "TimelineToolLine" );	// EEE
toolAction.setEnabled( false );	// XXX not yet implemented
  		this.addToggleButton( toggle, 0 );
		mapToolButtons.put( new Integer( toolAction.getID() ), toggle );

		toolAction		= new ToolAction( ToolAction.PENCIL );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F3, 0 ));
//        HelpGlassPane.setHelp( toggle, "TimelineToolPencil" );	// EEE
  		this.addToggleButton( toggle, 0 );
		mapToolButtons.put( new Integer( toolAction.getID() ), toggle );
      
		toolAction		= new ToolAction( ToolAction.ZOOM );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F4, 0 ));
//        HelpGlassPane.setHelp( toggle, "TimelineToolZoom" );	// EEE
  		this.addToggleButton( toggle, 0 );
		mapToolButtons.put( new Integer( toolAction.getID() ), toggle );

		this.addSeparator();
		actionBlending  = new BlendingAction( prefs.node( PrefsUtil.NODE_SHARED ));
		toggle			= actionBlending.getButton();
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_CAPS_LOCK, 0 )));
//        HelpGlassPane.setHelp( toggle, "ToolBlending" );		// EEE
        this.addToggleButton( toggle, 1 );
	}

	public void selectTool( int toolID )
	{
		final AbstractButton b = (AbstractButton) mapToolButtons.get( new Integer( toolID ));
		if( b != null ) b.doClick();
	}
	
	public void dispose()
	{
		/* empty */ 
	}
}