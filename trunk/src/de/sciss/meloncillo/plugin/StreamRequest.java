/*
 *  StreamRequest.java
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
 *		23-Jul-04   created
 *		01-Sep-04	addtional comments
 */

package de.sciss.meloncillo.plugin;

/**
 *  A StreamRequest describes the source
 *  data required for generating rendering output.
 *  This data is restricted to dynamic
 *  scalar vector data, i.e. data that
 *  changes over time : sense data
 *  (a receiver's sensibility at a point
 *  described by a transmitter trajectory)
 *  and trajectory data of a transmitter.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class StreamRequest
{
	/**
	 *  Number of transmitters involved
	 */
	public int				numTrns;
	/**
	 *  Number of receivers involved
	 */
	public int				numRcv;
	/**
	 *  The first array dimension corresponds
	 *  to the receiver index, the second array
	 *  dimension corresponds to the transmitter
	 *  index. A value of 'true' means that this
	 *  data should be generated. 'false' means
	 *  that this data is not rendering relevant
	 *  and thus needn't be calculated.
	 */
	public boolean[][]		senseRequest;
	/**
	 *  Same as senseRequest, but describing the
	 *  pure trajectory data and being linked to
	 *  trajBlockBuf.
	 */
	public boolean[]		trajRequest;
	
	/**
	 *  Constructs a new StreamRequest, where
	 *  the arrays are preallocated for the
	 *  given number of transmitters and receivers.
	 *  Note that the final vectors are not
	 *  initialized, i.e. senseBlockBuf will
	 *  become new float[numTrns][numRcv][] etc.
	 *  All request fields are set to false by default.
	 *
	 *	@param	numTrns	number of transmitters potentially involved
	 *	@param	numRcv	number of receivers potentially involved
	 */
	public StreamRequest( int numTrns, int numRcv )
	{
		senseRequest	= new boolean[ numTrns ][ numRcv ];		// set to false by default through the java VM
		trajRequest		= new boolean[ numTrns ];				// dito.
		
		this.numTrns	= numTrns;
		this.numRcv		= numRcv;
	}
	
	/**
	 *  Constructs a new StreamRequest by
	 *  copying a template. Note that the
	 *  vectors themselves are not allocated
	 *  just like in the StreamRequest( int numTrns, int numRcv )
	 *  constructor! However, the requests
	 *  are copied 1:1 to the new StreamRequest.
	 *
	 *	@param	template	template request whose
	 *						dimensions and request fields are
	 *						copied to the new request
	 */
	public StreamRequest( StreamRequest template )
	{
		this( template.numTrns, template.numRcv );
		
		for( int trnsIdx = 0; trnsIdx < numTrns; trnsIdx++ ) {
			System.arraycopy( template.senseRequest[ trnsIdx ], 0, this.senseRequest[ trnsIdx ], 0, numRcv );
		}
		System.arraycopy( template.trajRequest, 0, this.trajRequest, 0, numTrns );
	}
}