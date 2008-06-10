/*
 *  MultirateTrackEditor.java
 *  Meloncillo
 *
 *  Copyright (c) 2004 Hanns Holger Rutz. All rights reserved.
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
 *		03-Aug-04   commented, cleaned up.
 *					read( Span, float[][], int ) was removed
 *					read( SubsampleInfo, float[][] frames ) was removed
 *		28-Jan-05	enhanced to support variable decimation amounts and
 *					channel expansions for multi-model decimation
 */

package de.sciss.meloncillo.io;

import java.awt.*;
import java.io.*;

import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.gui.*;

import de.sciss.io.*;

/**
 *  This class provides means for automatic multirate handling
 *  of nondestructive nonlinear track editing objects.
 *  It wraps a number (currently 7) of track editors representing
 *  the same signal at different decimation stages where
 *  one file represents fullrate data and each subsampled
 *  file decimates the rate by 4. Thus, if fullrate corresponds
 *  to 1024 Hz sampling rate, the first subsampled file is
 *  a decimation to 256 Hz, the second subsampled file is
 *  a decimation to 64 Hz etc. So, using 6 subsampled files
 *  goes down to 1/4096th of the fullrate. Taking the unusual
 *  case that a user would use audio rate for sense data, say
 *  48 kHz, then the lowest resolution subsample file will run
 *  at about 12 Hz, so if a GUI element request data for a very
 *  long time span, say half an hour, it would have to handle
 *  a buffer of 21094 frames; in the more usual case of a sense
 *  rate of say 4800 Hz (or less), one hour could still be represented
 *  by 4219 frames, thus maintaining low RAM and CPU consumption.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	SubsampleTrackEditor
 */
public class MultirateTrackEditor
{
	public static final int MODEL_PCM				= 0;
	public static final int MODEL_HALFWAVE_PEAKRMS	= 1;
	public static final int MODEL_MEDIAN			= 2;

	private final int channels, modelChannels;
	private final int rate;
	private final int model;
	private final int[] decimations;

	// this array of size 'SUBNUM' contains STEs
	// that keep track of the subsampled versions of
	// this STE's track data. ste[0] is full rate,
	// ste[1] is 4x subsampled, ste[2] is 16x subsampled etc.
	private final SubsampleTrackEditor[] ste;
	private final int SUBNUM;
	private final int MAXSHIFT;
	private final int MAXCOARSE;
	private final long MAXMASK;
	private final int MAXCEILADD;

	private final float[][] tmpBuf;
	private final int tmpBufSize;
	private final float[][] tmpBuf2;
	private final int tmpBufSize2;

	/**
	 *  Create a new empty <code>MultirateTrackEditor</code>
	 *  for a given number of channels. Note that
	 *  temporary files are created automatically
	 *  once you start to write data to the editor.
	 *  When you wish to include preexisting data
	 *  from disk, you'll want to add the particular
	 *  file using <code>insert( InterleavedFloatFile, long, Span )</code>.
	 *
	 *  @param  channels	number of channels to use for the editor
	 *
	 *  @see	#insert(  InterleavedStreamFile, long, Span, ProgressComponent, float, float )
	 */
	public MultirateTrackEditor( int channels, int rate, int model, int[] decimations )
	{
		int i;
	
		this.channels		= channels;
		this.rate			= rate;
		this.model			= model;
		
		switch( model ) {
		case MODEL_HALFWAVE_PEAKRMS:
			this.modelChannels	= 4;
			break;
		case MODEL_MEDIAN:
			this.modelChannels	= 1;
			break;
		default:
			throw new IllegalArgumentException( String.valueOf( model ));
		}
		
		SUBNUM				= decimations.length;	// the first 'subsample' is actually fullrate
		this.decimations	= decimations;
		MAXSHIFT			= decimations[ SUBNUM - 1 ];
		MAXCOARSE			= 1 << MAXSHIFT;
		MAXMASK				= -MAXCOARSE;
		MAXCEILADD			= MAXCOARSE - 1;

		tmpBufSize			= MAXCOARSE << 1;
		tmpBuf				= new float[channels][tmpBufSize];
		tmpBufSize2			= SUBNUM > 1 ? (tmpBufSize >> decimations[ 1 ]) : tmpBufSize;
		tmpBuf2				= new float[modelChannels * channels][tmpBufSize2];
		
		ste					= new SubsampleTrackEditor[ SUBNUM ];
		ste[0]				= new SubsampleTrackEditor( channels, 0 );
		for( i = 1; i < SUBNUM; i++ ) {
			ste[i] = new SubsampleTrackEditor( modelChannels * channels, decimations[ i ]);
		}
	}

	/**
	 *  Returns a track list from the fullrate
	 *  version of the track editor for a given
	 *  time span. A track list describes the
	 *  possibly fragmented regions which form
	 *  a continuous block of time span data.
	 *  This can be used for copy and paste applications.
	 *
	 *  @param  span	the span for which a tracklist
	 *					should be constructed.
	 *  @return a newly constructed tracklist for the given time span
	 *
	 *  @see	SubsampleTrackEditor#getTrackList( Span )
	 */
	public TrackList getTrackList( Span span )
	{
		return ste[0].getTrackList( span );
	}

	/**
	 *  Clears the editor such that the time span
	 *  is empty. Deletes the temp file contents.
	 *
	 *  @throws IOException if an error occurs
	 *
	 *  @see	SubsampleTrackEditor#clear()
	 */
    public void clear()
    throws IOException
    {
		for( int i = 0; i < SUBNUM; i++ ) {
			ste[i].clear();
		}
	}
	
