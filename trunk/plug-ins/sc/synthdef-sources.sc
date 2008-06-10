/*
 *	Meloncillo Patches for SuperCollider
 *	Amplitude-Matrix-Mixer realtime preview
 *
 *	last modifications: 21-apr-05
 */

s = Server( "cilloScServer", NetAddr( "127.0.0.1", 57110 ));
p = "/Volumes/BiancoSound/Users/Rutz/Meloncillo/plug-ins/sc/";
p = "/Volumes/Claude/Developer/Meloncillo/plug-ins/sc/";
p = "/Applications/Meloncillo/plug-ins/sc/";
s = Server.local;
s.boot;
s.quit;

// ------------ realtime synthdefs ------------

// reads input from sound file
// and writes it to an audio bus
//
//	4 * numTrns UGens
//
SynthDef( "cillo-input", {
	arg i_aInBuf, i_aOutBus, i_gain = 1.0;
	
	OffsetOut.ar( bus: i_aOutBus, channelsArray: DiskIn.ar( numChannels: 1, bufnum: i_aInBuf ) * i_gain );
}).send( s ).writeDefFile( p );

// wie "cillo-input", der output wird
// jedoch zusaetzlich mit einem dem umgekehrten (1 - x)
// kontrollbus multipliziert
//
SynthDef( "cillo-input2", {
	arg i_aInBuf, i_aOutBus, i_gain = 1.0, i_kInBus;
	
	OffsetOut.ar( bus: i_aOutBus,
		channelsArray: DiskIn.ar( numChannels: 1, bufnum: i_aInBuf ) * i_gain *
			(1.0 - In.kr( bus: i_kInBus )));
}).send( s ).writeDefFile( p );

// reads input from an audio bus
// and delays it with respect to
// a distance buffer. rewrites
// the input audio bus
//
//	14 * numTrns UGens
//
SynthDef( "cillo-doppler", {
	arg i_aBus, i_kInBuf, i_kPhasorBus, i_extent = 0.04;
	var traj, dly;
	
	traj	= BufRd.kr( numChannels: 2, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus ));
	dly	= ((Select.kr( which: 0, array: traj ) - 0.5).squared +
		   (Select.kr( which: 1, array: traj ) - 0.5).squared).sqrt * i_extent;
	
	ReplaceOut.ar( bus: i_aBus, channelsArray: DelayC.ar( in: In.ar( bus: i_aBus ),
		maxdelaytime: 2 * i_extent, delaytime: dly ));
}).send( s ).writeDefFile( p );

// variable delay insert (for doppler).
// arguments:	i_aBus		audio bus to read input from. this synth rewrites the bus with the delayed signal
// 			i_kInBuf		sense buffer to read delay time from where 0.0 corresponds to max-delaytime
//						and 1.0 corresponds to no delay
//			i_kPhasorBus	bus to which the sync phasor outputs
//			i_MaxDelay	maximum delay time in seconds
//
// number of ugens used: ???
//
SynthDef( "cillo-delay", {
	arg i_aBus, i_kInBuf, i_kPhasorBus, i_MaxDelay;
	var sense, dly;
	
	sense = BufRd.kr( numChannels: 1, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus ));
	dly	 = (1.0 - min( 1.0, sense )) * i_MaxDelay;
	
	ReplaceOut.ar( bus: i_aBus, channelsArray: DelayC.ar( in: In.ar( bus: i_aBus ),
		maxdelaytime: i_MaxDelay, delaytime: dly ));
}).send( s ).writeDefFile( p );

