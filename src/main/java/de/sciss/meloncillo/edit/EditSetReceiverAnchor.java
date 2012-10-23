/*
 *  EditSetReceiverAnchor.java
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
 *		15-Mar-05	created from EditSetReceiverBounds
 */

package de.sciss.meloncillo.edit;

import java.awt.geom.Point2D;

import javax.swing.undo.UndoableEdit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;
import de.sciss.meloncillo.receiver.Receiver;
import de.sciss.meloncillo.session.Session;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the positioning of a receiver.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @see		UndoManager
 *  @todo		addEdit() and replaceEdit()
 *				should accept CompoundEdits
 *				comprised of EditSetReceiverAnchor
 *				edits of different receivers.
 */
public class EditSetReceiverAnchor
extends BasicUndoableEdit
{
	private final Session		doc;
	private final Receiver		rcv;
	private Point2D				oldAnchor, newAnchor;

	/**
	 *  Create and perform this edit. This
	 *  invokes the <code>ReceiverCollection.modified</code> method,
	 *  thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @param  source			who initiated the action
	 *  @param  doc				session object which contains
	 *							the receiver
	 *  @param  rcv				the receivers to be resized
	 *  @param  anchor			the new anchor of the receiver
	 *  @see	de.sciss.meloncillo.session.SessionCollection
	 *  @see	de.sciss.meloncillo.session.SessionCollection.Event
	 *  @synchronization		waitExclusive on DOOR_RCV
	 */
	public EditSetReceiverAnchor( Object source, Session doc, Receiver rcv, Point2D anchor )
	{
		super();
		this.doc			= doc;
		this.rcv			= rcv;
		this.newAnchor		= anchor;
		this.oldAnchor		= rcv.getAnchor();
//		perform();
	}

	public PerformableEdit perform()
	{
		try {
			doc.bird.waitExclusive( Session.DOOR_RCV );
			rcv.setAnchor( newAnchor );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_RCV );
		}
		return this;
	}

	/**
	 *  Undo the edit.
	 *  Invokes the <code>ReceiverCollection.modified</code>,
	 *  method, thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @synchronization	waitExlusive on DOOR_RCV.
	 */
	public void undo()
	{
		super.undo();
		try {
			doc.bird.waitExclusive( Session.DOOR_RCV );
			rcv.setAnchor( oldAnchor );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_RCV );
		}
	}
	
	/**
	 *  Redo the edit.
	 *  Invokes the <code>ReceiverCollection.modified</code>,
	 *  method, thus dispatching a <code>SessionCollection.Event</code>.
	 *  The original event source is discarded.
	 *
	 *  @synchronization	waitExlusive on DOOR_RCV.
	 */
	public void redo()
	{
		super.redo();
		perform();
	}

	/**
	 *  Collapse multiple successive EditSetReceiverAnchor edit
	 *  into one single edit. The new edit is sucked off by
	 *  the old one.
	 */
	public boolean addEdit( UndoableEdit anEdit )
	{
		if( (anEdit instanceof EditSetReceiverAnchor) &&
			this.rcv == ((EditSetReceiverAnchor) anEdit).rcv ) {
			
			this.newAnchor  = ((EditSetReceiverAnchor) anEdit).newAnchor;
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	/**
	 *  Collapse multiple successive EditSetReceiverAnchor edit
	 *  into one single edit. The old edit is sucked off by
	 *  the new one.
	 */
	public boolean replaceEdit( UndoableEdit anEdit )
	{
		if( (anEdit instanceof EditSetReceiverAnchor) &&
			this.rcv == ((EditSetReceiverAnchor) anEdit).rcv ) {

			this.oldAnchor  = ((EditSetReceiverAnchor) anEdit).oldAnchor;
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	public String getPresentationName()
	{
		return getResourceString( "editSetReceiverAnchor" );
	}
}