//
//  WindowHandler.java
//  Meloncillo
//
//  Created by Hanns Holger Rutz on 21.05.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.meloncillo.gui;

import java.awt.Font;

import de.sciss.common.BasicWindowHandler;
import de.sciss.meloncillo.Main;

/**
 * 	@version	0.75, 10-Jun-08
 *	@author		Hanns Holger Rutz
 */
public class WindowHandler
extends BasicWindowHandler
{
	public WindowHandler( Main root )
	{
		super( root );
	}
	
	public Font getDefaultFont()
	{
		return GraphicsUtil.smallGUIFont;
	}
}