// reads input from an audio bus
// and distorts it with respect to
// a control angle and a reference point.
// rewrites the input audio bus
//
//	37 * numRcv UGens
//
SynthDef( "cillo-distortion", {
	arg i_aBus, i_kInBuf, i_kPhasorBus, i_locX, i_locY, i_gain = 2.0, i_narrow = 4.0;
	var traj, dtrajx, dtrajy, dstatx, dstaty, da, dry, mix, med, flt, norm_mul, norm_add;
	
	dstatx	= i_locX - 0.5;
	dstaty	= i_locY - 0.5;
	norm_mul	= -1.0 / pi;
	norm_add	= 1.0;
	
	traj		= BufRd.kr( numChannels: 2, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus ));
	dtrajx	= Select.kr( which: 0, array: traj ) - 0.5;
	dtrajy	= Select.kr( which: 1, array: traj ) - 0.5;

	// delta angle normalized to -1.0 ... 1.0
	// (where 1.0 is 0 degrees, 0.5 is +/- 90 degrees,
	//  0.0 is 180 degrees)
	da		= abs( atan2( (dstatx * dtrajy) - (dstaty * dtrajx),
						(dstatx * dtrajx) + (dstaty * dtrajy) )) * norm_mul + norm_add;
	mix		= min( 1.0, (dtrajx.squared + dtrajy.squared) / 0.125 ) * da.pow( i_narrow );

	dry		= In.ar( bus: i_aBus );
	med		= Median.ar( length: 3, in: dry );
	flt		= Normalizer.ar( in: (dry - med) * med,
				level: Amplitude.kr( in: dry, mul: i_gain ), dur: 0.005 );

	ReplaceOut.ar( bus: i_aBus,
		channelsArray: Median.ar( length: 3, in: flt, mul: mix,
			add: DelayN.ar( in: dry, mul: 1.0 - mix, delaytime: 0.1, maxdelaytime: 0.01 )));
}).send( s ).writeDefFile( p );


// mixing echos to an audio bus
//
SynthDef( "cillo-echo", {
	arg i_aInBus, i_aOutBus;
	
	OffsetOut.ar( bus: i_aOutBus,
		channelsArray: LPZ2.ar( in: AllpassL.ar( in: In.ar( bus: i_aInBus ),
			maxdelaytime: 0.31, delaytime: LFNoise1.kr( 0.1, mul: 0.1, add: 0.2 ),
			decaytime: 3 )
		)
	 );
}).send( s ).writeDefFile( p );

//	??? UGens
//
SynthDef( "cillo-shifter", {
	arg 	i_aBus, i_kInBuf, i_kPhasorBus, i_locX, i_locY, i_fftBuf;
	var	traj, dtrajx, dtrajy, dstatx, dstaty, da, dry, dly, hpf, flt, mix,
		norm_mul, norm_add, bufSize, dlyTime, shift;
	
	dstatx	= i_locX - 0.5;
	dstaty	= i_locY - 0.5;
	norm_mul	= 1.0 / pi;
	norm_add	= 0.0;
	bufSize	 = BufFrames.ir( i_fftBuf );
	dlyTime	 = bufSize / SampleRate.ir;
	
	traj		= BufRd.kr( numChannels: 2, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus ));
	dtrajx	= Select.kr( which: 0, array: traj ) - 0.5;
	dtrajy	= Select.kr( which: 1, array: traj ) - 0.5;

	// delta angle normalized to -1.0 ... 1.0
	// (where -1.0 is 0 degrees, 0.0 is +/- 90 degrees,
	//  1.0 is 180 degrees)
	da		= abs( atan2( (dstatx * dtrajy) - (dstaty * dtrajx),
						(dstatx * dtrajx) + (dstaty * dtrajy) )) * norm_mul + norm_add;
	shift	= log( da * min( 1.0, (dtrajx.squared + dtrajy.squared) / 0.125 ) + 1.0 ) * 8000;
//	mix		= min( 1.0, (dtrajx.squared + dtrajy.squared) / 0.125 );
	dry		= In.ar( bus: i_aBus );
	hpf		= HPF.ar( in: dry, freq: shift + 10 );
//	dly		= DelayN.ar( in: dry, delaytime: dlyTime, maxdelaytime: dlyTime );
	flt		= (IFFT( PV_PhaseShift90( FFT( buffer: i_fftBuf, in: hpf ))) *
					SinOsc.ar( freq: shift, phase: 0 )) -
			   (hpf * SinOsc.ar( freq: shift, phase: -0.5 * pi ));

	ReplaceOut.ar( bus: i_aBus, channelsArray: flt ); // * mix + ((1.0 - mix) * hpf) );
}).send( s ).writeDefFile( p );

// reads input from sound file
// and writes it to an audio bus
SynthDef( "cillo-live", {
	arg i_aInBus, i_aOutBus, i_gain = 1.0;
	
	OffsetOut.ar( bus: i_aOutBus, channelsArray: In.ar( bus: i_aInBus, numChannels: 1 ) * i_gain );
}).send( s ).writeDefFile( p )

// mixing pipe taking an input audio
// bus, a control buf for amplitude
// modulation and an audio output bus
//
//	6 * numTrns * numRcv UGens
//
SynthDef( "cillo-mix", {
	arg i_aInBus, i_aOutBus, i_kInBuf, i_kPhasorBus;
	
	OffsetOut.ar( bus: i_aOutBus, channelsArray: In.ar( bus: i_aInBus ) * BufRd.kr( numChannels: 1, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus )));
}).send( s ).writeDefFile( p );

