/*
 *  HelpFrame.java
 *  FScape
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
 *		20-May-05   created from de.sciss.meloncillo.gui.HelpFrame,
 *					added scrollbar info in history, added static load method
 */

package de.sciss.gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Document;

import net.roydesign.mac.MRJAdapter;

import de.sciss.app.AbstractApplication;

/**
 *  A frame displaying online HTML help
 *  pages.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.10, 20-May-05
 *
 *  @see	HelpGlassPane
 */
public class HelpFrame
extends JFrame // BasicFrame
implements HyperlinkListener, PropertyChangeListener
{
	/**
	 *	Identifier for Application.getComponent
	 */
	public static final Object COMP_HELP	= HelpFrame.class;

    private final JEditorPane   htmlPane;
    private final JButton       ggOpenInBrowser, ggBack;
    private final ArrayList     history = new ArrayList();	// elements = historyEntry
    private int	  historyIndex  = -1;
	private final JScrollBar	ggVScroll;
	private final String		plainTitle;
            
	/**
	 *  Creates a new help frame. Use
     *  <code>loadHelpFile</code> to change the content.
	 *
     *  @see    #loadHelpFile( String )
	 */
    public HelpFrame()
    {
        super();
        
		Container							cp		= getContentPane();
        JPanel								buttonPanel;
		JButton								ggButton;
//		Font								fnt		= GraphicsUtil.smallGUIFont;
        JScrollPane							ggScroll;
		final HelpFrame						hf		= this;
		
		plainTitle	= GUIUtil.getResourceString( "frameHelp" );
		
		setTitle( plainTitle );
		
        // ---------- HTML schoko ----------
        htmlPane    = new JEditorPane();
        htmlPane.setEditable( false );
        htmlPane.addHyperlinkListener( this );
        htmlPane.setPreferredSize( new Dimension( 320, 320 ));
        htmlPane.setAutoscrolls( true );
//      htmlPane.setContentType( "text/html" );
        htmlPane.addPropertyChangeListener( this );
        HelpGlassPane.setHelp( htmlPane, "HelpHTMLPane" );
        ggScroll    = new JScrollPane( htmlPane );
		ggVScroll	= ggScroll.getVerticalScrollBar();
        
        // ---------- generic gadgets ----------

        buttonPanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 4, 4 ));
        ggBack      = new JButton( GUIUtil.getResourceString( "buttonBack" ));
        ggBack.setEnabled( false );
        buttonPanel.add( ggBack );
        ggBack.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent newEvent )
			{
				if( historyIndex > 0 ) {
					try {
						try {
							historyIndex--;
							loadURL();
						}
						catch( IOException e2 ) {   // try to load previous file
							historyIndex++;
							loadURL();
							throw e2;
						}
					}
					catch( IOException e3 ) {
						GUIUtil.displayError( hf, e3, null );
					}
				}
            }
		});
        ggOpenInBrowser	= new JButton( GUIUtil.getResourceString( "helpOpenBrowser" ));
        ggOpenInBrowser.setEnabled( false );
        HelpGlassPane.setHelp( ggOpenInBrowser, "HelpOpenInBrowser" );
        buttonPanel.add( ggOpenInBrowser );
        ggOpenInBrowser.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent newEvent )
			{
                openInBrowser();
            }	
		});
        ggButton	= new JButton( GUIUtil.getResourceString( "buttonClose" ));
        buttonPanel.add( ggButton );
        ggButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent newEvent )
			{
				setVisible( false );
                dispose();
			}	
		});

		cp.setLayout( new BorderLayout() );
        cp.add( ggScroll, BorderLayout.CENTER );
        cp.add( buttonPanel, BorderLayout.SOUTH );
//		GUIUtil.setDeepFont( cp, fnt );

