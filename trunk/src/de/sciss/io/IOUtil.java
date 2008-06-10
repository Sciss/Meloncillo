/*
 *  IOUtil.java
 *  de.sciss.io package
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
 *		03-Aug-04   commented. mkdir() in createEmptyDirectory was
 *					replaced by mkdirs() (the recursive version).
 *		04-Feb-05	bugfix in deleteAll()
 *		21-Apr-05	removed getBuildFolder()
 *		26-May-05	now de.sciss.io package, 'main' class, getResourceString()
 */

package de.sciss.io;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 *  This is a helper class containing utility static
 *  functions for common file operations.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.1, 26-May-05
 */
public class IOUtil
{
	/**
	 *  Value: String representing the pathname
	 *  of the temporary files directory.<br>
	 *  Has default value: yes!<br>
	 *  Node: root
	 */
	public static final String KEY_TEMPDIR	= "tmpdir";		// string : pathname

	private static final double VERSION	= 0.1;
	private static final ResourceBundle resBundle = ResourceBundle.getBundle( "IOUtilStrings" );
	private static final Preferences prefs = Preferences.userNodeForPackage( IOUtil.class );

	private IOUtil() {}
	
	public static final Preferences getUserPrefs()
	{
		return prefs;
	}

	public static final double getVersion()
	{
		return VERSION;
	}

	public static final String getResourceString( String key )
	{
		try {
			return resBundle.getString( key );
		}
		catch( MissingResourceException e1 ) {
			return( "[Missing Resource: " + key + "]" );
		}
	}
   
	/**
	 *  Input/output methods sometimes
	 *  contain methods from other java API that
	 *  throw exceptions which are not subclasses
	 *  of <code>IOException</code>. The present a more
	 *  consistent frontend to the caller of these
	 *  methods, any exceptions can be simply mapped
	 *  to <code>IOException</code> by creating a new <code>IOException</code>
	 *  with the same message text as the original
	 *  exception.
	 *  <p>
	 *  For example, in <code>PrefsUtil</code>'s <code>toXML</code> method,
	 *  <code>BackingStoreException</code>s and <code>DOMException</code>s are
	 *  caught and rethrown:
	 *  <pre>
	 *  try {
	 *      ...
	 *  } catch( DOMException e1 ) {
	 *      throw IOUtil.map( e1 );
	 *  }
	 *  </pre>
	 *  
	 *  @param  e   the original exception whose message is queries
	 *				and copied to the returned exception.
	 *  @return a newly created <code>IOException</code> with the message text
	 *			and exception class name copied from the given original
	 *			exception.
	 */
	public static IOException map( Exception e )
	{
		return new IOException( e.getClass().getName() + " : " + e.getLocalizedMessage() );
	}

	/**
	 *  Abbreviates a path name by
	 *  replacing sub directories 
	 *  by an ellipsis character.
	 *  This is used inside <code>MenuFactory</code>
	 *  to shorten the pathnames attached to
	 *  the Open-Recent menu.
	 *
	 *  @param  fileName	the pathName to abbreviate
	 *  @param  maxLen		maximum length of the
	 *						Abbreviation. Beware: this
	 *						method appends sub directories
	 *						until maxLen is exceeded, therefore
	 *						the returned String can well exceed
	 *						maxLen! 
	 *  @return				the abbreviated path name or
	 *						the original path name if it was
	 *						shorter or equal maxLen. It is
	 *						guaranteed that the file name part
	 *						of the path is fully included.
	 *
	 *  @todo   BBEdit 7 has a nicer looking alternative: placing
	 *			the filename in the beginning of the menu item
	 *			line, followed by an abbreviated path.
	 */
	public static String abbreviate( String fileName, int maxLen )
	{
		if( fileName.length() <= maxLen ) return fileName;
			
		File			f		= new File( fileName );
		ArrayList		coll	= new ArrayList();
		String			name;
		StringBuffer	begBuf  = new StringBuffer();
		StringBuffer	endBuf  = new StringBuffer();
		int				len;
		boolean			b;
			
		while( (f != null ) && (name = f.getName()) != null ) {
			coll.add( 0, name );
			f = f.getParentFile();
		}
		if( coll.isEmpty() ) return fileName;
			
		len		= coll.size() << 1;   // ellipsis character per subdir and filename, separator per subdir
		name	= (String) coll.remove( coll.size() - 1 );
		endBuf.insert( 0, name );
		len    += name.length();
		if( !coll.isEmpty() ) {
			name = (String) coll.remove( 0 );
			begBuf.append( name );
			begBuf.append( File.separator );
			len += name.length() - 1;
		}
		for( b = false; !coll.isEmpty() && len <= maxLen; b = !b ) {
			if( b ) {
				name = (String) coll.remove( coll.size() - 1 );
				endBuf.insert( 0, File.separator );
				endBuf.insert( 0, name );
			} else {
				name = (String) coll.remove( 0 );
				begBuf.append( name );
				begBuf.append( File.separator );
			}
			len += name.length() - 1;		// full name instead of single character ellipsis
		}
			
		while( !coll.isEmpty() ) {
			coll.remove( 0 );
			begBuf.append( '\u2026' );
			begBuf.append( File.separator );
		}
			
		return( begBuf.append( endBuf ).toString() );
	}

