/*
 *  Filler.java
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
 *		12-Aug-04   created + commented
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import javax.swing.*;

/**
 *  Analogon to Box.Filler but
 *  as a JComponent, which allows
 *  to attach a Border to this object
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.65, 12-Aug-04
 */
public class Filler
extends JComponent
{
	/**
	 *  Creates a new padding element
	 *  with given dimensions
	 *
	 *  @param  min		minimum size or null
	 *  @param  pref	preferred size or null
	 *  @param  max		maximum size or null
	 */
	public Filler( Dimension min, Dimension pref, Dimension max )
	{
		super();
		if( min != null )  setMinimumSize( min );
		if( pref != null ) setPreferredSize( pref );
		if( max != null )  setMaximumSize( max );
	}
}
