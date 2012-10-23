/*
 *  MainFrame.java
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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintStream;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.LogTextArea;
import de.sciss.gui.MenuAction;
import de.sciss.gui.ProgressBar;
import de.sciss.gui.ProgressComponent;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.session.DocumentFrame;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.util.PrefsUtil;


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
 *  @version	0.75, 19-Jun-08
 *
 *  @see	javax.swing.AbstractButton#doClick()
 */
public class MainFrame
extends DocumentFrame
implements ProgressComponent
{
	private final ProgressBar	pb;
	private final LogTextArea	lta;
	private final PrintStream	logStream;
	private final Font			fntMonoSpaced;

	public MainFrame( final Main root, final Session doc )
	{
		super( doc );
		
		// ---- own gui ----

		pb					= new ProgressBar();
		lta					= new LogTextArea( 6, 72, false, null );

		final Container		cp  	= getContentPane();
		final Box			b		= Box.createHorizontalBox();
		final String[]		fntNames;
//		HelpGlassPane.setHelp( lta, "MainLogPane" );	// EEE
//		HelpGlassPane.setHelp( pb, "MainProgressBar" );	// EEE
        final JScrollPane	ggScroll= new JScrollPane( lta, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                         	                            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
        final Application	app		= AbstractApplication.getApplication();
        
		logStream		= lta.getLogStream();
		System.setOut( logStream );
		System.setErr( logStream );
		b.add( pb );
        if( app.getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
    		b.add( Box.createHorizontalStrut( 16 )); // RigidArea( new Dimension( 16, 16 )));
        }
		cp.add( ggScroll, BorderLayout.CENTER );
		cp.add( b, BorderLayout.SOUTH );
		AbstractWindowHandler.setDeepFont( cp );

		fntNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		if( contains( fntNames, "Monaco" )) {							// Mac OS
			fntMonoSpaced = new Font( "Monaco", Font.PLAIN, 9 );		// looks bigger than it is
		} else if( contains( fntNames, "Lucida Sans Unicode" )) {		// Windows XP
			fntMonoSpaced = new Font( "Lucida Sans Unicode", Font.PLAIN, 9 );
		} else {
			fntMonoSpaced = new Font( "Monospaced", Font.PLAIN, 10 );
		}
		lta.setFont( fntMonoSpaced );

		// ---- listeners ----

		this.addListener( new AbstractWindow.Adapter() {
			public void windowClosing( AbstractWindow.Event e ) {
				root.quit();
			}
		});
		
		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
		
		init();
		updateTitle();
		app.addComponent( Main.COMP_MAIN, this );
		setVisible( true );
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
		final String		name	= doc.getName();		// 2022 20DF 2299 2605 2666
		final Application	app		= AbstractApplication.getApplication();
		final String		title   = app.getResourceString( "frameMain" ) + (doc.isDirty() ? " - \u2022" : " - " ) +
			name == null ? app.getResourceString( "frameUntitled" ) : name;
		setTitle( title );
		setDirty( doc.isDirty() );
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

	private static boolean contains( String[] array, String name )
	{
		for( int i = 0; i < array.length; i++ ) {
			if( array[ i ].equals( name )) return true;
		}
		return false;
	}

// ---------------- DocumentFrame abstract methods ----------------
	
	protected Action getCutAction() { return null; }
	protected Action getCopyAction() { return null; }
	protected Action getPasteAction() { return null; }
	protected Action getDeleteAction() { return new ActionDelete(); }
	protected Action getSelectAllAction() { return null; }

	private class ActionDelete
	extends MenuAction
	{
		protected ActionDelete() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			clearLog();
		}
	}
}