/*
 *  PrefsFrame.java
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
 *  Change log:
 *		06-Jun-04   switched to PreferenceSync'ed gadgets
 *		31-Jul-04   commented
 *      24-Dec-04   new fields for look-and-feel.
 *		30-Dec-04	added online help
 */

package de.sciss.meloncillo.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import de.sciss.util.Flag;
import de.sciss.util.NumberSpace;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.DynamicListening;
import de.sciss.app.PreferenceEntrySync;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicWindowHandler;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.CoverGrowBox;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpButton;
import de.sciss.gui.KeyStrokeTextField;
import de.sciss.gui.NumberEvent;
import de.sciss.gui.NumberListener;
import de.sciss.gui.PathField;
import de.sciss.gui.PrefCheckBox;
import de.sciss.gui.PrefComboBox;
import de.sciss.gui.PrefNumberField;
import de.sciss.gui.PrefPathField;
import de.sciss.gui.PrefTextField;
import de.sciss.gui.SpringPanel;
import de.sciss.gui.StringItem;
import de.sciss.io.IOUtil;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.timeline.TimelineEvent;
import de.sciss.meloncillo.timeline.TimelineListener;
import de.sciss.meloncillo.util.PrefsUtil;

/**
 *  This is the frame that
 *  displays the user adjustable
 *  application and session preferences
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 19-Jun-08
 */
