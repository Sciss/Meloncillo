package de.sciss.meloncillo.session;

import java.util.List;

public interface MutableSessionCollection
extends SessionCollection
{
	public boolean addAll( Object source, List c );
	public boolean removeAll( Object source, List c );
	public void clear( Object source );
}
