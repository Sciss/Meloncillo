/*
 *  ObserverPalette.java
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
 *		13-Jun-04   now uses NumberFields. New tabs for Transmitter + Timeline.
 *					Removed SurfaceListener interface. showTab() and showCursorInfo() methods.
 *		31-Jul-04   DynamicAncestorAdapter replaces DynamicComponentAdapter
 *      24-Dec-04   extends BasicPalette
 *      27-Dec-04   added online help
 *		19-Mar-05	fixed wrong tab switch (timeline events), removed automatic frame size packing
 */

package de.sciss.meloncillo.gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.RootPaneContainer;
import javax.swing.undo.CompoundEdit;

import de.sciss.util.NumberSpace;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.app.Application;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.common.AppWindow;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.NumberEvent;
import de.sciss.gui.NumberField;
import de.sciss.gui.NumberListener;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.edit.BasicCompoundEdit;
import de.sciss.meloncillo.edit.EditSetSessionObjectName;
import de.sciss.meloncillo.edit.TimelineVisualEdit;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionCollection;
import de.sciss.meloncillo.session.SessionObject;
import de.sciss.meloncillo.session.SessionObjectTable;
import de.sciss.meloncillo.timeline.TimelineEvent;
import de.sciss.meloncillo.timeline.TimelineListener;
import de.sciss.meloncillo.util.LockManager;

/**
 *  The <code>ObserverPalette</code> is a
 *  GUI component that displays context sensitive
 *  data and is closely linked to the user's
 *  mouse activities. It contains tabbed panels
 *  for general cursor information and information
 *  concerning receivers, transmitters and the
 *  timeline. Depending on the tab, the data is
 *  presented in editable text or number fields.
 *  <p>
 *  The cursor info pane is 'passive' because in this way
 *  it is easily expandible to display data of new
 *  components. The interested components are hence
 *  resonsible for calling <code>showCursorInfo</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 19-Jun-08
 */
