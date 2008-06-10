/*
 *  JathaDiddler.java
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
 *		16-Jul-04   created
 *		29-Jul-04   commented
 *      26-Dec-04   extends BasicPalette, has online help
 *		26-Apr-05	added temp-file-make primitive
 *		31-May-05	moved to lisp package, new uses multiline text area
 */

package de.sciss.meloncillo.lisp;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.text.*;

import org.jatha.dynatype.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.AbstractApplication;
import de.sciss.gui.*;

/**
 *  A simple frame that contains
 *  a string gadget. Typing text
 *  into this gadget and hitting
 *  return will use the
 *  <a href="http://jatha.sourceforge.net/">
 *  Jatha common lisp interpreter</a> to
 *  evaluate the expression and
 *  print it's results into the
 *  console frame.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @see		de.sciss.meloncillo.lisp.AdvancedJatha
 */
public class JathaDiddler
extends BasicPalette
{
	private final AdvancedJatha jatha;
	private final JTextArea	prompt;
	private final JCheckBox ggPrintInput, ggPrintResult;

	/**
	 *  Creates a new instance.
	 *
	 *  @param  root	application root
	 *  @param  doc		session object
	 */
	public JathaDiddler( Main root )
	{
		super( AbstractApplication.getApplication().getResourceString( "frameJatha" ));
	
		jatha			= new AdvancedJatha();
		jatha.addPrimitive( new TempFileMakePrimitive( jatha ) {
			public void consumeFile( File f ) throws IOException
			{
				// nothing to do
			}
		});
		
		final Container					cp		= getContentPane();
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
		final JScrollPane				scroll;
		final Box						box		= Box.createHorizontalBox();

		prompt			= new JTextArea( app.getResourceString( "jathaDiddlerIntro" ), 4, 0 ); //, 16, 40
// key location ignored ;-(
//		prompt.getInputMap().put( KeyStroke.getKeyStrokeForEvent( new KeyEvent(
//			this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER,
//			KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_NUMPAD )), "eval" );
		prompt.getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK ), "eval" );
		prompt.getActionMap().put( "eval", new actionEvalClass() );
//		prompt.setFont( app.getWindowHandler().getDefaultFont() );
		prompt.setTabSize( 4 );
		prompt.setLineWrap( false );
		prompt.setCaretPosition( prompt.getText().length() );
		scroll			= new JScrollPane( prompt );
		
		ggPrintInput	= new JCheckBox( app.getResourceString( "jathaDiddlerInput" ));
		ggPrintResult	= new JCheckBox( app.getResourceString( "jathaDiddlerResult" ));
		ggPrintResult.setSelected( true );
		box.add( ggPrintInput );
		box.add( ggPrintResult );
		if( app.getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
			box.add( Box.createVerticalStrut( 16 ));
        }

		cp.setLayout( new BorderLayout() );
		cp.add( scroll, BorderLayout.CENTER );
		cp.add( box, BorderLayout.SOUTH );

		GUIUtil.setDeepFont( cp, GraphicsUtil.smallGUIFont );

        HelpGlassPane.setHelp( prompt, "JathaPrompt" );
	
        init( root );
//		pack();
		show();
	}
	
	/**
	 *  @param  root	application root
	 *  @param  doc		session object
	 *
	 *  @return			an <code>Action</code>
	 *					suitable for attaching
	 *					to a <code>JMenuItem</code>. The
	 *					action will create and open a new
	 *					instance of <code>JathaDiddler</code>.
	 */
	public static Action getMenuAction( final Main root )
	{
		return new AbstractAction( AbstractApplication.getApplication().getResourceString( "frameJatha" ))
		{
			public void actionPerformed( ActionEvent e )
			{
				root.addComponent( new Integer( -1 ), new JathaDiddler( root ));
			}
		};
	}
	
	private class actionEvalClass
	extends AbstractAction
	{
		public void actionPerformed( ActionEvent e )
		{
//System.err.println( "we're here!" );
			try {
				String inText;
				
				inText	= prompt.getSelectedText();
				if( inText == null ) {	// nothing selected -> execute current line
					try {
						int line		= prompt.getLineOfOffset( prompt.getCaretPosition() );
						int	startOff	= prompt.getLineStartOffset( line );
						int endOff		= prompt.getLineEndOffset( line );
						inText			= prompt.getText( startOff, endOff - startOff );
					}
					catch( BadLocationException e1 ) {
						return;
					}
				}
			
				final LispValue input  = jatha.parse( inText );
				if( ggPrintInput.isSelected() )  System.out.println( "IN: " + input );
				final LispValue result = jatha.eval( input );
				if( ggPrintResult.isSelected() ) System.out.println( "OUT: " + result);
			} catch( Exception e1 ) {
				System.err.println( "LISP Exception: " + e1 );
			}
		}
	}
}