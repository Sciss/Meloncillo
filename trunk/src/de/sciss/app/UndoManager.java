/*
 *  UndoManager.java
 *  de.sciss.app package
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
 *		20-May-05	created from de.sciss.meloncillo.edit.UndoManager
 *		15-Jul-05	discardAllEdits calls setDirty
 */

package de.sciss.app;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;
// import javax.swing.undo.UndoManager;

/**
 *  An subclass of Swing's <code>UndoManager</code> that
 *  provides <code>Actions</code> to attach to menu items
 *  in a standard edit menu. Besides it informs the main
 *  application's document handler about the document being
 *	modified, thus allowing the application to update the
 *	window's title bar and display confirmation dialogs when
 *	an unsaved session is about to be discarded.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.11, 15-Jul-05
 */
public class UndoManager
extends javax.swing.undo.UndoManager
{
	/*
	 *  An <code>Action</code> object
	 *  suitable for attaching to a
	 *  <code>JMenuItem</code> in a edit
	 *  menu. The action will cause the
	 *  <code>UndoManager</code> to undo
	 *  the last edit in its history.
	 */
	private final AbstractAction  undoAction;
	/*
	 *  An <code>Action</code> object
	 *  suitable for attaching to a
	 *  <code>JMenuItem</code> in a edit
	 *  menu. The action will cause the
	 *  <code>UndoManager</code> to redo
	 *  the edit that was undone recently.
	 */
	private final AbstractAction  redoAction;

	private AbstractAction	debugAction	= null;

	private final de.sciss.app.Document		doc;

	private final String undoText, redoText;

	/**
	 *  Instantiate a new UndoManager.
	 *
	 *  @param  doc		the document whose edits are monitored
	 */
	public UndoManager( de.sciss.app.Document doc )
	{
		super();
		
		this.doc	= doc;
		undoText	= doc.getApplication().getResourceString( "menuUndo" );
		redoText	= doc.getApplication().getResourceString( "menuRedo" );
		undoAction	= new actionUndoClass();
		redoAction	= new actionRedoClass();
		updateStates();
	}
	
	/**
	 *  Get an Action object that will undo the
	 *	last step in the undo history.
	 *
	 *  @return <code>Action</code> suitable for attaching to a <code>JMenuItem</code>.
	 */
	public Action getUndoAction()
	{
		return undoAction;
	}

	/**
	 *  Get an Action object that will redo the
	 *	next step in the undo history.
	 *
	 *  @return <code>Action</code> suitable for attaching to a <code>JMenuItem</code>.
	 */
	public Action getRedoAction()
	{
		return redoAction;
	}
	
	/**
	 *  Get an Action object that will dump the
	 *  current undo history to the console.
	 *
	 *  @return <code>Action</code> suitable for attaching to a <code>JMenuItem</code>.
	 */
	public synchronized Action getDebugDumpAction()
	{
		if( debugAction == null ) {
			debugAction = new actionDebugDump();
		}
	
		return debugAction;
	}

	/**
	 *  Add a new edit to the undo history.
	 *  This behaves just like the normal
	 *  UndoManager, i.e. it tries to replace
	 *  the previous edit if possible. When
	 *  the edits <code>isSignificant()</code>
	 *  method returns true, the main application
	 *  is informed about this edit by calling
	 *  the <code>setModified</code> method.
	 *  Also the undo and redo action's enabled
	 *  / disabled states are updated.
	 *
	 *  @see	de.sciss.app.Document#setDirty( boolean )
	 *  @see	javax.swing.undo.UndoableEdit#isSignificant()
	 *  @see	javax.swing.Action#setEnabled( boolean )
	 */
	public boolean addEdit( UndoableEdit anEdit )
	{
		final boolean result = super.addEdit( anEdit );
		updateStates();
//		if( anEdit.isSignificant() ) doc.setDirty( true );
		return result;
	}
	
	/**
	 *  Purge the undo history and
	 *  update the undo / redo actions enabled / disabled state.
	 *
	 *  @see	de.sciss.app.Document#setDirty( boolean )
	 */
	public void discardAllEdits()
	{
		super.discardAllEdits();
		updateStates();
	}

	private void updateStates()
	{
		String text;

		if( undoAction.isEnabled() != canUndo() ) {
			undoAction.setEnabled( canUndo() );
			doc.setDirty( canUndo() );
		}
		if( redoAction.isEnabled() != canRedo() ) redoAction.setEnabled( canRedo() );
		
//		text = canUndo() ? undoText + " " + getUndoPresentationName() : undoText;
		text = getUndoPresentationName();
		if( !text.equals( undoAction.getValue( Action.NAME ))) undoAction.putValue( Action.NAME, text );
		
//		text = canRedo() ? redoText + " " + getRedoPresentationName() : redoText;
		text = getRedoPresentationName();
		if( !text.equals( redoAction.getValue( Action.NAME ))) redoAction.putValue( Action.NAME, text );
	}

	private class actionUndoClass
	extends AbstractAction
	{
		private actionUndoClass()
		{
			super( undoText );
			putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_Z,
					  Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() ));
		}
		
		public void actionPerformed( ActionEvent e )
		{
			try {
				undo();
			} catch( CannotUndoException e1 ) {
				System.err.println( e1.getLocalizedMessage() );
			}
			updateStates();
		}
	}

	private class actionRedoClass
	extends AbstractAction
	{
		private final String menuText = doc.getApplication().getResourceString( "menuRedo" );

		private actionRedoClass()
		{
			super( redoText );
			putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_Z,
					  Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() + KeyEvent.SHIFT_MASK ));
		}

		public void actionPerformed( ActionEvent e )
		{
			try {
				redo();
			} catch( CannotRedoException e1 ) {
				System.err.println( e1.getLocalizedMessage() );
			}
			updateStates();
		}
	}

	private class actionDebugDump
	extends AbstractAction
	{
		private actionDebugDump()
		{
			super( "Dump Undo History" );
		}

		public void actionPerformed( ActionEvent e )
		{
			final int			num			= edits.size();
			final UndoableEdit	redoEdit	= editToBeRedone();
			final UndoableEdit	undoEdit	= editToBeUndone();

			UndoableEdit	edit;

			System.err.println( "Undo buffer contains "+num+" edits." );
			
			for( int i = 0; i < num; i++ ) {
				edit = (UndoableEdit) edits.get( i );
				if( edit == redoEdit ) System.err.print( "R" );
				else if( edit == undoEdit ) System.err.print( "U" );
				else System.err.print( " " );
				System.err.println( " edit #"+i+" = "+edit.getPresentationName() );
			}
		}
	}
}