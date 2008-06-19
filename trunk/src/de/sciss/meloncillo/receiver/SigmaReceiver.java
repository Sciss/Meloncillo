/*
 *  SigmaReceiver.java
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
 *		10-Aug-04   getSensitivities replaces getSensitivityAt
 *		14-Aug-04   commented
 *		05-Apr-05	extends TableLookupReceiver
 */

package de.sciss.meloncillo.receiver;

import java.awt.*;
import java.awt.geom.*;

import de.sciss.meloncillo.util.*;
import de.sciss.util.NumberSpace;

/**
 *  A Receiver modelled after the
 *  loudspeaker objects in the Sigma-1
 *  software. That is, sensitivities
 *  are calculated as a product of a
 *  distance and rotation (directivity)
 *  table. A change in divergence is
 *  achieved by changing the size of
 *  the receiver.
 *  <p>
 *  This class implements the <code>Transferable</code>
 *  interface which allows to copy and paste
 *  the receiver to the clipboard.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class SigmaReceiver
extends TableLookupReceiver
{
	private Ellipse2D outline	= new Ellipse2D.Double();
	private Rectangle2D bounds	= new Rectangle2D.Double();
	
	private double rx, ry, cx, cy, minx, miny, maxx, maxy, rotNorm;
	private double width, height;

	private static final String	MAP_KEY_WIDTH	= "width";
	private static final String	MAP_KEY_HEIGHT	= "height";
	
	private static final String[] BOUNDING_KEYS = {
		MAP_KEY_X, MAP_KEY_Y, MAP_KEY_WIDTH, MAP_KEY_HEIGHT
	};

	/**
	 *  Creates a new SigmaReceiver with default tables.
	 *  The default table size is 1024 points both for
	 *  distance and rotation. The default characteristic
	 *  is omnidirectional (constant rotation table) and
	 *  the square of the distance sense is decreasing
	 *  with increasing distance from the center. In this
	 *  case two adjectant receivers, placed such that the
	 *  -3 dB point (sqrt(0.5)) is congruent, will maintain
	 *  constant energy. That is, sense(rcv1)^2 + sense(rcv2)^2 = 1,
	 *  well working for amplitude panning such that no
	 *  dips occur if the source moves from one receiver to
	 *  the other.
	 */
	public SigmaReceiver()
	{
		super( new float[ 1024 ], new float[ 1024 ]);
		
		// create a default setup for distance and rotation (monopole)
		final int		distPoints  = distanceTable.length;
		final int		rotPoints	= rotationTable.length;
		double d1;

		for( int i = 0; i < distPoints; i++ ) {
			d1					= 1.0 - (double) i / distPoints;
			distanceTable[ i ]	= (float) Math.sqrt( d1 );
		}
		for( int i = 0; i < rotPoints; i++ ) {
			rotationTable[ i ]	= 1.0f;
		}
		recalcBounds();
	}

