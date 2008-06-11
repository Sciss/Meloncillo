/*
 *  TimeWarpFilter.java
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
 *		15-Jun-04   created
 *		14-Jul-04   integrated new RenderSource concept
 *		24-Jul-04   directly extends JPanel / implements RenderPlugIn
 *		02-Sep-04	commented
 *		01-Jan-05	added online help
 */

package de.sciss.meloncillo.render;

import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.math.*;
import de.sciss.meloncillo.plugin.*;
import de.sciss.meloncillo.session.*;

import de.sciss.io.*;

/**
 *	A trajectory filter plug-in
 *	that maps input time offsets to
 *	output time offsets, thus provides
 *	time warping such as reversal,
 *	accelerando, ritardando, repetitions
 *	and so on. The mapping is done
 *	through a vector editor interface
 *	whose values are linearily interpolated.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo	there should be a time compression / expansion option
 */
public class TimeWarpFilter
extends JPanel
implements RenderPlugIn, TopPainter
{
	private Main			root;
	private VectorEditor	warpEditor;

	// context options map
	private static final String	KEY_RENDERINFO	= TimeWarpFilter.class.getName();

	// ---- top painting ----
	private Line2D		shpOriginal			= new Line2D.Double( 0.0, 0.0, 1.0, 1.0 );
	private Color		colrGuide			= Color.red; // new Color( 0xFF, 0xFF, 0x00, 0x7F );
	private static final float[] dash		= { 5.0e-3f, 5.0e-3f };
	private static final Stroke	strkGuide   = new BasicStroke( 2.0e-3f, BasicStroke.CAP_SQUARE,
															   BasicStroke.JOIN_BEVEL, 1.0f, dash, 0.0f );

	/**
	 *	Just calls the super class constructor.
	 *	Real initialization is in the init method
	 */
	public TimeWarpFilter()
	{
		super();
	}

	public void init( Main root, Session doc )
	{
		this.root   = root;
//		this.doc	= doc;
		
//		String className	= getClass().getName();
//		classPrefs			= AbstractApplication.getApplication().getUserPrefs().node(
//								PrefsUtil.NODE_SESSION ).node(
//									className.substring( className.lastIndexOf( '.' ) + 1 ));
		createSettingsView();
	}

	private void createSettingsView()
	{
		float			f1;
		float[]			warpTable;
		final	ToolBar	vtb			= new VectorEditorToolBar( root );
		
		warpTable   = new float[ 1024 ];
		f1			= 1.0f / 1023;
		for( int i = 0; i < 1024; i++ ) {
			warpTable[i] = i * f1;
		}
		
		warpEditor  = new VectorEditor( warpTable );
		warpEditor.setSpace( null, VectorSpace.createLinSpace( 0.0, 1.0, 0.0, 1.0, null, null, null, null ));
		warpEditor.setPreferredSize( new Dimension( 320, 320 ));
		warpEditor.addTopPainter( this );
		warpEditor.addMouseListener( new PopupListener( VectorTransformer.createPopupMenu( warpEditor )));
		vtb.setBorder( BorderFactory.createMatteBorder( 0, 0, 2, 0, Color.white ));

		this.setLayout( new BorderLayout() );
		this.add( vtb, BorderLayout.NORTH );
		this.add( warpEditor, BorderLayout.CENTER );
		vtb.addToolActionListener( warpEditor );
//        HelpGlassPane.setHelp( this, "FilterTimeWarp" );	// EEE
	}

	// --- GUI Presentation ---
	
	public JComponent getSettingsView( PlugInContext context )
	{
		return this;
	}

	public boolean producerBegin( RenderContext context, RenderSource source )
	throws IOException
	{
		int				trnsIdx;
		RenderInfo		info	= new RenderInfo();
		boolean			success = false;
		AudioFileDescr	afd		= new AudioFileDescr();
		AudioFileDescr	afd2;
		
		context.moduleMap.put( KEY_RENDERINFO, info );
		info.outLength		= context.getTimeSpan().getLength();
		info.progLen		= info.outLength << 1;		// arbit.
		info.progOff		= 0;
		
		// request all trajectories and
		// create temporary files for them
		afd.type			= AudioFileDescr.TYPE_AIFF;
		afd.channels		= 2;
		afd.rate			= context.getSourceRate();
		afd.bitsPerSample	= 32;
		afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;

		try {
			info.tempFiles = new File[ source.numTrns ];
			info.iffs		= new InterleavedStreamFile[ source.numTrns ];
			for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
				info.tempFiles[ trnsIdx ]	= IOUtil.createTempFile();
				afd2						= new AudioFileDescr( afd );
				afd2.file					= info.tempFiles[ trnsIdx ];
				info.iffs[ trnsIdx ]		= AudioFile.openAsWrite( afd2 );
				source.trajRequest[ trnsIdx ] = true;
			}
			success = true;
		}
		finally {
			if( !success ) cleanUp( info );
		}
		
		return success;
	}
	
	/**
	 *	Data is copied to temporary files
	 *	because we need random access during
	 *	time warping, which is actually performed
	 *	in <code>producerFinish</code>.
	 */
	public boolean producerRender( RenderContext context, RenderSource source )
	throws IOException
	{
		RenderInfo		info   = (RenderInfo) context.moduleMap.get( KEY_RENDERINFO );
		int				trnsIdx;
		boolean			success = false;

		try {
			for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
				info.iffs[ trnsIdx ].writeFrames( source.trajBlockBuf[ trnsIdx ],
												   source.blockBufOff, source.blockBufLen );
			}
			info.progOff  += source.blockBufLen;
			success			= true;
			((RenderHost) context.getHost()).setProgression( (float) info.progOff / (float) info.progLen );
		}
		finally {
			if( !success ) cleanUp( info );
		}

		return success;
	}
	
	/**
	 *	Shifts warped data back to the consumer
	 *	by evaluation of the mapping curve and
	 *	caching small chunks of input data from
	 *	the temp files.
	 */
	public boolean producerFinish( RenderContext context, RenderSource source )
	throws IOException
	{
		RenderInfo			info		= (RenderInfo) context.moduleMap.get( KEY_RENDERINFO );
		boolean				success		= false;
		int					trnsIdx, i, j;
		Integer				num;
		int					blockBufSize, blockBufSizeH;
		// describes the span of the iffs currently read into a cache buffer
		float[][][]			cacheBufs;
		float[]				warpTable   = warpEditor.getVector();
		long				startPos, warpedTimeI, outputWeight, cacheStart, cacheStop;
		double				inputWeight, warpedTime, phase, inputWarp, outputWarp;

		info.consumer = (RenderConsumer) context.getOption( RenderContext.KEY_CONSUMER );
		if( info.consumer == null ) return true;

		try {
			info.source		= new RenderSource( source );
			info.consumer	= (RenderConsumer) context.getOption( RenderContext.KEY_CONSUMER );
			if( !info.consumer.consumerBegin( context, info.source )) return false;
			num				= (Integer) context.getOption( RenderContext.KEY_MINBLOCKSIZE );
			i				= num == null ? 2 : num.intValue();
			num				= (Integer) context.getOption( RenderContext.KEY_MAXBLOCKSIZE );
			j				= num == null ? 0x7FFFFFFF : num.intValue();
			num				= (Integer) context.getOption( RenderContext.KEY_PREFBLOCKSIZE );
			blockBufSize	= num == null ? Math.max( i, Math.min( j, 1024 )) : num.intValue();
			blockBufSizeH   = blockBufSize >> 1;
			info.source.trajBlockBuf = new float[ source.numTrns ][ 2 ][ blockBufSize ];
			info.source.blockBufOff = 0;
			info.source.blockSpan = new Span( context.getTimeSpan().getStart(), context.getTimeSpan().getStart() );
			cacheBufs		= new float[ source.numTrns ][ 2 ][ blockBufSize + 1 ];  // + 1 because of linear interp.
			cacheStart		= 0;
			cacheStop		= 0;
			outputWeight	= (info.outLength - 1);
			inputWeight		= (double) (warpTable.length - 1) / (double) outputWeight;

			for( startPos = 0; startPos < info.outLength; ) {
				info.source.blockBufLen = (int) Math.min( blockBufSize, info.outLength - startPos );
				for( i = 0; i < info.source.blockBufLen; i++, startPos++ ) {
					inputWarp   = startPos * inputWeight;
					phase		= inputWarp % 1.0;
					j			= (int) inputWarp;
					outputWarp  = warpTable[ j ] * (1.0 - phase) + warpTable[ (j + 1) % warpTable.length ] * phase;
					warpedTime  = outputWarp * outputWeight;
					phase		= warpedTime % 1.0;
					warpedTimeI = (long) warpedTime;
					if( (warpedTimeI < cacheStart) || (warpedTimeI + 1 >= cacheStop) ) {	// need to re-cache
						cacheStart	= Math.max( 0, warpedTimeI - blockBufSizeH );	// center around needed time
						cacheStop   = Math.min( info.outLength, cacheStart + blockBufSize );
						j			= (int) (cacheStop - cacheStart);
						for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
							info.iffs[ trnsIdx ].seekFrame( cacheStart );
							info.iffs[ trnsIdx ].readFrames( cacheBufs[ trnsIdx ], 0, j );
						}
//System.err.println( "loaded new cache : "+cacheStart+" ... "+cacheStop+" (since warpedTime = "+warpedTime+")" );
					}
					j = (int) (warpedTimeI - cacheStart);
					for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
						info.source.trajBlockBuf[ trnsIdx ][ 0 ][ i ] =
							(float) (cacheBufs[ trnsIdx ][ 0 ][ j ]   * (1.0 - phase) + 
									 cacheBufs[ trnsIdx ][ 0 ][ j+1 ] * phase);
						info.source.trajBlockBuf[ trnsIdx ][ 1 ][ i ] =
							(float) (cacheBufs[ trnsIdx ][ 1 ][ j ]   * (1.0 - phase) + 
									 cacheBufs[ trnsIdx ][ 1 ][ j+1 ] * phase);
					} 
				}
				info.source.blockSpan = new Span( info.source.blockSpan.getStop(),
											       info.source.blockSpan.getStop() + info.source.blockBufLen );
				if( !info.consumer.consumerRender( context, info.source )) return false;
				info.progOff  += info.source.blockBufLen;
				((RenderHost) context.getHost()).setProgression( (float) info.progOff / (float) info.progLen );
			}

			success = info.consumer.consumerFinish( context, info.source );
		}
		finally {
			if( !success ) {
				try {
					info.consumer.consumerCancel( context, info.source );
				}
				catch( IOException e1 ) {
					((RenderHost) context.getHost()).setException( e1 );
				}
			}
			cleanUp( info );
		}

		((RenderHost) context.getHost()).setProgression( 1.0f );
		return success;
	}
	
	public void producerCancel( RenderContext context, RenderSource source )
	throws IOException
	{
		RenderInfo info = (RenderInfo) context.moduleMap.get( KEY_RENDERINFO );

		try {
			if( info.consumer != null ) {
				info.consumer.consumerCancel( context, info.source );
			}
		}
		finally {
			cleanUp( info );
		}
	}
	
	private void cleanUp( RenderInfo info )
	{
		int i;
	
		if( info.iffs != null ) {
			for( i = 0; i < info.iffs.length; i++ ) {
				if( info.iffs[i] != null ) {
					try {
						info.iffs[i].close();
					} catch( IOException e1 ) {}
					info.iffs[i]  = null;
				}
			}
		}
		if( info.tempFiles != null ) {
			for( i = 0; i < info.tempFiles.length; i++ ) {
				if( info.tempFiles[i] != null ) {
					info.tempFiles[i].delete();
					info.tempFiles[i] = null;
				}
			}
		}
	}

// ------- TopPainter interface -------

	/**
	 *	A straight line is painted
	 *	on top of the mapping vector editor
	 *	which represents linear time warp
	 *	(i.e. NO warp) as a reference.
	 */
	public void paintOnTop( Graphics2D g2 )
	{
		Stroke strkOrig = g2.getStroke();
		
		g2.setColor( colrGuide );
		g2.setStroke( strkGuide );
		g2.draw( shpOriginal );
		g2.setStroke( strkOrig );
	}

// -------- RenderInfo internal class --------
	private class RenderInfo
	{
		private File[]					tempFiles;  // one for each trns
		private InterleavedStreamFile[]  iffs;		// one for each trns
		private long					outLength, progOff, progLen;
		private RenderConsumer			consumer;
		private RenderSource			source;
	}
}