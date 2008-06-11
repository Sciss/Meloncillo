/*
 *  LispBounce.java
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
 *		06-Jul-04   created
 *		19-Jul-04   subclasses LispRenderPlugIn
 *		01-Jan-05	added online help
 */

package de.sciss.meloncillo.render;

import java.io.*;

import de.sciss.meloncillo.util.*;

/**
 *  A lightweight subclass
 *  of LispRenderPlugIn used
 *  for Bouncing to Disk.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class LispBounce
extends LispRenderPlugIn
{
	/**
	 *	Simply calls the superclass constructor
	 *	and installs the help pane.
	 */
	public LispBounce()
	{
		super();
//        HelpGlassPane.setHelp( this, "BounceLisp" );	// EEE
	}
	
	/**
	 *	Returns the preference key
	 *	for storing the source code list
	 *
	 *	@see	de.sciss.meloncillo.util.PrefsUtil#KEY_LISPBOUNCELIST
	 */
	protected String getSourceListKey()
	{
		return PrefsUtil.KEY_LISPBOUNCELIST;
	}
	
	/**
	 *	Executes the lisp script's "PREPARE" function.
	 */
	protected boolean invokeLispPrepare( RenderContext context,
										 RenderSource source, RenderInfo info )
	throws IOException
	{
		return executeLisp( "PREPARE" );
	}

	/**
	 *	Executes the lisp script's "RENDER" function.
	 */
	protected boolean invokeLispRender( RenderContext context,
										RenderSource source, RenderInfo info )
	throws IOException
	{
		return executeLisp( "RENDER" );
	}

	/**
	 *	Executes the lisp script's "CLEANUP" function.
	 */
	protected boolean invokeLispCleanUp( RenderContext context,
										 RenderSource source, RenderInfo info )
	throws IOException
	{
		return executeLisp( "CLEANUP" );
	}
}