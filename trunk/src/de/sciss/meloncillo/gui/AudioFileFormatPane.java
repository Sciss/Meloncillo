/*
 *  AudioFileFormatPane.java
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
 *		24-May-04   created
 *		31-Jul-04   commented. the gain gadget was changed to PrefNumberField.
 *					interface is changed from PreferenceEntrySync to
 *					PreferenceNodeSync.
 */

package de.sciss.meloncillo.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.*;
import javax.swing.*;

import de.sciss.util.NumberSpace;

import de.sciss.app.*;
import de.sciss.gui.*;
import de.sciss.io.*;

/**
 *  A multi component panel
 *  that provides gadgets for
 *  specification of the output
 *  format of an audio file,
 *  such as file format, resolution
 *  or sample rate. It implements
 *  the <code>PreferenceNodeSync</code>
 *  interface, allowing the automatic
 *  saving and recalling of its gadget's
 *  values from/to preferences.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 10-Jun-08
 *
 *  @see	de.sciss.meloncillo.util.PreferenceNodeSync
 *  @see	de.sciss.meloncillo.io.AudioFileDescr
 *
 *  @todo   sample rates should be user adjustable through a
 *			JComboBox with an editable field. this to-do has
 *			low priority since meloncillo is not really
 *			interested in audio files.
 */
