/*
 *  PathField.java
 *  de.sciss.gui package
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
 *		20-May-05	created from de.sciss.meloncillo.gui.PathField
 */

package de.sciss.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import de.sciss.app.AbstractApplication;
import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;

/**
 *  This class is an updated (slim) version
 *  of FScape's <code>PathField</code> and provides a GUI object
 *  that displays a file's or folder's path,
 *  optionally with format information, and allows
 *  the user to browse the harddisk to change
 *  to a different file.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.10, 20-May-05
 */
public class PathField
extends JPanel
implements ActionListener, PathListener, EventManager.Processor
{
// -------- public Variablen --------
	/**
	 *	type flag : the bitmask for all path types
	 */
	public static final int TYPE_BASICMASK	= 0x0F;
	/**
	 *	type flag : file is used for input / reading
	 */
	public static final int TYPE_INPUTFILE	= 0x00;
	/**
	 *	type flag : request an extra gadget to view file information
	 */
	public static final int TYPE_FORMATFIELD= 0x10;
	/**
	 *	type flag : file is used for output / writing
	 */
	public static final int TYPE_OUTPUTFILE	= 0x01;
	/**
	 *	type flag : path to be chosen is a folder
	 */
	public static final int TYPE_FOLDER		= 0x02;

// -------- private Variablen --------

	private static PathList		userPaths			= null;
	private static final int	USERPATHS_NUM		= 9;		// userPaths capacity
	private static final int	ABBR_LENGTH			= 12;		// constants for abbreviate
	private static final int	DEFAULT_COLUMN_NUM  = 32;		// constants for IOTextField

	private final IOTextField	ggPath;
	private final PathButton	ggChoose;
	private ColouredTextField	ggFormat	= null;
	
	private static final Color  COLOR_ERR   = new Color( 0xFF, 0x00, 0x00, 0x2F );
	private static final Color  COLOR_EXISTS= new Color( 0x00, 0x00, 0xFF, 0x2F );
	private static final Color  COLOR_PROPSET=new Color( 0x00, 0xFF, 0x00, 0x2F );

	private final int		type;
	private final String	dlgTxt;
	private String			scheme;
	private String			protoScheme;
	private PathField		superPaths[];

	private final java.util.List	collChildren	= new ArrayList();
	private final EventManager		elm				= new EventManager( this );

// -------- public Methoden --------

	/**
	 *  Create a new <code>PathField</code>
	 *
	 *  @param  type	one of the types covered by TYPE_BASICMASK
	 *					bitwise-OR optional displays like TYPE_FORMATFIELD
	 *  @param  dlgTxt  the text string to display in the filechooser or <code>null</code>
	 */
	public PathField( int type, String dlgTxt )
	{
		super();
		this.type		= type;
		this.dlgTxt		= dlgTxt;
		
		// first instance initialized userPath list
		if( userPaths == null ) {
			userPaths	= new PathList( USERPATHS_NUM, GUIUtil.getUserPrefs(), PathList.KEY_USERPATHS );
			if( userPaths.getPathCount() < USERPATHS_NUM ) {	// prefs not initialized
				File home	= new File( System.getProperty( "user.home" ));
				File[] sub  = home.listFiles();
				userPaths.addPathToHead( home );
				if( sub != null ) {
					for( int i = 0; i < sub.length && userPaths.getPathCount() < USERPATHS_NUM; i++ ) {
						if( sub[i].isDirectory() && !sub[i].isHidden() ) userPaths.addPathToTail( sub[i] );
					}
				}
				while( userPaths.getPathCount() < USERPATHS_NUM ) {
					userPaths.addPathToTail( home );
				}
			}
		}

		GridBagLayout lay		= new GridBagLayout();
		GridBagConstraints con	= new GridBagConstraints();

		setLayout( lay );

		ggPath			= new IOTextField();
		ggPath.addActionListener( this );		// High-Level Events: Return-Hit weiterleiten
		ggChoose		= new PathButton( type, dlgTxt );
		ggChoose.addPathListener( this );
		con.anchor		= GridBagConstraints.WEST;
		con.gridwidth	= GridBagConstraints.RELATIVE;
		con.fill		= GridBagConstraints.HORIZONTAL;
		con.gridy		= 1;
		con.weightx		= 1.0;
		lay.setConstraints( ggPath, con );
		add( ggPath );

		if( (type & TYPE_FORMATFIELD) != 0 ) {
			con.gridx		= 0;
			con.gridy		= 2;
			con.gridwidth	= 1;
			ggFormat		= new ColouredTextField();
			ggFormat.setEditable( false );
			ggFormat.setBackground( null );
			lay.setConstraints( ggFormat, con );
			add( ggFormat );
			con.gridx++;
			con.gridheight	= 3;
			con.anchor		= GridBagConstraints.NORTHWEST;
		} else {
			con.gridheight	= 2;
		}

		con.fill		= GridBagConstraints.NONE;
		con.gridy		= 0;
		con.weightx		= 0.0;
		lay.setConstraints( ggChoose, con );
		add( ggChoose );

//		deriveFrom( new PathField[0], (ggType != null) ? "$E" : "" );
		deriveFrom( new PathField[0], "" );

		// MacOS X has a bug with
		// the caret position when the
		// font isn't set explicitly for sub containers
		this.addPropertyChangeListener( "font", new PropertyChangeListener() {
			public void propertyChange( PropertyChangeEvent e )
			{
				Font fnt = ((PathField) e.getSource()).getFont();
				ggPath.setFont( fnt );
				if( ggFormat != null ) ggFormat.setFont( fnt );
			}
		});
	}
	
	/**
	 *  Sets the gadget's path. This is path will be
	 *  used as default setting when the file chooser is shown
	 *
	 *  @param  path	the new path for the button
	 */
	public void setPath( File path )
	{
		setPathIgnoreScheme( path );
		scheme = createScheme( path.getPath() );
	}

	private void setPathIgnoreScheme( File path )
	{
		ggPath.setText( path.getPath() );
		ggChoose.setPath( path );
		synchronized( collChildren ) {
			for( int i = 0; i < collChildren.size(); i++ ) {
				((PathField) collChildren.get( i )).motherSpeaks( path );
			}
		} // synchronized( collChildren )
		feedback();
	}
	
	/**
	 *  Sets a new path and dispatches a <code>PathEvent</code>
	 *  to registered listeners
	 *
	 *  @param  path	the new path for the gadget and the event
	 */
	protected void setPathAndDispatchEvent( File path )
	{
		setPathIgnoreScheme( path );
		elm.dispatchEvent( new PathEvent( this, PathEvent.CHANGED, System.currentTimeMillis(), path ));
	}
	
	/**
	 *  Returns the path displayed in the gadget.
	 *
	 *  @return the <code>File</code> corresponding to the path string in the gadget
	 */
	public File getPath()
	{
		return( new File( ggPath.getText() ));
	}

	/**
	 *  Change the contents of the format gadget.
	 *
	 *  @param  txt			Text to be displayed in the format gadget
	 *  @param  success		<code>false</code> indicates file format parse error
	 *						and will render the format gadget red.
	 *  @throws IllegalStateException   if the path field doesn't have a format gadget
	 */
	public void setFormat( String txt, boolean success )
	{
		if( ggFormat == null ) throw new IllegalStateException();
		
		ggFormat.setText( txt );
		ggFormat.setPaint( success ? null : COLOR_ERR );
	}
	
	/**
	 *  Get the string displayed in the format gadget.
	 *
	 *  @return		the currently displayed format text or <code>null</code>
	 *				if the path field has no format gadget.
	 */
	public String getFormat()
	{
		if( ggFormat != null ) {
			return ggFormat.getText();
		} else {
			return null;
		}
	}
	
	/**
	 *  Gets the type of the path field.
	 *
	 *  @return the gadget's type as specified in the constructor
	 *			use bitwise-AND with <code>TYPE_BASICMASK</code> to query the
	 *			file access type.
	 */
	public int getType()
	{
		return type;
	}
	
	/**
	 *  <code>PathField</code> offers a mechanism to automatically derive
	 *  a path name from a "mother" <code>PathField</code>. This applies
	 *  usually to output files whose names are derived from
	 *  PathFields which represent input paths. The provided
	 *  'scheme' String can contain the Tags
	 *  <pre>
	 *  $Dx = Directory of superPath x; $Fx = Filename; $E = Extension; $Bx = Brief filename
	 *  </pre>
	 *  where 'x' is the index in the provided array of
	 *  mother PathFields. Whenever the mother contents
	 *  changes, the child PathField will recalculate its
	 *  name. When the user changes the contents of the child
	 *  PathField, an algorithm tries to find out which components
	 *  are related to the mother's pathname, parts that cannot
	 *  be identified will not be automatically changing any more
	 *  unless the user completely clears the PathField (i.e.
	 *  restores full automation).
	 *  <p>
	 *  The user can abbreviate or extend filenames by pressing the appropriate
	 *  key; in this case the $F and $B tags are exchanged in the scheme.
	 *
	 *  @param  superPaths  array of mother path fields to listen to
	 *  @param  scheme		automatic formatting scheme which can incorporate
	 *						placeholders for the mother fields' paths.
	 */
	public void deriveFrom( PathField[] superPaths, String scheme )
	{
		this.superPaths = superPaths;
		this.scheme		= scheme;
		protoScheme		= scheme;

		for( int i = 0; i < superPaths.length; i++ ) {
			superPaths[ i ].addChildPathField( this );
		}
	}
	
	private void addChildPathField( PathField child )
	{
		synchronized( collChildren ) {
			if( !collChildren.contains( child )) collChildren.add( child );
		} // synchronized( collChildren )
	}
	
	private void motherSpeaks( File superPath )
	{
		setPathAndDispatchEvent( new File( evalScheme( scheme )));
	}

	// --- listener registration ---
	
	/**
	 *  Registers a <code>PathListener</code>
	 *  which will be informed about changes of
	 *  the path (i.e. user selections in the
	 *  file chooser or text editing).
	 *
	 *  @param  listener	the <code>PathListener</code> to register
	 *  @see	de.sciss.meloncillo.util.EventManager#addListener( Object )
	 */
	public void addPathListener( PathListener listener )
	{
		elm.addListener( listener );
	}

	/**
	 *  Unregisters a <code>PathListener</code>
	 *  from receiving path change events.
	 *
	 *  @param  listener	the <code>PathListener</code> to unregister
	 *  @see	de.sciss.meloncillo.util.EventManager#removeListener( Object )
	 */
	public void removePathListener( PathListener listener )
	{
		elm.removeListener( listener );
	}

	public void processEvent( BasicEvent e )
	{
		PathListener listener;
		int i;
		
		for( i = 0; i < elm.countListeners(); i++ ) {
			listener = (PathListener) elm.getListener( i );
			switch( e.getID() ) {
			case PathEvent.CHANGED:
				listener.pathChanged( (PathEvent) e );
				break;
			default:
				assert false : e.getID();
			}
		} // for( i = 0; i < elm.countListeners(); i++ )
	}

// -------- private Methoden --------

	private void checkExist()
	{
		String		fPath	= getPath().getPath();
		boolean		exists	= false;
		Color		c;
		
		if( (fPath != null) && (fPath.length() > 0) ) {
			try {
				exists = new File( fPath ).isFile();
			} catch( SecurityException e ) {}
		}
		c = exists ? COLOR_EXISTS : null;
		if( c != ggPath.getPaint() ) {
			ggPath.setPaint( c );
		}
	}

	/*
	 *	Tags: $Dx = Directory of superPath x; $Fx = Filename; $E = Extension; $Bx = Brief filename
	 */
	private String evalScheme( String scheme )
	{
		String	txt2;
		int		i, j, k;

		for( i = scheme.indexOf( "$D" ); (i >= 0) && (i < scheme.length() - 2); i = scheme.indexOf( "$D", i )) {
			j		= (int) scheme.charAt( i + 2 ) - 48;
			try {
				txt2 = superPaths[ j ].getPath().getPath();
			} catch( ArrayIndexOutOfBoundsException e1 ) {
				txt2 = "";
			}
			// sucky java 1.1 stringbuffer is impotent
			scheme	= scheme.substring( 0, i ) + txt2.substring( 0, txt2.lastIndexOf( File.separatorChar ) + 1 ) +
					  scheme.substring( i + 3 );
		}
		for( i = scheme.indexOf( "$F" ); (i >= 0) && (i < scheme.length() - 2); i = scheme.indexOf( "$F", i )) {
			j		= (int) scheme.charAt( i + 2 ) - 48;
			try {
				txt2 = superPaths[ j ].getPath().getPath();
			} catch( ArrayIndexOutOfBoundsException e1 ) {
				txt2 = "";
			}
			txt2	= txt2.substring( txt2.lastIndexOf( File.separatorChar ) + 1 );
			k		= txt2.lastIndexOf( '.' );
			scheme	= scheme.substring( 0, i ) + ((k > 0) ? txt2.substring( 0, k ) : txt2 ) +
					  scheme.substring( i + 3 );
		}
		for( i = scheme.indexOf( "$X" ); (i >= 0) && (i < scheme.length() - 2); i = scheme.indexOf( "$X", i )) {
			j		= (int) scheme.charAt( i + 2 ) - 48;
			try {
				txt2 = superPaths[ j ].getPath().getPath();
			} catch( ArrayIndexOutOfBoundsException e1 ) {
				txt2 = "";
			}
			txt2	= txt2.substring( txt2.lastIndexOf( File.separatorChar ) + 1 );
			k		= txt2.lastIndexOf( '.' );
			scheme	= scheme.substring( 0, i ) + ((k > 0) ? txt2.substring( k ) : "" ) +
					  scheme.substring( i + 3 );
		}
		for( i = scheme.indexOf( "$B" ); (i >= 0) && (i < scheme.length() - 2); i = scheme.indexOf( "$B", i )) {
			j		= (int) scheme.charAt( i + 2 ) - 48;
			try {
				txt2 = superPaths[ j ].getPath().getPath();
			} catch( ArrayIndexOutOfBoundsException e1 ) {
				txt2 = "";
			}
			txt2	= txt2.substring( txt2.lastIndexOf( File.separatorChar ) + 1 );
			k		= txt2.lastIndexOf( '.' );
			txt2	= abbreviate( (k > 0) ? txt2.substring( 0, k ) : txt2 );
			scheme 	= scheme.substring( 0, i ) + txt2 + scheme.substring( i + 3 );
		}
// XXXX
//		for( i = scheme.indexOf( "$E" ); i >= 0; i = scheme.indexOf( "$E", i )) {
//			j		= getType();
//			scheme	= scheme.substring( 0, i ) + GenericFile.getExtStr( j ) + scheme.substring( i + 2 );
//		}

		return scheme;
	}

	/*
	 *  A filename will be abbreviated. This is not so
	 *  critical on MacOS X any more because filenames
	 *  can be virtually as long as possible; on some
	 *  systems however when filenames are combinations
	 *  of more than one mother file this can be
	 *  crucial to keep the total filename length within
	 *  the file system's allowed bounds.
	 */
	private static String abbreviate( String longStr )
	{
		StringBuffer	shortStr;
		int				i, j;
		char			c;
	
		j = longStr.length();
		if( j <= ABBR_LENGTH ) return longStr;

		shortStr = new StringBuffer( j );
		for( i = 0; (i < j) && (shortStr.length() + j - i > ABBR_LENGTH); i++ ) {
			c = longStr.charAt( i );
			if( Character.isLetterOrDigit( c )) {
				shortStr.append( c );
			}
		}
		shortStr.append( longStr.substring( i ));
		longStr	= shortStr.toString();
		j		= longStr.length();
		if( j <= ABBR_LENGTH ) return longStr;
		
		shortStr = new StringBuffer( j );
		shortStr.append( longStr.charAt( 0 ));
		for( i = 1; (i < j - 1) && (shortStr.length() + j - i > ABBR_LENGTH); i++ ) {
			c = longStr.charAt( i );
			if( "aeiou√§√∂√º".indexOf( c ) < 0 ) {
				shortStr.append( c );
			}
		}
		shortStr.append( longStr.substring( i ));
		longStr	= shortStr.toString();
		j		= longStr.length();
		if( j <= ABBR_LENGTH ) return longStr;
		
		i = (ABBR_LENGTH >> 1) - 1;
		
		return( longStr.substring( 0, i ) + '\'' + longStr.substring( longStr.length() - i ));
	}

	/*
	 *  Try to analyse a concrete pathname
	 *  with respect to mother pathnames
	 *  to find some sort of scheme behind it.
	 */
	private String createScheme( String applied )
	{
		String	txt2;
		int		i = 0;
		int		k = 0;
		int		m;
		int		checkedAbbrev;
		boolean	checkedFull;

		if( applied.length() == 0 ) return protoScheme; 

		for( i = 0; i < superPaths.length; i++ ) {
			txt2 = superPaths[ i ].getPath().getPath();
			txt2 = txt2.substring( 0, txt2.lastIndexOf( File.separatorChar ) + 1 );
			if( applied.startsWith( txt2 )) {
				applied	= "$D" + (char) (i + 48) + applied.substring( txt2.length() );
				k		= 3;
				break;
			}
		}
		k = Math.max( k, applied.lastIndexOf( File.separatorChar ) + 1 );
		for( i = 0, checkedAbbrev = -1; i < superPaths.length; i++ ) {
			txt2	= superPaths[ i ].getPath().getPath();
			txt2	= txt2.substring( txt2.lastIndexOf( File.separatorChar ) + 1 );
			m		= txt2.lastIndexOf( '.' );
			txt2	= (m > 0) ? txt2.substring( 0, m ) : txt2;
			if( (protoScheme.indexOf( "$B" + (char) (i + 48) ) < 0) || (checkedAbbrev == i) ) {
				m	= applied.indexOf( txt2, k );
				if( m >= 0 ) {
					applied = applied.substring( 0, m ) + "$F" + (char) (i + 48) + applied.substring( m + txt2.length() );
					k		= m + 3;
					continue;
				}
				checkedFull	= true;
			} else {
				checkedFull = false;
			}
			if( checkedAbbrev == i ) continue;
			txt2 = abbreviate( txt2 );
			m	 = applied.indexOf( txt2, k );
			if( m >= 0 ) {
				applied = applied.substring( 0, m ) + "$B" + (char) (i + 48) + applied.substring( m + txt2.length() );
				k		= m + 3;
			} else if( !checkedFull ) {
				checkedAbbrev = i;
				i--;				// retry non-abbreviated
			}
		}
// XXX
//		txt2 = GenericFile.getExtStr( getType() );
//		if( applied.endsWith( txt2 )) {
//			applied = applied.substring( 0, applied.length() - txt2.length() ) + "$E";
//		}

		return applied;
	}

	private String abbrScheme( String orig )
	{
		int i = orig.lastIndexOf( "$F" );
		if( i >= 0 ) {
			return( orig.substring( 0, i ) + "$B" + orig.substring( i + 2 ));
		} else {
			return orig;
		}
	}

	private String expandScheme( String orig )
	{
		int i = orig.indexOf( "$B" );
		if( i >= 0 ) {
			return( orig.substring( 0, i ) + "$F" + orig.substring( i + 2 ));
		} else {
			return orig;
		}
	}

	private String udirScheme( String orig, int idx )
	{
		int		i;
		File	udir = userPaths.getPath( idx );
	
		if( udir == null ) return orig;
	
		if( orig.startsWith( "$D" )) {
			i = 3;
		} else {
			i = orig.lastIndexOf( File.separatorChar ) + 1;
		}

		return( new File( udir, orig.substring( i )).getPath() );
	}

	/*
	 *  Whenever the pathname changes,
	 *  update the format fild and the
	 *  indication of file existance.
	 */
	private void feedback()
	{
// XXX
//		if( (handledTypes != null) && ((type & TYPE_BASICMASK) == TYPE_INPUTFILE) ) {
//			calcFormat();
//		} else
		if( (type & TYPE_BASICMASK) == TYPE_OUTPUTFILE ) {
			checkExist();
		}
	}
	
// -------- PathListener interface --------
// we're listening to ggChoose

	public void pathChanged( PathEvent e )
	{
		File path = e.getPath();
		scheme = createScheme( path.getPath() );
		setPathAndDispatchEvent( path );
	}

// -------- ActionListener interface --------
// we're listening to ggPath

	public void actionPerformed( ActionEvent e )
	{
		String str = ggPath.getText();
		if( ggPath.getText().length() == 0 ) {				// automatic generation
			str = evalScheme( scheme );
		} else {
			scheme = createScheme( str );
		}
		setPathAndDispatchEvent( new File( str ));
	}

// -------- interne IOTextfeld-Klasse --------

	private class IOTextField
	extends ColouredTextField
	{
		private IOTextField()
		{
			super( 32 );
			
			InputMap	inputMap	= getInputMap();
			ActionMap	actionMap   = getActionMap();
			int			i;
			String		s;
			
			inputMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, KeyEvent.META_MASK + KeyEvent.ALT_MASK ), "abbr" );
			actionMap.put( "abbr", new AbstractAction() {
				public void actionPerformed( ActionEvent e )
				{
					scheme = abbrScheme( scheme );
					setPathAndDispatchEvent( new File( evalScheme( scheme )));
				}
			});
			inputMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, KeyEvent.META_MASK + KeyEvent.ALT_MASK ), "expd" );
			actionMap.put( "expd", new AbstractAction() {
				public void actionPerformed( ActionEvent e )
				{
					scheme = expandScheme( scheme );
					setPathAndDispatchEvent( new File( evalScheme( scheme )));
				}
			});
			inputMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, KeyEvent.META_MASK ), "auto" );
			actionMap.put( "auto", new AbstractAction() {
				public void actionPerformed( ActionEvent e )
				{
					scheme = protoScheme;
					setPathAndDispatchEvent( new File( evalScheme( scheme )));
				}
			});
			for( i = 0; i < USERPATHS_NUM; i++ ) {
				s = "sudir" + i;
				inputMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD1 + i,
													  KeyEvent.META_MASK + KeyEvent.SHIFT_MASK ), s );
				actionMap.put( s, new SetUserDirAction( i ));
				s = "rudir" + i;
				inputMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD1 + i, KeyEvent.META_MASK ), s );
				actionMap.put( s, new RecallUserDirAction( i ));
			}
		}

		private class SetUserDirAction
		extends AbstractAction
		{
			private int idx;
			private javax.swing.Timer visualFeedback;
			private Paint oldPaint = null;
		
			private SetUserDirAction( int idx )
			{
				this.idx		= idx;
				visualFeedback  = new javax.swing.Timer( 250, this );
				visualFeedback.setRepeats( false );
			}
			
			public void actionPerformed( ActionEvent e )
			{
				if( e.getSource() == visualFeedback ) {
					ggPath.setPaint( oldPaint );
				} else {
					File dir = getPath().getParentFile();
					if( dir != null ) {
						userPaths.setPath( idx, dir );
						if( visualFeedback.isRunning() ) {
							visualFeedback.restart();
						} else {
							oldPaint = ggPath.getPaint();
							ggPath.setPaint( COLOR_PROPSET );
							visualFeedback.start();
						}
					}
				}
			}
		}

		private class RecallUserDirAction
		extends AbstractAction
		{
			private int idx;
		
			private RecallUserDirAction( int idx )
			{
				this.idx = idx;
			}
			
			public void actionPerformed( ActionEvent e )
			{
				scheme = udirScheme( scheme, idx );
				setPathAndDispatchEvent( new File( evalScheme( scheme )));
			}
		}
	} // class IOTextField
} // class PathField
