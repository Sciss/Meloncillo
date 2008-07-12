/*
 *  SimpleTransmitter.java
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
 *		02-Sep-04	commented
 */

package de.sciss.meloncillo.transmitter;

import java.io.*;
import java.util.*;
import org.w3c.dom.*;

import de.sciss.meloncillo.io.*;

import de.sciss.app.*;
import de.sciss.io.*;

/**
 *  The most basic Transmitter, just
 *	offering a two channel multirate track editor
 *	for trajectory administration and a facility
 *	to import and export from/to XML.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class SimpleTransmitter
extends AbstractTransmitter
{
	private static final String SUFFIX_TRAJECTORY	= "-trj.aif";
	private static final String SUFFIX_TEMP			= "-trj.tmp";

	private static final Class	defaultEditor		= SimpleTransmitterEditor.class;
    
//  private final AudioTrail at;
    private AudioTrail at;
    private final DecimatedWaveTrail dwt;
//	private static final int[] decimations	= { 0, 2, 4, 6, 8, 10, 12 };
	private static final int[] decimations	= { 2, 4, 6, 8, 10, 12 };

	/**
	 *  Creates a new SimpleTransmitter with defaults
	 */
	public SimpleTransmitter()
	throws IOException
	{
		super();
		
		final AudioFileDescr afd = new AudioFileDescr();
		
//		at = new AudioTrail( 2, 1000, AudioTrail.MODEL_MEDIAN, decimations );	// XXX rate
		afd.bitsPerSample	= 32;
		afd.channels		= 2;
		afd.rate			= 1000;
		afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
		afd.type			= AudioFileDescr.TYPE_AIFF;
		at	= AudioTrail.newFrom( afd );
		dwt	= new DecimatedWaveTrail( at, DecimatedWaveTrail.MODEL_MEDIAN, decimations );
	}

	public Class getDefaultEditor()
	{
		return defaultEditor;
	}
	
	public AudioTrail getAudioTrail()
	{
		return at;
	}

	public DecimatedWaveTrail getDecimatedWaveTrail()
	{
		return dwt;
	}

// ---------------- XMLRepresentation imterface ---------------- 

	/** 
	 *  Additionally saves the trajectory data
	 *  to an extra file in the folder specified through
	 *  <code>setDirectory</code>. One two channel <code>InterleavedStreamFile</code>
	 *  is used to store the full rate cartesian coordinates.
	 *	Subsampled files are not stored at the moment.
	 *	The file name is
	 *  deduced from the transmitter's logical name and special
	 *  suffix.
	 *
	 *  @see	de.sciss.meloncillo.io.InterleavedStreamFile
	 *
	 *	@synchronization	caller must have sync on mte
	 *	
	 *	@todo				this method should attempt a sync itself
	 *	@todo				after saving, the non-fragmented traj file
	 *						should replace the whole track list of the mte
	 */
	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		super.toXML( domDoc, node, options );

		File					f, f2;
		InterleavedStreamFile	iff;
		File					dir;
		AudioFileDescr			afd;

		// flatten trajectory in a file named getName() in the directory getDirectory()

		// as soon as the old file is still used for reading, we
		// need to backup it first
		dir	= new File( (File) options.get( XMLRepresentation.KEY_BASEPATH ), SUBDIR );
		if( !dir.isDirectory() ) IOUtil.createEmptyDirectory( dir );
		f   = new File( dir, getName() + SUFFIX_TRAJECTORY );
		f2  = null;
		if( f.exists() ) {
			f2 = new File( dir, getName() + SUFFIX_TEMP );
			f2.delete();
			if( !f.renameTo( f2 )) throw new IOException(
				AbstractApplication.getApplication().getResourceString( "errBackupTraj" ));
			f = new File( dir, getName() + SUFFIX_TRAJECTORY );
		}
		afd					= new AudioFileDescr();
		afd.type			= AudioFileDescr.TYPE_AIFF;
		afd.rate			= at.getRate();
		afd.channels		= at.getChannelNum();
		afd.bitsPerSample	= 32;
		afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
		afd.file			= f;

		iff					= AudioFile.openAsWrite( afd );
		
		final int[] chanMap = new int[ at.getChannelNum() ];
		for( int i = 0; i < chanMap.length; i++ ) chanMap[ i ] = i;
		at.flatten( iff, at.getSpan(), chanMap );
		iff.truncate();
		iff.close();
		
		if( f2 != null ) f2.delete();
	}

	/** 
	 *  Additionally restores the trajectory data
	 *  from an extra file in the folder specified through
	 *  <code>setDirectory</code>. The file name is
	 *  deduced from the transmitter's logical name and special
	 *  suffix. Subsampled files in the temp dir are created
	 *	automatically.
	 *
	 *  @see	de.sciss.meloncillo.io.InterleavedStreamFile
	 *
	 *	@synchronization	caller must have sync on mte
	 *	
	 *	@todo				this method should attempt a sync itself
	 *	@todo				the file name should be stored in the session
	 *						as well and relative path names should be used
	 */
	public void fromXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		super.fromXML( domDoc, node, options );

		final AudioFile af = AudioFile.openAsRead( new File( new File(
			(File) options.get( XMLRepresentation.KEY_BASEPATH ), SUBDIR ), getName() + SUFFIX_TRAJECTORY ));
			
//		at.clear( null );
//		at.insert( iff, 0, new Span( 0, iff.getFrameNum() ), null, 0.0f, 1.0f );		// XXX edit ?
		at	= AudioTrail.newFrom( af );
	}
}