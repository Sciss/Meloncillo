/*
 *  GadgetMakePrimitive.java
 *  Meloncillo
 *
 *  Copyright (c) 2004-2005 Hanns Holger Rutz. All rights reserved.
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
 *		 7-Jul-04   created
 *		21-Jul-04   prefs listening works. binding-variable
 *					now passed as argument 1 and not evaluated
 *					during compilation. new argument for
 *					layout placement. last argument optional.
 *		04-Aug-04   commented
 *
 *  XXX TO-DO : check if gadget creation is inside event thread
 */

package de.sciss.meloncillo.lisp;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;

import org.jatha.compile.*;
import org.jatha.dynatype.*;
import org.jatha.machine.*;

import de.sciss.util.NumberSpace;

import de.sciss.app.*;
import de.sciss.gui.*;

/**
 *  A custom lisp function for creating gadgets (GUI elements). Each call of
 *  the function creates a new gadget. Concrete subclasses are
 *  responsible for storing or displaying the gadgets.
 *  This class implements
 *  the <code>PreferenceNodeSync</code> interface, thus allowing gadget values
 *  to be stored and recalled from Preferences. More over, when
 *  a lisp binding-symbol is provided, each preference change is
 *  reflected by the symbol-value of that symbol.<br>
 *  <br>
 *  The lisp function is called as follows:<br>
 *  <pre>
 *  (gadget-make <var>&lt;binding-variable&gt;</var> <var>&lt;type&gt;</var> <var>&lt;placement&gt;</var> [<var>&lt;initial-value&gt;</var> [<var>&lt;options&gt;</var>]])
 *  </pre><br>
 *  The <code>binding-variable</code> (not evaluated in the compilation process!)
 *  may be <code>NIL</code>. <code>type</code> is a string and can be one of "LABEL", "CHECKBOX",
 *  "CHOICE", "PATH", "NUMBER", "TEXT". <code>placement</code> is a list of four elements
 *  specifying the position and extent of the gadget (x y width height)
 *  where each value is an integer in a virtual grid space. The gadgets
 *  will be arranged using a <code>GridBagLayout</code>. the initial-value and options
 *  depend on the gadget type:
 *  <ul>
 *  <li>"LABEL" has no initial value or options</li>
 *  <li>"CHECKBOX" has an integer value of 0 or 1. no options</li>
 *  <li>"CHOICE" has a string representing the selected item. options
 *    is a list of cons cells whose car is the internally used value
 *    and whose cdr is the string presented to the user.</li>
 *  <li>"PATH" has a string initial value representing the pathname.
 *    options is a list whose first element is a string shown in
 *    the file selection dialog (or NIL). all following elements are
 *    options; allowed values are "INPUT", "OUTPUT", "FOLDER" to specify
 *    the type of path gadget.</li>
 *  <li>"NUMBER" has a number initial value, either integer or real.
 *    options is a list of 1 or 4 elements. The first element is
 *    a string specifying the unit label. Elements 2 to 4 specify
 *    the number space as (min max quant).</li>
 *  <li>"TEXT" has a string initial value, no options.</li>
 *  </ul>
 *
 *	@version	0.75, 10-Jun-08
 *	@author		Hanns Holger Rutz
 *
 *  @see	java.awt.GridBagLayout
 *  @see	de.sciss.meloncillo.gui.PrefCheckBox
 *  @see	de.sciss.meloncillo.gui.PrefComboBox
 *  @see	de.sciss.meloncillo.gui.PrefNumberField
 *  @see	de.sciss.meloncillo.gui.PrefPathField
 *  @see	de.sciss.meloncillo.gui.PrefTextField
 *  @see	javax.swing.JLabel
 */