// wie cillo-mix, aber mit nur einem
// bus und ReplaceOut
SynthDef( "cillo-volume", {
	arg i_aBus, i_kInBuf, i_kPhasorBus;
	
	ReplaceOut.ar( bus: i_aBus, channelsArray: In.ar( bus: i_aBus ) *
		BufRd.kr( numChannels: 1, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus )));
}).send( s ).writeDefFile( p );


// read audio, highpass filter with sense data, write output
SynthDef( "cillo-highpass", {
	arg i_aInBus, i_aOutBus, i_kInBuf, i_kPhasorBus;
	var freq;
	
	freq = (1.0 + BufRd.kr( numChannels: 1, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus ))).pow(14.3) + 30;
	
	OffsetOut.ar( bus: i_aOutBus, channelsArray: HPF.ar( in: In.ar( bus: i_aInBus ),
		freq: max( 20, min( 20000, freq )) ));
}).send( s ).writeDefFile( p );

// wie cillo-highpass, aber mit ReplaceOut
SynthDef( "cillo-highpass2", {
	arg i_aBus, i_kInBuf, i_kPhasorBus;
	var freq;
	
	freq = (1.0 + BufRd.kr( numChannels: 1, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus ))).pow(14.3) + 30;
	
	ReplaceOut.ar( bus: i_aBus, channelsArray: HPF.ar( in: In.ar( bus: i_aBus ),
		freq: max( 20, min( 20000, freq )) ));
}).send( s ).writeDefFile( p );

// read audio, lowpass filter with sense data, write output
SynthDef( "cillo-lowpass", {
	arg i_aInBus, i_aOutBus, i_kInBuf, i_kPhasorBus;
	var freq;
	
	freq = (2.0 - BufRd.kr( numChannels: 1, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus ))).pow(14.3) + 30;
	
	OffsetOut.ar( bus: i_aOutBus, channelsArray: LPF.ar( in: In.ar( bus: i_aInBus ),
		freq: max( 20, min( 20000, freq ))));
}).send( s ).writeDefFile( p );

// wie cillo-lowpass, aber mit ReplaceOut
SynthDef( "cillo-lowpass2", {
	arg i_aBus, i_kInBuf, i_kPhasorBus;
	var freq;
	
	freq = (2.0 - BufRd.kr( numChannels: 1, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus ))).pow(14.3) + 30;
	
	ReplaceOut.ar( bus: i_aBus, channelsArray: LPF.ar( in: In.ar( bus: i_aBus ),
		freq: max( 20, min( 20000, freq ))));
}).send( s ).writeDefFile( p );

// read audio, ringmod sense data freq, write output
SynthDef( "cillo-ringmod", {
	arg i_aInBus, i_aOutBus, i_kInBuf, i_kPhasorBus, i_fftBuf;
	var shift, dry, hpf, flt, dlyTime, dly, bufSize;

	shift 	= max( 20, min( 20000, (1.0 + BufRd.kr( numChannels: 1, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus ))).pow(14.3) + 30 ));
	bufSize	= BufFrames.ir( i_fftBuf );
	dlyTime	= bufSize / SampleRate.ir;

	dry		= In.ar( bus: i_aInBus );
	hpf		= HPF.ar( in: dry, freq: shift + 10 );
	dly		= DelayN.ar( in: hpf, delaytime: dlyTime, maxdelaytime: dlyTime );
	flt		= (IFFT( PV_PhaseShift90( FFT( buffer: i_fftBuf, in: hpf ))) *
					SinOsc.ar( freq: shift, phase: 0 )) -
			   (dly * SinOsc.ar( freq: shift, phase: -0.5 * pi ));

	ReplaceOut.ar( bus: i_aOutBus, channelsArray: flt );
}).send( s ).writeDefFile( p );

// identisch mit "cillo-mix", die
// sense daten werden aber zusaetzlich 
// auf einen kontrollbus geschrieben
//
SynthDef( "cillo-mix2", {
	arg i_aInBus, i_aOutBus, i_kInBuf, i_kPhasorBus, i_kOutBus;
	var sense;
	
	sense = BufRd.kr( numChannels: 1, bufnum: i_kInBuf, phase: In.kr( bus: i_kPhasorBus ));
	
	OffsetOut.ar( bus: i_aOutBus, channelsArray: In.ar( bus: i_aInBus ) * sense );
	OffsetOut.kr( bus: i_kOutBus, channelsArray: sense );
}).send( s ).writeDefFile( p );

