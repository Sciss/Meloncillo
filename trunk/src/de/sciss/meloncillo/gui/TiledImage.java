/*
 *  TiledImage.java
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
 *		01-Aug-04   commented
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.image.*;

/**
 *  An <code>Image</code> wrapper
 *  that generates a virtual grid of
 *  sub images which are accessible
 *  by x and y offset and paintable
 *  through a custom <code>paintTile</code>
 *  method.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class TiledImage
{
	private final Image	img;
	private final int	tileWidth, tileHeight;

	/**
	 *  Creates a new TiledImage from
	 *  an image file on harddisc and
	 *  applies a custom tiling grid.
	 *  This method waits until the image was loaded.
	 *
	 *  @param  imagePath   file name to the image
	 *						(can be relative to the
	 *						application path). allowed
	 *						image formats are GIF, PNG, JPG
	 *  @param  tileWidth   horizontal width of each tile.
	 *						thus number of columns = image width / tile width
	 *  @param  tileHeight  vertical height of each tile.
	 *						thus number of rows = image height / tile height
	 *
	 *  @see	java.awt.Toolkit#getDefaultToolkit()
	 *  @see	java.awt.Toolkit#getImage( String )
	 */
	public TiledImage( String imagePath, int tileWidth, int tileHeight )
	{
		img = Toolkit.getDefaultToolkit().getImage( imagePath );
		this.tileWidth  = tileWidth;
		this.tileHeight = tileHeight;

		MediaTracker mediaTracker = new MediaTracker( new Container() );
		mediaTracker.addImage( img, 0 );
		try {
			mediaTracker.waitForID( 0 );
		} catch( InterruptedException e1 ) {}
	}
	
	/**
	 *  Queries the tile width
	 *
	 *  @return the width of each tile in pixels,
	 *			as specified in the constructor
	 */
	public int getTileWidth()
	{
		return tileWidth;
	}

	/**
	 *  Queries the tile height
	 *
	 *  @return the height of each tile in pixels,
	 *			as specified in the constructor
	 */
	public int getTileHeight()
	{
		return tileHeight;
	}
	
	/**
	 *  Paints a tile onto a graphics surface.
	 *
	 *  @param  g		<code>Graphics</code> used to draw the image
	 *  @param  x		x offset in the graphics context
	 *  @param  y		y offset in the graphics context
	 *  @param  tileX	column index of the tile (starting at zero)
	 *  @param  tileY	row index of the tile (starting at zero)
	 *  @param  o		asynchronous image update notification receiver
	 *  @return <code>true</code> if the current output representation
	 *			is complete; <code>false</code> otherwise.
	 *
	 *  @see	java.awt.Graphics#drawImage( Image, int, int, int, int, int, int, int, int, ImageObserver )
	 */
	public boolean paintTile( Graphics g, int x, int y, int tileX, int tileY, ImageObserver o )
	{
		int sx = tileX * tileWidth;
		int sy = tileY * tileHeight;
		
		return g.drawImage( img, x, y, x + tileWidth, y + tileHeight,
							sx, sy, sx + tileWidth, sy + tileHeight, o );
	}
}