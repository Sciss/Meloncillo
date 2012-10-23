/*
 *  EditRemoveTimeSpan.java
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
 *		29-Jul-04   commented
 */

package de.sciss.meloncillo.edit;

import javax.swing.undo.*;

import de.sciss.meloncillo.session.*;

import de.sciss.app.*;
import de.sciss.io.*;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the removal of a time span
 *  from the session's timeline.
 *  Actually a <code>CompoundEdit</code>
 *  composed of <code>EditSetTimelineScroll</code> (optional),
 *  <code>EditSetTimelineSelection</code> (optional),
 *  <code>EditSetTimelinePosition</code> (optional)
 *  and <code>EditSetTimelineLength</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @see		UndoManager
 *  @see		EditSetTimelineLength
 *  @see		EditSetTimelineSelection
 *  @see		EditSetTimelineScroll
 *  @see		EditInsertTimeSpan
 */
public class EditRemoveTimeSpan
extends CompoundEdit
{
	/**
	 *  Create and perform the edit. If the visible
	 *  span's stop exceeds the new truncated timeline
	 *  length, the visible span will be shifted back
	 *  in time. if the timeline selection overlaps with
	 *  the removed span, it will be truncated. if the
	 *  timeline position lies within the removed span,
	 *  it will be set to <code>span.getStart()</code>.
	 *  if the position lies after the removed span it
	 *  will be shifted back in time.
	 *
	 *  @param  source		who originated the edit
	 *  @param  doc			session from whose timeline the span is to be removed
	 *  @param  span		the time span to remove. note that selection
	 *						span and visible are possible modified in
	 *						the course of the performance of the edit, the
	 *						timeline position might be altered.
	 *  @synchronization	waitExclusive on DOOR_TIME
	 *  @todo				as far as i see the (pratically irrelevant) case
	 *						in which the selection start is smaller than
	 *						the span to remove is not handled properly.
	 */
	public EditRemoveTimeSpan( Object source, Session doc, Span span )
	{
		super();
	
		long	spanLength, position, newLength;
		Span	selectionSpan, visibleSpan;

		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			spanLength	= span.getLength();
			visibleSpan = doc.timeline.getVisibleSpan();
			newLength   = doc.timeline.getLength() - spanLength;
			if( visibleSpan.getStop() > newLength ) {
				this.addEdit( TimelineVisualEdit.scroll( source, doc,
					new Span( Math.max( 0, newLength - visibleSpan.getLength() ), newLength )));
			}
			selectionSpan = doc.timeline.getSelectionSpan();
			if( selectionSpan.contains( span.getStart() )) {
				if( selectionSpan.contains( span.getStop() )) {
					this.addEdit( TimelineVisualEdit.select( source, doc,
						new Span( selectionSpan.getStart(), selectionSpan.getStop() - spanLength )));
				} else {
					this.addEdit( TimelineVisualEdit.select( source, doc,
						new Span( selectionSpan.getStart(), span.getStart() )));
				}
			} else if( selectionSpan.getStart() > span.getStart() ) {
				this.addEdit( TimelineVisualEdit.select( source, doc,
					new Span( selectionSpan.getStart() - spanLength, selectionSpan.getStop() - spanLength )));
			}
			position = doc.timeline.getPosition();
			if( span.contains( position )) {
				this.addEdit( TimelineVisualEdit.position( source, doc, span.getStart() ));
			} else if( position >= span.getStop() ) {
				this.addEdit( TimelineVisualEdit.position( source, doc, position - spanLength ));
			}
			this.addEdit( new EditSetTimelineLength( source, doc, newLength ));
			this.end();
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}

	public String getPresentationName()
	{
		return AbstractApplication.getApplication().getResourceString( "editRemoveTimeSpan" );
	}
}