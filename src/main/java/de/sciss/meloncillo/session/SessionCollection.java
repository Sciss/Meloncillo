package de.sciss.meloncillo.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.sciss.app.BasicEvent;
import de.sciss.meloncillo.util.MapManager;

public interface SessionCollection
extends SessionObject
{
	/**
	 *  Gets the session object at a given index
	 *  in the collection.
	 *
	 *  @param  index   index in the collection of all session objects
	 *  @return the session object at the given index
	 *
	 *  @see	List#get( int )
	 */
	public SessionObject get( int index );

	/**
	 *  Gets a list of all session objects in the collection
	 *  (i.e. a duplicate of the collection).
	 *
	 *  @return a list of all session objects. this is a copy
	 *			so that changes do not influence each other.
	 *			the elements (session objects) reference of course
	 *			the same objects.
	 *
	 *	@todo	should return Collections.immutableList instead
	 */
	public List getAll();

	/**
	 *  Tests if the collection is empty.
	 *
	 *  @return	<code>true</code> if the collection is empty
	 *
	 *  @see	java.util.Collection#isEmpty()
	 */
	public boolean isEmpty();
	
	/**
	 *  Gets the size of the session object collection.
	 *
	 *  @return	number of session objects in the collection
	 *  @see	java.util.Collection#size()
	 */
	public int size();

	/**
	 *  Queries the index of a session object in the collection.
	 *
	 *  @param  so		the session object to look up in the collection
	 *  @return the index in the collection or -1 if the session object was not
	 *			in the collection
	 *
	 *  @see	List#indexOf( Object )
	 */
	public int indexOf( SessionObject so );

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
	public SessionObject findByName( String name );
	
	/**
	 *  Tests if the collection contains a session object.
	 *
	 *  @param  so		the session object to look up
	 *  @return	<code>true</code> if the collection contains the
	 *					session object
	 *  @see			java.util.Collection#contains( Object )
	 */
	public boolean contains( SessionObject so );

	/**
	 *  Registers a <code>Listener</code>
	 *  which will be informed about changes of
	 *  the session object collection.
	 *
	 *  @param  listener	the <code>Listener</code> to register
	 *
	 *  @see	de.sciss.app.EventManager#addListener( Object )
	 */
	public void addListener( SessionCollection.Listener listener );

	/**
	 *  Unregisters a <code>Listener</code>
	 *  from receiving changes of
	 *  the session object collection.
	 *
	 *  @param  listener	the <code>Listener</code> to unregister
	 *  @see	de.sciss.app.EventManager#removeListener( Object )
	 */
	public void removeListener( SessionCollection.Listener listener );

	public void debugDump();
	
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
