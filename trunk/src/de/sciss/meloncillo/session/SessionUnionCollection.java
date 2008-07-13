/*
 *  SessionUnionCollection.java
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
 *		03-Feb-05	created
 */

package de.sciss.meloncillo.session;

import de.sciss.meloncillo.util.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class SessionUnionCollection
extends SessionCollection
{
	public static final int RECEIVERS		= 0;
	public static final int TRANSMITTERS	= 1;

	private final SessionGroup			allGroup;
	private final SessionCollection		selectedGroups;
	private final Listener				selectedGroupsListener, eachGroupListener;
	private final LockManager			lm;
	private final int					doors;
	private final int					type;
	private boolean allGroupSelected;

	public SessionUnionCollection( SessionGroup allGroupL, SessionCollection selectedGroupsL, int typeL,
								   LockManager lmL, int doorsL )
	{
		this.allGroup		= allGroupL;
		this.selectedGroups	= selectedGroupsL;
		this.lm				= lmL;
		this.doors			= doorsL;
		this.type			= typeL;
		
		SessionGroup group;
		int i;
		
		eachGroupListener = new Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				try {
					lm.waitExclusive( doors );
					updateUnionCollection( e );
				}
				finally {
					lm.releaseExclusive( doors );
				}
			}
			
			public void sessionObjectMapChanged( SessionCollection.Event e )
			{
				// XXX redispatch
			}
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				// XXX redispatch
			}
		};

		selectedGroupsListener = new Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				int				i;
				java.util.List	affectedGroups;
				SessionGroup	group;
			
				try {
					lm.waitExclusive( doors );
					switch( e.getModificationType() ) {
					case SessionCollection.Event.ACTION_ADDED:
						affectedGroups = e.getCollection();
						if( allGroupSelected && !affectedGroups.isEmpty() ) {
//System.err.println( "stop listen "+allGroup.getName() );
							getSessionCollection( allGroup ).removeListener( eachGroupListener );
							allGroupSelected = false;
						}
						for( i = 0; i < affectedGroups.size(); i++ ) {
							group = (SessionGroup) affectedGroups.get( i );
//System.err.println( "new listen "+group.getName() );
							getSessionCollection( group ).addListener( eachGroupListener );
						}
						updateUnionCollection( e );
						break;
						
					case SessionCollection.Event.ACTION_REMOVED:
						affectedGroups = e.getCollection();
						for( i = 0; i < affectedGroups.size(); i++ ) {
							group = (SessionGroup) affectedGroups.get( i );
//System.err.println( "stop listen "+group.getName() );
							getSessionCollection( group ).removeListener( eachGroupListener );
						}
						if( !allGroupSelected && selectedGroups.isEmpty() ) {
//System.err.println( "new listen "+allGroup.getName() );
							getSessionCollection( allGroup ).addListener( eachGroupListener );
							allGroupSelected = true;
						}
						updateUnionCollection( e );
						break;
						
					default:
						break;
					}
				}
				finally {
					lm.releaseExclusive( doors );
				}
			}
			
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
			public void sessionObjectChanged( SessionCollection.Event e ) {}
		};
		
		// initial listener installation
		try {
			lm.waitExclusive( doors );
			selectedGroups.addListener( selectedGroupsListener );
			allGroupSelected = selectedGroups.isEmpty();
			if( allGroupSelected ) {
//System.err.println( "new listen "+allGroup.getName() );
				getSessionCollection( allGroup ).addListener( eachGroupListener );
			} else {
				for( i = 0; i < selectedGroups.size(); i++ ) {
					group = (SessionGroup) selectedGroups.get( i );
//System.err.println( "new listen "+group.getName() );
					getSessionCollection( group ).addListener( eachGroupListener );
				}
			}
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}

	private SessionCollection getSessionCollection( SessionGroup group )
	{
		switch( type ) {
		case RECEIVERS:
			return group.getReceivers();
		case TRANSMITTERS:
			return group.getTransmitters();
		default:
			assert false : type;
			return null;
		}
	}

	// to be called with exclusive sync on 'doors'
	private void updateUnionCollection( SessionCollection.Event e )
	{
		java.util.List	coll, collNew, collRemoved;
		SessionGroup	group;
		int				i, j;
		Object			o;
				
		if( selectedGroups.isEmpty() ) {
			group	= allGroup;
		} else {
			group	= (SessionGroup) selectedGroups.get( 0 );
		}

		collNew = getSessionCollection( group ).getAll();
		for( i = 1; i < selectedGroups.size(); i++ ) {
			group	= (SessionGroup) selectedGroups.get( i );
			coll	= getSessionCollection( group ).getAll();
			for( j = 0; j < coll.size(); j++ ) {
				o	= coll.get( j );
				if( !collNew.contains( o )) collNew.add( o );
			}
		}
		
		// z.B.	collOld	= { B, C, D }
		//		collNew	= { A, B, C }
		// -->	collAdded	= collNew.removeAll( collOld ) = { A }
		// -->  collRemoved = collOld.removeAll( collNew ) = { D }
		
		collRemoved	= getAll();
		collRemoved.removeAll( collNew );
		collNew.removeAll( collObjects );

		if( !collRemoved.isEmpty() ) {
			collObjects.removeAll( collRemoved );
			for( i = 0; i < collRemoved.size(); i++ ) {
//System.err.println( "removed "+((SessionObject) collRemoved.get( i )).getName() );
				((SessionObject) collRemoved.get( i )).getMap().removeListener( objectListener );
			}
			dispatchCollectionChange( e.getSource(), collRemoved, Event.ACTION_REMOVED );
		}
		if( !collNew.isEmpty() ) {
			collObjects.addAll( collNew );
			for( i = 0; i < collNew.size(); i++ ) {
//System.err.println( "added "+((SessionObject) collNew.get( i )).getName() );
				((SessionObject) collNew.get( i )).getMap().addListener( objectListener );
			}
			dispatchCollectionChange( e.getSource(), collNew, Event.ACTION_ADDED );
		}
	}

