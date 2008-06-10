/*
 *  SessionObjectTable.java
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
 *		03-Feb-05	created
 *		27-Mar-05	adds support for dynamic plug-in created contexts and
 *					boolean and string type values
 *		08-Apr-05	bugfix : edits are undoable now
 *		27-Apr-05	bugfix : cancels editing when keys are updated
 */

package de.sciss.meloncillo.session;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.undo.*;

import de.sciss.meloncillo.*;
import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.math.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;
import de.sciss.gui.*;

/**
 *	This class is used to display a session object's
 *	map entries in the observer palette. it is a <code>JTable</code>
 *	with two columns for the key name and the corresponding value.
 *	When plug-ins are activated or deactivated or when the
 *	map changes, the table is automatically updated. The
 *	map manager's contexts are scanned for items that should be
 *	displayed in the palette, and for plug-in specific entries are
 *	matched with the currently active plug-ins.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class SessionObjectTable
extends JTable
implements DynamicListening
{
	private final Main					root;
	private final de.sciss.app.Document	doc;
	private final LockManager			lm;
	private final int					doors;
	private final Object				sync		= new Object();
	private final java.util.List		keys		= new ArrayList();
	private final java.util.Map			contexts	= new HashMap();
	private final java.util.List		collObjects	= new ArrayList();
	private final AbstractTableModel	model;
	private final TableCellEditor		editor;
	
	private final MapManager.Listener	soListener, plugListener;
	
	private static final String[] columnNames = new String[] { "key", "value" };	// not used
	
	public SessionObjectTable( Main root, de.sciss.app.Document doc, final LockManager lm, final int doors )
	{
		super();

		this.root	= root;
		this.doc	= doc;
		this.lm		= lm;
		this.doors	= doors;
		model		= new Model();
		
//		Renderer	renderer	= new Renderer();
//		Class		renderClass	= MapManager.Context.class;
		
		setModel( model );
		setRowSelectionAllowed( false );
		TableColumn	tc;
		tc		= getColumnModel().getColumn( 0 );
		tc.setMaxWidth( 92 ); // XXX
		tc		= getColumnModel().getColumn( 1 );
		tc.setCellRenderer( new Renderer() );
		editor	= new Renderer();
		tc.setCellEditor( editor );
//		setRowMargin( 4 );
		setRowHeight( 24 );	// XXX
//		setOpaque( false );
		setBackground( null );
		setShowGrid( false );

		// ------- listeners -------
		soListener = new MapManager.Listener() {
			public void mapChanged( MapManager.Event e )
			{
				if( e.getSource() == sync ) return;
			
				Iterator	iter;
				int			row;
				
				synchronized( sync ) {
					iter = e.getPropertyNames().iterator();
					
					while( iter.hasNext() ) {
						row = keys.indexOf( iter.next() );
						if( row >= 0 ) {
							model.fireTableCellUpdated( row, 1 );
						}
					}
				} // synchronized( sync )
			}
				
			public void mapOwnerModified( MapManager.Event e ) {}
		};
		
		plugListener = new MapManager.Listener() {
			public void mapChanged( MapManager.Event e )
			{
				if( !lm.attemptShared( doors, 250 )) return;
				try {
					synchronized( sync ) {
						unregisterAll();
						updateKeysAndContexts();	// calls model.fireTableDataChanged();
						registerAll();
					} // synchronized( sync )
				} finally {
					lm.releaseShared( doors );
				}
			}
				
			public void mapOwnerModified( MapManager.Event e ) {}
		};

        new DynamicAncestorAdapter( this ).addTo( this );
	}
	
// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		root.plugInManager.addListener( plugListener );
	
		if( !lm.attemptShared( doors, 250 )) return;
		try {
			synchronized( sync ) {
				updateKeysAndContexts(); // calls model.fireTableDataChanged();
				registerAll();
			}
		}
		finally {
			lm.releaseShared( doors );
		}
    }

    public void stopListening()
    {
		if( !lm.attemptShared( doors, 250 )) return;
		try {
			synchronized( sync ) {
				unregisterAll();
			}
		}
		finally {
			lm.releaseShared( doors );
		}
    }
	
	// sync: to be called with sync on doors and sync
	private void registerAll()
	{
		SessionObject so;
		
		for( int i = 0; i < collObjects.size(); i++ ) {
			so = (SessionObject) collObjects.get( i );
			so.getMap().addListener( soListener );
		}
	}

	// sync: to be called with sync on doors and sync
	private void unregisterAll()
	{
		SessionObject so;
		
		for( int i = 0; i < collObjects.size(); i++ ) {
			so = (SessionObject) collObjects.get( i );
			so.getMap().removeListener( soListener );
		}
	}

	public void setObjects( java.util.List collObjects )
	{
		lm.waitShared( doors );
		try {
			synchronized( sync ) {
				unregisterAll();
				this.collObjects.clear();
				this.collObjects.addAll( collObjects );
				updateKeysAndContexts();	// calls model.fireTableDataChanged();
				registerAll();
			} // synchronized( sync )
		} finally {
			lm.releaseShared( doors );
		}
	}
	
	// sync: to be called with sync on 'sync' and on doors
	// this method invokes model.fireTableDataChanged()
	private void updateKeysAndContexts()
	{
		MapManager			map;
		Object				o;
		MapManager.Context	c;
		SessionObject		so;

		if( isEditing() ) editor.cancelCellEditing();

		keys.clear();
		contexts.clear();
		if( !collObjects.isEmpty() ) {
			so	= (SessionObject) collObjects.get( 0 );
			map	= so.getMap();
			keys.addAll( map.keySet( MapManager.Context.FLAG_OBSERVER_DISPLAY,
									 MapManager.Context.NONE_EXCLUSIVE ));

			for( int i = keys.size() - 1; i >= 0 ; i-- ) {
				o	= keys.get( i );
				c	= map.getContext( o.toString() );
				contexts.put( o, c );
				// remove fields that belong to inactive plug-ins
				if( (c.dynamic != null) && 
					(root.plugInManager.getValue( c.dynamic.toString() ) == null) ) {
					keys.remove( o );
				}
			}
		}
		for( int i = 1; i < collObjects.size(); i++ ) {
			so	= (SessionObject) collObjects.get( i );	// only common fields are displayed
			map	= so.getMap();
			keys.retainAll( map.keySet( MapManager.Context.FLAG_OBSERVER_DISPLAY,
										MapManager.Context.NONE_EXCLUSIVE ));
		}
		model.fireTableDataChanged();
	}

	private class Model
	extends AbstractTableModel
	{
		public String getColumnName( int col )
		{
			return columnNames[ col ];
		}
		
		public int getRowCount()
		{
			synchronized( sync ) {
				return keys.size();
			}
		}
		
		public int getColumnCount()
		{
			return 2;
		}
		
		public Object getValueAt( int row, int col )
		{
			if( !lm.attemptShared( doors, 250 )) return null;
			try {
				synchronized( sync ) {
					if( row >= keys.size() ) return null;

					switch( col ) {
					case 0:
						String resKey = ((MapManager.Context) contexts.get( keys.get( row ))).label;
						if( resKey != null ) {
							// plug-ins will not store resKeys but human readable text
							return( AbstractApplication.getApplication().getResourceString( resKey, resKey ));
						}
						return( keys.get( row ).toString() );
						
					case 1:
						return getCommonValue( row );
						
					default:
						return null;
					}
				}
			}
			finally {
				lm.releaseShared( doors );
			}
		}

	    public Class getColumnClass( int col )
		{
			switch( col ) {
			case 0:
				return String.class;
			case 1:
				return MapManager.Context.class;
			default:
				return Object.class;
			}
		}
	
		public boolean isCellEditable( int row, int col )
		{
			return( col == 1 );
		}
		
		// sync: caller must sync on door and sync
		private Object getCommonValue( int row )
		{
			Object val, val2;
			int i;
		
			val = ((SessionObject) collObjects.get( 0 )).getMap().getValue( keys.get( row ).toString() );
			if( val == null ) return null;
			if( collObjects.size() > 1 ) {
				for( i = 1; i < collObjects.size(); i++ ) {
					val2 = ((SessionObject) collObjects.get( i )).getMap().getValue( keys.get( row ).toString() );
					if( val2 == null || !val2.equals( val )) {
						return null;
					}
				}
			}
			return val;
		}

		public void setValueAt( Object value, int row, int col )
		{
			if( col != 1 || value == null ) return;
		
			if( !lm.attemptExclusive( doors, 250 )) return;
			try {
				synchronized( sync ) {
					if( row >= keys.size() ) return;

					SessionObject	so;
					MapManager		map;
					final String	key		= keys.get( row ).toString();
					boolean			addEdit	= false;
					CompoundEdit	edit	= new BasicSyncCompoundEdit( lm, doors );

					for( int i = 0; i < collObjects.size(); i++ ) {
						so	= (SessionObject) collObjects.get( i );
						map = so.getMap();
						if( map.containsKey( key )) {
//System.err.println( "setting "+value.getClass().getName()+" on "+so.getName() );
							edit.addEdit( new EditPutMapValue( sync, lm, doors, map, key, value ));
							addEdit = true;
						}
					}
					if( addEdit ) {
						edit.end();
						doc.getUndoManager().addEdit( edit );
					}
				}
			}
			finally {
				lm.releaseExclusive( doors );
			}
		}
	} // class Model
	
	private class Renderer
	extends AbstractCellEditor
	implements TableCellRenderer, TableCellEditor, NumberListener, PathListener, ActionListener
	{
		private NumberField	ggNumberField	= null;
		private JTextField	ggTextField		= null;
		private Map			mapPathFields	= new HashMap();
		private JLabel		ggLabel			= null;
		private JCheckBox	ggCheckBox		= null;
		private JComboBox	ggComboBox		= null;
		// they are placed on a panel to avoid that there size is
		// maximized and looks ugly
		private JPanel		panelComboBox, panelTextField;
	
		private Object		editorValue		= null;
	
		private Renderer()
		{
			super();
		}

		public Object getCellEditorValue()
		{
			return editorValue;
		}

		private void prepareNumberField()
		{
			if( ggNumberField == null ) {
				ggNumberField = new NumberField( 0, NumberSpace.genericDoubleSpace, null );
				ggNumberField.addNumberListener( this );
				GUIUtil.setDeepFont( ggNumberField, GraphicsUtil.smallGUIFont );
			}
		}
		
		public Component getTableCellEditorComponent( JTable table, Object value, boolean isSelected, int row, int col )
		{
			editorValue = value;
			return getComponent( value, row, col, true );
		}

		public Component getTableCellRendererComponent( JTable table, Object value,
														boolean isSelected, boolean hasFocus,
														int row, int col )
		{
			Component c = getComponent( value, row, col, false );
//			if( c != null ) {
//				Dimension d = c.getPreferredSize();
//				if( d.height != table.getRowHeight( row )) {
//					table.setRowHeight( row, d.height );
//				}
//			}
			return c;
		}
		
		private Component getComponent( Object value, int row, int col, boolean isEditor )
		{
			NumberSpace ns;

			if( !lm.attemptShared( doors, 250 )) return null;
			try	{
				synchronized( sync ) {
					if( row >= keys.size() || col != 1 ) return null;

					MapManager.Context c = (MapManager.Context) contexts.get( keys.get( row ));
					switch( c.type ) {
					case MapManager.Context.TYPE_INTEGER:
					case MapManager.Context.TYPE_LONG:
					case MapManager.Context.TYPE_FLOAT:
					case MapManager.Context.TYPE_DOUBLE:
						prepareNumberField();
						if( (c.typeConstraints != null) && (c.typeConstraints instanceof NumberSpace) ) {
							ns = (NumberSpace) c.typeConstraints;
						} else {
							ns = (c.type == MapManager.Context.TYPE_INTEGER) || (c.type == MapManager.Context.TYPE_LONG) ?
								 NumberSpace.genericIntSpace : NumberSpace.genericDoubleSpace;
						}
						if( !ns.equals( ggNumberField.getSpace() )) ggNumberField.setSpace( 0, ns );

						ggNumberField.setNumber( (value == null) || !(value instanceof Number) ?
												 new Double( Double.NaN ) : (Number) value );
						return ggNumberField;

					case MapManager.Context.TYPE_BOOLEAN:
						if( ggCheckBox == null ) {
							ggCheckBox = new JCheckBox();
							ggCheckBox.setFocusable( false );
							GUIUtil.setDeepFont( ggCheckBox, GraphicsUtil.smallGUIFont );
						} else {
							ggCheckBox.removeActionListener( this );
						}
						ggCheckBox.setSelected( (value == null) || !(value instanceof Boolean) ?
											    false : ((Boolean) value).booleanValue() );
						ggCheckBox.addActionListener( this );
						return ggCheckBox;
	
					case MapManager.Context.TYPE_STRING:
						if( c.typeConstraints != null && (c.typeConstraints instanceof StringItem[]) ) {
							if( ggComboBox == null ) {
								ggComboBox		= new JComboBox();
								GUIUtil.setDeepFont( ggComboBox, GraphicsUtil.smallGUIFont );
								panelComboBox	= new JPanel( new BorderLayout() );
								panelComboBox.add( ggComboBox, BorderLayout.WEST );
								ggComboBox.setFocusable( false );	// because on Aqua it looks truncated
							} else {
								ggComboBox.removeActionListener( this );
								ggComboBox.removeAllItems();
							}
							StringItem[] items = (StringItem[]) c.typeConstraints;
							int idx = -1;
							for( int i = 0; i < items.length; i++ ) {
								ggComboBox.addItem( items[ i ]);
								if( items[ i ].getKey().equals( value )) idx = i;
							}
							ggComboBox.setSelectedIndex( idx );
							ggComboBox.addActionListener( this );
							return panelComboBox;
						} else {
							if( ggTextField == null ) {
								ggTextField		= new JTextField();
								panelTextField	= new JPanel( new BorderLayout() );
								panelTextField.add( ggTextField, BorderLayout.NORTH );
								GUIUtil.setDeepFont( ggTextField, GraphicsUtil.smallGUIFont );
//								ggTextField.setMaximumSize( new Dimension(
//									ggTextField.getMaximumSize().width, ggTextField.getPreferredSize().height ));
								ggTextField.addActionListener( this );
							}
							ggTextField.setText( (value == null) ? "" : value.toString() );
							return panelTextField;
						}
	
					case MapManager.Context.TYPE_FILE:
						Integer		type;
						PathField	ggPath;
						if( c.typeConstraints != null && (c.typeConstraints instanceof Integer) ) {
							type = (Integer) c.typeConstraints;
						} else {
							type = new Integer( PathField.TYPE_INPUTFILE );
						}
						ggPath = (PathField) mapPathFields.get( type );
						if( ggPath == null ) {
							ggPath = new PathField( type.intValue(), c.label );
							ggPath.addPathListener( this );
							GUIUtil.setDeepFont( ggPath, GraphicsUtil.smallGUIFont );
							mapPathFields.put( type, ggPath );
						}
						ggPath.setPath( (value == null) || !(value instanceof File) ?
										new File( "" ) : (File) value );
						return ggPath;
						
					default:
						if( ggLabel == null ) {
							ggLabel = new JLabel();
							GUIUtil.setDeepFont( ggLabel, GraphicsUtil.smallGUIFont );
						}
						ggLabel.setText( keys.get( row ).toString() );
						return ggLabel;
					}
				} // synchronized( sync )
			}
			finally {
				lm.releaseShared( doors );
			}
		}

		// from text fields --> editorValue instanceof String
		// from checkboxes --> editorValue instanceof Boolean
		// from comboboxes --> editorValue instanceof StringItem
		public void actionPerformed( ActionEvent e )
		{
			if( e.getSource() == ggTextField ) {
				editorValue = ggTextField.getText();
//System.err.println( "from text field : "+editorValue );
			} else if( e.getSource() == ggCheckBox ) {
				editorValue = new Boolean( ggCheckBox.isSelected() );
//System.err.println( "from checkbox : "+editorValue );
			} else if( e.getSource() == ggComboBox ) {
				Object o	= ggComboBox.getSelectedItem();
				if( (o != null) && (o instanceof StringItem) ) {
					editorValue = ((StringItem) o).getKey();
				} else {
					editorValue = null;
				}
//System.err.println( "from combo box : "+editorValue );
			}
			fireEditingStopped();
		}

		// from number fields --> editorValue instanceof Number
		public void numberChanged( NumberEvent e )
		{
			editorValue = e.getNumber();
			fireEditingStopped();
		}

		// from path fields --> editorValue instanceof File
		public void pathChanged( PathEvent e )
		{
			editorValue = e.getPath();
			fireEditingStopped();
		}
	} // class Renderer
}