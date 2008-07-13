/*
 *  TableLookupReceiver.java
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
 *		04-Apr-05	created
 */

package de.sciss.meloncillo.receiver;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.IOUtil;
import de.sciss.io.InterleavedStreamFile;
import de.sciss.meloncillo.io.XMLRepresentation;
import de.sciss.meloncillo.util.MapManager;

/**
 *  This receiver is abstract and generalizes
 *	the behaviour of SigmaReceiver and SectorReceiver
 *	allowing these subclasses to be lightweight.
 *	<p>
 *	This class manages clipboard copy/paste operations.
 *	For subclasses to be copyable, they must implement
 *	a public constructor that takes an instance of the same
 *	class as its only argument, such als
 *	<p><pre>
 *	public SectorReceiver( SectorReceiver orig )
 *	</pre>
 *	This constructor must call the super constructor
 *	<code>protected TableLookupReceiver( TableLookupReceiver orig )</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@warning	the order of sequence of initialization is
 *				very difficult and should be simplified.
 *
 *	@todo		simplify constructor processes
 */
public abstract class TableLookupReceiver
extends AbstractReceiver
implements Transferable
{
	/**
	 *	PI times two is saved here for convenience
	 */
	protected static double	PI2	= Math.PI * 2;

	/**
	 *	a vector describing the sensitivity with respect
	 *	to a point's distance to the anchor
	 */
	protected float[]		distanceTable;
	/**
	 *	a vector describing the sensitivity with respect
	 *	to a point's angle to the anchor
	 */
	protected float[]		rotationTable;
	
	private static final String SUFFIX_DISTANCE		= "-dst.aif";
	private static final String SUFFIX_ROTATION		= "-rot.aif";

	private static final DataFlavor[] supportedFlavors = { receiverFlavor, DataFlavor.stringFlavor };

	/**
	 *  Creates a new Receiver with custom tables.
	 *
	 *	@param	distanceTable	a vector describing the sensitivity with respect
	 *							to a point's distance to the anchor
	 *	@param	rotationTable	a vector describing the sensitivity with respect
	 *							to a point's angle to the anchor
	 */
	protected TableLookupReceiver( float[] distanceTable, float[] rotationTable )
	{
		super();
		
		this.distanceTable  = distanceTable;
		this.rotationTable  = rotationTable;
	}
	
	/**
	 *  Creates a new receiver which is identical
	 *  to a template receiver. This is used in clipboard
	 *  operations.
	 *
	 *  @param  orig	the receiver to copy. tables are
	 *					are copied so successive modifications of
	 *					<code>orig</code>'s tables do not influence
	 *					the newly created receiver.
	 */
	protected TableLookupReceiver( TableLookupReceiver orig )
	{
		super( orig );

		this.distanceTable  = new float[ orig.distanceTable.length ];
		System.arraycopy( orig.distanceTable, 0, this.distanceTable, 0, orig.distanceTable.length );
		this.rotationTable  = new float[ orig.rotationTable.length ];
		System.arraycopy( orig.rotationTable, 0, this.rotationTable, 0, orig.rotationTable.length );
	}

	/**
	 *	Asks the receiver to recalculate all
	 *	internal values that are necessary for
	 +	outline description and/or sensitivity calculation.
	 *	This is called by <code>TableLookupReceiver</code>
	 *	if any map key which is element of the bounding keys
	 *	is modified.
	 *	<p>
	 *	Subclasses must call this method themselves <strong>at the end</strong>
	 *	of their constructor bodies. Do not call this method from the
	 *	<code>init()</code> method because a <code>NullPointerException</code>
	 *	is likely to occur.
	 *
	 *	@see	#getBoundingKeys()
	 */
	protected abstract void recalcBounds();
	
	/**
	 *	Queries the map keys that 
	 *	influence the shape and bounding box
	 *	of the receiver.
	 *
	 *	@return	an array of keys all of which are relevant
	 *			for the receiver's shape
	 */
	protected abstract String[] getBoundingKeys();

	/**
	 *  Gets the table describing the
	 *  sensitivity as function of the distance.
	 *  The table is not copied and should thus
	 *  not be modified directly. This method
	 *  should only be called by special classes
	 *  such as the editor or undoable edits.
	 *
	 *  @return the distance table of the receiver
	 *
	 *  @see	de.sciss.meloncillo.edit.EditTableLookupRcvSense
	 */
	public final float[] getDistanceTable()
	{
		return distanceTable;
	}

	/**
	 *  Gets the table describing the
	 *  sensitivity as function of the angle.
	 *  The table is not copied and should thus
	 *  not be modified directly. This method
	 *  should only be called by special classes
	 *  such as the editor or undoable edits.
	 *
	 *  @return the rotation table of the receiver
	 *
	 *  @see	de.sciss.meloncillo.edit.EditTableLookupRcvSense
	 */
	public final float[] getRotationTable()
	{
		return rotationTable;
	}

	/**
	 *  Sets the table describing the
	 *  sensitivity as function of the distance.
	 *  The table is not copied! This method
	 *  should only be called by special classes
	 *  such as the editor or undoable edits.
	 *
	 *  @param  distanceTable   the new distance table for the receiver
	 *
	 *  @see	de.sciss.meloncillo.edit.EditTableLookupRcvSense
	 */
	public void setDistanceTable( float[] distanceTable )
	{
		this.distanceTable  = distanceTable;
	}

	/**
	 *  Sets the table describing the
	 *  sensitivity as function of the angle.
	 *  The table is not copied! This method
	 *  should only be called by special classes
	 *  such as the editor or undoable edits.
	 *
	 *  @param  rotationTable   the new rotation table for the receiver
	 *
	 *  @see	de.sciss.meloncillo.edit.EditTableLookupRcvSense
	 */
	public void setRotationTable( float[] rotationTable )
	{
		this.rotationTable  = rotationTable;
	}

// ---------------- Transferable interface ---------------- 

	public DataFlavor[] getTransferDataFlavors()
	{
		return supportedFlavors;
	}
	
	/**
	 *  Supported flavors are <code>receiverFlavor</code>
	 *  and <code>stringFlavor</code>. The string is
	 *  produced from the receiver's name.
	 *
	 *  @param  flavor  the flavor to check for support
	 *  @return <code>true</code> if the flavor is supported
	 *
	 *  @see	Receiver#receiverFlavor
	 *  @see	java.awt.datatransfer.DataFlavor#stringFlavor
	 */
	public boolean isDataFlavorSupported( DataFlavor flavor )
	{
		for( int i = 0; i < supportedFlavors.length; i++ ) {
			if( supportedFlavors[i].equals( flavor )) return true;
		}
		return false;
	}

	public Object getTransferData( DataFlavor flavor )
	throws UnsupportedFlavorException, IOException
	{
		if( flavor.equals( receiverFlavor )) {
			try {
				// try to find a constructor that takes a TableLookupReceiver
				// as argument
				Constructor cons = this.getClass().getConstructor( new Class[] { this.getClass() });
				return cons.newInstance( new Object[] { this });
			}
			catch( NoSuchMethodException e1 ) {}
			catch( InstantiationException e2 ) {}
			catch( IllegalAccessException e3 ) {}
			catch( IllegalArgumentException e4 ) {}
			catch( InvocationTargetException e5 ) {}
			
		} else if( flavor.equals( DataFlavor.stringFlavor )) {
			return getName();
		}
		throw new UnsupportedFlavorException( flavor );
	}

// ---------------- MapManager.Listener interface ---------------- 

	public void mapChanged( MapManager.Event e )
	{
		super.mapChanged( e );

//		if( !initialized ) return;

		final Set		keySet			= e.getPropertyNames();
		final String[]	boundingKeys	= getBoundingKeys();
		boolean			needsRecalc		= false;

		for( int i = 0; i < boundingKeys.length; i++ ) {
			if( keySet.contains( boundingKeys[ i ])) {
				needsRecalc = true;
				break;
			}
		}
		
		if( needsRecalc ) recalcBounds();
	}

// ---------------- XMLRepresentation interface ---------------- 

	/** 
	 *  Additionally saves the sensitivity tables
	 *  to extra files in the folder specified through
	 *  <code>setDirectory</code>. One <code>InterleavedStreamFile</code>s
	 *  is used for each table, because table sizes might
	 *  differ from each other. The file name's are
	 *  deduced from the receiver's logical name and special
	 *  suffix.
	 *
	 *  @see	de.sciss.meloncillo.io.InterleavedStreamFile
	 */
	public void toXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		super.toXML( domDoc, node, options );
	
		InterleavedStreamFile	iff;
		float[][]				frameBuf	= new float[ 1 ][];
		File					dir			= new File( (File) options.get(
												XMLRepresentation.KEY_BASEPATH ), SUBDIR );
		AudioFileDescr			afd;
		
		if( !dir.isDirectory() ) IOUtil.createEmptyDirectory( dir );
		
		afd					= new AudioFileDescr();
		afd.type			= AudioFileDescr.TYPE_AIFF;
		afd.channels		= 1;
		afd.rate			= 1000.0f;	// XXX
		afd.bitsPerSample	= 32;
		afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
		afd.file			= new File( dir, getName() + SUFFIX_DISTANCE );
		iff					= AudioFile.openAsWrite( afd );
					  
		frameBuf[ 0 ]		= distanceTable;
		iff.writeFrames( frameBuf, 0, distanceTable.length );
		iff.truncate();
		iff.close();

		afd					= new AudioFileDescr( afd );
		afd.file			= new File( dir, getName() + SUFFIX_ROTATION );
		iff					= AudioFile.openAsWrite( afd );
			
		frameBuf[ 0 ]		= rotationTable;
		iff.writeFrames( frameBuf, 0, rotationTable.length );
		iff.truncate();
		iff.close();
	}

	/** 
	 *  Additionally recalls the sensitivity tables
	 *  from extra files in the folder specified through
	 *  <code>setDirectory</code>. One <code>InterleavedStreamFile</code>s
	 *  is used for each table, because table sizes might
	 *  differ from each other. The file name's are
	 *  deduced from the receiver's logical name and special
	 *  suffix.
	 *
	 *  @see	de.sciss.meloncillo.io.InterleavedStreamFile
	 */
	public void fromXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		super.fromXML( domDoc, node, options );

		InterleavedStreamFile	iff;
		float[][]				frameBuf	= new float[ 1 ][];
		int						size;

		// read the tables from a named getName() in the directory getDirectory()
// File f2 = new File( getDirectory(), getName() + SUFFIX_DISTANCE );
// System.err.println( "file name : "+f2.getAbsolutePath() );

		iff = AudioFile.openAsRead( new File( new File( (File) options.get(
				XMLRepresentation.KEY_BASEPATH ), SUBDIR ), getName() + SUFFIX_DISTANCE ));
			
		size = (int) iff.getFrameNum();
		if( size != distanceTable.length ) {
			distanceTable = new float[ size ];
		}
		frameBuf[ 0 ] = distanceTable;
		iff.readFrames( frameBuf, 0, size );
		iff.close();

		iff = AudioFile.openAsRead( new File( new File( (File) options.get(
				XMLRepresentation.KEY_BASEPATH ), SUBDIR ), getName() + SUFFIX_ROTATION ));
			
		size = (int) iff.getFrameNum();
		if( size != rotationTable.length ) {
			rotationTable = new float[ size ];
		}
		frameBuf[ 0 ] = rotationTable;
		iff.readFrames( frameBuf, 0, size );
		iff.close();
	}
}