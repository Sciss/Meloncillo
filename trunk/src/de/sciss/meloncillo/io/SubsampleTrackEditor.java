/*
 *  SubsampleTrackEditor.java
 *  Meloncillo
 *
 *  Copyright (c) 2004-2005 Hanns Holger Rutz. All rights reserved.
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
 *		31-Jan-05   created from de.sciss.eisenkraut
 */

package de.sciss.meloncillo.io;

import java.awt.*;
import java.io.*;
import java.util.*;

import de.sciss.meloncillo.edit.*;

import de.sciss.app.*;
import de.sciss.io.*;

/**
 *  This class provides means for nondestructive
 *  nonlinear track editing. Internally, data streams
 *  are referenced by a list of regions which contain
 *  pointers into normal or temporary files. When cut
 *  or insert operations are performed, conherent regions
 *  are split up into two new regions. A <code>flatten</code>
 *  method is provided to create a coherent single file
 *  again from a (possibly fragmented) regions list.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	MultirateTrackEditor
 */
public class SubsampleTrackEditor
{
    private final int				channels;
    private final int				shift;
	private final int				roundAdd;
	private final long				mask;
    private InterleavedStreamFile	tempF		= null;
	private final java.util.List	collRegions = new ArrayList();

	public SubsampleTrackEditor( int channels, int shift )
	{
        this.channels		= channels;
		this.shift			= shift;
		roundAdd			= (1 << shift) >> 1;
		mask				= -(1 << shift);
	}
    
	/**
	 *  Returns the decimation
	 *  as a bitshift value.
	 *
	 *  @return the bitshift value used to
	 *			calcuate the decimation rate.
	 *			That is, <code>decimatedRate = fullRate >> returnedShift</code>
	 */
	public int getShift()
	{
		return shift;
	}

	/**
	 *  Converts a frame length from full rate to
	 *  decimated rate and rounds to nearest integer.
	 *
	 *  @param  full	number of frame at full rate
	 *  @return number of frames at this editor's decimated rate
	 */
	public long fullrateToSubsample( long full )
	{
		return( (full + roundAdd) >> shift );
	}
	
	/**
	 *  Clears the editor such that the time span
	 *  is empty. Deletes the temp file contents.
	 *
	 *  @throws IOException if an error occurs
	 */
    public void clear()
    throws IOException
    {
		collRegions.clear();
		if( tempF != null ) {
			tempF.seekFrame( 0 );
			tempF.truncate();
		}
    }

	/**
	 *  Returns the total length of the editor
	 *  which is the span between the <code>start</code> of the
	 *  first region and the <code>stop</code> of the last region.
	 *
	 *  @return the number of frames in this editor
	 */
	public long getFrameNum()
	{
		if( collRegions.size() > 0 ) {
			return ((TrackSpan) collRegions.get( collRegions.size() - 1 )).span.getStop();
		} else {
			return 0;
		}
	}
	
	public int getChannelNum()
	{
		return channels;
	}

	/**
	 *  Returns a track list for a given
	 *  time span. A track list describes the
	 *  possibly fragmented regions which form
	 *  a continuous block of time span data.
	 *  This can be used for copy and paste applications.
	 *
	 *  @param  span	the span for which a tracklist
	 *					should be constructed.
	 *  @return a newly constructed tracklist for the given time span
	 */
	public TrackList getTrackList( Span span )
	{
		TrackList		tl = new TrackList();
		int				i, j;
		TrackSpan bts, bts2;
		
		i = indexOf( span.getStart(), 0 );
		if( i == -1 ) return tl;
		j = indexOf( span.getStop(), i );
		if( j == -1 ) j = collRegions.size() - 1;
		
		bts = (TrackSpan) collRegions.get( i );
		if( bts.span.getStart() != span.getStart() ) {
			bts2 = replaceStart( bts, span.getStart() );
			if( i == j ) {
				bts2 = replaceStop( bts2, span.getStop() );
				tl.add( bts2 );
				return tl;
			}
			tl.add( bts2 );
			i++;
		}

		for( ; i < j; i++ ) {
			bts  = (TrackSpan) collRegions.get( i );
			bts2 = new TrackSpan( bts );
			tl.add( bts2 );
		}
		
		bts  = (TrackSpan) collRegions.get( i );
		bts2 = replaceStop( bts, span.getStop() );
		tl.add( bts2 );
		
		return tl;
	}

