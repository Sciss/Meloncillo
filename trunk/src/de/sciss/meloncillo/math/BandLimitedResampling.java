/*
 *  BandLimitedResampling.java
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
 *		27-May-04	created
 *		04-Aug-04   commented
 */
 
package de.sciss.meloncillo.math;

/**
 *  Bandlimited resampling using a
 *  windowed sinc as interpolation
 *  function, as described by Julius O. Smith
 *  on his stanford website. The
 *  cryptic packaging of multiple offset
 *  values into bit chunks of primitives
 *  has been omitted, besides a few java
 *  optimizations have been made and for
 *  the sake of speed a rather large
 *  function table is used by not interpolated
 *  by itself as proposed in the original paper.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 04-Aug-04
 */
public class BandLimitedResampling
extends Resampling
{
	private final float[]	coeff;				// FIR Koeffizienten (rechte Haelfte eines gefensterten Sinc-Lowpasses)
	private final int		numCoeff;			// Zahl der Koeffizient (=flt.length)
	private final int		coeffPerCrossing;	// Zahl der Samples zwischen zwei Nulldurchgaengen
	private double			gain;				// Gain to be applied during filtering

	/**
	 *  Creates a new <code>BandLimitedResampling</code>
	 *  object with medium quality (filter length)
	 */
	public BandLimitedResampling()
	{
		this( 1 );
	}
	
	/**
	 *  Creates a new <code>BandLimitedResampling</code>
	 *  object with a given quality. The higher the quality
	 *  the longer the window which is necessary to truncate
	 *  the otherwise infinitely long interpolation function.
	 *  Also the longer the window, the higher the antialiasing
	 *  filter cutoff and the less attenuation of high frequencies.
	 *
	 *  @param  quality		0 = low, 1 = medium, 2 = high
	 */
	public BandLimitedResampling( int quality )
	{
		int		i, numCrossings;
		float	rollOff, kaiserBeta;

		// im Gegensatz zum Algorithmus von J.O.Smith
		// verwenden wir keine lineare Interpolation zwischen benachbarten
		// Filterkoeffizienten, weil sich dadurch die Berechnung
		// erheblich verlangsamt. Stattdessen berechnen wir
		// einfach viel mehr Koeffizienten und lesen diese dann
		// ohne Interpolation aus
		coeffPerCrossing	= 4096;

		// drei verschiedene Qualitaetsstufen korrespondieren mit der
		// Zahl der Nulldurchlaeufe der Sinc-Funktion (fltCrossings).
		// Je kuerzer der Filter-Kernel gewaehlt wird, desto kleiner
		// muss der Filter-Cutoff (fltRolloff) gewaehlt werden, weil
		// der Transition-Bereich groesser wird. Die verschiedenen
		// Kaiser-Fenster-Parameter sind empirisch ermittelt, so dass
		// insgesamt eine moeglichst optimale Uebertragungsfunktion
		// gegen ist.
		switch( quality ) {
		case 0:
			rollOff			= 0.70f;
			kaiserBeta		= 6.5f;
			numCrossings	= 5;
			break;
		case 1:
			rollOff			= 0.80f;
			kaiserBeta		= 7.0f;
			numCrossings	= 9;
			break;
		default: // 2
			rollOff			= 0.86f;
			kaiserBeta		= 7.5f;
			numCrossings	= 15;
			break;
		}

		numCoeff	= (int) ((float) (coeffPerCrossing * numCrossings) / rollOff + 0.5f);
		coeff		= new float[ numCoeff ];
		createLPF( coeff, rollOff / 2, numCoeff, kaiserBeta, coeffPerCrossing );

		// account for the gain introduced by the filter
		gain = 0.0;
		for( i = coeffPerCrossing; i < numCoeff; i+= coeffPerCrossing ) {
			gain += coeff[ i ];
		}
		gain = 1.0 / Math.abs( 2 * gain + coeff[0] );
	}
	
	/**
	 *  Calculates the internally used half filter length for
	 *  a specific resampling factor. The ceiling integer
	 *  value of the returned value represents the amount
	 *  of samples outside the source resampling interval.
	 *  therefore when calling resample(), srcOff must
	 *  be >= Math.ceil( getWingSize() ) and src.length
	 *  must be > (srcOff + Math.ceil( length/factor + getWingSize() )),
	 *  otherwise ArrayIndexOutOfBoundsExceptions will
	 *  be thrown!
	 *  <p>
	 *  Since this method returns the length of one wing
	 *  of the filter, total filter length is actually
	 *  twice as high
	 *
	 *  @param  factor  the resampling factor
	 *  @return the theoretical wing size, of which you have
	 *			to consider the ceiling value
	 */
	public double getWingSize( double factor )
	{
		double	srcIncr	= 1.0 / factor;
		double  fltIncr;
		if( srcIncr > 1.0 ) {
			fltIncr		= (double) coeffPerCrossing / srcIncr;
		} else {
			fltIncr		= (double) coeffPerCrossing;
		}

		return( (double) numCoeff / fltIncr );
	}
	
	/**
	 *	Resamples a data vector.
	 *
	 *  @param  src			Original vector at source rate
	 *	@param	srcOff		where to begin in <code>src</code>.
	 *						This is a double value to allow
	 *						successive resampling of data blocks
	 *						with non integer resampling factors.
	 *						Note that to the left of srcOff there
	 *						must be at least getWingSize more samples
	 *						in the src buffer, otherwise an
	 *						ArrayIndexOutOfBoundsException will be thrown.
	 *	@param	length		number of <strong>target</strong> samples to calculate.
	 *						Note that the size of the src buffer must
	 *						exceed at least getWinSize more samples than
	 *						the stopping index which is srcOff + length / factor.
	 *	@param	factor		target rate divided by source rate
	 *  @return				the offset in src which would be the new offset
	 *						of a successive block resampling operation
	 */
	public double resample( float src[], double srcOff, float dest[], int destOff, int length, double factor )
	{
		double	srcIncr				= 1.0 / factor;
		int		i, fltOffI, srcOffI;
		double	q, val, fltIncr, fltOff, rsmpGain;
		double	phase				= srcOff;

//		int		srcLen				= src.length;
		
		if( srcIncr > 1.0 ) {
			fltIncr		= (double) coeffPerCrossing * factor;
			rsmpGain	= gain;							// XXX nachpruefen
		} else {
			fltIncr		= (double) coeffPerCrossing;	// effectively we realize a fractional sample delay
//			rsmpGain	= gain * srcIncr;
			rsmpGain	= gain;
//System.err.println( "gain "+gain+"; srcIncr "+srcIncr+"; rsmpGain "+rsmpGain );
		}
		
		for( i = 0; i < length; i++, phase = srcOff + i * srcIncr ) {

			q		= phase % 1.0;
			val		= 0.0;

			srcOffI	= (int) phase;
			fltOff	= q * fltIncr + 0.5f;	// wenn wir schon keine interpolation mehr benutzen...
			fltOffI	= (int) fltOff;
			while( fltOffI < numCoeff ) {
//			while( fltOffI < fltLen && srcOffI >= 0 ) {
				val	   += src[ srcOffI ] * coeff[ fltOffI ];
				srcOffI--;
				fltOff += fltIncr;
				fltOffI	= (int) fltOff;
			}

			srcOffI	= (int) phase + 1;
			fltOff	= (1.0 - q) * fltIncr;
			fltOffI	= (int) fltOff;
			while( fltOffI < numCoeff ) {
//			while( fltOffI < fltLen && srcOffI < srcLen ) {
				val	   += src[ srcOffI ] * coeff[ fltOffI ];
				srcOffI++;
				fltOff += fltIncr;
				fltOffI	= (int) fltOff;
			}

			dest[ destOff++ ] = (float) (val * rsmpGain);
		}
		
		return phase;
	}

	/*
	 *	@param	impResp				Ziel-Array der Groesse 'halfWinSize' fuer Impulsantwort
	 *	@param	freq				Grenzfrequenz
	 *	@param	halfWinSize			Groesse des Kaiser-Fensters geteilt durch zwei
	 *	@param	kaiserBeta			Parameter fuer Kaiser-Fenster
	 *	@param	fltSmpPerCrossing	Zahl der Koeffizienten pro Periode
	 */
	private static void createLPF( float impResp[], float freq, int halfWinSize, float kaiserBeta,
								   int fltSmpPerCrossing )
	{
		double	dNum		= (double) fltSmpPerCrossing;
		double	dBeta		= (double) kaiserBeta;
		double	d;
		double	smpRate		= freq * 2.0;
		double	iBeta;
		double	normFactor	= 1.0 / (double) (halfWinSize - 1);
		int		i;
	
		// ideal lpf = infinite sinc-function; create truncated version	
		impResp[ 0 ] = (float) smpRate;
		for( i = 1; i < halfWinSize; i++ ) {
			d				= Math.PI * (double) i / dNum;
			impResp[ i ]	= (float) (Math.sin( smpRate * d ) / d);
		}
		
		// apply Kaiser window
		iBeta = 1.0 / calcBesselZero( dBeta );
		for( i = 1; i < halfWinSize; i++ ) {
			d				= (double) i * normFactor;
			impResp[ i ]   *= calcBesselZero( dBeta * Math.sqrt( 1.0 - d*d )) * iBeta;
		}
	}
	
	private static double calcBesselZero( double x )
	{
		double	d1;
		double	d2	= 1.0;
		double	sum	= 1.0;
		int		n	= 1;
	
		x /= 2.0;
	
		do {
			d1	 = x / (double) n;
			n++;
			d2	*= d1*d1;
			sum	+= d2;
		} while( d2 >= sum * 1e-21 );	// auf 20 Nachkommastellen genau
		
		return sum;
	}
}