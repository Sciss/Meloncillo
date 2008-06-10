/*
 *  AudioFile.java
 *  de.sciss.io package
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
 *		21-May-05	created from de.sciss.eisenkraut.io.AudioFile
 */

package de.sciss.io;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import de.sciss.app.AbstractApplication;

/**
 *  Though Meloncillo does not really deal with
 *  audio, it was important to have audio file
 *  i/o in order to communicate with plug-ins.
 *  <p>
 *  The <code>AudioFile</code> class is a wrapper
 *  for a <code>RandomAccessFile</code> and supports
 *  a few common audio formats: AIFF, IRCAM and
 *  NeXT/Sun (.au). Supported resolutions are
 *  8/16/24/32 bit integer and 32/64 bit floating point.
 *  However not all audio formats support all bit depth.
 *  <p>
 *  In order to simplify communication with CSound,
 *  raw output files are supported, raw input files however
 *  are not recognized.
 *  <p>
 *  To create a new <code>AudioFile</code> you call
 *  one of its static methods <code>openAsRead</code> or
 *  <code>openAsWrite</code>. The format description
 *  is handled by an <code>AudioFileDescr</code> object.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.10, 21-May-05
 *
 *  @see		AudioFileDescr
 *
 *  @todo		all files are considered big endian at the
 *				moment which might be inconvenient on non
 *				mac systems because there are for example
 *				different endian versions for ircam and we
 *				cannot read the little endian one. aiff however
 *				is per se big endian and should therefore cause
 *				no trouble.
 *
 *	@todo		WAVE very basic, no marker support at the moment
 *				; WAVE 8 bit not supported (egal)
 */