//	public SigmaReceiver( float[] distanceTable, float[] rotationTable )
//	{
//		super();
//		
//		this.distanceTable  = distanceTable;
//		this.rotationTable  = rotationTable;
//
//		rotNorm = (double) rotationTable.length / PI2;
//		recalcBounds();
//	}
	
	/**
	 *  Creates a new receiver which is identical
	 *  to a template receiver. This is used in clipboard
	 *  operations.
	 *
	 *  @param  orig	the receiver to copy. tables are
	 *					are copied so successive modifications of
	 *					<code>orig</code>'s tables do not influence
	 *					the newly created receiver.
	 */
	public SigmaReceiver( SigmaReceiver orig )
	{
		super( orig );
		recalcBounds();
	}

	protected void init()
	{
		super.init();
		
//		final NumberSpace	spcCoord	= new NumberSpace( 0.0, 1.0, 0.0001, 0.5, 0.1 );
		final NumberSpace	spcCoord	= new NumberSpace( 0.0, 1.0, 0.0, 0, 4, 0.5 );
		final MapManager	map			= getMap();

		map.putContext( null, MAP_KEY_WIDTH, new MapManager.Context(  MapManager.Context.FLAG_OBSERVER_DISPLAY |
																	  MapManager.Context.FLAG_VISUAL,
																	  MapManager.Context.TYPE_DOUBLE, spcCoord, "labelW",
																	  null, new Double( 0.5 )));
		map.putContext( null, MAP_KEY_HEIGHT, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY |
																	  MapManager.Context.FLAG_VISUAL,
																	  MapManager.Context.TYPE_DOUBLE, spcCoord, "labelH",
																	  null, new Double( 0.5 )));
	}
	
	protected void recalcBounds()
	{
		final Point2D anchor  = getAnchor();

		Object	val;
		val		= getMap().getValue( MAP_KEY_WIDTH );
		width	= ((Number) val).doubleValue();
		val		= getMap().getValue( MAP_KEY_HEIGHT );
		height	= ((Number) val).doubleValue();
		rx		= width / 2.0;
		ry		= height / 2.0;
		cx		= anchor.getX();
		cy		= anchor.getY();
		minx	= cx - rx;
		miny	= cy - ry;
		maxx	= cx + rx;
		maxy	= cy + ry;

		outline.setFrameFromCenter( 0.0, 0.0, rx, ry );
		bounds.setFrame( minx, miny, width, height );
		
		rotNorm = (double) rotationTable.length / PI2;
	}

	protected String[] getBoundingKeys()
	{
		return BOUNDING_KEYS;
	}

	public Rectangle2D getBounds()
	{
		return bounds;
	}

	/**
	 *  The <code>getSensitivities</code>
	 *  method uses linear interpolation in
	 *  the table lookup process in order
	 *  to provide maximum smoothness and
	 *  avoid zipper noise.
	 */
	public void getSensitivities( float[][] points, float[] sense, int off, int stop, int step )
	{
		double		r, theta, dx, dy, wdist, wrot;
		int			rotIdx, distIdx, rotIdx2, distIdx2;
		float		x, y;
		
		for( ; off < stop; off += step ) {
			x   = points[ 0 ][ off ];
			if( x < minx || x > maxx ) {
				sense[ off ] = 0.0f;
				continue;
			}
			y   = points[ 1 ][ off ];
			if( y < miny || y > maxy ) {
				sense[ off ] = 0.0f;
				continue;
			}
			dx		= (x - cx) / rx;
			dy		= (y - cy) / ry;
			r		= Math.sqrt( dx*dx + dy*dy ) * distanceTable.length;
			distIdx = (int) r;
			if( distIdx >= distanceTable.length ) {
				sense[ off ] = 0.0f;
				continue;
			}
			distIdx2 = distIdx + 1;
			if( distIdx2 < distanceTable.length ) {
				wdist   = r % 1.0;
			} else {
				distIdx2 = 0;
				wdist    = 0.0;
			}
		
//			theta   = (Math.atan2( dy, dx ) + rotOff) * rotNorm;
			theta   = (PI2 - Math.atan2( dx, dy )) * rotNorm;
			rotIdx  = (int) theta;
			if( rotIdx >= rotationTable.length ) rotIdx -= rotationTable.length;
			rotIdx2 = rotIdx + 1;
			if( rotIdx2 == rotationTable.length ) rotIdx2 = 0;
			wrot	= theta % 1.0;

			sense[ off ]  = (float)
				(((1.0 - wdist) * distanceTable[ distIdx ] + wdist * distanceTable[ distIdx2 ]) *
				 ((1.0 - wrot)  * rotationTable[ rotIdx ]  + wrot  * rotationTable[ rotIdx2 ]));
		}
	}
	
	/**
	 *  The outline is ellipsoid.
	 *
	 *  @return ellipsoid outline of the receiver's real bounds
	 */
	public Shape getOutline()
	{
		return outline;
	}
	
	public Class getDefaultEditor()
	{
		return SigmaReceiverEditor.class;
	}
	
	public Dimension2D getSize()
	{
		return new Dimension2DDouble( width, height );
	}
}