   /**
     *  Copy the (possibly fragmented) trackspans,
     *  i.e. data from temporary files to one continuous
     *  file. this is typically called when a
	 *  session is saved. The data is written out starting
	 *  at the current seek position in the passed file,
	 *  so in most cases the caller would reset the file
	 *  position before flattening.
	 *
	 *  @param  f   the file into which the flattened data is written
	 *  @throws IOException if a read or write error occurs
	 *  @todo   when the file is flattened, the region list
	 *			should be cleared and replaced by the sole
	 *			coherent flat file
     */
	public void flatten( InterleavedStreamFile f )
    throws IOException
    {
        TrackSpan   ts;
		int			i;
        
        for( i = 0; i < collRegions.size(); i++ ) {
            ts = (TrackSpan) collRegions.get( i );
			ts.f.seekFrame( ts.offset );
			ts.f.copyFrames( f, ts.span.getLength() );
        }
    }

	/**
	 *  Inserts data from this or another editor using a
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
	 *						the implied harddisc files.
	 *  @param  ce			a compound edit to which this new insert edit is
	 *						added.
	 *  @throws IOException when a write error occurs
	 */
	public void insert( long position, TrackList tl, SyncCompoundEdit ce )
    throws IOException
	{
        int						i, j;
        long					delta   = position - tl.getSpan().getStart();
		long					delta2;
        TrackSpan				bts, bts2, bts3;
		EditChangeTrackList		edit	= new EditChangeTrackList();
      
//System.err.println( "NTE insert tracklist at "+position );
		j = 0;
		if( j == tl.size() ) return;

        i = indexOf( position, 0 );
        if( i >= 0 ) {  // insert somewhere between old trackspans
			bts		= tl.get( j ).shiftVirtual( delta );
			bts2    = (TrackSpan) collRegions.get( i );
			delta2  = bts.span.getStart() - bts2.span.getStart();
			if( delta2 > 0 ) { // split old ts
				bts3 = replaceStop( bts2, bts.span.getStart() );
				edit.addReplace( i, bts2, bts3 );
//System.err.println( "replaced (trunc) #"+i+" : "+ts2.span.getStart() + " ... "+ts2.span.getStop() +"   by   "+ts3.span.getStart() + " ... "+ts3.span.getStop() );
				collRegions.set( i, bts3 );
				bts3 = replaceStart( bts2, bts.span.getStart() );
				i++;
				edit.addAdd( i, bts3 );
				collRegions.add( i, bts3 );
//System.err.println( "added (trunc) #"+i+" : "+ts3.span.getStart() + " ... "+ts3.span.getStop() );
			}
		} else {  // insert at the very end
			i = collRegions.size();
		}

		// insert all tl entries
		for( ; j < tl.size(); j++, i++ ) {
			bts = tl.get( j ).shiftVirtual( delta );
//System.err.println( "added #"+j+" ; shifted : "+ts.span.getStart()+" ... "+ts.span.getStop() );
			edit.addAdd( i, bts );
			collRegions.add( i, bts );
		}
		
		// shift all successive regions' time tags
        delta2 = tl.getSpan().getLength();
        for( ; i < collRegions.size(); i++ ) {
			bts2 = (TrackSpan) collRegions.get( i );
			bts3 = bts2.shiftVirtual( delta2 );
			edit.addReplace( i, bts2, bts3 );
			collRegions.set( i, bts3 );
//System.err.println( "replaced (shift) #"+i+" : "+ts2.span.getStart() + " ... "+ts2.span.getStop() +"   by   "+ts3.span.getStart() + " ... "+ts3.span.getStop() );
		}
		
		ce.addEdit( edit );
	}

    public void insert( InterleavedStreamFile f, long offset, Span tag )
    throws IOException
    {
        collInsert( createBiasedTrackSpan( tag, f, offset ), null );
    }

// XXX	public void flatten( InterleavedStreamFile f )

    public void insert( Span tag, float[][] frames )
    throws IOException
    {
		this.insert( tag, frames, null );
	}

