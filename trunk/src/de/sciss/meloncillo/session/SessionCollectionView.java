/*
 *  SessionCollectionView.java
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

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;

import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class SessionCollectionView
extends AbstractSessionObject
implements SessionCollection, EventManager.Processor
{
	private final List					collObjects			= new ArrayList();
//	private final MapManager.Listener	objectListener;
	
	// --- event handling ---

	private EventManager elm = null;	// lazy
	
	private final SessionCollection				full;
	private final SessionCollection.Listener	fullListener;

	/**
	 *  Creates a new empty collection.
	 */
	public SessionCollectionView( SessionCollection full, final Filter filter )
	{
		super();
		
		this.full	= full;
//		this.filter	= filter;
	
//		objectListener = new MapManager.Listener() {
//			public void mapChanged( MapManager.Event e )
//			{
//				dispatchObjectMapChange( e );
//			}
//
//			public void mapOwnerModified( MapManager.Event e )
//			{
//				dispatchObjectMapChange( e );
//			}
//		};
		
		fullListener = new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				final List c;
				
				switch( e.getModificationType() ) {
				case SessionCollection.Event.ACTION_ADDED:
					c	= e.getCollection();
					for( int i = c.size() - 1; i >= 0; i-- ) {
						final SessionObject so = (SessionObject) c.get( i );
						if( !filter.select( so )) {
							c.remove( i );	// we are allowed to modify c
						}
					}
//					System.out.println( " View (" + SessionCollectionView.this + ") : ACTION_ADDED " + c.size() );
					if( !c.isEmpty() ) {
						collObjects.addAll( c );
						dispatchCollectionChange( e.getSource(), c, e.getModificationType() );
					}
					break;
					
				case SessionCollection.Event.ACTION_REMOVED:
					c	= e.getCollection();
					for( int i = c.size() - 1; i >= 0; i-- ) {
						final SessionObject so = (SessionObject) c.get( i );
						if( !filter.select( so )) {
							c.remove( i );	// we are allowed to modify c
						}
					}
//					System.out.println( " View (" + SessionCollectionView.this + ") : ACTION_REMOVED " + c.size() );
					if( !c.isEmpty() ) {
						collObjects.removeAll( c );
						dispatchCollectionChange( e.getSource(), c, e.getModificationType() );
					}
					break;
					
				default:
					break;
				}
			}
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( (elm != null) && collObjects.contains( e.getCollection().get( 0 ))) {
					elm.dispatchEvent( e );
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e )
			{
				if( (elm != null) && collObjects.contains( e.getCollection().get( 0 ))) {
					elm.dispatchEvent( e );
				}
			}
		};
		full.addListener( fullListener );
	}
		
	public void dispose()
	{
		full.removeListener( fullListener );
		collObjects.clear();
//		clear( null );
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
		return BasicSessionCollection.findByName( collObjects, name );
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
//		System.out.println( " View (" + this + ") : addListener " + listener );
		
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		
		if( elm == null ) {
			elm = new EventManager( this );
		}
		elm.addListener( listener );
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
//		System.out.println( " View (" + this + ") : removeListener " + listener );
		
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		if( elm != null ) elm.removeListener( listener );
	}

	/**
	 *  This is called by the EventManager
	 *  if new events are to be processed.
	 */
	public void processEvent( BasicEvent e )
	{
		SessionCollection.Listener listener;
		
		for( int i = 0; i < elm.countListeners(); i++ ) {
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
	private void dispatchCollectionChange( Object source, List affected, int type )
	{
		if( elm != null ) {
			final SessionCollection.Event e2 = new SessionCollection.Event( source, System.currentTimeMillis(), affected, type );
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

// -------------------------- internal classes --------------------------

	public interface Filter
	{
		public boolean select( SessionObject so );
	}
}
