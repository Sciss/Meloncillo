/*
 *  Track.java
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
 *		13-May-05   created from de.sciss.meloncillo.transmitter.AbstractTransmitter
 *		15-Jan-06	audio specific stuff moved to AudioTrack class
 *		13-Jul-08	copied back from EisK
 */

package de.sciss.meloncillo.timeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.sciss.meloncillo.io.AudioTrail;
import de.sciss.meloncillo.session.AbstractSessionObject;
import de.sciss.meloncillo.transmitter.Transmitter;
import de.sciss.timebased.Trail;
import de.sciss.util.MutableInt;

/**
 *  A simple implementation of the <code>Transmitter</code>
 *  interface that does not yet make assumptions
 *  about the data structure but provides some
 *  common means useful for all transmitters.
 *  It provides the basic mechanism for XML import and
 *  export, it handles all methods except
 *  <code>getTrackEditor</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public abstract class Track
extends AbstractSessionObject
{
	/**
	 *  Constructs a new empty transmitter.
	 *  Basic initialization is achieved by
	 *  adding a preexisting file to the track editor,
	 *  calling <code>setName</code> etc. methods.
	 */
	protected Track()
	{
		super();
	}
	
	public abstract Trail getTrail();
	
	public void clear( Object source )
	{
		getTrail().clear( source );
	}
	
	public static void debugDump( List infos )
	{
		for( int i = 0; i < infos.size(); i++ ) {
			System.out.print( "#" + (i+1) + " - " );
			((Track.Info) infos.get( i )).debugDump();
		}
	}
	
	public static List getInfos( List selectedTracks, List allTracks )
	{
		Track					track;
		Trail					trail;
		Track.Info				ti;
		int						chan;
		MutableInt				count;
		final Map				mapInfos	= new HashMap();
		final List				collInfos	= new ArrayList();
		final Map				mapCounts	= new HashMap();
		
		for( int i = 0; i < allTracks.size(); i++ ) {
			track	= (Track) allTracks.get( i );
			trail	= track.getTrail();
			ti		= (Track.Info) mapInfos.get( trail );
			count	= (MutableInt) mapCounts.get( trail.getClass() );
			if( count == null ) {
				count = new MutableInt( 0 );
				mapCounts.put( trail.getClass(), count );
			}
			if( ti == null ) {
				ti	= new Info( trail, count.value() );
				mapInfos.put( trail, ti );
				collInfos.add( ti );
			}
			if( selectedTracks.contains( track )) {
				count.add( 1 );
				ti.selected	= true;
				ti.tracks.add( track );
				if( track instanceof Transmitter ) {
					ti.channelMap[ 0 ] = true;
					ti.channelMap[ 1 ] = true;	// not possible to select x/y separately
				} else {
//					if( track instanceof AudioTrack ) {
//						chan	= ((AudioTrack) track).getChannelIndex();
//					} else {
						chan	= 0;
//					}
					ti.channelMap[ chan ] = true;
				}
			}
		}
		
		return collInfos;
	}
	
// -------------- internal classes --------------

	public static class Info
	{
		public final List		tracks		= new ArrayList();
		public final Trail		trail;
		public boolean			selected	= false;
		public final boolean[]	channelMap;
		public final int		numChannels;
//		public int				numTracks	= 0;
		public final int		trackIndex;
	
		protected Info( Trail trail, int trackIndex )
		{
			this.trail		= trail;
			this.trackIndex	= trackIndex;
			
			if( trail instanceof AudioTrail ) {
				numChannels	= ((AudioTrail) trail).getChannelNum();
			} else if( trail instanceof Transmitter ) {
				numChannels	= 2;
			} else {
				numChannels	= 1;
			}
			
			channelMap = new boolean[ numChannels ];
		}
		
		public boolean getChannelSync()
		{
			if( numChannels == 0 ) return true;
			
			final boolean first = channelMap[ 0 ];
			for( int i = 1; i < numChannels; i++ ) {
				if( channelMap[ i ] != first ) return false;
			}
			return true;
		}
		
		public int[] createChannelMap( int numCh2, int offset, boolean skipUnused )
		{
			final int[] chanMap = new int[ numCh2 ];
			int i, j;
			for( i = 0, j = offset; (i < this.numChannels) && (j < numCh2); i++ ) {
				if( channelMap[ i ]) {
					chanMap[ j++ ] = i;
				} else if( skipUnused ) {
					chanMap[ j++ ] = -1;
				}
			}
			while( j < numCh2 ) {
				chanMap[ j++ ] = -1;
			}
			return chanMap;
		}

		public void debugDump()
		{
			System.out.println( "Track.Info" );
			System.out.println( "  tracks.size = " + tracks.size() );
			System.out.println( "  trail       = " + trail );
			System.out.println( "  selected    = " + selected );
			System.out.println( "  channelMap  = " + channelMap );
			System.out.println( "  numChannels = " + numChannels );
			System.out.println( "  trackIndex  = " + trackIndex );
		}
	}
}