public class ObserverPalette
extends AppWindow
implements NumberListener, TimelineListener, DynamicListening
{
	private final Session	doc;
	
	private JLabel[]			lbCursorInfo;
	private final NumberField	ggTimelineStart, ggTimelineStop;
	private final JTabbedPane	ggTabPane;

	private final String[] TAB_NAMES	= { "observerReceiver", "observerTransmitter", "observerGroup" };
//	private final String[] HELP_NAMES	= { "ObserverReceiver", "ObserverTransmitter", "ObserverGroup" };
	private final int[] DOORS			= { Session.DOOR_RCV, Session.DOOR_TRNS, Session.DOOR_GRP };
	
	public static final int CURSOR_TAB		= 0;
	public static final int RECEIVER_TAB	= 1;
	public static final int TRANSMITTER_TAB	= 2;
	public static final int GROUP_TAB		= 3;
	public static final int TIMELINE_TAB	= 4;
	
	public static final int NUM_CURSOR_ROWS	= 5;
	
	/**
	 *  Constructs a new <code>ObserverPalette</code>
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
	public ObserverPalette( Main root, final Session doc )
	{
		super( PALETTE );
		
		this.doc	= doc;

		final Container					cp		= getContentPane();
		final Application				app		= AbstractApplication.getApplication();
		JPanel							c;
		JLabel							lb;
		GridBagLayout					lay;
		GridBagConstraints				con;
//		final NumberSpace				spcTime = new NumberSpace( 0.0, Double.POSITIVE_INFINITY, 0.001, 0.0, 1.0 );
		final NumberSpace				spcTime = new NumberSpace( 0.0, Double.POSITIVE_INFINITY, 0.0, 3, 3, 0.0 ); 
		JTextField						ggName;
		SessionObjectTable				ggTable;
		SessionCollection				scSel, scAll;
//		final JRootPane					rp		= getRootPane();

		setTitle( app.getResourceString( "paletteObserver" ));

		ggTabPane = new JTabbedPane();
//		HelpGlassPane.setHelp( ggTabPane, "ObserverPalette" );	// EEE
        ggTabPane.setTabLayoutPolicy( JTabbedPane.WRAP_TAB_LAYOUT );

		// ----- cursor tab ------
		c				= new JPanel();
		lay				= new GridBagLayout();
		con				= new GridBagConstraints();
		con.insets		= new Insets( 2, 2, 2, 2 );
		c.setLayout( lay );
		con.weightx		= 1.0;
		con.gridwidth   = GridBagConstraints.REMAINDER;
		lbCursorInfo	= new JLabel[ NUM_CURSOR_ROWS ];
		for( int i = 0; i < NUM_CURSOR_ROWS; i++ ) {
			lb			= new JLabel();
			lay.setConstraints( lb, con );
			c.add( lb );
			lbCursorInfo[i] = lb;
		}
		ggTabPane.addTab( app.getResourceString( "observerCursor" ), null, c, null );
//        HelpGlassPane.setHelp( c, "ObserverCursor" );	// EEE
        
		// ----- session objects tab ------
		for( int i = 0; i < 3; i++ ) {
			c				= new JPanel();
			lay				= new GridBagLayout();
			con				= new GridBagConstraints();
			con.insets		= new Insets( 2, 2, 2, 2 );
			c.setLayout( lay );
			lb				= new JLabel( app.getResourceString( "labelName" ), JLabel.RIGHT );
			con.weightx		= 0.0;
			con.gridwidth   = 1;
			lay.setConstraints( lb, con );
			c.add( lb );
			ggName			= new JTextField( 12 );
			con.weightx		= 1.0;
			con.gridwidth   = GridBagConstraints.REMAINDER;
			lay.setConstraints( ggName, con );
			lb.setLabelFor( ggName );
			c.add( ggName );
			con.fill		= GridBagConstraints.BOTH;
			con.weighty		= 1.0;
			ggTable			= new SessionObjectTable( doc, doc.bird, DOORS[ i ]);
			lay.setConstraints( ggTable, con );
			c.add( ggTable );
			ggTabPane.addTab( app.getResourceString( TAB_NAMES[ i ]), null, c, null );
//			HelpGlassPane.setHelp( c, HELP_NAMES[ i ]);	// EEE
			
			switch( i ) {
			case 0:
				scSel	= doc.selectedReceivers;
				scAll	= doc.receivers;
				break;
			case 1:
				scSel	= doc.selectedTransmitters;
				scAll	= doc.transmitters;
				break;
			case 2:
				scSel	= doc.selectedGroups;
				scAll	= doc.groups;
				break;
			default:
				assert false : i;
				scSel	= null;
				scAll	= null;
				break;
			}
			
//			new sessionCollectionListener( scSel, scAll, doc.bird, DOORS[ i ], i + 1, ggName, ggTable, rp );
			new sessionCollectionListener( scSel, scAll, doc.bird, DOORS[ i ], i + 1, ggName, ggTable, getMyRooty() );
		}
		
		// ----- timeline tab ------
		c				= new JPanel();
		lay				= new GridBagLayout();
		con				= new GridBagConstraints();
		con.insets		= new Insets( 2, 2, 2, 2 );
		c.setLayout( lay );
		lb				= new JLabel( app.getResourceString( "observerStart" ), JLabel.RIGHT );
		con.weightx		= 0.0;
		con.gridwidth   = 1;
		lay.setConstraints( lb, con );
		c.add( lb );
		ggTimelineStart	= new NumberField( spcTime );
		ggTimelineStart.setFlags( NumberField.HHMMSS );
		ggTimelineStart.addListener( this );
		con.weightx		= 0.5;
		con.gridwidth   = GridBagConstraints.REMAINDER;
		lay.setConstraints( ggTimelineStart, con );
		c.add( ggTimelineStart );
		lb.setLabelFor( ggTimelineStart );
		lb				= new JLabel( app.getResourceString( "observerStop" ), JLabel.RIGHT );
		con.weightx		= 0.0;
		con.gridwidth   = 1;
		lay.setConstraints( lb, con );
		c.add( lb );
		ggTimelineStop	= new NumberField( spcTime );
		ggTimelineStop.setFlags( NumberField.HHMMSS );
		ggTimelineStop.addListener( this );
		con.weightx		= 0.5;
		con.gridwidth   = GridBagConstraints.REMAINDER;
		lay.setConstraints( ggTimelineStop, con );
		c.add( ggTimelineStop );
		lb.setLabelFor( ggTimelineStop );
		ggTabPane.addTab( app.getResourceString( "observerTimeline" ), null, c, null );
//        HelpGlassPane.setHelp( c, "ObserverTimeline" );	// EEE
        
		cp.add( BorderLayout.CENTER, ggTabPane );
		
		AbstractWindowHandler.setDeepFont( ggTabPane );
		
		// --- Listener ---
		addDynamicListening( this );
		
//		addListener( new AbstractWindow.Adapter() {
//			public void windowClosing( AbstractWindow.Event e )
//			{
//				dispose();
//			}
//		});
//		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE ); // window listener see above!

		setPreferredSize( new Dimension( 272, 180 ));
		init();
		app.addComponent( Main.COMP_OBSERVER, this );
	}
	
	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_OBSERVER );
		super.dispose();
	}

	protected boolean autoUpdatePrefs()
	{
		return true;
	}

	// XXX EEE XXX
	private JComponent getMyRooty()
	{
		if( getWindow() instanceof RootPaneContainer ) {
			return ((RootPaneContainer) getWindow()).getRootPane();
		} else if( getWindow() instanceof JComponent ) {
			return (JComponent) getWindow();
		} else {
			return null;
		}
	}
	
	/**
	 *	Returns <code>false</code>
	 */
	protected boolean alwaysPackSize()
	{
		return false;
	}

	protected Point2D getPreferredLocation()
	{
		return new Point2D.Float( 0.95f, 0.8f );
	}

	/**
	 *  Switch the display to a specific
	 *  tab, where zero is the cursor info tab.
	 *
	 *  @param  tabIndex	the index of the tab to show
	 */
	public void showTab( int tabIndex )
	{
		ggTabPane.setSelectedIndex( tabIndex );
	}

	/**
	 *  Display information in the cursor pane.
	 *  It's up to the caller what kind of information
	 *  is displayed. This method simply displays
	 *  up to four lines of text. This method doesn't
	 *  switch the display to the cursor pane.
	 *
	 *  @param  info	an array of zero to four strings
	 *					which will be displayed in the
	 *					cursor tab.
	 */
	public void showCursorInfo( String[] info )
	{
		int i, j;
		
		j = Math.min( NUM_CURSOR_ROWS, info.length );
		
		for( i = 0; i < j; i++ ) {
			lbCursorInfo[i].setText( info[i] );
		}
		for( ; i < NUM_CURSOR_ROWS; i++ ) {
			lbCursorInfo[i].setText( "" );
		}
	}

	// to be called with shared lock on DOOR_TIME!
	private void updateTimeline( TimelineEvent e )
	{
		Span	span	= doc.timeline.getSelectionSpan();
		int		rate	= doc.timeline.getRate();

		ggTimelineStart.setNumber( new Double( (double) span.getStart() / rate ));
		ggTimelineStop.setNumber( new Double( (double) span.getStop() / rate ));
	}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		doc.timeline.addTimelineListener( this );
    }

    public void stopListening()
    {
		doc.timeline.removeTimelineListener( this );
    }

