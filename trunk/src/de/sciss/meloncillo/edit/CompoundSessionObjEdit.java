/*
 *  SyncCompoundSessionObjEdit.java
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
 *		03-Aug-04   provides door param
 *		22-Jan-05	rewritten as successor of SyncCompoundTransmitterEdit
 *		26-May-05	renamed to SyncCompoundSessionObjEdit for 31-characters filename limit
 */

package de.sciss.meloncillo.edit;

import java.util.ArrayList;
import java.util.List;

import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionObject;

/**
 *  This subclass of <code>SyncCompoundEdit</code> is used
 *  to synchronize an Undo or Redo operation of compound
 *  transmitter (or trajectory) modifying edits.
 *  The synchronization is provided by waiting exclusively
 *  for a given door.
 *
 *  @author			Hanns Holger Rutz
 *  @version		0.75, 10-Jun-08
 *  @see			UndoManager
 *  @see			de.sciss.meloncillo.util.LockManager
 */
public class CompoundSessionObjEdit
extends BasicCompoundEdit
{
	private Object					source;
	private final List				collSessionObjects;
	private final int				ownerModType;
	private final Object			ownerModParam, ownerUndoParam;

	/**
	 *  Creates a <code>CompoundEdit</code> objekt, whose Undo/Redo
	 *  actions are synchronized. When the edit gets finished
	 *  by calling the <code>end</code> method, the
	 *  <code>transmitterCollection.modifiedAll</code> method is called,
	 *  thus dispatching a <code>transmitterCollectionEvent</code>.
	 *
	 *  @param  source				Event-Source for <code>doc.transmitterCollection.modified</code>.
	 *								Gets discarded upon undo / redo invocation.
	 *  @param  doc					This Session's <code>bird</code> <code>LockManager</code>
	 *								is used as the locking instance.
	 *  @param  collSessionObjects	list of transmitters to be edited.
	 *	@param	ownerModType		XXX
	 *	@param	ownerModParam		XXX
	 *	@param	ownerUndoParam		XXX
	 *  @param  doors				doors to use for locking, usually
	 *								Session.DOOR_TRNS or Session.DOOR_TIMETRNSMTE
	 *
	 *  @see	de.sciss.meloncillo.util.LockManager
	 *  @see	de.sciss.meloncillo.session.SessionCollection
	 *  @see	de.sciss.meloncillo.session.SessionCollection.Event
	 *
	 *  @synchronization			waitExclusive on the given doors
	 */
	public CompoundSessionObjEdit( Object source, Session doc,
								   List collSessionObjects, int ownerModType,
								   Object ownerModParam, Object ownerUndoParam, int doors )
	{
		super(); // super( doc.bird, doors );
		
		this.source				= source;
		this.collSessionObjects = new ArrayList( collSessionObjects );
		this.ownerModType		= ownerModType;
		this.ownerModParam		= ownerModParam;
		this.ownerUndoParam		= ownerUndoParam;
	}
	
	/**
	 *  calls <code>doc.transmitterCollection.modifiedAll</code>.
	 *  The original edit source is discarded.
	 */
	protected void undoDone()
	{
		int i;
		
		for( i = 0; i < collSessionObjects.size(); i++ ) {
			((SessionObject) collSessionObjects.get( i )).getMap().dispatchOwnerModification(
				source, ownerModType, ownerUndoParam );
		}
	}

	/**
	 *  calls <code>doc.transmitterCollection.modifiedAll</code>.
	 *  The original edit source is discarded.
	 */
	protected void redoDone()
	{
		int i;
		
		for( i = 0; i < collSessionObjects.size(); i++ ) {
			((SessionObject) collSessionObjects.get( i )).getMap().dispatchOwnerModification(
				source, ownerModType, ownerModParam );
		}
	}

	protected void cancelDone() {}
	
	/**
	 *  Finishes the compound edit and calls
	 *  <code>doc.transmitterCollection.modifiedAll</code>
	 *  using the source provided in the constructor.
	 *
	 *  @synchronization	the caller must
	 *						block all <code>addEdit</code>
	 *						and the <code>end</code> call
	 *						into a sync block itself!
	 */
	public void end()
	{
		super.end();
		redoDone();
		source  = this;
	}
}