	public int getRate()
	{
		return rate;
	}

	/**
	 *  Starts an insertion operation that takes more than a single
	 *  buffer write. This is the first command, followed by any required
	 *  number of <code>continueWrite</code> calls and a final <code>finishWrite</code>
	 *  call, like shown in the following scheme:
	 *  <p><pre>
	 *    TrackSpan ts = mte.beginInsert( spanTotal );
	 *    for( i = 0; i < numberOfBlocks; i++ ) {
	 *      fillMyFrameBuf( frameBuf );
	 *      mte.continue( ts, frameBuf, 0, frameBufLength );
	 *   }
	 *   mte.finishWrite( ts );
	 *  </pre>
	 *  <p>In this case, make sure that <code>numberOfBlocks * frameBufLength</code>
	 *  exactly equals <code>spanTotal.getLength()</code> which is *NOT* checked
	 *  at the moment.
	 *
	 *  @param  tag		The total length of all expected continueWrite operations.
	 *  @return			an object which must be passed to the following continueWrite
	 *					and finishWrite calls.
	 *  @throws IOException if a write error occurs.
	 *  @see	#continueWrite( TrackSpan, float[][], int, int )
	 *  @see	#finishWrite( TrackSpan )
	 *  @synchronization	if the MultirateTrackEditor can be accessed from different
	 *						threads, you should block everything from beginInsert to
	 *						finishWrite into an appropriate synchronization.
	 */
	public TrackSpan beginInsert( Span tag )
    throws IOException
	{
		return ste[0].beginInsert( tag );
	}

	/**
	 *  Starts an insertion operation that takes more than a single
	 *  buffer write. Each action is appended to the provided <code>CompoundEdit</code>
	 *  such that, if an <code>IOException</code> is thrown anywhere, the whole action can be undo.
	 *  This is the first command, followed by any required
	 *  number of <code>continueWrite</code> calls and a final <code>finishWrite</code>
	 *  call, like shown in the following scheme:
	 *  <p><pre>
	 *
	 *   TrackSpan ts = mte.beginInsert( spanTotal, myCompoundEdit );
	 *   try {
	 *     for( i = 0; i < numberOfBlocks; i++ ) {
	 *       fillMyFrameBuf( frameBuf );
	 *       mte.continue( ts, frameBuf, 0, frameBufLength );
	 *     }
	 *     mte.finishWrite( ts, myCompoundEdit );
	 *   }
	 *   catch( IOException e1 ) {
	 *     myCompoundEdit.cancel();
	 *   }
	 *  </pre>
	 *  <p>In this case, make sure that <code>numberOfBlocks * frameBufLength</code>
	 *  exactly equals <code>spanTotal.getLength()</code> which is *NOT* checked
	 *  at the moment.
	 *
	 *  @param  tag		The total length of all expected continueWrite operations.
	 *  @param  edit	a compound edit with suitable synchronization settings which
	 *					can be used for later undo() or cancel() operation.
	 *  @return			an object which must be passed to the following continueWrite
	 *					and finishWrite calls.
	 *  @throws IOException if a write error occurs.
	 *  @see	#continueWrite( TrackSpan, float[][], int, int )
	 *  @see	#finishWrite( TrackSpan, SyncCompoundEdit )
	 *  @synchronization	if the MultirateTrackEditor can be accessed from different
	 *						threads, you should block everything from beginInsert to
	 *						finishWrite into an appropriate synchronization.
	 */
	public TrackSpan beginInsert( Span tag, SyncCompoundEdit edit )
    throws IOException
	{
		return ste[0].beginInsert( tag, edit );
	}

	/**
	 *  Starts an overwrite operation that takes more than a single
	 *  buffer write. Each action is appended to the provided <code>CompoundEdit</code>
	 *  such that, if an <code>IOException</code> is thrown anywhere, the whole action can be undo.
	 *  Automatic blending crossfade is applied using the given <code>BlendContext</code>.
	 *  This is the first command, followed by any required
	 *  number of <code>continueWrite</code> calls and a final <code>finishWrite</code>
	 *  call.
	 *
	 *  @param  tag		The total length of all expected continueWrite operations.
	 *  @param  bc		a <code>BlendContext</code> describing the crossfade shapes and lenghths.
	 *  @param  edit	a compound edit with suitable synchronization settings which
	 *					can be used for later undo() or cancel() operation.
	 *  @return			an object which must be passed to the following continueWrite
	 *					and finishWrite calls.
	 *  @throws IOException if a write error occurs.
	 *  @see	#beginOverwrite( Span, SyncCompoundEdit )
	 *  @see	#continueWrite( BlendSpan, float[][], int, int )
	 *  @see	#finishWrite( BlendSpan, SyncCompoundEdit )
	 *  @synchronization	if the MultirateTrackEditor can be accessed from different
	 *						threads, you should block everything from beginOverwrite to
	 *						finishWrite into an appropriate synchronization.
	 */
	public BlendSpan beginOverwrite( Span tag, BlendContext bc, SyncCompoundEdit edit )
    throws IOException
	{
		long blendLen = Math.min( tag.getLength() >> 1, bc.blendLen );
	
		TrackList   leftTL  = ste[0].getTrackList( new Span( tag.getStart(), tag.getStart() + blendLen ));
		TrackList   rightTL = ste[0].getTrackList( new Span( tag.getStop() - blendLen, tag.getStop() ));

		remove( tag, edit );
		TrackSpan   ts		= ste[0].beginInsert( tag, edit );
	
		BlendSpan   bs		= new BlendSpan( bc, leftTL, rightTL, ts, ste[0] );
		return bs;
	}

