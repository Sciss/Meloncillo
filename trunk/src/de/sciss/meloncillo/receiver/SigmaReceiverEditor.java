/*
 *  SigmaReceiverEditor.java
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
 *		14-Jun-04   now supports cursor info. top painter.
 *		31-Jul-04   DynamicAncestorAdapter replaces DynamicComponentAdapter
 *		14-Aug-04   commented. superclass manages disposal now.
 *		05-Apr-05	extends TableLookupReceiverEditor
 */

package de.sciss.meloncillo.receiver;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.session.*;

/**
 *  An editor suitable for <code>SigmaReceiver</code>s.
 *  Boundary and name edits are left to the
 *  <code>ObserverPalette</code>. This simply
 *  provides two <code>VectorEditor</code>s for
 *  the distance and rotation table respectively.
 *  It provides a basic copy and paste functionality.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		SigmaReceiver
 *
 *  @todo		in case of a clipboard copy operation, the receiver should
 *				not be copied but just the tables.
 */
public class SigmaReceiverEditor
extends TableLookupReceiverEditor
{
	private final Line2D				shpLine			= new Line2D.Double();
	private final Ellipse2D				shpEllipse		= new Ellipse2D.Double();

	public SigmaReceiverEditor()
	{
		super();

		rotationEditor.setWrapping( true, false );
    }

	public void init( Main root, Session doc, Receiver rcv )
	{
		super.init( root, doc, rcv );
	}
	
	/**
	 *  This editor can handle
	 *  <code>SigmaReceiver</code>s at the moment.
	 *
	 *  @see	SigmaReceiver
	 */
	public java.util.List getHandledReceivers()
	{
		java.util.List list = new ArrayList( 1 );
		list.add( SigmaReceiver.class );
		return list;
	}

	protected Shape getRotationShape( double angle )
	{
		final SigmaReceiver	srcv		= (SigmaReceiver) rcv;
		final Point2D		anchor		= srcv.getAnchor();
		final Dimension2D	size		= srcv.getSize();
		
		shpLine.setLine( anchor.getX(), anchor.getY(),
						 anchor.getX() + size.getWidth() / 2 * Math.cos( angle ),
						 anchor.getY() + size.getHeight() / 2 * Math.sin( angle ) );
		return shpLine;
	}

	protected Shape getDistanceShape( double dist )
	{
		final SigmaReceiver	srcv		= (SigmaReceiver) rcv;
		final Point2D		anchor		= srcv.getAnchor();

		shpEllipse.setFrameFromCenter( anchor.getX(), anchor.getY(),
									   anchor.getX() + dist, anchor.getY() + dist );
		return shpEllipse;
	}

	protected VectorSpace getDistanceSpace( VectorSpace oldSpace )
	{
		final SigmaReceiver	srcv		= (SigmaReceiver) rcv;
		final Dimension2D	size		= srcv.getSize();
		final VectorSpace	newSpace	= VectorSpace.createLinSpace(
			0.0, size.getWidth() / 2, oldSpace.vmin, oldSpace.vmax,
			oldSpace.hlabel, oldSpace.hunit, oldSpace.vlabel, oldSpace.vunit );
			
		return newSpace;
	}
	
	protected VectorSpace getRotationSpace( VectorSpace oldSpace )
	{
		final VectorSpace	newSpace	= VectorSpace.createLinSpace(
			-180.0, 180.0, oldSpace.vmin, oldSpace.vmax,
			oldSpace.hlabel, oldSpace.hunit, oldSpace.vlabel, oldSpace.vunit );
		return newSpace;
	}
}