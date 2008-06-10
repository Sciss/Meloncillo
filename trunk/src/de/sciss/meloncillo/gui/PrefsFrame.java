/*
 *  PrefsFrame.java
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
 *  Change log:
 *		06-Jun-04   switched to PreferenceSync'ed gadgets
 *		31-Jul-04   commented
 *      24-Dec-04   new fields for look-and-feel.
 *		30-Dec-04	added online help
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.*;
import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.math.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.timeline.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.gui.*;
import de.sciss.io.IOUtil;

/**
 *  This is the frame that
 *  displays the user adjustable
 *  application and session preferences
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class PrefsFrame
extends BasicFrame
implements TimelineListener, DynamicListening
{
	private final PrefNumberField ggRate;
	
	private final Session   doc;
	
	/**
	 *  Creates a new preferences frame
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
    public PrefsFrame( final Main root, final Session doc )
    {
		super( AbstractApplication.getApplication().getResourceString( "framePrefs" ));

		this.doc	= doc;

		final Container					cp		= getContentPane();
		SpringLayout					lay;
        final Font						fnt     = GraphicsUtil.smallGUIFont;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();

		JPanel							tab, tabWrap, buttonPanel;
		PrefTextField					ggText;
		KeyStrokeTextField				ggKeyStroke;
		PrefTextArea					ggArea;
		PrefNumberField					ggNumber;
		PrefPathField					ggPath;
		PrefCheckBox					ggCheckBox;
        PrefComboBox					ggChoice;
		JButton							ggButton;
		JTabbedPane						ggTabPane;
		JLabel							lb;
        UIManager.LookAndFeelInfo[]		lafInfos;

		Preferences						prefs;
		String							key, key2;
		int								rows;

		ggTabPane			= new JTabbedPane();

		// ---------- global pane ----------

		tab		= new JPanel();
		lay		= new SpringLayout();
		tab.setLayout( lay );
		rows	= 0;

		prefs   = IOUtil.getUserPrefs();
		key		= IOUtil.KEY_TEMPDIR;
		key2	= "prefsTmpDir";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggPath = new PrefPathField( PathField.TYPE_FOLDER, app.getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggPath, key2 );
		tab.add( ggPath );
		lb.setLabelFor( ggPath );
		rows++;

		prefs   = app.getUserPrefs();
		key		= PrefsUtil.KEY_RECALLFRAMES;
		key2	= "prefsRecallFrames";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggCheckBox  = new PrefCheckBox();
		ggCheckBox.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggCheckBox, key2 );
		tab.add( ggCheckBox );
		lb.setLabelFor( ggCheckBox );
		rows++;
		
        key     = PrefsUtil.KEY_LOOKANDFEEL;
		key2	= "prefsLookAndFeel";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggChoice = new PrefComboBox();
        lafInfos = UIManager.getInstalledLookAndFeels();
        for( int i = 0; i < lafInfos.length; i++ ) {
            ggChoice.addItem( new StringItem( lafInfos[i].getClassName(), lafInfos[i].getName() ));
        }
		ggChoice.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggChoice, key2 );
		tab.add( ggChoice );
		lb.setLabelFor( ggChoice );
		rows++;

       	key		= PrefsUtil.KEY_INTRUDINGSIZE;
		key2	= "prefsIntrudingSize";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggCheckBox  = new PrefCheckBox();
		ggCheckBox.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggCheckBox, key2 );
		tab.add( ggCheckBox );
		lb.setLabelFor( ggCheckBox );
		rows++;

		prefs   = GUIUtil.getUserPrefs();
       	key		= HelpGlassPane.KEY_KEYSTROKE_HELP;
		key2	= "prefsKeyStrokeHelp";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggKeyStroke = new KeyStrokeTextField();
		ggKeyStroke.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggKeyStroke, key2 );
		tab.add( ggKeyStroke );
		lb.setLabelFor( ggKeyStroke );
		rows++;
		
		prefs   = app.getUserPrefs();
		key2	= "prefsGeneral";
		GUIUtil.makeCompactSpringGrid( tab, rows, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		tabWrap = new JPanel( new BorderLayout() );
		tabWrap.add( tab, BorderLayout.NORTH );
        HelpGlassPane.setHelp( tabWrap, key2 );
		ggTabPane.addTab( app.getResourceString( key2 ), null, tabWrap, null );

		// ---------- plug-ins pane ----------

		prefs   = app.getUserPrefs().node( PrefsUtil.NODE_PLUGINS );
		tab		= new JPanel();
		lay		= new SpringLayout();
		tab.setLayout( lay );
		rows	= 0;

		key		= PrefsUtil.KEY_LISPREALTIMELIST;
		key2	= "prefsLispRealtimeList";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggPath	= new PrefPathField( PathField.TYPE_INPUTFILE, app.getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggPath, key2 );
		tab.add( ggPath );
		lb.setLabelFor( ggPath );
		rows++;

		key		= PrefsUtil.KEY_LISPBOUNCELIST;
		key2	= "prefsLispBounceList";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggPath	= new PrefPathField( PathField.TYPE_INPUTFILE, app.getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggPath, key2 );
		tab.add( ggPath );
		lb.setLabelFor( ggPath );
		rows++;

		key		= PrefsUtil.KEY_LISPFILTERLIST;
		key2	= "prefsLispFilterList";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggPath = new PrefPathField( PathField.TYPE_INPUTFILE, app.getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggPath, key2 );
		tab.add( ggPath );
		lb.setLabelFor( ggPath );
		rows++;

		key		= PrefsUtil.KEY_SUPERCOLLIDEROSC;
		key2	= "prefsSuperColliderOSC";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggText  = new PrefTextField( 32 );
		ggText.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggText, key2 );
		tab.add( ggText );
		lb.setLabelFor( ggText );
		rows++;

		key		= PrefsUtil.KEY_SUPERCOLLIDERAPP;
		key2	= "prefsSuperColliderApp";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggPath = new PrefPathField( PathField.TYPE_INPUTFILE, app.getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggPath, key2 );
		tab.add( ggPath );
		lb.setLabelFor( ggPath );
		rows++;

		key		= PrefsUtil.KEY_CSOUNDAPP;
		key2	= "prefsCSoundApp";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggPath = new PrefPathField( PathField.TYPE_INPUTFILE, app.getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggPath, key2 );
		tab.add( ggPath );
		lb.setLabelFor( ggPath );
		rows++;

		key		= PrefsUtil.KEY_AUDIOINPUTS;
		key2	= "prefsAudioInputChannels";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggNumber  = new PrefNumberField( 0, NumberSpace.createIntSpace( 0, 16384 ),
										 app.getResourceString( "labelUnitChannels" ));
		ggNumber.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggNumber, key2 );
		tab.add( ggNumber );
		lb.setLabelFor( ggNumber );
		rows++;

		key		= PrefsUtil.KEY_AUDIOOUTPUTS;
		key2	= "prefsAudioOutputChannels";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggNumber  = new PrefNumberField( 0, NumberSpace.createIntSpace( 0, 16384 ),
										 app.getResourceString( "labelUnitChannels" ));
		ggNumber.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggNumber, key2 );
		tab.add( ggNumber );
		lb.setLabelFor( ggNumber );
		rows++;

		key		= PrefsUtil.KEY_AUDIORATE;
		key2	= "prefsAudioRate";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggNumber  = new PrefNumberField( 0, NumberSpace.createIntSpace( 1, 768000 ),
										 app.getResourceString( "labelUnitHertz" ));
		ggNumber.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggNumber, key2 );
		tab.add( ggNumber );
		lb.setLabelFor( ggNumber );
		rows++;

		key		= PrefsUtil.KEY_RTSENSEBUFSIZE;
		key2	= "prefsRTSenseBufSize";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggNumber  = new PrefNumberField( 0, NumberSpace.createIntSpace( 1, 60000 ),
										 app.getResourceString( "labelUnitMillisec" ));
		ggNumber.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggNumber, key2 );
		tab.add( ggNumber );
		lb.setLabelFor( ggNumber );
		rows++;

		key		= PrefsUtil.KEY_RTMAXSENSERATE;
		key2	= "prefsRTMaxSenseRate";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggNumber  = new PrefNumberField( 0, NumberSpace.createIntSpace( 1, 768000 ),
										 app.getResourceString( "labelUnitHertz" ));
		ggNumber.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggNumber, key2 );
		tab.add( ggNumber );
		lb.setLabelFor( ggNumber );
		rows++;

		key		= PrefsUtil.KEY_OLSENSEBUFSIZE;
		key2	= "prefsOLSenseBufSize";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggNumber  = new PrefNumberField( 0, NumberSpace.createIntSpace( 1, 60000 ),
										 app.getResourceString( "labelUnitMillisec" ));
		ggNumber.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggNumber, key2 );
		tab.add( ggNumber );
		lb.setLabelFor( ggNumber );
		rows++;

		key2	= "prefsPlugIns";
		GUIUtil.makeCompactSpringGrid( tab, rows, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		tabWrap = new JPanel( new BorderLayout() );
		tabWrap.add( tab, BorderLayout.NORTH );
        HelpGlassPane.setHelp( tabWrap, key2 );
		ggTabPane.addTab( app.getResourceString( key2 ), null, tabWrap, null );

       // ---------- session pane ----------

		prefs   = app.getUserPrefs().node( PrefsUtil.NODE_SESSION );
		tab		= new JPanel();
		lay		= new SpringLayout();
		tab.setLayout( lay );
		rows	= 0;

		key		= PrefsUtil.KEY_COMMENT;
		key2	= "prefsComment";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggArea  = new PrefTextArea( 6, 32 );
		ggArea.setPreferences( prefs, key );
        HelpGlassPane.setHelp( ggArea, key2 );
		tab.add( ggArea );
		lb.setLabelFor( ggArea );
		rows++;

		key2	= "prefsSenseRate";
		lb		= new JLabel( app.getResourceString( key2 ), JLabel.TRAILING );
		tab.add( lb );
		ggRate  = new PrefNumberField( 0, NumberSpace.createIntSpace( 1, 768000 ),
										 app.getResourceString( "labelUnitHertz" ));
//		ggNumber.setPreferences( prefs, key );
		tab.add( ggRate );
		lb.setLabelFor( ggRate );
		ggRate.addNumberListener( new NumberListener() {
			public void numberChanged( NumberEvent e )
			{
				if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) {
					ggRate.setNumber( new Integer( doc.timeline.getRate() ));   // undo
					return;
				}
				try {
//					root.transport.stopAndWait();
					int newRate = ggRate.getNumber().intValue();
					if( newRate != doc.timeline.getRate() ) {
						doc.timeline.setRate( ggRate, newRate );
					}
				} finally {
					doc.bird.releaseExclusive( Session.DOOR_TIME );
				}
			}
		});
        HelpGlassPane.setHelp( ggRate, key2 );
		tab.add( ggRate );
		lb.setLabelFor( ggRate );
		rows++;

		key2	= "prefsSession";
		GUIUtil.makeCompactSpringGrid( tab, rows, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		tabWrap = new JPanel( new BorderLayout() );
		tabWrap.add( tab, BorderLayout.NORTH );
        HelpGlassPane.setHelp( tabWrap, key2 );
		ggTabPane.addTab( app.getResourceString( key2 ), null, tabWrap, null );

		// ---------- generic gadgets ----------

        ggButton	= new JButton( app.getResourceString( "buttonClose" ));
        buttonPanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 4, 4 ));
        buttonPanel.add( ggButton );
        ggButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent newEvent )
			{
				setVisible( false );
                dispose();
			}	
		});

		cp.setLayout( new BorderLayout() );
		cp.add( ggTabPane, BorderLayout.CENTER );
        cp.add( buttonPanel, BorderLayout.SOUTH );
		GUIUtil.setDeepFont( cp, fnt );

		// ---------- listeners ----------
        new DynamicAncestorAdapter( this ).addTo( getRootPane() );

		init( root );
		pack();
    }

	private void updateRateGadget()
	{
		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
		try {
			ggRate.setNumber( new Integer( doc.timeline.getRate() ));
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME ); 
		}
	}
	
// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		updateRateGadget();
		doc.timeline.addTimelineListener( this );
    }

    public void stopListening()
    {
		doc.timeline.removeTimelineListener( this );
    }
    
// ---------------- TimelineListener interface ---------------- 

	/**
	 *  Tracks sense rate changes
	 *  which must be reflected by the rate gadget
	 */
	public void timelineChanged( TimelineEvent e )
    {
		if( e.getSource() != ggRate ) {
			updateRateGadget();
		}
    }

	public void timelineSelected( TimelineEvent e ) {}
	public void timelinePositioned( TimelineEvent e ) {}
    public void timelineScrolled( TimelineEvent e ) {}
}