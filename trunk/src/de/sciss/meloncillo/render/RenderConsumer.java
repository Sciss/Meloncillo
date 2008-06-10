/*
 *  RenderConsumer.java
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
 *		15-Jun-04   created
 *		20-Jul-04   extended to symmetrically reflect RenderPlugIn
 *		02-Sep-04	commented
 */

package de.sciss.meloncillo.render;

import java.io.*;

/**
 *	Classes implementing this interface
 *	state that they can consume rendered
 *	and transformed streaming data. Its
 *	just like <code>RenderPlugIn</code> but
 *	from the reverse perspective. However
 *	unlike <code>RenderPlugIn</code>, the
 *	consumer cannot specify itself which
 *	data it wishes to receive (pull)
 *	but is provided with a preconfigured
 *	source object and requests (push).
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public interface RenderConsumer
{
	/**
	 *	Initiates the consumation.
	 *	The consumer should check the source's
	 *	request fields to find out which
	 *	data is to be written out.
	 *
	 *	@param	context	render context
	 *	@param	source	render source featuring
	 *					the target requests
	 *	@return	<code>false</code> if an error occurs
	 *			and consumation should be aborted
	 *
	 *	@throws	IOException	if a read/write error occurs
	 */
	public boolean consumerBegin( RenderContext context, RenderSource source )
	throws IOException;

	/**
	 *	Requests the consumer to consume a block of rendered data.
	 *
	 *	@param	context	render context
	 *	@param	source	render source featuring
	 *					the target requests and the rendered data block.
	 *	@return	<code>false</code> if an error occurs
	 *			and consumation should be aborted
	 *
	 *	@throws	IOException	if a read/write error occurs
	 */
	public boolean consumerRender( RenderContext context, RenderSource source )
	throws IOException;

	/**
	 *	Tells the consumer to finish consumption.
	 *	i.e. close files, end compound edits etc.
	 *
	 *	@param	context	render context
	 *	@param	source	render source
	 *	@return	<code>false</code> if an error occurs
	 *			and consumation should be aborted
	 *
	 *	@throws	IOException	if a read/write error occurs
	 */
	public boolean consumerFinish( RenderContext context, RenderSource source )
	throws IOException;

	/**
	 *	Tells the consumer that the rendering was
	 *	aborted and it should cancel any unfinished edits.
	 *
	 *	@param	context	render context
	 *	@param	source	render source
	 *
	 *	@throws	IOException	if a read/write error occurs
	 */
	public void consumerCancel( RenderContext context, RenderSource source )
	throws IOException;
}
