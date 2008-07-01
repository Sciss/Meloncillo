package de.sciss.meloncillo.plugin;

import java.util.HashMap;

import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.util.MapManager;

public class PlugInManager
extends MapManager
{
	private static PlugInManager instance;
	
	public PlugInManager( Main root )
	{
		super( root, new HashMap() );
		if( instance != null ) throw new IllegalStateException( "PlugInManager is a singleton" );
		instance = this;
	}
	
	public static PlugInManager getInstance()
	{
		return instance;
	}
}