	/**
	 *  Starts an overwrite operation that takes more than a single
	 *  buffer write. This is the first command, followed by any required
	 *  number of <code>continueWrite</code> calls and a final <code>finishWrite</code>
	 *  call. This is identical to <code>beginInsert( Span )</code> except that
	 *  the new time span will erase the track list which previously formed
	 *  the given time span, hence not altering the total duration of the track editor.
	 *
	 *  @param  tag		The total length of all expected continueWrite operations.
	 *  @return			an object which must be passed to the following continueWrite
	 *					and finishWrite calls.
	 *  @throws IOException if a write error occurs.
	 *  @see	#beginInsert( Span )
	 *  @see	#continueWrite( TrackSpan, float[][], int, int )
	 *  @see	#finishWrite( TrackSpan )
	 *  @synchronization	if the MultirateTrackEditor can be accessed from different
	 *						threads, you should block everything from beginOverwrite to
	 *						finishWrite into an appropriate synchronization.
	 */
    public TrackSpan beginOverwrite( Span tag )
    throws IOException
    {
		// XXX besser direkt implementieren, weil durch
		// die zweifache subsampleInsert anpassung zuviel
		// performance verschwendet wird
        remove( tag );
		return ste[0].beginInsert( tag );
    }

	/**
	 *  Starts an overwrite operation that takes more than a single
	 *  buffer write. Each action is appended to the provided <code>CompoundEdit</code>
	 *  such that, if an <code>IOException</code> is thrown anywhere, the whole action can be undo.
	 *  This is the first command, followed by any required
	 *  number of <code>continueWrite</code> calls and a final <code>finishWrite</code>
	 *  call. This is identical to <code>beginInsert( Span )</code> except that
	 *  the new time span will erase the track list which previously formed
	 *  the given time span, hence not altering the total duration of the track editor.
	 *
	 *  @param  tag		The total length of all expected continueWrite operations.
	 *  @param  edit	a compound edit with suitable synchronization settings which
	 *					can be used for later undo() or cancel() operation.
	 *  @return			an object which must be passed to the following continueWrite
	 *					and finishWrite calls.
	 *  @throws IOException if a write error occurs.
	 *  @see	#beginInsert( Span, SyncCompoundEdit )
	 *  @see	#continueWrite( TrackSpan, float[][], int, int )
	 *  @see	#finishWrite( TrackSpan, SyncCompoundEdit )
	 *  @synchronization	if the MultirateTrackEditor can be accessed from different
	 *						threads, you should block everything from beginOverwrite to
	 *						finishWrite into an appropriate synchronization.
	 */
    public TrackSpan beginOverwrite( Span tag, SyncCompoundEdit edit )
    throws IOException
    {
		remove( tag, edit );
		return ste[0].beginInsert( tag, edit );
    }

	/**
	 *  Writes a block of frame data in a beginInsert/beginOverwrite ... finishWrite
	 *  block.
	 *
	 *  @param  ts		the object returned from the initial beginInsert or beginOverwrite
	 *					call
	 *  @param  frames  data to write where frame[0][] is channel0 etc.
	 *  @param  offset  frame offset in frames such that the first written frame is
	 *					frames[ch][offset]
	 *  @param  length  number of successive frames to write. Make sure that when you call
	 *					finishWrite, the sum of the written buffer lengths is exactly
	 *					the length of the track span.
	 *  @throws IOException when a write error occurs
	 *  @see	#beginInsert( Span )
	 *  @see	#finishWrite( TrackSpan )
	 */
	public void continueWrite( TrackSpan ts, float[][] frames, int offset, int length )
    throws IOException
	{
		ste[0].continueWrite( ts, frames, offset, length );
	}

	/**
	 *  Writes a block of frame data in a beginInsert/beginOverwrite ... finishWrite
	 *  block.
	 *
	 *  @param  bs		the object returned from the initial beginInsert or beginOverwrite
	 *					call
	 *  @param  frames  data to write where frame[0][] is channel0 etc.
	 *  @param  offset  frame offset in frames such that the first written frame is
	 *					frames[ch][offset]
	 *  @param  length  number of successive frames to write. Make sure that when you call
	 *					finishWrite, the sum of the written buffer lengths is exactly
	 *					the length of the track span.
	 *  @throws IOException when a write error occurs
	 *  @see	#beginOverwrite( Span, BlendContext, SyncCompoundEdit )
	 *  @see	#finishWrite( BlendSpan, SyncCompoundEdit )
	 */
	public void continueWrite( BlendSpan bs, float[][] frames, int offset, int length )
    throws IOException
	{
		bs.write( frames, offset, length );
	}

	/**
	 *  Finishes a multi-block-write operation.
	 *  This will update the decimated representations of
	 *  the written data, so that GUI displays can show
	 *  the data right after calling this method.
	 *
	 *  @param  ts		the object returned from the initial beginInsert or beginOverwrite
	 *					call
	 *  @throws IOException when a write error occurs
	 *  @throws IllegalStateException if the sum of the continueWrite calls didn't
	 *			provide the required number of frames
	 *  @see	#beginInsert( Span )
	 *  @see	#continueWrite( TrackSpan, float[][], int, int )
	 */
	public void finishWrite( TrackSpan ts )
    throws IOException
	{
		ste[0].finishWrite( ts );
		subsampleInsert( ts.span, null, null, 0.0f, 0.0f );
	}

