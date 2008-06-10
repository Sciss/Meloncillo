/*
 *  SelectableLabel.java
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
 *		11-Aug-04   created
 *		02-Sep-04	commented
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;

/**
 *	Re-implementation of a Swing label
 *	which extends <code>JLabel</code> by
 *	making the label selectable and offering
 *	vertical orientation. Selected state
 *	is displayed with translucent blue.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class SelectableLabel
extends JComponent
{
	/**
	 *	orient: normal left-to-right horizontally
	 */
	public static final int HORIZONTAL  = 0;
	/**
	 *	orient: rotated ninty degrees clockwise top-to-bottom vertically
	 */
	public static final int VERTICAL	= 1;

	private boolean					selected	= false;
	private String					labelText;
	private final boolean			horizontal;
	private final Dimension			emptySize   = new Dimension( 0, 0 );
	private Dimension				recentSize  = emptySize;
	private final AffineTransform   trnsLabel   = new AffineTransform();
	private Rectangle2D				labelRect;

    private static final Color  colrSelection   = GraphicsUtil.colrSelection;

	/**
	 *	Creates a new empty label
	 *	with the given orientation
	 *
	 *	@param	orient	either <code>HORIZONTAL</code> or <code>VERTICAL</code>
	 */
	public SelectableLabel( int orient )
	{
		super();
		
		horizontal = orient == HORIZONTAL;
	}
	
	/**
	 *  Determines if this label is selected
	 *
	 *  @return true if this label is shown as being selected
	 */
	public boolean isSelected()
	{
		return selected;
	}

	/**
	 *  Changes the selection state
	 *
	 *  @param	state	<code>true</code> to display the label
	 *					in selected state, <code>false</code> elsewise
	 */
	public void setSelected( boolean state )
	{
		selected = state;
		repaint();
	}
	
	// ? (sync : call in event thread )
	/**
	 *	Changes the label text
	 *
	 *	@param	text	new text of the label or <code>null</code>
	 */
	public void setText( String text )
	{
		labelText   = text;
		recentSize  = emptySize;
		repaint();
	}

	/**
	 *	Queries the current label text
	 *
	 *	@return	the label text or <code>null</code> if no text is shown
	 */
	public String getText()
	{
		return labelText;
	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );

		Dimension		d			= getSize();
		Graphics2D		g2			= (Graphics2D) g;
		AffineTransform	trnsOrig	= g2.getTransform();
		FontMetrics		fntMetr;

		if( (recentSize.width != d.width) || (recentSize.height != d.height) ) {
			recentSize = d;
			if( labelText != null ) {
				fntMetr		= g.getFontMetrics();
				labelRect   = fntMetr.getStringBounds( labelText, g );

				if( horizontal ) {
					trnsLabel.setToTranslation(
						d.width - labelRect.getWidth() - 4,
						(d.height - fntMetr.getHeight()) / 2 + fntMetr.getAscent() );
				} else {
					trnsLabel.setToRotation( Math.PI/2, 0.0, 0.0 );
					trnsLabel.translate( d.height - labelRect.getWidth() - 4,
										 d.width/2 - fntMetr.getAscent() );
				}
			}
		}
		
		if( labelText != null ) {
			g2.transform( trnsLabel );
			g2.drawString( labelText, 0, 0 );
			g2.setTransform( trnsOrig );
		}
		if( selected ) {
			g.setColor( colrSelection );
			g.fillRect( 0, 0, recentSize.width, recentSize.height );
		}
	}
}