	/**
	 *  Inserts a single block of decimated frame data. The
	 *  operation is attached to the provided compound edit
	 *  making undo possible.
	 *
	 *  @param  tag		the time span to insert. The new frames are inserted
	 *					at tag.getStart(), the length of the inserted block is
	 *					tag.getLength(), thus all data previously starting at
	 *					tag.getStart() or later, will be shifted by tag.getLength().
	 *  @param  frames  data to write where frame[0][] is channel0 etc. frame offset
	 *					is presumed to be zero, frame length is determined from
	 *					tag.getLength() by decimation. Don't call this directly,
	 *					but use the MultirateSubsampleTrackEditor method instead.
	 *  @param  edit	a compound edit to which this new insert edit is
	 *					added.
	 *  @throws IOException when a write error occurs
	 */
    public void insert( Span tag, float[][] frames, SyncCompoundEdit edit )
    throws IOException
    {
        InterleavedStreamFile   f       = prepareTempFile();
        long                    offset  = f.getFramePosition();
		TrackSpan				bts		= createBiasedTrackSpan( tag, f, offset );
        int						delta	= (int) (bts.getBiasedLength() >> shift);

        f.writeFrames( frames, 0, delta );
        collInsert( bts, edit );
    }

    /**
     *  Returns the tempfile with write position at the very end.
	 *  If the temp file does not exist, it will be automatically
	 *  created.
	 *
	 *  @return the temp file to append data to
	 *  @throws IOException when the file cannot be created or a seek error occurs
     */
    private InterleavedStreamFile prepareTempFile()
    throws IOException
    {
        if( tempF == null ) {
			// simply use an AIFC file with float format as temp file
			AudioFileDescr afd	= new AudioFileDescr();
			afd.type			= AudioFileDescr.TYPE_AIFF;
			afd.channels		= this.channels;
			afd.rate			= 1000.0f;	// XXX
			afd.bitsPerSample	= 32;
			afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
//			afd.bitsPerSample	= 8;
//			afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
			afd.file			= IOUtil.createTempFile();
			tempF				= AudioFile.openAsWrite( afd );
        } else {
            tempF.seekFrame( tempF.getFrameNum() );
        }
        return tempF;
    }

	public TrackSpan beginInsert( Span tag )
    throws IOException
	{
		return this.beginInsert( tag, null );
	}

	public TrackSpan beginInsert( Span tag, SyncCompoundEdit edit )
    throws IOException
	{
        InterleavedStreamFile   f       = prepareTempFile();
        long                    offset  = f.getFramePosition();
		TrackSpan				bts		= createBiasedTrackSpan( tag, f, offset );

        collInsert( bts, edit );

		return bts;

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
		ts.f.writeFrames( frames, offset, length );
	}

	/**
	 *  Finishes a multi-block-write operation.
	 *
	 *  @param  ts		the object returned from the initial beginInsert or beginOverwrite
	 *					call
	 *  @throws IOException when a write error occurs
	 *  @see	#beginInsert( Span )
	 *  @see	#continueWrite( TrackSpan, float[][], int, int )
	 */
	public void finishWrite( TrackSpan ts )
    throws IOException
	{
		// important to override super method which would throw an IllegalStateException
	}

    public void remove( Span tag )
    throws IOException
    {
		this.remove( tag, null );
    }