	/**
	 *  Finishes a multi-block-write operation.
	 *  This will update the decimated representations of
	 *  the written data, so that GUI displays can show
	 *  the data right after calling this method.
	 *
	 *  @param  ts		the object returned from the initial beginInsert or beginOverwrite
	 *					call
	 *  @param  edit	the edit object passed to the initial beginInsert or beginOverwrite
	 *					call
	 *  @throws IOException when a write error occurs
	 *  @throws IllegalStateException if the sum of the continueWrite calls didn't
	 *			provide the required number of frames
	 *  @see	#beginInsert( Span, SyncCompoundEdit )
	 *  @see	#continueWrite( TrackSpan, float[][], int, int )
	 */
	public void finishWrite( TrackSpan ts, SyncCompoundEdit edit )
    throws IOException
	{
		ste[0].finishWrite( ts );
		subsampleInsert( ts.span, edit, null, 0.0f, 0.0f );
	}

	/**
	 *  Finishes a multi-block-write operation.
	 *  This will update the decimated representations of
	 *  the written data, so that GUI displays can show
	 *  the data right after calling this method.
	 *
	 *  @param  bs		the object returned from the initial beginOverwrite call
	 *  @param  edit	the edit object passed to the initial beginOverwrite call
	 *  @throws IOException when a write error occurs
	 *  @throws IllegalStateException if the sum of the continueWrite calls didn't
	 *			provide the required number of frames
	 *  @see	#beginOverwrite( Span, BlendContext, SyncCompoundEdit )
	 *  @see	#continueWrite( BlendSpan, float[][], int, int )
	 */
	public void finishWrite( BlendSpan bs, SyncCompoundEdit edit )
    throws IOException
	{
		ste[0].finishWrite( bs.trackSpan );
		subsampleInsert( bs.trackSpan.span, edit, null, 0.0f, 0.0f );
	}

	/**
	 *  Overwrites a single block of frame data. Decimated
	 *  subsample track editors are updated accordingly.
	 *
	 *  @param  tag		the time span to overwrite. Note that no data is overwritten
	 *					on the harddisc but simply appended to the temp files, so
	 *					undo is possible.
	 *  @param  frames  data to write where frame[0][] is channel0 etc. frame offset
	 *					is presumed to be zero, frame length is determined from
	 *					tag.getLength().
	 *  @throws IOException when a write error occurs
	 */
    public void overwrite( Span tag, float[][] frames )
    throws IOException
    {
		// XXX besser direkt implementieren, weil durch
		// die zweifache subsampleInsert anpassung zuviel
		// performance verschwendet wird
        remove( tag );
        insert( tag, frames );
    }

	/**
	 *  Overwrites a single block of frame data. Decimated
	 *  subsample track editors are updated accordingly. The
	 *  operation is attached to the provided compound edit
	 *  making undo possible.
	 *
	 *  @param  tag		the time span to overwrite. Note that no data is overwritten
	 *					on the harddisc but simply appended to the temp files, so
	 *					undo is possible.
	 *  @param  frames  data to write where frame[0][] is channel0 etc. frame offset
	 *					is presumed to be zero, frame length is determined from
	 *					tag.getLength().
	 *  @param  edit	a compound edit to which this new overwrite edit is
	 *					added.
	 *  @throws IOException when a write error occurs
	 */
    public void overwrite( Span tag, float[][] frames, SyncCompoundEdit edit )
    throws IOException
    {
        remove( tag, edit );
        insert( tag, frames, edit );
    }

	/**
	 *  Inserts a single block of frame data. Decimated
	 *  subsample track editors are updated accordingly.
	 *
	 *  @param  tag		the time span to insert. The new frames are inserted
	 *					at tag.getStart(), the length of the inserted block is
	 *					tag.getLength(), thus all data previously starting at
	 *					tag.getStart() or later, will be shifted by tag.getLength().
	 *  @param  frames  data to write where frame[0][] is channel0 etc. frame offset
	 *					is presumed to be zero, frame length is determined from
	 *					tag.getLength().
	 *  @throws IOException when a write error occurs
	 */
    public void insert( Span tag, float[][] frames )
    throws IOException
    {
		ste[0].insert( tag, frames );
		subsampleInsert( tag, null, null, 0.0f, 0.0f );
	}

	/**
	 *  Inserts a single block of frame data. Decimated
	 *  subsample track editors are updated accordingly. The
	 *  operation is attached to the provided compound edit
	 *  making undo possible.
	 *
	 *  @param  tag		the time span to insert. The new frames are inserted
	 *					at tag.getStart(), the length of the inserted block is
	 *					tag.getLength(), thus all data previously starting at
	 *					tag.getStart() or later, will be shifted by tag.getLength().
	 *  @param  frames  data to write where frame[0][] is channel0 etc. frame offset
	 *					is presumed to be zero, frame length is determined from
	 *					tag.getLength().
	 *  @param  edit	a compound edit to which this new insert edit is
	 *					added.
	 *  @throws IOException when a write error occurs
	 */
    public void insert( Span tag, float[][] frames, SyncCompoundEdit edit )
    throws IOException
    {
		ste[0].insert( tag, frames, edit );
		subsampleInsert( tag, edit, null, 0.0f, 0.0f );
	}
  