//	/**
//	 *  Adds a new session object to the tail of the collection.
//	 *  Fires a <code>SessionCollectionEvent</code>
//	 *  (<code>CHANGED</code>).
//	 *
//	 *  @param  source  source of the fired event or null
//	 *					if no event shall be generated
//	 *  @param  so		the session object to be added
//	 *
//	 *  @see	SessionCollectionEvent#CHANGED
//	 *  @see	java.util.Collection#add( Object )
//	 */
//	public void add( Object source, SessionObject so )
//	{
//		collObjects.add( so );
//		so.getMap().addListener( objectListener );
//		java.util.List c = new ArrayList( 1 );
//		c.add( so );
//		dispatchCollectionChange( source, c, Event.ACTION_ADDED );
//	}
//
//	/**
//	 *  Adds a list of session objects to the tail of the collection.
//	 *  Fires a <code>SessionCollectionEvent</code>
//	 *  (<code>CHANGED</code>) if the collection changed as a
//	 *  result of the call.
//	 *
//	 *  @param  source  source of the fired event or null
//	 *					if no event shall be generated
//	 *  @param  c		the collection of session objects to be added
//	 *					(may be empty)
//	 *  @return true	if the collection changed as a result of the call.
//	 *
//	 *  @see	SessionCollectionEvent#CHANGED
//	 *  @see	java.util.Collection#addAll( Collection )
//	 */
//	public boolean addAll( Object source, java.util.List c )
//	{
//		boolean result = collObjects.addAll( c );
//		if( result ) {
//			for( int i = 0; i < c.size(); i++ ) {
//				((SessionObject) c.get( i )).getMap().addListener( objectListener );
//			}
//			dispatchCollectionChange( source, c, Event.ACTION_ADDED );
//		}
//		return result;
//	}
//
//	/**
//	 *  Removes a session object from the collection.
//	 *  Fires a <code>SessionCollectionEvent</code>
//	 *  (<code>CHANGED</code>) if the collection
//	 *  contained the session object.
//	 *
//	 *  @param  source  source of the fired event or null
//	 *					if no event shall be generated
//	 *  @param  so		the session object to be removed
//	 *  @return true	if the collection contained the session object
//	 *
//	 *  @see	SessionCollectionEvent#CHANGED
//	 *  @see	java.util.Collection#remove( Object )
//	 */
//	public boolean remove( Object source, SessionObject so )
//	{
//		boolean result = collObjects.remove( so );
//		if( result ) {
//			so.getMap().removeListener( objectListener );
//			java.util.List c = new ArrayList( 1 );
//			c.add( so );
//			dispatchCollectionChange( source, c, Event.ACTION_REMOVED );
//		}
//		return result;
//	}
//
//	/**
//	 *  Removes a list of session objects from the collection.
//	 *  Fires a <code>SessionCollectionEvent</code>
//	 *  (<code>CHANGED</code>) if the collection changed as a
//	 *  result of the call.
//	 *
//	 *  @param  source  source of the fired event or null
//	 *					if no event shall be generated
//	 *  @param  c		the collection of session objects to be removed
//	 *					(may be empty)
//	 *  @return true	if the collection changed as a result of the call.
//	 *
//	 *  @see	SessionCollectionEvent#CHANGED
//	 *  @see	java.util.Collection#removeAll( Collection )
//	 */
//	public boolean removeAll( Object source, java.util.List c )
//	{
//		boolean result = collObjects.removeAll( c );
//		if( result ) {
//			for( int i = 0; i < c.size(); i++ ) {
//				((SessionObject) c.get( i )).getMap().removeListener( objectListener );
//			}
//			dispatchCollectionChange( source, c, Event.ACTION_REMOVED );
//		}
//		return result;
//	}
//
//	/**
//	 *  Removes all session objects from the collection.
//	 *  Fires a <code>SessionCollectionEvent</code>
//	 *  (<code>CHANGED</code>) if the collection
//	 *  was not empty.
//	 *
//	 *  @param  source  source of the fired event or null
//	 *					if no event shall be generated
//	 *
//	 *  @see	SessionCollectionEvent#CHANGED
//	 *  @see	java.util.Collection#clear()
//	 */
//	public void clear( Object source )
//	{
//		if( !isEmpty() ) {
//			java.util.List c = getAll();
//			for( int i = 0; i < collObjects.size(); i++ ) {
//				((SessionObject) collObjects.get( i )).getMap().removeListener( objectListener );
//			}
//			collObjects.clear();
//			dispatchCollectionChange( source, c, Event.ACTION_REMOVED );
//		}
//	}

	public void dispose()
	{
		int i;
		SessionGroup group;
	
		if( lm.attemptExclusive( doors, 1000 )) {
			try {
				allGroupSelected = selectedGroups.isEmpty();
				if( allGroupSelected ) {
					getSessionCollection( allGroup ).removeListener( eachGroupListener );
				} else {
					for( i = 0; i < selectedGroups.size(); i++ ) {
						group = (SessionGroup) selectedGroups.get( i );
						getSessionCollection( group ).removeListener( eachGroupListener );
					}
				}
				selectedGroups.removeListener( selectedGroupsListener );
			}
			finally {
				lm.releaseExclusive( doors );
			}
		}

		super.dispose();
	}
}