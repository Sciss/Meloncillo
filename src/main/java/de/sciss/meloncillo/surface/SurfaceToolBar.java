/*
 *  SurfaceToolBar.java
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
 *		14-Mar-05	created from gui.ToolPalette
 *		19-Mar-05	fixed small bug in blending button state
 *		25-Mar-05	keys are local to focussed window, blending action externalized
 */

package de.sciss.meloncillo.surface;

import java.awt.event.KeyEvent;

import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

import de.sciss.gui.GUIUtil;
import de.sciss.meloncillo.gui.ToolAction;
import de.sciss.meloncillo.gui.ToolBar;
import de.sciss.meloncillo.session.Session;

/**
 *	A palette of tools for editing
 *	objects on the surface. Window
 *	key commands are installed: F1 to F6
 *	for the tools and capslock for toggling
 *	the blending option.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class SurfaceToolBar
extends ToolBar
{
	/**
	 *	Creates a tool palette with
	 *	default buttons for editing the surface.
	 *
	 *	@param	root	Application root
	 */
	public SurfaceToolBar( Session doc )
	{
		super( ToolBar.HORIZONTAL );

		ToolAction			toolAction;
//		BlendingAction		actionBlending;
		JToggleButton		toggle;

		toolAction		= new ToolAction( ToolAction.POINTER );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 ));
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 )));
//        HelpGlassPane.setHelp( toggle, "SurfaceToolPointer" );	// EEE
  		this.addToggleButton( toggle, 0 );
        
		toolAction		= new ToolAction( ToolAction.LINE );
        toggle		= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F2, 0 ));
//        HelpGlassPane.setHelp( toggle, "SurfaceToolLine" );	// EEE
  		this.addToggleButton( toggle, 0 );
        
		toolAction		= new ToolAction( ToolAction.CURVE );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F3, 0 ));
//        HelpGlassPane.setHelp( toggle, "SurfaceToolCurve" );	// EEE
   		this.addToggleButton( toggle, 0 );
       
		toolAction		= new ToolAction( ToolAction.ARC );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F4, 0 ));
//        HelpGlassPane.setHelp( toggle, "SurfaceToolArc" );	// EEE
  		this.addToggleButton( toggle, 0 );
        
		toolAction		= new ToolAction( ToolAction.PENCIL );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F5, 0 ));
//        HelpGlassPane.setHelp( toggle, "SurfaceToolPencil" );	// EEE
  		this.addToggleButton( toggle, 0 );
      
		toolAction		= new ToolAction( ToolAction.FORK );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F6, 0 ));
//        HelpGlassPane.setHelp( toggle, "SurfaceToolFork" );	// EEE
  		this.addToggleButton( toggle, 0 );
        
		this.addSeparator();
// XXX DESIGN FEHLER, BUTTON KANN NUR EINMAL ADDIERT WERDEN!!
//		actionBlending  = doc.getBlendingAction();
//		actionBlending = new BlendingAction( doc.timeline, null );
//
//		final AbstractButton button = actionBlending.getButton();
////		root.menuFactory.addGlobalKeyCommand( new DoClickAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_CAPS_LOCK, 0 )));
//GUIUtil.createKeyAction( button, KeyStroke.getKeyStroke( KeyEvent.VK_CAPS_LOCK, 0 ));
//		add( actionBlending.getComboBox() );
//      this.addToggleButton( toggle, 1 );
	}
}