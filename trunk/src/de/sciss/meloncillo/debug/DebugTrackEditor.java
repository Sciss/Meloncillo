/*
 *  DebugTrackEditor.java
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
 *		29-Jul-04   commented
 */

package de.sciss.meloncillo.debug;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import de.sciss.app.AbstractApplication;
import de.sciss.meloncillo.io.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.transmitter.*;

/**
 *  Frame hosting a graphical
 *  representation of a <code>MultirateTrackEditor</code>
 *  suitable for debugging its internals
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @see		de.sciss.meloncillo.io.MultirateTrackEditor
 */
public class DebugTrackEditor
extends JFrame
{
	public DebugTrackEditor( final Session doc )
	{
		super( "DebugTrackEditor" );
	
		Container cp = getContentPane();
		
		cp.setLayout( new BorderLayout() );
		
		JComponent c = new JComponent() {
			public void paintComponent( Graphics g )
			{
				Graphics2D g2 = (Graphics2D) g;
				Dimension d = getSize();
				Transmitter trns;
				MultirateTrackEditor mte;
				
				try {
					doc.bird.waitShared( Session.DOOR_TRNSMTE );
					if( doc.selectedTransmitters.size() == 0 ) return;
					trns = (Transmitter) doc.selectedTransmitters.get( 0 );
					setTitle( "Track: "+trns.getName() );
					mte = trns.getTrackEditor();
					mte.debugPaint( g2, 8, 8, d.width - 16, d.height - 16 );
				}
				finally {
					doc.bird.releaseShared( Session.DOOR_TRNSMTE );
				}
			}
		};
		
		cp.add( c, BorderLayout.CENTER );
		
		c.addMouseListener( new MouseAdapter() {
			public void mouseClicked( MouseEvent e )
			{
				repaint();
			}
		});
		
		setSize( 300, 300 );
		setLocation( 10, 10 );
		setVisible( true );
	}

	/**
	 *  Get an Action object that will open a new frame
	 *  displaying the structure of the MultiTrackEditor
	 *  of the first selected transmitter
	 *  
	 *  @param  root	application root
	 *  @param  doc		session document
	 *  @return <code>Action</code> suitable for attaching to a <code>JMenuItem</code>.
	 */
	public static Action getDebugViewAction( final Session doc )
	{
		return new AbstractAction( "View structure of selected track" ) {
			public void actionPerformed( ActionEvent e )
			{
				AbstractApplication.getApplication().addComponent( new Integer( -1 ), new DebugTrackEditor( doc ));
			}
		};
	}

	/**
	 *  Get an Action object that will dump the
	 *  structure of the MultiTrackEditors of
	 *  all selected transmitters
	 *
	 *  @param  root	application root
	 *  @param  doc		session doc
	 *  @return <code>Action</code> suitable for attaching to a <code>JMenuItem</code>.
	 */
	public static Action getDebugDumpAction( final Session doc )
	{
		return new AbstractAction( "Dump structure of selected tracks" ) {
			public void actionPerformed( ActionEvent e )
			{
				Transmitter trns;
				MultirateTrackEditor mte;
				int i;
				
				try {
					doc.bird.waitShared( Session.DOOR_TRNSMTE );
					for( i = 0; i < doc.selectedTransmitters.size(); i++ ) {
						trns = (Transmitter) doc.selectedTransmitters.get( i );
						mte = trns.getTrackEditor();
						System.err.println( "------------ Track: "+trns.getName()+" ------------" );
						mte.debugDump();
					}
				}
				finally {
					doc.bird.releaseShared( Session.DOOR_TRNSMTE );
				}
			}
		};
	}
}
