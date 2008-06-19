/*
 *  VectorTransformFilter.java
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
 *		29-Jun-04   created
 *		14-Jul-04   integrated new RenderSource concept
 *		24-Jul-04   directly extends JPanel / implements RenderPlugIn
 *		02-Sep-04	commented
 *		01-Jan-05	uses DynamicPrefChangeManager; added online help;
 *					center anchor point is fully functional in cartesian mode
 */
 
// XXX unklar (pruefen): reihenfolge und vollstaendigkeit und keine
// doppelten aufrufe von producer+consumer cancels

package de.sciss.meloncillo.render;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.PrefComboBox;
import de.sciss.gui.PrefCheckBox;
import de.sciss.gui.PrefNumberField;
import de.sciss.gui.StringItem;
import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.IOUtil;
import de.sciss.io.InterleavedStreamFile;
import de.sciss.io.Span;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.math.VectorTransformer;
import de.sciss.meloncillo.plugin.PlugInContext;
import de.sciss.meloncillo.session.Session;
import de.sciss.meloncillo.util.PrefsUtil;
import de.sciss.util.NumberSpace;

/**
 *	A trajectory filter plug-in
 *	that uses the <code>Vector...</code>
 *	classes of the math package to transform
 *	x and y coordinates individually.
 *	Alternatively transformation is done
 *	in a polar fashion were a process
 *	can be applied to the radius and 
 *	angle of the trajectories with
 *	respect to a user defined central point
 *	on the surface.
 *	<p>
 *	This method filters out the vector functions
 *	that implement the <code>RenderPlugIn</code>
 *	interface; this is true for the apply-function
 *	and raise-and-muliply classes. Others
 *	may follow in future versions.
 *	<p>
 *	This class acts as a new host for the
 *	algorithms render plug-in interface. It implements
 *	the <code>RenderConsumer</code> interface to
 *	re-interlace the transformed data and perform
 *	postprocessing such as polar->cartesian conversion.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *	@todo		check if the vector algorithm's producerFinish
 *				methods get called at all
 */