public abstract class GadgetMakePrimitive
extends BasicLispPrimitive
implements PreferenceChangeListener, LaterInvocationManager.Listener, PreferenceNodeSync
{
	private	Preferences				prefs				= null;
	private HashMap					mapVarsToGadgets	= new HashMap();
	private LaterInvocationManager  lim					= new LaterInvocationManager( this );
	private AdvancedJatha			lisp;
	private GridBagLayout			lay					= new GridBagLayout();
	private GridBagConstraints		layCons				= getDefaultConstraints();

	public GadgetMakePrimitive( AdvancedJatha lisp )
	{
		super( lisp, "GADGET-MAKE", 3, 5 );			// <binding-variable> <type> <placement> <inital-value> <option-list>
		
		this.lisp   = lisp;
	}
	
	public void Execute( SECDMachine machine )
	{
		final LispValue					reste   = machine.S.pop();
		final LispValue					binding	= machine.S.pop();
		LispValue						options	= reste.basic_length() == 4 ? reste.fourth() : f_lisp.NIL;
		final LispValue					initial	= reste.basic_length() >= 3 ? reste.third() : f_lisp.NIL;
		LispValue						place   = reste.second();
		final LispValue					type	= reste.first();
		LispValue						option;
		String							typeStr, prefsKey, prefsVal, s;
		int								i, j;
		JComponent						c;
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();

		try {
			if( !type.basic_stringp() ||
				!(binding.basic_null() || (binding.basic_symbolp() && !binding.basic_constantp())) ||
				!(options.basic_null() || options.basic_listp()) ) {

				System.err.println( app.getResourceString( "errLispWrongArgType" ) + " : "+functionName );
				return;
			}

			typeStr = type.toStringSimple().toUpperCase();
			if( !binding.basic_null() && prefs != null ) {
				prefsKey	= binding.internal_getName();
				prefsVal	= prefs.get( prefsKey, null );
			} else {
				prefsKey	= null;
				prefsVal	= null;
			}

			// -------------- Label --------------
			if( typeStr.equals( "LABEL" )) {
				if( initial.basic_null() ) {
					c = new JLabel();
				} else if( initial.basic_stringp() ) {
					c = new JLabel( initial.toStringSimple(), JLabel.RIGHT );
				} else {
					System.err.println( app.getResourceString( "errLispWrongArgType" ) + " : "+functionName );
					return;
				}

			// -------------- CheckBox --------------
			} else if( typeStr.equals( "CHECKBOX" )) {
				c = new PrefCheckBox();
				if( initial.basic_numberp() ) {
					if( initial.zerop() == f_lisp.NIL ) {	// any number != 0 will tick the box
						((PrefCheckBox) c).setSelected( true );
					}
				}
				if( prefsVal == null ) {  // set initial prefs
					prefs.putBoolean( prefsKey, ((PrefCheckBox) c).isSelected() );
				}

			// -------------- Choice --------------
			} else if( typeStr.equals( "CHOICE" )) {
				StringItem		item;
				
				c = new PrefComboBox();
				if( initial.basic_stringp() ) {
					s = initial.toStringSimple();
				} else {
					s = null;
				}
				if( options.basic_listp() ) {
					for( i = 0, j = -1; options.basic_length() > 0; i++ ) {
						option  = options.car();
						options = options.cdr();
						if( option.basic_consp() ) {
							item = new StringItem( option.car().toStringSimple(), option.cdr().toStringSimple() );
							((PrefComboBox) c).addItem( item );
							if( s != null && item.getKey().equals( s )) {
								j = i;
							}
						}
					}
					if( j >= 0 ) {
						((PrefComboBox) c).setSelectedIndex( j );
					} else {
						s = null;
					}
					if( prefsVal == null && s != null ) {		// set initial prefs
						prefs.put( prefsKey, s );
					}
				}

			// -------------- NumberField --------------
			} else if( typeStr.equals( "NUMBER" )) {
				NumberSpace		spc = null;
				double			min, max, quant;
				Number			num = null;
				
				s = null;
				if( options.basic_listp() ) {
					if( options.basic_length() >= 1 ) {
						option  = options.car();
						options = options.cdr();
						if( option.basic_stringp() ) {
							s = option.toStringSimple();	// unit label
						}
					}
					if( options.basic_length() >= 3 ) {
						option  = options.car();
						options = options.cdr();
						if( option.basic_numberp() ) {
							min = ((LispNumber) option).getDoubleValue();
							option  = options.car();
							options = options.cdr();
							if( option.basic_numberp() ) {
								max = ((LispNumber) option).getDoubleValue();
								option  = options.car();
								options = options.cdr();
								if( option.basic_numberp() ) {
									quant   = ((LispNumber) option).getDoubleValue();
									spc		= new NumberSpace( min, max, quant );
								}
							}
						}
					}
				}
				if( spc == null ) {
					spc = initial.basic_integerp() ? NumberSpace.genericIntSpace : NumberSpace.genericDoubleSpace;
				}

				final PrefNumberField pnf = new PrefNumberField();
				c = pnf;
				pnf.setSpace( spc );
//				pnf.setUnit( s );
				if( initial.basic_floatp() ) {
					num = new Double( ((LispNumber) initial).getDoubleValue() );
					pnf.setNumber( num );
				} else if( initial.basic_integerp() ) {
					num = new Long( ((LispNumber) initial).getLongValue() );
					pnf.setNumber( num );
				}

				if( prefsVal == null ) {	// set initial prefs
					num = pnf.getNumber();
					if( num instanceof Long ) {
						prefs.putLong( prefsKey, num.longValue() );
					} else {
						prefs.putDouble( prefsKey, num.doubleValue() );
					}
				}

			// -------------- PathField --------------
			} else if( typeStr.equals( "PATH" )) {
				int				flags   = 0;
				String			s2;
				
				s = null;
				if( options.basic_listp() ) {
					if( options.basic_length() >= 1 ) {
						option  = options.car();
						options = options.cdr();
						if( option.basic_stringp() ) {
							s = option.toStringSimple();	// dialog text
						}
					}
					while( options.basic_length() > 0 ) {
						option  = options.car();
						options = options.cdr();
						if( option.basic_stringp() ) {
							s2  = option.toStringSimple().toUpperCase();
							if( s2.equals( "INPUT" )) {
								flags = (flags & ~PathField.TYPE_BASICMASK) | PathField.TYPE_INPUTFILE;
							} else if( s2.equals( "OUTPUT" )) {
								flags = (flags & ~PathField.TYPE_BASICMASK) | PathField.TYPE_OUTPUTFILE;
							} else if( s2.equals( "FOLDER" )) {
								flags = (flags & ~PathField.TYPE_BASICMASK) | PathField.TYPE_FOLDER;
							} else if( s2.equals( "FORMAT" )) {
								flags |= PathField.TYPE_FORMATFIELD;
							}
						}
					}
				}
				c = new PrefPathField( flags, s );  // XXX null allowed for dlgTxt?
				if( initial.basic_stringp() ) {
					s = initial.toStringSimple();
					((PrefPathField) c).setPath( new File( s ));
				}
				if( prefsVal == null ) {	// set initial prefs
					prefs.put( prefsKey, ((PrefPathField) c).getPath().getPath() );
				}
				
			// -------------- TextField --------------
			} else if( typeStr.equals( "TEXT" )) {
				if( initial.basic_null() ) {
					c = new PrefTextField();
				} else {
					c = new PrefTextField( initial.toStringSimple() );
				}
				if( initial.basic_stringp() ) {
					s = initial.toStringSimple();
					((PrefTextField) c).setText( s );
				}
				if( prefsVal == null ) {	// set initial prefs
					prefs.put( prefsKey, ((PrefTextField) c).getText() );
				}
				
			} else {
				System.err.println( app.getResourceString( "errLispWrongArgValue" ) + " : "+functionName );
				return;
			}
			
			// ------------ finish gadget initialization, layout ------------
			layCons.gridwidth   = 1;
			layCons.gridheight  = 1;
			if( place.basic_listp() ) {
				layCons.gridy = GridBagConstraints.RELATIVE;
				option  = place.car();
				place   = place.cdr();
				if( option.basic_integerp() ) {
					layCons.gridx = (int) ((LispNumber) option).getLongValue() - 1;
					option  = place.car();
					place   = place.cdr();
					if( option.basic_integerp() ) {
						layCons.gridy = (int) ((LispNumber) option).getLongValue() - 1;
						option  = place.car();
						place   = place.cdr();
						if( option.basic_integerp() ) {
							layCons.gridwidth = (int) ((LispNumber) option).getLongValue();
							option  = place.car();
							place   = place.cdr();
							if( option.basic_integerp() ) {
								layCons.gridheight = (int) ((LispNumber) option).getLongValue();
							}
						}
					}
				}
			}
			lay.setConstraints( c, layCons );
			
			if( prefsKey != null ) {
				if( c instanceof PreferenceEntrySync ) {
					((PreferenceEntrySync) c).setPreferences( prefs, prefsKey );
				}
				mapVarsToGadgets.put( prefsKey, c );
				prefsVal = prefs.get( prefsKey, null ); // might have been set in the meantime
				if( prefsVal != null ) prefToSymbol( c, binding, prefsVal );
			}
			consumeGadget( c );
		}
		finally {
			machine.S.push( f_lisp.NIL );
			machine.C.pop();
		}
	}

	public LispValue CompileArgs( LispCompiler compiler, SECDMachine machine, LispValue args,
								  LispValue valueList, LispValue code)
	throws CompilerException
	{
		// Don't evaluate the first arg. (load it as a constant)
		// args two to end are compiled to a list
		LispValue   first   = args.first();
		LispValue   reste   = args.cdr();
		
		return( f_lisp.makeCons( machine.LDC, f_lisp.makeCons( first,
				compiler.compileArgsLeftToRight( reste, valueList, f_lisp.makeCons(
												 machine.LIS, f_lisp.makeCons( reste.length(), code ))))));
	}
	
	/**
	 *  Returns the used LayoutManager
	 *  which is a GridBagLayout
	 *
	 *  @return the layout manager used to arrange the gadgets
	 *
	 *  @see	java.awt.GridBagLayout
	 */
	public LayoutManager getLayout()
	{
		return lay;
	}

	public void setPreferences( Preferences prefs )
	{
		Iterator	iter = mapVarsToGadgets.keySet().iterator();
		Object		key, val;
		
		if( this.prefs != null ) {
			this.prefs.removePreferenceChangeListener( this );
		}
		this.prefs  = prefs;
		if( prefs != null ) {
			prefs.addPreferenceChangeListener( this );
		}
		while( iter.hasNext() ) {
			key = iter.next();
			val = mapVarsToGadgets.get( key );
			if( val instanceof PreferenceEntrySync ) {
				((PreferenceEntrySync) val).setPreferences( prefs, key.toString() );
			}
		}
	}
	
	/**
	 *  Clears the preferences settings
	 *  and removes all gadgets from the internal map.
	 *  Calls freeGadgets() and resets the layout constraints.
	 */
	public void clear()
	{
		setPreferences( null );
		mapVarsToGadgets.clear();
		lay = new GridBagLayout();
		freeGadgets();
	}

	/**
	 *  Called when a gadget has been created
	 *
	 *  @param  c   the newly created gadget
	 */
	public abstract void consumeGadget( JComponent c );
	/**
	 *  Called when clear() is invoked.
	 */
	public abstract void freeGadgets();
	/**
	 *  Requests default layout constraints for
	 *  the GridBagLayout, which can contain for example
	 *  padding and insets, anchor etc.
	 *
	 *  @return the initial constraint values used for layout
	 */
	public abstract GridBagConstraints getDefaultConstraints();

	private void prefToSymbol( Object gg, LispValue symb, String value )
	{
		if( EventManager.DEBUG_EVENTS ) System.err.println( "@GadgetMakePrimitive.prefToSymbol. symb = "+symb.internal_getName() );

		if( gg instanceof PrefCheckBox ) {
			symb.setf_symbol_value( f_lisp.makeInteger( new Boolean( value ).booleanValue() ? 1 : 0 ));
		} else if( gg instanceof PrefComboBox ) {
			symb.setf_symbol_value( value != null ? (LispValue) f_lisp.makeString( value ) : (LispValue) f_lisp.NIL );
		} else if( gg instanceof PrefNumberField ) {
			NumberSpace spc = ((PrefNumberField) gg).getSpace();
			try {
				if( spc.isInteger() ) {
					symb.setf_symbol_value( f_lisp.makeInteger( value != null ?
											Integer.parseInt( value ) : (long) spc.reset ));
				} else {
					symb.setf_symbol_value( f_lisp.makeReal( value != null ?
											Double.parseDouble( value ) : spc.reset ));
				}
			}
			catch( NumberFormatException e1 ) {
				System.err.println( e1 );
			}
		} else if( gg instanceof PrefPathField || gg instanceof PrefTextField ) {
			symb.setf_symbol_value( f_lisp.makeString( value != null ? value : "" ));
		} else {
			assert false : gg.getClass().getName();
		}
	}

// ---------------- LaterInvocationManager.Listener interface ---------------- 

	// o instanceof PreferenceChangeEvent
	public void laterInvocation( Object o )
	{
		String		key		= ((PreferenceChangeEvent) o).getKey();
		LispValue   symb	= lisp.findSymbol( key );
		Object		gg		= mapVarsToGadgets.get( key );
		String		value   = ((PreferenceChangeEvent) o).getNewValue();

		if( EventManager.DEBUG_EVENTS ) System.err.println( "@GadgetMakePrimitive li. key = "+key+"; value = "+value );

		if( gg != null && symb != null ) {
			prefToSymbol( gg, symb, value );
		}
	}

// ---------------- PreferenceChangeListener interface ---------------- 

	public void preferenceChange( PreferenceChangeEvent e )
	{
		lim.queue( e );
	}
}