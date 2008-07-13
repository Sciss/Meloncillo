/*
 *  SessionCollection.java
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
 *		13-May-05	created from de.sciss.meloncillo.session.SessionCollection
 *		27-Jan-06	allows null sources ; lazy EventManager creation
 *		13-Jul-08	copied back from EisK
 */

package de.sciss.meloncillo.session;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;
import de.sciss.meloncillo.util.MapManager;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.71, 13-Jul-08
 */
public class SessionCollection
extends AbstractSessionObject
implements EventManager.Processor
{
	protected final List			collObjects			= new ArrayList();
	protected final MapManager.Listener	objectListener;
	
//	private final Set	dynamicSet	= new HashSet();

	// --- event handling ---

	protected EventManager elm = null;	// lazy

	/**
	 *  Creates a new empty collection.
	 */
	public SessionCollection()
	{
		super();
	
		objectListener = new MapManager.Listener() {
			public void mapChanged( MapManager.Event e )
			{
				dispatchObjectMapChange( e );
			}

			public void mapOwnerModified( MapManager.Event e )
			{
				dispatchObjectMapChange( e );
			}
		};
	}
	
//	public void addDynamic( Object source, Object dynamic )
//	{
//		boolean result = dynamicSet.add( dynamic );
//		if( source != null && result ) {
//			dispatchObjectMapChange( MapManger.Event e );
//		}
//	}
//
//	public void removeDynamic( Object source, Object dynamic )
//	{
//		boolean result = dynamicSet.remove( dynamic );
//		if( source != null && result ) {
//			dispatchObjectMapChange( MapManger.Event e );
//		}
//	}
	
	public void dispose()
	{
		clear( null );
		super.dispose();
	}

	/**
	 *  Pauses dispatching of <code>SessionCollection.Event</code>s.
	 *
	 *  @see	de.sciss.app.EventManager#pause()
	 */
	public void pauseDispatcher()
	{
		if( elm != null ) elm.pause();
	}

	/**
	 *  Resumes dispatching of <code>SessionCollection.Event</code>s.
	 *
	 *  @see	de.sciss.app.EventManager#resume()
	 */
	public void resumeDispatcher()
	{
		if( elm != null ) elm.resume();
	}

	/**
	 *  Gets the session object at a given index
	 *  in the collection.
	 *
	 *  @param  index   index in the collection of all session objects
	 *  @return the session object at the given index
	 *
	 *  @see	List#get( int )
	 */
	public SessionObject get( int index )
	{
		return (SessionObject) collObjects.get( index );
	}

	/**
	 *  Gets a list of all session objects in the collection
	 *  (i.e. a duplicate of the collection).
	 *
	 *  @return a list of all session objects. this is a copy
	 *			so that changes do not influence each other.
	 *			the elements (session objects) reference of course
	 *			the same objects.
	 */
	public List getAll()
	{
		return new ArrayList( collObjects );
	}

	/**
	 *  Adds a new session object to the tail of the collection.
	 *  Fires a <code>SessionCollection.Event</code>
	 *  (<code>CHANGED</code>).
	 *
	 *  @param  source  source of the fired event or null
	 *					if no event shall be generated
	 *  @param  so		the session object to be added
	 *
	 *  @see	SessionCollection.Event#COLLECTION_CHANGED
	 *  @see	SessionCollection.Event#ACTION_ADDED
	 *  @see	java.util.Collection#add( Object )
	 */
	public void add( Object source, SessionObject so )
	{
		collObjects.add( so );
		so.getMap().addListener( objectListener );
		if( source != null ) {
			dispatchCollectionChange( source, Collections.singletonList( so ), Event.ACTION_ADDED );
		}
	}

	/**
	 *  Adds a new session object to the tail of the collection.
	 *  Fires a <code>SessionCollection.Event</code>
	 *  (<code>CHANGED</code>).
	 *
	 *  @param  source  source of the fired event or null
	 *					if no event shall be generated
	 *  @param  so		the session object to be added
	 *
	 *  @see	SessionCollection.Event#COLLECTION_CHANGED
	 *  @see	SessionCollection.Event#ACTION_ADDED
	 *  @see	java.util.Collection#add( Object )
	 */
	public void add( Object source, int idx, SessionObject so )
	{
		collObjects.add( idx, so );
		so.getMap().addListener( objectListener );
		if( source != null ) {
			dispatchCollectionChange( source, Collections.singletonList( so ), Event.ACTION_ADDED );
		}
	}

	/**
	 *  Adds a list of session objects to the tail of the collection.
	 *  Fires a <code>SessionCollectionEvent</code>
	 *  (<code>CHANGED</code>) if the collection changed as a
	 *  result of the call.
	 *
	 *  @param  source  source of the fired event or null
	 *					if no event shall be generated
	 *  @param  c		the collection of session objects to be added
	 *					(may be empty)
	 *  @return true	if the collection changed as a result of the call.
	 *
	 *  @see	SessionCollection.Event#COLLECTION_CHANGED
	 *  @see	SessionCollection.Event#ACTION_ADDED
	 *  @see	java.util.Collection#addAll( Collection )
	 */
	public boolean addAll( Object source, List c )
	{
		final boolean result = collObjects.addAll( c );
		if( result ) {
			for( int i = 0; i < c.size(); i++ ) {
				((SessionObject) c.get( i )).getMap().addListener( objectListener );
			}
			if( source != null ) dispatchCollectionChange( source, c, Event.ACTION_ADDED );
		}
		return result;
	}

	/**
	 *  Removes a session object from the collection.
	 *  Fires a <code>SessionCollectionEvent</code>
	 *  (<code>CHANGED</code>) if the collection
	 *  contained the session object.
	 *
	 *  @param  source  source of the fired event or null
	 *					if no event shall be generated
	 *  @param  so		the session object to be removed
	 *  @return true	if the collection contained the session object
	 *
	 *  @see	SessionCollection.Event#COLLECTION_CHANGED
	 *  @see	SessionCollection.Event#ACTION_REMOVED
	 *  @see	java.util.Collection#remove( Object )
	 */
	public boolean remove( Object source, SessionObject so )
	{
		final boolean result = collObjects.remove( so );
		if( result ) {
			so.getMap().removeListener( objectListener );
			if( source != null ) {
				dispatchCollectionChange( source, Collections.singletonList( so ), Event.ACTION_REMOVED );
			}
		}
		return result;
	}

	/**
	 *  Removes a list of session objects from the collection.
	 *  Fires a <code>SessionCollectionEvent</code>
	 *  (<code>CHANGED</code>) if the collection changed as a
	 *  result of the call.
	 *
	 *  @param  source  source of the fired event or null
	 *					if no event shall be generated
	 *  @param  c		the collection of session objects to be removed
	 *					(may be empty)
	 *  @return true	if the collection changed as a result of the call.
	 *
	 *  @see	SessionCollection.Event#COLLECTION_CHANGED
	 *  @see	SessionCollection.Event#ACTION_REMOVED
	 *  @see	java.util.Collection#removeAll( Collection )
	 */
	public boolean removeAll( Object source, List c )
	{
		boolean result = collObjects.removeAll( c );
		if( result ) {
			for( int i = 0; i < c.size(); i++ ) {
				((SessionObject) c.get( i )).getMap().removeListener( objectListener );
			}
			if( source != null ) dispatchCollectionChange( source, c, Event.ACTION_REMOVED );
		}
		return result;
	}

	/**
	 *  Tests if the collection contains a session object.
	 *
	 *  @param  so		the session object to look up
	 *  @return	<code>true</code> if the collection contains the
	 *					session object
	 *  @see			java.util.Collection#contains( Object )
	 */
	public boolean contains( SessionObject so )
	{
		return collObjects.contains( so );
	}

	/**
	 *  Queries the index of a session object in the collection.
	 *
	 *  @param  so		the session object to look up in the collection
	 *  @return the index in the collection or -1 if the session object was not
	 *			in the collection
	 *
	 *  @see	List#indexOf( Object )
	 */
	public int indexOf( SessionObject so )
	{
		return collObjects.indexOf( so );
	}
	
	/**
	 *  Tests if the collection is empty.
	 *
	 *  @return	<code>true</code> if the collection is empty
	 *
	 *  @see	java.util.Collection#isEmpty()
	 */
	public boolean isEmpty()
	{
		return collObjects.isEmpty();
	}
	
	/**
	 *  Gets the size of the session object collection.
	 *
	 *  @return	number of session objects in the collection
	 *  @see	java.util.Collection#size()
	 */
	public int size()
	{
		return collObjects.size();
	}

	/**
	 *  Removes all session objects from the collection.
	 *  Fires a <code>SessionCollectionEvent</code>
	 *  (<code>CHANGED</code>) if the collection
	 *  was not empty.
	 *
	 *  @param  source  source of the fired event or null
	 *					if no event shall be generated
	 *
	 *  @see	SessionCollection.Event#COLLECTION_CHANGED
	 *  @see	SessionCollection.Event#ACTION_REMOVED
	 *  @see	java.util.Collection#clear()
	 */
	public void clear( Object source )
	{
		if( !isEmpty() ) {
			final List c = (source == null) ? null : getAll();
			for( int i = 0; i < collObjects.size(); i++ ) {
				((SessionObject) collObjects.get( i )).getMap().removeListener( objectListener );
			}
			collObjects.clear();
			if( source != null ) dispatchCollectionChange( source, c, Event.ACTION_REMOVED );
		}
	}

	// --- create a unique name for a new session object ---

	/**
	 *  Creates a unique new logical name for
	 *  a session object. This method formats the
	 *	given message format with the given arguments
	 *	and looks in the given collection if an object
	 *	exists with that name. If not, the formatted string
	 *	is returned. Otherwise, <code>args[0]</code> is
	 *	incremented by 1 and the procedure is repeated until
	 *	a unique name has been found. 
	 *
	 *	@param	ptrn		the message format used to create
	 *						versions of a name
	 *	@param	args		argument array for the message format.
	 *						<code>args[0]</code> <strong>MUST</strong>
	 *						be a <code>Number</code> object and will
	 *						be replaced by this method, if the initial
	 *						name already existed.
	 *  @param  theseNot	a list of session objects whose names
	 *						are forbidden to be returned by this method.
	 *
	 *  @return	a synthesized name for a new session object which
	 *			is guaranteed to be not used by any of the session objects
	 *			in the given collection <code>theseNot</code>.
	 *			<code>args[0]</code> contains the next index of
	 *			iterative calling of this method.
	 */
	public static String createUniqueName( MessageFormat ptrn, Object[] args, List theseNot )
	{
		final StringBuffer	strBuf	= new StringBuffer();
		int					i		= ((Number) args[ 0 ]).intValue();
		String				name;
		
		do {
			strBuf.setLength( 0 );
			name		= ptrn.format( args, strBuf, null ).toString();
			args[ 0 ]	= new Integer( ++i );
		} while( findByName( theseNot, name ) != null );
		
		return name;
	}

	/**
	 *  Looks up a session object by its name.
	 *  The search is case insensitive because
	 *  the name might be used for data storage and
	 *  the underlying file system might not distinguish
	 *  between upper and lower case file names!
	 *
	 *  @param  name	the name of the session object to find.
	 *  @return the session object or null if no session object by that
	 *			name exists in the current collection of all session objects.
	 *
	 *  @see	java.lang.String#equalsIgnoreCase( String )
	 */
	public SessionObject findByName( String name )
	{
		return findByName( collObjects, name );
	}

	public static SessionObject findByName( List coll, String name )
	{
		SessionObject	so;
	
		for( int i = 0; i < coll.size(); i++ ) {
			so = (SessionObject) coll.get( i );
			if( so.getName().equalsIgnoreCase( name )) return so;
		}
		return null;
	}

	// --- listener registration ---
	
	/**
	 *  Registers a <code>Listener</code>
	 *  which will be informed about changes of
	 *  the session object collection.
	 *
	 *  @param  listener	the <code>Listener</code> to register
	 *
	 *  @see	de.sciss.app.EventManager#addListener( Object )
	 */
	public void addListener( SessionCollection.Listener listener ) // , Set keySet, int mode )
	{
		synchronized( this ) {
			if( elm == null ) {
				elm = new EventManager( this );
			}
			elm.addListener( listener );
		}
	}

	/**
	 *  Unregisters a <code>Listener</code>
	 *  from receiving changes of
	 *  the session object collection.
	 *
	 *  @param  listener	the <code>Listener</code> to unregister
	 *  @see	de.sciss.app.EventManager#removeListener( Object )
	 */
	public void removeListener( SessionCollection.Listener listener )
	{
		if( elm != null ) elm.removeListener( listener );
	}

	/**
	 *  This is called by the EventManager
	 *  if new events are to be processed.
	 */
	public void processEvent( BasicEvent e )
	{
		SessionCollection.Listener listener;
		int i;
		
		for( i = 0; i < elm.countListeners(); i++ ) {
			listener = (SessionCollection.Listener) elm.getListener( i );
			switch( e.getID() ) {
			case SessionCollection.Event.COLLECTION_CHANGED:
				listener.sessionCollectionChanged( (SessionCollection.Event) e );
				break;
			case SessionCollection.Event.MAP_CHANGED:
				listener.sessionObjectMapChanged( (SessionCollection.Event) e );
				break;
			case SessionCollection.Event.OBJECT_CHANGED:
				listener.sessionObjectChanged( (SessionCollection.Event) e );
				break;
			default:
				assert false : e.getID();
			}
		} // for( i = 0; i < elm.countListeners(); i++ )
	}

	// utility function to create and dispatch a SessionObjectCollectionEvent
	protected void dispatchCollectionChange( Object source, List affected, int type )
	{
		if( elm != null ) {
			final Event e2 = new Event( source, System.currentTimeMillis(), affected, type );
			elm.dispatchEvent( e2 );
		}
	}

	// utility function to create and dispatch a SessionObjectCollectionEvent
	protected void dispatchObjectMapChange( MapManager.Event e )
	{
		if( elm != null ) {
			final Event e2 = new Event( e.getSource(), System.currentTimeMillis(), e );
			elm.dispatchEvent( e2 );
		}
	}

	public void debugDump()
	{
		System.err.println( "Dumping "+this.getClass().getName() );
		for( int i = 0; i < collObjects.size(); i++ ) {
			System.err.println( "object "+i+" = "+collObjects.get( i ).toString() );
		}
//		elm.debugDump();
	}

// ---------------- SessionObject interface ---------------- 

	/**
	 *  This simply returns <code>null</code>!
	 */
	public Class getDefaultEditor()
	{
		return null;
	}

// -------------------------- inner Event class --------------------------

	// XXX TO-DO : Event should have a getDocumentCollection method
	// XXX TO-DO : Event should have indices of all elements
	public static class Event
	extends BasicEvent
	{
	// --- ID values ---

		/**
		 *  returned by getID() : the collection was changed by
		 *  adding or removing elements
		 */
		public static final int COLLECTION_CHANGED	= 0;
		/**
		 *  returned by getID() : the collection elements have
		 *  been modified, e.g. resized
		 */
		public static final int MAP_CHANGED		= 1;
		/**
		 *  returned by getID() : the collection elements have
		 *  been modified, e.g. resized
		 */
		public static final int OBJECT_CHANGED		= 2;

		public static final int ACTION_ADDED		= 0;
		public static final int ACTION_REMOVED		= 1;
		public static final int ACTION_CHANGED		= 2;

		private final List				affectedColl;
		private final int				affectedType;
		private final Set				affectedSet;
		private final Object			affectedParam;

		/**
		 *  Constructs a new <code>SessionObjectCollectionEvent</code>.
		 *
		 *  @param  source		who originated the action / event
		 *  @param  ID			one of <code>CHANGED</code>, <code>TRANSFORMED</code>
		 *						or <code>SELECTED</code>.
		 *  @param  when		system time when the event occured
		 *  @param  actionID	unused, must be zero at the moment
		 *  @param  actionObj	currently unused (provide null)
		 */
		protected Event( Object source, long when, List affectedColl, int type )
		{
			super( source, COLLECTION_CHANGED, when );
		
			this.affectedColl	= new ArrayList( affectedColl );
			this.affectedType	= type;
			this.affectedParam	= null;
			this.affectedSet	= Collections.EMPTY_SET;
		}
		
		protected Event( Event superEvent, List affectedColl )
		{
			super( superEvent.getSource(), superEvent.getID(), superEvent.getWhen() );
			
			this.affectedColl	= new ArrayList( affectedColl );
			this.affectedType	= superEvent.getModificationType();
			this.affectedParam	= getModificationParam();
			this.affectedSet	= new HashSet( superEvent.affectedSet );
		}
		
		protected Event( Object source, long when, MapManager.Event e )
		{
			super( source, e.getID() == MapManager.Event.MAP_CHANGED ? MAP_CHANGED : OBJECT_CHANGED, when );

			this.affectedColl	= Collections.singletonList( e.getOwner() );

			if( getID() == MAP_CHANGED ) {
				this.affectedType	= ACTION_CHANGED;
				this.affectedSet	= e.getPropertyNames();
				this.affectedParam	= null;
			} else {
				this.affectedType	= e.getOwnerModType();
				this.affectedParam	= e.getOwnerModParam();
				this.affectedSet	= Collections.EMPTY_SET;
			}
		}

		public List getCollection()
		{
			return new ArrayList( affectedColl );
		}

		public boolean collectionContains( SessionObject so )
		{
			return affectedColl.contains( so );
		}

		public boolean collectionContainsAny( List coll )
		{
			for( int i = 0; i < coll.size(); i++ ) {
				if( affectedColl.contains( coll.get( i ))) return true;
			}
			return false;
		}

		public boolean setContains( String key )
		{
			return( affectedSet.contains( key ));
		}

		public boolean setContainsAny( List coll )
		{
			for( int i = 0; i < coll.size(); i++ ) {
				if( affectedSet.contains( coll.get( i ))) return true;
			}
			return false;
		}

		public int getModificationType()
		{
			return affectedType;
		}

		public Object getModificationParam()
		{
			return affectedParam;
		}
		
		public boolean incorporate( BasicEvent oldEvent )
		{
//			if( oldEvent instanceof Event &&
//				this.getSource() == oldEvent.getSource() &&
//				this.getID() == oldEvent.getID() ) {
//				
//				// XXX beware, when the actionID and actionObj
//				// are used, we have to deal with them here
//				
//				return true;
//
//			} else return false;
			return false;	// XXX for now
		}
	}

// -------------------------- inner Listener interface --------------------------

	public interface Listener
	extends EventListener
	{
		/**
		 *  Invoked when the collection was changed by
		 *  adding or removing elements
		 *
		 *  @param  e   the event describing
		 *				the collection change
		 */
		public void sessionCollectionChanged( SessionCollection.Event e );

		public void sessionObjectMapChanged( SessionCollection.Event e );

		public void sessionObjectChanged( SessionCollection.Event e );
	}
}