public class VectorTransformFilter
extends JPanel
implements  RenderPlugIn, RenderHost, RenderConsumer, PreferenceChangeListener
{
	private Main					root;
	private Session					doc;
	private Preferences				classPrefs;
	private RenderHost				mainHost	= null;
//	private LaterInvocationManager  lim			= new LaterInvocationManager( this );
	private RenderPlugIn[] prod	= new RenderPlugIn[ 2 ];

	private PrefCheckBox		ggApplyX, ggApplyY;
	private PrefComboBox		ggTransformX, ggTransformY, ggMode;
	private PrefNumberField		ggCenterX, ggCenterY;
	private JScrollPane			funcGUIX, funcGUIY;
	private int					modeState   = -1;   // GUI display state: 0 for cartesian, 1 for polar

	// option map keys
	private static final String	KEY_RENDERINFO	= VectorTransformFilter.class.getName();

	// class prefs (session node)
	private static final String KEY_APPLYX  = "applyx";
	private static final String KEY_APPLYY  = "applyy";
	private static final String KEY_TRANSX  = "transformx";
	private static final String KEY_TRANSY  = "transformy";
	private static final String KEY_CENTERX = "centerx";
	private static final String KEY_CENTERY = "centery";
	private static final String KEY_MODE	= "mode";
	private static final String NODE_TRANSX = "trnsxprefs";
	private static final String NODE_TRANSY = "trnsyprefs";
	
	// the ones that need to be checked in the dynamic listening stages
	private static final String[] KEYS		= { KEY_TRANSX, KEY_TRANSY, KEY_APPLYX, KEY_APPLYY, KEY_MODE };

	// context options map
	private static final String	KEY_CONSC		= "consc";
	public  static final String	KEY_DIMENSION	= "dim";	// Integer : 0 = X, 1 = Y

	private static final int MODE_CARTESIAN		= 0;
	private static final int MODE_POLAR			= 1;

	private final StringItem[] modes;

	/**
	 *	Just calls the super class constructor.
	 *	Real initialization is in the init method
	 */
	public VectorTransformFilter()
	{
		super();
		
		final de.sciss.app.Application	app = AbstractApplication.getApplication();
		
		modes	= new StringItem[] {
			new StringItem( "cartesian", app.getResourceString( "renderVTModeCartesian" )),
			new StringItem( "polar", app.getResourceString( "renderVTModePolar" ))
		};
	}

	public void init( Main root, Session doc )
	{
		this.root   = root;
		this.doc	= doc;
		
		String className	= getClass().getName();
		classPrefs			= AbstractApplication.getApplication().getUserPrefs().node(
								PrefsUtil.NODE_SESSION ).node(
									className.substring( className.lastIndexOf( '.' ) + 1 ));
		createSettingsView();

		// --- Listener ---
        new DynamicAncestorAdapter( new DynamicPrefChangeManager( classPrefs, KEYS, this )).addTo( this );
	}

	/**
	 *	Vector algorithm specific
	 *	GUI is integrated as sub panels
	 *	in the main GUi.
	 */
	private void createSettingsView()
	{
		JLabel							lb;
		final java.util.List			collAlgorithms  = VectorTransformer.getTransforms();
		Map								map;
		StringItem						item;
		Boolean							b;
		final GridBagLayout				lay				= new GridBagLayout();
		final GridBagConstraints		con				= new GridBagConstraints();
		final Insets					ascetic			= new Insets( 2, 4, 2, 4 );
		final Insets					bourgeois		= new Insets( 2, 24, 14, 4 );
		final de.sciss.app.Application	app				= AbstractApplication.getApplication();

		this.setLayout( lay );

		ggMode			= new PrefComboBox();
		for( int i = 0; i < modes.length; i++ ) {
			ggMode.addItem( modes[i] );
		}
		ggMode.setSelectedIndex( MODE_CARTESIAN );
		ggMode.setPreferences( classPrefs, KEY_MODE );

		ggApplyX		= new PrefCheckBox(); // Main.getResourceString( "renderVTApplyX" ));
		ggApplyY		= new PrefCheckBox(); // Main.getResourceString( "renderVTApplyY" ));
		ggTransformX	= new PrefComboBox();
		ggTransformY	= new PrefComboBox();
		ggCenterX		= new PrefNumberField();
		ggCenterX.setSpace( new NumberSpace( 0.0, 1.0, 0.0 ));
		ggCenterX.setNumber( new Double( 0.5 ));
		ggCenterY		= new PrefNumberField();
		ggCenterX.setSpace( new NumberSpace( 0.0, 1.0, 0.0 ));
		ggCenterY.setNumber( new Double( 0.5 ));
		funcGUIX		= new JScrollPane( JScrollPane.VERTICAL_SCROLLBAR_NEVER,
										   JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
		funcGUIY		= new JScrollPane( JScrollPane.VERTICAL_SCROLLBAR_NEVER,
										   JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
		funcGUIX.setViewportBorder( BorderFactory.createEmptyBorder( 2, 2, 2, 2 ));
		funcGUIY.setViewportBorder( BorderFactory.createEmptyBorder( 2, 2, 2, 2 ));
		
		for( int i = 0; i < collAlgorithms.size(); i++ ) {
			map		= (Map) collAlgorithms.get( i );
			b		= (Boolean) map.get( VectorTransformer.KEY_RENDERPLUGIN );
			if( b == null || !b.booleanValue() ) continue;
			item	= new StringItem( map.get( Main.KEY_CLASSNAME ).toString(),
									  map.get( Main.KEY_HUMANREADABLENAME ));
			ggTransformX.addItem( item );
			ggTransformY.addItem( item );
		}

		ggApplyX.setPreferences( classPrefs, KEY_APPLYX );
		ggApplyY.setPreferences( classPrefs, KEY_APPLYY );
		ggTransformX.setPreferences( classPrefs, KEY_TRANSX );
		ggTransformY.setPreferences( classPrefs, KEY_TRANSY );
		ggCenterX.setPreferences( classPrefs, KEY_CENTERX );
		ggCenterY.setPreferences( classPrefs, KEY_CENTERY );

		lb				= new JLabel( app.getResourceString( "renderVTMode" ));
		con.anchor		= GridBagConstraints.WEST;
		con.gridwidth	= 1;
		con.fill		= GridBagConstraints.HORIZONTAL;
		con.insets		= ascetic;
		lay.setConstraints( lb, con );
		this.add( lb );
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggMode, con );
		this.add( ggMode );
//        HelpGlassPane.setHelp( ggMode, "FilterVectorTransformCoord" );	// EEE

		lb				= new JLabel( app.getResourceString( "renderVTCenterX" ));
		con.gridwidth	= 1;
		con.weightx		= 0.0;
		lay.setConstraints( lb, con );
		this.add( lb );
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggCenterX, con );
		this.add( ggCenterX );
//        HelpGlassPane.setHelp( ggCenterX, "FilterVectorTransformCoord" );	// EEE

		lb				= new JLabel( app.getResourceString( "renderVTCenterY" ));
		con.gridwidth	= 1;
		con.weightx		= 0.0;
		lay.setConstraints( lb, con );
		this.add( lb );
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggCenterY, con );
		this.add( ggCenterY );
//        HelpGlassPane.setHelp( ggCenterY, "FilterVectorTransformCoord" );	// EEE

		con.gridwidth	= 1;
		con.weightx		= 0.0;
		lay.setConstraints( ggApplyX, con );
		this.add( ggApplyX );
//        HelpGlassPane.setHelp( ggApplyX, "FilterVectorTransformFunc" );	// EEE
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggTransformX, con );
		this.add( ggTransformX );
		con.fill		= GridBagConstraints.BOTH;
		con.insets		= bourgeois;
		lay.setConstraints( funcGUIX, con );
		this.add( funcGUIX );

		con.gridwidth	= 1;
		con.weightx		= 0.0;
		con.fill		= GridBagConstraints.HORIZONTAL;
		con.insets		= ascetic;
		lay.setConstraints( ggApplyY, con );
		this.add( ggApplyY );
