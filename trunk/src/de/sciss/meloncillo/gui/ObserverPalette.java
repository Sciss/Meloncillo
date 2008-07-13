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
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.DocumentListener;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.common.AppWindow;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.ParamField;
import de.sciss.gui.SpringPanel;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.edit.BasicCompoundEdit;
import de.sciss.meloncillo.edit.EditSetSessionObjectName;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.session.SessionCollection;
import de.sciss.meloncillo.session.SessionObject;
import de.sciss.meloncillo.session.SessionObjectTable;
import de.sciss.meloncillo.timeline.TimelineEvent;
import de.sciss.meloncillo.timeline.TimelineListener;

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
implements	ParamField.Listener, TimelineListener, DynamicListening, DocumentListener,
			ActionListener, SwingConstants
{
	private Session						doc					= null;
	
	private JLabel[]					lbCursorInfo;
	private final ParamField			ggTimelineStart, ggTimelineStop, ggTimelineLen;
	private final DefaultUnitTranslator	timeTrans;
	private final JTabbedPane			ggTabPane;
	private final LockButton			ggLockStop, ggLockLen;

	public static final int CURSOR_TAB		= 0;
	public static final int RECEIVER_TAB	= 1;
	public static final int TRANSMITTER_TAB	= 2;
	public static final int GROUP_TAB		= 3;
	public static final int TIMELINE_TAB	= 4;
	
	public static final int	NUM_CURSOR_ROWS		= 5;

	private final String[] TAB_NAMES	= { "observerReceiver", "observerTransmitter", "observerGroup" };
	
	private final SessionCollectionListener[] selColL	= new SessionCollectionListener[ 3 ];
	private final JTextField[]				ggNames		= new JTextField[ 3 ];
	private final SessionObjectTable[]		ggTables	= new SessionObjectTable[ 3 ];
	
	private boolean isListening	= false;
	
	/**
	 *  Constructs a new <code>ObserverPalette</code>
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
	public ObserverPalette()
	{
		super( PALETTE );
		
		final Application app = AbstractApplication.getApplication();
	
		setTitle( app.getResourceString( "paletteObserver" ));
// EEE
//		setResizable( false );
		
		final Container		cp		= getContentPane();
		JPanel				c;
		SpringPanel			p;
		JLabel				lb;
		GridBagLayout		lay;
		GridBagConstraints  con;

		ggTabPane = new JTabbedPane();
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
			ggNames[ i ]	= new JTextField( 12 );
			con.weightx		= 1.0;
			con.gridwidth   = GridBagConstraints.REMAINDER;
			lay.setConstraints( ggNames[ i ], con );
			lb.setLabelFor( ggNames[ i ]);
			c.add( ggNames[ i ]);
			con.fill		= GridBagConstraints.BOTH;
			con.weighty		= 1.0;
			ggTables[ i ]	= new SessionObjectTable( doc );
			lay.setConstraints( ggTables[ i ], con );
			c.add( ggTables[ i ]);
			ggTabPane.addTab( app.getResourceString( TAB_NAMES[ i ]), null, c, null );
//			HelpGlassPane.setHelp( c, HELP_NAMES[ i ]);	// EEE
			
//			switch( i ) {
//			case 0:
//				scSel	= doc.selectedReceivers;
//				scAll	= doc.receivers;
//				break;
//			case 1:
//				scSel	= doc.selectedTransmitters;
//				scAll	= doc.transmitters;
//				break;
//			case 2:
//				scSel	= doc.selectedGroups;
//				scAll	= doc.groups;
//				break;
//			default:
//				assert false : i;
//				scSel	= null;
//				scAll	= null;
//				break;
//			}
//			
//			new SessionCollectionListener( scSel, scAll, i + 1, ggName, ggTable, getMyRooty() );
		}

		// ----- timeline tab ------
		timeTrans		= new DefaultUnitTranslator();
		
		p				= new SpringPanel( 4, 2, 4, 2 );
		lb				= new JLabel( app.getResourceString( "observerStart" ), RIGHT );
		p.gridAdd( lb, 1, 0 );
		ggTimelineStart	= new ParamField( timeTrans );
		ggTimelineStart.addSpace( ParamSpace.spcTimeHHMMSS );
		ggTimelineStart.addSpace( ParamSpace.spcTimeSmps );
		ggTimelineStart.addSpace( ParamSpace.spcTimeMillis );
		ggTimelineStart.addSpace( ParamSpace.spcTimePercentR );
		ggTimelineStart.addListener( this );
		p.gridAdd( ggTimelineStart, 2, 0 );
		lb.setLabelFor( ggTimelineStart );
		
		ggLockStop		= new LockButton( true );
		ggLockStop.addActionListener( this );
		p.gridAdd( ggLockStop, 0, 1 );
		lb				= new JLabel( app.getResourceString( "observerStop" ), RIGHT );
		p.gridAdd( lb, 1, 1 );
		ggTimelineStop	= new ParamField( timeTrans );
		ggTimelineStop.addSpace( ParamSpace.spcTimeHHMMSS );
		ggTimelineStop.addSpace( ParamSpace.spcTimeSmps );
		ggTimelineStop.addSpace( ParamSpace.spcTimeMillis );
		ggTimelineStop.addSpace( ParamSpace.spcTimePercentR );
		ggTimelineStop.addListener( this );
		p.gridAdd( ggTimelineStop, 2, 1 );
		lb.setLabelFor( ggTimelineStop );

		ggLockLen		= new LockButton( true );
		ggLockLen.addActionListener( this );
		p.gridAdd( ggLockLen, 0, 2 );
		lb				= new JLabel( app.getResourceString( "observerLen" ), RIGHT );
		p.gridAdd( lb, 1, 2 );
		ggTimelineLen	= new ParamField( timeTrans );
		ggTimelineLen.addSpace( ParamSpace.spcTimeHHMMSS );
		ggTimelineLen.addSpace( ParamSpace.spcTimeSmps );
		ggTimelineLen.addSpace( ParamSpace.spcTimeMillis );
		ggTimelineLen.addSpace( ParamSpace.spcTimePercentR );
		ggTimelineLen.addListener( this );
		p.gridAdd( ggTimelineLen, 2, 2 );
		lb.setLabelFor( ggTimelineLen );
		
		p.makeCompactGrid( false, false );
		ggTabPane.addTab( app.getResourceString( "observerTimeline" ), null, p, null );
 
		cp.add( BorderLayout.CENTER, ggTabPane );
		
		AbstractWindowHandler.setDeepFont( ggTabPane );
		
		// --- Listener ---
        addDynamicListening( this );
		
		app.getDocumentHandler().addDocumentListener( this );

		addListener( new AbstractWindow.Adapter() {
			public void windowClosing( AbstractWindow.Event e )
			{
				dispose();
			}
		});
		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE ); // window listener see above!
		
		setPreferredSize( new Dimension( 272, 180 ));
		
		init();
		app.addComponent( Main.COMP_OBSERVER, this );
	}
	
	protected boolean autoUpdatePrefs()
	{
		return true;
	}

	protected Point2D getPreferredLocation()
	{
		return new Point2D.Float( 0.95f, 0.8f );
	}

	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_OBSERVER );
		super.dispose();
	}
	
	/**
	 *  Switch the display to a specific
	 *  tab, where zero is the cursor info tab.
	 *
	 *  @param  tabIndex	the index of the tab to show
	 */
	public void showTab( int tabIndex )
	{
		if( ggTabPane.getSelectedIndex() != tabIndex ) {
			ggTabPane.setSelectedIndex( tabIndex );
		}
	}
	
	public int getShownTab()
	{
		return ggTabPane.getSelectedIndex();
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

	// attempts shared on DOOR_TIME
	private void updateTimeline()
	{
		if( doc != null ) {

			final Span	span	= doc.timeline.getSelectionSpan();
			final double rate	= doc.timeline.getRate();

			timeTrans.setLengthAndRate( doc.timeline.getLength(), rate );
			ggTimelineStart.setValue( new Param( span.getStart(), ParamSpace.spcTimeSmps.unit ));
			ggTimelineStop.setValue( new Param( span.getStop(), ParamSpace.spcTimeSmps.unit ));
			ggTimelineLen.setValue( new Param( span.getLength(), ParamSpace.spcTimeSmps.unit ));
			if( !ggTimelineStart.isEnabled() ) ggTimelineStart.setEnabled( true );
			if( !ggTimelineStop.isEnabled() )  ggTimelineStop.setEnabled( true );
			if( !ggTimelineLen.isEnabled() )  ggTimelineLen.setEnabled( true );
			
		} else {
			
			ggTimelineStart.setEnabled( false );
			ggTimelineStop.setEnabled( false );
			ggTimelineLen.setEnabled( false );
		}
	}

//	 ---------------- ActionListener interface ---------------- 

	public void actionPerformed( ActionEvent e )
	{
		if( e.getSource() == ggLockStop ) {
			if( ggLockStop.isLocked() && ggLockLen.isLocked() ) {
				ggLockLen.setLocked( false );
			}		
		} else if( e.getSource() == ggLockLen ) {
			if( ggLockStop.isLocked() && ggLockLen.isLocked() ) {
				ggLockStop.setLocked( false );
			}		
		}
	}

	// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
    	isListening = true;
		doc = (Session) AbstractApplication.getApplication().getDocumentHandler().getActiveDocument();
		if( doc != null ) {
			doc.timeline.addTimelineListener( this );
			selColL[ 0 ].startListening();
			selColL[ 1 ].startListening();
			selColL[ 2 ].startListening();
		}
		updateTimeline();
    }

    public void stopListening()
    {
    	isListening = false;
		if( doc != null ) {
			doc.timeline.removeTimelineListener( this );
			selColL[ 0 ].stopListening();
			selColL[ 1 ].stopListening();
			selColL[ 2 ].stopListening();
		}
    }

 // ---------------- ParamListener interface ---------------- 

	public void paramValueChanged( ParamField.Event e )
	{
		long	n		= (long) e.getTranslatedValue( ParamSpace.spcTimeSmps ).val;
		long	n2;
		Span	span;
	
		if( (e.getSource() == ggTimelineStart) || (e.getSource() == ggTimelineStop) ||
			(e.getSource() == ggTimelineLen) ) {

			span	= doc.timeline.getSelectionSpan();
			
			// ----- start was adjusted -----
			if( e.getSource() == ggTimelineStart ) {
				if( ggLockLen.isLocked() ) {
					n2	= n + span.getLength();
					if( n2 > doc.timeline.getLength() ) {
						n2	= doc.timeline.getLength();
						n	= n2 - span.getLength();
						ggTimelineStart.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n, n2 );
					ggTimelineStop.setValue( new Param( n2, ParamSpace.spcTimeSmps.unit ));
				} else {
					n2 = span.getStop();
					if( n > n2 ) {
						n = n2;
						ggTimelineStart.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n, n2 );
					ggTimelineLen.setValue( new Param( span.getLength(), ParamSpace.spcTimeSmps.unit ));
				}
			// ----- stop was adjusted -----
			} else if( e.getSource() == ggTimelineStop ) {
				if( ggLockLen.isLocked() ) {
					n2		= n - span.getLength();
					if( n2 < 0 ) {
						n2	= 0;
						n	= n2 + span.getLength();
						ggTimelineStop.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					if( n > doc.timeline.getLength() ) {
						n	= doc.timeline.getLength();
						n2	= n - span.getLength();
						ggTimelineStop.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n2, n );
					ggTimelineStart.setValue( new Param( n2, ParamSpace.spcTimeSmps.unit ));
				} else {
					n2		= span.getStart();
					if( n < n2 ) {
						n	= n2;
						ggTimelineStop.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					if( n > doc.timeline.getLength() ) {
						n	= doc.timeline.getLength();
						ggTimelineStop.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n2, n );
					ggTimelineLen.setValue( new Param( span.getLength(), ParamSpace.spcTimeSmps.unit ));
				}
			// ----- len was adjusted -----
			} else {
				if( ggLockStop.isLocked() ) {
					n2		= span.getStop() - n;
					if( n2 < 0 ) {
						n2	= 0;
						n	= span.getStop();
						ggTimelineLen.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n2, n2 + n );
					ggTimelineStart.setValue( new Param( n2, ParamSpace.spcTimeSmps.unit ));
				} else {
					n2		= span.getStart() + n;
					if( n2 > doc.timeline.getLength() ) {
						n2	= doc.timeline.getLength();
						n	= n2 - span.getStart();
						ggTimelineLen.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n2 - n, n2 );
					ggTimelineStop.setValue( new Param( n2, ParamSpace.spcTimeSmps.unit ));
				}
			}
			doc.timeline.editSelect( this, span );
		}
	}

	public void paramSpaceChanged( ParamField.Event e )
	{
		ggTimelineStart.setSpace( e.getSpace() );
		ggTimelineStop.setSpace( e.getSpace() );
		ggTimelineLen.setSpace( e.getSpace() );
	}
    
// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		if( e.getSource() != this ) {
			showTab( doc.timeline.getSelectionSpan().isEmpty() ? CURSOR_TAB : TIMELINE_TAB );
			updateTimeline();
		}
    }

	public void timelineChanged( TimelineEvent e )
	{
		updateTimeline();
	}
    
	public void timelinePositioned( TimelineEvent e ) { /* ignored */ }
    public void timelineScrolled( TimelineEvent e ) { /* ignored */ }

 // ---------------- DocumentListener interface ---------------- 

	public void documentFocussed( de.sciss.app.DocumentEvent e )
	{
		if( doc != null ) {
			doc.timeline.removeTimelineListener( this );
			selColL[ 0 ].stopListening(); selColL[ 0 ] = null;
			selColL[ 1 ].stopListening(); selColL[ 1 ] = null;
			selColL[ 2 ].stopListening(); selColL[ 2 ] = null;
		}
		doc	= (Session) e.getDocument();
		if( doc != null ) {
			doc.timeline.addTimelineListener( this );
			selColL[ 0 ] = new SessionCollectionListener( doc.selectedReceivers, doc.receivers, 1, ggNames[ 0 ], ggTables[ 0 ]);
			selColL[ 1 ] = new SessionCollectionListener( doc.selectedTransmitters, doc.transmitters, 2, ggNames[ 1 ], ggTables[ 1 ]);
			selColL[ 2 ] = new SessionCollectionListener( doc.selectedGroups, doc.groups, 2, ggNames[ 2 ], ggTables[ 2 ]);
			if( isListening ) {
				selColL[ 0 ].startListening();
				selColL[ 1 ].startListening();
				selColL[ 2 ].startListening();
			}
		}
		updateTimeline();
	}

	public void documentAdded( de.sciss.app.DocumentEvent e ) { /* ignore */ }
	public void documentRemoved( de.sciss.app.DocumentEvent e ) { /* ignore */ }

// ---------------- internal classes ---------------- 

	private class SessionCollectionListener
	implements ActionListener, SessionCollection.Listener, DynamicListening
	{
		private final int tab;
		private final SessionCollection scSel, scAll;
		private final JTextField ggName;
		private final SessionObjectTable ggTable;
	
		private SessionCollectionListener( SessionCollection scSel, SessionCollection scAll,
										   int tab, JTextField ggName,
										   SessionObjectTable ggTable )
		{
			this.scSel	= scSel;
			this.scAll	= scAll;
			this.tab	= tab;
			this.ggName	= ggName;
			this.ggTable= ggTable;
			
//			ggName.addActionListener( this );
//			new DynamicAncestorAdapter( this ).addTo( ancestor );
		}
	
		public void sessionCollectionChanged( SessionCollection.Event e )
		{
			showTab( scSel.isEmpty() ? CURSOR_TAB : tab );   // intelligently switch between the tabs
			ggTable.setObjects( scSel.getAll() );
			updateFields( e );
		}
		
		public void sessionObjectMapChanged( SessionCollection.Event e )
		{
			// nada
		}

		public void sessionObjectChanged( SessionCollection.Event e )
		{
			if( e.getModificationType() == SessionObject.OWNER_RENAMED ) {
				updateFields( e );
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
					edit.addPerform( new EditSetSessionObjectName( this, (SessionObject) coll.get( 0 ),
					                                               name ));
				} else {
					Session.makeNamePattern( name, args );
					for( i = 0; i < num; i++ ) {
						so		= (SessionObject) coll.get( i );
						name	= SessionCollection.createUniqueName( Session.SO_NAME_PTRN, args, coll2 );
						edit.addPerform( new EditSetSessionObjectName( this, so, name ));
						coll2.add( so );
					}
				}
				edit.perform();
				edit.end();
				doc.getUndoManager().addEdit( edit );
			}
		}

		public void startListening()
		{
			scSel.addListener( this );
			ggName.addActionListener( this );
		}

		public void stopListening()
		{
			scSel.removeListener( this );
			ggName.removeActionListener( this );
		}
	} // class sessionCollectionListener
}