// ---------------- NumberListener interface ---------------- 

	public void numberChanged( NumberEvent e )
	{
		Span				span;
		long				n;
		double				d		= e.getNumber().doubleValue();
	
		if( (e.getSource() == ggTimelineStart) || (e.getSource() == ggTimelineStop) ) {

			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				span	= doc.timeline.getSelectionSpan();
				n		= (long) (d * doc.timeline.getRate() + 0.5);
				
				if( e.getSource() == ggTimelineStart ) {
					span	= new Span( Math.max( 0, Math.min( span.getStop(), n )), span.getStop() );
				} else {
					span	= new Span( span.getStart(), Math.min( doc.timeline.getLength(),
														 Math.max( span.getStart(), n )) );
				}
				doc.getUndoManager().addEdit( TimelineVisualEdit.select( this, doc, span ).perform() );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	}
    
// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		try {
			doc.bird.waitShared( Session.DOOR_TIME );
			showTab( doc.timeline.getSelectionSpan().isEmpty() ? CURSOR_TAB : TIMELINE_TAB );   // intelligently switch between the tabs
			updateTimeline( e );
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
    }

	public void timelineChanged( TimelineEvent e )
	{
		try {
			doc.bird.waitShared( Session.DOOR_TIME );
			updateTimeline( e );
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
	}
    
	public void timelinePositioned( TimelineEvent e ) {}
    public void timelineScrolled( TimelineEvent e ) {}

// ---------------- internal classes ---------------- 

	private class sessionCollectionListener
	implements ActionListener, SessionCollection.Listener, DynamicListening
	{
		private final LockManager lm;
		private final int doors;
		private final int tab;
		private final SessionCollection scSel, scAll;
		private final JTextField ggName;
		private final SessionObjectTable ggTable;
	
		private sessionCollectionListener( SessionCollection scSel, SessionCollection scAll,
										   LockManager lm, int doors, int tab, JTextField ggName,
										   SessionObjectTable ggTable, JComponent ancestor )
		{
			this.scSel	= scSel;
			this.scAll	= scAll;
			this.lm		= lm;
			this.doors	= doors;
			this.tab	= tab;
			this.ggName	= ggName;
			this.ggTable= ggTable;
			
			ggName.addActionListener( this );
			new DynamicAncestorAdapter( this ).addTo( ancestor );
		}
	
		public void sessionCollectionChanged( SessionCollection.Event e )
		{
			try {
				lm.waitShared( doors );
				showTab( scSel.isEmpty() ? CURSOR_TAB : tab );   // intelligently switch between the tabs
				ggTable.setObjects( scSel.getAll() );
				updateFields( e );
			}
			finally {
				lm.releaseShared( doors );
			}
		}
		
		public void sessionObjectMapChanged( SessionCollection.Event e )
		{
//			System.out.println( "KUUKA" );
//			pack();
		}

		public void sessionObjectChanged( SessionCollection.Event e )
		{
			if( e.getModificationType() == SessionObject.OWNER_RENAMED ) {
				try {
					lm.waitShared( doors );
					updateFields( e );
				}
				finally {
					lm.releaseShared( doors );
				}
			}
		}

		// to be called with shared lock on 'doors'!
		private void updateFields( SessionCollection.Event e )
		{
			SessionObject					so;
			final int						numObj	= scSel.size();
			String							name;
			final de.sciss.app.Application	app		= AbstractApplication.getApplication();

			if( numObj >= 1 ) {
				so		= (SessionObject) scSel.get( 0 );
				name	= numObj == 1 ? so.getName() : app.getResourceString( "observerMultiSelection" );
			} else {
				name	= app.getResourceString( "observerNoSelection" );
			}
			ggName.setText( name );
		}

		public void actionPerformed( ActionEvent e )
		{
			SessionObject			so;
			int						i, num;
			AbstractCompoundEdit	edit;
			String					name;
			List					coll, coll2;
			Object[]				args;

			if( e.getSource() == ggName ) {
				if( !lm.attemptExclusive( doors, 250 )) return;
				try {
					coll	= scSel.getAll();
					coll2	= scAll.getAll();
					num		= coll.size();
					if( num == 0 ) return;
					
					args	= new Object[ 3 ];
					
					coll2.removeAll( coll );
					edit	= new BasicCompoundEdit();
					name	= ggName.getText();
					if( num == 1 ) {
						if( SessionCollection.findByName( coll2, name ) != null ) {
							Session.makeNamePattern( name, args );
							name = SessionCollection.createUniqueName( Session.SO_NAME_PTRN, args, coll2 );
						}
						edit.addPerform( new EditSetSessionObjectName( this, doc, (SessionObject) coll.get( 0 ),
						                                               name, doors ));
					} else {
						Session.makeNamePattern( name, args );
						for( i = 0; i < num; i++ ) {
							so		= (SessionObject) coll.get( i );
							name	= SessionCollection.createUniqueName( Session.SO_NAME_PTRN, args, coll2 );
							edit.addPerform( new EditSetSessionObjectName( this, doc, so, name, doors ));
							coll2.add( so );
						}
					}
					edit.perform();
					edit.end();
					doc.getUndoManager().addEdit( edit );
				}
				finally {
					lm.releaseExclusive( doors );
				}
			}
		}

		public void startListening()
		{
			scSel.addListener( this );
		}

		public void stopListening()
		{
			scSel.removeListener( this );
		}
	} // class sessionCollectionListener
}