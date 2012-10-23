package de.sciss.meloncillo.session;

public interface SessionGroup
extends SessionObject
{
	public static final String XML_VALUE_RECEIVERS		= "receivers";
	public static final String XML_VALUE_TRANSMITTERS	= "transmitters";
	public static final String XML_VALUE_GROUPS			= "groups";
	public static final String MAP_KEY_USERIMAGE		= "userimage";

	public SessionCollection getReceivers();
	public SessionCollection getTransmitters();
//	public SessionCollection getGroups();
}
