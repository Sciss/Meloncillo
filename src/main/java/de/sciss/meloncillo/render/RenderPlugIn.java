/*
 *  RenderPlugIn.java
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
 *		15-Jun-04   created
 *		14-Jul-04   new RenderSource Management
 *		20-Jul-04   methods renamed for consistency
 *		24-Jul-04   subclasses PlugIn
 *		02-Sep-04	additional comments
 */

package de.sciss.meloncillo.render;

import java.io.*;

import de.sciss.meloncillo.plugin.*;

/**
 *  A simple interface for communication
 *  between a render host (implementing the RenderHost
 *  interface) and an object that can produce
 *  any kind of output (implementing this
 *  interface). The host calls the getSettingsView()
 *  to present a GUI to the user. When the user
 *  hits the render button, the host will in succession
 *  call producerBegin (once), producerRender (once or
 *  several times), producerFinish (once) or if the
 *  user aborts the rendering producerCancel.
 *  Communication is mainly provided through a
 *  RenderContext object.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public interface RenderPlugIn
extends PlugIn
{
	/**
	 *  Begins the rendering. If the parameters are
	 *  not workable for the module, it should throw
	 *  or set an Exception or warnings and return false.
	 *  It shall return true on success. It can make
	 *  adjustments to the RenderContext by setting options
	 *  like KEY_TARGETRATE, KEY_MINBLOCKSIZE etc. which
	 *  will be read out by the host. Though access to
	 *  Session is provided through the init() method,
	 *  the render module should only use the fields provided
	 *  by the context, such as getSourceRate(), getReceivers()
	 *  etc. It needn't deal with door locking which is
	 *  provided by the host.
	 *
	 *	@param	context	render context
	 *	@param	source	render source. the plug-in should
	 *					check the requests related to the data
	 *					it wishes to receive.
	 *	@return	<code>false</code> if an error occurs
	 *			and rendering should be aborted
	 *
	 *	@throws	IOException	if a read/write error occurs
	 */
	public boolean producerBegin( RenderContext context, RenderSource source )
	throws IOException;
	
	/**
	 *  Renders some output from the provided block of
	 *  sense data. Options like target samplerate or
	 *  blocksize are considered to be set in beginRender()
	 *  and thus it's not guaranteed that the host check
	 *  a modification of these values. The module should
	 *  invoke host.setProgression() if possible allowing
	 *  the user to predict the duration of the rendering.
	 *
	 *	@param	context	render context
	 *	@param	source	render source containing the current
	 *					data  block
	 *	@return	<code>false</code> if an error occurs
	 *			and rendering should be aborted
	 *
	 *	@throws	IOException	if a read/write error occurs
	 */
	public boolean producerRender( RenderContext context, RenderSource source )
	throws IOException;

	/**
	 *  Allows the render module to perform any necessary
	 *  finishing activities like closing files or
	 *  normalizing output.
	 *
	 *	@param	context	render context
	 *	@param	source	render source
	 *	@return	<code>false</code> if an error occurs
	 *			and rendering should be aborted
	 *
	 *	@throws	IOException	if a read/write error occurs
	 */
	public boolean producerFinish( RenderContext context, RenderSource source )
	throws IOException;
	
	/**
	 *  Tells the module that the rendering was aborted.
	 *  The module should perform any necessary cleanups
	 *  and return as soon as possible.
	 *
	 *	@param	context	render context
	 *	@param	source	render source
	 *
	 *	@throws	IOException	if a read/write error occurs
	 */
	public void producerCancel( RenderContext context, RenderSource source )
	throws IOException;
}