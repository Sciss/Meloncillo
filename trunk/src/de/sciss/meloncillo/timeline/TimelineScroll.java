/*
 *  TimelineScroll.java
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
 *		12-Aug-04   commented. slight changes
 *      27-Dec-04   added online help
 *		29-Jan-05	removed duplicate scroll updates
 *		27-Mar-05	removed mouse listener (superfluous); added support for catch
 *		15-Jul-05	fix in setPosition to avoid duplicate event generation
 */

package de.sciss.meloncillo.timeline;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.prefs.*;

import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.io.*;

/**
 *  A GUI element for allowing
 *  horizontal timeline scrolling.
 *  Subclasses <code>JScrollBar</code>
 *  simply to override the <code>paintComponent</code>
 *  method: an additional hairline is drawn
 *  to visualize the current timeline position.
 *  also a translucent blue rectangle is drawn
 *  to show the current timeline selection.
 *	<p>
 *	This class tracks the catch preferences
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @todo		the display properties work well
 *				with the Aqua look+and+feel, however
 *				are slightly wrong on Linux with platinum look+feel
 *				because the scroll gadgets have different positions.
 */
public class TimelineScroll
extends JScrollBar
implements AdjustmentListener, TimelineListener, DynamicListening, PreferenceChangeListener
{
	public static final int TYPE_UNKNOWN	= 0;
	public static final int TYPE_DRAG		= 1;
	public static final int TYPE_TRANSPORT	= 2;

    private final Session   doc;

	private Dimension	recentSize		= getMinimumSize();
    private Shape		shpSelection	= null;
    private Shape		shpPosition     = null;
	private Span		timelineSel		= null;
	private long		timelineLen		= 0;
	private int			timelineLenShift= 0;
	private long		timelinePos		= 0;
	private Span		timelineVis		= new Span();
	private boolean		prefCatch;
	
	private final	Object	adjustmentSource	= new Object();
    
    private static final Color    colrSelection   = GraphicsUtil.colrSelection;
    private static final Color    colrPosition    = Color.red;
    private static final Stroke   strkPosition    = new BasicStroke( 0.5f );

	private final int trackMarginLeft;
	private final int trackMargin;
    
	/**
	 *  Constructs a new <code>TimelineScroll</code> object.
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 *
	 *	@todo	a clean way to determine the track rectangle ...
	 */
    public TimelineScroll( Main root, Session doc )
    {
        super( HORIZONTAL );
        this.doc    = doc;

		LookAndFeel laf = UIManager.getLookAndFeel();
		if( (laf != null) && laf.isNativeLookAndFeel() && (laf.getName().indexOf( "Aqua" ) >= 0) ) {
			trackMarginLeft = 6;  // for Aqua look and feel	
			trackMargin		= 39;
		} else {
			trackMarginLeft = 16;	// works for Metal, Motif, Liquid, Metouia
			trackMargin		= 32;
		}

		recalcBoundedRange();

		// --- Listener ---
		
		new DynamicAncestorAdapter( this ).addTo( this );
//        ScrollBarUI ui = getUI();
//        if( ui instanceof BasicScrollBarUI ) {
//           System.err.println( "track rect : "+((BasicScrollBarUI) ui).trackRect.x + ", "+ ((BasicScrollBarUI) ui).trackRect.y + ", "+((BasicScrollBarUI) ui).trackRect.width+", "+((BasicScrollBarUI) ui).trackRect.height );
//        }
        this.addAdjustmentListener( this );

        new DynamicAncestorAdapter( new DynamicPrefChangeManager(
			AbstractApplication.getApplication().getUserPrefs().node( PrefsUtil.NODE_SHARED ),
			new String[] { PrefsUtil.KEY_CATCH }, this )).addTo( this );
        
//        HelpGlassPane.setHelp( this, "TimelineScroll" );	// EEE
    }
    
	/**
	 *  Paints the normal scroll bar using
	 *  the super class's method. Additionally
	 *  paints timeline position and selection cues
	 */
    public void paintComponent( Graphics g )
    {
        super.paintComponent( g );
 
		Dimension   d           = getSize();
        Graphics2D  g2          = (Graphics2D) g;
		Stroke		strkOrig	= g2.getStroke();
		Paint		pntOrig		= g2.getPaint();

		if( d.width != recentSize.width || d.height != recentSize.height ) {
			recentSize = d;
			recalcTransforms();
		}
        
        if( shpSelection != null ) {
            g2.setColor( colrSelection );
            g2.fill( shpSelection );
        }
        if( shpPosition != null ) {
            g2.setColor( colrPosition );
            g2.setStroke( strkPosition );
            g2.draw( shpPosition );
        }

        g2.setStroke( strkOrig );
		g2.setPaint( pntOrig );
    }

    private void recalcBoundedRange()
    {
		final int len	= (int) (timelineLen >> timelineLenShift);
		final int len2	= (int) (timelineVis.getLength() >> timelineLenShift);
		if( len > 0 ) {
			if( !isEnabled() ) setEnabled( true );
			setValues( (int) (timelineVis.getStart() >> timelineLenShift), len2, 0, len );   // val, extent, min, max
			setUnitIncrement( Math.max( 1, (int) (len2 >> 5) ));             // 1/32 extent
			setBlockIncrement( Math.max( 1, (int) ((len2 * 3) >> 2) ));      // 3/4 extent
		} else {
			if( isEnabled() ) setEnabled( false );
			setValues( 0, 100, 0, 100 );	// full view will hide the scrollbar knob
		}
    }

    /*
     *  Calculates virtual->screen coordinates
     *  for timeline position and selection
     */
    private void recalcTransforms()
    {
        double  scale, x;

//for( int i = 0; i < getComponentCount(); i++ ) {
//	Component c = getComponent( i );
//	System.err.println( "scroll container component : "+c.getClass().getName()+" ; at "+c.getLocation().x+", "+
//		c.getLocation().y+"; w = "+c.getWidth()+"; h = "+c.getHeight() );
//}
//        
		if( timelineLen > 0 ) {
			scale           = (double) (recentSize.width - trackMargin) / (double) timelineLen;
			if( timelineSel != null ) {
				shpSelection = new Rectangle2D.Double( timelineSel.getStart() * scale + trackMarginLeft, 0,
													   timelineSel.getLength() * scale, recentSize.height );
			} else {
				shpSelection = null;                   
			}
			x               = timelinePos * scale + trackMarginLeft;
			shpPosition     = new Line2D.Double( x, 0, x, recentSize.height );
		} else {
			shpSelection    = null;
			shpPosition     = null;
		}
    }
    
	/**
	 *  Updates the red hairline representing
	 *  the current timeline position in the
	 *  overall timeline span.
	 *  Called directly from TimelineFrame
	 *  to improve performance. Don't use
	 *  elsewhere.
	 *
	 *  @param  pos			new position in absolute frames
	 *  @param  patience	allowed graphic update interval
	 *
	 *  @see	java.awt.Component#repaint( long )
	 */
	protected void setPosition( long pos, long patience, int type )
	{
		if( prefCatch && timelineVis.contains( timelinePos ) &&
			!timelineVis.contains( pos + (type == TYPE_TRANSPORT ? timelineVis.getLength() >> 3 : 0) )) {
			
			timelinePos = pos;
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				long start, stop;
				
				start	= timelinePos;
				if( type == TYPE_TRANSPORT ) {
					start -= timelineVis.getLength() >> 3;
				} else if( type == TYPE_DRAG ) {
					if( timelineVis.getStop() <= timelinePos ) {
						start -= timelineVis.getLength();
					}
				} else {
					start -= timelineVis.getLength() >> 2;
				}
				stop	= Math.min( timelineLen, Math.max( 0, start ) + timelineVis.getLength() );
				start	= Math.max( 0, stop - timelineVis.getLength() );
				if( stop > start ) {
					// it's crucial to update internal var timelineVis here because
					// otherwise the delay between emitting the edit and receiving the
					// change via timelineScrolled might be two big, causing setPosition
					// to fire more than one edit!
					timelineVis = new Span( start, stop );
					doc.timeline.editScroll( this, timelineVis );
					return;
				}
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
		timelinePos = pos;
		recalcTransforms();
		repaint( patience );
	}
	
// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
        doc.timeline.addTimelineListener( this );
		recalcTransforms();
        repaint();
    }

    public void stopListening()
    {
        doc.timeline.removeTimelineListener( this );
    }
 
// ---------------- LaterInvocationManager.Listener interface ---------------- 

	// o instanceof PreferenceChangeEvent
	public void preferenceChange( PreferenceChangeEvent pce)
	{
		final String  key	= pce.getKey();
		final String  value	= pce.getNewValue();

		if( key.equals( PrefsUtil.KEY_CATCH )) {
			prefCatch	= Boolean.valueOf( value ).booleanValue();
			if( (prefCatch == true) && !(timelineVis.contains( timelinePos ))) {
				if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
				try {
					long start	= Math.max( 0, timelinePos - (timelineVis.getLength() >> 2) );
					long stop	= Math.min( timelineLen, start + timelineVis.getLength() );
					start		= Math.max( 0, stop - timelineVis.getLength() );
					if( stop > start ) {
						doc.timeline.editScroll(  this, new Span( start, stop ));
					}
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_TIME );
				}
			}
		}
	}

// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		try {
			doc.bird.waitShared( Session.DOOR_TIME );
			timelineSel = doc.timeline.getSelectionSpan();
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
		recalcTransforms();
        repaint();
    }
    
	public void timelineChanged( TimelineEvent e )
    {
		try {
			doc.bird.waitShared( Session.DOOR_TIME );
			timelineLen = doc.timeline.getLength();
			timelineVis = doc.timeline.getVisibleSpan();
			for( timelineLenShift = 0; (timelineLen >> timelineLenShift) > 0x3FFFFFFF; timelineLenShift++ );
			recalcTransforms();
			recalcBoundedRange();
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
        repaint();
    }

	// ignored since the timeline frame will inform us
	public void timelinePositioned( TimelineEvent e ) {}

    public void timelineScrolled( TimelineEvent e )
    {
		try {
			doc.bird.waitShared( Session.DOOR_TIME );
			timelineVis = doc.timeline.getVisibleSpan();
			if( e.getSource() != adjustmentSource ) {
				recalcBoundedRange();
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
    }

// ---------------- AdjustmentListener interface ---------------- 
// we're listening to ourselves

    public void adjustmentValueChanged( AdjustmentEvent e )
    {
		if( isEnabled() ) {
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 200 )) return;
			try {
				Span oldVisi = doc.timeline.getVisibleSpan();
				Span newVisi = new Span( this.getValue() << timelineLenShift,
										 (this.getValue() + this.getVisibleAmount()) << timelineLenShift );
				if( !newVisi.equals( oldVisi )) {
//System.err.println( "dispatch EditSetTimelineScroll" );
					if( prefCatch && oldVisi.contains( timelinePos ) && !newVisi.contains( timelinePos )) {
						AbstractApplication.getApplication().getUserPrefs().node(
							PrefsUtil.NODE_SHARED ).putBoolean( PrefsUtil.KEY_CATCH, false );
					}
					doc.timeline.editScroll( adjustmentSource, newVisi );
				}
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
        }
    }
}