/*
 *  FilterDialog.java
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
 *		14-Jul-04   now subclasses BasicRenderDialog
 *		02-Sep-04	commented
 *		01-Jan-05	added online help
 */

package de.sciss.meloncillo.render;

import java.io.*;
import java.util.*;
import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.io.*;
import de.sciss.meloncillo.session.*;
import de.sciss.meloncillo.transmitter.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.gui.*;

/**
 *  The dialog for filtering trajectory data.
 *	The GUI is presented through
 *	the superclass. This class handles
 *	creation and validation of the render context
 *	and provides simple forwarding
 *	for the <code>invokeProducer...</code> methods.
 *	<p>
 *	Since transformed trajectory data needs to
 *	re-inserted in the session, this class also
 *	implements the <code>RenderConsumer/code> which
 *	will deal with this issue.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo		an option to render the transformed data
 *				into the clipboard.
 *	@todo		support of variable output time span
 *	@todo		change in selected transmitters is not
 *				recognized unless the dialog is closed and re-opened
 */
public class FilterDialog
extends BasicRenderDialog
implements RenderConsumer
{
	private static final Vector collProducerTypes   = new Vector();
	private static final Vector collEmpty			= new Vector();

	// context options map
	private static final String	KEY_CONSC	= "consc";

	/**
	 *	Constructs a Trajectory-Filtering dialog.
	 *
	 *	@param	root	Application root
	 *	@param	doc		Session document
	 */
	public FilterDialog( Main root, Session doc )
	{
		super( root, doc, AbstractApplication.getApplication().getResourceString( "frameFilter" ), GADGET_RESAMPLING );
        HelpGlassPane.setHelp( this.getRootPane(), "FilterDialog" );
	}

	protected java.util.List getProducerTypes()
	{
		if( collProducerTypes.isEmpty() ) {
			Hashtable h;
			h = new Hashtable();
			h.put( Main.KEY_CLASSNAME, "de.sciss.meloncillo.render.TimeWarpFilter" );
			h.put( Main.KEY_HUMANREADABLENAME, "Time Warp" );
			collProducerTypes.add( h );
			h = new Hashtable();
			h.put( Main.KEY_CLASSNAME, "de.sciss.meloncillo.render.VectorTransformFilter" );
			h.put( Main.KEY_HUMANREADABLENAME, "Vector Transformation" );
			collProducerTypes.add( h );
			h = new Hashtable();
			h.put( Main.KEY_CLASSNAME, "de.sciss.meloncillo.render.LispFilter" );
			h.put( Main.KEY_HUMANREADABLENAME, "Lisp Plug-In" );
			collProducerTypes.add( h );
		}
		return collProducerTypes;
	}

	/**
	 *	Creates a render context
	 *	using no receivers (trajectory filtering only), all selected transmitters
	 *	and the currently selected time span.
	 *
	 *	@synchronization	attemptShared on DOOR_TIMETRNS
	 */
	protected RenderContext createRenderContext()
	{
		if( !doc.bird.attemptShared( Session.DOOR_TIMETRNS, 250 )) return null;
		try {
			return new RenderContext( this, collEmpty, doc.selectedTransmitters.getAll(),
									  doc.timeline.getSelectionSpan(), doc.timeline.getRate() );
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIMETRNS );
		}
	}

	/**
	 *	Checks if the render context is still valid.
	 *
	 *	@return	<code>false</code> if the context became invalid
	 *			e.g. after a change in the selected time span
	 *
	 *	@synchronization	attemptShared on DOOR_TIMETRNS
	 */
	protected boolean isRenderContextValid( RenderContext context )
	{
		if( !doc.bird.attemptShared( Session.DOOR_TIMETRNS, 250 )) return false;
		try {
			return( doc.selectedTransmitters.getAll().equals( context.getTransmitters() ) &&
					doc.timeline.getSelectionSpan().equals( context.getTimeSpan() ) &&
					doc.timeline.getRate() == context.getSourceRate() );
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIMETRNS );
		}
	}

