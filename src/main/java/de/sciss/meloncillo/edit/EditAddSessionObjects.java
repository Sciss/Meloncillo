/*
 *  EditAddSessionObjects.java
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
 *		22-Jan-05	created from EditAddReceivers
 */

package de.sciss.meloncillo.edit;

import java.util.ArrayList;
import java.util.List;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;
import de.sciss.meloncillo.session.MutableSessionCollection;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the adding of new receivers
 *  to the session.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @see		UndoManager
 *  @see		EditRemoveSessionObjects
 */
public class EditAddSessionObjects
extends BasicUndoableEdit
{
	private final List						collSessionObjects;
	private Object							source;
	private final MutableSessionCollection	quoi;

	/**
	 *  Create and perform this edit. This
	 *  invokes the <code>SessionObjectCollection.addAll</code> method,
	 *  thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @param  source			who initiated the action
	 *  @param  doc				session object to which the
	 *							receivers are added
	 *  @param  collSessionObjects   collection of receivers to
	 *							add to the session.
	 *  @see	de.sciss.meloncillo.session.SessionCollection
	 *  @see	de.sciss.meloncillo.session.SessionCollection.Event
	 *  @synchronization		waitExclusive on doors
	 */
	public EditAddSessionObjects( Object source, MutableSessionCollection quoi,
								  List collSessionObjects )
	{
		super();
		this.source				= source;
		this.quoi				= quoi;
		this.collSessionObjects = new ArrayList( collSessionObjects );
//		perform();
//		this.source				= this;
	}

	public PerformableEdit perform()
	{
		quoi.addAll( source, collSessionObjects );
		this.source = this;
		return this;
	}

	/**
	 *  Undo the edit
	 *  by calling the <code>SessionObjectCollection.removeAll</code>,
	 *  method, thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @synchronization	waitExlusive on doors.
	 */
	public void undo()
	{
		super.undo();
		quoi.removeAll( source, collSessionObjects );
	}
	
	/**
	 *  Redo the add operation.
	 *  The original source is discarded
	 *  which means, that, since a new <code>SessionCollection.Event</code>
	 *  is dispatched, even the original object
	 *  causing the edit will not know the details
	 *  of the action, hence thoroughly look
	 *  and adapt itself to the new edit.
	 *
	 *  @synchronization	waitExlusive on doors.
	 */
	public void redo()
	{
		super.redo();
		perform();
	}

	public String getPresentationName()
	{
		return getResourceString( "editAddSessionObjects" );
	}
}