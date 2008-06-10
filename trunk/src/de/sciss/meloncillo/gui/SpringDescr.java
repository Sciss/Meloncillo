/*
 *  SpringDescr.java
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
 *		26-May-04   created
 *		31-Jul-04   commented
 */


package de.sciss.meloncillo.gui;

import javax.swing.*;

import de.sciss.meloncillo.util.*;

/**
 *  A SpringDescr describes a layout relation
 *  of component towards another component.
 *  It is composed of a SpringLayout's edge constant
 *  NORTH, SOUTH, WEST or EAST and the component
 *  to which is related. For example, if window A
 *  wants to be displayed right to window B, it
 *  should create a SpringDescr using SpringLayout.EAST
 *  and reference component window B. The SpringDescr
 *  are used in BasicFrame's layoutWindows method.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 31-Jul-04
 *
 *  @see		BasicFrame#layoutWindows( Main, Map, boolean )
 *  @see		javax.swing.SpringLayout
 */
public class SpringDescr
{
	/**
	 *  reference edge : this component should be
	 *  displayed above the reference object
	 */
	public static final String   NORTH	= SpringLayout.NORTH;
	/**
	 *  reference edge : this component should be
	 *  displayed below the reference object
	 */
	public static final String   SOUTH	= SpringLayout.SOUTH;
	/**
	 *  reference edge : this component should be
	 *  displayed left to the reference object
	 */
	public static final String   WEST	= SpringLayout.WEST;
	/**
	 *  reference edge : this component should be
	 *  displayed right to the reference object
	 */
	public static final String   EAST	= SpringLayout.EAST;
	/**
	 *  reference edge : special value. the reference
	 *  object is not another component but a Boolean
	 *  specifying if the component wishes to be shown
	 *  or hidden.
	 */
	public static final String   VISIBLE = PrefsUtil.KEY_VISIBLE;

	/**
	 *  The preferred padding between
	 *  the two components in pixels
	 */
	protected final int pad;
	/**
	 *  The edge relationship between the
	 *  two components, one of NORTH, SOUTH,
	 *  EAST or WEST. Or the special value VISIBLE
	 */
	protected final String	refEdge;
	protected final Object	ref;
	
	/**
	 *  Constructs a new SpringDescr.
	 *
	 *  @param  refEdge one of NORTH, SOUTH, EAST, WEST, or VISIBLE.
	 *  @param  ref		the reference component or a Boolean if
	 *					refEdge is VISIBLE.
	 *  @param  pad		preferred padding amount to the refernce
	 *					component in pixels
	 */
	public SpringDescr( String refEdge, Object ref, int pad )
	{
		this.pad		= pad;
		this.refEdge	= refEdge;
		this.ref		= ref;
	}
	
	/**
	 *  Constructs a new SpringDescr with no padding to the
	 *  reference object.
	 *
	 *  @param  refEdge one of NORTH, SOUTH, EAST, WEST, or VISIBLE.
	 *  @param  ref		the reference component or a Boolean if
	 *					refEdge is VISIBLE.
	 */
	public SpringDescr( String refEdge, Object ref )
	{
		this( refEdge, ref, 8 );
	}
}
