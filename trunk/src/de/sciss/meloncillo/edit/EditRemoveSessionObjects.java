/*
 *  EditRemoveSessionObjects.java
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
 *		22-Jan-05	created from EditRemoveReceivers
 */

package de.sciss.meloncillo.edit;

import java.util.ArrayList;
import java.util.List;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionCollection;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the removal of receivers
 *  from the session.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @see		UndoManager
 *  @see		EditAddSessionObjects
 */
public class EditRemoveSessionObjects
extends BasicUndoableEdit
{
	private final Session			doc;
	private final List				collSessionObjects;
	private Object					source;
	private final int				doors;
	private final SessionCollection	quoi;

	/**
	 *  Create and perform this edit. This
	 *  invokes the <code>SessionObjectCollection.addAll</code> method,
	 *  thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @param  source				who initiated the action
	 *  @param  doc					session object to which the
	 *								receivers are added
	 *  @param  collSessionObjects  collection of objects to
	 *								remove from the session.
	 *  @see	de.sciss.meloncillo.session.SessionCollection
	 *  @see	de.sciss.meloncillo.session.SessionCollection.Event
	 *  @synchronization		waitExclusive on doors
	 */
	public EditRemoveSessionObjects( Object source, Session doc, SessionCollection quoi,
									 List collSessionObjects, int doors )
	{
		super();
		this.source				= source;
		this.doc				= doc;
		this.collSessionObjects	= new ArrayList( collSessionObjects );
		this.doors				= doors;
		this.quoi				= quoi;
//		perform();
//		this.source				= this;
	}

	public PerformableEdit perform()
	{
		try {
			doc.bird.waitExclusive( doors );
			quoi.removeAll( source, collSessionObjects );
			source = this;
		}
		finally {
			doc.bird.releaseExclusive( doors );
		}
		return this;
	}

	/**
	 *  Undo the edit
	 *  by calling the <code>SessionObjectCollection.addAll</code> method,
	 *  thus dispatching a <code>SessionCollection.Event</code>
	 *
	 *  @synchronization	waitExclusive on doors
	 */
	public void undo()
	{
		super.undo();
		try {
			doc.bird.waitExclusive( doors );
			quoi.addAll( source, collSessionObjects );
		}
		finally {
			doc.bird.releaseExclusive( doors );
		}
	}
	
	/**
	 *  Redo the add operation.
	 *  The original source is discarded
	 *  which means, that, since a new <code>SessionCollection.Event</code>
	 *  is dispatched, even the original object
	 *  causing the edit will not know the details
	 *  of the action, hence thorougly look
	 *  and adapt itself to the new edit.
	 *
	 *  @synchronization	waitExclusive on doors
	 */
	public void redo()
	{
		super.redo();
		perform();
	}

	public String getPresentationName()
	{
		return getResourceString( "editRemoveSessionObjects" );
	}
}