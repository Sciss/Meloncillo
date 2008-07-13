/*
 *  Receiver.java
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
 *		15-Mar-05	removed size specific methods
 */

package de.sciss.meloncillo.receiver;

import java.awt.Shape;
import java.awt.datatransfer.DataFlavor;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import de.sciss.meloncillo.session.GroupableSessionObject;

/**
 *  A receiver is one of the two main
 *  objects contained in a session. It is
 *  maintained by the session's <code>ReceiverCollection</code>.
 *  A receiver represents a static area shaped
 *  object that can have different socalled
 *  sensitivites for different locations inside
 *  its bounding area.
 *  <p>
 *  The <code>Receiver</code> interface is a
 *  general description of a receiver,
 *  giving it a name and bounds
 *  and providing a method for querying
 *  sensitivities as a function of
 *  a point in 2D-space. Additional
 *  methods deal with data storage
 *  (<code>set/getDirectory</code>)
 *  and GUI display (<code>getOutline</code>).
 *  <p>
 *  All coordinates are defined for
 *  the virtual space (0,0) ... (1,1)
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	de.sciss.meloncillo.session.SessionCollection
 *  @see	de.sciss.meloncillo.gui.VirtualSurface
 */
public interface Receiver
extends GroupableSessionObject
{
	public static final int OWNER_SENSE	=	0x2000;

	public static final String	MAP_KEY_X		= "x";
	public static final String	MAP_KEY_Y		= "y";

	/**
	 *  Flavor for copying the Receiver
	 *  to a clipboard
	 */
	public static final DataFlavor receiverFlavor = new DataFlavor( Receiver.class, null );

	/**
	 *  Changes the Receiver's anchor point.
	 *  Implementors must copy the point such
	 *  that a successive modification through
	 *  <code>setLocation</code> does not influence
	 *  the receiver.
	 *
	 *  @param  newAnchor   new anchor point, i.e. the
	 *						point which in a way defines
	 *						the receivers center or drag point,
	 *						usually visualized through a crosshair.
	 */
	public void setAnchor( Point2D newAnchor );

	/**
	 *  Changes the Receiver's size.
	 *  Implementors must copy the dimension such
	 *  that a successive modification through
	 *  <code>setSize</code> does not influence
	 *  the receiver.
	 *
	 *  @param  newSize		new width and height of the receiver
	 *						which is usually centered around the
	 *						anchor point.
	 */
//	public void setSize( Dimension2D newSize );

	/**
	 *  Queries the Receiver's anchor point.
	 *  Implementors must copy the point such
	 *  that a successive modification through
	 *  <code>setLocation</code> does not influence
	 *  the receiver.
	 *
	 *  @return		the current anchor point, i.e. the
	 *				point which in a way defines
	 *				the receivers center or drag point,
	 *				usually visualized through a crosshair.
	 */
	public Point2D getAnchor();

	/**
	 *  Queries the Receiver's size.
	 *  Implementors must copy the dimension such
	 *  that a successive modification through
	 *  <code>setSize</code> does not influence
	 *  the receiver.
	 *
	 *  @return		current width and height of the receiver
	 *				which is usually centered around the
	 *				anchor point.
	 */
//	public Dimension2D getSize();

	/**
	 *  A convenience method that uses <code>getAnchor</code>
	 *  and <code>getSize</code> to calculate the bounding
	 *  rectangle of the receiver, such that the dimension is
	 *  centered around the anchor point.
	 *
	 *  @return		the bounding rect of the receiver
	 */
	public Rectangle2D getBounds();

	/**
	 *  Calculates sensitivities for a given number of
	 *  points in the virtual space. Points read and
	 *  calculated are <code>points[][off + n*step]</code>
	 *  where <code>n < N; N = stop - off</code>.
	 *  <p>
	 *  Implementors should usually return zero
	 *  for all points outside the receiver's bounds.
	 *  Though this is because other objects may well
	 *  assume that the receiver doesn't sense outside
	 *  the bounds and wish to not call this method
	 *  at all for points outside the bounds.
	 *
	 *  @param  points  an array of two-dimensional points
	 *					of arbitrary succession, where each
	 *					point consists of horizontal coordinate
	 *					in <code>points[0][n]</code> and vertical coordinate
	 *					in <code>points[1][n]</code>.
	 *  @param  sense   an array to store the results. that is,
	 *					this method will place a sensitivity value
	 *					for each <code>point[][n]</code> into the corresponding
	 *					element of this array, <code>sense[n]</code>.
	 *  @param  off		array offset for both <code>points</code>
	 *					and <code>sense</code>.
	 *  @param  stop	last array index (exclusive!) for both
	 *					<code>points</code> and <code>sense</code>. a stop
	 *					value is used instead of a length value because this
	 *					is more convenient when using step sizes other than 1.
	 *  @param  step	step sizes in the arrays. in the realtime performance
	 *					it may be convenient to not calculate sensitivites
	 *					for all points.
	 */
	public void getSensitivities( float[][] points, float[] sense, int off, int stop, int step );

	/**
	 *  Gets a geometric shape of the schematic outline
	 *  of the receiver, usable for displaying on the GUI.
	 *  For rectangular shaped receives, this should be
	 *  identical to the rectangle returned by <code>getBounds</code>.
	 *  Other receives might return a circle shape or the like.
	 *
	 *  @return		receiver's outline which, when drawn
	 *				onto the surface, will give an idea about
	 *				the schematic bounds of the receiver.
	 */
	public Shape getOutline();
}