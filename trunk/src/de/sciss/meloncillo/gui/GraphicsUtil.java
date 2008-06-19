/*
 *  GraphicsUtil.java
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
 *		31-Jul-04   commented
 *		26-Mar-05	added zoom tool
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.swing.*;

import de.sciss.gui.TiledImage;

/**
 *  This is a helper class containing utility static functions
 *  and public objects for common graphics operations
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo	the tool buttons on non-macos look-and-feel are far to wide
 */
public class GraphicsUtil
{
	/**
	 *  Blue translucent colour
	 *  for consistent style in selected objects
	 */
	public static final Color		colrSelection   = new Color( 0x00, 0x00, 0xFF, 0x2F );
	/**
	 *  Yellow translucent colour
	 *  for consistent style in optional adjustment objects
	 */
	public static final Color		colrAdjusting   = new Color( 0xFF, 0xFF, 0x00, 0x2F );
	/**
	 *  Default font for GUI elements.
	 *
	 *  @see	GUIUtil#setDeepFont( Container, Font )
	 *  @todo   this is rather small and could be
	 *			user adjustable in a future version.
	 */
	public static final Font		smallGUIFont	= new Font( "Helvetica", Font.PLAIN, 10 );
	/**
	 *  MacOS X Aqua style bar gradient with a size of 15 pixels
	 *
	 *  @see	de.sciss.meloncillo.timeline.TimelineAxis
	 *  @todo   this should look different on Windows and Linux
	 *			depending on their VM's chrome.
	 */
	public static final Paint		pntBarGradient;
	/**
	 *  Collection of toolbar icons. The corresponding IDs are those
	 *  named ICON_... (e.g. <code>ICON_PLAY</code> for the transport
	 *  play icon).
	 *
	 *  @see	#createToolIcons( int )
	 */
//	protected static final TiledImage  imgToolIcons	= new TiledImage( "images/toolicons.png", 16, 16 );
	protected static final TiledImage  imgToolIcons	= new TiledImage( GraphicsUtil.class.getResource( "toolicons.png" ), 16, 16 );
	
	/**
	 *  Tool icon ID: transport play
	 */
	public static final int ICON_PLAY		= 0;
	/**
	 *  Tool icon ID: transport stop
	 */
	public static final int ICON_STOP		= 1;
	/**
	 *  Tool icon ID: transport loop
	 */
	public static final int ICON_LOOP		= 2;
	/**
	 *  Tool icon ID: transport rewind
	 */
	public static final int ICON_REWIND		= 3;
	/**
	 *  Tool icon ID: transport fast forward
	 */
	public static final int ICON_FASTFORWARD= 4;
	/**
	 *  Tool icon ID: open file chooser
	 */
	public static final int ICON_CHOOSEPATH = 5;
	/**
	 *  Tool icon ID: pointer tool
	 */
	public static final int ICON_POINTER	= 6;
	/**
	 *  Tool icon ID: line tool
	 */
	public static final int ICON_LINE		= 7;
	/**
	 *  Tool icon ID: bezier tool
	 */
	public static final int ICON_CURVE		= 8;
	/**
	 *  Tool icon ID: freehand tool
	 */
	public static final int ICON_PENCIL		= 9;
	/**
	 *  Tool icon ID: arc tool
	 */
	public static final int ICON_ARC		= 10;
	/**
	 *  Tool icon ID: tuning fork tool
	 */
	public static final int ICON_FORK		= 11;
	/**
	 *  Tool icon ID: tuning magnifying glass
	 */
	public static final int ICON_ZOOM		= 12;
	/**
	 *  Tool icon ID: catch (follow) timeline postion
	 */
	public static final int ICON_CATCH		= 13;
	/**
	 *  Tool icon ID: realtime plug-in (supercollider)
	 */
	public static final int ICON_REALTIME	= 15;
	/**
	 *  Tool icon ID: solo button for session objects
	 */
	public static final int ICON_SOLO		= 16;
	/**
	 *  Tool icon ID: mute button for session objects
	 */
	public static final int ICON_MUTE		= 18;
	/**
	 *  Tool icon ID: trajectory blending
	 */
	public static final int ICON_BLENDING	= 19;
	/**
	 *  Tool icon ID: preroll blending (not used)
	 */
	public static final int ICON_PREEXTRA	= 20;
	/**
	 *  Tool icon ID: postroll blending (not used)
	 */
	public static final int ICON_POSTEXTRA	= 21;