public class PrefsFrame
extends AppWindow
implements SwingConstants, TimelineListener, DynamicListening
{
	private final PrefNumberField ggRate;
	
	private final Session   doc;
	
	/**
	 *  Creates a new preferences frame
	 *
	 *  @param  root	application root
	 *  @param  doc		session document
	 */
    public PrefsFrame( final Session doc ) // final Main root, final Session doc )
    {
    	super( SUPPORT );

		this.doc	= doc;

		final Container					cp		= getContentPane();
		final Application				app		= AbstractApplication.getApplication();
		final Flag						haveWarned			= new Flag( false );
		final String					txtWarnLookAndFeel	= getResourceString( "warnLookAndFeelUpdate" );

		PrefTextField					ggText;
		KeyStrokeTextField				ggKeyStroke;
		PrefTextArea					ggArea;
		PrefNumberField					ggNumber;
		PrefPathField					ggPath;
		PrefCheckBox					ggCheckBox;
        PrefComboBox					ggChoice;
		JTabbedPane						ggTabPane;
		JLabel							lb;
        UIManager.LookAndFeelInfo[]		lafInfos;
		SpringPanel						tab;

		Preferences						prefs;
		String							key, key2, title;
		int								row;

		ggTabPane			= new JTabbedPane();

    	setTitle( getResourceString( "framePrefs" ));

    	// ---------- global pane ----------

		tab		= createTab();

		row	= 0;
		prefs   = IOUtil.getUserPrefs();
		key		= IOUtil.KEY_TEMPDIR;
		key2	= "prefsTmpDir";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggPath = new PrefPathField( PathField.TYPE_FOLDER, getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggPath, key2 );	// EEE
		tab.gridAdd( ggPath, 1, row );

		row++;
		prefs   = app.getUserPrefs();
		key		= PrefsUtil.KEY_RECALLFRAMES;
		key2	= "prefsRecallFrames";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggCheckBox  = new PrefCheckBox();
		ggCheckBox.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggCheckBox, key2 );	// EEE
		tab.gridAdd( ggCheckBox, 1, row );
		
		row++;
		prefs   = app.getUserPrefs();
        key     = PrefsUtil.KEY_LOOKANDFEEL;
		key2	= "prefsLookAndFeel";
		title	= getResourceString( key2 );
		lb		= new JLabel( title, TRAILING );
		tab.gridAdd( lb, 0, row );
		ggChoice = new PrefComboBox();
		lafInfos = UIManager.getInstalledLookAndFeels();
        for( int i = 0; i < lafInfos.length; i++ ) {
            ggChoice.addItem( new StringItem( lafInfos[i].getClassName(), lafInfos[i].getName() ));
        }
		ggChoice.setPreferences( prefs, key );
		ggChoice.addActionListener( new WarnPrefsChange( ggChoice, ggChoice, haveWarned, txtWarnLookAndFeel, title ));
		
		tab.gridAdd( ggChoice, 1, row, -1, 1 );

		row++;
       	key		= BasicWindowHandler.KEY_LAFDECORATION;
		key2	= "prefsLAFDecoration";
		title	= getResourceString( key2 );
		ggCheckBox  = new PrefCheckBox( title );
		ggCheckBox.setPreferences( prefs, key );
		tab.gridAdd( ggCheckBox, 1, row, -1, 1 );
		ggCheckBox.addActionListener( new WarnPrefsChange( ggCheckBox, ggCheckBox, haveWarned, txtWarnLookAndFeel, title ));

		row++;
       	key		= BasicWindowHandler.KEY_INTERNALFRAMES;
		key2	= "prefsInternalFrames";
		title	= getResourceString( key2 );
		ggCheckBox  = new PrefCheckBox( title );
		ggCheckBox.setPreferences( prefs, key );
		tab.gridAdd( ggCheckBox, 1, row, -1, 1 );
		ggCheckBox.addActionListener( new WarnPrefsChange( ggCheckBox, ggCheckBox, haveWarned, txtWarnLookAndFeel, title ));

		row++;
       	key		= CoverGrowBox.KEY_INTRUDINGSIZE;
		key2	= "prefsIntrudingSize";
		ggCheckBox  = new PrefCheckBox( getResourceString( key2 ));
		ggCheckBox.setPreferences( prefs, key );
		tab.gridAdd( ggCheckBox, 1, row, -1, 1 );

		row++;
       	key		= BasicWindowHandler.KEY_FLOATINGPALETTES;
		key2	= "prefsFloatingPalettes";
		ggCheckBox  = new PrefCheckBox( getResourceString( key2 ));
		ggCheckBox.setPreferences( prefs, key );
		tab.gridAdd( ggCheckBox, 1, row, -1, 1 );
		ggCheckBox.addActionListener( new WarnPrefsChange( ggCheckBox, ggCheckBox, haveWarned, txtWarnLookAndFeel, title ));

		row++;
		prefs   = GUIUtil.getUserPrefs();
//     	key		= HelpGlassPane.KEY_KEYSTROKE_HELP;	// EEE
		key2	= "prefsKeyStrokeHelp";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggKeyStroke = new KeyStrokeTextField();
		ggKeyStroke.setPreferences( prefs, key );
//      HelpGlassPane.setHelp( ggKeyStroke, key2 );	// EEE
		tab.gridAdd( ggKeyStroke, 1, row );
		
		addTab( ggTabPane, tab, "prefsGeneral" );

		// ---------- plug-ins pane ----------

		prefs   = app.getUserPrefs().node( PrefsUtil.NODE_PLUGINS );
		tab		= createTab();

		row	= 0;
		key		= PrefsUtil.KEY_LISPREALTIMELIST;
		key2	= "prefsLispRealtimeList";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggPath	= new PrefPathField( PathField.TYPE_INPUTFILE, getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggPath, key2 );	// EEE
		tab.gridAdd( ggPath, 1, row );

		row++;
		key		= PrefsUtil.KEY_LISPBOUNCELIST;
		key2	= "prefsLispBounceList";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggPath	= new PrefPathField( PathField.TYPE_INPUTFILE, getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggPath, key2 );	// EEE
		tab.gridAdd( ggPath, 1, row );

		row++;
		key		= PrefsUtil.KEY_LISPFILTERLIST;
		key2	= "prefsLispFilterList";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggPath = new PrefPathField( PathField.TYPE_INPUTFILE, getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggPath, key2 );	// EEE
		tab.gridAdd( ggPath, 1, row );

		row++;
		key		= PrefsUtil.KEY_SUPERCOLLIDEROSC;
		key2	= "prefsSuperColliderOSC";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggText  = new PrefTextField( 32 );
		ggText.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggText, key2 );	// EEE
		tab.gridAdd( ggText, 1, row );

		row++;
		key		= PrefsUtil.KEY_SUPERCOLLIDERAPP;
		key2	= "prefsSuperColliderApp";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggPath = new PrefPathField( PathField.TYPE_INPUTFILE, getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggPath, key2 );	// EEE
		tab.gridAdd( ggPath, 1, row );

		row++;
		key		= PrefsUtil.KEY_CSOUNDAPP;
		key2	= "prefsCSoundApp";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggPath = new PrefPathField( PathField.TYPE_INPUTFILE, getResourceString( key2 ));
		ggPath.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggPath, key2 );	// EEE
		tab.gridAdd( ggPath, 1, row );

		row++;
		key		= PrefsUtil.KEY_AUDIOINPUTS;
		key2	= "prefsAudioInputChannels";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggNumber  = new PrefNumberField();
		ggNumber.setSpace( NumberSpace.createIntSpace( 0, 16384 ));
//		ggNumber.setUnit( getResourceString( "labelUnitChannels" ));	// EEE
		ggNumber.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggNumber, key2 );	// EEE
		tab.gridAdd( ggNumber, 1, row );

		row++;
		key		= PrefsUtil.KEY_AUDIOOUTPUTS;
		key2	= "prefsAudioOutputChannels";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggNumber  = new PrefNumberField();
		ggNumber.setSpace( NumberSpace.createIntSpace( 0, 16384 ));
//		ggNumber.setUnit( getResourceString( "labelUnitChannels" ));	// EEE
		ggNumber.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggNumber, key2 );	// EEE
		tab.gridAdd( ggNumber, 1, row );

		row++;
		key		= PrefsUtil.KEY_AUDIORATE;
		key2	= "prefsAudioRate";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggNumber  = new PrefNumberField();
		ggNumber.setSpace( NumberSpace.createIntSpace( 1, 768000 ));
//		ggNumber.setUnit( getResourceString( "labelUnitHertz" ));	// EEE
		ggNumber.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggNumber, key2 );	// EEE
		tab.gridAdd( ggNumber, 1, row );

		row++;
		key		= PrefsUtil.KEY_RTSENSEBUFSIZE;
		key2	= "prefsRTSenseBufSize";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggNumber  = new PrefNumberField();
		ggNumber.setSpace( NumberSpace.createIntSpace( 1, 60000 ));
