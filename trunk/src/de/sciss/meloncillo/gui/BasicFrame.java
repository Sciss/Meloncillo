/*
 *  BasicFrame.java
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
 *		13-Jun-04   new method alwaysPackSize(); use lim to validate bounds.
 *		27-Jul-04   have added a WindowListener since sucky awt reports
 *					componentHidden not when we call window.hide(). On the
 *					other hand windowOpened is only called once, when the
 *					frame is shown for the second times, componentShown is
 *					called instead. Someone should take a rifle and shoot all their heads off.
 *		31-Jul-04   commented, cleanup.
 *					pack() has been removed from layoutWindows() which seems to be
 *					superfluous.
 *		14-Aug-04   dispose works
 *      24-Dec-04   added support for variable look-and-feel
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.gui.*;

/**
 *  Common functionality for all application windows.
 *  This class provides means for storing and recalling
 *  window bounds in preferences. All subclass windows
 *  will get a copy of the main menubar as well.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @todo   the window bounds prefs storage sucks like hell
 *          ; there's a bug: if recall-window-bounds is deactivated
 *          the prefs are loaded nevertheless, hence when restarting
 *          the application, the bounds will be those of the
 *          last loaded session
 */
public class BasicFrame
extends JFrame
{
	// map entries parsed in layoutWindows()
	private static final String[] edges = { SpringLayout.NORTH, SpringLayout.SOUTH,
											SpringLayout.WEST, SpringLayout.EAST };

	// windows bounds get saved to a sub node inside the shared node
	// the node's name is the class name's last part (omitting the package)
	private Preferences               	classPrefs;
	// all windows are virtually layed out by an insible container
	// with a spring layout
	private static LayoutContainer springContainer			= null;
	private static final SpringLayout		springLayout	= new SpringLayout();
	private LayoutComponent					springComp;
	private Main							root;
//	private final LaterInvocationManager	lim;

	private ComponentListener				cmpListener;
	private WindowListener					winListener;
    
	/**
	 *  Constructs a new frame.
	 *  A preference node of the subclasse's class name
	 *  is created inside the main preferences' NODE_SHARED
	 *  node. Listeners are installed to track changes
	 *  of the window bounds and visibility which are then saved to the
	 *  class preferences.
	 *
	 *  @param  title   title shown in the frame's title bar
	 */
	public BasicFrame( String title )
	{
		super( title );

		String  className   = getClass().getName();
		classPrefs			= AbstractApplication.getApplication().getUserPrefs().node(
								PrefsUtil.NODE_SHARED ).node(
									className.substring( className.lastIndexOf( '.' ) + 1 ));
		// MacOS X has a stupid bug that causes the window bounds
		// update to be ignored in many cases. invoking the validation
		// at a later time improves the situation but still does
		// not fix this bug in all cases.
//		lim = new LaterInvocationManager( new LaterInvocationManager.Listener() {
//			public void laterInvocation( Object o ) {
//				((Window) o).validate();
//				if( alwaysPackSize() ) {
//					pack();
//				}
//			}
//		});
		
		cmpListener = new ComponentAdapter() {
			public void componentResized( ComponentEvent e )
			{
				classPrefs.put( PrefsUtil.KEY_SIZE, PrefsUtil.dimensionToString( getSize() ));
			}

			public void componentMoved( ComponentEvent e )
			{
				classPrefs.put( PrefsUtil.KEY_LOCATION, PrefsUtil.pointToString( getLocation() ));
			}

			public void componentShown( ComponentEvent e )
			{
				classPrefs.putBoolean( PrefsUtil.KEY_VISIBLE, true );
			}

			public void componentHidden( ComponentEvent e )
			{
				classPrefs.putBoolean( PrefsUtil.KEY_VISIBLE, false );
			}
		};
		winListener = new WindowAdapter() {
			public void windowOpened( WindowEvent e )
			{
				classPrefs.putBoolean( PrefsUtil.KEY_VISIBLE, true );
			}

			public void windowClosing( WindowEvent e )
			{
				classPrefs.putBoolean( PrefsUtil.KEY_VISIBLE, false );
				
			}
		};
		
		addComponentListener( cmpListener );
		addWindowListener( winListener );
		
		if( springContainer == null ) {
			springContainer = new LayoutContainer();
		}
		springComp	= new LayoutComponent( this );
		springContainer.add( springComp );
   	}
    
	/**
	 *  Frees resources, clears references
	 */
	public void dispose()
	{
		if( root != null ) {
			if( hasMenuBar() ) root.menuFactory.forgetAbout( this );
			root.addComponent( getClass().getName(), null );
			classPrefs  = null;
			root		= null;
		}
		springContainer.remove( springComp );
		springComp  = null;
		super.dispose();
	}
	
	/**
	 *  Queries whether this frame's bounds
	 *  should be packed automatically to the
	 *  preferred size independent of
	 *  concurrent preference settings
	 *
	 *  @return	<code>true</code>, if the frame wishes
	 *			to be packed each time a custom setSize()
	 *			would be applied in the course of a
	 *			preference recall. The default value
	 *			of <code>true</code> can be modified by
	 *			subclasses by overriding this method.
	 *  @see	java.awt.Window#pack()
	 */
	protected boolean alwaysPackSize()
	{
		return true;
	}

	/**
	 *  Queries whether this frame should
     *  have a copy of the menu bar. The default
     *  implementation returns true, basic palettes
     *  will return false.
	 *
	 *  @return	<code>true</code>, if the frame wishes
	 *			to be given a distinct menu bar
	 */
	protected boolean hasMenuBar()
	{
		return true;
	}

 	/**
	 *  Restores all <code>BasicFrames</code> of the
	 *  application to the bounds given by their
	 *  class preferences. This gets called by the
	 *  <code>MenuFactory</code> at the end of a "Open Session"
	 *  action, depending of the preference settings
	 *  for recalling window bounds.
	 *
	 *  @see	de.sciss.meloncillo.util.PrefsUtil#KEY_RECALLFRAMES
	 */
	protected static void restoreAllFromPrefs()
	{
		BasicFrame		bf;
		int				i;
		LayoutComponent springComp;
	
		for( i = 0; i < springContainer.getComponentCount(); i++ ) {
			springComp	= (LayoutComponent) springContainer.getComponent( i );
			bf			= springComp.getRealOne();
			bf.restoreFromPrefs();
			try {
				Thread.sleep( 1 );
			}
			catch( InterruptedException e1 ) {};
		}
	}

 	/**
	 *  Updates Swing component tree for all
     *  frames after a look-and-feel change
	 */
	public static void lookAndFeelUpdate()
	{
        if( springContainer == null ) return;
        
		BasicFrame		bf;
		int				i;
		LayoutComponent springComp;
	
		for( i = 0; i < springContainer.getComponentCount(); i++ ) {
			springComp	= (LayoutComponent) springContainer.getComponent( i );
			bf			= springComp.getRealOne();
            SwingUtilities.updateComponentTreeUI( bf );
        }
	}

	/*
	 *  Restores this frame's bounds and visibility
	 *  from its class preferences.
	 *
	 *  @see	#restoreAllFromPrefs()
	 */
	private void restoreFromPrefs()
	{
		String		sizeVal = classPrefs.get( PrefsUtil.KEY_SIZE, null );
		String		locVal  = classPrefs.get( PrefsUtil.KEY_LOCATION, null );
		String		visiVal	= classPrefs.get( PrefsUtil.KEY_VISIBLE, null );
		Rectangle   r		= getBounds();
//		Insets		i		= getInsets();

		Dimension d			= PrefsUtil.stringToDimension( sizeVal );
		if( d == null || alwaysPackSize() ) {
			pack();
			d				= getSize();
		}

		r.setSize( d );
		Point p = PrefsUtil.stringToPoint( locVal );
		if( p != null ) {
			r.setLocation( p );
		}
		setBounds( r );
		invalidate();
//		if( alwaysPackSize() ) {
//			pack();
//		} else {
			validate();
//		}
//		lim.queue( this );
		if( visiVal != null ) {
			setVisible( new Boolean( visiVal ).booleanValue() );
		}
	}

	// returns the virtual component on
	// the invisible container used to represent
	// this frame when layouting all windows
	private Component getLayoutComponent()
	{
		return springComp;
	}

	protected String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}	

	/**
	 *  Subclasses should call this
	 *  after having constructed their GUI.
	 *  Then this method will attach a copy of the main menu
	 *  from <code>root.menuFactory</code> and
	 *  restore bounds from preferences.
	 *
	 *  @param  root	application root
	 *
	 *  @see	MenuFactory#gimmeSomethingReal( JFrame )
	 */
	protected void init( Main root )
	{
		this.root = root;
		if( hasMenuBar() ) root.menuFactory.gimmeSomethingReal( this );
        HelpGlassPane.attachTo( this );
		restoreFromPrefs();
	}
	
	/**
	 *  This method is invoked by the <code>Main</code>
	 *  class upon application launch. It is declared
	 *  <code>public</code> for the mere reason that
	 *  <code>Main</code> belongs to a different package,
	 *  and should be considered private otherwise.
	 *  <p>
	 *  This method lays out all frames according
	 *  to a description map and using an internal
	 *  <code>SpringLayout</code>.
	 *
	 *  @deprecated		the <code>SpringLayout</code> requires a lot of effort
	 *					by us and is inflexible at the same time.
	 *					This whole mechanism should be replaced by
	 *					something more clear and efficient, omitting
	 *					the whole idea of a virtual container in the
	 *					background. Besides bugs in Apple's window
	 *					resizing management should be thoroughly checked.
	 */
	public static void layoutWindows( Main root, Map mapSpringMaps, boolean usePrefs )
	{
		int							i, j;
		LayoutComponent				springComp;
		BasicFrame					bf;
		Point						loc, loc2;
		Dimension					size;
		SpringDescr					sd;
		Map							springMap;
		Boolean						visible;
		Component					refComp;
		SpringLayout.Constraints	cons;
		Spring						maxWidthSpring  = Spring.constant( 0 );
		Spring						maxHeightSpring = Spring.constant( 0 );
		
//System.err.println( "--- "+springContainer.getClass().getName()+" ---" );
//GUIUtil.printSizes( springContainer );

		for( i = 0; i < springContainer.getComponentCount(); i++ ) {
			springComp	= (LayoutComponent) springContainer.getComponent( i );
			cons		= springLayout.getConstraints( springComp );
			bf			= springComp.getRealOne();
			loc			= PrefsUtil.stringToPoint( bf.classPrefs.get( PrefsUtil.KEY_LOCATION, null ));
			size		= PrefsUtil.stringToDimension( bf.classPrefs.get( PrefsUtil.KEY_SIZE, null ));

			// if the caller asks us to use preferences and
			// the prefs contains values for either size or
			// location, apply those values, otherwise rely
			// on the spring layout descriptions
			if( usePrefs && (loc != null || size != null) ) {
				if( loc != null ) {
					cons.setConstraint( SpringLayout.WEST, Spring.constant( loc.x - springContainer.getX() ));
					cons.setConstraint( SpringLayout.NORTH, Spring.constant( loc.y - springContainer.getY() ));
					bf.setLocation( loc );
				}
				if( size != null ) {
					if( loc == null ) loc = bf.getLocation();
					cons.setConstraint( SpringLayout.EAST, Spring.constant( loc.x + size.width - springContainer.getX() ));
					cons.setConstraint( SpringLayout.SOUTH, Spring.constant( loc.y + size.height - springContainer.getY() ));
					bf.setSize( size );
				}
				springComp.setBounds( bf.getBounds() );
				springComp.setVisible( bf.classPrefs.getBoolean( PrefsUtil.KEY_VISIBLE, false ));
			} else {
				springMap   = (Map) mapSpringMaps.get( bf.getClass().getName() );
				springComp.setBounds( bf.getBounds() );
				if( springMap != null ) {
					for( j = 0; j < edges.length; j++ ) {
						sd	= (SpringDescr) springMap.get( edges[j] );
						if( sd != null ) {
							refComp = sd.ref == null ? springContainer : ((BasicFrame) root.getComponent( sd.ref )).getLayoutComponent();
							if( refComp != null ) {
								springLayout.putConstraint( edges[j], springComp, sd.pad, sd.refEdge, refComp );
							}
						}
					}
					visible = (Boolean) springMap.get( SpringDescr.VISIBLE );
					if( visible != null ) {
						springComp.setVisible( visible.booleanValue() );
					}
				}
			}
			maxWidthSpring  = Spring.max( maxWidthSpring, cons.getConstraint( SpringLayout.EAST ));
			maxHeightSpring = Spring.max( maxHeightSpring, cons.getConstraint( SpringLayout.SOUTH));
		}
		cons = springLayout.getConstraints( springContainer );
		cons.setConstraint( SpringLayout.EAST,  Spring.sum( maxWidthSpring, Spring.constant( 150 )));
		cons.setConstraint( SpringLayout.SOUTH, Spring.sum( maxHeightSpring, Spring.constant( 150 )));
//		cons.setConstraint( SpringLayout.EAST,  Spring.constant( springContainer.getWidth() ));
//		cons.setConstraint( SpringLayout.SOUTH, Spring.constant( springContainer.getHeight() ));

		// the whole verification of the layout is very tricky
		// after these two calls, the virtual component's bounds
		// can be investigated and transferred to the real windows
		springLayout.invalidateLayout( springContainer );
		springLayout.layoutContainer( springContainer );

		loc2 = springContainer.getLocation();
		for( i = 0; i < springContainer.getComponentCount(); i++ ) {
			springComp	= (LayoutComponent) springContainer.getComponent( i );
			bf			= springComp.getRealOne();
			loc			= PrefsUtil.stringToPoint( bf.classPrefs.get( PrefsUtil.KEY_LOCATION, null ));
			size		= PrefsUtil.stringToDimension( bf.classPrefs.get( PrefsUtil.KEY_SIZE, null ));
			if( usePrefs && (loc != null || size != null) ) {
				if( loc != null ) {
					bf.setLocation( loc );
				}
				if( size != null ) {
					bf.setSize( size );
				}
			} else {
				loc		= springComp.getLocation();
				loc.translate( loc2.x, loc2.y );
				bf.setLocation( loc );
				bf.setSize( springComp.getSize() );
			}
			bf.setVisible( springComp.isVisible() );
		}
	}
	
