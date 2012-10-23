/*
 *  TrackRowHeader.java
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
 *		15-Jan-06	created from de.sciss.eisenkraut.timeline.ChannelRowHeader
 *		13-Jul-08	copied back from EisK
 */

package de.sciss.meloncillo.timeline;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Spring;
import javax.swing.SpringLayout;
import javax.swing.undo.UndoableEdit;
import javax.swing.undo.UndoManager;

import de.sciss.meloncillo.edit.EditSetSessionObjects;
import de.sciss.meloncillo.gui.GraphicsUtil;
import de.sciss.meloncillo.session.MutableSessionCollection;
import de.sciss.meloncillo.session.SessionCollection;
import de.sciss.meloncillo.session.SessionObject;
import de.sciss.meloncillo.util.MapManager;
import de.sciss.gui.GradientPanel;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.GraphicsHandler;
import de.sciss.util.Disposable;

/**
 *	A row header in Swing's table 'ideology'
 *	is a component left to the leftmost
 *	column of each row in a table. It serves
 *	as a kind of label for that specific row.
 *	This class shows a header left to each
 *	sound file's waveform display, with information
 *	about the channel index, possible selections
 *	and soloing/muting. In the future it could
 *	carry insert effects and the like.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 13-Jul-08
 */
public class TrackRowHeader
extends JPanel
implements MouseListener, DynamicListening, Disposable
{
	private final JLabel			lbTrackName;
	private final Track				t;
	private final SessionCollection	tracks;
	private final MutableSessionCollection	selectedTracks;
	private final UndoManager		undo;

	protected boolean				selected		= false;

    private static final Color		colrSelected	= GraphicsUtil.colrSelection;
    private static final Color		colrUnselected	= new Color( 0x00, 0x00, 0x00, 0x20 );
    private static final Color		colrDarken		= new Color( 0x00, 0x00, 0x00, 0x18 );
	private static final Paint		pntSelected		= new GradientPaint(  0, 0, colrSelected,
																		 36, 0, new Color( colrSelected.getRGB() & 0xFFFFFF, true ));
	private static final Paint		pntUnselected	= new GradientPaint(  0, 0, colrUnselected,
																		 36, 0, new Color( colrUnselected.getRGB() & 0xFFFFFF, true ));
	private static final Paint		pntDarken		= new GradientPaint(  0, 0, colrDarken,
																		 36, 0, new Color( colrDarken.getRGB() & 0xFFFFFF, true ));
	
	private final MapManager.Listener trackListener;
	private final SessionCollection.Listener selectedTracksListener;
	
	/**
	 */
	public TrackRowHeader( final Track t, final SessionCollection tracks, final MutableSessionCollection selectedTracks,
						   UndoManager undo )
	{
		super();
		
		this.t				= t;
		this.tracks			= tracks;
		this.selectedTracks	= selectedTracks;
		this.undo			= undo;
		
		final SpringLayout	lay	= new SpringLayout();
		SpringLayout.Constraints cons;
		setLayout( lay );
		
 		lbTrackName = new JLabel();
		lbTrackName.setFont( AbstractApplication.getApplication().getGraphicsHandler().getFont( GraphicsHandler.FONT_SYSTEM | GraphicsHandler.FONT_SMALL ));
		cons		= lay.getConstraints( lbTrackName );
		cons.setX( Spring.constant( 7 ));
// doesnt' work (why???)
//		cons.setY( Spring.minus( Spring.max(	// min( X, Y ) = -max( -X, -Y )
//			Spring.constant( -4 ), Spring.minus( Spring.sum(
//				lay.getConstraints( this ).getHeight(), Spring.constant( -15 )))
//		)));
		cons.setY( Spring.minus( Spring.max(	// min( X, Y ) = -max( -X, -Y )
				Spring.constant( -4 ),
				Spring.minus( Spring.sum( Spring.sum( lay.getConstraint( SpringLayout.SOUTH, this ), Spring.minus( lay.getConstraint( SpringLayout.NORTH, this ))), Spring.constant( -15 ))))));
		add( lbTrackName );
		setBorder( BorderFactory.createMatteBorder( 0, 0, 0, 2, Color.white ));   // top left bottom right

		// --- Listener ---
        new DynamicAncestorAdapter( this ).addTo( this );
		this.addMouseListener( this );

//		// XXX should only listen to the track itself (requires event manager in AbstractSessionObject ?)
//		tracksListener = new SessionCollection.Listener() {
//			public void sessionCollectionChanged( SessionCollection.Event e ) {}	// XXX could dispose
//			
//			public void sessionObjectChanged( SessionCollection.Event e )
//			{
//				if( e.collectionContains( t ) && (e.getModificationType() == Track.OWNER_RENAMED) ) {
//					checkTrackName();
//				}
//			}
//
//			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
//		};

		trackListener = new MapManager.Listener() {
			public void mapChanged( MapManager.Event e ) { /* ignore */ }
		
			public void mapOwnerModified( MapManager.Event e )
			{
				if( e.getOwnerModType() == SessionObject.OWNER_RENAMED ) {
					checkTrackName();
				}
			}
		};

		selectedTracksListener = new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				if( e.collectionContains( t )) {
					if( selected != selectedTracks.contains( t )) {
						selected = !selected;
						repaint();
					}
				}
			}
			
			public void sessionObjectChanged( SessionCollection.Event e ) { /* ignore */ }
			public void sessionObjectMapChanged( SessionCollection.Event e ) { /* ignore */ }
		};
	}
	
	public void dispose()
	{
//		pan.dispose();
	}
	
	/**
	 *  Determines if this row is selected
	 *  i.e. is part of the selected transmitters
	 *
	 *	@return	<code>true</code> if the row (and thus the transmitter) is selected
	 */
	public boolean isSelected()
	{
		return selected;
	}

	public Track getTrack()
	{
		return t;
	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
//System.err.println(" - ");		
		final Graphics2D	g2	= (Graphics2D) g;
		final int			h	= getHeight();
		final int			w	= getWidth();
		final int			x	= Math.min( w - 36, lbTrackName.getX() + lbTrackName.getWidth() );
		
//		g2.setColor( colrDarken );
//		g2.fillRect( 0, 19, w, 2 );
	g2.translate( x, 0 );
		g2.setPaint( pntDarken );
		g2.fillRect( -x, 19, x + 36, 2 );
//		g2.setColor( selected ? colrSelected : colrUnselected );
//		g2.fillRect( 0, 0, 5, h );
//		g2.fillRect( 5, 0, w, 20 );
		g2.setPaint( selected ? pntSelected : pntUnselected );
		g2.fillRect( -x, 0, x + 36, 20 );
	g2.translate( -x, 0 );

//		g2.setPaint( pntTopBorder );
//		g2.fillRect( 0, 0, w, 8 );
	g2.translate( 0, h - 8 );
		g2.setPaint( GradientPanel.pntBottomBorder );
//		g2.fillRect( 0, h - 9, w, 8 );
		g2.fillRect( 0, 0, w, 8 );
	g2.translate( 0, 8 - h );

	}

	public void paintChildren( Graphics g )
	{
		super.paintChildren( g );
		final Graphics2D	g2	= (Graphics2D) g;
		final int			w	= getWidth();
		g2.setPaint( GradientPanel.pntTopBorder );
		g2.fillRect( 0, 0, w, 8 );
	}

	protected void checkTrackName()
	{
		if( !t.getName().equals( lbTrackName.getText() )) {
			lbTrackName.setText( t.getName() );
		}
	}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		t.getMap().addListener( trackListener );
		selectedTracks.addListener( selectedTracksListener );
		checkTrackName();
