/**
 *  LockManager.java
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
 *		09-Aug-04   commented
 *		15-Jul-05	bugfix in attemptShared() : if multiple exclusive locks were nested,
 *					the method would return successless after the inner-most nest
 *					exclusive lock was released. now, the delta-wait time is calculated
 *					and method carries on waiting!
 */

package de.sciss.meloncillo.util;

import java.util.*;

/**
 *  A LockManager keeps track of a number of doors.
 *  A door can be locked with one or more exclusive locks
 *  from the same owner (Thread) or any number of shared locks.
 *  <p>
 *  The safest way to prevent deadlocks is to use attempts
 *  whenever possible. The safest way to prevent "losing" keys
 *  in case a runtime exception is thrown inside the sync block,
 *  is to release doors in a finally clause, such as:
 *  <pre>
 *  try {
 *      lockManager.waitExclusive( theDoors );
 *      // ... perform quelquechoses ...
 *  }
 *  finally {
 *      lockManager.releaseExclusive( theDoors );
 *  }
 *  </pre>
 *  However, it was not tested yet, if the population of all the
 *  source codes with try / finally blocks will degrade performance.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 15-Jul-05
 *
 *  @see	de.sciss.meloncillo.session.Session#bird
 *  @see	de.sciss.meloncillo.session.Session#DOOR_RCV
 */
public class LockManager
{
	private final ArrayList[] sharedLocks;		// index is door ID, the vector elements class is Thread
	private final ArrayList[] exclusiveLocks;	// index is door ID, the vector elements class is Thread

	/**
	 *  Creates a new LockManager for
	 *  a specified number of doors.
	 *  Initially all doors are unlocked.
	 *
	 *  @param  numDoors	the maximum number of doors that can
	 *						be managed by this instance
	 */
	public LockManager( int numDoors )
	{
		sharedLocks		= new ArrayList[ numDoors ];
		exclusiveLocks  = new ArrayList[ numDoors ];
		for( int i = 0; i < numDoors; i++ ) {
			sharedLocks[i]		= new ArrayList( 10 );
			exclusiveLocks[i]   = new ArrayList( 5 );   // unlikely that more than five nested routines will need a door
		}
	}

