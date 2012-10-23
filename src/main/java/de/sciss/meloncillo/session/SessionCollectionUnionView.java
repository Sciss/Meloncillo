package de.sciss.meloncillo.session;

public class SessionCollectionUnionView
extends SessionCollectionView
{
	public static final int RECEIVERS		= 0;
	public static final int TRANSMITTERS	= 1;

	public SessionCollectionUnionView( SessionCollection full, int type, boolean implicitAllGroup, SessionCollection selectedGroupsL )
	{
		super( full, new GroupFilter( full, type, implicitAllGroup, selectedGroupsL ));
	}
		
	private static class GroupFilter
	implements SessionCollectionView.Filter
	{
		private final int				type;
		private final boolean			implicitAllGroup;
		private final SessionCollection	selectedGroupsL;
		
		private GroupFilter( SessionCollection full, int type, boolean implicitAllGroup, SessionCollection selectedGroupsL )
		{
			this.implicitAllGroup	= implicitAllGroup;
			this.selectedGroupsL	= selectedGroupsL;
			this.type				= type;
			
			if( (type < 0) || (type > 1) ) throw new IllegalArgumentException( String.valueOf( type ));
		}
		
		public boolean select( SessionObject so )
		{
//			final GroupableSessionObject gso = (GroupableSessionObject) so;
//			
			if( selectedGroupsL.isEmpty() ) return implicitAllGroup;
			for( int i = 0; i < selectedGroupsL.size(); i++ ) {
				final SessionGroup sg = (SessionGroup) selectedGroupsL.get( i );
				final SessionCollection sc = type == TRANSMITTERS ? sg.getTransmitters() : sg.getReceivers();
				if( sc.contains( so )) return true;
			}
			
			return false;
		}
	}
}
