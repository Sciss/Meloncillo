/*
 *  ToolAction.java
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
 *		01-Aug-04   commented
 *		26-Mar-05	zoom tool added
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 *  An extension of <code>AbstractAction</code>
 *  that creates a set of related
 *  tool icons and a default mouse
 *  cursor for the tool.
 *  <p>
 *  The <code>actionPerformed</code> method
 *  doesn't do anything at the moment.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class ToolAction
extends AbstractAction
{
	/**
	 *  tool ID : pointer tool
	 */
	public static final int POINTER = 0;
	/**
	 *  tool ID : line drawing tool
	 */
	public static final int LINE	= 1;
	/**
	 *  tool ID : bezier drawing tool
	 */
	public static final int CURVE   = 2;
	/**
	 *  tool ID : arc drawing tool
	 */
	public static final int ARC		= 3;
	/**
	 *  tool ID : freehand / pencil tool
	 */
	public static final int PENCIL  = 4;
	/**
	 *  tool ID : fork / preview tool
	 */
	public static final int FORK	= 5;
	/**
	 *  tool ID : zoom tool
	 */
	public static final int ZOOM	= 6;
	/**
	 *  maximum tool ID at the moment
	 */
	public static final int MAX_ID  = 6;
	
//	private final static Toolkit	tk			= Toolkit.getDefaultToolkit();
//	private final static Cursor		csrZoomIn	= tk.createCustomCursor(
//		tk.createImage( "images" + File.separator + "zoomincursor.png" ), new Point( 5, 4 ), "zoom in" );
//	private final static Cursor		csrZoomOut	= tk.createCustomCursor(
//		tk.createImage( "images" + File.separator + "zoomoutcursor.png" ), new Point( 5, 4 ), "zoom out" );
	
	private static final int[] ICONS = {
		GraphicsUtil.ICON_POINTER,  GraphicsUtil.ICON_LINE,
		GraphicsUtil.ICON_CURVE,	GraphicsUtil.ICON_ARC,
		GraphicsUtil.ICON_PENCIL,	GraphicsUtil.ICON_FORK,
		GraphicsUtil.ICON_ZOOM
	};
	private static final Cursor[] CURSORS = {
		Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ), Cursor.getPredefinedCursor( Cursor.CROSSHAIR_CURSOR ),
		Cursor.getPredefinedCursor( Cursor.CROSSHAIR_CURSOR ), Cursor.getPredefinedCursor( Cursor.CROSSHAIR_CURSOR ),
		Cursor.getPredefinedCursor( Cursor.CROSSHAIR_CURSOR ), Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ),
		Cursor.getPredefinedCursor( Cursor.HAND_CURSOR )	// csrZoomIn
	};

	private final int		ID;
	private final Icon[]	icons;
//	private AbstractButton  b;

	/**
	 *  Creates a tool action with
	 *  the given ID.
	 *
	 *  @param  ID  identier for the tool, e.g. LINE or PENCIL,
	 *				which determines the icons and mouse pointer
	 */
	public ToolAction( int ID )
	{
		super();
		
		this.ID = ID;
		
		icons = GraphicsUtil.createToolIcons( ICONS[ID] );
	}
	
	/**
	 *  Attaches the icons for the different
	 *  gadget states to a button
	 *
	 *  @param  b   the button whose icons are to be set
	 *
	 *  @see	GraphicsUtil#setToolIcons( AbstractButton, Icon[] )
	 */
	public void setIcons( AbstractButton b )
	{
		GraphicsUtil.setToolIcons( b, icons );
//		this.b = b;
	}
	
	public void actionPerformed( ActionEvent e )
	{
		// ...
	}
	
	/**
	 *  Returns the tool action's ID
	 *
	 *  @return the identifier used to construct the tool action
	 */
	public int getID()
	{
		return ID;
	}
	
	/**
	 *  Asks for a default mouse cursor
	 *
	 *  @return a <code>Cursor</code> object usually used for
	 *			this kind of tool
	 */
	public Cursor getDefaultCursor()
	{
		return CURSORS[ ID ];
	}
}