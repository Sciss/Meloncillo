/*
 *  EditSetTimelineScroll.java
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
 *		29-Jul-04   commented
 */

package de.sciss.meloncillo.edit;

import javax.swing.undo.*;

import de.sciss.meloncillo.session.*;

import de.sciss.app.*;
import de.sciss.io.*;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the modification of the
 *  visible time span in the <code>TimelineFrame</code>.
 *  There seems to be no use for making
 *  the scroll operation an undoable
 *  edit, but it becomes important as
 *  part of a compound edit that describes
 *  the modification of the timeline
 *  by removing or inserting time spans
 *  or changing the sample rate.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @see		UndoManager
 *  @see		EditRemoveTimeSpan
 *  @see		de.sciss.meloncillo.timeline.TimelineFrame
 */
public class EditSetTimelineScroll
extends BasicUndoableEdit
{
	private Object			source;
	private final Session   doc;
	private Span			oldSpan, newSpan;

	/**
	 *  Create and perform the edit. This method
	 *  invokes the <code>Timeline.setVisibleSpan</code> method,
	 *  thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @param  source		who originated the edit. the source is
	 *						passed to the <code>Timeline.setVisibleSpan</code> method.
	 *  @param  doc			session into whose <code>Timeline</code> is
	 *						to be scrolled.
	 *  @param  span		the new timeline visible span.
	 *  @synchronization	waitExclusive on DOOR_TIME
	 */
	public EditSetTimelineScroll( Object source, Session doc, Span span )
	{
		super();
		this.source		= source;
		this.doc		= doc;

		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			this.oldSpan = doc.timeline.getVisibleSpan();
			this.newSpan = span;
			doc.timeline.setVisibleSpan( source, span );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}

		this.source		= this;
	}

	/**
	 *  @return		false to tell the UndoManager it should not feature
	 *				the edit as a single undoable step in the history.
	 *				which is especially important since <code>TimelineScroll</code>
	 *				will generate lots of edits when the user scrolls
	 *				the timeline view.
	 */
	public boolean isSignificant()
	{
		return false;
	}

	/**
	 *  Undo the edit
	 *  by calling the <code>Timeline.setVisibleSpan</code>,
	 *  method, thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @synchronization	waitExlusive on DOOR_TIME.
	 */
	public void undo()
	{
		super.undo();
		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			doc.timeline.setVisibleSpan( source, oldSpan );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}
	
	/**
	 *  Redo the edit. The original source is discarded
	 *  which means, that, since a new <code>TimelineEvent</code>
	 *  is dispatched, even the original object
	 *  causing the edit will not know the details
	 *  of the action, hence thoroughly look
	 *  and adapt itself to the new edit.
	 *
	 *  @synchronization	waitExlusive on DOOR_TIME.
	 */
	public void redo()
	{
		super.redo();
		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			doc.timeline.setVisibleSpan( source, newSpan );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}
	
	/**
	 *  Collapse multiple successive edits
	 *  into one single edit. The new edit is sucked off by
	 *  the old one.
	 */
	public boolean addEdit( UndoableEdit anEdit )
	{
		if( anEdit instanceof EditSetTimelineScroll ) {
			this.newSpan = ((EditSetTimelineScroll) anEdit).newSpan;
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	/**
	 *  Collapse multiple successive edits
	 *  into one single edit. The old edit is sucked off by
	 *  the new one.
	 */
	public boolean replaceEdit( UndoableEdit anEdit )
	{
		if( anEdit instanceof EditSetTimelineScroll ) {
			this.oldSpan = ((EditSetTimelineScroll) anEdit).oldSpan;
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	public String getPresentationName()
	{
		return getResourceString( "editSetTimelineScroll" );
	}
}