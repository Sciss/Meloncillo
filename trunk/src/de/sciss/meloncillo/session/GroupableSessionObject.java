package de.sciss.meloncillo.session;

//import java.util.List;

public interface GroupableSessionObject
extends SessionObject
{
	public MutableSessionCollection getGroups();
}