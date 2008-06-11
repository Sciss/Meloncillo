/*
 *  HRIRPrepareDialog.java
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
 *		27-Apr-05   created
 */

package de.sciss.meloncillo.debug;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;
import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.AbstractApplication;
import de.sciss.gui.*;
import de.sciss.io.*;

/**
 *	Temporary utility dialog to prepare
 *	head-related impulse response files from
 *	the LISTEN database for use with the binaural
 *	supercollider plug-in.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class HRIRPrepareDialog
extends JFrame
implements RunnableProcessing, ActionListener
{
	private static final int	IR_OFFSET	= 200;		// frame offset in the original IR files (just before the click)
	private static final int	IR_LENGTH	= 1024;		// truncation length of the original IRs

	private final PathField	ggInputDir, ggOutputDir;
	
	private static final String TITLE = "HRIR Preparation";
	
	private final Session doc;

	public HRIRPrepareDialog( Session doc )
	{
		super( TITLE );
		this.doc	= doc;
		
		Container cp = getContentPane();
		cp.setLayout( new SpringLayout() );
		
		int rows = 0;
		
		ggInputDir	= new PathField( PathField.TYPE_FOLDER, "Input folder ('IRC_XXXX_R')" );
		ggOutputDir	= new PathField( PathField.TYPE_FOLDER, "Output folder" );
		
		rows++;
		cp.add( new JLabel( "Input folder" ));
		cp.add( ggInputDir );
		rows++;
		cp.add( new JLabel( "Output folder" ));
		cp.add( ggOutputDir );
		rows++;
		cp.add( new JLabel( " " ));
		cp.add( new JLabel() );
		rows++;
		JButton ggProc = new JButton( "Process" );
		cp.add( ggProc );
		cp.add( new JLabel() );
		GUIUtil.makeCompactSpringGrid( cp, rows, 2, 2, 2, 4, 4 );
		
		ggProc.addActionListener( this );
		
		pack();
		show();
	}
	
	/**
	 *  @param  root	application root
	 *  @param  doc		session object
	 *
	 *  @return			an <code>Action</code>
	 *					suitable for attaching
	 *					to a <code>JMenuItem</code>. The
	 *					action will create and open a new
	 *					instance of <code>HRIRPrepareDialog</code>.
	 */
	public static Action getMenuAction( final Session doc )
	{
		return new AbstractAction( TITLE ) {
			public void actionPerformed( ActionEvent e )
			{
				AbstractApplication.getApplication().addComponent( new Integer( -1 ), new HRIRPrepareDialog( doc ));
			}
		};
	}

	public void actionPerformed( ActionEvent e )
	{
		File[] fileArgs = new File[] {
			ggInputDir.getPath(),
			ggOutputDir.getPath()
		};
				
		final Main root = (Main) AbstractApplication.getApplication();
//		public ProcessingThread( final RunnableProcessing rp, final ProgressComponent pc, final Application root,
//			 Session doc, String procName, final Object rpArgument, int requiredDoors )
		new ProcessingThread( this, root, root, doc, getTitle(), fileArgs, 0 );
	}
	
	// ------- RunnableProcessing ---------
			
	public boolean run( ProcessingThread context, Object argument )
	{
		final float[][]			inBuf			= new float[ 2 ][ IR_LENGTH ];
		final float[][]			outBuf			= new float[ 1 ][ 0 ];
		final Object[]			fileFrmtArgs	= new Object[ 5 ];
		final MessageFormat		fileFormat		= new MessageFormat( "{0}{1}{2}_R0195_T{3}_P{4}.wav", Locale.US );
		final NumberFormat		numFormat		= NumberFormat.getInstance( Locale.US );
		File					inputDir, outputDir, inputFile, outputFile;
		int						aziDeg, elevDeg;
		AudioFile				inF				= null;
		AudioFile				outF			= null;
		AudioFileDescr			inDescr, outDescr;
		File[]					fileArgs		= (File[]) argument;

		final int progLen = 24 * 7;
		int progOff = 0;
		boolean success = false;

		try {
			numFormat.setMinimumIntegerDigits( 3 );
			numFormat.setMaximumIntegerDigits( 3 );
			numFormat.setMinimumFractionDigits( 0 );
			numFormat.setMaximumFractionDigits( 0 );
			fileFormat.setFormatByArgumentIndex( 3, numFormat );
			fileFormat.setFormatByArgumentIndex( 4, numFormat );
			
			inputDir		= fileArgs[0];
			outputDir		= fileArgs[1];
			fileFrmtArgs[0]	= inputDir.getAbsolutePath();
			fileFrmtArgs[1]	= File.separator;
			fileFrmtArgs[2]	= inputDir.getName();
			
			try {
				for( int azi = 0; azi < 24; azi++ ) {
					for( int elev = 0; elev < 7; elev++ ) {
						aziDeg			= ((azi + 12) * 15) % 360;
						elevDeg			= elev <= 3 ? (45 - elev * 15) : (405 - elev * 15);
						fileFrmtArgs[3]	= new Integer( aziDeg );
						fileFrmtArgs[4]	= new Integer( elevDeg );
						inputFile		= new File( fileFormat.format( fileFrmtArgs ));

						System.err.println( "Reading '"+inputFile.getName()+"' ..." );
						inF					= AudioFile.openAsRead( inputFile );
						inDescr				= inF.getDescr();
						if( inDescr.channels != 2 ) throw new IOException( "Wrong channel num. Needs to be 2, but is "+inDescr.channels );
						if( inDescr.length != 8192 ) System.err.println( "Warning, frame length should be 8192, but is "+inDescr.length );
						inF.seekFrame( IR_OFFSET );
						inF.readFrames( inBuf, 0, IR_LENGTH );	// simply truncate
						inF.close();
						inF					= null;
						
						for( int ch = 0; ch < 2; ch++ ) {
							// short fade in / out
							for( int i = 1, j = 0; i < 32; i++, j++ ) {
								inBuf[ ch ][ j ] *= (float) i / 32;
							}
							for( int i = 31, j = IR_LENGTH - 32; i > 0; i--, j++ ) {
								inBuf[ ch ][ j ] *= (float) i / 32;
							}
						
							outputFile			= new File( outputDir, "hrtf" + (elev * 24 + azi) + (ch == 0 ? "l" : "r" ) + ".aif" );
							System.err.println( "Writing '"+outputFile.getName()+"' ..." );
							outDescr			= new AudioFileDescr( inDescr );
							outDescr.type		= AudioFileDescr.TYPE_AIFF;
							outDescr.channels	= 1;
							outDescr.file		= outputFile;
							outF				= AudioFile.openAsWrite( outDescr );
							outBuf[ 0 ]			= inBuf[ ch ];
							outF.writeFrames( outBuf, 0, IR_LENGTH );
							outF.close();
							outF				= null;
						}
						
						progOff++;
						context.setProgression( (float) progOff / (float) progLen );
					}
				}
				success = true;
			}
			finally {
				if( inF != null ) inF.cleanUp();
				if( outF != null ) outF.cleanUp();
			}
		}
		catch( IOException e1 ) {
			context.setException( e1 );
		}
		return success;
	} // run
	
	public void finished( ProcessingThread context, Object argument, boolean success )
	{
		// nothing to do
	}
}
