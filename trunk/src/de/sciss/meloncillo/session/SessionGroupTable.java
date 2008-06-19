/*
 *  SessionGroupTable.java
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
 *		22-Jan-05	created
 */

package de.sciss.meloncillo.session;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import de.sciss.meloncillo.edit.*;
import de.sciss.meloncillo.gui.*;
import de.sciss.meloncillo.util.*;

import de.sciss.app.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 */
public class SessionGroupTable
extends JTable
{
	public static final int VIEW_RECEIVERS		=	0x00;
	public static final int VIEW_TRANSMITTERS	=	0x01;
	public static final int VIEW_GROUPS			=	0x02;
	public static final int VIEW_OBJECT_MASK	=	0xFF;

	public static final int VIEW_FLAGS			=	0x100;

	private final Session doc;
	private final SessionCollection	collAll, collSelected;
	private final int doors;
	private final Model model;
	
	private static final String[] columnNames = new String[] { null, "flags" };	// XXX replace by Context.label
	private final int columnNum;
	
	private static final int COL_NAME	= 0;
	private static final int COL_FLAGS	= 1;

	private final JTable enc_table = this;
	
	public SessionGroupTable( final Session doc, SessionGroup grp, int views )
	{
		super();

		if( (views & VIEW_FLAGS) == 0 ) {
			columnNum			= 1;
		} else {
			columnNum			= 2;
		}
		
		this.doc	= doc;
//		this.views	= views;
//		this.grp	= grp;
		
		final de.sciss.app.Application	app = AbstractApplication.getApplication();
		
		switch( views & VIEW_OBJECT_MASK ) {
		case VIEW_RECEIVERS:
			collAll			= grp.receivers;
			collSelected	= doc.selectedReceivers;
			doors			= Session.DOOR_RCV;
			columnNames[0]	= app.getResourceString( "labelReceivers" );
			break;
		case VIEW_TRANSMITTERS:
			collAll			= grp.transmitters;
			collSelected	= doc.selectedTransmitters;
			doors			= Session.DOOR_TRNS;
			columnNames[0]	= app.getResourceString( "labelTransmitters" );
			break;
		case VIEW_GROUPS:
			collAll			= grp.groups;
			collSelected	= doc.selectedGroups;
			doors			= Session.DOOR_GRP;
			columnNames[0]	= app.getResourceString( "labelGroups" );
			break;
		default:
			assert false : views;
			collAll			= null;
			collSelected	= null;
			doors			= 0;
		}

		model = new Model();
		this.setModel( model );
		
		if( (views & VIEW_FLAGS) != 0 ) {
			this.setDefaultRenderer( model.getColumnClass( COL_FLAGS ),
				new FlagsRenderer( collAll, doc.bird, doors ));
			this.setDefaultEditor( model.getColumnClass( COL_FLAGS ),
				new FlagsEditor( collAll, doc.bird, doors ));
		}

		collAll.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				if( e.getSource() == enc_table ) return;
//System.err.println( "all sessionCollectionChanged" );

				int i;
				java.util.List	coll;
				Point p;
			
				switch( e.getModificationType() ) {
				case SessionCollection.Event.ACTION_ADDED:
					if( !doc.bird.attemptShared( doors, 250 )) {
						model.fireTableStructureChanged();
						return;
					}
					try	{
						coll = calcRowSpans( e.getCollection() );
						for( i = 0; i < coll.size(); i++ ) {
							p = (Point) coll.get( i );
							model.fireTableRowsInserted( p.x, p.y );
						}
					}
					finally {
						doc.bird.releaseShared( doors );
					}
					break;
				case SessionCollection.Event.ACTION_REMOVED:
					model.fireTableStructureChanged();
//					coll = calcRowSpans( e.getCollection() );
//					for( i = coll.size() - 1 ; i >= 0; i-- ) {
//						p = (Point) coll.get( i );
//						model.fireTableRowsDeleted( p.x, p.y );
//					}
					break;
					
				default:
					break;
				}
			}
			
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}

			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.getSource() == enc_table ) return;

				int i;
				java.util.List coll;
				Point p;
			
				switch( e.getModificationType() ) {
				case SessionObject.OWNER_RENAMED:
					if( !doc.bird.attemptShared( doors, 250 )) {
						model.fireTableStructureChanged();
						return;
					}
					try	{
						coll = calcRowSpans( e.getCollection() );
						for( i = 0; i < coll.size(); i++ ) {
							p = (Point) coll.get( i );
							model.fireTableRowsUpdated( p.x, p.y );
						}
					}
					finally {
						doc.bird.releaseShared( doors );
					}
					break;
					
				default:
					break;
				}
			}
		});
		
		collSelected.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				if( e.getSource() == enc_table ) return;
