/*
 *  SectorReceiverEditor.java
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
 *		15-Mar-05	created from SigmaReceiverEditor
 *		04-Apr-05	extends TableLookupReceiverEditor
 */

package de.sciss.meloncillo.receiver;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import de.sciss.gui.VectorSpace;
import de.sciss.meloncillo.session.Session;

/**
 *  An editor suitable for <code>SectorReceiver</code>s.
 *  Boundary and name edits are left to the
 *  <code>ObserverPalette</code>. This simply
 *  provides two <code>VectorEditor</code>s for
 *  the distance and rotation table respectively.
 *  It provides a basic copy and paste functionality.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see		SectorReceiver
 */
public class SectorReceiverEditor
extends TableLookupReceiverEditor
{
	private final Line2D		shpLine			= new Line2D.Double();
	private final Ellipse2D		shpEllipse		= new Ellipse2D.Double();

	public SectorReceiverEditor( Session doc )
	{
		super( doc );
    }
	
	public void init( Receiver rcv )
	{
		super.init( rcv );
	}
	
	/**
	 *  This editor can handle
	 *  <code>SectorReceiver</code>s at the moment.
	 *
	 *  @see	SectorReceiver
	 */
	public java.util.List getHandledReceivers()
	{
		final List list = new ArrayList( 1 );
		list.add( SectorReceiver.class );
		return list;
	}
	
	protected Shape getRotationShape( double angle )
	{
		final SectorReceiver	srcv		= (SectorReceiver) rcv;
		final Point2D			cp			= srcv.getCenterPoint();
		final double			innerRadius	= srcv.getInnerRadius();
		final double			outerRadius	= srcv.getOuterRadius();
		final double			sin			= Math.sin( angle );
		final double			cos			= Math.cos( angle );
		
		shpLine.setLine( cp.getX() + innerRadius * cos, cp.getY() - innerRadius * sin,
						 cp.getX() + outerRadius * cos, cp.getY() - outerRadius * sin );
		return shpLine;
	}

	protected Shape getDistanceShape( double dist )
	{
		final Point2D cp = ((SectorReceiver) rcv).getCenterPoint();

		shpEllipse.setFrameFromCenter( cp.getX(), cp.getY(), cp.getX() + dist, cp.getY() + dist );
//		shpEllipse.setFrame( cp.getX() - dist, cp.getY() - dist, 2 * dist, 2 * dist );
		return shpEllipse;
	}

	protected VectorSpace getDistanceSpace( VectorSpace oldSpace )
	{
		final SectorReceiver	srcv			= (SectorReceiver) rcv;
		final double			innerRadius		= srcv.getInnerRadius();
		final double			outerRadius		= srcv.getOuterRadius();
//		final double			dist			= (outerRadius - innerRadius) / 2;
		final VectorSpace		newSpace		= VectorSpace.createLinSpace(
			innerRadius, outerRadius, oldSpace.vmin, oldSpace.vmax,
			oldSpace.hlabel, oldSpace.hunit, oldSpace.vlabel, oldSpace.vunit );
			
		return newSpace;
	}
	
	protected VectorSpace getRotationSpace( VectorSpace oldSpace )
	{
		final SectorReceiver	srcv			= (SectorReceiver) rcv;
		final double			angleStartDeg	= srcv.getAngleStart();
		final double			angleExtentDeg	= srcv.getAngleExtent();
		
		return( VectorSpace.createLinSpace(
			angleStartDeg, angleStartDeg + angleExtentDeg, oldSpace.vmin, oldSpace.vmax,
			oldSpace.hlabel, oldSpace.hunit, oldSpace.vlabel, oldSpace.vunit ));
	}
}