// one phasor object represents something
// like a synchronized motor for all
// control rate amplitude buffer reads.
// a trigger is fired twice per buffer cycle
// (triggerID = 0; triggerValue = increasing
// integer starting at 0)
//
//	i_rate		= rate of the control buffer in frames per sec.
//	i_bufSize		= size of sense buffer over which phasor scans
//	i_kPhaserBus	= output bus of the phasor data
//
//	14 UGens
//
SynthDef( "cillo-phasor", {
	arg i_rate, i_bufSize, i_kPhasorBus;
	var clockTrig, phasorTrig, clockRate, phasorRate, controlRate;
	
	controlRate	= SampleRate.ir / 64;
	phasorRate	= i_rate / controlRate;
	clockRate		= 2 * i_rate / i_bufSize;
	clockTrig		= Impulse.kr( freq: clockRate );
	phasorTrig	= PulseDivider.kr( trig: clockTrig, div: 2, start: 1 );
	
	SendTrig.kr( in: clockTrig, id: 0, value: PulseCount.kr( trig: clockTrig ));
	OffsetOut.kr( bus: i_kPhasorBus, channelsArray: Phasor.kr( trig: phasorTrig, rate: phasorRate, start: 0, end: i_bufSize ));
}).send( s ).writeDefFile( p )

(
SynthDef( "hrtf-synth", {
	arg i_aInBus, i_aOutBusL, i_aOutBusR, i_kAziBuf, i_kElevBuf, i_hrtfBufOffL, i_hrtfBufOffR, i_kPhasorBus,
	    i_locX = 0.5, i_locY = 0.5;
	var traj, aziNorm, elevNorm, hrtfBuf1, hrtfBuf2, hrtfWeight1, hrtfWeight2, dtrajx, dtrajy, elev, inSig, trigSig, i, j, k;
	
	aziNorm		= -12/pi;
	elevNorm		= 168; // 7 * 24;
	traj			= BufRd.kr( numChannels: 2, bufnum: i_kAziBuf, phase: In.kr( bus: i_kPhasorBus ));
	dtrajx		= i_locX - Select.kr( which: 0, array: traj );
	dtrajy		= i_locY - Select.kr( which: 1, array: traj );
	traj			= BufRd.kr( numChannels: 2, bufnum: i_kElevBuf, phase: In.kr( bus: i_kPhasorBus ));
	elev			= 72; // min( 144, max( 0, (Select.kr( which: 1, array: traj ) - i_locY) * elevNorm + 72 ));
	// finding the right orient of deltax + deltay is quite tricky,
	// this was found experimentally ; note that the IRs are clockwise
	// with zero degrees north, while math (atan2) walks anticlockwise
	// with zero degrees east...
	i			= (atan2( dtrajy, dtrajx ) * aziNorm) + 42; // 12;
	j			= floor( i );
//	k			= bitAnd( j, 1 );  // doesn't work
//	k			= j & 1;	// doesn't work
//	k			= j - (div( j, 2 ) << 1);	// doesn't work
//	k			= j - (div( j, 2 ) * 2);	// doesn't work
	k			= j - trunc( j, 2 );		// a marche
	hrtfWeight2	= k - (sign( k - 0.5 ) * frac( i )); // k - (sign( k - 0.5 ) * frac( i ));
	hrtfBuf1		= ((j + k) % 24) + elev;
	hrtfBuf2		= ((j + 1 - k) % 24) + elev;
	hrtfWeight1	= 1.0 - hrtfWeight2;
	inSig		= In.ar( bus: i_aInBus );
//	controlRate	= SampleRate.ir / 64;
//	phasorRate	= i_rate / controlRate;
	trigSig		= Phasor.kr( rate: 0.5 );

	OffsetOut.ar( bus: i_aOutBusL, channelsArray: Convolution2.ar( in: inSig,
					bufnum: i_hrtfBufOffL + hrtfBuf1, trigger: trigSig,
					framesize: 1024, mul: hrtfWeight1, add:
					Convolution2.ar( in: inSig,	bufnum: i_hrtfBufOffL + hrtfBuf2,
					trigger: trigSig, framesize: 1024, mul: hrtfWeight2 )));
	OffsetOut.ar( bus: i_aOutBusR, channelsArray: Convolution2.ar( in: inSig,
					bufnum: i_hrtfBufOffR + hrtfBuf1, trigger: trigSig,
					framesize: 1024, mul: hrtfWeight1, add:
					Convolution2.ar( in: inSig,	bufnum: i_hrtfBufOffR + hrtfBuf2,
					trigger: trigSig, framesize: 1024, mul: hrtfWeight2 )));
	
}).send( s ).writeDefFile( p );
)