	/**
	 *  Inserts multirate data from this or another editor using a
	 *  tracklist description. This is used in clipboard copy / paste
	 *  operations. This operation is attached to the provided compound edit
	 *  making undo possible.
	 *
	 *  @param  position	the offset in this editor at which the track list
	 *						data will be inserted. the length of the inserted block is
	 *						tl.getSpan().getLength(), thus all data previously
	 *						starting at tl.getSpan().getStart() or later, will
	 *						be shifted by tl.getSpan().getLength().
	 *  @param  tl			the list of one or more track spans forming a
	 *						continuous time span to insert and referencing
	 *						the implied harddisc files. These track spans
	 *						are merely referenced for the fullrate data. To avoid
	 *						boundary problems, however, decimated data is re-written.
	 *  @param  edit		a compound edit to which this new insert edit is
	 *						added.
	 *  @throws IOException when a write error occurs
	 */
	public void insert( long position, TrackList tl, SyncCompoundEdit edit )
    throws IOException
	{
		Span tag = new Span( position, position + tl.getSpan().getLength() );
		ste[0].insert( position, tl, edit );
		subsampleInsert( tag, edit, null, 0.0f, 0.0f );
	}

	/**
	 *  Inserts preexisting <code>InterleavedFloat</code> file data into
	 *  this editor.
	 *
	 *  @param  f		the file to insert. the caller has to ensure the
	 *					number of channels equals this editor's number of channels.
	 *  @param  offset	frame offset in the the <code>InterleavedFloat</code>
	 *					corresponding to the first referenced frame
	 *  @param  tag		where to insert the new file. data previously starting
	 *					at tag.getStart() or later is shifted by tag.getLength().
	 *					the new frame at tag.getStart() corresponds to the frame
	 *					in the provided file at <code>offset</code>.
	 *  @throws IOException when a write error occurs
	 */
    public void insert( InterleavedStreamFile f, long offset, Span tag,
						ProgressComponent pc, float progOff, float progWeight )
    throws IOException
    {
		ste[0].insert( f, offset, tag );
		subsampleInsert( tag, null, pc, progOff, progWeight );
	}
	
	/**
	 *  Removes a time span from the editor. Decimated
	 *  subsample track editors are updated accordingly.
	 *
	 *  @param  tag		the time span to remove.
	 *  @throws IOException when a write error occurs
	 *						during subsample update
	 */
    public void remove( Span tag )
    throws IOException
    {
		ste[0].remove( tag );

		long		floorStart  = tag.getStart() & MAXMASK;
		long		ceilStop	= (tag.getStart() + MAXCEILADD) & MAXMASK;
		Span		extendedSpan= new Span( floorStart, ceilStop + tag.getLength() );
		Span		reinsertSpan= new Span( floorStart, ceilStop );
		int			i;

		for( i = 1; i < SUBNUM; i++ ) {
			ste[i].remove( extendedSpan );		// XXX arbeitsweise ueberpruefen
		}

		if( !reinsertSpan.isEmpty() ) subsampleInsert( reinsertSpan, null, null, 0.0f, 0.0f );
    }

	/**
	 *  Removes a time span from the editor. Decimated
	 *  subsample track editors are updated accordingly. The provided
	 *  edit can be used to undo the action.
	 *
	 *  @param  tag		the time span to remove.
	 *  @param  edit	a compound edit to which this remove edit is added.
	 *  @throws IOException when a write error occurs
	 *						during subsample update
	 */
    public void remove( Span tag, SyncCompoundEdit edit )
    throws IOException
    {
		ste[0].remove( tag, edit );

		long		floorStart  = tag.getStart() & MAXMASK;
		long		ceilStop	= (tag.getStart() + MAXCEILADD) & MAXMASK;
		Span		extendedSpan= new Span( floorStart, ceilStop + tag.getLength() );
		Span		reinsertSpan= new Span( floorStart, ceilStop );
		int			i;

		for( i = 1; i < SUBNUM; i++ ) {
			ste[i].remove( extendedSpan, edit );		// XXX arbeitsweise ueberpruefen
		}

		if( !reinsertSpan.isEmpty() ) subsampleInsert( reinsertSpan, edit, null, 0.0f, 0.0f );
    }
	
	/**
	 *  Wrapper method for calling the fullrate
	 *  editor's flatten method.
	 *
	 *  @param  f   the file into which the flattened data is written
	 *  @throws IOException if a read or write error occurs
	 *  @see	SubsampleTrackEditor#flatten( InterleavedStreamFile )
	 */
	public void flatten( InterleavedStreamFile f )
    throws IOException
	{
		ste[0].flatten( f ); // XXX oder subsamples mit abspeichern ?
	}

	/**
	 *  Determines which subsampled version is suitable
	 *  for a given display range (the most RAM and CPU
	 *  economic while maining optimal display resolution).
	 *  For a given time span, the lowest resolution is
	 *  chosen which will produce at least <code>minLen</code>
	 *  frames.
	 *
	 *  @param  tag		the time span the caller is interested in
	 *  @param  minLen  the minimum number of sampled points wanted.
	 *	@return			an information object describing the best
	 *					subsample of the track editor. note that
	 *					info.sublength will be smaller than minLen
	 *					if tag.getLength() was smaller than minLen
	 *					(in this case the fullrate version is used).
	 *  @see	#read( SubsampleInfo, float[][], int )
	 */
	public SubsampleInfo getBestSubsample( Span tag, int minLen )
	{
		long			subLength;
		int				idx, inlineDecim;
		SubsampleInfo   info;
		boolean			isFullScale;
		
		for( idx = 1; idx < SUBNUM; idx++ ) {
			if( ste[idx].fullrateToSubsample( tag.getLength() ) < minLen ) break;
		}
		--idx;
		subLength = ste[idx].fullrateToSubsample( tag.getLength());
		// had to change '>= minLen' to '> minLen' because minLen could be zero!
		switch( model ) {
		case MODEL_HALFWAVE_PEAKRMS:
			for( inlineDecim = 2; subLength / inlineDecim > minLen; inlineDecim++ ) ;
			inlineDecim--;
			break;
		
		case MODEL_MEDIAN:
			inlineDecim = 1;
			break;
		
		default:
			assert false : model;
			inlineDecim = 1;
		}
		subLength /= inlineDecim;
//System.err.println( "minLen = "+minLen+"; subLength = "+subLength+"; inlineDecim = "+inlineDecim+" ; idx = "+idx );
		isFullScale = idx == 0 && inlineDecim == 1;
		info	= new SubsampleInfo( ste[ idx ], tag, subLength,
									 isFullScale ? channels : (channels * modelChannels),
									 inlineDecim, isFullScale ? MODEL_PCM : model );
		return info;
	}

