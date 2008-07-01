/*
 *  EditInsertTimeSpan.java
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
 *  describes the insertion of a time span
 *  to the session's <code>Timeline</code>.
 *  Actually a <code>CompoundEdit</code>
 *  composed of <code>EditSetTimelineLength</code>,
 *  and <code>EditSetTimelineSelection</code> (optional).
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @see		UndoManager
 *  @see		EditSetTimelineLength
 *  @see		EditSetTimelineSelection
 *  @see		EditRemoveTimeSpan
 */
public class EditInsertTimeSpan
extends CompoundEdit
{
	/**
	 *  Create and perform the edit. When the beginning
	 *  of the timeline selection is after the point
	 *  of time span insertion, it is shifted by
	 *  <code>span.getLength()</code> to the back. If the
	 *  the point of time span insertion lies in the
	 *  timeline's selection span, the selection span is
	 *  extended in length by <code>span.getLength()</code>.
	 *
	 *  @param  source		who originated the edit
	 *  @param  doc			session into whose <code>Timeline</code> the span is
	 *						inserted
	 *  @param  span		the time span to insert. the insertion is made
	 *						at <code>span.getStart()</code> for the duration of
	 *						<code>span.getLength()</code>.
	 *						the timeline's selection span is possible modified in
	 *						the course of the performance of the edit.
	 *  @synchronization	waitExclusive on DOOR_TIME
	 */
	public EditInsertTimeSpan( Object source, Session doc, Span span )
	{
		super();
	
		long	length;
		Span	selectionSpan;

		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			length  = span.getLength();
			this.addEdit( new EditSetTimelineLength( source, doc, doc.timeline.getLength() + length ));
			if( doc.timeline.getPosition() > span.getStart() ) {
//				this.addEdit( new EditSetTimelinePosition( source, doc, doc.timeline.getPosition() + length ));
				this.addEdit( TimelineVisualEdit.position( source, doc, doc.timeline.getPosition() + length ));
			}
			selectionSpan = doc.timeline.getSelectionSpan();
			if( selectionSpan.contains( span.getStart() )) {
				this.addEdit( TimelineVisualEdit.select( source, doc,
					new Span( selectionSpan.getStart(), selectionSpan.getStop() + length )));
			} else if( selectionSpan.getStart() > span.getStart() ) {
				this.addEdit( TimelineVisualEdit.select( source, doc,
					new Span( selectionSpan.getStart() + length, selectionSpan.getStop() + length )));
			}
			this.end();
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}

	public String getPresentationName()
	{
		return AbstractApplication.getApplication().getResourceString( "editInsertTimeSpan" );
	}
}