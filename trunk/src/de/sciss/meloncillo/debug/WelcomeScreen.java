/*
 *  WelcomeScreen.java
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
 *		01-Jun-04   created
 *		29-Jul-04   commented
 *		02-Sep-04	changed the text since this is the final thesis version
 */

package de.sciss.meloncillo.debug;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import net.roydesign.mac.MRJAdapter;

import de.sciss.common.BasicWindowHandler;
import de.sciss.meloncillo.Main;

/**
 *  A new frame is created and
 *  opened that displays a welcome
 *  message to the new user
 *  (whenever preferences are absent
 *  upon application launch).
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class WelcomeScreen
extends JFrame
implements HyperlinkListener
{
	private final JEditorPane		ggContent;
	protected final JButton			ggClose;
	protected final WelcomeScreen	welcome		= this;

	private static final String htmlWelcome1 =
		"<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\" \"http://www.w3.org/TR/REC-html40/loose.dtd\">"+
		"<html><head><style type=\"text/css\"><!--\n"+
		"body { background:black; color:white; padding:20px; }\n"+
		"em { font-weight:bold; font-style:normal; }\n"+
		"a { color:white;font-weight:bold; }\n"+
		"p { font-family:\"Lucida Grande\" Helvetica sans-serif;font-size:14pt;padding:4pt 4pt 4pt 4pt;margin:0; }\n"+
		"--></style></head><body>"+
		"<p>Welcome to the ";
	private static final String htmlWelcome2 =
		" beta version. This screen pops up because I couldn't find any known "+
		"preferences version, which suggests that you start this application for the first time. Please take a few "+
		"minutes to read the <EM>README.rtf</EM> file and the introductory part of the manual. As a first step before doing "+
		"anything else, you should adjust your preferences. The preferences pane will show up when you "+
		"close this window.</p>"+
		"<p>This software is free software; you can redistribute it and/or "+
		"modify it under the terms of the GNU General Public License "+
		"as published by the Free Software Foundation; either "+
		"version 2, june 1991 of the License, or (at your option) any later version.</p>"+
		"<p>This software is distributed in the hope that it will be useful, "+
		"but <strong>WITHOUT ANY WARRANTY</strong>; without even the implied warranty of "+
		"<strong>MERCHANTABILITY</strong> or <strong>FITNESS FOR A PARTICULAR PURPOSE</strong>. See the GNU "+
		"General Public License for more details.</p>"+
		"<p>\"La musique du futur? S&ucirc;rement bas&eacute;e sur le son et au-del&agrave; des notes\" "+
		"(Edgard Var&egrave;se)</p>"+
		"</body></html>";

	/**
	 *  Create and open welcome screen. This
	 *  is in the debug package because it's likely
	 *  to change in a future version.
	 *
	 *  @param  root	application root
	 */
	public WelcomeScreen( final Main root )
	{
		super( "Welcome to " + root.getName() );
	
		final Container cp = getContentPane();
		ggContent = new JEditorPane( "text/html", htmlWelcome1 + root.getName() + htmlWelcome2 );
		ggContent.setEditable( false );
		ggContent.addHyperlinkListener( this );
		cp.add( ggContent, BorderLayout.CENTER );
		final Action closeAction = new AbstractAction( "- Close -" ) {
			public void actionPerformed( ActionEvent e )
			{
				welcome.setVisible( false );
				welcome.dispose();
				root.getMenuFactory().showPreferences();
			}
		};
		ggClose = new JButton( closeAction );
		cp.add( ggClose, BorderLayout.SOUTH );
		addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e )
			{
				ggClose.doClick( 150 );
			}
		});
		setSize( 640, 480 );
		setLocationRelativeTo( null );
		
		setVisible( true );
		toFront();
	}

	// --------- HyperlinkListener interface ---------

	public void hyperlinkUpdate( HyperlinkEvent e )
	{
		if( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED ) {
			try {
				MRJAdapter.openURL( e.getURL().toString() );
			}
			catch( Exception e1 ) {
				BasicWindowHandler.showErrorDialog( this, e1, getTitle() );
			}
		}
	}
}