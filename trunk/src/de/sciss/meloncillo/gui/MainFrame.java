/*
 *  MainFrame.java
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
 *		26-May-04   created
 *		31-Jul-04   bugfix: default close action is none now.
 *					commented.
 *		01-Aug-04   EditMenuListener added for clearing console window
 *		21-Aug-04	growbox rigid area
 *      23-Dec-04   overrides setVisible()
 *                  to ensure the main window is displayed on win xp
 *      26-Dec-04   added online help
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.common.AppWindow;
import de.sciss.gui.*;

/**
 *  The main window of the application.
 *  It has several functions:
 *  <ul>
 *  <li>It causes the application to quit if the user
 *  clicks on its close gadget.</li>
 *  <li>It hosts a console log text area to which
 *  the system output and error stream are redirected
 *  and which is used by the progress component.</li>
 *  <li>It hosts a progression component used by
 *  various asynchronous processes such as trajectory
 *  rendering or bouncing.</li>
 *  <li>It's title bar displays the file name of
 *  the current session.</li>
 *  </ul>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 26-Dec-04
 *
 *  @see	javax.swing.AbstractButton#doClick()
 */
public class MainFrame
extends AppWindow
implements ProgressComponent, EditMenuListener
{
	private final ProgressBar	pb;
	private final LogTextArea	lta;
	private final PrintStream	logStream;
	private static final Font	fnt		= GraphicsUtil.smallGUIFont;
	private final Session		doc;

	public MainFrame( final Main root, final Session doc )
	{
		super( REGULAR );
		
		this.doc		= doc;
		
		// ---- own gui ----

		Container   cp  = getContentPane();
		Box			b	= Box.createHorizontalBox();
		pb				= new ProgressBar();
		lta				= new LogTextArea( 6, 40, false, null );
//		HelpGlassPane.setHelp( lta, "MainLogPane" );	// EEE
//		HelpGlassPane.setHelp( pb, "MainProgressBar" );	// EEE
        JScrollPane ggScroll = new JScrollPane( lta, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
													 JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
		logStream		= lta.getLogStream();
		System.setOut( logStream );
		System.setErr( logStream );
		b.add( pb );
        if( AbstractApplication.getApplication().getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
    		b.add( Box.createHorizontalStrut( 16 )); // RigidArea( new Dimension( 16, 16 )));
        }
        cp.setLayout( new BorderLayout() );
		cp.add( ggScroll, BorderLayout.CENTER );
		cp.add( b, BorderLayout.SOUTH );
		GUIUtil.setDeepFont( cp, fnt );
		
		// ---- listeners ----

		this.addListener( new AbstractWindow.Adapter() {
			public void windowClosing( AbstractWindow.Event e ) {
				root.quit();
			}
		});
		// if user cancels confirm dlg, the window has to stay open
		// if user confirms quit, VM will close all windows anyway
		setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
		
		// ---- layout ----

//      classPrefs.putBoolean( PrefsUtil.KEY_VISIBLE, true );
		init();
		updateTitle();
        setVisible( true );
        toFront();
	}
    
//    /**
//     *  The main frame is always visible
//     */
//    public void setVisible( boolean visibility )
//    {
//        if( visibility ) super.setVisible( visibility );
//    }
//
//	protected boolean alwaysPackSize()
//	{
//		return false;
//	}

	/**
	 *  Recreates the main frame's title bar
	 *  after a sessions name changed (clear/load/save as session)
	 */
	public void updateTitle()
	{
		final String	name	= doc.getName();		// 2022 20DF 2299 2605 2666
		final String	title   = AbstractApplication.getApplication().getResourceString( "frameMain" ) + (doc.isDirty() ? " - \u2022" : " - " ) + name;
		setTitle( title );
	}

	/**
	 *  Clears the console log window
	 */
	public void clearLog()
	{
		lta.setText( null );
	}

// ---------------- ProgressComponent interface ---------------- 

	public Component getComponent()
	{
		return pb;
	}
	
	public void resetProgression()
	{
		pb.reset();
	}
	
	public void setProgression( float p )
	{
		pb.setProgression( p );
	}
	
	public void	finishProgression( int type)
	{
		pb.finish( type );
	}
	
	public void setProgressionText( String text )
	{
		logStream.println( text );
//		pb.setText( text );
	}
	
	public void addCancelListener( ActionListener al )
	{
		// EEE NOTHING
	}
	
	public void removeCancelListener( ActionListener al )
	{
		// EEE NOTHING
	}

	public void showMessage( int type, String text )
	{
		// potentially condidates of unicodes
		// for the different messages types are:
		// ERROR_MESSAGE		2620  21AF
		// INFORMATION_MESSAGE  24D8'(i)' 2148'i' 2139'i'
		// PLAIN_MESSAGE
		// QUESTION_MESSAGE		2047
		// WARNING_MESSAGE		261D  2297'X'  203C

		// the print stream is using bytes not unicode,
		// therefore the 'icons' are appended directly
		// to the textarea (so they won't appear in a
		// logfile which is quite unnecessary anyway).
		switch( type ) {
		case JOptionPane.ERROR_MESSAGE:
			lta.append( "\u21AF " );		// Blitz
			break;
		case JOptionPane.INFORMATION_MESSAGE:
			lta.append( "\u263C " );		// Sun
			break;
		case JOptionPane.QUESTION_MESSAGE:
			lta.append( "\u2047 " );		// '??'
			break;
		case JOptionPane.WARNING_MESSAGE:
			lta.append( "\u203C " );		// '!!'
			break;
		default:
			lta.append( "   " );
			break;
		}
		// due to inserting unicode characters we have to
		// advance manually to keep the scrollpane working for us.
// 		lta.setCaretPosition( lta.getText().length() );
		logStream.println( text );
	}
	
	public void displayError( Exception e, String processName )
	{
		GUIUtil.displayError( getWindow(), e, processName );
	}

// ---------------- EditMenuListener interface ---------------- 

	/**
	 *  Clear the console window
	 */
	public void editClear( ActionEvent e )
	{
		clearLog();
	}

	public void editSelectAll( ActionEvent e ) {}
	public void editPaste( ActionEvent e ) {}
	public void editCut( ActionEvent e ) {}
	public void editCopy( ActionEvent e ) {}
}