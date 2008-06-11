/*
 *  MeterFrame.java
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
 *		11-Aug-04   created
 *		12-Aug-04   commented
 *		14-Aug-04   small bugfixes
 *      24-Dec-04   support for intruding-grow-box prefs.
 *                  extends BasicPalette
 *      26-Dec-04   added online help
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.realtime.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.common.AppWindow;

/**
 *  A GUI component that displays
 *  a list of sense meters. The user can either
 *  select a transmitter; in this case this transmitter's
 *  sense excitation on all receivers are monitored.
 *  Or the user can select a receiver; in this case all
 *  transmitters' excitations regarding this particular
 +  receiver are monitored.
 *  <p>
 *  This class contains an inner class for displaying
 *  The meters. An offscreen image is rendered depending
 *  on the window size which contains a translucent blue-to-red
 *  gradient bar. Portions of this image are painted depending
 *  on the current sensitivities.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class MeterFrame
extends AppWindow
implements  DynamicListening, RealtimeConsumer, SessionCollection.Listener
{
	private final Session		doc;
	private final Transport		transport;
	private final MeterFrame	meterFrame				= this;
	
	private final MeterPane		meterPane;
	private final Border		trnsBorder, rcvBorder;
	private final Dimension		trnsSize, rcvSize;
	private final ArrayList		collTrnsLabels			= new ArrayList();
	private final ArrayList		collRcvLabels			= new ArrayList();
	private final Box			trnsPane, rcvPane;
	private final MouseListener labelSelectionListener  = new LabelSelectionListener();
	private static final Font	fnt						= new Font( "Gill Sans", Font.ITALIC, 10 );

	private static final int METER_EXTENT   = 12;
	private static final int LABEL_EXTENT   = 48;

	// --- realtime ---
	private boolean		rt_valid		= false;
	private float[]		rt_sense		= new float[0];
	private int[]		rt_peak			= new int[0];
	private int			rt_objPeak		= -1;
	private boolean		rt_orient;		// false = a transmitter selected, true = a receiver selected
	private int			selectedTrns	= -1;
	private int			selectedRcv		= -1;
	
	/**
	 *  Constructs a new matrix meter display.
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
	public MeterFrame( Main root, Session doc )
	{
		super( PALETTE );
		setTitle( AbstractApplication.getApplication().getResourceString( "frameMeter" ));

		this.doc	= doc;
		transport   = root.transport;

		Container   cp		= getContentPane();
		Filler		pad;
		Dimension   d;

		meterPane   = new MeterPane();

		trnsPane	= Box.createVerticalBox();  // new JPanel();
//		trnsPane.setLayout( new BoxLayout( trnsPane, BoxLayout.Y_AXIS ));
		rcvPane		= Box.createHorizontalBox();	// new JPanel();
//		rcvPane.setLayout( new BoxLayout( rcvPane, BoxLayout.X_AXIS ));

		trnsSize	= new Dimension( LABEL_EXTENT, METER_EXTENT );
		trnsBorder	= BorderFactory.createMatteBorder( 0, 1, 1, 1, Color.white ); // top left bottom right
		rcvSize		= new Dimension( METER_EXTENT, LABEL_EXTENT );
		rcvBorder	= BorderFactory.createMatteBorder( 1, 0, 1, 1, Color.white ); // top left bottom right

		d			= new Dimension( LABEL_EXTENT, LABEL_EXTENT );
		pad			= new Filler( d, d, d );
		pad.setBorder( BorderFactory.createMatteBorder( 1, 1, 1, 1, Color.white ));
		rcvPane.add( pad );
		
		cp.setLayout( new BorderLayout() );
		cp.add( meterPane, BorderLayout.CENTER );
		cp.add( trnsPane, BorderLayout.WEST );
		cp.add( rcvPane, BorderLayout.NORTH );
        if( AbstractApplication.getApplication().getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
            d		= new Dimension( LABEL_EXTENT, 16 );
        	pad		= new Filler( d, d, null );
    		pad.setBorder( BorderFactory.createMatteBorder( 0, 1, 0, 1, Color.white ));
            cp.add( pad, BorderLayout.SOUTH );
        }
        
		// --- Listener ---
        addDynamicListening( this );

		// -------

// 		HelpGlassPane.setHelp( getRootPane(), "LevelMeter" );	// EEE
        init();
	}

	/*
	 *  Sync: syncs to tc / rc / grp
	 */
	private void syncLabels()
	{
		int					i, trnsIdx, rcvIdx, numTrns, numRcv, numLb;
		boolean				revalidate = false;
		String				name;
		SelectableLabel		lb;
	
		try {
			doc.bird.waitShared( Session.DOOR_TRNS | Session.DOOR_RCV | Session.DOOR_GRP );
		// ------------- Transmitters -------------
			numTrns = doc.activeTransmitters.size();
			numLb   = collTrnsLabels.size();
			i		= Math.min( numTrns, numLb );
			for( trnsIdx = 0; trnsIdx < i; trnsIdx++ ) {
				name	= doc.activeTransmitters.get( trnsIdx ).getName();
				lb		= (SelectableLabel) collTrnsLabels.get( trnsIdx );
				if( name != lb.getText() ) {
					lb.setText( name );
				}
			}
			for( ; trnsIdx < numTrns; trnsIdx++ ) {
				name	= doc.activeTransmitters.get( trnsIdx ).getName();
				lb		= new SelectableLabel( SelectableLabel.HORIZONTAL );
				lb.setText( name );
				lb.setMinimumSize( trnsSize );
				lb.setPreferredSize( trnsSize );
				lb.setMaximumSize( trnsSize );
				lb.setBorder( trnsBorder );
				lb.setFont( fnt );
				if( trnsIdx == selectedTrns ) lb.setSelected( true );
				lb.addMouseListener( labelSelectionListener );
				collTrnsLabels.add( lb );
				trnsPane.add( lb );
				revalidate = true;
			}
			for( trnsIdx = numLb - 1; trnsIdx >= numTrns; trnsIdx-- ) {
				lb		= (SelectableLabel) collTrnsLabels.remove( trnsIdx );
				trnsPane.remove( lb );
				lb.removeMouseListener( labelSelectionListener );
				revalidate = true;
			}

		// ------------- Receivers -------------
			numRcv  = doc.activeReceivers.size();
			numLb   = collRcvLabels.size();
			i		= Math.min( numRcv, numLb );
			for( rcvIdx = 0; rcvIdx < i; rcvIdx++ ) {
				name	= doc.activeReceivers.get( rcvIdx ).getName();
				lb		= (SelectableLabel) collRcvLabels.get( rcvIdx );
				if( name != lb.getText() ) {
					lb.setText( name );
				}
			}
			for( ; rcvIdx < numRcv; rcvIdx++ ) {
				name	= doc.activeReceivers.get( rcvIdx ).getName();
				lb		= new SelectableLabel( SelectableLabel.VERTICAL );
				lb.setText( name );
				lb.setMinimumSize( rcvSize );
				lb.setPreferredSize( rcvSize );
				lb.setMaximumSize( rcvSize );
				lb.setBorder( rcvBorder );
				lb.setFont( fnt );
				if( rcvIdx == selectedRcv ) lb.setSelected( true );
				lb.addMouseListener( labelSelectionListener );
				collRcvLabels.add( lb );
				rcvPane.add( lb );
				revalidate = true;
			}
			for( rcvIdx = numLb - 1; rcvIdx >= numRcv; rcvIdx-- ) {
				lb		= (SelectableLabel) collRcvLabels.remove( rcvIdx );
				rcvPane.remove( lb );
				lb.removeMouseListener( labelSelectionListener );
				revalidate = true;
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_RCV | Session.DOOR_GRP );
		}
		
		if( revalidate ) {
//			trnsPane.revalidate();
//			trnsPane.invalidate();
//			trnsPane.validate();
//			rcvPane.revalidate();
//			rcvPane.invalidate();
//			rcvPane.validate();
			this.getContentPane().repaint();
			pack();
		}
	}