//        HelpGlassPane.setHelp( ggApplyY, "FilterVectorTransformFunc" );	// EEE
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggTransformY, con );
		this.add( ggTransformY );
		con.fill		= GridBagConstraints.BOTH;
		con.insets		= bourgeois;
		lay.setConstraints( funcGUIY, con );
		this.add( funcGUIY );

//        HelpGlassPane.setHelp( this, "FilterVectorTransform" );	// EEE
	}

	// --- GUI Presentation ---
	
	public JComponent getSettingsView( PlugInContext context )
	{
		return this;
	}

	private void switchProducer( String key )
	{
		final String					className   = classPrefs.get( key, null );
		RenderPlugIn					producer;
		JScrollPane						pane;
		JComponent						view;
		Window							ancestor;
		String							trnsPrefsNode;
		final de.sciss.app.Application	app			= AbstractApplication.getApplication();

		try {
			if( key.equals( KEY_TRANSX )) {
				prod[ 0 ] = null;
			} else if( key.equals( KEY_TRANSY )) {
				prod[ 1 ] = null;
			} else {
				assert false : key;
			}
			
			if( className != null ) {
				try {
					producer = (RenderPlugIn) Class.forName( className ).newInstance();
					producer.init( root, doc );
					if( key.equals( KEY_TRANSX )) {
						prod[ 0 ]		= producer;
						pane			= funcGUIX;
						trnsPrefsNode   = NODE_TRANSX;
					} else {
						prod[ 1 ]		= producer;
						pane			= funcGUIY;
						trnsPrefsNode   = NODE_TRANSY;
					}
					((VectorTransformer) producer).setPreferences( classPrefs.node( trnsPrefsNode ).node(
						className.substring( className.lastIndexOf( '.' ) + 1 )));
					view	= producer.getSettingsView( null );		// XXX shouldn't be null
//					GUIUtil.setDeepFont( view, fnt );
					AbstractWindowHandler.setDeepFont( view );
					pane.setViewportView( view );
					ancestor = (Window) SwingUtilities.getAncestorOfClass( Window.class, this );
					if( ancestor != null ) ancestor.pack();
				}
				catch( InstantiationException e1 ) {
					GUIUtil.displayError( this, e1, app.getResourceString( "errInitPlugIn" ));
				}
				catch( IllegalAccessException e2 ) {
					GUIUtil.displayError( this, e2, app.getResourceString( "errInitPlugIn" ));
				}
				catch( ClassNotFoundException e3 ) {
					GUIUtil.displayError( this, e3, app.getResourceString( "errInitPlugIn" ));
				}
			}
//			success = true; // reContext();
		}
		finally {
//			ggRender.setEnabled( success );
		}
	}

	/**
	 *	Prepares rendering and acts
	 *	as a host for the vector algorithms
	 *	whose respective <code>producerBegin</code>
	 *	methods get called.
	 */
	public boolean producerBegin( RenderContext context, RenderSource source )
	throws IOException
	{
		int				trnsIdx, i;
		RenderInfo		info	= new RenderInfo();
		ConsumerContext consc;
		boolean			success = false;
		Object			o;
		AudioFileDescr	afd		= new AudioFileDescr();
		AudioFileDescr	afd2;

		mainHost				= (RenderHost) context.getHost();
		
		context.moduleMap.put( KEY_RENDERINFO, info );
		info.outLength			= context.getTimeSpan().getLength();
		info.progLen			= info.outLength << 1;		// arbit.
		info.progOff			= 0;
		info.apply[ 0 ]			= ggApplyX.isSelected();
		info.apply[ 1 ]			= ggApplyY.isSelected();
		o						= ggMode.getSelectedItem();
		info.polar				= o instanceof StringItem ?
								  ((StringItem) o).getKey().equals( modes[ MODE_POLAR ].getKey() ) : false;
//		info.polar			  &&= info.applyX || info.applyY; // if both are bypassed no need to convert

//		if( info.polar ) {
			info.centerX		= ggCenterX.getNumber().floatValue();
			info.centerY		= ggCenterY.getNumber().floatValue();
//		}
		info.convBuf			= new float[ source.numTrns ][];

		// requests
		for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
			source.trajRequest[ trnsIdx ]   = true;
		}
		info.source			= new RenderSource( source.numTrns, source.numRcv );
		afd.type			= AudioFileDescr.TYPE_AIFF;
		afd.channels		= source.numTrns;
		afd.rate			= context.getSourceRate();
		afd.bitsPerSample	= 32;
		afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
		
		for( i = 0; i < 2; i++ ) {
			info.contexts[ i ]	= new RenderContext( this, context.getReceivers(),
										context.getTransmitters(), context.getTimeSpan(),
										context.getSourceRate() );
			info.contexts[ i ].setOption( RenderContext.KEY_CONSUMER, this );
			info.tempFiles[ i ] = IOUtil.createTempFile();
			afd2				= new AudioFileDescr( afd );
			afd2.file			= info.tempFiles[ i ];
			info.iffs[ i ]		= AudioFile.openAsWrite( afd2 );
			consc				= new ConsumerContext();
			consc.iff			= info.iffs[ i ];
			consc.convBuf		= info.convBuf;
			info.contexts[ i ].setOption( KEY_CONSC, consc );
			info.contexts[ i ].setOption( KEY_DIMENSION, new Integer( i ));
			info.contexts[ i ].getModifiedOptions();   // clear state
		}

		try {
			for( i = 0; i < 2; i++ ) {
				if( info.apply[ i ]) {
					if( prod[ i ] != null ) {
//						if( !((VectorTransformer) prod[ i ]).query( this )) return false;	// XXX
						if( !prod[ i ].producerBegin( info.contexts[ i ], info.source )) return false;
					} else {
						throw new IOException( AbstractApplication.getApplication().getResourceString(
							"renderVTNoProducer" ));
					}
				}
			}
			success = true;
		}
		finally {
			if( !success ) cleanUp( info, source );
		}

		return success;
	}
	
	/**
	 *	Forwards a block of data to the 
	 *	respective <code>producerRender</code>
	 *	methods of the vector algorithms.
	 *	Cartesian -> polar conversion is
	 *	performed beforehand if necessary.
	 */
	public boolean producerRender( RenderContext context, RenderSource source )
	throws IOException
	{
		RenderInfo	info			= (RenderInfo) context.moduleMap.get( KEY_RENDERINFO );
		int			i, j, trnsIdx;
		float		f1, f2;
		boolean		success			= false;

		info.source.blockSpan		= source.blockSpan;
		info.source.blockBufOff		= source.blockBufOff;
		info.source.blockBufLen		= source.blockBufLen;
		info.source.trajBlockBuf	= source.trajBlockBuf;
		
		try {
			// rect -> polar
			if( info.polar ) {
				for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
					for( i = source.blockBufOff, j = i + source.blockBufLen; i < j; i++ ) {
						f1  = source.trajBlockBuf[ trnsIdx ][ 0 ][ i ] - info.centerX;
						f2  = source.trajBlockBuf[ trnsIdx ][ 1 ][ i ] - info.centerY;
						source.trajBlockBuf[ trnsIdx ][ 0 ][ i ] = (float) Math.sqrt( f1*f1 + f2*f2 );
						source.trajBlockBuf[ trnsIdx ][ 1 ][ i ] = (float) (Math.atan2( f2, f1 ) / Math.PI);
					}
				}
			} else if( (info.centerX != 0.0f) || (info.centerY != 0.0f) ) {	// rect shift
				for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
					for( i = source.blockBufOff, j = i + source.blockBufLen; i < j; i++ ) {
						source.trajBlockBuf[ trnsIdx ][ 0 ][ i ] -= info.centerX;
						source.trajBlockBuf[ trnsIdx ][ 1 ][ i ] -= info.centerY;
					}
				}
			}
			// transform
			for( i = 0; i < 2; i++ ) {
				if( info.apply[ i ]) {
					prod[ i ].producerRender( info.contexts[ i ], info.source );
				} else {
					for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
						info.convBuf[ trnsIdx ] = source.trajBlockBuf[ trnsIdx ][ i ];
					}
					info.iffs[ i ].writeFrames( info.convBuf, source.blockBufOff, source.blockBufLen );
				}
			}
			info.progOff   += source.blockBufLen;
			success			= true;
			((RenderHost) context.getHost()).setProgression( (float) info.progOff / (float) info.progLen );
		}
		finally {
			if( !success ) cleanUp( info, source );
		}

		return success;
	}
	
	/**
	 *	Finishes rendering. The separate
	 *	temp files for x and y channels
	 *	are re-interleaved, and in case of
	 *	the polar mode, a polar -> cartesian
	 *	conversion is made before the interleaved
	 *	data is passed to the overall consumer
	 *	(i.e. the filter dialog).
	 */
	public boolean producerFinish( RenderContext context, RenderSource source )
	throws IOException
	{
		RenderInfo			info		= (RenderInfo) context.moduleMap.get( KEY_RENDERINFO );
		boolean				success		= false;
		int					trnsIdx, i, j;
		float				f1;
		double				d1;
		Integer				num;
		int					blockBufSize;
		long				startPos;
		float[][]			tempBuf;

		info.consumer = (RenderConsumer) context.getOption( RenderContext.KEY_CONSUMER );
		if( info.consumer == null ) return true;

		try {
			info.source		= new RenderSource( source );
			if( !info.consumer.consumerBegin( context, info.source )) return false;
			num					= (Integer) context.getOption( RenderContext.KEY_MINBLOCKSIZE );
			i					= num == null ? 2 : num.intValue();
			num					= (Integer) context.getOption( RenderContext.KEY_MAXBLOCKSIZE );
			j					= num == null ? 0x7FFFFFFF : num.intValue();
			num					= (Integer) context.getOption( RenderContext.KEY_PREFBLOCKSIZE );
			blockBufSize		= num == null ? Math.max( i, Math.min( j, 1024 )) : num.intValue();
			info.source.trajBlockBuf = new float[ source.numTrns ][ 2 ][ blockBufSize ];
			info.source.blockBufOff  = 0;
			info.source.blockSpan	= new Span( context.getTimeSpan().getStart(), context.getTimeSpan().getStart() );
			tempBuf				= new float[ source.numTrns ][ blockBufSize ];

			for( i = 0; i < 2; i++ ) {
				info.iffs[ i ].seekFrame( 0 );
			}
			for( startPos = 0; startPos < info.outLength; ) {
				info.source.blockBufLen = (int) Math.min( blockBufSize, info.outLength - startPos );
				for( i = 0; i < 2; i++ ) {
					info.iffs[ i ].readFrames( tempBuf, 0, info.source.blockBufLen );
					for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
						System.arraycopy( tempBuf[ trnsIdx ], 0, info.source.trajBlockBuf[ trnsIdx ][ i ], 0,
										  info.source.blockBufLen );
					}
				}

				// polar -> rect
				if( info.polar ) {
					for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
						for( i = 0; i < info.source.blockBufLen; i++ ) {
							f1  = info.source.trajBlockBuf[ trnsIdx ][ 0 ][ i ];
							d1  = info.source.trajBlockBuf[ trnsIdx ][ 1 ][ i ] * Math.PI;
							info.source.trajBlockBuf[ trnsIdx ][ 0 ][ i ] =
								(float) (info.centerX + f1 * Math.cos( d1 ));
							info.source.trajBlockBuf[ trnsIdx ][ 1 ][ i ] =
								(float) (info.centerY + f1 * Math.sin( d1 ));
						}
					}
				} else if( (info.centerX != 0.0f) || (info.centerY != 0.0f) ) {	// undo rect shift
					for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
						for( i = 0; i < info.source.blockBufLen; i++ ) {
							info.source.trajBlockBuf[ trnsIdx ][ 0 ][ i ] += info.centerX;
							info.source.trajBlockBuf[ trnsIdx ][ 1 ][ i ] += info.centerY;
						}
					}
				}
				info.source.blockSpan = new Span( info.source.blockSpan.getStop(),
												   info.source.blockSpan.getStop() + info.source.blockBufLen );
				if( !info.consumer.consumerRender( context, info.source )) return false;
				info.progOff  += info.source.blockBufLen;
				startPos	  += info.source.blockBufLen;
				((RenderHost) context.getHost()).setProgression( (float) info.progOff / (float) info.progLen );
			}

			success = info.consumer.consumerFinish( context, info.source );
		}
		finally {
			if( !success ) {
				try {
					info.consumer.consumerCancel( context, info.source );
				}
				catch( IOException e1 ) {
					((RenderHost) context.getHost()).setException( e1 );
				}
			}
			cleanUp( info, source );
		}

		((RenderHost) context.getHost()).setProgression( 1.0f );
		return success;
	}
	
	public void producerCancel( RenderContext context, RenderSource source )
	throws IOException
	{
		RenderInfo info = (RenderInfo) context.moduleMap.get( KEY_RENDERINFO );

		cleanUp( info, source );
	}
	
	private void cleanUp( RenderInfo info, RenderSource source )
	{
		int i;
	
		mainHost = null;

		if( info.iffs != null ) {
			for( i = 0; i < info.iffs.length; i++ ) {
				if( info.iffs[i] != null ) {
					try {
						info.iffs[i].close();
					} catch( IOException e1 ) {}
					info.iffs[i]  = null;
				}
			}
		}
		if( info.tempFiles != null ) {
			for( i = 0; i < info.tempFiles.length; i++ ) {
				if( info.tempFiles[i] != null ) {
					info.tempFiles[i].delete();
					info.tempFiles[i] = null;
				}
			}
		}
		
		for( i = 0; i < 2; i++ ) {
			if( info.contexts != null && info.contexts[ i ] != null &&
				info.apply != null && info.apply[ i ] && prod[ i ] != null ) {

				try {
					prod[ i ].producerCancel( info.contexts[ i ], source );
				} catch( IOException e1 ) {}
			}
		}
	}