	/**
	 *  Reads a block of frames (always uses the fullrate version).
	 *
	 *  @param  span	the time span to read
	 *  @param  frames  to buffer to fill, where frames[0][] corresponds
	 *					to the first channel etc. and the buffer length
	 *					must be at least off + span.getLength()
	 *  @param  off		offset in frames, such that the first frame
	 *					will be placed in frames[ch][off]
	 *  @throws IOException if a read error occurs
	 */
    public void read( Span span, float[][] frames, int off )
    throws IOException
    {
		ste[0].read( span, frames, off );
	}

	/**
	 *  Reads a block of subsampled frames.
	 *
	 *  @param  info	the <code>SubsampleInfo</code> as returned by
	 *					<code>getBestSubsample</code>, describing the
	 *					span to read and which resolution to choose.
	 *  @param  frames  to buffer to fill, where frames[0][] corresponds
	 *					to the first channel etc. and the buffer length
	 *					must be at least off + info.sublength!
	 *  @param  off		offset in frames, such that the first frame
	 *					will be placed in frames[ch][off]
	 *  @throws IOException if a read error occurs
	 *  @see	#getBestSubsample( Span, int )
	 *  @see	SubsampleInfo#sublength
	 */
    public void read( SubsampleInfo info, float[][] frames, int off )
    throws IOException
    {
//System.err.println( "read "+info.idx+" (span = "+info.span.getStart()+" ... "+info.span.getStop() );

		if( info.inlineDecim == 1 ) {
			info.ste.read( info.span, frames, off );
		} else {
			boolean fromPCM			= info.ste.getShift() == 0;
			long	maxLen			= (fromPCM ? tmpBufSize : tmpBufSize2) << info.ste.getShift();
			Span	chunkSpan;
			long	start			= info.span.getStart();
			long	fullLen;
			int		chunkLen, decimLen;
			long	totalLength		= (info.sublength * info.inlineDecim) << info.ste.getShift();
//System.err.println( "idx = "+info.idx+"; maxLen "+maxLen+"; totallength "+totalLength+"; test "+test+"; frames[0].length "+frames[0].length+
//					"; subLength = "+info.sublength+"; inlineDecim = "+info.inlineDecim+"; shift = "+decimations[ info.idx ]);
			do {
				fullLen		= Math.min( maxLen, totalLength );
				chunkSpan	= new Span( start, start + fullLen );
				chunkLen	= (int) info.ste.fullrateToSubsample( fullLen );
				decimLen	= chunkLen / info.inlineDecim;
//System.err.println( "remaining "+totalLength+"; fullLen = "+fullLen +" = chunkLen "+chunkLen+"; = decimLen "+decimLen+";  off = "+off );
				switch( model ) {
				case MODEL_HALFWAVE_PEAKRMS:
					if( fromPCM ) {
						info.ste.read( chunkSpan, tmpBuf, 0 );
						decimatePCMtoHalfPeakRMS( tmpBuf, frames, off, decimLen, info.inlineDecim );
					} else {
						info.ste.read( chunkSpan, tmpBuf2, 0 );
						decimateHalfPeakRMS( tmpBuf2, frames, off, decimLen, info.inlineDecim );
					}
					break;
					
//				case MODEL_MEDIAN:
//					info.ste.read( chunkSpan, tmpBuf, 0 );
//					decimateMedian( tmpBuf, frames, off, decimLen, info.inlineDecim );
//					break;
					
				default:
					assert false : model;
				}
				off			+= decimLen;
				start		+= fullLen;
				totalLength -= fullLen;
			} while( totalLength > 0 );
		}
	}
	
	/**
	 *  Dumps the list of regions
	 *  of each subsample editor to the console.
	 */
    public void debugDump()
    {
		for( int i = 0; i < SUBNUM; i++ ) {
			System.out.println( "MultirateTrackEditor : decimation "+decimations[i] );
			ste[i].debugDump();
        }
    }

	/**
	 *  Paints a scheme of the list of regions
	 *  of each subsample editor to a given graphics context.
	 *
	 *  @param  g   where to paint
	 *  @param  x   x offset to use in paint operation
	 *  @param  y   y offset to use in paint operation
	 *  @param  w   total width to use in paint operation
	 *  @param  h   total height to use in paint operation
	 */
	public void debugPaint( Graphics2D g, int x, int y, int w, int h )
    {
		int dh = h / (SUBNUM + 1 );
		
		h = dh - 4;
	
		for( int i = 0; i < SUBNUM; i++ ) {
			ste[i].debugPaint( g, x, y, w, h );
			y += dh;
        }
    }
	
