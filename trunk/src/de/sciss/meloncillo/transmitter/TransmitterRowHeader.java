/*
 *  TransmitterRowHeader.java
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
 *		01-Aug-04   added checkTrnsName
 *		02-Sep-04	commented
 *      27-Dec-04   added online help
 *		19-Mar-05	fixed: selecting all transmitters (alt+click) will only
 *					select transmitters in active group; displays longer transmitter names
 */

package de.sciss.meloncillo.transmitter;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.undo.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.session.*;

import de.sciss.app.*;

/**
 *	A row header in Swing's table 'ideology'
 *	is a component left to the leftmost
 *	column of each row in a table. It serves
 *	as a kind of label for that specific row.
 *	In Meloncillo's timeline frame, each transmitter
 *	is displayed using a transmitter editor. The
 *	editor however simply shows the trajectory,
 *	so this is an additional component viewing
 *	that transmitter's name. Besides it allows
 *	to select and deselect transmitters.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class TransmitterRowHeader
extends JPanel
implements MouseListener, DynamicListening
{
	private final Transmitter	trns;
	private final Session		doc;
	private final JLabel		lbTrnsName;

	private boolean				selected		= false;

    private static final Color  colrSelection   = GraphicsUtil.colrSelection;
	private static final Paint	pntTopBorder	= new GradientPaint( 0.0f, 0.0f, new Color( 0xFF, 0xFF, 0xFF, 0xFF ),
																	 0.0f, 8.0f, new Color( 0xFF, 0xFF, 0xFF, 0x00 ));
	private static final Paint	pntBottomBorder	= new GradientPaint( 0.0f, 0.0f, new Color( 0x9F, 0x9F, 0x9F, 0xFF ),
																	 0.0f, 8.0f, new Color( 0x9F, 0x9F, 0x9F, 0x00 ),
																	 true );
	
	private final SessionCollection.Listener transmittersListener;
	private final SessionCollection.Listener selectedTransmittersListener;

	/**
	 *	Creates a new row header display
	 *	for a given transmitter. The row header
	 *	will track changes in the transmitter's
	 *	name and selection state.
	 *
	 *	@param	root	Application root
	 *	@param	doc		Session document
	 *	@param	trns	the transmitter to represent
	 */
	public TransmitterRowHeader( final Main root, final Session doc, final Transmitter trns )
	{
		super( new BorderLayout() );
	
		this.doc	= doc;
		this.trns   = trns;
		
		lbTrnsName  = new JLabel();
		lbTrnsName.setFont( GraphicsUtil.smallGUIFont );
		lbTrnsName.setBorder( BorderFactory.createEmptyBorder( 0, 4, 0, 0 ));   // top left bottom right
		add( lbTrnsName, BorderLayout.CENTER );
		add( new FlagsPanel( trns, doc.transmitters, doc.bird, Session.DOOR_TRNS ), BorderLayout.SOUTH );
		setPreferredSize( new Dimension( 64, 16 )); // XXX
		setMaximumSize( new Dimension( 64, getMaximumSize().height )); // XXX
		setBorder( BorderFactory.createMatteBorder( 0, 0, 0, 2, Color.white ));   // top left bottom right

		// --- Listener ---
        new DynamicAncestorAdapter( this ).addTo( this );
		this.addMouseListener( this );

		transmittersListener = new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e ) {}	// XXX could dispose
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.getModificationType() == Transmitter.OWNER_RENAMED ) {
					checkTrnsName();
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) {}		// XXX solo, mute
		};

		selectedTransmittersListener = new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				selected = doc.selectedTransmitters.contains( trns );
				repaint();
			}
			
			public void sessionObjectChanged( SessionCollection.Event e ) {}
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		};
	
//        HelpGlassPane.setHelp( this, "TransmitterTrack" );	// EEE
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

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		final Graphics2D	g2	= (Graphics2D) g;
		final int			h	= getHeight();
		final int			w	= getWidth();
		
		g2.setPaint( pntTopBorder );
		g2.fillRect( 0, 0, w, 8 );
		g2.setPaint( pntBottomBorder );
		g2.fillRect( 0, h - 9, w, 8 );
		
		if( selected ) {
			g2.setColor( colrSelection );
			g2.fillRect( 0, 0, w, h );
		}
	}

	// syncs attemptShared to DOOR_TRNS
	private void checkTrnsName()
	{
		if( !doc.bird.attemptShared( Session.DOOR_TRNS, 250 )) return;
		try {
			if( !trns.getName().equals( lbTrnsName.getText() )) {
				lbTrnsName.setText( trns.getName() );
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRNS );
		}
	}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		doc.transmitters.addListener( transmittersListener );
		doc.selectedTransmitters.addListener( selectedTransmittersListener );
		checkTrnsName();
// XXX		selected = doc.selectedTransmitter.contains( trns );
    }

    public void stopListening()
    {
		doc.transmitters.removeListener( transmittersListener );
		doc.selectedTransmitters.removeListener( selectedTransmittersListener );
    }

// ---------------- MouseListener interface ---------------- 
// we're listening to the ourselves

	public void mouseEntered( MouseEvent e ) {}
	public void mouseExited( MouseEvent e ) {}

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
		java.util.List	collTransmitters;
	
		if( !doc.bird.attemptExclusive( Session.DOOR_TRNS | Session.DOOR_GRP, 250 )) return;
		try {
			if( e.isAltDown() ) {
				selected = !selected;   // toggle item
				if( selected ) {		// select all
					collTransmitters = doc.activeTransmitters.getAll();
				} else {				// deselect all
					collTransmitters = new ArrayList( 1 );
				}
			} else if( e.isMetaDown() ) {
				selected = !selected;   // toggle item
				if( selected ) {		// deselect all except uns
					collTransmitters = new ArrayList( 1 );
					collTransmitters.add( trns );
				} else {				// select all except us
					collTransmitters = doc.activeTransmitters.getAll();
					collTransmitters.remove( trns );
				}
			} else {
				if( e.isShiftDown() ) {
					collTransmitters = doc.selectedTransmitters.getAll();
					selected = !selected;
					if( selected ) {
						collTransmitters.add( trns );			// add us to selection
					} else {
						collTransmitters.remove( trns );		// remove us from selection
					}
				} else {
					if( selected ) return;						// no action
					selected			= true;
					collTransmitters	= new ArrayList( 1 );	// deselect all except uns
					collTransmitters.add( trns );
				}
			}
			edit = new EditSetSessionObjects( this, doc, doc.selectedTransmitters,
													   collTransmitters, Session.DOOR_TRNS );
			doc.getUndoManager().addEdit( edit );
			repaint();
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TRNS | Session.DOOR_GRP );
		}
    }

	public void mouseReleased( MouseEvent e ) {}
	public void mouseClicked( MouseEvent e ) {}
}