	/**
	 *  Attempts to lock a door exclusively.
	 *  If the attempt is successful, no other
	 *  Thread can lock this door until the
	 *  owner releases the lock. The same thread
	 *  can attach both exclusive and shared locks
	 *  to a door.
	 *
	 *  @param  theOnesILove	a Bitmask of the doors that shall be locked.
	 *							Bit 0 corresponds to door 0, Bit 1 to door 1 etc.
	 *							So for example, passing 0x06 means you want
	 *							to lock the doors 1 and 2 (1<<1 + 1<<2 = 6)
	 *
	 *  @return					true if the attempt was successful, false if any of the
	 *							specified doors couldn't be locked. A door can't
	 *							be locked, if there's already one lock (either
	 *							shared or exclusive) by a different owner.
	 */
	public boolean attemptExclusive( int theOnesILove )
	{
		int			i, j, k;
		Thread		newOwner	= Thread.currentThread();
	
		synchronized( this ) {
checkLoop:	for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
					if( exclusiveLocks[i].contains( newOwner )) continue checkLoop;		// success
					if( !exclusiveLocks[i].isEmpty() ) return false;					// failure
					if( !sharedLocks[i].isEmpty() ) {
						for( k = 0; k < sharedLocks[i].size(); k++ ) {
							if( sharedLocks[i].get( k ) != newOwner ) return false;		// failure
						}
					}
					// success
				}
			}
			for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
					exclusiveLocks[i].add( newOwner );
				}
			}
			return true;
		} // synchronized( this )
	}

	/**
	 *  Same as attemptExclusively, but waits
	 *  until locking can be performed.
	 *
	 *  @param  theOnesILove	a Bitmask of the doors that shall be locked.
	 *							Bit 0 corresponds to door 0, Bit 1 to door 1 etc.
	 *							So for example, passing 0x06 means you want
	 *							to lock the doors 1 and 2 (1<<1 + 1<<2 = 6)
	 *
	 *  @see	java.lang.Object#wait()
	 */
	public void waitExclusive( int theOnesILove )
	{
		int			i, j, k;
		Thread		newOwner	= Thread.currentThread();
	
		synchronized( this ) {
checkLoop:	for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
waitLoop:			do {
						if( exclusiveLocks[i].contains( newOwner )) continue checkLoop;   // success
							
						if( !exclusiveLocks[i].isEmpty() ) {
							try {
								this.wait();
							} catch( InterruptedException e1 ) {}
							continue waitLoop;
						} else if( !sharedLocks[i].isEmpty() ) {
							for( k = 0; k < sharedLocks[i].size(); k++ ) {
								if( sharedLocks[i].get( k ) != newOwner ) {
									try {
										this.wait();
									} catch( InterruptedException e1 ) {}
									continue waitLoop;
								}
							}
						}
						// success
						continue checkLoop;
					} while( true );
				}
			}
			for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
					exclusiveLocks[i].add( newOwner );
				}
			}
		} // synchronized( this )
	}

	/**
	 *  Same as attemptExclusive( int ),
	 *  but tries for a certain amount of time to get
	 *  the doors.
	 *
	 *  @param  theOnesILove	a Bitmask of the doors that shall be locked.
	 *							Bit 0 corresponds to door 0, Bit 1 to door 1 etc.
	 *							So for example, passing 0x06 means you want
	 *							to lock the doors 1 and 2 (1<<1 + 1<<2 = 6)
	 *  @param  timeOut			maximum time to wait in millisecs
	 *
	 *  @see	java.lang.Object#wait( long )
	 */
	public boolean attemptExclusive( int theOnesILove, long timeOut )
	{
		int			i, j, k;
		boolean		retry		= true;
		Thread		newOwner	= Thread.currentThread();
	
		synchronized( this ) {
checkLoop:	for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
waitLoop:			do {
						if( exclusiveLocks[i].contains( newOwner )) continue checkLoop;   // success
							
						if( !exclusiveLocks[i].isEmpty() ) {
							if( !retry ) return false;
							retry = false;
							try {
								this.wait( timeOut );
							} catch( InterruptedException e1 ) {}
							continue waitLoop;
							
						} else if( !sharedLocks[i].isEmpty() ) {
							for( k = 0; k < sharedLocks[i].size(); k++ ) {
								if( sharedLocks[i].get( k ) != newOwner ) {
									if( !retry ) return false;
									retry = false;
									try {
										this.wait( timeOut );
									} catch( InterruptedException e1 ) {}
									continue waitLoop;
								}
							}
						}
						// success
						continue checkLoop;
					} while( true );
				}
			}
			for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
					exclusiveLocks[i].add( newOwner );
				}
			}
			return true;
		} // synchronized( this )
	}

	/**
	 *  Same as attemptExclusively, but the door may
	 *  be shared between different owners. The attempt
	 *  fails if one of the doors is locked exclusively
	 *  by a different owner.
	 *
	 *  @param  theOnesILove	a Bitmask of the doors that shall be locked.
	 *							Bit 0 corresponds to door 0, Bit 1 to door 1 etc.
	 *							So for example, passing 0x06 means you want
	 *							to lock the doors 1 and 2 (1<<1 + 1<<2 = 6)
	 *
	 */
	public boolean attemptShared( int theOnesILove )
	{
		int			i, j;
		Thread		newOwner	= Thread.currentThread();
	
		synchronized( this ) {
checkLoop:	for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
					if( exclusiveLocks[i].isEmpty() ||
						exclusiveLocks[i].contains( newOwner )) continue checkLoop;   // success
					
					return false;	// failure
				}
			}
			for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
					sharedLocks[i].add( newOwner );
				}
			}
			return true;
		} // synchronized( this )
	}

	/**
	 *  Same as attemptShared, but waits
	 *  until locking can be performed.
	 *
	 *  @param  theOnesILove	a Bitmask of the doors that shall be locked.
	 *							Bit 0 corresponds to door 0, Bit 1 to door 1 etc.
	 *							So for example, passing 0x06 means you want
	 *							to lock the doors 1 and 2 (1<<1 + 1<<2 = 6)
	 *
	 *  @see	java.lang.Object#wait()
	 */
	public void waitShared( int theOnesILove )
	{
		int				i, j;
		final Thread	newOwner	= Thread.currentThread();
	
		synchronized( this ) {
checkLoop:	for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
					do {
						if( exclusiveLocks[i].isEmpty() ||
							exclusiveLocks[i].contains( newOwner )) continue checkLoop;   // success
					
						try {
							this.wait();
						} catch( InterruptedException e1 ) {}
					} while( true );
				}
			}
			for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
					sharedLocks[i].add( newOwner );
				}
			}
		} // synchronized( this )
	}

	/**
	 *  Same as attemptShared( int )
	 *  but tries for a certain amount of time to get
	 *  the doors.
	 *
	 *  @param  timeOut		maximum time to wait in millisecs
	 *
	 *	 @see	java.lang.Object#wait( long )
	 */
	public boolean attemptShared( int theOnesILove, long timeOut )
	{
		int				i, j;
		final Thread	newOwner	= Thread.currentThread();
		long			n;
		
		synchronized( this ) {
checkLoop:	for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
					do {
						if( exclusiveLocks[i].isEmpty() ||
							exclusiveLocks[i].contains( newOwner )) continue checkLoop;   // success
					
						try {
							if( timeOut <= 0 ) return false;
							n = System.currentTimeMillis();
							this.wait( timeOut );
							timeOut += n - System.currentTimeMillis();	// rest-zeit
						} catch( InterruptedException e1 ) {}
					} while( true );
				}
			}
			for( i = 0, j = theOnesILove; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) {
					sharedLocks[i].add( newOwner );
				}
			}
			return true;
		} // synchronized( this )
	}

	/**
	 *  Removes exclusive locks from doors. Only the locks
	 *  are removed that are owned by the current
	 *  Thread. If the the Thread owns more than
	 *  one lock per door, only the last lock
	 *  of that door is removed.
	 *
	 *  @param  theOnesIHate	the door bitmask <strong>exactly</strong>
	 *							as used to lock the doors. it <strong>is</strong>
	 *							to partially release doors, however whenever performance
	 *							is not ultra critical, it makes code clearer if waits/attempts
	 *							and release occur in matched pairs.
	 *	@warning	Never mix shared and exclusive locks!
	 */
	public void releaseExclusive( int theOnesIHate )
	{
		int		i, j;
		Thread  recentOwner = Thread.currentThread();

		synchronized( this ) {
			for( i = 0, j = theOnesIHate; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) exclusiveLocks[i].remove( recentOwner );
			}
			this.notifyAll();
		} // synchronized( this )
	}

	/**
	 *  Removes shared locks from doors. Only the locks
	 *  are removed that are owned by the current
	 *  Thread. If the the Thread owns more than
	 *  one lock per door, only the last lock
	 *  of that door is removed.
	 *
	 *  @param  theOnesIHate	the door bitmask <strong>exactly</strong>
	 *							as used to lock the doors. it <strong>is</strong>
	 *							to partially release doors, however whenever performance
	 *							is not ultra critical, it makes code clearer if waits/attempts
	 *							and release occur in matched pairs.
	 *	@warning	Never mix shared and exclusive locks!
	 */
	public void releaseShared( int theOnesIHate )
	{
		int		i, j;
		Thread  recentOwner = Thread.currentThread();

		synchronized( this ) {
			for( i = 0, j = theOnesIHate; j > 0; i++, j >>= 1 ) {
				if( (j & 1) == 1 ) sharedLocks[i].remove( recentOwner );
			}
			this.notifyAll();
		} // synchronized( this )
	}
}