//		init( root );
		AbstractWindowHandler.setDeepFont( getContentPane(), null );

		ggScroll.setPreferredSize( new Dimension( 512, 512 ));	// XXX
		pack();
    }
    
    /**
     *  Views a help document. If no help viewer
	 *	has been opened, a new one will be created
	 *	and installed with the current application
	 *	(component ID = <code>COMP_HELP</code>).
     *
     *  @param  fileName    relative file name of
     *                      the help text, omitting
     *                      the directory and the ".html" suffix
     */
    public static void openViewerAndLoadHelpFile( String fileName )
	{
		HelpFrame helpFrame = (HelpFrame) AbstractApplication.getApplication().getComponent(
			HelpFrame.COMP_HELP );
	
		if( helpFrame == null ) {
			helpFrame = new HelpFrame();
			AbstractApplication.getApplication().addComponent( HelpFrame.COMP_HELP, helpFrame );
		}
		helpFrame.loadHelpFile( fileName );
		helpFrame.setVisible( true );
		helpFrame.show();
	}

    /**
     *  Replaces the window's contents
     *  by a new HTML file.
     *
     *  @param  fileName    relative file name of
     *                      the help text, omitting
     *                      the directory and the ".html" suffix
     */
    public void loadHelpFile( String fileName )
    {
        try {
            URL url = new File( "help", fileName + ".html" ).toURL();
            addAndLoadURL( url );
        }
        catch( IOException e1 ) {
            GUIUtil.displayError( this, e1, null );
        }
    }

    private void addAndLoadURL( URL url )
    throws IOException
    {
		if( historyIndex >= 0 ) {
			((HistoryEntry) history.get( historyIndex )).setVerticalScroll( ggVScroll.getValue() );
		}
        history.add( ++historyIndex, new HistoryEntry( url ));
        try {
            loadURL();
            for( int i = history.size() - 1; i > historyIndex; i-- ) {
                history.remove( i );
            }
        }
        catch( IOException e2 ) {   // try to load previous file
            history.remove( historyIndex-- );
            if( historyIndex >= 0 ) loadURL();
            throw e2;
        }
    }
    
    private void loadURL()
    throws IOException
    {
		try {
			HistoryEntry he = (HistoryEntry) history.get( historyIndex );
			htmlPane.setPage( he.url );
		}
		finally {
			updateButtons();
		}
    }

    private void updateButtons()
    {
        ggOpenInBrowser.setEnabled( historyIndex >= 0 );
        ggBack.setEnabled( historyIndex > 0 );
    }

    private void openInBrowser()
    {
        if( historyIndex >= 0 ) {
            try {
                MRJAdapter.openURL( ((HistoryEntry) history.get( historyIndex )).url.toString() );
            }
            catch( IOException e1 ) {
                GUIUtil.displayError( this, e1, null );
            }
        }
    }

    // doc.getProperty always returns null on windows??
    private void pageIsLoaded()
    {
		javax.swing.text.Document		doc = htmlPane.getDocument();
		HistoryEntry	he	= (HistoryEntry) history.get( historyIndex );

        if( doc != null ) {
            Object title = doc.getProperty( Document.TitleProperty );
            setTitle( title == null ? plainTitle : plainTitle + (" : " + title) );
			ggVScroll.setValue( he.getVerticalScroll() );
        }
    }
    
    // --------------- HyperlinkListener interface ---------------
    public void hyperlinkUpdate( HyperlinkEvent e )
    {
        if( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            try {
                addAndLoadURL( e.getURL() );
            }
            catch( IOException e1 ) {
                GUIUtil.displayError( this, e1, null );
            }
        }
    }

    // --------------- PropertyChangeListener interface ---------------
    public void propertyChange( PropertyChangeEvent e )
    {
        if( e.getPropertyName().equals( "page" )) {
            pageIsLoaded();
        }
    }
	
// --------------- internal classes ---------------

	private static class HistoryEntry
	{
		private final URL	url;
		private int	verticalScroll;
	
		private HistoryEntry( URL url, int verticalScroll )
		{
			this.url			= url;
			this.verticalScroll	= verticalScroll;
		}
		
		private HistoryEntry( URL url )
		{
			this( url, 0 );
		}
		
		private void setVerticalScroll( int verticalScroll )
		{
			this.verticalScroll	= verticalScroll;
		}

		private int getVerticalScroll()
		{
			return verticalScroll;
		}
	}
}