//System.err.println( "selected sessionCollectionChanged" );
			
				int i;
				java.util.List	coll;
				Point p;
			
				switch( e.getModificationType() ) {
				case SessionCollection.Event.ACTION_ADDED:
					if( !doc.bird.attemptShared( doors, 250 )) {
						model.fireTableStructureChanged();
						return;
					}
					try	{
						coll = calcRowSpans( e.getCollection() );
						for( i = 0; i < coll.size(); i++ ) {
							p = (Point) coll.get( i );
	//System.err.println( "addRowSelectionInterval "+p.x+" ... "+p.y );
							enc_table.addRowSelectionInterval( p.x, p.y );
						}
					}
					finally {
						doc.bird.releaseShared( doors );
					}
					break;
					
				case SessionCollection.Event.ACTION_REMOVED:
//					coll = calcRowSpans( e.getCollection() );
//					for( i = 0; i < coll.size(); i++ ) {
//						p = (Point) coll.get( i );
//System.err.println( "removeRowSelectionInterval "+p.x+" ... "+p.y );
//						enc_table.removeRowSelectionInterval( p.x, p.y );
//					}
					model.fireTableStructureChanged();
					break;
					
				default:
					break;
				}
			}
			
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
			public void sessionObjectChanged( SessionCollection.Event e ) {}
		});
		
		this.getSelectionModel().addListSelectionListener( new ListSelectionListener() {
			public void valueChanged( ListSelectionEvent e )
			{
				if( !doc.bird.attemptShared( doors, 250 )) return;
				try	{
//System.err.println( "valueChanged" );
					java.util.List selected		= new ArrayList();
					java.util.List deselected	= new ArrayList();
					SessionObject so;
					int i, max					= Math.min( e.getLastIndex() + 1, collAll.size() );
				
					for( i = Math.max( 0, e.getFirstIndex() ); i < max; i++ ) {
						so = (SessionObject) collAll.get( i );
						if( enc_table.isRowSelected( i ) && !(collSelected.contains( so ))) {
							selected.add( so );
//System.err.println( "selected.add "+i );
						} else if( !(enc_table.isRowSelected( i )) && collSelected.contains( so )) {
							deselected.add( so );
//System.err.println( "deselected.add "+i );
						}
					}
					if( !selected.isEmpty() ) {
						doc.getUndoManager().addEdit( new EditAddSessionObjects( enc_table, doc, collSelected, selected, doors ));
					}
					if( !deselected.isEmpty() ) {
						doc.getUndoManager().addEdit( new EditRemoveSessionObjects( enc_table, doc, collSelected, deselected, doors ));
					}
				}
				finally {
					doc.bird.releaseShared( doors );
				}
			}
		});

		setBackground( null );
		setShowGrid( false );
		setRowHeight( 18 );
		setSelectionBackground( GraphicsUtil.colrSelection );
	} // constructor
	
	private java.util.List calcRowSpans( java.util.List coll )
	{
		java.util.List rowSpans = new ArrayList();
		int[] indices = new int[ coll.size() ];
		int i, j, min = collAll.size(), max = -1;
		
		for( i = 0; i < indices.length; i++ ) {
			indices[i] = collAll.indexOf( (SessionObject) coll.get( i ));
			if( indices[i] == -1 ) continue;
			if( indices[i] < min ) {
				min = indices[i];
			}
			if( indices[i] > max ) {
				max = indices[i];
			}
		}
findSpan: for( j = min + 1; min <= max; j++ ) {
			for( i = 0; i < indices.length; i++ ) {
				if( indices[i] == j ) {
					continue findSpan;
				}
			}
			rowSpans.add( new Point( min, j - 1 ));
			for( i = 0, min = max + 1; i < indices.length; i++ ) {
				if( indices[i] > j && indices[i] < min ) {
					min = indices[i];
				}
			}
			j = min;
		}
		
		return rowSpans;
	}

	private class FlagsRenderer
	extends FlagsPanel
	implements TableCellRenderer
	{
		protected int flags;
	
		private FlagsRenderer( SessionCollection sc, LockManager lm, int doors )
		{
			super( sc, lm, doors );
		}
	
		public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus,
														int row, int column )
		{
			flags = value == null ? 0 : ((Integer) value).intValue();

			if( row >= collAll.size() ) return null;
				
			this.so = (SessionObject) collAll.get( row );
			updateButtons( flags );
			setBackground( isSelected ? GraphicsUtil.colrSelection : null );
//System.err.println( "getTableCellRendererComponent() ; row "+row+"; column "+column );
			return this;
		}
	}
	
	private class FlagsEditor
	extends AbstractCellEditor
	implements TableCellEditor
	{
		private final FlagsRenderer fr;
	
		private FlagsEditor( SessionCollection sc, LockManager lm, int doors )
		{
			fr = new FlagsRenderer( sc, lm, doors ) {
				protected void setFlags( int mask, boolean set )
				{
					super.setFlags( mask, set );
					
//					if( set ) {
//						flags |= mask;
//					} else {
//						flags &= ~mask;
//					}

					fireEditingStopped();
				}

				protected void broadcastFlags( int mask, boolean set )
				{
					super.broadcastFlags( mask, set );
					
//					model.fireTableDataChanged();	// deletes row selection ;-(
					enc_table.repaint();
				}
			};
		}
	
		public Object getCellEditorValue()
		{
//System.err.println( "query flags "+fr.flags );
			return null; // new Integer( fr.flags );
		}

		public Component getTableCellEditorComponent( JTable table, Object value, boolean isSelected, int row, int column )
		{
			return fr.getTableCellRendererComponent( table, value, isSelected, true, row, column );
		}
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
			if( !doc.bird.attemptShared( doors, 250 )) return 0;
			try	{
				return collAll.size();
			}
			finally {
				doc.bird.releaseShared( doors );
			}
		}
		
		public int getColumnCount()
		{
			return columnNum;
		}
		
		public Object getValueAt( int row, int col )
		{
			if( !doc.bird.attemptShared( doors, 250 )) return null;
			try	{
				if( row >= collAll.size() ) return null;
				
				SessionObject so = (SessionObject) collAll.get( row );
				
				switch( col ) {
				case COL_NAME:
					return( so.getName() );
				case COL_FLAGS:
					return( so.getMap().getValue( SessionObject.MAP_KEY_FLAGS ));
				default:
					return null;
				}
			}
			finally {
				doc.bird.releaseShared( doors );
			}
		}

	    public Class getColumnClass( int col )
		{
			switch( col ) {
			case COL_NAME:
				return String.class;
			case COL_FLAGS:
				return Integer.class;
			default:
				return Object.class;
			}
		}
	
		public boolean isCellEditable( int row, int col )
		{
			switch( col ) {
			case COL_FLAGS:
				return true;
			default:
				return false;
			}
		}
		
		public void setValueAt( Object value, int row, int col )
		{
			if( !doc.bird.attemptExclusive( doors, 250 )) return;
			try	{
				if( row >= collAll.size() ) return;
				
//				SessionObject so = (SessionObject) collAll.get( row );
//				
				switch( col ) {
//				case COL_FLAGS:
//					if( value instanceof Integer ) {
//						so.getMap().putValue( this, SessionObject.MAP_KEY_FLAGS, value );
//					}
//					break;
				default:
					break;
				}
			}
			finally {
				doc.bird.releaseExclusive( doors );
			}
		}
	}
}