// --------------- internal classes ---------------

	/*
	 *  A doppelgaenger class for representing
	 *  a frame in the virtual container, passing
	 *  minimum, maximum and preferred size directly
	 *  from the real component.
	 */  
	private class LayoutComponent
	extends Component
	{
		private BasicFrame realOne;

		private LayoutComponent( BasicFrame realOne )
		{
			this.realOne = realOne;
		}
		
		private BasicFrame getRealOne()
		{
			return realOne;
		}
		
		public Dimension getMinimumSize()		{ return realOne.getMinimumSize(); }
		public Dimension getMaximumSize()		{ return realOne.getMaximumSize(); }
		public Dimension getPreferredSize()		{ return realOne.getPreferredSize(); }
	}
	
	/*
	 *  A container as a simulation
	 *  of the screen space with the
	 *  BasicFrames represented as LayoutComponent
	 *  objects.
	 */
	private class LayoutContainer
	extends Container
	{
		private Rectangle bounds;
		
		private LayoutContainer()
		{
			super();
			bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
			setBounds( bounds );
		}
		
		public Dimension getMaximumSize()
		{
			return bounds.getSize();
		}

		public Dimension getMinimumSize()
		{
			return getMaximumSize();
		}

		public Dimension getPreferredSize()
		{
			return getMaximumSize();
		}
	}
}