/*
 *  BounceDialog.java
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
 *		07-Jul-04	created as RenderDialog successor using AbstractRenderDialog superclass
 *		14-Jul-04	new RenderSource Management. Now subclasses BasicRenderDialog
 *		02-Sep-04	commented
 *		01-Jan-05	added online help
 */

package de.sciss.meloncillo.render;

import java.io.*;
import java.util.*;

import javax.swing.WindowConstants;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.io.*;

/**
 *  The dialog for bouncing sound synthesis
 *	to disk. The GUI is presented through
 *	the superclass. This class handles
 *	creation and validation of the render context
 *	and provides simple forwarding
 *	for the <code>invokeProducer...</code> methods.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class BounceDialog
extends BasicRenderDialog
{
	private static final Vector collProducerTypes   = new Vector();

	/**
	 *	Constructs a Bounce-to-Disk dialog.
	 *
	 *	@param	root	Application root
	 *	@param	doc		Session document
	 */
	public BounceDialog( Main root, Session doc )
	{
		super( root, doc, AbstractApplication.getApplication().getResourceString( "frameBounce" ),
			GADGET_RESAMPLING | GADGET_SELECTION );
//		HelpGlassPane.setHelp( this.getRootPane(), "BounceDialog" );	// EEE
		addListener( new AbstractWindow.Adapter() {
			public void windowClosing( AbstractWindow.Event e )
			{
				dispose();
			}
		});
		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE ); // window listener see above!
		AbstractApplication.getApplication().addComponent( Main.COMP_BOUNCE, this );
	}

	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_BOUNCE );
		super.dispose();
	}

	protected java.util.List getProducerTypes()
	{
		if( collProducerTypes.isEmpty() ) {
			Hashtable h;
			h = new Hashtable();
			h.put( Main.KEY_CLASSNAME, "de.sciss.meloncillo.render.LispBounce" );
			h.put( Main.KEY_HUMANREADABLENAME, "Lisp Plug-In" );
			collProducerTypes.add( h );
		}
		return collProducerTypes;
	}

	/**
	 *	Creates a render context
	 *	using all receivers and all transmitters,
	 *	time span depending on the selection-only
	 *	gadget.
	 *
	 *	@synchronization	attemptShared on DOOR_TIMETRNSRCV
	 */
	protected RenderContext createRenderContext()
	{
		boolean all = !classPrefs.getBoolean( KEY_SELECTIONONLY, false );
	
		if( !doc.bird.attemptShared( Session.DOOR_TIMETRNSRCV, 250 )) return null;
		try {
			return new RenderContext( this, doc.receivers.getAll(),
											doc.transmitters.getAll(),
				all || doc.timeline.getSelectionSpan().isEmpty() ?
					new Span( 0, doc.timeline.getLength() ) : doc.timeline.getSelectionSpan(),
											doc.timeline.getRate() );
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIMETRNSRCV );
		}
	}

	/**
	 *	Checks if the render context is still valid.
	 *
	 *	@return	<code>false</code> if the context became invalid
	 *			e.g. after a change in the selection-only gadget
	 *
	 *	@synchronization	attemptShared on DOOR_TIMETRNSRCV
	 */
	protected boolean isRenderContextValid( RenderContext context )
	{
		boolean all = !classPrefs.getBoolean( KEY_SELECTIONONLY, false );
		Span	timeSpan;

		if( !doc.bird.attemptShared( Session.DOOR_TIMETRNSRCV, 250 )) return false;
		try {
			timeSpan = all || doc.timeline.getSelectionSpan().isEmpty() ?
				new Span( 0, doc.timeline.getLength() ) : doc.timeline.getSelectionSpan();
					
			return( doc.transmitters.getAll().equals( context.getTransmitters() ) &&
					doc.receivers.getAll().equals( context.getReceivers() ) &&
					timeSpan.equals( context.getTimeSpan() ) &&
					doc.timeline.getRate() == context.getSourceRate() );
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIMETRNSRCV );
		}
	}

// ---------------- concrete methods ---------------- 

	/**
	 *	This implementation simply calls
	 *	<code>prod.producerBegin( context, source )</code>
	 */
	protected boolean invokeProducerBegin( ProcessingThread pt, RenderContext context,
										   RenderSource source, RenderPlugIn prod )
	throws IOException
	{
		return prod.producerBegin( context, source );
	}
	
	/**
	 *	This implementation simply calls
	 *	<code>prod.producerCancel( context, source )</code>
	 */
	protected void invokeProducerCancel( ProcessingThread pt, RenderContext context,
									     RenderSource source, RenderPlugIn prod )
	{
		try {
			prod.producerCancel( context, source );
		} catch( IOException e2 ) {}
	}

	/**
	 *	This implementation simply calls
	 *	<code>prod.producerRender( context, source )</code>
	 */
	protected boolean invokeProducerRender( ProcessingThread pt, RenderContext context,
										    RenderSource source, RenderPlugIn prod )
	throws IOException
	{
		return prod.producerRender( context, source );
	}

	/**
	 *	This implementation simply calls
	 *	<code>prod.producerFinish( context, source )</code>
	 */
	protected boolean invokeProducerFinish( ProcessingThread pt, RenderContext context,
										    RenderSource source, RenderPlugIn prod )
	throws IOException
	{
		return prod.producerFinish( context, source );
	}
}