public class AudioFile
implements InterleavedStreamFile
{
// -------- public Variablen --------

	public static final int MODE_READONLY   = 0;
	public static final int MODE_READWRITE  = 1;

// -------- private Variablen --------

	private final RandomAccessFile	raf;
	private final FileChannel		fch;
	private AudioFileDescr			afd;
	private AudioFileHeader			afh;
	private final int				mode;
	
	private ByteBuffer				byteBuf;
	private int						byteBufCapacity;
	private int						bytesPerFrame;
	private int						frameBufCapacity;
	private BufferHandler			bh;
	private int						channels;
	private long					framePosition;
	
	private long					updateTime;
	private long					updateLen;
	private int						updateStep;

	private static final String	NAME_LOOP		= "loop";

// -------- public Methoden --------

	/**
	 *  Opens an audio file for reading.
	 *
	 *  @param		f   the path name of the file
	 *  @return		a new <code>AudioFile</code> object
	 *				whose header is already parsed and can
	 *				be obtained through the <code>getDescr</code> method.
	 *
	 *  @throws IOException if the file was not found, could not be read
	 *						or has an unknown or unsupported format
	 */
	public static AudioFile openAsRead( File f )
	throws IOException
	{
		AudioFile af	= new AudioFile( f, MODE_READONLY );
		af.afd			= new AudioFileDescr();
		af.afd.file		= f;
		af.afd.type		= af.retrieveType();
		af.afh			= af.createHeader();
		af.afh.readHeader( af.afd );
		af.init();
		af.seekFrame( 0 );
		return af;
	}
	
	/**
	 *  Opens an audio file for reading/writing. The pathname
	 *	is determined by the <code>file</code> field of the provided <code>AudioFileDescr</code>
	 *
	 *  @param  afd format and resolution of the new audio file.
	 *				the header is immediatly written to the harddisc
	 *
	 *  @throws IOException if the file could not be created or the
	 *						format is unsupported
	 */
	public static AudioFile openAsWrite( AudioFileDescr afd )
	throws IOException
	{
		if( afd.file.exists() ) afd.file.delete();
		AudioFile af	= new AudioFile( afd.file, MODE_READWRITE );
		af.afd			= afd;
		afd.length		= 0;
		af.afh			= af.createHeader();
		af.afh.writeHeader( af.afd );
		af.init();
		af.seekFrame( 0 );
		af.updateStep	= (int) afd.rate * 20;
		af.updateLen	= af.updateStep;
		af.updateTime	= System.currentTimeMillis() + 10000;
		return af;
	}
	
	private AudioFile( File f, int mode )
	throws IOException
	{
		raf			= new RandomAccessFile( f, mode == MODE_READWRITE ? "rw" : "r" );
		fch			= raf.getChannel();
		this.mode   = mode;
	}

	/**
	 *  Returns a description of the audio file's format.
	 *  Fields which are guaranteed to be filled in, are
	 *  the type (use <code>getType</code>), <code>channels</code>,
	 *  <code>bitsPerSample</code>, <code>sampleFormat</code>,
	 *  <code>rate</code> and <code>length</code>.
	 *
	 *  @return an <code>AudioFileDescr</code> describing
	 *			this audio file.
	 *
	 *  @warning	the returned description is read only, do not
	 *				modify it.
	 */
	public AudioFileDescr getDescr()
	{
		return afd;
	}
	
	/**
	 *  Returns the file that was used to open
	 *  the audio file. Note that this simply returns
	 *	getDescr().file, so it's not a good idea to
	 *	modify this field after opening the audio file.
	 *
	 *  @return the <code>File</code> that was used in
	 *			the static constructor methods. Can be used
	 *			to query the pathname or to delete the file after
	 *			it has been closed
	 */
	public File getFile()
	{
		return afd.file;
	}
	
	private void init()
	throws IOException
	{
		channels		= afd.channels;
		bytesPerFrame	= (afd.bitsPerSample >> 3) * channels;
		frameBufCapacity= Math.max( 1, 65536 / bytesPerFrame );
		byteBufCapacity = frameBufCapacity * bytesPerFrame;
		byteBuf			= ByteBuffer.allocateDirect( byteBufCapacity );
		byteBuf.order( afh.getByteOrder() );
		bh				= null;

		switch( afd.sampleFormat ) {
		case AudioFileDescr.FORMAT_INT:
			switch( afd.bitsPerSample ) {
			case 8:		// 8 bit int
				bh  = new ByteBufferHandler();
				break;
			case 16:		// 16 bit int
				bh  = new ShortBufferHandler();
				break;
			case 24:		// 24 bit int
				if( afh.getByteOrder() == ByteOrder.BIG_ENDIAN ) {
					bh  = new ThreeByteBufferHandler();
				} else {
					bh  = new ThreeLittleByteBufferHandler();
				}
				break;
			case 32:		// 32 bit int
				bh  = new IntBufferHandler();
				break;
			}
			break;
		case AudioFileDescr.FORMAT_FLOAT:
			switch( afd.bitsPerSample ) {
			case 32:		// 32 bit float
				bh  = new FloatBufferHandler();
				break;
			case 64:		// 64 bit float
				bh  = new DoubleBufferHandler();
				break;
			}
		}
		if( bh == null) throw new IOException( getResourceString( "errAudioFileEncoding" ));
	}

	private AudioFileHeader createHeader()
	throws IOException
	{
		switch( afd.getType() ) {
		case AudioFileDescr.TYPE_AIFF:
			return new AIFFHeader();
		case AudioFileDescr.TYPE_SND:
			return new SNDHeader();
		case AudioFileDescr.TYPE_IRCAM:
			return new IRCAMHeader();
		case AudioFileDescr.TYPE_WAVE:
			return new WAVEHeader();
		case AudioFileDescr.TYPE_RAW:
			return new RawHeader();
		default:
			throw new IOException( getResourceString( "errAudioFileType" ));
		}
	}

	/*
	 *	Datei Header einlesen, um Filetype zu ermitteln
	 */
	private int retrieveType()
	throws IOException
	{
		long	len		= raf.length();
		long	oldpos	= raf.getFilePointer();
		int		magic;
		int		type	= AudioFileDescr.TYPE_UNKNOWN;

		if( len < 4 ) return AudioFileDescr.TYPE_UNKNOWN;

		raf.seek( 0L );
		magic = raf.readInt();
		switch( magic ) {
		case AIFFHeader.FORM_MAGIC:					// -------- probably AIFF --------
			if( len < 8 ) return AudioFileDescr.TYPE_UNKNOWN;
			raf.readInt();
			magic = raf.readInt();
			switch( magic ) {
			case AIFFHeader.AIFC_MAGIC:
			case AIFFHeader.AIFF_MAGIC:
				type = AudioFileDescr.TYPE_AIFF;
				break;
			}
			break;

		case SNDHeader.SND_MAGIC:					// -------- snd sound --------
			type = AudioFileDescr.TYPE_SND;
			break;

		case IRCAMHeader.IRCAM_VAXBE_MAGIC:			// -------- IRCAM sound --------
		case IRCAMHeader.IRCAM_SUNBE_MAGIC:
		case IRCAMHeader.IRCAM_MIPSBE_MAGIC:
			type = AudioFileDescr.TYPE_IRCAM;
			break;
			
		case WAVEHeader.RIFF_MAGIC:					// -------- probably WAVE --------
			if( len < 8 ) return AudioFileDescr.TYPE_UNKNOWN;
			raf.readInt();
			magic = raf.readInt();
			switch( magic ) {
			case WAVEHeader.WAVE_MAGIC:
				type = AudioFileDescr.TYPE_WAVE;
				break;
			}
			break;

		default:
			break;
		}

		raf.seek( oldpos );
		return type;
	}

	/**
	 *  Moves the file pointer to a specific
	 *  frame.
	 *
	 *  @param  frame   the sample frame which should be
	 *					the new file position. this is really
	 *					the sample index and not the physical file pointer.
	 *  @throws IOException when a seek error occurs or you try to
	 *						seek past the file's end.
	 */
	public void seekFrame( long frame )
	throws IOException
	{
		long physical	= afh.getSampleDataOffset() + frame * bytesPerFrame;

		// XXX fch.force( true );
		
		raf.seek( physical );
		framePosition = frame;
	}
	
	public void flush()
	throws IOException
	{
		updateTime	= System.currentTimeMillis() + 10000;
		afd.length	= framePosition;
		afh.updateHeader( afd );
		updateLen	= framePosition + updateStep;
		fch.force( true );
	}
	
	/**
	 *  Returns the current file pointer in sample frames
	 *
	 *  @return		the sample frame index which is the offset
	 *				for the next read or write operation.
	 *
	 *  @throws IOException		when the position cannot be queried
	 */
	public long getFramePosition()
	throws IOException
	{
		return( framePosition );
	}

	/**
	 *	Reads sample frames from the current position
	 *
	 *  @param  data	buffer to hold the frames read from harddisc.
	 *					the samples will be deinterleaved such that
	 *					data[0][] holds the first channel, data[1][]
	 *					holds the second channel etc.
	 *  @param  offset  offset in the buffer in sample frames, such
	 *					that he first frame of the first channel will
	 *					be placed in data[0][offset] etc.
	 *  @param  length  number of continuous frames to read.
	 *
	 *  @throws IOException if a read error or end-of-file occurs.
	 */
	public void readFrames( float[][] data, int offset, int length )
	throws IOException
	{
		bh.readFrames( data, offset, length );
		framePosition += length;
	}

	/**
	 *	Writes sample frames to the file starting at the current position.
	 *  If you write past the previous end of the file, the <code>length</code>
	 *  field of the internal <code>AudioFileDescr</code> is updated.
	 *  Since you get a reference from <code>getDescr</code> and not
	 *  a copy, using this reference to the description will automatically
	 *  give you the correct file length.
	 *
	 *  @param  data	buffer holding the frames to write to harddisc.
	 *					the samples must be deinterleaved such that
	 *					data[0][] holds the first channel, data[1][]
	 *					holds the second channel etc.
	 *  @param  offset  offset in the buffer in sample frames, such
	 *					that he first frame of the first channel will
	 *					be read from data[0][offset] etc.
	 *  @param  length  number of continuous frames to write.
	 *
	 *  @throws IOException if a write error occurs.
	 */
	public void writeFrames( float[][] data, int offset, int length )
	throws IOException
	{
		bh.writeFrames( data, offset, length );
		framePosition += length;

		if( framePosition > afd.length ) {
			if( (framePosition > updateLen) || (System.currentTimeMillis() > updateTime) ) {
				flush();
			} else {
				afd.length = framePosition;
			}
		}
	}
	
	public long getFrameNum()
	throws IOException
	{
		return afd.length;
	}

	public void truncate()
	throws IOException
	{
		fch.truncate( fch.position() );
		if( framePosition != afd.length ) {
			afd.length	= framePosition;
			updateTime	= System.currentTimeMillis() + 10000;
			afh.updateHeader( afd );
			updateLen	= framePosition + updateStep;
		}
	}

	public void copyFrames( InterleavedStreamFile target, long length )
	throws IOException
	{
		int chunkLength;

//		if( (target instanceof AudioFile) && ((AudioFile) target).bh.getClass().equals( this.bh.getClass() ) &&
//			(((AudioFile) target).channels == this.channels) ) {
//		
//			while( length > 0 ) {
//				chunkLength = Math.min( frameBufCapacity, length );
//				byteBuf.clear();
//					...
//				byteBuf.flip();
//				fch.write( byteBuf );
//				length -= chunkLength;
//			}
//
//		} else {
			int			tempBufSize	= (int) Math.min( length, 8192 );
			float[][]	tempBuf		= new float[ channels ][ tempBufSize ];
			
			while( length > 0 ) {
				chunkLength	= (int) Math.min( length, tempBufSize );
				this.readFrames( tempBuf, 0, chunkLength );
				target.writeFrames( tempBuf, 0, chunkLength );
				length -= chunkLength;
			}
//		}
	}

	/**
	 *  Flushes and closes the file
	 *
	 *  @throws IOException if an error occurs during buffer flush
	 *						or closing the file.
	 */
	public void close()
	throws IOException
	{
		if( mode == MODE_READWRITE ) {
			fch.force( true );
			afh.updateHeader( afd );
		}
		raf.close();

	}

	/**
	 *  Flushes and closes the file. As opposed
	 *	to <code>close()</code>, this does not
	 *	throw any exceptions but simply ignores any errors.
	 *
	 *	@see	#close()
	 */
	public void cleanUp()
	{
		try {
			close();
		}
		catch( IOException e ) {}		// ignore
	}

	private static final String getResourceString( String key )
	{
		return IOUtil.getResourceString( key );
	}
	
// -------- BufferHandler Klassen --------

	private abstract class BufferHandler
	{
		protected abstract void writeFrames( float[][] frames, int off, int len ) throws IOException;
		protected abstract void readFrames( float[][] frames, int off, int len ) throws IOException;
	}
	
	private class ByteBufferHandler
	extends BufferHandler
	{
		protected void writeFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.clear();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						byteBuf.put( (byte) (frames[ch][offset] * 0x7F + 0.5f) );
					}
				}
				byteBuf.flip();
				fch.write( byteBuf );
				length -= chunkLength;
			}
		}

		protected void readFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.limit( chunkLength * bytesPerFrame );
				byteBuf.rewind();
				fch.read( byteBuf );
				byteBuf.flip();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						frames[ch][offset] = (float) byteBuf.get() / 0x7F;
					}
				}
				length -= chunkLength;
			}
		}
	}

	private class ShortBufferHandler
	extends BufferHandler
	{
		protected void writeFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;

			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.clear();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						byteBuf.putShort( (short) (frames[ch][offset] * 0x7FFF + 0.5f) );
					}
				}
				byteBuf.flip();
				fch.write( byteBuf );
				length -= chunkLength;
			}
		}

		protected void readFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.limit( chunkLength * bytesPerFrame );
				byteBuf.rewind();
				fch.read( byteBuf );
				byteBuf.flip();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						frames[ch][offset] = (float) byteBuf.getShort() / 0x7FFF;
					}
				}
				length -= chunkLength;
			}
		}
	}

	/*
	 *  24bit big endian
	 */
	private class ThreeByteBufferHandler
	extends BufferHandler
	{
		protected void writeFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, j, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.clear();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						j = (int) (frames[ ch ][ offset ] * 0x7FFFFF + 0.5f);
						byteBuf.put( (byte) (j >> 16) );
						byteBuf.put( (byte) (j >> 8) );
						byteBuf.put( (byte)  j );
					}
				}
				byteBuf.flip();
				fch.write( byteBuf );
				length -= chunkLength;
			}
		}

		protected void readFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.limit( chunkLength * bytesPerFrame );
				byteBuf.rewind();
				fch.read( byteBuf );
				byteBuf.flip();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						frames[ch][offset] = (float) (((int) byteBuf.get() << 16 ) |
												     (((int) byteBuf.get() & 0xFF) << 8) |
													  ((int) byteBuf.get() & 0xFF)) / 0x7FFFFF;
					}
				}
				length -= chunkLength;
			}
		}
	}

	/*
	 *  24bit little endian
	 */
	private class ThreeLittleByteBufferHandler
	extends BufferHandler
	{
		protected void writeFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, j, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.clear();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						j = (int) (frames[ ch ][ offset ] * 0x7FFFFF + 0.5f);
						byteBuf.put( (byte)  j );
						byteBuf.put( (byte) (j >> 8) );
						byteBuf.put( (byte) (j >> 16) );
					}
				}
				byteBuf.flip();
				fch.write( byteBuf );
				length -= chunkLength;
			}
		}

		protected void readFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.limit( chunkLength * bytesPerFrame );
				byteBuf.rewind();
				fch.read( byteBuf );
				byteBuf.flip();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						frames[ch][offset] = (float) (((int) byteBuf.get() & 0xFF) |
												     (((int) byteBuf.get() & 0xFF) << 8) |
													  ((int) byteBuf.get() << 16 )) / 0x7FFFFF;
					}
				}
				length -= chunkLength;
			}
		}
	}

	private class IntBufferHandler
	extends BufferHandler
	{
		protected void writeFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.clear();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						byteBuf.putInt( (int) ((double) frames[ch][offset] * 0x7FFFFFFF + 0.5) );
					}
				}
				byteBuf.flip();
				fch.write( byteBuf );
				length -= chunkLength;
			}
		}

		protected void readFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.limit( chunkLength * bytesPerFrame );
				byteBuf.rewind();
				fch.read( byteBuf );
				byteBuf.flip();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						frames[ch][offset] = (float) ((double) byteBuf.getInt() / 0x7FFFFFFF);
					}
				}
				length -= chunkLength;
			}
		}
	}

	private class FloatBufferHandler
	extends BufferHandler
	{
		protected void writeFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.clear();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						byteBuf.putFloat( frames[ch][offset] );
					}
				}
				byteBuf.flip();
				fch.write( byteBuf );
				length -= chunkLength;
			}
		}

		protected void readFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.limit( chunkLength * bytesPerFrame );
				byteBuf.rewind();
				fch.read( byteBuf );
				byteBuf.flip();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						frames[ch][offset] = byteBuf.getFloat();
					}
				}
				length -= chunkLength;
			}
		}
	}

	private class DoubleBufferHandler
	extends BufferHandler
	{
		protected void writeFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;
		
			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.clear();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						byteBuf.putDouble( (double) frames[ch][offset] );
					}
				}
				byteBuf.flip();
				fch.write( byteBuf );
				length -= chunkLength;
			}
		}

		protected void readFrames( float[][] frames, int offset, int length )
		throws IOException
		{
			int i, ch, chunkLength;

			while( length > 0 ) {
				chunkLength = Math.min( frameBufCapacity, length );
				byteBuf.limit( chunkLength * bytesPerFrame );
				byteBuf.rewind();
				fch.read( byteBuf );
				byteBuf.flip();
				for( i = 0; i < chunkLength; i++, offset++ ) {
					for( ch = 0; ch < channels; ch++ ) {
						frames[ch][offset] = (float) byteBuf.getDouble();
					}
				}
				length -= chunkLength;
			}
		}
	}

