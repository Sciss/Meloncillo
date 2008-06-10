//ScissUtil.trackIncomingOSC
//~trackIncomingOSC.stop;

GUI.swing;
GUI.cocoa;
(
	var gui, win, ggState, ggTimer, resps, routTimer, fTimer, refTime = 0;
	var routTrig, fTrig, syncAddr, clockPeriod = 1;
	var pen, ggUser, trajBuf, numTrns = 0, trajShot, senseRate = 1, senseBufSize = 0;
	var senseBufDur = 1, refreshPeriod = 0.05;
	
	gui		= GUI.current;
	pen		= gui.pen;
	win		= gui.window.new( "Cillo", Rect( 100, 50, 200, 200 ));
	win.view.background = Color.black;
	ggState	= gui.staticText.new( win, Rect( 4, 4, 50, 20 ))
		.string_( "inactive" ).stringColor_( Color.grey );
	ggTimer	= gui.staticText.new( win, Rect( 58, 4, 50, 20 ))
		.font_( gui.font.new( gui.font.defaultMonoFace, 10 )).stringColor_( Color.white );
	gui.staticText.new( win, Rect( 4, 28, 192, 1 )).resize_( 2 ).background_( Color.grey );
	ggUser	= gui.userView.new( win, Rect( 4, 32, 192, 164 ))
// broken for resize_( 5 ) in cocoa!!
//		.relativeOrigin_( true )
		.canFocus_( false )
		.resize_( 5 ).drawFunc_({ arg view;
			var bounds, scale;
			bounds = view.bounds; scale = bounds.width @Êbounds.height;
//			trajShot.postln;
			pen.strokeColor = Color.white;
			pen.translate( bounds.left, bounds.top );
			trajShot.do({ arg pt;
				pt = pt * scale;
				pen.line( pt - (4 @Ê0), pt + (4 @Ê0 ));
				pen.line( pt - (0 @Ê4), pt + (0 @Ê4 ));
			});
			pen.stroke;
		});
	win.onClose = { routTimer.stop; routTrig.stop; resps.do( _.remove )};
	fTimer = {
		var dt, idx, off = thisThread.seconds;
		inf.do({
			dt = thisThread.seconds - off;
			idx = ((dt % senseBufDur) * senseRate).asInteger;
			trajShot = trajBuf.collect({ arg buf; buf[ idx ]});
{
			if( ggTimer.notClosed, {
				ggTimer.string = (dt + refTime).asTimeString;
			});
			if( ggUser.notClosed, {
				ggUser.refresh;
			});
}.defer; // weird CocoaGUI bug!
			refreshPeriod.wait;
		});
	};
	fTrig = {
//		(clockPeriod / 3).wait;
		inf.do({ arg count;
			if( syncAddr.notNil, {
				syncAddr.sendMsg( '/tr', 0, 0, count + 1 );
			});
			clockPeriod.wait;
		});
	};
	resps	= resps.add( OSCresponderNode( nil, '/cll_in', { arg time, resp, msg;
		var cmd, dur, numTrns;
		{ ggState.string_( "ready" ).stringColor_( Color.white )}.defer;
		#cmd, dur, senseRate, senseBufSize, numTrns = msg;
		senseBufDur = senseBufSize / senseRate;
		clockPeriod = senseBufDur / 2;
		trajBuf = (0 @ 0) ! senseBufSize ! numTrns;
//		[ "senseBufSize", senseBufSize, "senseRate", senseRate ].postln;
	}));
	resps	= resps.add( OSCresponderNode( nil, '/cll_pl', { arg time, resp, msg;
		{ ggState.string_( "playing" ).stringColor_( Color.green( 0.8 ))}.defer;
		refTime = msg[ 1 ];
		routTimer = fTimer.fork( AppClock );
		routTrig  = fTrig.fork( SystemClock );
	}));
	resps	= resps.add( OSCresponderNode( nil, '/cll_st', { arg time, resp, msg;
		{ ggState.string_( "stopped" ).stringColor_( Color.red )}.defer;
		routTimer.stop; routTimer = nil;
		routTrig.stop; routTrig = nil;
	}));
	resps	= resps.add( OSCresponderNode( nil, '/cll_po', { arg time, resp, msg;
		refTime = msg[ 1 ];
		routTimer.stop; routTrig.stop;
		routTimer = fTimer.fork( AppClock );
		routTrig  = fTrig.fork( SystemClock );
	}));
	resps	= resps.add( OSCresponderNode( nil, '/cll_qu', { arg time, resp, msg;
		{ ggState.string_( "inactive" ).stringColor_( Color.grey )}.defer;
		routTimer.stop; routTimer = nil;
		routTrig.stop; routTrig = nil;
	}));
	resps	= resps.add( OSCresponderNode( nil, '/cll_sy', { arg time, resp, msg, addr;
		syncAddr = addr;
//		[Ê"SYNC", addr ].postln;
	}));
	resps	= resps.add( OSCresponderNode( nil, '/cll_tr', { arg time, resp, msg;
		var cmd, trns, off, num, buf, j;
		#cmd, trns, off, num = msg;
		off = off >> 1;
		num = num >> 1;
		buf = trajBuf[ trns ];
		if( buf.notNil, {
			j = 4;
			num.do({ arg i;
				buf[ i + off ] = msg[ j ] @ msg[ j + 1 ];
				j = j + 2;
			});
		});
	}));
	resps.do( _.add );
	win.front;
)
