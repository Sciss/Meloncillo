/*
 *  SectorReceiver.java
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
 *		15-Mar-05	created
 *		19-Mar-05	bugfix : init methods should use null source for MapManager
 *					to prevent premature listener invocation (NullPointerException)
 *		04-Apr-05	extends TableLookupReceiver
 */

package de.sciss.meloncillo.receiver;

import java.awt.*;
import java.awt.geom.*;

import de.sciss.meloncillo.util.*;
import de.sciss.util.NumberSpace;

/**
 *  A Receiver generalizing the circular
 *	shape of <code>SigmaReceiver</code> by
 *	defining a starting and ending angle and
 *	an inner radius, resulting in a circle sector
 *	shape. This receiver is much more useful
 *	for placing receivers in a circle than the
 *	predecessor <code>SigmaReceiver</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo		cloning buggy (X, Y anchor points not independant copies??)
 */
public class SectorReceiver
extends TableLookupReceiver
{
	private final Arc2D	outerArc			= new Arc2D.Double();
	private final Area	outline				= new Area();
	
	private static final String	MAP_KEY_OUTERDIAM	= "outerdiam";
	private static final String	MAP_KEY_INNERDIAM	= "innerdiam";
	private static final String	MAP_KEY_ANGLESTART	= "anglestart";
	private static final String	MAP_KEY_ANGLEEXTENT	= "anglextent";
	
	private double	innerRadius, outerRadius, minx, miny, maxx, maxy, cx, cy;
	private double	minRadSqr, maxRadSqr, rotNorm, rotOff, distNorm;
	private double	angleStart, angleExtent;		// note : measured in radians

	private static final String[] BOUNDING_KEYS = {
		MAP_KEY_X, MAP_KEY_Y, MAP_KEY_OUTERDIAM, MAP_KEY_INNERDIAM, MAP_KEY_ANGLESTART, MAP_KEY_ANGLEEXTENT
	};

	/**
	 *  Creates a new SectorReceiver with default tables.
	 */
	public SectorReceiver()
	{
		super( new float[ 1024 ], new float[ 1024 ]);
		
		// create a default setup for distance and rotation
		final int		distPoints  = distanceTable.length;
		final int		rotPoints	= rotationTable.length;
		double d1, d2;

		d1 = PI2 / distPoints;
		d2 = -Math.PI/2;
		for( int i = 0; i < distPoints; i++ ) {
			distanceTable[ i ]	= (float) ((Math.sin( i * d1 + d2 ) + 1.0) / 2);
		}
		d2 = 0.5 * rotPoints;
		d1 = 1.0 / d2;
		for( int i = 0; i < rotPoints; i++ ) {
			rotationTable[ i ]	= (float) Math.sqrt( 1.0 - Math.abs( i - d2 ) * d1 );
		}
		recalcBounds();
	}

//	public SectorReceiver( float[] distanceTable, float[] rotationTable )
//	{
//		super( distanceTable, rotationTable );
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
	public SectorReceiver( SectorReceiver orig )
	{
		super( orig );
		recalcBounds();
	}

	protected void init()
	{
		super.init();
		
//		final NumberSpace	spcDiam		= new NumberSpace( 0.0, 1.0, 0.0001, 0.5, 0.1 );
		final NumberSpace	spcDiam		= new NumberSpace( 0.0, 2.0, 0, 0, 4, 0.5 );
//		final NumberSpace	spcArc		= new NumberSpace( -360.0, 360.0, 0.0001, 0.0, 0.1 );
		final NumberSpace	spcArc		= new NumberSpace( -360.0, 360.0, 0.0, 0, 4, 0.1 );
		final MapManager	map			= getMap();

		map.putContext( null, MAP_KEY_INNERDIAM, new MapManager.Context(  MapManager.Context.FLAG_OBSERVER_DISPLAY |
																		  MapManager.Context.FLAG_VISUAL,
																		  MapManager.Context.TYPE_DOUBLE, spcDiam,
																		  "labelInnerDiam", null, new Double( 0.5 )));
		map.putContext( null, MAP_KEY_OUTERDIAM, new MapManager.Context(  MapManager.Context.FLAG_OBSERVER_DISPLAY |
																		  MapManager.Context.FLAG_VISUAL,
																		  MapManager.Context.TYPE_DOUBLE, spcDiam,
																		  "labelOuterDiam", null, new Double( 1.5 )));
		map.putContext( null, MAP_KEY_ANGLESTART, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY |
																		  MapManager.Context.FLAG_VISUAL,
																		  MapManager.Context.TYPE_DOUBLE, spcArc,
																		  "labelAngleStart", null, new Double( 0.0 )));
		map.putContext( null, MAP_KEY_ANGLEEXTENT, new MapManager.Context(MapManager.Context.FLAG_OBSERVER_DISPLAY |
																		  MapManager.Context.FLAG_VISUAL,
																		  MapManager.Context.TYPE_DOUBLE, spcArc,
																		  "labelAngleExtent", null, new Double( 90.0 )));
	}
	
	protected void recalcBounds()
	{
		final Point2D anchor  = getAnchor();

		Object		val;
		double		angleStartDeg, angleExtentDeg, anchorAngle, anchorRadius;
		Rectangle2D	bounds;
		
		val				= getMap().getValue( MAP_KEY_INNERDIAM );
		innerRadius		= ((Number) val).doubleValue() / 2;
		val				= getMap().getValue( MAP_KEY_OUTERDIAM );
		outerRadius		= ((Number) val).doubleValue() / 2;
		val				= getMap().getValue( MAP_KEY_ANGLEEXTENT );
		angleExtentDeg	= ((Number) val).doubleValue();
		val				= getMap().getValue( MAP_KEY_ANGLESTART );
		angleStartDeg	= -((Number) val).doubleValue() - angleExtentDeg + 90;
		angleStart		= angleStartDeg * Math.PI / 180;
		while( angleStart < 0.0 ) angleStart += PI2;
		angleStart	   %= PI2;
		angleExtent		= angleExtentDeg * Math.PI / 180;
		
		anchorRadius	= (innerRadius + outerRadius) / 2;
		anchorAngle		= angleStart + angleExtent / 2;
		cx				= anchor.getX() - Math.cos( anchorAngle ) * anchorRadius;
		cy				= anchor.getY() + Math.sin( anchorAngle ) * anchorRadius;
		distNorm		= distanceTable.length / (outerRadius - innerRadius);
		rotNorm			= rotationTable.length / angleExtent;
		minRadSqr		= innerRadius * innerRadius;
		maxRadSqr		= outerRadius * outerRadius;
		rotOff			= -angleStart; // (angleStart + angleExtent);
		while( rotOff > Math.PI ) rotOff -= PI2;
		while( rotOff < Math.PI ) rotOff += PI2;
		
//System.err.println( "rotOff "+rotOff+"; rotNorm "+rotNorm+"; angleExtent "+angleExtent );

//System.err.println( "angleStartDeg "+angleStartDeg+"; angleStart "+angleStart+"; anchorAngle "+anchorAngle+"; cx "+
//	cx+"; cy "+cy );

		outerArc.setArcByCenter( cx, cy, outerRadius, angleStartDeg, angleExtentDeg, Arc2D.PIE );
		outline.reset();
		outline.add( new Area( outerArc ));
		outline.subtract( new Area( new Ellipse2D.Double( cx - innerRadius, cy - innerRadius, innerRadius * 2, innerRadius * 2 )));
		bounds			= outline.getBounds2D();
		minx			= bounds.getMinX();
		maxx			= bounds.getMaxX();
		miny			= bounds.getMinY();
		maxy			= bounds.getMaxY();
		outline.transform( AffineTransform.getTranslateInstance( -anchor.getX(), -anchor.getY() ));
	}

	protected String[] getBoundingKeys()
	{
		return BOUNDING_KEYS;
	}

	public Rectangle2D getBounds()
	{
		return outerArc.getBounds();
	}

	public Point2D getCenterPoint()
	{
		final double anchorRadius	= (getInnerRadius() + getOuterRadius()) / 2;
		final double angleExtentDeg	= getAngleExtent();
		final double angleStartDeg	= -getAngleStart() - angleExtentDeg + 90;
		final Point2D anchor		= getAnchor();
		
		angleStart					= angleStartDeg * Math.PI / 180;
		while( angleStart < 0.0 ) angleStart += PI2;
		angleStart				   %= PI2;
		angleExtent					= angleExtentDeg * Math.PI / 180;

		final double anchorAngle	= angleStart + angleExtent / 2;
		final double cx				= anchor.getX() - Math.cos( anchorAngle ) * anchorRadius;
		final double cy				= anchor.getY() + Math.sin( anchorAngle ) * anchorRadius;

		return new Point2D.Double( cx, cy );
	}

	// in degrees
	public double getAngleStart()
	{
		return( ((Number) getMap().getValue( MAP_KEY_ANGLESTART )).doubleValue() );
	}

	// in degrees
	public double getAngleExtent()
	{
		return( ((Number) getMap().getValue( MAP_KEY_ANGLEEXTENT )).doubleValue() );
	}

	public double getInnerRadius()
	{
		return( ((Number) getMap().getValue( MAP_KEY_INNERDIAM )).doubleValue() / 2 );
	}

	public double getOuterRadius()
	{
		return( ((Number) getMap().getValue( MAP_KEY_OUTERDIAM )).doubleValue() / 2 );
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
		double		r, theta, dx, dy, dxs, dys, ds, wdist, wrot;
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
			dx		= x - cx;
			dxs		= dx * dx;
			dy		= cy - y;
			dys		= dy * dy;
			ds		= dxs + dys;
			if( (ds > maxRadSqr) || (ds < minRadSqr) ) {
				sense[ off ] = 0.0f;
				continue;
			}
			r		= (Math.sqrt( ds ) - innerRadius) * distNorm;
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

			theta	= Math.atan2( dy, dx ) + rotOff;
			if( theta > PI2 ) theta -= PI2;
			if( theta >= angleExtent ) {
				sense[ off ] = 0.0f;
				continue;
			}
			theta	= (angleExtent - theta) * rotNorm;
//			theta	= ((Math.atan2( dy, dx ) + rotOff) * rotNorm);
//System.err.println( "atan( "+dy+", "+dx+" ) = "+Math.atan2( dy, dx )+" --> theta = "+theta);
//			if( theta < 0.0 ) theta += PI2;
//			theta  *= rotNorm;
			rotIdx  = (int) theta;
//			if( rotIdx < 0 || rotIdx >= rotationTable.length ) {
//				sense[ off ] = 0.0f;
//				continue;
//			}
			rotIdx2 = rotIdx + 1;
			if( rotIdx2 == rotationTable.length ) rotIdx2 = 0;
			wrot	= theta % 1.0;

			sense[ off ]  = (float)
				(((1.0 - wdist) * distanceTable[ distIdx ] + wdist * distanceTable[ distIdx2 ]) *
				 ((1.0 - wrot)  * rotationTable[ rotIdx ]  + wrot  * rotationTable[ rotIdx2 ]));
		}
	}
	
	public Shape getOutline()
	{
		return outline;
	}
	
	public Class getDefaultEditor()
	{
		return SectorReceiverEditor.class;
	}
	
//	public Dimension2D getSize()
//	{
//		return new Dimension2DDouble( outerRadius * 2, outerRadius * 2 );	// XXX
//	}
}