/*
 *  RealtimeFrame.java
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
 *		24-Jul-04   created
 *		01-Sep-04	commented and cleaned up
 *		01-Jan-05	accelerator changed to Ctrl+Shift+R
 *					because Ctrl+S on Windows equals File->Save
 *		19-Mar-05	bugfix in reContext() (NullPointerException)
 */

package de.sciss.meloncillo.realtime;

import java.awt.event.*;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.*;
import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.plugin.*;
import de.sciss.meloncillo.session.*;

import de.sciss.app.*;
import de.sciss.gui.*;

/**
 *  Analogon to the AbstractRenderDialog,
 *  but not abstract and including parts
 *  of BounceDialog.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo	before hibernation, focus should be pulled by some component
 */
public class RealtimeFrame
extends AbstractPlugInFrame
// implements TransportListener
{
	private static final Vector			collProducerTypes   = new Vector();
	private final TransportPalette		transportPalette;
	private final Transport				transport;
	private final actionRealtimeClass	actionRealtime;
	private final JToggleButton			ggRealtime;

	private RealtimePlugIn				plugIn				= null;
	private RealtimeContext				context				= null;

	private boolean realtimeRunning = false;

	public RealtimeFrame( Main root, Session doc )
	{
		super( root, doc, AbstractApplication.getApplication().getResourceString( "frameRealtime" ), 0 );
		
		transport			= root.transport;
		transportPalette	= (TransportPalette) root.getComponent( Main.COMP_TRANSPORT );

		actionRealtime		= new actionRealtimeClass( GraphicsUtil.ICON_REALTIME );
        ggRealtime			= new JToggleButton( actionRealtime );
		actionRealtime.setIcons( ggRealtime );
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( ggRealtime, KeyStroke.getKeyStroke(
//											  KeyEvent.VK_MULTIPLY, 0 )));
// EEE
		transportPalette.addButton( ggRealtime );
		
		// listeners
//		transport.addTransportListener( this );
//		HelpGlassPane.setHelp( this.getRootPane(), "RealtimeDialog" );	// EEE
		
		init();
		AbstractApplication.getApplication().addComponent( Main.COMP_REALTIME, this );
	}

	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_REALTIME );
		super.dispose();
	}

	protected Point2D getPreferredLocation()
	{
		return new Point2D.Float( 0.58f, 0.68f );
//		return new Point2D.Float( 0.7f, 0.05f );
	}

	protected void checkReContext()
	{
		if( context != null && !isRealtimeContextValid( context )) {
			boolean success = false;
			try {
				success = reContext();
			}
			finally {
				ggRealtime.setEnabled( success );
			}
		}
	}
	
	private boolean reContext()
	{
		JComponent	view;

		context = createRealtimeContext();
//System.err.println( "reContext(). context = "+context+"; plugIn = "+plugIn );
		if( context != null && plugIn != null ) {
			view	= plugIn.getSettingsView( context );
			if( view != null ) AbstractWindowHandler.setDeepFont( view );
			ggSettingsPane.setViewportView( view );
			pack();
			return( view != null );
		} else {
			ggSettingsPane.setViewportView( null );
			return false;
		}
	}

	private RealtimeContext createRealtimeContext()
	{
		return transport.getContext();
	}

	private boolean isRealtimeContextValid( RealtimeContext context )
	{
		if( !doc.bird.attemptShared( Session.DOOR_TIMETRNSRCV, 250 )) return false;
		try {
			return( doc.selectedTransmitters.getAll().equals( context.getTransmitters() ) &&
					doc.timeline.getSelectionSpan().equals( context.getTimeSpan() ) &&
					doc.timeline.getRate() == context.getSourceRate() );	// XXX SourceBlockSpan
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIMETRNSRCV );
		}
	}

	/**
	 *	Returns null at the moment
	 *	(i.e. no components)
	 */
	protected JComponent createBottomPanel( int flags )
	{
		return null;
	}

	protected java.util.List getProducerTypes()
	{
		if( collProducerTypes.isEmpty() ) {
			Hashtable h;
			h = new Hashtable();
			h.put( Main.KEY_CLASSNAME, "de.sciss.meloncillo.realtime.LispRealtimePlugIn" );
			h.put( Main.KEY_HUMANREADABLENAME, "Lisp Plug-In" );
			collProducerTypes.add( h );
		}
		return collProducerTypes;
	}

	/**
	 *	Instantiates and initializes the new plug-in.
	 */
	protected void switchPlugIn( String className )
	{
		boolean	success	= false;

//System.err.println( "switchPlugIn(). className = "+className );
		try {
			plugIn = null;
			if( className != null ) {
				try {
					plugIn	= (RealtimePlugIn) Class.forName( className ).newInstance();
					plugIn.init( root, doc );
				}
				catch( InstantiationException e1 ) {
					GUIUtil.displayError( getWindow(), e1, AbstractApplication.getApplication().getResourceString( "errInitPlugIn" ));
				}
				catch( IllegalAccessException e2 ) {
					GUIUtil.displayError( getWindow(), e2, AbstractApplication.getApplication().getResourceString( "errInitPlugIn" ));
				}
				catch( ClassNotFoundException e3 ) {
					GUIUtil.displayError( getWindow(), e3, AbstractApplication.getApplication().getResourceString( "errInitPlugIn" ));
				}
			}
			success = reContext();
		}
		finally {
			ggRealtime.setEnabled( success );
		}
	}

	private void progressStop()
	{
		if( context == null || plugIn == null || !realtimeRunning ) return;
	
		try {
			plugIn.realtimeDisable( context, transport );
		}
		catch( IOException e1 ) {
			GUIUtil.displayError( getWindow(), e1, AbstractApplication.getApplication().getResourceString( "errInitPlugIn" ));
		}
		finally {
			realtimeRunning = false;
			hibernation( false );
		}
	}
	
	private boolean progressStart()
	{
		if( plugIn == null || realtimeRunning ) return false;
	
		boolean	success = false;
	
		try {
			reContext();
			hibernation( true );
//System.err.println( "context.getTransmitters().size() = "+context.getTransmitters().size()+
//				";   context.getReceivers().size() = "+context.getReceivers().size() );
			realtimeRunning	= true;
			success			= plugIn.realtimeEnable( context, transport );
		}
		catch( IOException e1 ) {
			GUIUtil.displayError( getWindow(), e1, AbstractApplication.getApplication().getResourceString( "errInitPlugIn" ));
		}
		finally {
			if( !success ) {
				progressStop();
			}
		}
//		pt  = new ProcessingThread( this, pc, root, doc, getTitle(), context, Session.DOOR_ALL );
//		ggRender.requestFocus();
		return success;
	}

// ---------------- TransportListener interface ---------------- 

/*
	public void transportStop( long pos )
	{
	}
	
	public void transportPosition( long pos )
	{
	}
	
	public void transportPlay( long pos )
	{
	}
	
	public void transportQuit() {}
*/

// ---------------- actions ---------------- 

	private class actionRealtimeClass extends AbstractAction
	{
		private		Icon[]  icons;
	
		public actionRealtimeClass( int ICON_ID )
		{
			super();
			icons   = GraphicsUtil.createToolIcons( ICON_ID );
		}

		public void setIcons( AbstractButton b )
		{
			GraphicsUtil.setToolIcons( b, icons );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			if( ((AbstractButton) e.getSource()).isSelected() ) {
				if( !progressStart() ) {
					ggRealtime.setSelected( false );
				}
			} else {
				progressStop();
			}
        }
	} // class actionRealtimeClass
}