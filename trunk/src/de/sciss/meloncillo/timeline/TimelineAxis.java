/*
 *  TimelineAxis.java
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
 *		13-Jun-04   fixed label calculation
 *		12-Aug-04   commented
 *		14-Aug-04   shift+click to extend selection now more ergonomic.
 *      26-Dec-04   added online help
 *		29-Jan-05	bugfix in recalcLabels()
 *		25-Mar-05	turned into a subclass of Axis
 */

// XXX TO-DO : dispose, removeTimelineListener

package de.sciss.meloncillo.timeline;

import java.awt.event.*;
import javax.swing.undo.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.session.*;

import de.sciss.app.*;
import de.sciss.gui.*;
import de.sciss.io.*;

/**
 *  A GUI element for displaying
 *  the timeline's axis (ruler)
 *  which is used to display the
 *  time indices and to allow the
 *  user to position and select the
 *  timeline.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class TimelineAxis
extends Axis
implements TimelineListener, MouseListener, MouseMotionListener, DynamicListening
{
    private final Session   doc;

	// when the user begins a selection by shift+clicking, the
	// initially fixed selection bound is saved to selectionStart.
	private long				selectionStart  = -1;
	private boolean				shiftDrag, altDrag;
    
	/**
	 *  Constructs a new object for
	 *  displaying the timeline ruler
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
	public TimelineAxis( Main root, Session doc )
	{
		super( HORIZONTAL | TIMEFORMAT );
        
        this.doc    = doc;
		
		// --- Listener ---
        new DynamicAncestorAdapter( this ).addTo( this );
		this.addMouseListener( this );
		this.addMouseMotionListener( this );

		// ------
        HelpGlassPane.setHelp( this, "TimelineAxis" );
	}
  
	private void recalcSpace()
	{
		Span		visibleSpan;
		double		d1;
		VectorSpace	space;
	
		try {
			doc.bird.waitShared( Session.DOOR_TIME );
			visibleSpan = doc.timeline.getVisibleSpan();
			d1			= 1.0 / doc.timeline.getRate();
			space		= VectorSpace.createLinSpace( visibleSpan.getStart() * d1,
													  visibleSpan.getStop() * d1,
													  0.0, 1.0, null, null, null, null );
			setSpace( space );
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
	}

	// Sync: attempts doc.timeline
    private void dragTimelinePosition( MouseEvent e )
    {
        int				x   = e.getX();
        Span			span, span2;
        long			position;
		UndoableEdit	edit;
	   
        // translate into a valid time offset
		if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 200 )) return;
		try {
            span        = doc.timeline.getVisibleSpan();
            position    = span.getStart() + (long) ((double) x / (double) getWidth() *
                                                    (double) span.getLength());
            position    = Math.max( 0, Math.min( doc.timeline.getLength(), position ));
            
            if( shiftDrag ) {
				span2	= doc.timeline.getSelectionSpan();
				if( altDrag || span2.isEmpty() ) {
					selectionStart = doc.timeline.getPosition();
					altDrag = false;
				} else if( selectionStart == -1 ) {
					selectionStart = Math.abs( span2.getStart() - position ) >
									 Math.abs( span2.getStop() - position ) ?
									 span2.getStart() : span2.getStop();
				}
				span	= new Span( Math.min( position, selectionStart ),
									Math.max( position, selectionStart ));
				edit	= new EditSetTimelineSelection( this, doc, span );
            } else {
				if( altDrag ) {
					edit	= new CompoundEdit();
					edit.addEdit( new EditSetTimelineSelection( this, doc, new Span() ));
					edit.addEdit( new EditSetTimelinePosition( this, doc, position ));
					((CompoundEdit) edit).end();
					altDrag = false;
				} else {
					edit	= new EditSetTimelinePosition( this, doc, position );
				}
            }
			doc.getUndoManager().addEdit( edit );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
        }
    }

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
         doc.timeline.addTimelineListener( this );
    }

    public void stopListening()
    {
         doc.timeline.removeTimelineListener( this );
    }

// ---------------- MouseListener interface ---------------- 
// we're listening to the ourselves

	public void mouseEntered( MouseEvent e )
	{
//		if( isEnabled() ) dispatchMouseMove( e );
	}
	
	public void mouseExited( MouseEvent e ) {}

	public void mousePressed( MouseEvent e )
    {
		shiftDrag		= e.isShiftDown();
		altDrag			= e.isAltDown();
		selectionStart  = -1;
        dragTimelinePosition( e );
    }

	public void mouseReleased( MouseEvent e ) {}
	public void mouseClicked( MouseEvent e ) {}

// ---------------- MouseMotionListener interface ---------------- 
// we're listening to ourselves

    public void mouseMoved( MouseEvent e ) {}

	public void mouseDragged( MouseEvent e )
	{
        dragTimelinePosition( e );
	}

// ---------------- TimelineListener interface ---------------- 
  
   	public void timelineSelected( TimelineEvent e ) {}
	public void timelinePositioned( TimelineEvent e ) {}

	public void timelineChanged( TimelineEvent e )
	{
		recalcSpace();
	}

   	public void timelineScrolled( TimelineEvent e )
    {
		recalcSpace();
    }
}