    public void remove( Span tag, SyncCompoundEdit ce )
    throws IOException
    {
        int						i, j;
        TrackSpan			bts, bts2;
        long					start   = tag.getStart();
        long					stop    = tag.getStop();
        long					delta;
		EditChangeTrackList	edit	= ce == null ? null : new EditChangeTrackList();
        
        i = indexOf( start, 0 );
        if( i == -1 ) return;
        
        bts = (TrackSpan) collRegions.get( i );
        delta = start - bts.span.getStart();
        if( delta > 0 ) { // split old ts
            bts2 = replaceStop( bts, start );
			if( ce != null ) edit.addReplace( i, bts, bts2 );
			collRegions.set( i, bts2 );
			bts2 = replaceStart(bts,  start );
			i++;
			if( ce != null ) edit.addAdd( i, bts2 );
			collRegions.add( i, bts2 );
        }

        j = indexOf( stop, i );
        if( j == -1 ) { // remove everything till the very end
            for( j = collRegions.size() - 1; j >= i; j-- ) {
				bts2 = (TrackSpan) collRegions.remove( j );
				if( ce != null ) edit.addRemove( j, bts2 );
            }
        } else {   
            for( j--; j >= i; j-- ) {
				bts2 = (TrackSpan) collRegions.remove( j );
				if( ce != null ) edit.addRemove( j, bts2 );
            }
            bts  = (TrackSpan) collRegions.get( i );
			bts2 = replaceStart( bts, stop );	// trunc old ts
			if( ce != null ) edit.addReplace( i, bts, bts2 );
			collRegions.set( i, bts2 );
			
            delta = -tag.getLength();
            for( ; i < collRegions.size(); i++ ) {   // shift all successive regions' time btags
                bts  = (TrackSpan) collRegions.get( i );
				bts2 = bts.shiftVirtual( delta );
				if( ce != null ) edit.addReplace( i, bts, bts2 );
				collRegions.set( i, bts2 );
            }
        }
		
		if( ce != null ) ce.addEdit( edit );
    }