// ---------------- RealtimeConsumer interface ---------------- 

	/**
	 *  Requests sense data (only for the selected
	 *  transmitter / receiver) at c. 15 fps
	 *
	 *	sync: attempt on DOOR_RCV, TRNS; GRP
	 */
	public RealtimeConsumerRequest createRequest( RealtimeContext context )
	{
		RealtimeConsumerRequest request = new RealtimeConsumerRequest( this, context );
		int						trnsIdx, rcvIdx, numTrns, numRcv;
		java.util.List			collRcv, collTrns;

		rt_valid = false;

		if( !doc.bird.attemptShared( Session.DOOR_RCV | Session.DOOR_TRNS | Session.DOOR_GRP, 250 )) return request;
		try {

			// 15 fps is ok for meter
			request.notifyTickStep  = RealtimeConsumerRequest.approximateStep( context, 15 );
			request.frameStep		= request.notifyTickStep;
			request.notifyTicks		= true;
			request.notifyOffhand	= true;

			collRcv					= doc.activeReceivers.getAll();
			collTrns				= doc.activeTransmitters.getAll();
			numRcv					= collRcv.size();
			numTrns					= collTrns.size();

			if( selectedTrns >= 0 && selectedTrns < numTrns ) {
				rt_objPeak = context.getTransmitters().indexOf( collTrns.get( selectedTrns ));
				if( rt_objPeak >= 0 ) {
					if( rt_sense.length != numRcv ) {
						rt_sense	= new float[ numRcv ];
						rt_peak		= new int[ numRcv ];
					}
					for( rcvIdx = 0; rcvIdx < numRcv; rcvIdx++ ) {
						rt_peak[ rcvIdx ] = context.getReceivers().indexOf( collRcv.get( rcvIdx ));
						if( rt_peak[ rcvIdx ] >= 0 ) {
							request.senseRequest[ rt_objPeak ][ rt_peak[ rcvIdx ]] = true;
						}
					}
				}
			} else if( selectedRcv >= 0 && selectedRcv < numRcv ) {
				rt_objPeak = context.getReceivers().indexOf( collRcv.get( selectedRcv ));
				if( rt_objPeak >= 0 ) {
					if( rt_sense.length != numTrns ) {
						rt_sense	= new float[ numTrns ];
						rt_peak		= new int[ numTrns ];
					}
					for( trnsIdx = 0; trnsIdx < numTrns; trnsIdx++ ) {
						rt_peak[ trnsIdx ] = context.getTransmitters().indexOf( collTrns.get( trnsIdx ));
						if( rt_peak[ trnsIdx ] >= 0 ) {
							request.senseRequest[ rt_peak[ trnsIdx ]][ rt_objPeak ]  = true;
						}
					}
				}
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_RCV | Session.DOOR_TRNS | Session.DOOR_GRP );
		}
		rt_valid = true;
		return request;
	}
	
	public void realtimeTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
	{
		int bufOff, trnsIdx, rcvIdx;

		if( !rt_valid ) return;

		bufOff = (int) (currentPos - source.firstHalf.getStart());
		if( bufOff < 0 || bufOff >= source.bufSizeH ) {
			bufOff = (int) (currentPos - source.secondHalf.getStart());
			if( bufOff < 0 || bufOff >= source.bufSizeH ) return;  // currently no valid buffer
			bufOff += source.bufSizeH;
		}
		
		if( rt_orient && rt_objPeak >= 0 && rt_objPeak < source.numRcv ) {
			for( trnsIdx = 0; trnsIdx < rt_sense.length; trnsIdx++ ) {
				if( rt_peak[ trnsIdx ] < 0 ) continue;
				rt_sense[ trnsIdx ]  = source.senseBlockBuf[ rt_peak[ trnsIdx ]][ rt_objPeak ][ bufOff ];
			}
		} else if( !rt_orient && rt_objPeak >= 0 && rt_objPeak < source.numTrns ) {
			for( rcvIdx = 0; rcvIdx < rt_sense.length; rcvIdx++ ) {
				if( rt_peak[ rcvIdx ] < 0 ) continue;
				rt_sense[ rcvIdx ]  = source.senseBlockBuf[ rt_objPeak ][ rt_peak[ rcvIdx ]][ bufOff ];
			}
		}
		
		meterPane.repaint();
	}

	public void offhandTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
	{
		int trnsIdx, rcvIdx;

		if( !rt_valid ) return;
		
		if( rt_orient && rt_objPeak >= 0 && rt_objPeak < source.numRcv ) {
			for( trnsIdx = 0; trnsIdx < rt_sense.length; trnsIdx++ ) {
				if( rt_peak[ trnsIdx ] < 0 ) continue;
				rt_sense[ trnsIdx ]  = source.senseOffhand[ rt_peak[ trnsIdx ]][ rt_objPeak ];
			}
		} else if( !rt_orient && rt_objPeak >= 0 && rt_objPeak < source.numTrns ) {
			for( rcvIdx = 0; rcvIdx < rt_sense.length; rcvIdx++ ) {
				if( rt_peak[ rcvIdx ] < 0 ) continue;
				rt_sense[ rcvIdx ]  = source.senseOffhand[ rt_objPeak ][ rt_peak[ rcvIdx ]];
			}
		}

		meterPane.repaint();
	}

	public void realtimeBlock( RealtimeContext context, RealtimeProducer.Source source, boolean even ) {}

