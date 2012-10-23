package de.sciss.meloncillo.session;

// XXX NOT NEEDED XXX
public interface MutableSessionGroup
extends SessionGroup
{
	public void add( Object source, GroupableSessionObject so );
	public void remove( Object source, GroupableSessionObject so );
}
