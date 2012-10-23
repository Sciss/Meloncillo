/*
 *  AbstractGeomTool.java
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
 *		21-Aug-04	finishGesture small bugfix
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 *  A basic class for implementing geometric surface
 *  tools like line, bezier curve or arc. Provides
 *  common mechanisms for dragging points and
 *  painting shapes.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *  @todo		rotation of shapes is not yet implemented.
 */
public abstract class AbstractGeomTool
extends AbstractTool
implements KeyListener
{
	// --- drag-and-drop ---
	private static final int DND_NONE		= 0;	// gesture hasn't yet started or has been completed.
	private static final int DND_INIT		= 1;	// mouse button pressed to start a gesture
	private static final int DND_INITDRAG   = 2;	// the user has started dragging the initial shape
	private static final int DND_CTRL		= 3;	// ready to adjust control points
	private static final int DND_CTRLDRAG	= 4;	// the user is adjusting a control point
	private static final int DND_ROTATE		= 5;	// rotating the shape
	private static final int DND_VELOCITY	= 6;	// changing the velocity shape
	private static final int DND_VELODRAG	= 7;	// user adjusting a velocity ctrl point

	private MouseEvent		dndFirstEvent		= null;		// event from the initial mouse button press (dndState >= DND_INIT)
	private Rectangle2D		dndRecentRect		= null;		// for graphics rendering update
	private Shape			dndBasicShape		= null;		// the shape during DND_INITDRAG
	private Shape			dndControlledShape  = null;		// the shape since DND_CTRL
	private Shape			dndCtrlPointsShape;				// the united shape of all control points
//	private AffineTransform	dndRotation			= null;		// rotation transform
	private final Point2D[]	dndCtrlPoints;					// all control points of the controlled shape
	private int				dndCtrlPointIdx;				// index in dndCtrlPoints describing the point dragged (DND_CTRLDRAG)
//	private Point2D			dndCtrlOldPoint;				// backup of dragged control point
	private int				dndState			= DND_NONE; // describes the current dnd situation (see constants above)

	// --- velocity ---
	private static final int	VELO_POINTS			= 72;		// number of velocity shape samples
	private static final double	VELO_NORM			= 0.05;		// XXX should be left to subclasses
	private float[][]			veloSamples			= null;		// array [2][VELO_POINTS]
	private double[]			veloAngles			= null;		// array size VELO_POINTS
	private Shape				veloShape			= null;
	private Shape				veloCtrlPointsShape;			// the united shape of all velo ctrl points
	private Rectangle2D			veloRecentRect		= null;		// union of dndControlledShape and dndCtrlPointsShape
	private Point2D[]			veloCtrlPoints		= new Point2D[ 4 ];
	private int					veloCtrlPointIdx;				// index in veloCtrlPoints describing the point dragged (DND_VELODRAG)
	private Point2D				veloCtrlOldPoint;				// backup of dragged control point
	private Point2D				veloCenterPoint;				// begin or end of the controlled shape
	private double				veloStart, veloStop;

	// --- misc ---
	private final VirtualSurface  s;

	// --- shapes and drawing ---
	// the basic shape's outline will be stroked to create an area
	// and detect nearby mouse clicks
 	private static final BasicStroke snapStroke = new BasicStroke( 7.5f );
	
	/**
	 *  Creates a new instance of this tool.
	 *
	 *  @param  s			a virtual surface providing translations
	 *						between the virtual space and a screen space.
	 *						all shapes and control points are placed
	 *						in virtual space. The surface is also 
	 *						used for snapping mouse points.
	 *
	 *  @param  numCtrlPt   the number of points controlling the shape,
	 *						including the two points required for
	 *						drawing the initial basic shape!
	 *						E.g. for the Bezier, numCtrlPt = 4
	 */
	public AbstractGeomTool( VirtualSurface s, int numCtrlPt )
	{
		super();
		
		this.s			= s;
		dndCtrlPoints   = new Point2D[ numCtrlPt ];
	}
	
	/**
	 *  @return		the <code>VirtualSurface</code> used
	 *				in the constructor
	 */
	protected VirtualSurface getSurface()
	{
		return s;
	}
	
	/**
	 *  Invokes the <code>AbstractTool</code>'s method
	 *  and additionally installs a <code>KeyListener</code> on
	 *  the <code>Component</code>.
	 */
	public void toolAcquired( Component c )
	{
		super.toolAcquired( c );
		c.addKeyListener( this );

//System.err.println( "acquired : "+this.getClass().getName() );
		// essential inits already in finishGesture()
	}

	/**
	 *  Completes or cancels the tool gesture
	 *  befoer invoking the <code>AbstractTool</code>'s method.
	 *  Finally removes the <code>KeyListener</code> from
	 *  the <code>Component</code>.
	 */
	public void toolDismissed( Component c )
	{
		finishGesture( false );
	
		super.toolDismissed( c );
		c.removeKeyListener( this );
	}
	
	/**
	 *  This method is called at the end of a
	 *  tool gesture, i.e. when the user completes
	 *  the tool gesture or the tool was dismissed.
	 *
	 *  @param  success		whether the gesture was
	 *						successfully completed. in this
	 *						case (<code>true</code>),
	 *						the tool's <code>renderGesture</code>
	 *						method will be invoked.
	 *  @see	#renderGesture( Point2D[] )
	 *  @see	#toolDismissed( Component )
	 *  @synchronization	the method is called in the event thread
	 */
	protected void finishGesture( boolean success )
	{
		if( dndState >= DND_INITDRAG ) {
			if( dndState == DND_VELOCITY ) {
				dndRecentRect = dndRecentRect.createUnion( veloRecentRect );
			}
			efficientRepaint( dndRecentRect, null );
			dndRecentRect   = null;
			veloSamples		= null;
			veloAngles		= null;
			veloShape		= null;
			if( success ) {
				renderGesture( dndCtrlPoints );
			}
		}
		dndState = DND_NONE;
	}
	
	/**
	 *  Invoked when a gesture has been successfully completed.
	 *  The subclass should render the tool gesture according
	 *  to the provided control points.
	 *
	 *  @param  ctrlPoint   the complete array of control points
	 *						at the end of the tool gesture
	 *  @see	#finishGesture( boolean )
	 *  @synchronization	the method is called in the event thread
	 */
	protected abstract void renderGesture( Point2D[] ctrlPoint );

	/**
	 *  A basic implementation of the abstract method
	 *  that will call specific sub methods depending on
	 *  the state of the dragging gesture:
	 *  <ul>
	 *  <li>in the first (initial) drag gesture and
	 *  the normal control point adjusting gestures,
	 *  the <code>paintShape</code> method will be called.</li>
	 *  <li>in the velocity adjusting phase, both
	 *  <code>paintShape</code> and <code>paintVelocityShape</code>
	 *  are called.</li>
	 *  <li>if no current drag is ongoing, this method does nothing.</li>
	 *  </ul>
	 *
	 *  @see	#paintShape( Graphics2D, Shape, Shape )
	 *  @see	#paintVelocityShape( Graphics2D, Shape, Shape )
	 */
	public void paintOnTop( Graphics2D g2 )
	{
		switch( dndState ) {
		case DND_NONE:
		case DND_INIT:
			return;

		case DND_INITDRAG:  // draw basic shape
			paintShape( g2, dndBasicShape, dndCtrlPointsShape );
			break;

		case DND_VELOCITY:
		case DND_VELODRAG:
			paintVelocityShape( g2, veloShape, veloCtrlPointsShape );
			paintShape( g2, dndControlledShape, null );
			break;
			
		default:			// all others draw the controlled shape
			paintShape( g2, dndControlledShape, dndCtrlPointsShape );
			break;
		}
	}

	/**
	 *  The actual shape painting method.
	 *  The particular implementation is
	 *  up to the subclasses.
	 *  
	 *  @param  g2			the <code>Graphics2D</code> context to render onto.
	 *  @param  geomShape   the geometric shape of the tool gesture
	 *						(use the graphics' <code>draw</code> method)
	 *  @param  ctrlPtShape the united shape of all control points
	 *						(use the graphics' <code>fill</code> method).
	 *						this may be <code>null</code> in which case no control
	 *						point shape is to be drawn!
	 *  @see	java.awt.Graphics2D#draw( Shape )
	 *  @see	java.awt.Graphics2D#fill( Shape )
	 */
	protected abstract void paintShape( Graphics2D g2, Shape geomShape, Shape ctrlPtShape );

	/**
	 *  The velocity shape painting method.
	 *  The default implementation does nothing,
	 *  so it needn't be overriden if the
	 *  concrete tool cannot accelerate.
	 *  
	 *  @param  g2			the <code>Graphics2D</code> context to render onto.
	 *  @param  geomShape   the geometric shape of the velocity representation
	 *						(use the graphics' <code>draw</code> method)
	 *  @param  ctrlPtShape the united shape of all velocity control points
	 *						(use the graphics' <code>fill</code> method).
	 *						this may be <code>null</code> in which case no control
	 *						point shape is to be drawn!
	 *  @see	java.awt.Graphics2D#draw( Shape )
	 *  @see	java.awt.Graphics2D#fill( Shape )
	 */
	protected void paintVelocityShape( Graphics2D g2, Shape geomShape, Shape ctrlPtShape )
	{
	}
	
	/**
	 *  Subclasses can override this
	 *  to acknowledge the permission
	 *  of rotating the shape. This
	 *  method returns false by default.
	 *
	 *  @return whether the tool supports shape rotation
	 *			or not. defaults to <code>false</code>.
	 */
	protected boolean canRotate()
	{
		return false;
	}

	/**
	 *  Subclasses can override this
	 *  to acknowledge the permission
	 *  of concatening a gesture by
	 *  a successive gesture.
	 *  returns true by default.
	 *
	 *  @return whether the tool supports concatenation
	 *			of successive gestures. defaults to <code>true</code>.
	 */
	protected boolean canConcatenate()
	{
		return true;
	}
	
	/**
	 *  Subclasses can override this
	 *  to acknowledge the permission
	 *  of accelerating/deccelerating
	 *  the trace of their shape in
	 *  time (i.e. warping the time
	 *  in the rendering process).
	 *  <p>
	 *  Note that this
	 *  method returns <code>true</code> by default,
	 *  which implies that <code>initFunctionEvaluation</code>
	 *  and <code>evaluateFunction</code> might get called.
	 *  Therefore if the tool can accelerate it must override
	 *  the latter two methods, if it can't, it must
	 *  override this method and return <code>false</code>.
	 *
	 *  @return whether the tool supports dynamic
	 *			velocity or not. defaults to <code>true</code>.
	 *  @see	#initFunctionEvaluation( Point2D[] )
	 *  @see	#evaluateFunction( float[], float[][], int )
	 */
	protected boolean canAccelerate()
	{
		return true;
	}

	/**
	 *  This gets called before calling
	 *  <code>evaluateFunction</code>.
	 *  It permits the concrete tool to initialize
	 *  common variables used during the rendering.
	 *
	 *  @param  ctrlPoints  the points used for evaluating
	 *						the function
	 *  @return				<code>true</code> if rendering is possible,
	 *						<code>false</code> if the shape is invalid for rendering
	 *						(e.g. the length is zero). Returns <code>false</code>
	 *						by default!
	 *  @see	#evaluateFunction( float[], float[][], int )
	 */
	protected boolean initFunctionEvaluation( Point2D[] ctrlPoints )
	{
		return false;
	}

	/**
	 *  The subclass has to evaluate the
	 *  function describing the geometric shape along
	 *  the provided points in time, where
	 *  time is normalized between 0 and 1. This
	 *  gets never called if <code>initFunctionEvaluation</code>
	 *  returns <code>false</code>. By default this function does
	 *  nothing!
	 *
	 *  @param  warpedTime  points in time ranging from
	 *						zero (very beginning of the function)
	 *						to one (very ending of the function).
	 *						They aren't neccessarily linearly spaced,
	 *						but they are guaranteed to increase from
	 *						one index to the next.
	 *  @param  interpBuf   this method should place the results of
	 *						the evaluation of the function along
	 *						the time provided points in this buffer,
	 *						such that <code>interpBuf[0][i]</code> =
	 *						<code><var>function_x</var>( warpedTime[i] )</code>
	 *						and <code>interpBuf[1][i]</code> =
	 *						<code><var>function_y</var>( warpedTime[i] )</code>.
	 *  @param  len			number of provided time points / buffer points to calculate.
	 *  @see	#initFunctionEvaluation( Point2D[] )
	 */
	protected void evaluateFunction( float[] warpedTime, float[][] interpBuf, int len )
	{
	}

	/**
	 *  After the completion of the inital
	 *  drag gesture subclasses must provide
	 *  initial values for all remaining
	 *  control points (array index >= 2).
	 *  If the tool only has the two initial
	 *  control points, it can just return
	 *  from this method without doing anything.
	 *
	 *  @param  ctrlPoints  array to be filled except
	 *						for elements 0 and 1 which
	 *						contain the inital drag's
	 *						control points.
	 */
	protected abstract void initControlPoints( Point2D[] ctrlPoints );

	/**
	 *  This gets called if the user wishes
	 *  to concatenate a gesture and
	 *  <code>canConcatenate()</code> returns <code>true</code>.
	 *  Subclasses that return <code>false</code>
	 *  from <code>canConcatenate()</code> can ignore this
	 *  method, otherwise they should read the
	 *  old control values and calculate new ones.
	 *  By default this method takes <code>oldCtrlPoints[1]</code>
	 *  and puts it into <code>oldCtrlPoints[0]</code>.
	 *  This is also the place where changes
	 *  in the timeline selection can take place.
	 *
	 *  @param  oldCtrlPoints   control points of the recent gesture
	 *							(read and replace by new ones)
	 *  @return					<code>true</code>, if concat can be done.
	 *							<code>false</code>, if concat fails for
	 *							some reason. Returns <code>true</code>
	 *							by default.
	 *  @todo   concatenation should be possible across
	 *			different tools!
	 */
	protected boolean initConcatenation( Point2D[] oldCtrlPoints )
	{
		oldCtrlPoints[0] = oldCtrlPoints[1];
		return true;
	}

	/**
	 *  During the initial drag gesture,
	 *  updates the basic shape, using the
	 *  first two control points.
	 *
	 *  @param  ctrlPoints  array whose elements 0 and 1
	 *						describe the initial drag's
	 *						control points
	 *  @return the <code>Shape</code> that describes
	 *			the resulting geometric form of the initial drag.
	 */
	protected abstract Shape createBasicShape( Point2D[] ctrlPoints);

	/**
	 *  During the control point adjustment phase,
	 *  updates the controlled shape, using all of
	 *  the provided control points.
	 *  This routine may savely adjust (overwrite) the
	 *  control points if necessary.
	 *
	 *  @param  ctrlPoints  array containing all the control
	 *						points for the final shape.
	 *  @return the <code>Shape</code> that describes
	 *			the resulting geometric form of the drag.
	 */
	protected abstract Shape createControlledShape( Point2D[] ctrlPoints );

	/*
	 *  efficient recalcuation / repainting of the tool's component : if the two clips intersect,
	 *  recalc the union and paint it if its area is smaller than the sum of the separate
	 *  clips. if not, repaint each clip separately
	 *
	 *  @param  clipRect		first clipping rectangle in virtual coords, may be null.
	 *  @param  clipRect2		second clipping rectangle in virtual coords, may be null.
	 */
	private void efficientRepaint( Rectangle2D clipRect, Rectangle2D clipRect2 )
	{
		Rectangle2D clipRect3;
		Rectangle   repaintRect;

		if( clipRect != null ) {
			if( clipRect2 != null ) {
				clipRect3 = clipRect.createUnion( clipRect2 );
				if( clipRect3.getWidth() * clipRect3.getHeight() <
					(clipRect.getWidth() * clipRect.getHeight() + clipRect2.getWidth() * clipRect2.getHeight()) ) {
					repaintRect = s.virtualToScreenClip( clipRect3 );
					getComponent().repaint( repaintRect.x, repaintRect.y, repaintRect.width, repaintRect.height );
				} else {
					repaintRect = s.virtualToScreenClip( clipRect3 );
					getComponent().repaint( repaintRect.x, repaintRect.y, repaintRect.width, repaintRect.height );
					repaintRect = s.virtualToScreenClip( clipRect2 );
					getComponent().repaint( repaintRect.x, repaintRect.y, repaintRect.width, repaintRect.height );
				}
			} else {
				repaintRect = s.virtualToScreenClip( clipRect );
				getComponent().repaint( repaintRect.x, repaintRect.y, repaintRect.width, repaintRect.height );
			}
		} else if( clipRect2 != null ) {
			repaintRect = s.virtualToScreenClip( clipRect2 );
			getComponent().repaint( repaintRect.x, repaintRect.y, repaintRect.width, repaintRect.height );
		}
	}

	/**
	 *  Subclasses must provide
	 *  a shape object that be used
	 *  as a template for drawing
	 *  the control points.
	 *
	 *  @return prototype <code>Shape</code> which
	 *			can be used to draw the drag's
	 *			control points when translated
	 *			properly. Thus, the virtual point
	 *			(0, 0) of the returned shape should
	 *			correspond to the Shape's center.
	 */
	protected abstract Shape getCtrlPointShape();

	/*
	 *  Calculate the united shape
	 *  of all control points whose
	 *  index is between 0 and numCtrlPt-1
	 */
	private Shape calcCtrlPointsShape( Point2D[] ctrlPoints, int numCtrlPt )
	{
		AffineTransform at				= new AffineTransform();
		Shape			shpCtrl			= getCtrlPointShape();
		GeneralPath		shpCtrlPoints	= new GeneralPath();
	
		for( int i = 0; i < numCtrlPt; i++ ) {
			at.setToTranslation( ctrlPoints[i].getX(), ctrlPoints[i].getY() );
			shpCtrlPoints.append( at.createTransformedShape( shpCtrl ), false );
		}
		return shpCtrlPoints;
	}

	/*
	 *  Initialize the veloctiy
	 *  adjustment mode. Returns false if
	 *  this fails (e.g. if initFunctionEvaluation()
	 *  returns false), true on success.
	 *  Calculates samples along the velocity
	 *  shape using VELO_POINTS number of samples
	 *  and invoking the evaluateFunction() method.
	 */
	private boolean initVelocityMode()
	{
		if( !initFunctionEvaluation( dndCtrlPoints )) return false;

		int			numEval = VELO_POINTS * 3;
		float[]		time	= new float[ numEval ];
		float[][]   eval	= new float[ 2 ][ numEval ];
		int			i, j;
		float		f1;
		float		weight  = 1.0f / (float) (numEval - 3);
		
		for( i = 0; i < numEval; ) {
			f1			= (float) i * weight;
			time[i++]   = f1 - 1.0e-3f;
			time[i++]   = f1;
			time[i++]   = f1 + 1.0e-3f;
		}
		time[0]			= time[1];
		time[numEval-1] = time[numEval-2];
		
		evaluateFunction( time, eval, numEval );
		
		veloSamples		= new float[ 2 ][ VELO_POINTS ];
		veloAngles		= new double[ VELO_POINTS ];
		
		for( i = 1, j = 0; j < VELO_POINTS; j++, i += 3 ) {
			veloSamples[0][j]   = eval[0][i];
			veloSamples[1][j]   = eval[1][i];
			veloAngles[j]		= Math.atan2( eval[1][i+1] - eval[1][i-1], eval[0][i+1] - eval[0][i-1] );
		}
		return true;
	}
	
	/*
	 *  Fills the passed array of size 4
	 *  with the control points for adjusting the
	 *  velocity shape; these points are at the
	 *  four corners of the shape, using the
	 *  precalcuated veloSamples[] array
	 *  (calculated in initVelocityMode()).
	 */
	private void initVeloCtrlPoints( Point2D[] ctrlPoints )
	{
		int		i;
		double  v;
	
		i   = 0;
		v   = veloStart * VELO_NORM;
		ctrlPoints[0] = new Point2D.Double( veloSamples[0][i] + Math.sin( veloAngles[i] ) * v,
											veloSamples[1][i] - Math.cos( veloAngles[i] ) * v );
		ctrlPoints[1] = new Point2D.Double( veloSamples[0][i] - Math.sin( veloAngles[i] ) * v,
											veloSamples[1][i] + Math.cos( veloAngles[i] ) * v );
		i   = VELO_POINTS - 1;
		v   = veloStop * VELO_NORM;
		ctrlPoints[2] = new Point2D.Double( veloSamples[0][i] + Math.sin( veloAngles[i] ) * v,
											veloSamples[1][i] - Math.cos( veloAngles[i] ) * v );
		ctrlPoints[3] = new Point2D.Double( veloSamples[0][i] - Math.sin( veloAngles[i] ) * v,
											veloSamples[1][i] + Math.cos( veloAngles[i] ) * v );
	}

	/*
	 *  Calculates a Java2D shape from
	 *  the provided velocity control points,
	 *  using the precalced veloSamples[] array.
	 *  The shape branches orthogonally to the
	 *  left and right of the tool's shape,
	 *  where the farest distance from the tool shape
	 *  is given by VELO_NORM and the shape
	 *  has two symmetrical wings to the left and
	 *  right of the tool's shape. The farer
	 *  the distance, the higher the velocity.
	 *  A quadratic function of the time is
	 *  used to calculate the shape, which is
	 *  taken from Java2D's quadratic curve.
	 *  See java.awt.geom.PathIterator#SEG_QUADTO .
	 */
	private Shape createVelocityShape( Point2D[] ctrlPoints )
	{
		int			i;
		double		v_ctrl, v_coeff1, v_coeff2, v_coeff3, t, t_norm, v, v_start, v_stop;
		GeneralPath shape = new GeneralPath( GeneralPath.WIND_NON_ZERO, (VELO_POINTS << 2) + 2 );

		v_start		= ctrlPoints[0].distance( ctrlPoints[1] ) / (2 * VELO_NORM);
		v_stop		= ctrlPoints[2].distance( ctrlPoints[3] ) / (2 * VELO_NORM);
		t_norm		= 1.0 / (VELO_POINTS - 1);
		v_ctrl		= 3.0 - v_stop - v_start;
		v_coeff1	= VELO_NORM * (v_start - 2 * v_ctrl + v_stop);
		v_coeff2	= VELO_NORM * (2 * (v_ctrl - v_start));
		v_coeff3	= VELO_NORM * (v_start);

		// v(t) = t^2 (v0 - 2v1 + vT) + t (-2v0 + 2v1) + v0
	
		// left wing
		i = 0;
		t = i * t_norm;
		v = t*t * v_coeff1 + t * v_coeff2 + v_coeff3;
		shape.moveTo( veloSamples[0][i] + (float) (Math.sin( veloAngles[i] ) * v),
					  veloSamples[1][i] - (float) (Math.cos( veloAngles[i] ) * v) );
		for( i++; i < VELO_POINTS; i++ ) {
			t = i * t_norm;
			v = t*t * v_coeff1 + t * v_coeff2 + v_coeff3;
			shape.lineTo( veloSamples[0][i] + (float) (Math.sin( veloAngles[i] ) * v),
						  veloSamples[1][i] - (float) (Math.cos( veloAngles[i] ) * v) );
		}

		// right wing
		for( i--; i >= 0; i-- ) {
			t = i * t_norm;
			v = t*t * v_coeff1 + t * v_coeff2 + v_coeff3;
			shape.lineTo( veloSamples[0][i] - (float) (Math.sin( veloAngles[i] ) * v),
						  veloSamples[1][i] + (float) (Math.cos( veloAngles[i] ) * v) );
		}
		
		shape.closePath();
		
		return shape;
	}

	/**
	 *  Calculates warped time samples for rendering accelerated shapes.
	 *  This method uses the start and stop velocity from the user's
	 *  drag to calculate time using a cubic function described in
	 *  Java2D's <code>PathIterator</code>.
	 *
	 *  @param  time		buffer of length 'len' in which this method stores
	 *						it's result. buffer offset is zero.
	 *  @param  startTime   time tag of first sample to calculated, in
	 *						normalized time space (0...1)
	 *  @param  t_norm		the period of one non-warped sample, i.e.
	 *						<code>(plannedStopTime - startTime) / (len - 1)</code>
	 *  @param  len			number of samples to calculate
	 *  @see	java.awt.geom.PathIterator#SEG_CUBICTO
	 */
	protected final void calcWarpedTime( float[] time, double startTime, double t_norm, int len )
	{
		double	v_coeff1, v_coeff2, v_coeff3, v_ctrl, t, tt, ttt;
		int		i;
		
		v_ctrl			= 3.0 - veloStop - veloStart;
		v_coeff1		= (veloStart - 2 * v_ctrl + veloStop)/3;
		v_coeff2		= v_ctrl - veloStart;
		v_coeff3		= veloStart;

		for( i = 0; i < len; i++ ) {
			t		= startTime + i * t_norm;
			tt		= t*t;
			ttt		= tt*t;
			time[i] = (float) (ttt * v_coeff1 + tt * v_coeff2 + t * v_coeff3);
		}
	}

// -------- MouseListener interface ---------

	/**
	 *  Called when the user presses the mouse button
	 *  on the tool's component. Action depends on the
	 *  internal drag state:
	 *  <ul>
	 *  <li>When the drag gesture hasn' yet begun,
	 *  the mouse point is possible snapped to nearby
	 *  objects and considered a potential drag start.</li>
	 *  <li>When the initial drag has been made,
	 *  the mouse point is possible snapped to nearby
	 *  control points and considered a potential control
	 *  point adjustment start.</li>
	 *  <li>when a double click in control point adjument
	 *  mode is made, the velocity adjustment mode is
	 *  entered.</li>
	 *  <li>When the user entered velocity mode,
	 *  the mouse point is possible snapped to nearby
	 *  velocity control points and considered a potential
	 *  veloctiy adjustment start.</li>
	 *  <li>When an intial concatenation drag has started,
	 *  nothing is done, because the concatenation's initial
	 *  drag will be terminated by the following
	 *  <code>mouseReleased</code> event.</li>
	 *  </ul>
	 */
	public void mousePressed( MouseEvent e )
	{
		int			i, j;
		double		d1, d2;
		Point		mousePt;
		Rectangle2D dndCurrentRect;
	
		switch( dndState ) {
		case DND_NONE:
			dndFirstEvent   = e;
			dndState		= DND_INIT;
			dndCtrlPoints[0]= s.screenToVirtual( s.snap( dndFirstEvent.getPoint(), false ));
			break;
			
		case DND_CTRL:
			// check for velocity mode
			if( e.getClickCount() == 2 && canAccelerate() ) {
				if( initVelocityMode() ) {
					initVeloCtrlPoints( veloCtrlPoints );
					dndState			= DND_VELOCITY;
					veloShape			= createVelocityShape( veloCtrlPoints );
					veloCtrlPointsShape = calcCtrlPointsShape( veloCtrlPoints, veloCtrlPoints.length );
					dndCurrentRect		= veloShape.getBounds2D().createUnion( veloCtrlPointsShape.getBounds2D() );
					efficientRepaint( dndRecentRect, dndCurrentRect );
					veloRecentRect		= dndRecentRect;
					dndRecentRect		= dndCurrentRect;
					break;
				}
			}
			// figure out if a control point has been hit
			j		= -1;
			d1		= 25.1;		// control points must have a squared distance less than this
			mousePt = new Point( e.getX(), e.getY() );
			for( i = 0; i < dndCtrlPoints.length; i++ ) {
				d2 = s.virtualToScreen( dndCtrlPoints[i] ).distanceSq( mousePt );
				if( d2 < d1 ) {
					d1  = d2;
					j   = i;
				}
			}
			if( j >= 0 ) {   // found a nearby control point
//				dndCtrlOldPoint = dndCtrlPoints[j]; // save old point in case the drag is cancelled
				dndCtrlPointIdx = j;
				dndState		= DND_CTRLDRAG;
			} else if( canRotate() ) {  // detect if mouse is over outline shape and thus could start a rotation gesture
				if( snapStroke.createStrokedShape( s.virtualToScreen( dndBasicShape )).contains( mousePt )) {
					// XXX
					System.err.println( "begin rotation gesture" );
				}
			}
			break;

		case DND_INITDRAG:  // ending concatening first drag, handled by mouseReleased()
			break;
		
		case DND_VELOCITY:
			// figure out if a control point has been hit
			j		= -1;
			d1		= 25.1;		// control points must have a squared distance less than this
			mousePt = new Point( e.getX(), e.getY() );
			for( i = 0; i < veloCtrlPoints.length; i++ ) {
				d2 = s.virtualToScreen( veloCtrlPoints[i] ).distanceSq( mousePt );
				if( d2 < d1 ) {
					d1  = d2;
					j   = i;
				}
			}
			if( j >= 0 ) {   // found a nearby control point
				veloCtrlOldPoint	= veloCtrlPoints[j]; // save old point in case the drag is cancelled
				veloCtrlPointIdx	= j;
				j					= veloCtrlPointIdx < 2 ? 0 : VELO_POINTS - 1;
				veloCenterPoint		= new Point2D.Double( veloSamples[0][j], veloSamples[1][j] );
				dndState			= DND_VELODRAG;
			}
			break;

		default:	// not possible to get here
			// we do not throw an AssertionError since this is not a critical situation
			System.err.println( "invalid drag state (mousePressed) : " + dndState );
			finishGesture( false );
			break;
		}
	} // mousePressed( MouseEvent e )

	/**
	 *  Called when the user releases the mouse button
	 *  from the tool's component. Action depends on the
	 *  internal drag state:
	 *  <ul>
	 *  <li>When the drag gesture hasn' yet begun,
	 *  any potential drag is cancelled and the
	 *  internal state is reset.</li>
	 *  <li>When the initial drag has begun,
	 *  the control point adjustment phase is entered.</li>
	 *  </ul>
	 */
	public void mouseReleased( MouseEvent e )
	{
		Rectangle2D dndCurrentRect;

		switch( dndState ) {
		case DND_NONE:  // no valid gesture
		case DND_INIT:  // didn't really start the basic drag
			finishGesture( false );
			break;

		case DND_INITDRAG:
			initControlPoints( dndCtrlPoints );
			dndState			= DND_CTRL;		// advance to control adjusting state
			dndControlledShape	= createControlledShape( dndCtrlPoints );
			dndCtrlPointsShape  = calcCtrlPointsShape( dndCtrlPoints, dndCtrlPoints.length );
			dndCurrentRect		= dndControlledShape.getBounds2D().createUnion( dndCtrlPointsShape.getBounds2D() );
			efficientRepaint( dndRecentRect, dndCurrentRect );
			dndRecentRect		= dndCurrentRect;
			veloStart			= 1.0;
			veloStop			= 1.0;
			break;

		case DND_VELODRAG:	// velocity drag complete, recalc velo values
			veloStart			= veloCtrlPoints[0].distance( veloCtrlPoints[1] ) / (2 * VELO_NORM);
			veloStop			= veloCtrlPoints[2].distance( veloCtrlPoints[3] ) / (2 * VELO_NORM);
			dndState			= DND_VELOCITY;
			break;
		
		case DND_CTRLDRAG:	// control drag complete. nothing special to do
		case DND_ROTATE:	// rotation drag complete. nothing special to do
			dndState			= DND_CTRL;		// back to control adjusting state
			break;

		case DND_VELOCITY:	// didn't really start the velocity ctrl drag
		case DND_CTRL:		// didn't really start the control drag
			break;

		default:	// not possible to get here
			assert false : dndState;
			break;
		}
	} // mouseReleased( MouseEvent e )

	/**
	 *  Mouse clicks are not analyzed at the moment.
	 */
	public void mouseClicked( MouseEvent e ) {}

	/**
	 *  Entering the tool's component is not analyzed at the moment.
	 */
	public void mouseEntered( MouseEvent e ) {}

	/**
	 *  Exiting the tool's component is not analyzed at the moment.
	 */
	public void mouseExited( MouseEvent e ) {}

// -------- MouseMotionListener interface ---------

	/**
	 *  Called when the user dragges the mouse pointer
	 *  on the tool's component. Action depends on the
	 *  internal drag state:
	 *  <ul>
	 *  <li>control points drags are performed for initial,
	 *  control point adjusting and velocity adjusting phase.
	 *  for initial phase, drag is only performed, if the
	 *  mouse was moved reasonably far away from the
	 *  original mouse-press location to avoid accidental
	 *  drags.</li>
	 *  </ul>
	 */
	public void mouseDragged( MouseEvent e )
	{
		Rectangle2D dndCurrentRect;
	
		switch( dndState ) {
		case DND_INIT:  // check mouse distance from drag init point
			if( e.getPoint().distanceSq( dndFirstEvent.getPoint() ) <= 25.0 ) return;
			dndState			= DND_INITDRAG;
			dndRecentRect		= null;
			// THRU
		case DND_INITDRAG:
			dndCtrlPoints[1]	= s.screenToVirtual( s.snap( e.getPoint(), false ));
			dndBasicShape		= createBasicShape( dndCtrlPoints );
			dndCtrlPointsShape  = calcCtrlPointsShape( dndCtrlPoints, 2 );
			dndCurrentRect		= dndBasicShape.getBounds2D().createUnion( dndCtrlPointsShape.getBounds2D() );
			efficientRepaint( dndRecentRect, dndCurrentRect );
			dndRecentRect		= dndCurrentRect;
			break;

		case DND_CTRLDRAG:
			dndCtrlPoints[ dndCtrlPointIdx ] = s.screenToVirtual( s.snap( e.getPoint(), false ));
			dndControlledShape	= createControlledShape( dndCtrlPoints );
			dndCtrlPointsShape  = calcCtrlPointsShape( dndCtrlPoints, dndCtrlPoints.length );
			dndCurrentRect		= dndControlledShape.getBounds2D().createUnion( dndCtrlPointsShape.getBounds2D() );
			efficientRepaint( dndRecentRect, dndCurrentRect );
			dndRecentRect		= dndCurrentRect;
			break;

		case DND_VELODRAG:
			int		mirroir			= veloCtrlPointIdx ^ 1;
			Line2D  hundertwasser   = new Line2D.Double( veloCenterPoint, veloCtrlOldPoint );
			veloCtrlPoints[ veloCtrlPointIdx ]  = GraphicsUtil.projectPointOntoLine(
													s.screenToVirtual( e.getPoint() ), hundertwasser );
			veloCtrlPoints[ mirroir ].setLocation( 2 * veloCenterPoint.getX() - veloCtrlPoints[ veloCtrlPointIdx ].getX(),
												   2 * veloCenterPoint.getY() - veloCtrlPoints[ veloCtrlPointIdx ].getY() );
			veloShape				= createVelocityShape( veloCtrlPoints );
			veloCtrlPointsShape		= calcCtrlPointsShape( veloCtrlPoints, veloCtrlPoints.length );
			dndCurrentRect			= veloShape.getBounds2D().createUnion( veloCtrlPointsShape.getBounds2D() );
			efficientRepaint( dndRecentRect, dndCurrentRect );
			dndRecentRect			= dndCurrentRect;
			break;
			
		case DND_ROTATE:
			// XXX
			break;
		
		case DND_VELOCITY:
		case DND_CTRL:  // didn't find a control point in mousePressed(), ignore
			break;
				
		default:	// not possible to get here
			// we do not throw an AssertionError since this is not a critical situation
			System.err.println( "invalid drag state (mouseDragged) : " + dndState );
			finishGesture( false );
			break;
		}
	} // mouseDragged( MouseEvent e )

	/**
	 *  In concatenation mode, a mouse move
	 *  can occur in a drag gesture, because
	 *  the mouse button was released at the end
	 *  of the previous gesture. In this case
	 *  the mouse move is handled like a mouse
	 *  drag.
	 */
	public void mouseMoved( MouseEvent e )
	{
		if( dndState == DND_INITDRAG ) mouseDragged( e );   // forward concatening first drag
	}

// -------- KeyListener interface ---------
	
	/**
	 *  Because tool gestures are comprised of
	 *  several stages, it was decided to use
	 *  a non-mouse-based means of ending the
	 *  gesture. Actions are taking by hitting
	 *  specific keyboard keys:
	 *  <ul>
	 *  <li>The enter or return key finishes a
	 *  gesture and renders the results.</li>
	 *  <li>Pressing escape cancels the gesture.</li>
	 *  <li>Holding down the control key while
	 *  hitting return will finish and render
	 *  the gesture and go into concatenation mode.</li>
	 *  </ul>
	 */
	public void keyPressed( KeyEvent e )
	{
		Rectangle2D dndCurrentRect;
	
		switch( e.getKeyCode() ) {
		case KeyEvent.VK_ENTER:  // complete
			if( dndState == DND_CTRL || dndState == DND_VELOCITY ) {
				finishGesture( true );
				if( e.isControlDown() && canConcatenate() ) {   // anschluss-gesture
					if( !initConcatenation( dndCtrlPoints )) return;	// failed
					dndState			= DND_INITDRAG;
					dndRecentRect		= null;
					dndCtrlPoints[1]	= dndCtrlPoints[0];
					dndBasicShape		= createBasicShape( dndCtrlPoints );
					dndCtrlPointsShape  = calcCtrlPointsShape( dndCtrlPoints, 2 );
					dndCurrentRect		= dndBasicShape.getBounds2D().createUnion( dndCtrlPointsShape.getBounds2D() );
					efficientRepaint( dndRecentRect, dndCurrentRect );
					dndRecentRect		= dndCurrentRect;
				}
			}
			break;
			
//		case KeyEvent.VK_ESCAPE: // abort
//			// XXX while dragging escape should return to the last stable state!
//			if( dndState != DND_NONE ) finishGesture( false );
//			break;
			
		default:
			break;
		}
	}
	
	/**
	 *  Does nothing since we only track keyPressed events.
	 */
	public void keyReleased( KeyEvent e ) {}

	/**
	 *  Does nothing since we only track keyPressed events.
	 */
	public void keyTyped( KeyEvent e ) {}
	
	protected void cancelGesture()
	{
		finishGesture( false );
	}
}