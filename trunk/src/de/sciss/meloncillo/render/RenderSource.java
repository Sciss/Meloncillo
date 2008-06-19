/*
 *  RenderSource.java
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
 *		14-Jul-04   created
 *		27-Jul-04   subclasses StreamRequest
 *		02-Sep-04	additional comments
 */

package de.sciss.meloncillo.render;

import de.sciss.meloncillo.plugin.*;

import de.sciss.io.*;

/**
 *  A RenderSource describes the source
 *  data for generating rendering output.
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
public class RenderSource
extends StreamRequest
{
	/**
	 *  The blockSpan describes the
	 *  current time span of the provided data
	 *  in the source rate sense.
	 *  Thus, blockSpan.getLength() equals
	 *  blockBufLen
	 *
	 *	@todo	check what happens when resampling is active
	 */
	public Span				blockSpan;
	/**
	 *  First array dim. is rcv idx, second dim. is
	 *  trns idx. Each resulting vector corresponds
	 *  to a block of sense data for the specific
	 *  rcv and trns, with offset and length given
	 *  by blockBufOff and blockBufLen. The vector
	 *  is null if the corresponding senseRequest
	 *  is false.
	 */
	public float[][][]		senseBlockBuf;
	/**
	 *  Same as senseBlockBuf, but describing the
	 *  pure trajectory data and being linked to
	 *  trajRequest. First dim. is transmitter,
	 *  second dim. is X/Y, third dimension is
	 *  buffer index.
	 */
	public float[][][]		trajBlockBuf;
	/**
	 *  Offset to use when reading data
	 *  from senseBlockBuf and trajBlockBuf
	 */
	public int				blockBufOff;
	/**
	 *  Length to use when reading data
	 *  from senseBlockBuf and trajBlockBuf
	 */
	public int				blockBufLen;
	
	/**
	 *  Constructs a new RenderSource, where
	 *  the arrays are preallocated for the
	 *  given number of transmitters and receivers.
	 *  Note that the final vectors are not
	 *  initialized, i.e. senseBlockBuf will
	 *  become new float[numTrns][numRcv][] etc.
	 *  All request fields are set to false by default.
	 *
	 *	@param	numTrns	number of transmitters used for rendering
	 *	@param	numRcv	number of receivers used for rendering
	 */
	public RenderSource( int numTrns, int numRcv )
	{
		super( numTrns, numRcv );
	
		senseBlockBuf   = new float[ numTrns ][ numRcv ][];
		trajBlockBuf	= new float[ numTrns ][][];
	}
	
	/**
	 *  Constructs a new RenderSource by
	 *  copying a template. Note that the
	 *  vectors themselves are not allocated
	 *  just like in the RenderSource( int numTrns, int numRcv )
	 *  constructor! However, the requests
	 *  are copied 1:1 to the new RenderSource.
	 *
	 *	@param	template	a template request whose dimensions
	 *						and requests are copied to the newly
	 *						created render source
	 */
	public RenderSource( StreamRequest template )
	{
		super( template );
	
		senseBlockBuf   = new float[ numTrns ][ numRcv ][];
		trajBlockBuf	= new float[ numTrns ][][];
	}
}