// -------- AudioFileHeader Klassen --------

	private abstract class AudioFileHeader
	{
		protected abstract void readHeader( AudioFileDescr afd ) throws IOException;
		protected abstract void writeHeader( AudioFileDescr afd ) throws IOException;
		protected abstract void updateHeader( AudioFileDescr afd ) throws IOException;
		protected abstract long getSampleDataOffset();
		protected abstract ByteOrder getByteOrder();

		protected final int readLittleUShort()
		throws IOException
		{
			int i = raf.readUnsignedShort();
			return( (i >> 8) | ((i & 0xFF) << 8) );
		}

		protected final int readLittleInt()
		throws IOException
		{
			int i = raf.readInt();
			return( ((i >> 24) & 0xFF) | ((i >> 8) & 0xFF00) | ((i << 8) & 0xFF0000) | (i << 24) );
		}

		protected final void writeLittleShort( int i )
		throws IOException
		{
			raf.writeShort( (i >> 8) | ((i & 0xFF) << 8) );
		}

		protected final void writeLittleInt( int i )
		throws IOException
		{
			raf.writeInt( ((i >> 24) & 0xFF) | ((i >> 8) & 0xFF00) | ((i << 8) & 0xFF0000) | (i << 24) );
		}
	}
	
	private class AIFFHeader
	extends AudioFileHeader
	{
		private static final int FORM_MAGIC		= 0x464F524D;	// 'FORM'
		private static final int AIFF_MAGIC		= 0x41494646;	// 'AIFF'   (offset 8)
		private static final int AIFC_MAGIC		= 0x41494643;	// 'AIFC'   (offset 8)

		// chunk identifiers
		private static final int COMM_MAGIC		= 0x434F4D4D;	// 'COMM'
		private static final int INST_MAGIC		= 0x494E5354;	// 'INST'
		private static final int MARK_MAGIC		= 0x4D41524B;	// 'MARK'
		private static final int SSND_MAGIC		= 0x53534E44;	// 'SSND
		private static final int FVER_MAGIC		= 0x46564552;	// 'FVER
		
		// aifc compression identifiers
		private static final int NONE_MAGIC		= 0x4E4F4E45;	// 'NONE' (AIFC-compression)
		private static final int fl32_MAGIC		= 0x666C3332;	// 'fl32' (AIFC-compression)
		private static final int FL32_MAGIC		= 0x464C3332;	// SoundHack variant
		private static final int fl64_MAGIC		= 0x666C3634;
		private static final int FL64_MAGIC		= 0x464C3634;	// SoundHack variant
		private static final int in16_MAGIC		= 0x696E3136;	// we "love" SoundHack for its special interpretations
		private static final int in24_MAGIC		= 0x696E3234;
		private static final int in32_MAGIC		= 0x696E3332;

		private boolean isAIFC					= true;			// default for writing files
		private static final int AIFCVersion1	= 0xA2805140;	// FVER chunk
//		private static final String NONE_HUMAN	= "uncompressed";
		private static final String fl32_HUMAN	= "32-bit float";
		private static final String fl64_HUMAN	= "64-bit float";

		private long sampleDataOffset;
		
		private long formLengthOffset = 4L;
		private long commSmpNumOffset;
		private long ssndLengthOffset;
		private long lastUpdateLength = 0L;

		protected void readHeader( AudioFileDescr afd )
		throws IOException
		{
			long	l1, l2, l3;
			int		i, i1, i2, i3, len, chunkLen, essentials, magic;
			int		loopStart = 0;
			int		loopEnd   = 0;
			byte[]  strBuf;
			long	markersOffset   = 0L;
			boolean loop			= false;
			java.util.List  markers;

			raf.readInt();		// FORM
			len		= (raf.readInt() + 1) & 0xFFFFFFFE;		// Laenge ohne FORM-Header (Dateilaenge minus 8)
			isAIFC  = raf.readInt() == AIFC_MAGIC;
			len	   -= 4;
			
			for( essentials = 2; (len > 0) && (essentials > 0); ) {
			
				magic		= raf.readInt();
				chunkLen	= (raf.readInt() + 1) & 0xFFFFFFFE;
				len		   -= chunkLen + 8;

				switch( magic ) {
				case COMM_MAGIC:
					essentials--;
					afd.channels		= raf.readShort();	// # of channels
					commSmpNumOffset	= raf.getFilePointer();
					afd.length			= raf.readInt();	// # of samples
					afd.bitsPerSample	= raf.readShort();	// # of bits per sample
					afd.sampleFormat	= AudioFileDescr.FORMAT_INT;   // default, AIFC will be dealt with later

					// suckers never die. perhaps the most stupid data format to store a float:
					l1 					= raf.readLong();
					l2	 				= (long) raf.readUnsignedShort();
					l3	 				= l1 & 0x0000FFFFFFFFFFFFL;
					i1					= ((int) (l1 >> 48) & 0x7FFF) - 0x3FFE;
					afd.rate			= (float) ((((double) l3 * Math.pow( 2.0, i1 - 48 )) +
												    ((double) l2 * Math.pow( 2.0, i1 - 64 ))) * (l1 < 0 ? -1 : 1));

					chunkLen -= 18;
					if( isAIFC ) {
						switch( raf.readInt() ) {
						case NONE_MAGIC:
							break;
						case in16_MAGIC:
							afd.bitsPerSample	= 16;
							break;
						case in24_MAGIC:
							afd.bitsPerSample	= 24;
							break;
						case in32_MAGIC:
							afd.bitsPerSample	= 32;
							break;
						case fl32_MAGIC:
						case FL32_MAGIC:
							afd.bitsPerSample	= 32;
							afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
							break;
						case fl64_MAGIC:
						case FL64_MAGIC:
							afd.bitsPerSample	= 64;
							afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
							break;
						default:
							throw new IOException( getResourceString( "errAudioFileEncoding" ));
						}
						chunkLen -= 4;
					}
					break;

				case INST_MAGIC:
					raf.readInt();		// char: MIDI Note, Detune, LowNote, HighNote
//					i1					= readInt();	// char: MIDI Note, Detune, LowNote, HighNote
//					b1					= (byte) ((i1 & 0x00FF0000) >> 16);	// Detune in -50...50 Cent
//											// MIDI-Note to Hz (69 = A4 = 440 Hz)
//					stream.base			= (float) (440.0 * Math.pow( 2, ((float) (((i1 & 0x7F000000) >> 24) -
//											69) + (float) b1 / 100.0f) / 12.0f ));
					i1					= raf.readInt();		// char velocityLo, char velocityHi, short gain [dB]
					afd.setProperty( AudioFileDescr.KEY_GAIN,
									 new Float( Math.exp( (double) (i1 & 0xFFFF) / 20 * Math.log( 10 ))));
					i1	 				= raf.readShort();		// Sustain-Loop: 0 = no loop, 1 = fwd, 2 = back
					loop				= i1 != 0;
					i1					= raf.readInt();		// Short Lp-Start-MarkerID, Short End-ID
					loopStart			= (i1 >> 16) & 0xFFFF;
					loopEnd				= i1 & 0xFFFF;
					chunkLen -= 14;
					break;

				case MARK_MAGIC:
					markersOffset = raf.getFilePointer();		// read them out later
					break;

				case SSND_MAGIC:
					essentials--;
					i1 = raf.readInt();			// sample data offset
					raf.readInt();
					sampleDataOffset = raf.getFilePointer() + i1;
					chunkLen -= 8;
					break;
				
				default:
					break;
				} // switch( magic )
				raf.seek( raf.getFilePointer() + chunkLen );	// skip to next chunk
			} // for( essentials = 2; (len > 0) && (essentials > 0); )
			if( essentials > 0 ) throw new IOException( getResourceString( "errAudioFileIncomplete" ));
			
			if( loop ) {		// ---- Sustain-Loop ----
				essentials = 2;
			}
			if( markersOffset > 0L ) {
				markers = new ArrayList();
				strBuf  = new byte[ 64 ];			// to store the names
				raf.seek( markersOffset );
				i1 = raf.readUnsignedShort();		// number of markers
				for( i = i1; i > 0; i-- ) {
					i3 = raf.readUnsignedShort();	// marker ID
					i2 = raf.readInt();				// marker position (sample offset)
					i1 = raf.readUnsignedByte();	// markerName String-len
					if( loop && i3 == loopStart ) {
						loopStart	= i2;
						essentials--;
					} else if( loop && i3 == loopEnd ) {
						loopEnd		= i2;
						essentials--;
					} else {
						i3	 = Math.min( i1, strBuf.length );
						raf.readFully( strBuf, 0, i3 );
						i1	-= i3;
						if( (i3 > 0) && (strBuf[ i3 - 1 ] == 0x20) ) {
							i3--;	// ignore padding space created by Peak
						}
						markers.add( new Marker( i2, new String( strBuf, 0, i3 )));
					}
					raf.seek( (raf.getFilePointer() + (i1 + 1)) & ~1 );
				}
				afd.setProperty( AudioFileDescr.KEY_MARKERS, markers );
			}
			if( loop && essentials == 0 ) {
				afd.setProperty( AudioFileDescr.KEY_LOOP, new Region( new Span( loopStart, loopEnd ), NAME_LOOP ));
			}
		}
		
		protected void writeHeader( AudioFileDescr afd )
		throws IOException
		{
			int		len;
			int		i1, i2;
			String	str;
			byte	strBuf[];
			Float   gainProperty;
			Region  region;
			java.util.List  markers;
			Marker	marker;
			float	f1;
			double	d1;
			byte	b1;
			long	pos, pos2;
			boolean loop;

			isAIFC = afd.sampleFormat == AudioFileDescr.FORMAT_FLOAT;	// floating point requires AIFC compression extension
			raf.writeInt( FORM_MAGIC );
			raf.writeInt( 0 );				// Laenge ohne FORM-Header (Dateilaenge minus 8); unknown now
			raf.writeInt( isAIFC ? AIFC_MAGIC : AIFF_MAGIC );

			// FVER Chunk
			if( isAIFC ) {
				raf.writeInt( FVER_MAGIC );
				raf.writeInt( 4 );
				raf.writeInt( AIFCVersion1 );
			}
			
			// COMM Chunk
			raf.writeInt( COMM_MAGIC );
			pos = raf.getFilePointer();
			raf.writeInt( 0 );				// not known yet
			raf.writeShort( afd.channels );
			commSmpNumOffset = raf.getFilePointer();
			raf.writeInt( 0 );				// updated later
			raf.writeShort( isAIFC ? 16 : afd.bitsPerSample );	// a quite strange convention ...

			// suckers never die.
			i2		= (afd.rate < 0.0f) ? 128 : 0;
			f1		= Math.abs( afd.rate  );
			i1		= (int) (Math.log( f1 ) / Math.log( 2 ) + 16383.0) & 0xFFFF;
			d1		= f1 * (1 << (0x401E-i1));	// Math.pow( 2.0, 0x401E - i1 );
			raf.writeShort( (((i2 | (i1 >> 8)) & 0xFF) << 8) | (i1 & 0xFF) );
			raf.writeInt( (int) ((long) d1 & 0xFFFFFFFF) );
			raf.writeInt( (int) ((long) ((d1 % 1.0) * 4294967296.0) & 0xFFFFFFFF) );

			if( isAIFC ) {
				if( afd.bitsPerSample == 32 ) {
					str	= fl32_HUMAN;
					i1	= fl32_MAGIC;
				} else {
					str = fl64_HUMAN;
					i1	= fl64_MAGIC;
				}
				raf.writeInt( i1 );
				raf.writeByte( str.length() );
				raf.writeBytes( str );
				if( (str.length() & 1) == 0 ) {
					raf.writeByte( 0x00 );
//				} else {
//					raf.writeShort( 0x0000 );
				}
			}
			// ...chunk length update...
			pos2 = raf.getFilePointer();
			raf.seek( pos );
			raf.writeInt( (int) (pos2 - pos - 4) );
			raf.seek( pos2 );

			// INST Chunk
			raf.writeInt( INST_MAGIC );
			raf.writeInt( 20 );

//			f1	= (float) (12 * Math.log( (double) stream.base / 440.0 ) / Constants.ln2);
//			i1	= (int) (f1 + 0.5f);
//			b1	= (byte) ((f1 - (float) i1) * 100.0f);
//			writeInt( (((i1 + 69) & 0xFF) << 24) | ((int) b1 << 16) | 0x007F );	// char: MIDI Note, Detune, LowNote, HighNote
			raf.writeInt( (69 << 24) | (0 << 16) | 0x007F );	// char: MIDI Note, Detune, LowNote, HighNote
			
			// XXX the gain information could be updated in updateHeader()
			gainProperty = (Float) afd.getProperty( AudioFileDescr.KEY_GAIN );
			if( gainProperty != null ) {
				i1	= (int) (20 * Math.log( gainProperty.floatValue() ) / Math.log( 10 ) + 0.5);
			} else {
				i1  = 0;
			}
			raf.writeInt( (0x007F << 16) | (i1 & 0xFFFF) );		// char velLo, char velHi, short gain [dB]

			region  = (Region) afd.getProperty( AudioFileDescr.KEY_LOOP );
			loop	= region != null;
			raf.writeShort( loop ? 1 : 0 );					// No loop vs. loop forward
			raf.writeInt( loop ? 0x00010002 : 0 );			// Sustain-Loop Markers
			raf.writeShort( 0 );							// No release loop
			raf.writeInt( 0 );

			markers  = (java.util.List) afd.getProperty( AudioFileDescr.KEY_MARKERS );
			if( markers == null ) markers = new ArrayList();
			// MARK Chunk
			if( loop || !markers.isEmpty() ) {
				raf.writeInt( MARK_MAGIC );
				pos = raf.getFilePointer();
				raf.writeInt( 0 );				// not known yet
				i1	= markers.size() + (loop ? 2 : 0);
				raf.writeShort( i1 );
				i2	= 1;					// ascending marker ID
				if( loop ) {
					raf.writeShort( i2++ );						// loop start ID
					raf.writeInt( (int) region.span.getStart() );	// sample offset
					raf.writeLong( 0x06626567206C7000L );		// Pascal style String: "beg lp"
					raf.writeShort( i2++ );
					raf.writeInt( (int) region.span.getStop() );
					raf.writeLong( 0x06656E64206C7000L );		// Pascal style String: "end lp"
				}
				for( i1 = 0; i1 < markers.size(); i1++ ) {
					raf.writeShort( i2++ );
					marker = (Marker) markers.get( i1 );
					raf.writeInt( (int) marker.pos );
//					raf.writeByte( (marker.name.length() + 1) & 0xFE );
					raf.writeByte( marker.name.length()  & 0xFF );
					raf.writeBytes( marker.name );
					if( (marker.name.length() & 1) == 0 ) {
						raf.writeByte( 0x00 );
//					} else {
//						raf.writeShort( 0x2000 );	// padding space + zero pad to even address
					}
				}
				// ...chunk length update...
				pos2 = raf.getFilePointer();
				raf.seek( pos );
				raf.writeInt( (int) (pos2 - pos - 4) );
				raf.seek( pos2 );
			}

			// SSND Chunk (Header)
			raf.writeInt( SSND_MAGIC );
			ssndLengthOffset = raf.getFilePointer();
			raf.writeInt( 8 );		// + stream.samples * frameLength );
			raf.writeInt( 0 );		// sample
			raf.writeInt( 0 );		// block size (?!)
			sampleDataOffset = raf.getFilePointer();
			
			updateHeader( afd );
		}
		
		protected void updateHeader( AudioFileDescr afd )
		throws IOException
		{
			long oldPos	= raf.getFilePointer();
			long len	= raf.length();
			if( len == lastUpdateLength ) return;
			
			if( len >= formLengthOffset + 4 ) {
				raf.seek( formLengthOffset );
				raf.writeInt( (int) (len - 8) );								// FORM Chunk len
			}
			if( len >= commSmpNumOffset + 4 ) {
				raf.seek( commSmpNumOffset );
				raf.writeInt( (int) afd.length );								// COMM: Sample-Num
			}
			if( len >= ssndLengthOffset + 4 ) {
				raf.seek( ssndLengthOffset );
				raf.writeInt( (int) (len - (ssndLengthOffset + 4)) );			// SSND Chunk len
			}
			raf.seek( oldPos );
			lastUpdateLength = len;
		}
		
		protected long getSampleDataOffset()
		{
			return sampleDataOffset;
		}
		
		protected ByteOrder getByteOrder()
		{
			return ByteOrder.BIG_ENDIAN;
		}
	} // class AIFFHeader

	private class WAVEHeader
	extends AudioFileHeader
	{
		private static final int RIFF_MAGIC		= 0x52494646;	// 'RIFF'
		private static final int WAVE_MAGIC		= 0x57415645;	// 'WAVE' (offset 8)

		// chunk identifiers
		private static final int FMT_MAGIC		= 0x666D7420;	// 'fmt '
		private static final int FACT_MAGIC		= 0x66616374;	// 'fact'
		private static final int DATA_MAGIC		= 0x64617461;	// 'data'
		private static final int CUE_MAGIC		= 0x63756520;	// 'cue '
		private static final int LIST_MAGIC		= 0x4C495354;	// 'LIST'
		private static final int INST_MAGIC		= 0x696E7374;	// 'inst'
		
		// fmt format-code
		private static final int FORMAT_PCM		= 0x0001;
		private static final int FORMAT_FLOAT	= 0x0003;
		private static final int FORMAT_EXT		= 0xFFFE;
		
		private long sampleDataOffset;
		
		private long riffLengthOffset = 4L;
		private long dataLengthOffset;
		private long factSmpNumOffset;
		private long lastUpdateLength = 0L;
		private boolean isFloat	= false;

		protected void readHeader( AudioFileDescr afd )
		throws IOException
		{
			int i, i1, i2, i3, len, chunkLen, essentials, magic, dataLen = 0, bytesPerFrame = 0;

			raf.readInt();		// RIFF
			len		= (readLittleInt() + 1) & 0xFFFFFFFE;		// Laenge ohne RIFF-Header (Dateilaenge minus 8)
			raf.readInt();		// WAVE
			len	   -= 4;
			
			for( essentials = 2; (len > 0) && (essentials > 0); ) {
			
				magic		= raf.readInt();
				chunkLen	= (readLittleInt() + 1) & 0xFFFFFFFE;
				len		   -= chunkLen + 8;

				switch( magic ) {
				case FMT_MAGIC:
					essentials--;
					i					= readLittleUShort();		// format
					afd.channels		= readLittleUShort();		// # of channels
					i1					= readLittleInt();			// sample rate (integer)
					afd.rate			= (float) i1;
					i2					= readLittleInt();			// bytes per frame and second (=#chan * #bits/8 * rate)
					bytesPerFrame		= readLittleUShort();		// bytes per frame (=#chan * #bits/8)
					afd.bitsPerSample	= readLittleUShort();		// # of bits per sample
					if( ((afd.bitsPerSample & 0x07) != 0) ||
						((afd.bitsPerSample >> 3) * afd.channels != bytesPerFrame) ||
						((afd.bitsPerSample >> 3) * afd.channels * i1 != i2) ) {
											
						throw new IOException( getResourceString( "errAudioFileEncoding" ));
					}

					chunkLen -= 16;

					switch( i ) {
					case FORMAT_PCM:
						afd.sampleFormat = AudioFileDescr.FORMAT_INT;
						break;
					case FORMAT_FLOAT:
						afd.sampleFormat = AudioFileDescr.FORMAT_FLOAT;
						break;
					case FORMAT_EXT:
						if( chunkLen < 24 ) throw new IOException( getResourceString( "errAudioFileIncomplete" ));
						i1 = readLittleUShort();	// extension size
						if( i1 < 22 ) throw new IOException( getResourceString( "errAudioFileIncomplete" ));
						i2 = readLittleUShort();	// # valid bits per sample
						raf.readInt();				// channel mask, ignore
						i3 = readLittleUShort();	// GUID first two bytes
						if( (i2 != afd.bitsPerSample) ||
							(i3 != FORMAT_PCM) ||
							(i3 != FORMAT_FLOAT) ) throw new IOException( getResourceString( "errAudioFileEncoding" ));
						afd.sampleFormat = i3 == FORMAT_PCM ? AudioFileDescr.FORMAT_INT : AudioFileDescr.FORMAT_FLOAT;
						chunkLen -= 10;
						break;
					default:
						throw new IOException( getResourceString( "errAudioFileEncoding" ));
					}
					break;

				case DATA_MAGIC:
					essentials--;
					sampleDataOffset	= raf.getFilePointer();
					dataLen				= chunkLen;
					break;
				
				default:
					break;
				} // switch( magic )
				raf.seek( raf.getFilePointer() + chunkLen );	// skip to next chunk
			} // for( essentials = 2; (len > 0) && (essentials > 0); )
			if( essentials > 0 ) throw new IOException( getResourceString( "errAudioFileIncomplete" ));
			
			afd.length	= dataLen / bytesPerFrame;
		}
		
		protected void writeHeader( AudioFileDescr afd )
		throws IOException
		{
			int		len;
			int		i1, i2;
			String	str;
			byte	strBuf[];
			Float   gainProperty;
			Region  region;
			java.util.List  markers;
			Marker	marker;
			float	f1;
			double	d1;
			byte	b1;
			long	pos, pos2;
			boolean loop;

			isFloat = afd.sampleFormat == AudioFileDescr.FORMAT_FLOAT;	// floating point requires FACT extension
			raf.writeInt( RIFF_MAGIC );
			raf.writeInt( 0 );				// Laenge ohne RIFF-Header (Dateilaenge minus 8); unknown now
			raf.writeInt( WAVE_MAGIC );

			// fmt Chunk
			raf.writeInt( FMT_MAGIC );
			writeLittleInt( isFloat ? 18 : 16 );	// FORMAT_FLOAT has extension of size 0
			writeLittleShort( isFloat ? FORMAT_FLOAT : FORMAT_PCM );
			writeLittleShort( afd.channels );
			i1 = (int) (afd.rate + 0.5f);
			writeLittleInt( i1 );
			i2 = (afd.bitsPerSample >> 3) * afd.channels;
			writeLittleInt( i1 * i2 );
			writeLittleShort( i2 );
			writeLittleShort( afd.bitsPerSample );
			
			if( isFloat ) raf.writeShort( 0 );

			// fact Chunk
			if( isFloat ) {
				raf.writeInt( FACT_MAGIC );
				writeLittleInt( 4 );
				factSmpNumOffset = raf.getFilePointer();
				raf.writeInt( 0 );
			}

			// data Chunk (Header)
			raf.writeInt( DATA_MAGIC );
			dataLengthOffset = raf.getFilePointer();
			raf.writeInt( 0 );
			sampleDataOffset = raf.getFilePointer();
			
			updateHeader( afd );
		}
		
		protected void updateHeader( AudioFileDescr afd )
		throws IOException
		{
			long oldPos	= raf.getFilePointer();
			long len	= raf.length();
			if( len == lastUpdateLength ) return;
			
			if( len >= riffLengthOffset + 4 ) {
				raf.seek( riffLengthOffset );
				writeLittleInt( (int) (len - 8) );								// RIFF Chunk len
			}
			if( isFloat && (len >= factSmpNumOffset + 4) ) {
				raf.seek( factSmpNumOffset );
				writeLittleInt( (int) (afd.length * afd.channels) );			// fact: Sample-Num XXX check multich.!
			}
			if( len >= dataLengthOffset + 4 ) {
				raf.seek( dataLengthOffset );
				writeLittleInt( (int) (len - (dataLengthOffset + 4)) );			// data Chunk len
			}
			raf.seek( oldPos );
			lastUpdateLength = len;
		}
		
		protected long getSampleDataOffset()
		{
			return sampleDataOffset;
		}
		
		protected ByteOrder getByteOrder()
		{
			return ByteOrder.LITTLE_ENDIAN;
		}
	} // class WAVEHeader

	private class SNDHeader
	extends AudioFileHeader
	{
		private static final int SND_MAGIC		= 0x2E736E64;	// '.snd'

		private long sampleDataOffset;
		private long headDataLenOffset= 8L;
		private long lastUpdateLength = 0L;
		
		protected void readHeader( AudioFileDescr afd )
		throws IOException
		{
			int i1, i2;
		
			raf.readInt();  // SND magic
			sampleDataOffset= raf.readInt();
			i2				= raf.readInt();
			i1				= raf.readInt();
			afd.rate		= (float) raf.readInt();
			afd.channels	= raf.readInt();

			switch( i1 ) {
			case 2:	// 8 bit linear
				afd.bitsPerSample	= 8;
				afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
				break;
			case 3:	// 16 bit linear
				afd.bitsPerSample	= 16;
				afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
				break;
			case 4:	// 24 bit linear
				afd.bitsPerSample	= 24;
				afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
				break;
			case 5:	// 32 bit linear
				afd.bitsPerSample	= 32;
				afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
				break;
			case 6:	// 32 bit float
				afd.bitsPerSample	= 32;
				afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
				break;
			case 7:	// 64 bit float
				afd.bitsPerSample	= 64;
				afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
				break;
			default:
				throw new IOException( getResourceString( "errAudioFileEncoding" ));
			}

			afd.length	= i2 / (((afd.bitsPerSample + 7) >> 3) * afd.channels);
		}
		
		protected void writeHeader( AudioFileDescr afd )
		throws IOException
		{
			sampleDataOffset = 28L;
			raf.writeInt( SND_MAGIC );
			raf.writeInt( (int) sampleDataOffset );
//			raf.writeInt( stream.samples * frameLength );	// len
			raf.writeInt( 0 );

			if( afd.sampleFormat == AudioFileDescr.FORMAT_INT ) {
				raf.writeInt( (afd.bitsPerSample >> 3) + 1 );
			} else {
				raf.writeInt( (afd.bitsPerSample >> 5) + 5 );
			}
			raf.writeInt( (int) (afd.rate + 0.5f) );
			raf.writeInt( afd.channels );
			raf.writeInt( 0 );  // minimum 4 byte character data

//			updateHeader( afd );
		}
		
		protected void updateHeader( AudioFileDescr afd )
		throws IOException
		{
			long oldPos;
			long len	= raf.length();
			if( len == lastUpdateLength ) return;
			
			if( len >= headDataLenOffset + 4 ) {
				oldPos = raf.getFilePointer();
				raf.seek( headDataLenOffset );
				raf.writeInt( (int) (len - sampleDataOffset) );		// data size
				raf.seek( oldPos );
				lastUpdateLength = len;
			}
		}

		protected long getSampleDataOffset()
		{
			return sampleDataOffset;
		}

		protected ByteOrder getByteOrder()
		{
			return ByteOrder.BIG_ENDIAN;
		}
	} // class SNDHeader

	private class IRCAMHeader
	extends AudioFileHeader
	{
		// http://www.tsp.ece.mcgill.ca/MMSP/Documents/AudioFormats/IRCAM/IRCAM.html
		// for details about the different magic cookies
		private static final int IRCAM_VAXBE_MAGIC		= 0x0001A364;
		private static final int IRCAM_SUNBE_MAGIC		= 0x64A30200;
		private static final int IRCAM_MIPSBE_MAGIC		= 0x0003A364;

		private static final short BICSF_END			= 0;
		private static final short BICSF_MAXAMP			= 1;
		private static final short BICSF_COMMENT		= 2;
		private static final short BICSF_LINKCODE		= 3;
		private static final short BICSF_VIRTUALCODE	= 4;
		private static final short BICSF_CUECODE		= 8;
		private static final short BICSF_PARENTCODE		= 11;

		private long sampleDataOffset;

		protected void readHeader( AudioFileDescr afd )
		throws IOException
		{
			int		i1, i2, i3;
			long	l1;
			byte[]  strBuf  = null;
			java.util.List  regions = new ArrayList();
		
			raf.readInt();		// IRCAM magic
			afd.rate		= raf.readFloat();
			afd.channels	= raf.readInt();
			i1				= raf.readInt();

			switch( i1 ) {
			case 1:	// 8 bit linear
				afd.bitsPerSample	= 8;
				afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
				break;
			case 2:	// 16 bit linear
				afd.bitsPerSample	= 16;
				afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
				break;
			case 3:	// 24 bit linear; existiert dieser wert offiziell?
				afd.bitsPerSample	= 24;
				afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
				break;
			case 0x40004:	// 32 bit linear
				afd.bitsPerSample	= 32;
				afd.sampleFormat	= AudioFileDescr.FORMAT_INT;
				break;
			case 4:	// 32 bit float
				afd.bitsPerSample	= 32;
				afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
				break;
			case 8:	// 64 bit float
				afd.bitsPerSample	= 64;
				afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
				break;
			default:
				throw new IOException( getResourceString( "errAudioFileEncoding" ));
			}

			do {
				i1   = raf.readInt();
				i2	 = i1 & 0xFFFF;		// last short = block size
				i1 >>= 16;				// first short = code
// System.err.println( "next tag: code "+i1+"; len "+i2 );
				switch( i1 ) {
				case BICSF_CUECODE:
					if( strBuf == null ) {
						strBuf = new byte[ 64 ];			// to store the names
					}
					raf.readFully( strBuf );				// region name
					for( i3 = 0; i3 < 64; i3++ ) {
						if( strBuf[ i3 ] == 0 ) break;
					}
					i1	= raf.readInt();					// begin smp
					i2	= raf.readInt();					// end smp
					regions.add( new Region( new Span( i1, i2 ), new String( strBuf, 0, i3 )));
					break;
					
				case BICSF_LINKCODE:
				case BICSF_VIRTUALCODE:
					throw new IOException( getResourceString( "errAudioFileEncoding" ));
				
				default:
					raf.seek( raf.getFilePointer() + i2 );		// skip unknown code
					break;
				}
			} while( i1 != BICSF_END );
			
			if( !regions.isEmpty() ) {
				afd.setProperty( AudioFileDescr.KEY_REGIONS, regions );
			}
			
			l1				= raf.getFilePointer();
			sampleDataOffset= (l1 + 1023L) & ~1023L;			// aufgerundet auf ganze kilobyte
			l1				= raf.length() - sampleDataOffset;  // dataLen in bytes
			afd.length		= l1 / (((afd.bitsPerSample + 7) >> 3) * afd.channels);
		}
		
		protected void writeHeader( AudioFileDescr afd )
		throws IOException
		{
			int		i1, i2;
			java.util.List  regions;
			Region  region;
			byte[]  strBuf;
			long	pos;
		
			raf.writeInt( IRCAM_VAXBE_MAGIC );
			raf.writeFloat( afd.rate );
			raf.writeInt( afd.channels );

			if( (afd.sampleFormat == AudioFileDescr.FORMAT_INT) && (afd.bitsPerSample == 32) ) {
				i1 = 0x40004;
			} else {
				i1	= afd.bitsPerSample >> 3;		// 1 = 8bit int, 2 = 16bit lin; 3 = 24 bit, 4 = 32bit float, 8 = 64bit float
			}
			raf.writeInt( i1 );

			// markers + regions, loop
			regions  = (java.util.List) afd.getProperty( AudioFileDescr.KEY_REGIONS );
			if( regions != null && !regions.isEmpty() ) {
				i1		= (BICSF_CUECODE << 16) + 72;		// short cue-code, short sizeof-cuepoint (64 + 4 + 4)
				strBuf	= new byte[ 64 ];
				strBuf[ 0 ] = 0;
				for( i2 = 0; i2 < regions.size(); i2++ ) {
					region	= (Region) regions.get( i2 );
					raf.writeInt( i1 );		// chunk header
					if( region.name.length() <= 64 ) {
						raf.writeBytes( region.name );
						raf.write( strBuf, 0, 64 - region.name.length() );
					} else {
						raf.writeBytes( region.name.substring( 0, 64 ));
					}
					raf.writeInt( (int) region.span.getStart() );
					raf.writeInt( (int) region.span.getStop() );
				}
			}
			raf.writeInt( BICSF_END << 16 );
			pos				= raf.getFilePointer();
			sampleDataOffset= (pos + 1023L) & ~1023L;		// aufgerundet auf ganze kilobyte
			strBuf			= new byte[ (int) (sampleDataOffset - pos) ];
			raf.write( strBuf );							// pad until sample offset
		}
		
		protected void updateHeader( AudioFileDescr afd )
		throws IOException
		{
			// not necessary
		}

		protected long getSampleDataOffset()
		{
			return sampleDataOffset;
		}

		protected ByteOrder getByteOrder()
		{
			return ByteOrder.BIG_ENDIAN;	// XXX at the moment only big endian is supported
		}
	} // class IRCAMHeader

	private class RawHeader
	extends AudioFileHeader
	{
		// this never get's called because
		// retrieveType will never say it's a raw file
		protected void readHeader( AudioFileDescr afd )
		throws IOException
		{}
		
		protected void writeHeader( AudioFileDescr afd )
		throws IOException
		{}
		
		protected void updateHeader( AudioFileDescr afd )
		throws IOException
		{}
		
		protected long getSampleDataOffset()
		{
			return 0L;
		}
		
		protected ByteOrder getByteOrder()
		{
			return ByteOrder.BIG_ENDIAN;		// XXX check compatibility, e.g. with csound linux
		}
	} // class RawHeader
} // class AudioFile