	private void subsampleInsert( Span tag, SyncCompoundEdit ce, ProgressComponent pc, float progOff, float progWeight )
    throws IOException
	{
		Span		extendedSpan= new Span( tag.getStart() & MAXMASK, (tag.getStop() + MAXCEILADD) & MAXMASK );
		TrackSpan[]	ts			= new TrackSpan[SUBNUM];
		int			len, i, ch;
		float		f1;
		Span		tag2;
		long		pos			= extendedSpan.getStart();
		long		insertLen	= extendedSpan.getLength();
		long		fullrateStop= Math.min( extendedSpan.getStop(), ste[0].getFrameNum() );
		long		fullrateLen = fullrateStop - extendedSpan.getStart();
		int			numFullBuf  = (int) (fullrateLen >> MAXSHIFT);
		
		progWeight /= insertLen;
		progOff	   -= pos * progWeight;
		
		for( i = 1; i < SUBNUM; i++ ) {
			ts[i] = ste[i].beginInsert( extendedSpan, ce );
		}

		for( i = 0; i < numFullBuf; i++ ) {
			tag2 = new Span( pos, pos + MAXCOARSE );
			ste[0].read( tag2, tmpBuf, 0 );
			subsampleWrite( tmpBuf, tmpBuf2, ts, MAXCOARSE );
			pos += MAXCOARSE;
			if( pc != null ) {
				pc.setProgression( pos * progWeight + progOff );
			}
		}

		len = (int) (fullrateStop - pos);
		if( len > 0 ) {
			tag2 = new Span( pos, pos + len );
			ste[0].read( tag2, tmpBuf, 0 );
			for( ch = 0; ch < channels; ch++ ) {
				f1 = tmpBuf[ch][len-1];
				for( i = len; i < MAXCOARSE; i++ ) {
					tmpBuf[ch][i] = f1;
				}
			}
			subsampleWrite( tmpBuf, tmpBuf2, ts, MAXCOARSE );
			pos += len;
			if( pc != null ) {
				pc.setProgression( pos * progWeight + progOff );
			}
		}
		
		// we need to remove the obsolete material
		// that was replaced by pre/postroll
 		tag2 = new Span( pos, pos + insertLen - tag.getLength() );

		for( i = 1; i < SUBNUM; i++ ) {
			ste[i].finishWrite( ts[i] );
			ste[i].remove( tag2, ce );		// XXX arbeitsweise ueberpruefen!
		}
    }

	private void decimatePCMtoHalfPeakRMS( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim )
	{
		int		stop, j, k, m, ch;
		float   f1, f2, f3, f4, f5;
		float[] inBufCh1, outBufCh1, outBufCh2, outBufCh3, outBufCh4;

		for( ch = 0; ch < channels; ch++ ) {
			inBufCh1 = inBuf[ch];

			outBufCh1 = outBuf[ch];
			outBufCh2 = outBuf[ch + channels];
			outBufCh3 = outBuf[ch + 2*channels];
			outBufCh4 = outBuf[ch + 3*channels];

			for( j = outOff, stop = outOff + len, k = 0; j < stop; j++ ) {
				f5 = inBufCh1[k++];
				if( f5 >= 0.0f ) {
					f1 = f5;
					f3 = f5*f5;
					f2 = 0.0f;
					f4 = 0.0f;
				} else {
					f2 = f5;
					f4 = f5*f5;
					f1 = 0.0f;
					f3 = 0.0f;
				}
				for( m = 1; m < decim; m++ ) {
					f5 = inBufCh1[k++];
					if( f5 >= 0.0f ) {
						if( f5 > f1 ) f1 = f5;
						f3 += f5*f5;
					} else {
						if( f5 < f2 ) f2 = f5;
						f4 += f5*f5;
					}
				}
				outBufCh1[j]	= f1;			// positive halfwave peak
				outBufCh2[j]	= f2;			// negative halfwave peak
				outBufCh3[j]	= f3 / decim;	// positive halfwave mean square
				outBufCh4[j]	= f4 / decim;	// negative halfwave mean square
			}
		} // for( ch )
	}

	private void decimateHalfPeakRMS( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim )
	{
		int		stop, j, k, m, ch;
		float   f1, f2, f3, f4, f5;
		float[] inBufCh1, inBufCh2, inBufCh3, inBufCh4, outBufCh1, outBufCh2, outBufCh3, outBufCh4;

		for( ch = 0; ch < channels; ch++ ) {
			inBufCh1 = inBuf[ch];
			inBufCh2 = inBuf[ch + channels];
			inBufCh3 = inBuf[ch + 2*channels];
			inBufCh4 = inBuf[ch + 3*channels];

			outBufCh1 = outBuf[ch];
			outBufCh2 = outBuf[ch + channels];
			outBufCh3 = outBuf[ch + 2*channels];
			outBufCh4 = outBuf[ch + 3*channels];

			for( j = outOff, stop = outOff + len, k = 0; j < stop; j++ ) {
				f1 = inBufCh1[k];
				f2 = inBufCh2[k];
				f3 = inBufCh3[k];
				f4 = inBufCh4[k];
				for( m = k + decim, k++; k < m; k++ ) {
					f5 = inBufCh1[k];
					if( f5 > f1 ) f1 = f5;
					f5 = inBufCh2[k];
					if( f5 < f2 ) f2 = f5;
					f3 += inBufCh3[k];
					f4 += inBufCh4[k];
				}
				outBufCh1[j]	= f1;			// positive halfwave peak
				outBufCh2[j]	= f2;			// negative halfwave peak
				outBufCh3[j]	= f3 / decim;	// positive halfwave mean square
				outBufCh4[j]	= f4 / decim;	// negative halfwave mean square
			}
		} // for( ch )
	}
	