public class AudioFileFormatPane
extends JPanel
implements ItemListener, PreferenceNodeSync
{
	/**
	 *  Constructor-Flag : create file type gadget
	 */
	public static final int FORMAT		= 1 << 0;
	/**
	 *  Constructor-Flag : create sample encoding gadget
	 */
	public static final int ENCODING	= 1 << 1;
	/**
	 *  Constructor-Flag : create sample rate gadget
	 */
	public static final int RATE		= 1 << 2;
	/**
	 *  Constructor-Flag : create gain gadget
	 */
	public static final int GAIN		= 1 << 4;
	/**
	 *  Constructor-Flag : create normalize option
	 */
	public static final int NORMALIZE	= 1 << 5;

	/**
	 *  Constructor-Flag : conventient combination
	 *  of <code>FORMAT</code>, <code>ENCODING</code> and <code>RATE</code>.
	 */
	public static final int FORMAT_ENCODING_RATE	= FORMAT | ENCODING | RATE;
	/**
	 *  Constructor-Flag : conventient combination
	 *  of <code>GAIN</code> and <code>NORMALIZE</code>.
	 */
	public static final int GAIN_NORMALIZE			= GAIN | NORMALIZE;
	
	private static final int[] BITSPERSMP			= { 16, 24, 32, 32 };   // idx corresp. to ENCODING_ITEMS
	private static final int[] ENCODINGS			= {						// idx corresp. to ENCODING_ITEMS
		AudioFileDescr.FORMAT_INT, AudioFileDescr.FORMAT_INT,
		AudioFileDescr.FORMAT_INT, AudioFileDescr.FORMAT_FLOAT
	};
	private static final StringItem[]   ENCODING_ITEMS  = {
		new StringItem( "int16", "16-bit int" ),
		new StringItem( "int24", "24-bit int" ),
		new StringItem( "int32", "32-bit int" ),
		new StringItem( "float32", "32-bit float" )
	};
	private static final float[] RATES = {    // idx corresp. to RATE_ITEMS
		32000.0f, 44100.0f, 48000.0f, 88200.0f, 96000.0f
	};
	private static final StringItem[] RATE_ITEMS = {
		new StringItem( "32000", "32 kHz" ),
		new StringItem( "44100", "44.1 kHz" ),
		new StringItem( "48000", "48 kHz" ),
		new StringItem( "88200", "88.2 kHz" ),
		new StringItem( "96000", "96 kHz" )
	};
	private static final int			DEFAULT_ENCODING= 1;		// default int24
	private static final int			DEFAULT_RATE	= 1;		// default 44.1 kHz
	private static final double			DEFAULT_GAIN	= 0.0;		// default 0 dB
	private static final boolean		DEFAULT_NORMALIZE= true;	// default normalization
	
	// prefs keys
	private static final String		KEY_FORMAT		= "format";
	private static final String		KEY_ENCODING	= "encoding";
	private static final String		KEY_RATE		= "rate";
	private static final String		KEY_GAIN		= "gain";
	private static final String		KEY_NORMALIZE	= "normalize";
	
	private JLabel				lbGainType;
	private PrefCheckBox		ggNormalize = null;
	private PrefComboBox		ggFormat	= null;
	private PrefComboBox		ggEncoding	= null;
	private PrefComboBox		ggRate		= null;
	private PrefNumberField		ggGain		= null;
	
	/**
	 *  Construct a new AudioFileFormatPane with the
	 *  shown components specified by the given flags.
	 *
	 *  @param  flags   a bitwise OR combination of
	 *					gadget creation flags such as FORMAT or GAIN_NORMALIZE
	 */
	public AudioFileFormatPane( int flags )
	{
		super();
		
		LayoutManager   lay		= new SpringLayout();
		int				rows	= 0;
		int				cols;
		int				i;
		StringItem[]	items;

		setLayout( lay );
		if( (flags & FORMAT_ENCODING_RATE) != 0 ) {
			rows++;
			cols	= 0;
			if( (flags & FORMAT) != 0 ) {
				ggFormat = new PrefComboBox();
				items   = AudioFileDescr.getFormatItems();
				for( i = 0; i < items.length; i++ ) {
					ggFormat.addItem( items[i] );
				}
				ggFormat.setSelectedIndex( 0 );
				add( ggFormat );
				cols++;
			}
			if( (flags & ENCODING) != 0 ) {
				ggEncoding = new PrefComboBox();
				items   = ENCODING_ITEMS;
				for( i = 0; i < items.length; i++ ) {
					ggEncoding.addItem( items[i] );
				}
				ggEncoding.setSelectedIndex( DEFAULT_ENCODING );
				add( ggEncoding );
				cols++;
			}
			if( (flags & RATE) != 0 ) {
				ggRate = new PrefComboBox();
				items   = RATE_ITEMS;
				for( i = 0; i < items.length; i++ ) {
					ggRate.addItem( items[i] );
				}
				ggRate.setSelectedIndex( DEFAULT_RATE );
				add( ggRate );
				cols++;
			}
			for( ; cols < 3; cols++ ) add( new JLabel() );
		}

		if( (flags & GAIN_NORMALIZE) != 0 ) {
			rows++;
			cols	= 0;
			if( (flags & GAIN) != 0 ) {
				ggGain  = new PrefNumberField();
				ggGain.setSpace( new NumberSpace(
					Double.NEGATIVE_INFINITY, 384.0, 0.01 ));
				ggGain.setNumber( new Double( DEFAULT_GAIN ));
				add( ggGain );
				cols++;
				lbGainType  = new JLabel();
				add( lbGainType );
				cols++;
			}
			if( (flags & NORMALIZE) != 0 ) {
				ggNormalize = new PrefCheckBox( AbstractApplication.getApplication().getResourceString(
					"labelNormalize" ));
				ggNormalize.setSelected( DEFAULT_NORMALIZE );
				ggNormalize.addItemListener( this );
				add( ggNormalize );
				cols++;
			}
			for( ; cols < 3; cols++ ) add( new JLabel() );
			setGainLabel();
		}
		
		if( rows > 0 ) {
			GUIUtil.makeCompactSpringGrid( this, rows, 3, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		}
	}
	
	/**
	 *  Copy the internal state of
	 *  the <code>AudioFileFormatPane</code> into the
	 *  <code>AudioFileDescr</code> object. This will
	 *  fill in the <code>type</code>,
	 *  <code>bitsPerSample</code>, <code>sampleFormat</code>
	 *  and <code>rate</code> fields,
	 *  provided that the pane was specified
	 *  to contain corresponding gadgets
	 *
	 *  @param  target  the description whose
	 *					format values are to be overwritten.
	 */
	public void toDescr( AudioFileDescr target )
	{
		if( ggFormat != null ) {
			target.type = ggFormat.getSelectedIndex();
		}
		if( ggEncoding != null ) {
			target.bitsPerSample	= BITSPERSMP[ ggEncoding.getSelectedIndex() ];
			target.sampleFormat		= ENCODINGS[ ggEncoding.getSelectedIndex() ];
		}
		if( ggRate != null ) {
			target.rate				= RATES[ ggRate.getSelectedIndex() ];
		}
	}
	
	/**
	 *  Return the value of the
	 *  gain gadget (in decibels).
	 *  If the pane was created without
	 *  a dedicated gain gadget, this
	 *  method returns 0.0.
	 *
	 *  @return		the pane's gain setting
	 *				or 0.0 if no gain gadget exists.
	 */
	public double getGain()
	{
		double  gain	= 0.0;
	
		if( ggGain != null ) {
			gain = ggGain.getNumber().doubleValue();
		}
		
		return gain;
	}
	
	/**
	 *  Return the state of the 'normalized'
	 *  checkbox of the pane.
	 *
	 *  @return		<code>true</code> if the pane's
	 *				'normalized' checkbox was checked,
	 *				<code>false</code> otherwise or if no checkbox
	 *				gadget exists.
	 */
	public boolean getNormalized()
	{
		if( ggNormalize != null ) {
			return ggNormalize.isSelected();
		} else {
			return false;
		}
	}
	
	/**
	 *  Copy a sound format from the given
	 *  <code>AudioFileDescr</code> to the
	 *  corresponding gadgets in the pane.
	 *  <strong>This is not yet implemented</strong>.
	 *
	 *  @throws UnsupportedOperationException   until this method
	 *											is implemented in a future version
	 *  @todo   ...
	 */
	public void fromDescr( AudioFileDescr source )
	{
		throw new UnsupportedOperationException();
	}
	
	// update the gain's label when the normalize checkbox is toggled
	private void setGainLabel()
	{
		boolean normalize   = (ggNormalize != null) && ggNormalize.isSelected();
		
		lbGainType.setText( AbstractApplication.getApplication().getResourceString(
			normalize ? "labelDBHeadroom" : "labelDBGain" ));
	}
	
	// we're listening to the normalize checkbox
	public void itemStateChanged( ItemEvent e )
	{
		if( e.getSource() == ggNormalize ) {
			setGainLabel();		
		}
	}

// --------------------- PreferenceNodeSync interface ---------------------
	
	public void setPreferences( Preferences prefs )
	{
		if( ggFormat != null ) {
			ggFormat.setPreferences( prefs, KEY_FORMAT );
		}
		if( ggEncoding != null ) {
			ggEncoding.setPreferences( prefs, KEY_ENCODING );
		}
		if( ggRate != null ) {
			ggRate.setPreferences( prefs, KEY_RATE );
		}
		if( ggGain != null ) {
			ggGain.setPreferences( prefs, KEY_GAIN );
		}
		if( ggNormalize != null ) {
			ggNormalize.setPreferences( prefs, KEY_NORMALIZE );
		}
	}
}
