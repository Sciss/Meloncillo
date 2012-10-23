/*
 *  EditSetSessionObjectName.java
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
 *		22-Jan-05	created from original EditSetReceiverName
 */

package de.sciss.meloncillo.edit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;
import de.sciss.meloncillo.session.SessionObject;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the renaming of a session object.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @see		UndoManager
 */
public class EditSetSessionObjectName
extends BasicUndoableEdit
{
	private Object				source;
	private final SessionObject	so;
	private final String		oldName, newName;

	/**
	 *  Create and perform this edit. This
	 *  invokes the <code>SessionObjectCollection.modified</code> method,
	 *  thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @param  source			who initiated the action
	 *  @param  doc				session object which contains
	 *							the session object
	 *  @param  so				the session object to be renamed
	 *  @param  name			the new name of the session object
	 *  @see	de.sciss.meloncillo.session.SessionCollection
	 *  @see	de.sciss.meloncillo.session.SessionCollection.Event
	 *  @synchronization		waitExclusive on doors
	 *  @warning				the serialization of session objects through
	 *							the session's xml file requires that
	 *							all session objects have different names(?).
	 */
	public EditSetSessionObjectName( Object source, SessionObject so, String name )
	{
		super();
		this.source			= source;
		this.so				= so;
		this.newName		= name;
		this.oldName		= so.getName();
	}

	public PerformableEdit perform()
	{
		so.setName( newName );
		so.getMap().dispatchOwnerModification( source, SessionObject.OWNER_RENAMED, newName );
		source = this;
		return this;
	}

	/**
	 *  Undo the edit.
	 *  Invokes the <code>SessionObjectCollection.modified</code>,
	 *  method, thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @synchronization	waitExlusive on doors.
	 */
	public void undo()
	{
		super.undo();
		so.setName( oldName );
		so.getMap().dispatchOwnerModification( source, SessionObject.OWNER_RENAMED, oldName );
	}
	
	/**
	 *  Redo the edit.
	 *  Invokes the <code>SessionObjectCollection.modified</code>,
	 *  method, thus dispatching a <code>SessionCollection.Event</code>.
	 *  The original event source is discarded.
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
		return getResourceString( "editSetSessionObjectName" );
	}
}