	private void decimateMedian( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim )
	{	
		int		stop, j, k, ch;
		float   f1, f2, f3, f4, f5;
		float[] inBufCh1, outBufCh1;

		assert decim == 4 : decim;

		for( ch = 0; ch < channels; ch++ ) {
			inBufCh1  = inBuf[ch];
			outBufCh1 = outBuf[ch];

			for( j = outOff, stop = outOff + len, k = 0; j < stop; j++ ) {
				f1 = inBufCh1[k++];
				f2 = inBufCh1[k++];
				f3 = inBufCh1[k++];
				f4 = inBufCh1[k++];
				
				// calculate the median of four successive frames
				if( f1 > f2 ) {
					f5 = f1;
					f1 = f2;
					f2 = f5;
				}
				if( f2 > f3 ) {
					if( f1 > f3 ) {
						f5 = f1;
						f1 = f3;
						f3 = f2;
						f2 = f5;
					} else {
						f5 = f2;
						f2 = f3;
						f3 = f5;
					}
				}
				if( f3 > f4 ) {
					if( f2 > f4 ) {
						if( f1 > f4 ) {
							outBufCh1[j] = (f1 + f2) / 2;
						} else {
							outBufCh1[j] = (f4 + f2) / 2;
						}
					} else {
						outBufCh1[j] = (f2 + f4) / 2;
					}
				} else {
					outBufCh1[j] = (f2 + f3) / 2;
				}
			}
		} // for( ch )
	}
	
	/*
	 *  This is invoked by insert(). it subsamples
	 *  the given buffer for all subsample STEs
	 *  and writes it out using continueWrite;
	 *  therefore the call to this method should
	 *  be bracketed with beginInsert() and finishWrite().
	 *  len must be an integer muliple of MAXCOARSE !
	 */
	private void subsampleWrite( float[][] inBuf, float[][] outBuf, TrackSpan[] ts, int len )
	throws IOException
	{
		int	i, decim;
		
		if( SUBNUM < 2 ) return;
		
		switch( model ) {
		case MODEL_HALFWAVE_PEAKRMS:
			decim	= decimations[1];
//System.err.println( "subsampleWrite len "+len+"; first decim = "+decim );
//System.err.println( "len = "+len+"; inBuf.length = "+inBuf[0].length+"; outBuf.length = "+outBuf[0].length );
			// calculate first decimation from fullrate PCM
			len		= len >> decim;
			decimatePCMtoHalfPeakRMS( inBuf, outBuf, 0, len, 1 << decim );
//System.err.println( "continue write 1 : "+len );
			ste[1].continueWrite( ts[1], outBuf, 0, len );

			// calculate remaining decimations from preceding ones
			for( i = 2; i < SUBNUM; i++ ) {
				decim = decimations[i] - decimations[i-1];
				len >>= decim;
//				System.err.println( "continue write "+i+" : "+len );
				decimateHalfPeakRMS( outBuf, outBuf, 0, len, 1 << decim );
				ste[i].continueWrite( ts[i], outBuf, 0, len );
			} // for( SUBNUM )
			break;

		case MODEL_MEDIAN:
			for( i = 1; i < SUBNUM; i++ ) {
				decim = decimations[i] - decimations[i-1];
				len >>= decim;
				decimateMedian( inBuf, outBuf, 0, len, 1 << decim );
				ste[i].continueWrite( ts[i], outBuf, 0, len );
				inBuf = outBuf;
			} // for( SUBNUM )
			break;

		default:
			assert false : model;
			break;
		}
	}

	/*
	 *  This is invoked by insert(). it subsamples
	 *  the given buffer for all subsample STEs
	 *  and writes it out using continueWrite;
	 *  therefore the call to this method should
	 *  be bracketed with beginInsert() and finishWrite().
	 *  len must be an integer muliple of MAXCOARSE !
	 */
//	private void subsampleWrite__( float[][] buf, TrackSpan[] ts, int len )
//	throws IOException
//	{
//		int		i, j, k, ch;
//		float   f1, f2, f3, f4, f5;
//		float[] chBuf;
//		
//		for( i = 1; i < SUBNUM; i++ ) {
//			len >>= 2;
//			for( ch = 0; ch < channels; ch++ ) {
//				chBuf = buf[ch];
//				for( j = 0, k = 0; j < len; ) {
//					f1 = chBuf[k++];
//					f2 = chBuf[k++];
//					f3 = chBuf[k++];
//					f4 = chBuf[k++];
//					
//					// calculate the median of four successive frames
//					if( f1 > f2 ) {
//						f5 = f1;
//						f1 = f2;
//						f2 = f5;
//					}
//					if( f2 > f3 ) {
//						if( f1 > f3 ) {
//							f5 = f1;
//							f1 = f3;
//							f3 = f2;
//							f2 = f5;
//						} else {
//							f5 = f2;
//							f2 = f3;
//							f3 = f5;
//						}
//					}
//					if( f3 > f4 ) {
//						if( f2 > f4 ) {
//							if( f1 > f4 ) {
//								chBuf[j++] = (f1 + f2) / 2;
//							} else {
//								chBuf[j++] = (f4 + f2) / 2;
//							}
//						} else {
//							chBuf[j++] = (f2 + f4) / 2;
//						}
//					} else {
//						chBuf[j++] = (f2 + f3) / 2;
//					}
//				}
//			} // for( ch )
//			ste[i].continueWrite( ts[i], buf, 0, len );
//		}
//	}
} // class MultirateTrackEditor