	private static final int[] pntBarGradientPixels = { 0xFFB8B8B8, 0xFFC0C0C0, 0xFFC8C8C8, 0xFFD3D3D3,
														0xFFDBDBDB, 0xFFE4E4E4, 0xFFEBEBEB, 0xFFF1F1F1,
														0xFFF6F6F6, 0xFFFAFAFA, 0xFFFBFBFB, 0xFFFCFCFC,
														0xFFF9F9F9, 0xFFF4F4F4, 0xFFEFEFEF };
	
	static {
		BufferedImage img = new BufferedImage( 1, 15, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 1, 15, pntBarGradientPixels, 0, 1 );
		pntBarGradient = new TexturePaint( img, new Rectangle( 0, 0, 1, 15 ));
	}
	
	private GraphicsUtil() {}
	
	/**
	 *  Creates an array of icons which display
	 *  a particular icon in different shades
	 *  which correspond to a gadget's states.
	 *  <ul>
	 *  <li>index 0 - gadget normal</li>
	 *  <li>index 1 - gadget selected</li>
	 *  <li>index 2 - gadget pressed down</li>
	 *  <li>index 3 - gadget disabled</li>
	 *  </ul>
	 *  Usually you'll pass the result directly to
	 *  the <code>setToolIcons</code> method.
	 *
	 *  @param  ID  ID corresponding to the index in
	 *				the tool icon tiled image, e.g.
	 *				<code>ICON_PLAY</code> or <code>ICON_LINE</code>.
	 *  @return		four <code>Icon</code> objects for
	 *				different gadget states.
	 *
	 *  @see	#setToolIcons( AbstractButton, Icon[] )
	 */
	public static Icon[] createToolIcons( int ID )
	{
		Icon[] icons = new Icon[ 4 ];
	
		for( int i = 0; i < 4; i++ ) {
			icons[i]	= imgToolIcons.createIcon( ID, i );
		}
		return icons;
	}

	/**
	 *  Change the <code>Icon</code>s of an
	 *  <code>AbstractButton</code> (<code>JButton</code>
	 *  or <code>JToggleButton</code>).
	 *
	 *  @param  b		the button whose icons are to be set
	 *  @param  icons   four <code>Icon</code> objects for
	 *					different gadget states, such as
	 *					created by the <code>createToolIcons</code> method.
	 *
	 *  @see	#createToolIcons( int )
	 */
	public static void setToolIcons( AbstractButton b, Icon[] icons )
	{
		b.setIcon( icons[0] );
		b.setSelectedIcon( icons[1] );
		b.setPressedIcon( icons[3] );
		b.setDisabledIcon( icons[2] );
//        Insets defInsets = b.getInsets();
//        int minMargin = Math.min( Math.min( defInsets.left, defInsets.right ),
//                                  Math.min( defInsets.top, defInsets.bottom ));
//        b.setMargin( new Insets( minMargin, minMargin, minMargin, minMargin ));
	}
	
	/**
	 *  Calculates the length of line.
	 *
	 *  @param  ln  the <code>Line2D</code> shape whose
	 *				length is to be calculated
	 *  @return		the length as given by the distance
	 *				of the start point to the end point
	 *
	 *  @see	java.awt.geom.Point2D#distance( double, double, double, double )
	 */
	public static double getLineLength( Line2D ln )
	{
		return( Point2D.distance( ln.getX1(), ln.getY1(), ln.getX2(), ln.getY2() ));
	}
	
	/**
	 *  Calculates the one point on a line which is
	 *  nearest to a given point, such that a line
	 *  between the given and the returned point will
	 *  be orthogonal to the line.
	 *
	 *  @param  pt  point lying somewhere next to or on the line.
	 *  @param  ln  line onto which the pt should be projected
	 *  @return		the given point projected onto the line
	 */
	public static Point2D projectPointOntoLine( Point2D pt, Line2D ln )
	{
		double  dx			= ln.getX2() - ln.getX1();
		double  dy			= ln.getY2() - ln.getY1();
		double  lineLenSq   = dx*dx + dy*dy;
		double  d1;
		
		if( lineLenSq == 0.0 ) {
			return( new Point2D.Double( ln.getX1(), ln.getY1() ));
		} else {
			d1 = ( (pt.getX() - ln.getX1()) * dx +
				   (pt.getY() - ln.getY1()) * dy ) / lineLenSq;
			return( new Point2D.Double( ln.getX1() + d1 * dx, ln.getY1() + d1 * dy ));
		}
	}
}