/*
 *  PlugInContext.java
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
 *		24-Jul-04   subclasses PlugInContext
 *		01-Sep-04	additional comments.
 */

package de.sciss.meloncillo.plugin;

import java.util.*;

import de.sciss.io.*;

/**
 *	This class contains static information
 *	about a plug-in process such as the
 *	involved transmitters and receivers. It
 *	serves as a medium between the host and
 *	the plug-in and provides a mechanism for
 *	communication through setting of map
 *	entries.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class PlugInContext
{
	/**
	 *  This variable is merely
	 *  for free use by the plugin.
	 *  It can be used to store variables
	 *  so they needn't be global.
	 */
	public final HashMap  moduleMap  = new HashMap();

	/**
	 *  Key: Minimum required Blocksize<br>
	 *  Value: Integer
	 */
	public static final Object KEY_MINBLOCKSIZE = "minblocksize";
	/**
	 *  Key: Maximum required Blocksize<br>
	 *  Value: Integer
	 */
	public static final Object KEY_MAXBLOCKSIZE = "maxblocksize";
	/**
	 *  Key: Preferred Blocksize<br>
	 *  Value: Integer
	 */
	public static final Object KEY_PREFBLOCKSIZE = "prefblocksize";
	/**
	 *  Key: Object that produces output<br>
	 *  Value: PlugIn
	 */
	public static final Object KEY_PLUGIN		= "plugin";
	/**
	 *  Key: Object that consumes the produced output<br>
	 *  Value: subclass dependant
	 */
	public static final Object KEY_CONSUMER		= "consumer";

	private final PlugInHost		host;
	private final java.util.List	collReceivers;
	private final java.util.List	collTransmitters;
	private final Span				time;
	private final int				sourceRate;
	private int						sourceBlockSize;
	
	private final HashMap			options			= new HashMap();
	private final HashSet			modifiedOptions = new HashSet();
	
	/**
	 *  Constructs a new PlugInContext.
	 *
	 *  @param  host				the object responsible for hosting
	 *								the rendering process
	 *  @param  collReceivers		the receivers involved in the rendering
	 *  @param  collTransmitters	the transmitters involved in the rendering
	 *  @param  time				the time span to render
	 *  @param  sourceRate			the source sense data rate
	 *
	 *	@warning	the collections are <b>not</b> copied, hence
	 *				the caller is responsible for ensuring their
	 *				constancy.
	 */
	public PlugInContext( PlugInHost host, java.util.List collReceivers,
						  java.util.List collTransmitters, Span time, int sourceRate )
	{
		this.host				= host;
		this.collReceivers		= collReceivers;
		this.collTransmitters   = collTransmitters;
		this.time				= time;
		this.sourceRate			= sourceRate;
	}
	
	/**
	 *  Returns the object responsible for hosting
	 *  the rendering process
	 *
	 *	@return		the plug-in host as passed to
	 *				the constructor
	 */
	public PlugInHost getHost()
	{
		return host;
	}

	/**
	 *  Returns the receivers involved in the rendering.
	 *	This is identical to the list passed to the constructor.
	 *
	 *	@return	a list whose elements are of class Receiver
	 *
	 *	@see	de.sciss.meloncillo.receiver.Receiver
	 *
	 *	@warning	does not make a copy of the list, thus
	 *				use read-only!
	 */
	public java.util.List getReceivers()
	{
		return collReceivers;
	}

	/**
	 *  Returns the transmitters involved in the rendering.
	 *	This is identical to the list passed to the constructor.
	 *
	 *	@return	a list whose elements are of class Transmitter
	 *
	 *	@see	de.sciss.meloncillo.transmitter.Transmitter
	 *
	 *	@warning	does not make a copy of the list, thus
	 *				use read-only!
	 */
	public java.util.List getTransmitters()
	{
		return collTransmitters;
	}

	/**
	 *  Returns the time span to render
	 *
	 *	@return	the rendering time span as passed to the constructor
	 */
	public Span getTimeSpan()
	{
		return time;
	}

	/**
	 *  Returns the source sense data rate
	 *
	 *	@return	the source rate (in hertz) as passed to the constructor
	 */
	public int getSourceRate()
	{
		return sourceRate;
	}

	/**
	 *  Adjusts the size of source data in
	 *	blocking operation. This shall only be
	 *	called by the host and not be altered
	 *	during processing.
	 *
	 *	@param	sourceBlockSize	the new block size in frames
	 */
	public void setSourceBlockSize( int sourceBlockSize )
	{
		this.sourceBlockSize = sourceBlockSize;
	}

	/**
	 *  Queries the  the size of source data in
	 *	blocking operation. Plug-ins can call
	 *	this to adjust their internal buffers.
	 *
	 *	@return		the block size in frames
	 */
	public int getSourceBlockSize()
	{
		return sourceBlockSize;
	}

	/**
	 *  Replaces a value for an option
	 *  (or create a new option if no
	 *  value was previously set). The
	 *  option is added to the list of
	 *  modifications, see getModifiedOptions().
	 *
	 *	@param	key		key of the option such as KEY_PREFBLOCKSIZE
	 *	@param	value	corresponding value. Hosts and plug-ins
	 *					should "know" what kind of key required what
	 *					kind of value class
	 */
	public void setOption( Object key, Object value )
	{
		options.put( key, value );
		modifiedOptions.add( key );
	}
	
	/**
	 *  Performs setOption() on a series
	 *  of key/value pairs.
	 *
	 *	@param	map		a map whose key/value pairs
	 *					are copied to the context options and
	 *					appear in the modified options list
	 */
	public void setOptions( Map map )
	{
		options.putAll( map );
		modifiedOptions.addAll( map.keySet() );
	}
	
	/**
	 *  Queries the value of an options.
	 *
	 *	@return		the value corresponding to the key
	 *				or null if the option wasn't set.
	 */
	public Object getOption( Object key )
	{
		return options.get( key );
	}
	
	/**
	 *  Returns a set of all options modified
	 *  since last calling this method. Calling
	 *  this method twice in succession will
	 *  result in an empty set. All options
	 *  set using setOption() after calling
	 *  getModifiedOptions() will be present
	 *  at the next invocation of this method.
	 *
	 *	@return	a set of keys which were modified
	 *			since the last invocation of this method
	 */
	public java.util.Set getModifiedOptions()
	{
		java.util.Set result = new HashSet( modifiedOptions );
		modifiedOptions.clear();
	
		return result;
	}
}