// ---------------- concrete methods ---------------- 

	/**
	 *	This implementation sets itself as
	 *	the consumer option in the context, then calls
	 *	<code>prod.producerBegin( context, source )</code>
	 *
	 *	@see	RenderContext#KEY_CONSUMER
	 */
	protected boolean invokeProducerBegin( ProcessingThread pt, RenderContext context,
										   RenderSource source, RenderPlugIn prod )
	throws IOException
	{
		context.setOption( RenderContext.KEY_CONSUMER, this );
		context.getModifiedOptions();   // clear state

		return prod.producerBegin( context, source );
	}
	
	/**
	 *	This implementation simply calls
	 *	<code>prod.producerCancel( context, source )</code>
	 */
	protected void invokeProducerCancel( ProcessingThread pt, RenderContext context,
									     RenderSource source, RenderPlugIn prod )
	throws IOException
	{
		prod.producerCancel( context, source );
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

// ---------------- RenderConsumer interface ---------------- 

		// --- finishing / consumer ---
		// XXX big problem:	begin/continue/finish overwrite don't allow
		//					intermitting read access. therefore we cannot
		//					begin consuming before all render input was
		//					read. this requires that the producer must create
		//					temp files in every case, even if it could
		//					produce inplace. we might try to use getTrackList()
		//					before the render loop, which won't(?) interfere
		//					with the concurrent mte writes.

	/**
	 *  Initializes the consumption process.
	 *  CRUCIAL: starting a multi call write process
	 *  on a MultirateTrackEditor forbids intermitting
	 *  read access. Therefore, when this method consumerBegin()
	 *  is called, all source requests must have been satisfied!
	 *  This is usually the case when the producer's producerRender()
	 *  method has been called for the last time, i.e. right before
	 *  producerFinished() is called. Thus, when looking at
	 *  the TimeWarpFilter or the VectorTransformFilter, we see
	 *  that all our consumer methods, begin, render and finish, are
	 *  invoked from inside their producerFinish() method.<br><br>
	 *  Alternatively we could have put the mte.beginOverwrite()
	 *  call inside consumerRender() but that's not so elegant.
	 */
	public boolean consumerBegin( RenderContext context, RenderSource source )
	throws IOException
	{
		Transmitter				trns;
		MultirateTrackEditor	mte;
		int						trnsIdx;
		ConsumerContext			consc;
		
		consc			= new ConsumerContext();
		consc.edit		= new SyncCompoundSessionObjEdit( this, doc, context.getTransmitters(), Transmitter.OWNER_TRAJ,
															  null, null, Session.DOOR_TIMETRNSMTE );
		consc.bs		= new BlendSpan[ source.numTrns ];
		consc.bc		= root.getBlending();
		context.setOption( KEY_CONSC, consc );

		for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
			if( !source.trajRequest[ trnsIdx ]) continue;
			
			trns				= (Transmitter) context.getTransmitters().get( trnsIdx );
			mte					= trns.getTrackEditor();
			consc.bs[ trnsIdx ]	= mte.beginOverwrite( context.getTimeSpan(), consc.bc, consc.edit );
		}
		
		return true;
	}

	/**
	 *	Finishes all multirate track editor write 
	 *	operations currently in progress. Closes
	 *	the compound edit.
	 */
	public boolean consumerFinish( RenderContext context, RenderSource source )
	throws IOException
	{
		Transmitter				trns;
		MultirateTrackEditor	mte;
		int						trnsIdx;
		ConsumerContext			consc   = (ConsumerContext) context.getOption( KEY_CONSC );

		for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
			if( !source.trajRequest[ trnsIdx ]) continue;

			trns				= (Transmitter) context.getTransmitters().get( trnsIdx );
			mte					= trns.getTrackEditor();
			mte.finishWrite( consc.bs[ trnsIdx], consc.edit );
		}

		consc.edit.end(); // fires doc.tc.modified()
		doc.getUndoManager().addEdit( consc.edit );
		return true;
	}

	/**
	 *	Writes a block of the transformed data back
	 *	to the transmitter trajectory tracks.
	 */
	public boolean consumerRender( RenderContext context, RenderSource source )
	throws IOException
	{
		ConsumerContext			consc   = (ConsumerContext) context.getOption( KEY_CONSC );
		Transmitter				trns;
		MultirateTrackEditor	mte;
		int						trnsIdx;

		for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
			if( !source.trajRequest[ trnsIdx ]) continue;

			trns				= (Transmitter) context.getTransmitters().get( trnsIdx );
			mte					= trns.getTrackEditor();
			if( consc.bs[ trnsIdx ] == null ) {
				context.getHost().showMessage( JOptionPane.ERROR_MESSAGE,
					AbstractApplication.getApplication().getResourceString( "renderEarlyConsume" ));
				return false;
			}
			mte.continueWrite( consc.bs[ trnsIdx], source.trajBlockBuf[ trnsIdx ],
							   source.blockBufOff, source.blockBufLen );
		}

		return true;
	}

	/**
	 *	Cancels the re-importing of transformed data.
	 *	Aborts and undos the compound edit.
	 */
	public void consumerCancel( RenderContext context, RenderSource source )
	throws IOException
	{
		ConsumerContext	consc   = (ConsumerContext) context.getOption( KEY_CONSC );

		if( consc != null && consc.edit != null ) {
			consc.edit.cancel();
			consc.edit = null;
		}
	}

// -------- ConsumerContext internal class --------
	private class ConsumerContext
	{
		SyncCompoundSessionObjEdit	edit;
		BlendContext					bc;
		BlendSpan[]						bs;			// for each trns
	}
}