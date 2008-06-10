/*
 *  EditSetTimelineLength.java
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

/**
 *  An <code>UndoableEdit</code> that
 *  describes the truncation or increment of
 *  to session's <code>Timeline</code> duration.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @see		UndoManager
 *  @see		EditInsertTimeSpan
 *  @see		EditRemoveTimeSpan
 */
public class EditSetTimelineLength
extends BasicUndoableEdit
{
	private Object			source;
	private final Session   doc;
	private final long 		oldLength, newLength;

	/**
	 *  Create and perform the edit. This is usually
	 *  not invoked directly by the application but as
	 *  part of the compound edits <code>EditInsertTimeSpan</code>
	 *  and <code>EditRemoveTimeSpan</code>. This method
	 *  doesn't take care of the timeline's selection or
	 *  visible span. It invokes the <code>Timeline.setLength</code> method,
	 *  thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @param  source		who originated the edit. the source is
	 *						passed to the <code>Timeline.setLength</code> method.
	 *  @param  doc			session into whose <code>Timeline</code> is
	 *						affected.
	 *  @param  length		the new timeline length in sample frames.
	 *  @synchronization	waitExclusive on DOOR_TIME
	 */
	public EditSetTimelineLength( Object source, Session doc, long length )
	{
		super();
		this.source			= source;
		this.doc			= doc;
		this.newLength		= length;
		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			this.oldLength	= doc.timeline.getLength();
			doc.timeline.setLength( source, newLength );
			this.source		= this;
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}

	/**
	 *  Undo the edit
	 *  by calling the <code>Timeline.setLength</code>,
	 *  method, thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @synchronization	waitExlusive on DOOR_TIME.
	 */
	public void undo()
	{
		super.undo();
		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			doc.timeline.setLength( source, oldLength );
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
			doc.timeline.setLength( source, newLength );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}

	public String getPresentationName()
	{
		return getResourceString( "editSetTimelineLength" );
	}
}