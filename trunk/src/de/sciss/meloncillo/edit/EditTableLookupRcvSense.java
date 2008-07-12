/*
 *  EditTableLookupRcvSense.java
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
 *		04-Apr-05	created from EditChangeSectorReceiverSensitivity
 *		26-May-05	renamed to EditTableLookupRcvSense for 31-characters filename limit
 */

package de.sciss.meloncillo.edit;

import java.io.*;
import javax.swing.undo.*;

import de.sciss.meloncillo.receiver.*;
import de.sciss.meloncillo.session.*;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;
import de.sciss.io.*;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the modification of a receiver's
 *  sensibility tables.
 *  <p><small>
 *  all instances share one <code>InterleavedStreamFile</code>
 *  that is created the first time a <code>EditTableLookupRcvSense</code>
 *  is instantiated. Successive edits will write their undo data
 *  to this float file. The float file is a temporary file that
 *  will be deleted if the undo history is purged or the application
 *  quits.
 *  </small>
 *
 *  @author				Hanns Holger Rutz
 *  @version			0.75, 10-Jun-08
 *  @see				UndoManager
 *  @see				de.sciss.meloncillo.io.InterleavedStreamFile
 *  @synchronization	this class is thread safe
 */
public class EditTableLookupRcvSense
extends BasicUndoableEdit
{
	private Object						source;
	private final Session				doc;
	private final TableLookupReceiver	rcv;
	private final Span					distSpan, rotSpan;
	
	private static InterleavedStreamFile iff	= null;
	private static long writeOffset				= 0;
	private static int  writeCount				= 0;
	
	private final long	oldDistOff, newDistOff, oldRotOff, newRotOff;
	private final int	oldDistLen, newDistLen, oldRotLen, newRotLen;

	/**
	 *  Change the Sensitivity of a <code>TableLookupReceiver</code>. 
	 *
	 *  @param  source		The modfying instance. This is the source of the
	 *						<code>SessionCollection.Event</code>
	 *						that will be dispatched if the transfer succeeds. Successive undo/redos
	 *						however will not report the original source anymore in order to ensure
	 *						a complete GUI update.
	 *  @param  doc			The document which the Receiver belongs to
	 *  @param  rcv			The receiver to modify
	 *  @param  distTab		The table holding the new distance values. This can be null in which case
	 *						the distance table of the Receiver is not changed.
	 *  @param  rotTab		The table holding the new rotation values. This can be null in which case
	 *						the rotation table of the Receiver is not changed.
	 *  @param  distSpan	This describes the offset and length in distTab to be copied to the
	 *						Receiver. If distSpan is null, a complete replacement is assumed.
	 *						distSpan must be null if the length of the distTab differs from the
	 *						distance table of the Receiver, e.g. in case of a clipboard paste.
	 *  @param  rotSpan		This describes the offset and length in rotTab to be copied to the
	 *						Receiver. If rotSpan is null, a complete replacement is assumed.
	 *						rotSpan must be null if the length of the rotTab differs from the
	 *						rotation table of the Receiver, e.g. in case of a clipboard paste.
	 *
	 *  @throws IOException Since undo data is stored to the temporary directory this
	 *						constructor can possibly fail. The target receiver remains
	 *						safely unchanged if this exception is thrown
	 *  @see	de.sciss.meloncillo.session.SessionCollection.Event
	 *  @synchronization	waitExclusive on DOOR_RCV.
	 */
	public EditTableLookupRcvSense( Object source, Session doc, TableLookupReceiver rcv,
									float[] distTab, Span distSpan, float[] rotTab, Span rotSpan )
	throws IOException
	{
		super();

		float[][]   frames  = new float[1][];
		long		off;
		int			off2;

		try {
			doc.bird.waitExclusive( Session.DOOR_RCV );
			if( iff == null ) {
				AudioFileDescr afd	= new AudioFileDescr();
				afd.type			= AudioFileDescr.TYPE_AIFF;
				afd.channels		= 1;
				afd.rate			= 1000.0f;	// XXX arbitrary
				afd.bitsPerSample	= 32;
				afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
				afd.file			= IOUtil.createTempFile();
				iff					= AudioFile.openAsWrite( afd );
			}

			synchronized( iff ) {
				off			= writeOffset;
				oldDistOff  = off;
				if( distTab != null ) {
					frames[0]   = rcv.getDistanceTable();
					if( distSpan != null ) {	// copy parts
						oldDistLen	= (int) distSpan.getLength();
						off2		= (int) distSpan.getStart();
						newDistLen  = oldDistLen;
					} else {					// completely replace buffer
						oldDistLen  = frames[0].length;
						off2		= 0;
						newDistLen  = distTab.length;
					}
					iff.seekFrame( off );
					iff.writeFrames( frames, off2, oldDistLen );
					off		   += oldDistLen;
					newDistOff  = off;
					frames[0]   = distTab;
					iff.writeFrames( frames, off2, newDistLen );
					off		   += newDistLen;
				} else {
					oldDistLen  = 0;
					newDistLen  = 0;
					newDistOff  = off;
				}
				oldRotOff   = off;
				if( rotTab != null ) {
					frames[0]   = rcv.getRotationTable();
					if( rotSpan != null ) {		// copy parts
						oldRotLen   = (int) rotSpan.getLength();
						off2		= (int) rotSpan.getStart();
						newRotLen   = oldRotLen;
					} else {					// completely replace buffer
						oldRotLen   = frames[0].length;
						off2		= 0;
						newRotLen   = rotTab.length;
					}
					iff.seekFrame( off );
					iff.writeFrames( frames, off2, oldRotLen );
					off		   += oldRotLen;
					newRotOff   = off;
					frames[0]   = rotTab;
					iff.writeFrames( frames, off2, newRotLen );
					off		   += newRotLen;
				} else {
					oldRotLen	= 0;
					newRotLen	= 0;
					newRotOff   = off;
				}
				writeOffset		= off;
				writeCount++;
			} // synchronized( iff )
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_RCV );
		}

		this.source			= source;
		this.doc			= doc;
		this.rcv			= rcv;
		this.distSpan		= distSpan;
		this.rotSpan		= rotSpan;
//		this.source			= this;
	}

	/**
	 *  Tell this edit that it is never needed again.
	 *  If all EditChangeTableLookupReceiverSensibilities died,
	 *  which happens in case of a undo history purge,
	 *  the temporary file is cleared and therefore
	 *  unused diskspace is freed.
	 */
	public void die()
	{
		super.die();
		synchronized( iff ) {
			writeCount--;
			assert writeCount >= 0 : writeCount;
			
			if( writeCount == 0 ) {		// free disk space after undo history purge
				try {
					writeOffset = 0;
					iff.seekFrame( 0 );
					iff.truncate();
				}
				catch( IOException e1 ) {}
			}
		} // synchronized( iff )
	}
	
	public PerformableEdit perform()
	{
		try {
			perform( newDistOff, newDistLen, newRotOff, newRotLen );
		}
		catch( IOException e1 ) { // hmmm..... what to do??? XXX
			e1.printStackTrace();
		}
		return this;
	}
	
	private void perform( long distOff, int distLen, long rotOff, int rotLen )
	throws IOException
	{
		float[]		distTab = new float[ distLen ];
		float[]		rotTab  = new float[ rotLen ];
		float[][]   frames	= new float[1][];
	
		try {
			doc.bird.waitExclusive( Session.DOOR_RCV );
			synchronized( iff ) {
				if( distLen != 0 ) {
					frames[0] = distTab;
					iff.seekFrame( distOff );
					iff.readFrames( frames, 0, distLen );
				}
				if( rotLen != 0 ) {
					frames[0] = rotTab;
					iff.seekFrame( rotOff );
					iff.readFrames( frames, 0, rotLen );
				}
			} // synchronized( iff )

			// now it's safe to replace the contents
			if( distLen != 0 ) {
				if( distSpan != null ) {		// copy parts
					System.arraycopy( distTab, 0, rcv.getDistanceTable(), (int) distSpan.getStart(), distLen );
				} else {						// completely replace buffer
					rcv.setDistanceTable( (float[]) distTab.clone() );
				}
			}
			if( rotLen != 0 ) {
				if( rotSpan != null ) {		// copy parts
					System.arraycopy( rotTab, 0, rcv.getRotationTable(), (int) rotSpan.getStart(), rotLen );
				} else {					// completely replace buffer
					rcv.setRotationTable( (float[]) rotTab.clone() );
				}
			}
			rcv.getMap().dispatchOwnerModification( source, Receiver.OWNER_SENSE, null );
//			doc.receivers.modified( source, rcv );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_RCV );
			this.source = this;
		}
	}

	/**
	 *  Perform the undo operation. IO errors due to
	 *  problems reading the temporary file are
	 *  propagated as a <code>CannotUndoException</code>.
	 *
	 *  @throws javax.swing.undo.CannotUndoException	if an i/o error occurs
	 */
	public void undo()
	{
		super.undo();
		try {
			perform( oldDistOff, oldDistLen, oldRotOff, oldRotLen );
		}
		catch( IOException e1 ) {
			throw new CannotUndoException();
		}
	}
	
	/**
	 *  Perform the redo operation. IO errors due to
	 *  problems reading the temporary file are
	 *  propagated as a <code>CannotRedoException</code>.
	 *  The original source is discarded in order
	 *  to have it act well behaved as if the
	 *  edit was caused by someone else.
	 *
	 *  @throws javax.swing.undo.CannotRedoException	if an i/o error occurs
	 */
	public void redo()
	{
		super.redo();
		try {
			perform( newDistOff, newDistLen, newRotOff, newRotLen );
		}
		catch( IOException e1 ) {
			throw new CannotRedoException();
		}
	}

	public String getPresentationName()
	{
		return getResourceString( "editChangeReceiverSensitivity" );
	}
}