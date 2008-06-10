/*
 *  AbstractTool.java
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
 *		31-Jul-04   commented. RuntimeExceptions added
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;

/**
 *  This class describes a generic GUI tool
 *  that can be aquired and dismissed by
 *  a <code>Component</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 10-Jun-08
 */
public abstract class AbstractTool
implements MouseListener, MouseMotionListener, TopPainter
{
	private Component c;

	public AbstractTool() {}

	/**
	 *  Makes this tool a component's active
	 *  tool. Subclasses should override this
	 *  method to perform additional initializations
	 *  but are obliged to call the super method,
	 *  which will store the component for the
	 *  <code>getComponent</code> method and will
	 *  activate Mouse and MouseMotion listening.
	 *
	 *  @param  c							the <code>Component</code> on which
	 *										this tool will operate.
	 *  @throws IllegalArgumentException	if the passed <code>Component</code>
	 *										is <code>null</code>
	 *  @throws IllegalStateException		if the tool was aquired before.
	 *  @synchronization	the method must be called in the event thread
	 */
	public void toolAcquired( Component c )
	{
		if( this.c != null ) throw new IllegalStateException();
		if( c == null ) throw new IllegalArgumentException();

		this.c = c;
		c.addMouseListener( this );
		c.addMouseMotionListener( this );
	}

	/**
	 *  Makes this tool inactive. This usually
	 *  happens when the user switches to
	 *  a different tool. Any ongoing drag- and
	 *  drop actions or the like are to be
	 *  cancelled upon invocation of this method.
	 *  Subclasses should override this
	 *  method to perform additional cleanup operations
	 *  but are obliged to call the super method,
	 *  which will forget the component so
	 *  <code>getComponent</code> will return
	 *  <code>null</code> afterwards, also it will
	 *  deactivate Mouse and MouseMotion listening
	 *
	 *  @param  c							the <code>Component</code> from
	 *										which the tool is removed.
	 *  @throws IllegalArgumentException	if the passed <code>Component</code>
	 *										is <code>null</code> or if the
	 *										tool was attached to a different
	 *										component.
	 *  @throws IllegalStateException		if the tool was not aquired before.
	 *  @synchronization	the method must be called in the event thread
	 */
	public void toolDismissed( Component c )
	{
		if( this.c == null ) throw new IllegalStateException();
		if( c == null || c != this.c ) throw new IllegalArgumentException();
	
		c.removeMouseMotionListener( this );
		c.removeMouseListener( this );
		this.c = null;
	}

	/**
	 *  Paint the current (possibly volatile) state
	 *  of the tool performance. The should be called
	 *  at the end of the component's <code>paintComponent</code>
	 *  method. The tool is allowed to modify the <code>Stroke</code>
	 *  and the <code>Paint</code> of the <code>Graphics2D</code>
	 *  object without needing to restore it. The tool is responsible
	 *  for undoing any changes to the <code>AffineTransform</code>
	 *  of the <code>Graphics2D</code> object however.
	 *
	 *  @param  g   a <code>Graphics2D</code> object to paint on.
	 *				Initial <code>Stroke</code> and <code>Paint</code>
	 *				are undefinied. Initial transform should
	 *				be negotiated between the component
	 *				and the tool, e.g. the <code>Surface</code> object
	 *				may garantee to scale the graphics
	 *				to the virtual space (0, 0 ... 1, 1)
	 *  @see		javax.swing.JComponent#paintComponent( Graphics )
	 */
	public abstract void paintOnTop( Graphics2D g );

	/**
	 *  Returns the component on which the
	 *  tool operates.
	 *
	 *  @return the tool's <code>Component</code> or
	 *			<code>null</code> if the tool was dismissed.
	 */
	protected Component getComponent()
	{
		return c;
	}
}