    public void read( Span tag, float[][] frames, int framesOff )
    throws IOException
    {
        int				i, j, k, ch, len, oldOff;
        TrackSpan bts;
		long			start		= tag.getStart();
        long			currentPos  = start;
        long			stop		= tag.getStop();
        int				off			= 0;
		long			maxLen		= (stop - start + roundAdd) >> shift;
		long			startPos;

        i = indexOf( start, 0 );
        if( i == -1 ) return;
        j = indexOf( stop, i );
        if( j == -1 ) j = collRegions.size() - 1;

        for( ; i <= j; i++ ) {
            bts     = (TrackSpan) collRegions.get( i );
			startPos= ((currentPos - bts.getBiasedStart() + roundAdd) >> shift);
			len		= (int) Math.min( maxLen - off, ((bts.getBiasedLength() + roundAdd) >> shift) - startPos );

			bts.f.seekFrame( bts.offset + startPos );
			bts.f.readFrames( frames, off + framesOff, len ); 

            currentPos  = Math.min( stop, bts.span.getStop() );
			oldOff		= off + len;
			off			= (int) ((currentPos - start + roundAdd) >> shift);
			if( oldOff < off && len > 0 ) {
				for( k = oldOff - 1; oldOff < off; oldOff++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						frames[ch][oldOff] = frames[ch][k];
					}
				}
			}
        }
    }

	/**
	 *  Dumps the list of regions in the editor.
	 */
    public void debugDump()
    {
        int				i;
		TrackSpan bts;
        
		if( tempF != null ) {
			try {
				System.err.println( "Tempfile length "+tempF.getFrameNum() );
			} catch( IOException e1 ) {}
		}
        for( i = 0; i < collRegions.size(); i++ ) {
            bts  = (TrackSpan) collRegions.get( i );
            System.err.println( "Region #"+i+" (hash "+bts.hashCode()+") : "+bts.span.getStart()+
								" (+"+bts.startBias+") ... "+bts.span.getStop()+" (+"+bts.stopBias+
								"); file offset = "+bts.offset+"; biasedLength "+bts.getBiasedLength() );
        }
    }
	
	/**
	 *  Paints a scheme of the list of regions in the editor
	 *  to a given graphics context.
	 *
	 *  @param  g   where to paint
	 *  @param  x   x offset to use in paint operation
	 *  @param  y   y offset to use in paint operation
	 *  @param  w   total width to use in paint operation
	 *  @param  h   total height to use in paint operation
	 */
	public void debugPaint( Graphics2D g, int x, int y, int w, int h )
	{
//        int         i;
//        TrackSpan   ts;
//		double		scaleX;
//		Color		c1  = new Color( 0x7F, 0x00, 0x00, 0x4F );
//		Color		c2  = new Color( 0x00, 0x00, 0x7F, 0x4F );
//		Color		c3  = new Color( 0x00, 0x7F, 0x00, 0x4F );
//		Color		c4  = new Color( 0x4F, 0x4F, 0x4F, 0x4F );
//		Rectangle2D rect;
//		double		y1  = y + (double) h * 0.125;
//		double		y2  = y + (double) h * 0.375;
//		double		h1  = (double) h * 0.5;
//		boolean		b;
//
//		if( collRegions.size() == 0 ) {
//			g.drawLine( x, y, x + w, y + h );
//			return;
//		}
//        
//		scaleX = (double) w / (double) ((TrackSpan) collRegions.lastElement()).span.getStop();
//		
//        for( i = 0; i < collRegions.size(); i++ ) {
//			b		= (i % 2) == 0;
//			g.setColor( b ? c1 : c2 );
//            ts		= (TrackSpan) collRegions.get( i );
//			g.drawString( "#"+i+"; "+ts.span.getStart()+" - "+ts.span.getStop(), (float) (x + 4 + ts.span.getStart() * scaleX), (float) (y + 12) );
//			rect	= new Rectangle2D.Double( x + ts.span.getStart() * scaleX, y, ts.span.getLength() * scaleX, h );
//			g.draw( rect );
//			g.setColor( b ? c3 : c4 );
//			rect	= new Rectangle2D.Double( x + ((BiasedSpan) ts.span).getBiasedStart() * scaleX, b ? y1 : y2, ((BiasedSpan) ts.span).getBiasedLength() * scaleX, h1 );
//			g.draw( rect );
//			g.drawString( ((BiasedSpan) ts.span).getStartBias()+" / "+((BiasedSpan) ts.span).getStopBias(), (float) (x + 16 + ts.span.getStart() * scaleX), (float) ((b ? y1 : y2) + 12) );
//        }
	}

    /**
     *  Gets the element number in collRegions of the region
     *  containing the given position
	 *
	 *  @param  position	frame offset to find. starting at
	 *						region <code>startIdx</code>, each track span's span
	 *						is investigated, where its start is
	 *						considered inclusive and stop is
	 *						considered exclusive.
	 *  @param  startIdx	the first region to look at.
	 *  @return the found index or -1 if the position is
	 *			greater or equal than the last's element stop tag
	 *  @see	de.sciss.meloncillo.util.Span#contains( long )
     */
    private int indexOf( long position, int startIdx )
    {
        int				i;
        TrackSpan bts;
        
        for( i = startIdx; i < collRegions.size(); i++ ) {
            bts = (TrackSpan) collRegions.get( i );
            if( bts.span.getStop() > position ) return i;
        }
        return -1;
    }

    /**
     *  Adds a new TrackSpan to the collection. Uses an
	 *  additional compound edit to make undo possible.
	 *  
	 *  @param  ts  the track span to insert. The index
	 *				in the collection is automatically calculated
	 *				and if the track span's beginning falls into
	 *				a coherent region, that region will be split
	 *				into two regions accordingly. Regions starting
	 *				after the new span's beginning will be shifted to
	 *				the end by the track span's length.
	 *  @param  ce	an appropriate undo action will be
	 *				added to the CompoundEdit
     */
    private void collInsert( TrackSpan bts, SyncCompoundEdit ce )
    {
        long					start   = bts.span.getStart();
        int						i;
        long					delta;
        TrackSpan				bts2, bts3;
		EditChangeTrackList		edit	= ce == null ? null : new EditChangeTrackList();
      
        i = indexOf( start, 0 );
        if( i == -1 ) { // add to the very ending
			if( ce != null ) {
				edit.addAdd( collRegions.size(), bts );
				ce.addEdit( edit );
			}
			collRegions.add( bts );
            return;
        }

        bts2    = (TrackSpan) collRegions.get( i );
        delta   = start - bts2.span.getStart();
        if( delta > 0 ) { // split old ts
            bts3 = replaceStop( bts2, start );
			if( ce != null ) edit.addReplace( i, bts2, bts3 );
			collRegions.set( i, bts3 );
			bts3 = replaceStart( bts2, start );
			i++;
			if( ce != null ) edit.addAdd( i, bts3 );
			collRegions.add( i, bts3 );
        }

		if( ce != null ) {
			edit.addAdd( i, bts );
		}
		collRegions.add( i, bts );

        delta = bts.span.getLength();
        for( ++i; i < collRegions.size(); i++ ) {   // shift all successive regions' time tags
			bts2 = (TrackSpan) collRegions.get( i );
			bts3 = bts2.shiftVirtual( delta );
			if( ce != null ) edit.addReplace( i, bts2, bts3 );
			collRegions.set( i, bts3 );
		}
		
		if( ce != null ) ce.addEdit( edit );
	}

	private TrackSpan replaceStart( TrackSpan old, long newStart )
	{
		long newBiasedStart = (newStart + roundAdd) & mask;
		long newOffset		= old.offset + ((newBiasedStart - (old.span.getStart() + old.startBias)) >> shift);
	
		return new TrackSpan( new Span( newStart, old.span.getStop() ), old.f, newOffset,
									(int) (newBiasedStart - newStart), old.stopBias );
	}

	private TrackSpan replaceStop( TrackSpan old, long newStop )
	{
		long newBiasedStop = (newStop + roundAdd) & mask;
	
		return new TrackSpan( new Span( old.span.getStart(), newStop ), old.f, old.offset,
									old.startBias, (int) (newBiasedStop - newStop) );
	}

	public TrackSpan createBiasedTrackSpan( Span tag, InterleavedStreamFile f, long offset )
	{
		return new TrackSpan( tag, f, offset, (int) (((tag.getStart() + roundAdd) & mask) - tag.getStart()),
											  (int) (((tag.getStop() + roundAdd) & mask) - tag.getStop()) );
	}
	

	private static final int EDIT_ADD			= 0;
	private static final int EDIT_REPLACE		= 1;
	private static final int EDIT_REMOVE		= 2;

	private class EditChangeTrackList
	extends BasicUndoableEdit
	{
		private final java.util.List ops  = new ArrayList();
	
		private void addAdd( int index, TrackSpan newBTS )
		{
			TrackListOperation  nteco   = new TrackListOperation( EDIT_ADD, index, null, newBTS );
			ops.add( nteco );
		}

		private void addRemove( int index, TrackSpan oldBTS )
		{
			TrackListOperation  nteco   = new TrackListOperation( EDIT_REMOVE, index, oldBTS, null );
			ops.add( nteco );
		}
		
		private void addReplace( int index, TrackSpan oldBTS, TrackSpan newBTS )
		{
			TrackListOperation  nteco   = new TrackListOperation( EDIT_REPLACE, index, oldBTS, newBTS );
			ops.add( nteco );
		}
		
		private void perform()
		{
			int						i;
			TrackListOperation  nteco;
			
			for( i = 0; i < ops.size(); i++ ) {
				nteco  = (TrackListOperation) ops.get( i );
				switch( nteco.ID ) {
				case EDIT_ADD:
					collRegions.add( nteco.index, nteco.newBTS );
					break;
				case EDIT_REPLACE:
					collRegions.set( nteco.index, nteco.newBTS );
					break;
				case EDIT_REMOVE:
					collRegions.remove( nteco.index );
					break;
				default:
					assert false : nteco.ID;
				}
			}
		}
		
		public void undo()
		{
			super.undo();
			
			int						i;
			TrackListOperation  nteco;
			
			for( i = ops.size() - 1; i >= 0 ; i-- ) {
				nteco  = (TrackListOperation) ops.get( i );
				switch( nteco.ID ) {
				case EDIT_ADD:
					collRegions.remove( nteco.index );
					break;
				case EDIT_REPLACE:
					collRegions.set( nteco.index, nteco.oldBTS );
					break;
				case EDIT_REMOVE:
					collRegions.add( nteco.index, nteco.oldBTS );
					break;
				default:
					assert false : nteco.ID;
					break;
				}
			}
		}
		
		public void redo()
		{
			super.redo();
			perform();
		}
		
		public String getPresentationName()
		{
			return AbstractApplication.getApplication().getResourceString( "editChangeTrackStructure" );
		}
	}

	private class TrackListOperation
	{
		private final int				ID;
		private final int				index;
		private final TrackSpan	oldBTS, newBTS;
		
		private TrackListOperation( int ID, int index, TrackSpan oldBTS, TrackSpan newBTS )
		{
			this.ID		= ID;
			this.index	= index;
			this.oldBTS	= oldBTS;
			this.newBTS = newBTS;
		}
	}
} // class SubsampleTrackEditor