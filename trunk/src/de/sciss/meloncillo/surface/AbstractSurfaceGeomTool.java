/*
 *  AbstractSurfaceGeomTool.java
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
 *		05-May-04   created
 *		31-Jul-04   commented
 *		15-Jan-05	moved to separate package
 */

package de.sciss.meloncillo.surface;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.common.BasicApplication;
import de.sciss.common.ProcessingThread;
import de.sciss.gui.ProgressComponent;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.edit.CompoundSessionObjEdit;
import de.sciss.meloncillo.gui.AbstractGeomTool;
import de.sciss.meloncillo.gui.MenuFactory;
import de.sciss.meloncillo.gui.VirtualSurface;
import de.sciss.meloncillo.io.AudioStake;
import de.sciss.meloncillo.io.AudioTrail;
import de.sciss.meloncillo.io.BlendContext;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.transmitter.Transmitter;

/**
 *  A basic class for implementing geometric surface
 *  tools like line, bezier curve or arc. In addition
 *  to the <code>AbstractGeomTool</code> superclass this
 *  puts in concrete terms the painting methods and
 *  the rendering process (<code>run</code> method).
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  
 *  @see		SurfacePane
 */
public abstract class AbstractSurfaceGeomTool
extends AbstractGeomTool
implements ProcessingThread.Client
{
	// --- rendering ---
	private ProcessingThread renderThread   = null;		// this is the thread responsible for rendering the finished gesture
	
	// --- shapes and drawing ---
	private static final Color	colrSolidLine   = SurfacePane.colrSelection;
	private static final Color	colrDottedLine  = SurfacePane.colrTextSelection;
	private static final Color	colrVelocity	= new Color( 0xFF, 0xFF, 0x00, 0x2F );
	private static final Stroke strkDottedLine  = SurfacePane.strkDottedLine;
	private static final Stroke strkLine		= SurfacePane.strkLine;
	private static final Shape	shpCrossHair	= SurfacePane.shpCrossHair;

	// --- misc ---
	private final Main		root;
	private final Session	doc;
	
	/**
	 *  Instantiates a new geometric surface tool.
	 *
	 *  @param  root		the application root
	 *  @param  doc			the session document
	 *  @param  s			the virtual surface used to perform
	 *						translations between the screen space
	 *						and the normalized virtual space.
	 *  @param  numCtrlPt   number of control points for this
	 *						tool (specified by the subclass of
	 *						this class), such as two for a line or
	 *						four for the bezier curve.
	 */
	public AbstractSurfaceGeomTool( Main root, Session doc, VirtualSurface s, int numCtrlPt )
	{
		super( s, numCtrlPt );
		
		this.root   = root;
		this.doc	= doc;
	}
	
	/**
	 *  Overrides the AbstractGeomTool method.
	 *  If the tool is being rendered, wait
	 *  until rendering is finished before
	 *  tool is dismissed.
	 */
	public void toolDismissed( Component c )
	{
		super.toolDismissed( c );

		// wait for rendering to be completed
		if( renderThread != null && renderThread.isRunning() ) {
			renderThread.sync();
			renderThread = null;
		}
	}
	
	/**
	 *  If gesture was successful, render the
	 *  output. This creates a new <code>ProcessingThread</code
	 *  which is using this surface tools as 
	 *  a <code>RunnableProcessing</code> instance, thus
	 *  invoking our <code>run</code> method.
	 *
	 *  @see				de.sciss.meloncillo.util.ProcessingThread
	 *  @synchronization	waitExclusive on DOOR_TIMETRNSMTE as
	 *						part of the processing thread's performance.
	 */
	protected void renderGesture( Point2D[] ctrlPoints )
	{
//		renderThread = new ProcessingThread( this, root, root, doc,
//		                         			AbstractApplication.getApplication().getResourceString( "toolWriteTransmitter" ),
//		                         			ctrlPoints, Session.DOOR_TIMETRNSMTE );
		final Span span		= doc.timeline.getSelectionSpan();
		final List collTrns	= doc.getSelectedTransmitters().getAll();
		if( span.isEmpty() || collTrns.isEmpty() ) return;

		final AbstractCompoundEdit edit;
		edit = new CompoundSessionObjEdit( this, collTrns, Transmitter.OWNER_TRAJ,
		                                   null, null, "Geometric Tool" );

		renderThread = new ProcessingThread( this, root,
		    AbstractApplication.getApplication().getResourceString( "toolWriteTransmitter" ));
		renderThread.putClientArg( "points", ctrlPoints );
		renderThread.putClientArg( "span", span );
		renderThread.putClientArg( "trns", collTrns );
		renderThread.putClientArg( "edit", edit );

		renderThread.start();
	}

	/**
	 *  Overrides the AbstractGeomTool method.
	 *  If the tool is being rendered, wait
	 *  until rendering is finished before
	 *  method returns.
	 */
	protected boolean initConcatenation( Point2D[] oldCtrlPoints )
	{
		boolean success = super.initConcatenation( oldCtrlPoints );

		if( success ) {
			// wait for rendering to be completed
			if( renderThread != null && renderThread.isRunning() ) {
				renderThread.sync();
				renderThread = null;
			}
			((MenuFactory) ((BasicApplication) AbstractApplication.getApplication()).getMenuFactory()).actionSelectionForward.perform();
		}
		return success;
	}

	/**
	 *  Implementation with
	 *  consistent <code>Surface</code> look-and-feel
	 */
	protected void paintShape( Graphics2D g2, Shape geomShape, Shape ctrlPtShape )
	{
		g2.setColor( colrSolidLine );
		g2.setStroke( strkLine );
		g2.draw( geomShape );
		g2.setColor( colrDottedLine );
		g2.setStroke( strkDottedLine );
		g2.draw( geomShape );
		if( ctrlPtShape != null) g2.fill( ctrlPtShape );
	}

	/**
	 *  Implementation with
	 *  consistent <code>Surface</code> look-and-feel
	 */
	protected void paintVelocityShape( Graphics2D g2, Shape geomShape, Shape ctrlPtShape )
	{
		g2.setColor( colrVelocity );
		g2.fill( geomShape );
		g2.setColor( colrDottedLine );
		if( ctrlPtShape != null) g2.fill( ctrlPtShape );
	}
	
	/**
	 *  Implementation with
	 *  consistent <code>Surface</code> look-and-feel
	 */
	protected Shape getCtrlPointShape()
	{
		return shpCrossHair;
	}

// -------- MouseListener interface ---------

	/**
	 *  Overrides the AbstractGeomTool method.
	 *  If the tool is being rendered, wait
	 *  until rendering is finished before
	 *  method calls the superclass's method
	 *  and returns.
	 *  <p>
	 *  cancelling the new drag would result in a loss of ergonomy
	 *  since the user would have to retry to press the button.
	 */
	public void mousePressed( MouseEvent e )
	{
		// if the last tool gesture is still rendering,
		// wait for the rendering to complete.
		if( renderThread != null && renderThread.isRunning() ) {
			renderThread.sync();
			renderThread = null;
		}

		e.getComponent().requestFocus();
		super.mousePressed( e );
	} // mousePressed( MouseEvent e )

// -------- RunnableProcessing interface ---------
	
	/**
	 *  Render the tool gesture
	 *  <p>
	 *  time warp is performed as follows:
	 *  <pre>
	 *  t*(t) = t^3 (v_start - 2v_ctrl + v_stop)/3 + t^2 (v_ctrl - v_start) + t (v_start)
	 *  where 0 <= t <= 1 and v_ctrl = 3 - (v_stop + v_start);
	 *  </pre>
	 *  <p>
	 *  If the selected time span is smaller than two samples
	 *  or the <code>initFunctionEvaluation</code> call returns false,
	 *  the method returns immediately. Otherwise an overwrite
	 *  action is performed on the <code>MultirateTrackEditor</code>s
	 *  of all selected transmitters, blockwise calling
	 *  <code>calcWarpedTime</code> and <code>evaluateFunction</code>.
	 *  The application's <code>BlendContext</code> is used if blending
	 *  is activated. The trajectory rendering is added as a new edit
	 *  to the undo manager. If an error occurs during the rendering,
	 *  partial edits are undone before the method returns.
	 *
	 *  @param  context		the hosting <code>ProcessingThread</code>
	 *  @param  argument	we pass the control points (<code>Point2D[]</code>) as
	 *						the processing argument.
	 */
	public int processRun( ProcessingThread context ) throws IOException
	{
		final Span						span		= (Span) context.getClientArg( "span" );
		final AbstractCompoundEdit		edit		= (AbstractCompoundEdit) context.getClientArg( "edit" ); 
		final List						collTrns	= (List) context.getClientArg( "trns" );
		final BlendContext				bc			= root.getBlending();
		final float[][]					srcBuf		= bc == null ? null : new float[ 2 ][ 4096 ];
		final float[][]					interpBuf;
		Transmitter						trns;
		AudioTrail						at;
		float[]							warpedTime;
		int								i, len;
		double							t_norm;
//		BlendSpan						bs;
		// interpLen entspricht 'T' in der Formel (Gesamtzeit), interpOff entspricht 't' (aktueller Zeitpunkt)
		long							start, interpOff, interpLen, progressLen;
		long							progress	= 0;
//		boolean							success		= false;
		AudioStake						as;

//		if( span.getLength() < 2 ) return DONE;
		if( !initFunctionEvaluation( (Point2D[]) context.getClientArg( "points" ))) return FAILED;

		interpLen		= span.getLength();
		warpedTime		= new float[(int) Math.min( interpLen, 4096 )];
		interpBuf		= new float[2][ warpedTime.length ];
		// '-1' because the last sample shall really equal the end point of the shape
		t_norm			= 1.0 / (interpLen - 1);

		progressLen		= interpLen*collTrns.size();

		try {
			for( i = 0; i < collTrns.size(); i++ ) {
				trns	= (Transmitter) collTrns.get( i );
				at		= trns.getAudioTrail();
				as		= at.alloc( span );
//				bs = at.beginOverwrite( span, bc, edit );
				for( start = span.getStart(), interpOff = 0; start < span.getStop();
					 start += len, interpOff += len ) {

					len = (int) Math.min( 4096, span.getStop() - start );
					calcWarpedTime( warpedTime, interpOff * t_norm, t_norm, len );
					evaluateFunction( warpedTime, interpBuf, len );
//					at.continueWrite( bs, interpBuf, 0, len );
					if( bc != null ) {
						at.readFrames( srcBuf, 0, new Span( start, start + len ));
						if( interpOff < bc.getLen() ) {	// EEE getLen?
							bc.blend( interpOff, srcBuf, 0, interpBuf, 0, interpBuf, 0, len );
						}
						if( interpLen - (interpOff + len) < bc.getLen() ) {	// EEE getLen?
							bc.blend( interpOff - (interpLen - bc.getLen()), interpBuf, 0, srcBuf, 0, interpBuf, 0, len );
						}
					}
					as.writeFrames( interpBuf, 0, new Span( start, start + len ));
					progress += len;
					context.setProgression( (float) progress / (float) progressLen );
				}
//				at.finishWrite( bs, edit );
				at.editBegin( edit );
				at.editClear( this, span, edit );
				at.editAdd( this, as, edit );
				at.editEnd( edit );
			} // for( i = 0; i < collTransmitters.size(); i++ )
			
//			edit.perform();
//			edit.end(); // fires doc.tc.modified()
//			doc.getUndoManager().addEdit( edit );
//			success = true;
			return DONE;
		}
		catch( IOException e1 ) {
//			edit.cancel();
			context.setException( e1 );
			return FAILED;
		}
	} // run()

	/**
	 *  Invoked by the <code>ProcessingThread</code> upon
	 *  processing completion. This implementation does not nothing
	 */
	public void processFinished( ProcessingThread context )
	{
		final AbstractCompoundEdit edit = (AbstractCompoundEdit) context.getClientArg( "edit" );
		
		if( context.getReturnCode() == ProgressComponent.DONE ) {
			edit.perform();
			edit.end(); // fires doc.tc.modified()
			doc.getUndoManager().addEdit( edit );
		} else {
			edit.cancel();
		}
	}

	public void processCancel( ProcessingThread context ) {}
}