// ---------------- RenderConsumer interface ---------------- 

	/**
	 *	Gathers the transformed data
	 *	in a temp file. There are two
	 *	different contexts though they
	 *	both use the same consumer (this object).
	 *	This class uses a special option
	 *	to distinguish these two contexts
	 *	in terms of the "channel" (x or y).
	 *	Re-interleaving these channels is
	 *	done in the <code>producerFinish</code> method.
	 */
	public boolean consumerRender( RenderContext context, RenderSource source )
	throws IOException
	{
		ConsumerContext consc   = (ConsumerContext) context.getOption( KEY_CONSC );
		int				dim		= ((Integer) context.getOption( KEY_DIMENSION )).intValue();
		int				trnsIdx;

		for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
			consc.convBuf[ trnsIdx ] = source.trajBlockBuf[ trnsIdx ][ dim ];
		}
		consc.iff.writeFrames( consc.convBuf, source.blockBufOff, source.blockBufLen );

		return true;
	}

	public boolean consumerBegin( RenderContext context, RenderSource source )
	throws IOException
	{
		return true;
	}

	public boolean consumerFinish( RenderContext context, RenderSource source )
	throws IOException
	{
		return true;
	}

	public void consumerCancel( RenderContext context, RenderSource source )
	throws IOException
	{}