// ---------------- SessionCollection.Listener interface ---------------- 

	public void sessionCollectionChanged( SessionCollection.Event e )
	{
		if( rt_valid ) {
			rt_valid = false;
			transport.removeRealtimeConsumer( this );
			transport.addRealtimeConsumer( this );
		}
		syncLabels();
	}

	public void sessionObjectChanged( SessionCollection.Event e )
	{
		if( e.getModificationType() == SessionObject.OWNER_RENAMED ) {
			syncLabels();
		}
	}

	public void sessionObjectMapChanged( SessionCollection.Event e ) {}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		rt_valid = false;
		transport.addRealtimeConsumer( this );
		doc.activeTransmitters.addListener( this );
		doc.activeReceivers.addListener( this );
		syncLabels();
    }

    public void stopListening()
    {
		doc.activeTransmitters.removeListener( this );
		doc.activeReceivers.removeListener( this );
		transport.removeRealtimeConsumer( this );
		rt_valid = false;
    }

// ---------------- internal classes ---------------- 

	private class MeterPane
	extends JComponent
	{
		private final int[] pntBarGradientPixels = {
			0xFF8C8C8C, 0xFFE1E1E1, 0xFFF5F5F5, 0xFFF6F6F6,
			0xFFF3F3F3, 0xFFD9D9D9, 0xFFE6E6E6, 0xFFEEEEEE,
			0xFFF7F7F7, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF
		};
		
		private final Paint		pntHorizBars, pntVertBars;
		private final Dimension emptySize			= new Dimension( 0, 0 );
		private Dimension		recentSize			= emptySize;
		private final Color		colrTrnslRed, colrTrnslBlue;
		private Image levelImg;
			
		Random rand = new Random( System.currentTimeMillis() );
			
		private MeterPane()
		{
			super();

			setMinimumSize( new Dimension( 85, 85 ));
			setPreferredSize( getMinimumSize() );
			setOpaque( true );

			BufferedImage   img;

			colrTrnslRed	= new Color( 0xFF, 0x00, 0x00, 0x66 );
			colrTrnslBlue   = new Color( 0x00, 0x00, 0xFF, 0x66 );
			
			img = new BufferedImage( METER_EXTENT, 1, BufferedImage.TYPE_INT_ARGB );
			img.setRGB( 0, 0, METER_EXTENT, 1, pntBarGradientPixels, 0, METER_EXTENT );
			pntVertBars = new TexturePaint( img, new Rectangle( 0, 0, METER_EXTENT, 1 ));
			img = new BufferedImage( 1, METER_EXTENT, BufferedImage.TYPE_INT_ARGB );
			img.setRGB( 0, 0, 1, METER_EXTENT, pntBarGradientPixels, 0, 1 );
			pntHorizBars = new TexturePaint( img, new Rectangle( 0, 0, 1, METER_EXTENT ));
			
		}
	
		private void recalcLevelGradients()
		{
			if( levelImg != null ) levelImg.flush();

			Graphics2D  g2;

			if( rt_orient ) {
				levelImg	= createImage( recentSize.width, METER_EXTENT );
				g2			= (Graphics2D) levelImg.getGraphics();
				g2.setPaint( pntHorizBars );
				g2.fillRect( 0, 0, recentSize.width, METER_EXTENT );
				g2.setPaint( new GradientPaint( 0.0f, 0.0f, colrTrnslBlue,
												recentSize.width, 0.0f, colrTrnslRed ));
				g2.fillRect( 0, 0, recentSize.width, METER_EXTENT );
				g2.dispose();
			} else {
				levelImg	= createImage( METER_EXTENT, recentSize.height );
				g2			= (Graphics2D) levelImg.getGraphics();
				g2.setPaint( pntVertBars );
				g2.fillRect( 0, 0, METER_EXTENT, recentSize.height );
				g2.setPaint( new GradientPaint( 0.0f, 0.0f, colrTrnslRed,
												0.0f, recentSize.height, colrTrnslBlue ));
				g2.fillRect( 0, 0, METER_EXTENT, recentSize.height );
				g2.dispose();
			}
		}

		public void paintComponent( Graphics g )
		{
			if( !rt_valid ) return;

			Dimension   d   = getSize();
			Graphics2D  g2  = (Graphics2D) g;
			int i, off, ext;
			
			if( d.width != recentSize.width || d.height != recentSize.height ) {
				recentSize = d;
				recalcLevelGradients();
			}
			
			if( rt_orient ) {
				ext = rt_sense.length * METER_EXTENT;
				g2.clearRect( 0, ext, d.width, d.height - ext );
				g2.setPaint( pntHorizBars );
				g2.fillRect( 0, 0, d.width, ext );
			
				for( i = 0, off = 0; i < rt_sense.length; i++, off += METER_EXTENT ) {
					ext = (int) (rt_sense[i] * d.width);
//					g2.fillRect( 0, i * METER_EXTENT, h, METER_EXTENT );
					g2.drawImage( levelImg, 0, off, ext, off + METER_EXTENT, 0, 0, ext, METER_EXTENT, this );
 				}
			} else {
				ext = rt_sense.length * METER_EXTENT;
				g2.clearRect( ext, 0, d.width - ext, d.height );
				g2.setPaint( pntVertBars );
				g2.fillRect( 0, 0, ext, d.height );

				for( i = 0, off = 0; i < rt_sense.length; i++, off += METER_EXTENT ) {
					ext = (int) (rt_sense[i] * d.height);
//					g2.fillRect( i * METER_EXTENT, d.height - h, METER_EXTENT, h );
					g2.drawImage( levelImg, off, d.height - ext, off + METER_EXTENT, d.height,
								  0, d.height - ext, METER_EXTENT, d.height, this );
				}
			}
		}
	}
	
	private class LabelSelectionListener
	extends MouseAdapter
	{
		public void mousePressed( MouseEvent e )
		{
			boolean oldOrient = rt_orient;
		
			rt_valid = false;
			transport.removeRealtimeConsumer( meterFrame );
			if( selectedTrns >= 0 && selectedTrns < collTrnsLabels.size() ) {
				((SelectableLabel) collTrnsLabels.get( selectedTrns )).setSelected( false );
			}
			if( selectedRcv >= 0 && selectedRcv < collRcvLabels.size() ) {
				((SelectableLabel) collRcvLabels.get( selectedRcv )).setSelected( false );
			}
			selectedRcv		= -1;
			selectedTrns	= -1;
			selectedRcv		= collRcvLabels.indexOf( e.getSource() );
			if( selectedRcv != -1 ) {
				((SelectableLabel) collRcvLabels.get( selectedRcv )).setSelected( true );
				rt_orient = true;
			} else {
				selectedTrns = collTrnsLabels.indexOf( e.getSource() );
				if( selectedTrns != -1 ) {
					((SelectableLabel) collTrnsLabels.get( selectedTrns )).setSelected( true );
					rt_orient = false;
				}
			}

			if( rt_orient != oldOrient ) meterPane.recalcLevelGradients();
	
			transport.addRealtimeConsumer( meterFrame );	// XXX check??
		}
	}
}