	/**
	 *  Creates a new empty directory.
	 *  If a file or directory of the
	 *  specified name already exists
	 *  it will be deleted.
	 *
	 *  @param  f   pathname denoting the folder to create.
	 *				this method will attempt to create nonexisting
	 *				parent folders if required.
	 *  @throws IOException is the directory cannot be created
	 *						or emptied.
	 *
	 *  @see	#deleteAll( File )
	 *  @see	File#mkdirs()
	 */
	public static void createEmptyDirectory( File f )
	throws IOException
	{
		deleteAll( f );
		if( !f.mkdirs() ) {
			throw new IOException( f.getAbsolutePath() + " : " +
				IOUtil.getResourceString( "errMakeDir" ));
		}
	}

	/**
	 *	Creates an empty file. If file denoted
	 *	by <code>f</code> already exists, it will
	 *	be erased.
	 *
	 *	@param	f	the file to be erased
	 *
	 *	@throws	IOException	if an error occurs such as the file
	 *			being protected or indicating an invalid path
	 */
	public static void createEmptyFile( File f )
	throws IOException
	{
		f.delete();
		RandomAccessFile raf = new RandomAccessFile( f, "rw" );
		try {
			raf.setLength( 0 );
		}
		finally {
			raf.close();
		}
	}
	
	/**
	 *  Creates a new temporary file,
	 *  using the preferred temp file folder
	 *  (<code>KEY_TEMPDIR</code>). The file will
	 *  automatically deleted upon
	 *  application exit.
	 *
	 *  @return the newly created temp file, suitable for
	 *			passing to a RandomAccessFile constructor or similar.
	 *  @throws IOException if this method fails to create the file
	 *  @see	File#createTempFile( String, String, File )
	 *  @see	File#deleteOnExit()
	 *  @see	PrefsUtil#KEY_TEMPDIR
	 */
	public static File createTempFile()
	throws IOException
	{
		return IOUtil.createTempFile( "sciss", null );
	}

	/**
	 *  Create a new temporary file with
	 *  specified prefix and suffix.
	 *  Suffix is allowed to be null in
	 *  which case a default suffix (.tmp) is used.
	 *
	 *  @param  prefix  string with which to filename
	 *					shall begin, minimum three letters but
	 *					as short as possible. might be truncated.
	 *  @param  suffix  string with which to filename
	 *					shall end (including the period if used as a file type suffix).
	 *					might be truncated, but if it begins with a period, the
	 *					following three letters are guaranteed to be preserved.
	 *  @return			the newly created temp file, suitable for
	 *					passing to a RandomAccessFile constructor or similar.
	 *  @throws IOException if this method fails to create the file
	 *  @see	File#createTempFile( String, String, File )
	 *  @see	File#deleteOnExit()
	 *  @see	PrefsUtil#KEY_TEMPDIR
	 */
	public static File createTempFile( String prefix, String suffix )
	throws IOException
	{
		File tmpF = File.createTempFile( prefix, suffix, new File( IOUtil.getUserPrefs().get(
			IOUtil.KEY_TEMPDIR, null )));
		tmpF.deleteOnExit();
		return tmpF;
	}

	/**
	 *  Tries to get the folder containing
	 *  the (packaged MacOS X) meloncillo
	 *  application. Assuming, the current
	 *  work directory of the java machine
	 *  is <code>Meloncillo.app/Contents/Resources/Java</code>, this
	 *  climbs up four subdirs. May return
	 *  <code>null</code>, if the path cannot be determined.
	 *
	 *  @return the assumed build directory of the application
	 *			or null if the method fails.
	 */
//	public static File getBuildFolder()
//	{
//		File currentDir = new File( "" ).getAbsoluteFile();
//System.err.println( "currentDir : "+currentDir );
//		for( int i = 0; i < 4 && currentDir != null; i++ ) {
//			currentDir = currentDir.getParentFile();
//		}
//		return currentDir;
//	}
	
	/**
	 *  Deletes a file or directory.
	 *  If the directory is not empty,
	 *  all files and subfolders in the
	 *  directory will be deleted recursively.
	 *
	 *  @param  f   the path (file or folder) to delete
	 *  @throws IOException if the deletion fails
	 *
	 *  @see	File#delete()
	 */
	public static void deleteAll( File f )
	throws IOException
	{
		if( f.isDirectory() ) {
			File[] subFiles = f.listFiles();
			for( int i = 0; i < subFiles.length; i++ ) {
				deleteAll( subFiles[i] );
			}
		}
		if( f.exists() ) {
			if( !f.delete() ) {
				throw new IOException( f.getAbsolutePath() + " : " +
					IOUtil.getResourceString( "errDeleteFile" ));
			}
		}
	}
}