//		if( !lm.attemptShared( doors, 250 )) return;
//		try {
			if( selected != selectedTracks.contains( t )) {
				selected = !selected;
				repaint();
			}
//		}
//		finally {
//			lm.releaseShared( doors );
//		}
    }

    public void stopListening()
    {
		t.getMap().removeListener( trackListener );
		selectedTracks.removeListener( selectedTracksListener );
    }

// ---------------- MouseListener interface ---------------- 
// we're listening to the ourselves

	public void mouseEntered( MouseEvent e ) { /* ignore */ }
	public void mouseExited( MouseEvent e ) { /* ignore */ }

	/**
	 *	Handle mouse presses.
	 *	<pre>
	 *  Keyboard shortcuts as in ProTools:
	 *  Alt+Click   = Toggle item & set all others to same new state
	 *  Meta+Click  = Toggle item & set all others to opposite state
	 *	</pre>
	 *
	 *	@synchronization	attempts exclusive on TRNS + GRP
	 */
	public void mousePressed( MouseEvent e )
    {
		UndoableEdit	edit;
		List			collTracks;
	
		if( e.isAltDown() ) {
			selected = !selected;   // toggle item
			if( selected ) {		// select all
//				collTracks = doc.activeTransmitters.getAll();
				collTracks = tracks.getAll();
			} else {				// deselect all
				collTracks = new ArrayList( 1 );
			}
		} else if( e.isMetaDown() ) {
			selected = !selected;   // toggle item
			if( selected ) {		// deselect all except uns
				collTracks = Collections.singletonList( t );
			} else {				// select all except us
//					collTracks = doc.activeTransmitters.getAll();
				collTracks = tracks.getAll();
				collTracks.remove( t );
			}
		} else {
			if( e.isShiftDown() ) {
				collTracks = selectedTracks.getAll();
				selected = !selected;
				if( selected ) {
					collTracks.add( t );			// add us to selection
				} else {
					collTracks.remove( t );		// remove us from selection
				}
			} else {
				if( selected ) return;						// no action
				selected	= true;
				collTracks	= Collections.singletonList( t );	// deselect all except uns
			}
		}
		// XXX should use a lazy edit here
		edit = new EditSetSessionObjects( this, selectedTracks, collTracks ).perform();
		undo.addEdit( edit );
		repaint();
    }

	public void mouseReleased( MouseEvent e ) { /* ignore */ }
	public void mouseClicked( MouseEvent e ) { /* ignore */ }
}