//		ggNumber.setUnit( getResourceString( "labelUnitMillisec" ));	// EEE
		ggNumber.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggNumber, key2 );	// EEE
		tab.gridAdd( ggNumber, 1, row );

		row++;
		key		= PrefsUtil.KEY_RTMAXSENSERATE;
		key2	= "prefsRTMaxSenseRate";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggNumber  = new PrefNumberField();
		ggNumber.setSpace( NumberSpace.createIntSpace( 1, 768000 ));
//		ggNumber.setUnit( getResourceString( "labelUnitHertz" ));	// EEE
		ggNumber.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggNumber, key2 );	// EEE
		tab.gridAdd( ggNumber, 1, row );

		row++;
		key		= PrefsUtil.KEY_OLSENSEBUFSIZE;
		key2	= "prefsOLSenseBufSize";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggNumber  = new PrefNumberField();
		ggNumber.setSpace( NumberSpace.createIntSpace( 1, 60000 ));
//		ggNumber.setUnit( getResourceString( "labelUnitMillisec" ));	// EEE
		ggNumber.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggNumber, key2 );	// EEE
		tab.gridAdd( ggNumber, 1, row );

		addTab( ggTabPane, tab, "prefsPlugIns" );

		// ---------- session pane ----------

		prefs   = app.getUserPrefs().node( PrefsUtil.NODE_SESSION );
		tab		= createTab();
		row	= 0;

		key		= PrefsUtil.KEY_COMMENT;
		key2	= "prefsComment";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggArea  = new PrefTextArea( 6, 32 );
		ggArea.setPreferences( prefs, key );
//        HelpGlassPane.setHelp( ggArea, key2 );	// EEE
		tab.gridAdd( ggArea, 1, row );

		row++;
		key2	= "prefsSenseRate";
		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
		tab.gridAdd( lb, 0, row );
		ggRate  = new PrefNumberField();
		ggRate.setSpace( NumberSpace.createIntSpace( 1, 768000 ));
//		ggRate.setUnit( getResourceString( "labelUnitHertz" ));	// EEE
//		ggNumber.setPreferences( prefs, key );
		tab.gridAdd( ggRate, 1, row );
		ggRate.addListener( new NumberListener() {
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
//        HelpGlassPane.setHelp( ggRate, key2 );	// EEE
		tab.gridAdd( ggRate, 1, row );

		addTab( ggTabPane, tab, "prefsSession" );

		// ---------- generic gadgets ----------

		cp.add( ggTabPane, BorderLayout.CENTER );
		AbstractWindowHandler.setDeepFont( cp );

		// ---------- listeners ----------
		addDynamicListening( this );
		
		addListener( new AbstractWindow.Adapter() {
			public void windowClosing( AbstractWindow.Event e )
			{
				setVisible( false );
				dispose();
			}
		});

		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
		init();
		app.addComponent( Main.COMP_PREFS, this );
  	}

	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_PREFS );
		super.dispose();
	}

    private SpringPanel createTab()
    {
    	return new SpringPanel( 2, 1, 4, 2 );
    }
    
    private void addTab( JTabbedPane ggTabPane, SpringPanel tab, String key )
    {
    	final JPanel tabWrap, p;
    	
		tab.makeCompactGrid();
		tabWrap = new JPanel( new BorderLayout() );
		tabWrap.add( tab, BorderLayout.NORTH );
		p		= new JPanel( new FlowLayout( FlowLayout.RIGHT ));
		p.add( new HelpButton( key ));
		tabWrap.add( p, BorderLayout.SOUTH );
		ggTabPane.addTab( getResourceString( key ), null, tabWrap, null );
    }
	
    protected boolean autoUpdatePrefs()
	{
		return true;
	}

	protected static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
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

 // ---------------- internal classes ---------------- 

	private static class WarnPrefsChange
	implements ActionListener
	{
		private final PreferenceEntrySync	pes;
		private final Component				c;
		private final Flag					haveWarned;
		private final String				text;
		private final String				title;
		private final String				initialValue;
	
		protected WarnPrefsChange( PreferenceEntrySync pes, Component c, Flag haveWarned, String text, String title )
		{
			this.pes		= pes;
			this.c			= c;
			this.haveWarned	= haveWarned;
			this.text		= text;
			this.title		= title;
			
			initialValue	= pes.getPreferenceNode().get( pes.getPreferenceKey(), null );
		}

		public void actionPerformed( ActionEvent e )
		{
			final String newValue = pes.getPreferenceNode().get( pes.getPreferenceKey(), initialValue );
		
			if( !newValue.equals( initialValue ) && !haveWarned.isSet() ) {
				final JOptionPane op = new JOptionPane( text, JOptionPane.INFORMATION_MESSAGE );
//				JOptionPane.showMessageDialog( c, text, title, JOptionPane.INFORMATION_MESSAGE );
				BasicWindowHandler.showDialog( op, c, title );
				haveWarned.set( true );
			}
		}
	}
}