/*
 *  WaveformView.java
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
 *		18-Feb-05	created
 *		30-Jun-08	copied from EisK
 */
 
package de.sciss.meloncillo.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;

import javax.swing.JComponent;

import de.sciss.meloncillo.io.AudioTrail;
//import de.sciss.meloncillo.io.DecimatedSonaTrail;
import de.sciss.meloncillo.io.DecimatedWaveTrail;
import de.sciss.meloncillo.io.DecimationInfo;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.transmitter.Transmitter;
import de.sciss.meloncillo.util.PrefsUtil;
import de.sciss.gui.ComponentHost;
import de.sciss.io.Span;
import de.sciss.util.Disposable;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class WaveformView
extends JComponent
implements Disposable
{
//	private boolean				doRecalc		= true;
	
//	private int[]				channelMap;
	
	private Insets				insets			= new Insets( 0, 0, 0, 0 );
	private int					vGap			= 1;
		
	private Rectangle			r				= new Rectangle();
	
	private static final Paint	pntNull			= new Color( 0x7F, 0x7F, 0x00, 0xC0 );
	private static final Stroke	strkNull		= new BasicStroke( 1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL,
													1.0f, new float[] { 4.0f, 4.0f }, 0.0f );

	private int					vertScale		= PrefsUtil.VSCALE_AMP_LIN;
//	private boolean				logarithmic		= false;
	private float				ampLinMin		= -1.0f;	// minimum vector value
	private float				ampLinMax		= 1.0f;		// maximum vector value
	private float				ampLogMin		= -60f;
	private float				ampLogMax		= 0f;
	private float				freqMin			= 27.5f;
	private float				freqMax			= 20000f;
	private boolean				nullLinie		= false;

	private final Session		doc;
	private Span				viewSpan		= new Span();
	
	private DecimationInfo		info			= null; // most recent one!

	private final ComponentHost	host;
	
//	private AudioTrail currentAT = null;
	private int					fullChannels;
	private int					channelOffset;

	public WaveformView( Session doc )
	{
		this( doc, null );
	}

	public WaveformView( Session doc, ComponentHost host )
	{
		super();
		
		this.host			= host;

// EEE
//		final AudioTrail at	= doc.getAudioTrail();
//		fullChannels		= at.getChannelNum();
//		channelMap			= new int[ fullChannels ];
//		for( int i = 0; i < fullChannels; i++ ) {
//			channelMap[ i ]	= i;
//		}
		
		this.doc			= doc;
	}
	
	public void setNullLinie( boolean onOff )
	{
		if( nullLinie != onOff ) {
			nullLinie = onOff;
//			if( !logarithmic )
				triggerRedisplay();
		}
	}

	public boolean getNullLinie()
	{
		return nullLinie;
	}
	
	public void setVerticalScale( int mode )
	{
		if( vertScale != mode ) {
			vertScale = mode;
			triggerRedisplay();
		}
	}

	public int getVerticalScale()
	{
		return vertScale;
	}

//	public void setLogarithmic( boolean onOff )
//	{
//		if( logarithmic != onOff ) {
//			logarithmic = onOff;
//			triggerRedisplay();
//		}
//	}
//
//	public boolean isLogarithmic()
//	{
//		return logarithmic;
//	}

	/**
	 *  Gets the minimum allowed y value
	 *
	 *  @return		the minimum specified function value
	 */
	public float getAmpLinMin()
	{
		return ampLinMin;
	}

	/**
	 *  Gets the maximum allowed y value
	 *
	 *  @return		the maximum specified function value
	 */
	public float getAmpLinMax()
	{
		return ampLinMax;
	}

	public float getAmpLogMin()
	{
		return ampLogMin;
	}


	public float getAmpLogMax()
	{
		return ampLogMax;
	}
	
	/**
	 *  Changes the allowed range for vector values.
	 *  Influences the graphics display such that
	 *  the top margin of the panel corresponds to max
	 *  and the bottom margin corresponds to min. Also
	 *  user drawings are limited to these values unless
	 *  wrapY is set to true (not yet implemented).
	 *
	 *  @param  min		new minimum y value
	 *  @param  max		new maximum y value
	 *
	 *  @warning	the current vector is left untouched,
	 *				even if values lie outside the new
	 *				allowed range.
	 */
	public void setAmpLinMinMax( float min, float max )
	{
		if( (this.ampLinMin != min) || (this.ampLinMax != max) ) {
			this.ampLinMin	= min;
			this.ampLinMax	= max;

			if( vertScale == PrefsUtil.VSCALE_AMP_LIN ) triggerRedisplay();
		}
	}

// EEE
//	public void setAmpLogMinMax( float min, float max )
//	{
//		if( (this.ampLogMin != min) || (this.ampLogMax != max) ) {
//			this.ampLogMin	= min;
//			this.ampLogMax	= max;
//
//			if( vertScale != PrefsUtil.VSCALE_AMP_LIN ) triggerRedisplay();
//		}
//	}

	public float getFreqMin()
	{
		return freqMin;
	}


	public float getFreqMax()
	{
		return freqMax;
	}

// EEE
//	public void setFreqMinMax( float min, float max )
//	{
//		if( (this.freqMin != min) || (this.freqMax != max) ) {
//			this.freqMin	= min;
//			this.freqMax	= max;
//
//			if( vertScale == PrefsUtil.VSCALE_FREQ_SPECT ) triggerRedisplay();
//		}
//	}

	/**
	 *	@synchronization	use in swing thread only
	 */
	public void update( Span s )
	{
		viewSpan	= s;
		triggerRedisplay();
	}

//	public int getNumChannels()
//	{
//		return fullChannels;
//	}
	
	/**
	 *	@synchronization	this uses and alters one internal rectangle object,
	 *						be sure to not use this rectangle outside the swing thread,
	 *						otherwise make a copy. do not modify the returned rectangle
	 */
	public Rectangle rectForChannel( int ch )
	{
		ch += channelOffset;
		
		final int ht	= getHeight() - (insets.top + insets.bottom);
		final int temp	= ht * ch / fullChannels;
		final int y		= insets.top + temp;
		final int h		= (ht * (ch + 1) / fullChannels) - temp - vGap;
		
		r.setBounds( insets.left, y, getWidth() - (insets.left + insets.right), h );
//		r.setBounds( insets.left, insets.top, getWidth() - (insets.left + insets.right),
//		             getHeight() - (insets.top + insets.bottom) );
		
		return r;
	}
	
	public Rectangle rectForTransmitter( int trnsIdx )
	{
		final int numTrns = doc.getActiveTransmitters().size();
		final int ht	= getHeight() - (insets.top + insets.bottom);
		final int temp	= ht * trnsIdx / numTrns;
		final int y		= insets.top + temp;
		final int h		= (ht * (trnsIdx + 1) / numTrns) - temp - vGap;
		
		r.setBounds( insets.left, y, getWidth() - (insets.left + insets.right), h );
//		r.setBounds( insets.left, insets.top, getWidth() - (insets.left + insets.right),
//		             getHeight() - (insets.top + insets.bottom) );
		
		return r;
	}

	public int channelForPoint( Point p )
	{
		final int	py	= p.y - insets.top;
		final int	ht	= getHeight();
		int			y1	= 0;
		int			y2;
		
		for( int ch = 0; ch < fullChannels; ch++ ) {
			y2 = ht * (ch + 1) / fullChannels;
			if( (py >= y1) && (py < (y2 - vGap)) ) return ch;
			y1 = y2;
		}
		return -1;
	}
	
	public DecimationInfo getDecimationInfo() { return info; }

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		if( viewSpan.isEmpty() ) return;
		final Graphics2D g2 = (Graphics2D) g;

		switch( vertScale ) {
		case PrefsUtil.VSCALE_AMP_LIN:
			paintAmpLin( g2 );
			break;
// EEE
//		case PrefsUtil.VSCALE_AMP_LOG:
//			paintAmpLog( g2 );
//			break;
//		case PrefsUtil.VSCALE_FREQ_SPECT:
//			paintFreqSpect( g2 );
//			break;
		default:
			assert false : vertScale;
		}
	}
	
	private void paintAmpLin( Graphics2D g2 )
	{
//		final DecimatedWaveTrail	dt		= doc.getDecimatedWaveTrail();
//		if( dt == null ) return;
		
		final int	w	= getWidth();
		Rectangle	cr;
		int			y;
		
		fullChannels = 0;
		for( int i = 0; i < doc.getActiveTransmitters().size(); i++ ) {
			final Transmitter t = (Transmitter) doc.getActiveTransmitters().get( i );
			final AudioTrail at = t.getAudioTrail();
			fullChannels += at.getChannelNum();
		}
		
		channelOffset = 0;
		for( int i = 0; i < doc.getActiveTransmitters().size(); i++ ) {
			final Transmitter t = (Transmitter) doc.getActiveTransmitters().get( i );
			final AudioTrail at = t.getAudioTrail();
			final DecimatedWaveTrail dt = t.getDecimatedWaveTrail();
			if( dt == null ) continue;
			info = dt.getBestSubsample( new Span( viewSpan.start, viewSpan.stop + 1 ), w );
//			fullChannels = at.getChannelNum(); // XXX dirty shit
			dt.drawWaveform( info, this, g2 );
	
			if( nullLinie ) {
				g2.setPaint( pntNull );
				g2.setStroke( strkNull );
				for( int ch = 0; ch < fullChannels; ch++ ) {
					cr = rectForChannel( ch );
					y = cr.y + (cr.height >> 1);
					g2.drawLine( cr.x, y, cr.x + cr.width, y );
				}
			}
			channelOffset += at.getChannelNum();
		}
	}

/* EEE
	private void paintAmpLog( Graphics2D g2 )
	{
		final DecimatedWaveTrail	dt		= doc.getDecimatedWaveTrail();
		if( dt == null ) return;
		
		final int	w	= getWidth();
		Rectangle	cr;
		int			y;
		
		info = dt.getBestSubsample( new Span( viewSpan.start, viewSpan.stop + 1 ), w );
		dt.drawWaveform( info, this, g2 );

		if( nullLinie ) {
			g2.setPaint( pntNull );
			g2.setStroke( strkNull );
			for( int ch = 0; ch < fullChannels; ch++ ) {
				cr = rectForChannel( ch );
				y = cr.y + cr.height - 1;
				g2.drawLine( cr.x, y, cr.x + cr.width, y );
			}
		}
	}
*/
	
	private void triggerRedisplay()
	{
//		doRecalc	= true;
		if( host != null ) {
			host.update( this );
		} else if( isVisible() ) {
			repaint();
		}
	}
  
	// -------------- Disposable interface --------------
	
	public void dispose() { /* empty */ }
}
