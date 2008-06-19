/*
 *	FlagsPanel.java
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
 *      28-Feb-05	created
 *		19-Mar-05	fixed bug in isAny()
 *		08-Apr-05	changed virtual-mute behaviour : explicit flag FLAGS_VIRTUALMUTE.
 *					session object not final anymore
 */
 
package de.sciss.meloncillo.session;

import de.sciss.meloncillo.util.*;

import de.sciss.app.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class FlagsPanel
extends AbstractFlagsPanel
implements DynamicListening, SessionCollection.Listener
{
	private final SessionCollection sc;
	private final LockManager lm;
	private final int doors;
	protected SessionObject so;

	public FlagsPanel( SessionObject so, SessionCollection sc, LockManager lm, int doors )
	{
		this( sc, lm, doors );

		this.so		= so;
		setOpaque( false );
        new DynamicAncestorAdapter( this ).addTo( this );
	}
	
	protected FlagsPanel( SessionCollection sc, LockManager lm, int doors )
	{
		super();

		this.sc		= sc;
		this.lm		= lm;
		this.doors	= doors;
	}

	// sync : attempt exclusive on doors
	protected void setFlags( int mask, boolean set )
	{
		int				flags, flagsNew;
		Object			o;
		MapManager		map;
		boolean			soloChange;
	
		if( !lm.attemptExclusive( doors, 250 )) return;
		try {
			map		= so.getMap();
			o		= map.getValue( SessionObject.MAP_KEY_FLAGS );
			flags	= o == null ? 0 : ((Integer) o).intValue();
			
			if( set ) {
				flagsNew	= flags | mask;
			} else {
				flagsNew	= flags & ~mask;
			}
			soloChange = (mask & SessionObject.FLAGS_SOLO) != 0;
			if( soloChange || (mask & SessionObject.FLAGS_SOLOSAFE) != 0  ) {
				if( set ) {
					flagsNew &= ~SessionObject.FLAGS_VIRTUALMUTE;
				} else if( (flagsNew & SessionObject.FLAGS_SOLO) == 0 &&
						   isAny( SessionObject.FLAGS_SOLO, true )) {
						   
					flagsNew |= SessionObject.FLAGS_VIRTUALMUTE;
				}
			}
			if( flags != flagsNew ) {
				map.putValue( this, SessionObject.MAP_KEY_FLAGS, new Integer( flagsNew ));
			}
			if( soloChange ) broadcastFlags( 0, true );
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}

	// sync : attempt exclusive on doors
	protected void broadcastFlags( int mask, boolean set )
	{
		int				i, flags, flagsNew;
		SessionObject	so2;
		Object			o;
		MapManager		map;
		boolean			virtualMute	= false;

		if( !lm.attemptExclusive( doors, 250 )) return;
		try {
			if( (mask & SessionObject.FLAGS_SOLO) == 0 &&
				!((mask & SessionObject.FLAGS_SOLOSAFE) != 0 && set) ) {
			
				virtualMute = isAny( SessionObject.FLAGS_SOLO, true );
			}
			for( i = 0; i < sc.size(); i++ ) {
				so2		= sc.get( i );
				map		= so2.getMap();
				o		= map.getValue( SessionObject.MAP_KEY_FLAGS );
				flags	= o == null ? 0 : ((Integer) o).intValue();
				
				if( set ) {
					flagsNew	= flags | mask;
				} else {
					flagsNew	= flags & ~mask;
				}
				if( virtualMute && (flagsNew & (SessionObject.FLAGS_SOLO | SessionObject.FLAGS_SOLOSAFE)) == 0 ) {
					flagsNew |= SessionObject.FLAGS_VIRTUALMUTE;
				} else {
					flagsNew &= ~SessionObject.FLAGS_VIRTUALMUTE;
				}
				
				if( flags != flagsNew ) {
					map.putValue( this, SessionObject.MAP_KEY_FLAGS, new Integer( flagsNew ));
				}
			}
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}

	// sync : attempt shared on doors
	protected boolean isAny( int mask, boolean set )
	{
		int				i, flags;
		SessionObject	so2;
		Object			o;
		MapManager		map;

		if( !lm.attemptShared( doors, 250 )) return false;
		try {
			for( i = 0; i < sc.size(); i++ ) {
				so2		= sc.get( i );
				map		= so2.getMap();
				o		= map.getValue( SessionObject.MAP_KEY_FLAGS );
				flags	= o == null ? 0 : ((Integer) o).intValue();
				
				if( set ) {
					if( (flags & mask) != 0 ) return true;
				} else {
					if( (flags & mask) == 0 ) return true;
				}
			}
			
			return false;
		}
		finally {
			lm.releaseShared( doors );
		}
	}

	// sync : shared on doors
	private void updateButtons()
	{
		Object	o;
		int		flags;
	
		lm.waitShared( doors );
		try {
			o		= so.getMap().getValue( SessionObject.MAP_KEY_FLAGS );
			flags	= o == null ? 0 : ((Integer) o).intValue();
			updateButtons( flags );
		}
		finally {
			lm.releaseShared( doors );
		}
	}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		updateButtons();
		sc.addListener( this );
    }

    public void stopListening()
    {
		sc.removeListener( this );
    }

// ---------------- SessionCollection.Listener interface ---------------- 

	public void sessionCollectionChanged( SessionCollection.Event e )
	{
		updateButtons();
	}
	
	public void sessionObjectChanged( SessionCollection.Event e ) {}
	
	public void sessionObjectMapChanged( SessionCollection.Event e )
	{
		if( e.setContains( SessionObject.MAP_KEY_FLAGS )) {
			updateButtons();
		}
	}
}