// ---------------- RenderHost interface ---------------- 

	public void	showMessage( int type, String text )
	{
		if( mainHost != null ) mainHost.showMessage( type, text );
	}
	
	public void setProgression( float p )
	{
		// ...
	}
	
	public void setException( Exception e )
	{
		if( mainHost != null ) mainHost.setException( e );
	}

	public boolean isRunning()
	{
		if( mainHost != null ) return mainHost.isRunning();
		else return false;
	}

// ---------------- LaterInvocationManager.Listener interface ---------------- 

	// o instanceof PreferenceChangeEvent
	/**
	 *	Deferred preference changes here
	 */
	public void preferenceChange( PreferenceChangeEvent pce)
	{
		final String					key		= pce.getKey();
		final String					value   = pce.getNewValue();
		String							oldValue;
		boolean							b;
		Window							ancestor;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();

		if( key.equals( KEY_TRANSX )) {
			oldValue = prod[ 0 ] != null ? prod[ 0 ].getClass().getName() : null;
			if( !(oldValue == null && value == null) ||
				((oldValue != null && value != null) && !oldValue.equals( value ))) {

				switchProducer( key );
			}
		} else if( key.equals( KEY_TRANSY )) {
			oldValue = prod[ 1 ] != null ? prod[ 1 ].getClass().getName() : null;
			if( !(oldValue == null && value == null) ||
				((oldValue != null && value != null) && !oldValue.equals( value ))) {

				switchProducer( key );
			}

		} else if( key.equals( KEY_APPLYX )) {
			b = value != null ? new Boolean( value ).booleanValue() : false;
			if( ggTransformX.isEnabled() != b ) {
				ggTransformX.setEnabled( b );
			}
			if( funcGUIX.isVisible() != b ) {
				funcGUIX.setVisible( b );
				ancestor = (Window) SwingUtilities.getAncestorOfClass( Window.class, this );
				if( ancestor != null ) ancestor.pack();
			}
		} else if( key.equals( KEY_APPLYY )) {
			b = value != null ? new Boolean( value ).booleanValue() : false;
			if( ggTransformY.isEnabled() != b ) {
				ggTransformY.setEnabled( b );
			}
			if( funcGUIY.isVisible() != b ) {
				funcGUIY.setVisible( b );
				ancestor = (Window) SwingUtilities.getAncestorOfClass( Window.class, this );
				if( ancestor != null ) ancestor.pack();
			}

		} else if( key.equals( KEY_MODE )) {
			for( int i = 0; i < modes.length; i++ ) {
				if( modes[i].getKey().equals( value )) {
					if( modeState != i ) {
						modeState = i;
//						ggCenterX.setEnabled( modeState == 1 );
//						ggCenterY.setEnabled( modeState == 1 );
						switch( modeState ) {
						case MODE_CARTESIAN:
							ggApplyX.setText( app.getResourceString( "labelX" ));
							ggApplyY.setText( app.getResourceString( "labelY" ));
							break;
						case MODE_POLAR:
							ggApplyX.setText( app.getResourceString( "labelRadius" ));
							ggApplyY.setText( app.getResourceString( "labelAngle" ));
							break;
						}
					}
					break;
				}
			}
		}
	}

// -------- internal classes --------
	private class RenderInfo
	{
		private long						outLength, progOff, progLen;
		private RenderContext[]				contexts	= new RenderContext[ 2 ];
		private RenderSource				source;
		private boolean[]					apply		= new boolean[ 2 ];
		private boolean						polar;
		private float						centerX, centerY;
		private File[]						tempFiles   = new File[ 2 ];
		private InterleavedStreamFile[]		iffs		= new InterleavedStreamFile[ 2 ];
		private float[][]					convBuf;	// [numTrns][]
		private RenderConsumer				consumer;   // host consumption
	}

	private class ConsumerContext
	{
		private InterleavedStreamFile	iff;
		private float[][]				convBuf;	// [numTrns][]
	}
}