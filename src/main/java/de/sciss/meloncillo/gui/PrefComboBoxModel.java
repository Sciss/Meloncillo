/**
 *	Changelog:
 *		14-Jul-08	copied from EisK 
 */
package de.sciss.meloncillo.gui;

import javax.swing.ComboBoxModel;

/**
 */
public abstract class PrefComboBoxModel
extends PrefListModel
implements ComboBoxModel
{
	private Object selectedItem = null;
	
	public void setSelectedItem( Object anItem )
	{
		selectedItem = anItem;
	}
	
	public Object getSelectedItem()
	{
		return selectedItem;
	}
}
