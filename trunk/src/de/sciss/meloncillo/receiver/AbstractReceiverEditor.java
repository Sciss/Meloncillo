/*
 *  AbstractReceiverEditor.java
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
 *		14-Aug-04   commented
 *      24-Dec-04   extends BasicPalette
 *      27-Dec-04   added online help
 *		23-Apr-05	extends BasicFrame instead of BasicPalette, otherwise copy/paste
 *					not possible on non-Mac systems
 */

package de.sciss.meloncillo.receiver;

import java.awt.event.*;
import javax.swing.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.session.*;

import de.sciss.app.*;
import de.sciss.gui.*;

/**
 *  A simple implementation of the <code>ReceiverEditor</code>
 *  interface that does not yet make assumptions
 *  about the supported receivers but provides some
 *  common means useful for all receivers.
 *  It handles initialization and disposal when the
 *  frame is closed.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public abstract class AbstractReceiverEditor
extends BasicFrame
implements ReceiverEditor, SessionCollection.Listener
{
	protected   Receiver	rcv		= null;
	protected   Session		doc		= null;
	protected   Main		root	= null;

	/**
	 *  Creates a new editor. Initialization
	 *  is performed by calling the <code>init</code>
	 *  method. This method will install a listener
	 *  that deals with the window disposal.
	 */
	protected AbstractReceiverEditor()
	{
		super( null );

		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE );

		addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e )
			{
				hideAndDispose();
			}
		});

        HelpGlassPane.setHelp( this.getRootPane(), "ReceiverEditor" );
    }

	// update frame title which displays the receiver's name
	private void updateTitle()
	{
		setTitle( AbstractApplication.getApplication().getResourceString( "frameReceiverEditor" ) +
				  " : "+rcv.getName() );
	}

	// hide + dipose the frame. remove listeners, call
	// subclass's receiverDied() method and null the
	// receiver reference.
	private void hideAndDispose()
	{
		hide();
		dispose();
		doc.receivers.removeListener( this );
		receiverDied();
		rcv = null;
	}
	
	/**
	 *  Informs a subclass that the editor
	 *  was disposed or the associated receiver
	 *  was deleted. The subclass can use
	 *  this method to perform additional disposals
	 *  or clear unnecessary references to the receiver
	 *  to catalyze garbage collection.
	 */
	protected abstract void receiverDied();

	/**
	 *  This implementation will call
	 *  <code>getHandledReceivers</code> and
	 *  scan the list for the queried receiver
	 *  class.
	 */
	public boolean canHandle( Receiver rcv )
	{
		java.util.List  coll = getHandledReceivers();
		int				i;
		Class			rcvClass;
		
		for( i = 0; i < coll.size(); i++ ) {
			rcvClass	= (Class) coll.get( i );
			if( rcvClass.isInstance( rcv )) return true;
		}
		return false;
	}

	public Receiver getReceiver()
	{
		return rcv;
	}

	/**
	 *  Subclasses should override this and
	 *  call the superclass implementation
	 *  at first.
	 */
	public void init( Main root, Session doc, Receiver rcv )
	{
		if( this.canHandle( rcv )) {
			this.rcv	= rcv;
			this.root   = root;
			this.doc	= doc;
			super.init( root );
			doc.receivers.addListener( this );
			updateTitle();
		} else {
			throw new IllegalArgumentException( rcv.getClass().getName() +
				AbstractApplication.getApplication().getResourceString( "receiverExceptionNotHandled" ));
		}
	}
	
	/**
	 *  Since this is a subclass
	 *  of <code>JFrame</code>, it simply returns
	 *  this object itself.
	 */
	public JFrame getView()
	{
		return this;
	}

// ---------------- SessionCollection.Listener interface ---------------- 

	public void sessionCollectionChanged( SessionCollection.Event e )
	{
		if( e.collectionContains( rcv ) && e.getModificationType() == SessionCollection.Event.ACTION_REMOVED ) {
			hideAndDispose();
		}
	}
	
	public void sessionObjectChanged( SessionCollection.Event e )
	{
		if( e.collectionContains( rcv ) && e.getModificationType() == Receiver.OWNER_RENAMED ) {
			updateTitle();
		}
	}

	public void sessionObjectMapChanged( SessionCollection.Event e ) {}
}