s = Server.local;

// utility calls for viewing the synthdefs
(
	var sd = SynthDescLib.new( p );
	sd.read;
	sd.browse;
// SynthDescLib.global.browse;
)

SynthDescLib.new( "cilloLib" ).read( p++"*.scsyndef" ).browse;

// ------------------------- debugging things -------------------------

SynthDef( "cillo-sine", {
	arg i_aInBuf, i_aOutBus, freq = 440, amp = 0.2;
	
	Out.ar( bus: i_aOutBus, channelsArray: SinOsc.ar(freq, 0, amp ));
}).writeDefFile( p )

SynthDef( "cillo-testtone", {
	arg i_aOutBus, i_freq = 0, i_gain = 1.0;
	var toneFreq, pulseFreq;
	
	toneFreq	 = 221 * pow( 2, i_freq/3 );
	pulseFreq = pow( i_freq + 1, 0.25 );
	
	Out.ar( bus: i_aOutBus, channelsArray:
	   SinOsc.ar( toneFreq, mul: LFPulse.kr( freq: pulseFreq, iphase: 0, mul: i_gain * 0.2 ) *
	   	LFSaw.kr( freq: 1, iphase: 1 )));
}).writeDefFile( p );

// ------------------------- ignore the stuff below -------------------------

// testing the performance
// warning: keep in mind the proper node order (mixes at the tail)!
(
var	numTrns = 8, numRcv = 8, senseBufSize = 128, diskBufSize = 16384,
	bufNum, sfBusOff, ampBufOff, phasorBus;
	
sfBusOff		= numRcv;
phasorBus		= numTrns * numRcv;
ampBufOff		= numTrns;

numTrns.do({ arg trnsIdx;
	s.sendMsg( "/b_alloc", trnsIdx, diskBufSize );
	numRcv.do({ arg rcvIdx;
		var ampBufNum;
		ampBufNum = rcvIdx * numTrns + trnsIdx + ampBufOff;
		s.sendMsg( "/b_alloc", ampBufNum, senseBufSize );
	});
});

numTrns.do({ arg trnsIdx;
	s.sendMsg( "/b_close", trnsIdx );
	s.sendMsg( "/b_read", trnsIdx, "/Volumes/Edgard/audio/IchnoTest/ThreeTrns"++(trnsIdx+1)++".aif", 0, diskBufSize, 0, 1 );
});

s.sendMsg( "/g_new", 1977, 1, 0 );
s.sendMsg( "/n_run", 1977, 0 );
s.sendMsg( "/s_new", "cillo-phasor", 1978, 1, 1977, "i_rate", 1.0, "i_bufSize", senseBufSize, "i_kPhasorBus", phasorBus );
numTrns.do({ arg trnsIdx;
	var sfBusNum;
	sfBusNum = sfBusOff + trnsIdx;
	s.sendMsg( "/s_new", "cillo-input", 1979 + trnsIdx, 1, 1977, "i_aInBuf", trnsIdx, "i_aOutBus", sfBusNum );
	numRcv.do({ arg rcvIdx;
		var off;
		off = rcvIdx * numTrns + trnsIdx;
		s.sendMsg( "/s_new", "cillo-mix", 1979 + numTrns + off, 1, 1977, "i_aInBus", sfBusNum, "i_aOutBus", rcvIdx, "i_kInBuf", off + ampBufOff, "phasorBus", phasorBus );
	});
});
s.sendMsg( "/n_run", 1977, 1 );
)

// cleanup
(
var	numTrns = 8, numRcv = 8, senseBufSize = 128, diskBufSize = 16384,
	bufNum, sfBusOff, ampBufOff, phasorBus;
	
sfBusOff		= numRcv;
phasorBus		= numTrns * numRcv;
ampBufOff		= numTrns;

numTrns.do({ arg trnsIdx;
	s.sendMsg( "/b_close", trnsIdx );
	s.sendMsg( "/b_free", trnsIdx );
	numRcv.do({ arg rcvIdx;
		var ampBufNum;
		ampBufNum = rcvIdx * numTrns + trnsIdx + ampBufOff;
		s.sendMsg( "/b_free", ampBufNum );
	});
});

s.sendMsg( "/g_freeAll", 1977 );
)