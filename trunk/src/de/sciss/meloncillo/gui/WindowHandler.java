//
//  WindowHandler.java
//  Meloncillo
//
//  Created by Hanns Holger Rutz on 21.05.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.meloncillo.gui;

import java.awt.Font;

import de.sciss.gui.AbstractWindowHandler;

/**
 * 	@version	0.75, 10-Jun-08
 *	@author		Hanns Holger Rutz
 */
public class WindowHandler
extends AbstractWindowHandler
{
	public WindowHandler()
	{
		super();
	}
	
	public Font getDefaultFont()
	{
		return GraphicsUtil.smallGUIFont;
	}
}
