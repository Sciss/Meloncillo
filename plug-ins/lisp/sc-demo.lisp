; SUPERCOLLIDER AMPLITUDE-MATRIX-MIXER WITH DOPPLER SHIFT, DISTORTION AND REVERSE OPTION;; last modified: 25-dec-04; changelog:;   25-dec-04   uses new 'concatentate' statements;               so it works in jatha 2.3;; tab-size: 4;; java object ids used: 1977 = datagram channel;						1978 = bytebuffer for sense data;						1979 = bytebuffer for traj data;						1980 = datagram listening channel; iterate over all transmitters, where trnsidx; should be initially set to zero and increases up to (numtrns-1)(defun trnsiter (cmd)	(if (< trnsidx numtrns)		(progn			(eval cmd)			(setq trnsidx (1+ trnsidx))			(trnsiter cmd)		)	)); iterate over all receivers, where rcvidx; should be initially set to zero and increases up to (numrcv-1)(defun rcviter (cmd)	(if (< rcvidx numrcv)		(progn			(eval cmd)			(setq rcvidx (1+ rcvidx))			(rcviter cmd)		)	)); iterate over all outputs, where outidx; should be initially set to zero and increases up to (numoutputs-1)(defun outiter (cmd)	(if (< outidx numoutputs)		(progn			(eval cmd)			(setq outidx (1+ outidx))			(outiter cmd)		)	)); iterate over all inputs, where inidx; should be initially set to zero and increases up to (numinputs-1)(defun initer (cmd)	(if (< inidx numinputs)		(progn			(eval cmd)			(setq inidx (1+ inidx))			(initer cmd)		)	)); iterate over all inputs, where inidx; should be initially set to zero and increases up to (numinputs-1)(defun streamiter (cmd)	(if (< stream-idx (length stream-files))		(progn			(eval cmd)			(setq stream-idx (1+ stream-idx))			(streamiter cmd)		)	)); iterate over a list of regex matcher word boundary groups; and extract all inbetween words(defun regexiter (groups input)	(if (> (length groups) 1)		(cons (substring input (cdr (first groups)) (car (second groups)))			(if (> (length groups) 2)				(regexiter (cdr groups) input)			)		)	))(defun align (val block) (* (floor (/ (+ val (1- block)) block)) block)); returns T if an error occurs(defun my-bundle-send (timetag bundle)	(if realtime		(null (osc-bundle-send-and-wait 1977 timetag bundle "/synced" 2000))	; else		(null (osc-bundle-send 1977 timetag bundle))	))(defun make-sense NIL	(progn		(setq stream-idx 0)		(streamiter			'(let ((bufidx (elt stream-bufidx stream-idx))				   (temp-file-name (elt stream-files stream-idx)))						(osc-bundle-send 1977 bundle-time (list					; /b_read bufIdx path fileOff numFrames bufOff leaveOpen					(list "/b_read" bufidx temp-file-name read-pos sensebuf-size-h (if even-odd 0 sensebuf-size-h) 1)				))			)		)		(setq read-pos (+ read-pos sensebuf-size-h))		(setq even-odd (not even-odd))	))(defun senseiter (incr-time stop-time cmd)	(if (< bundle-time stop-time)		(progn			(eval cmd)			(setq bundle-time (+ bundle-time incr-time))			(senseiter incr-time stop-time cmd)		)	; else		NIL	))(defun concat-args (args)	(if (null args) "" (concatenate 'STRING (car args) " " (concat-args (cdr args)))))(defun prepare NIL	(progn		(setq collrcv (gethash "RECEIVERS" cillo))		(setq numrcv (length collrcv))		(setq colltrns (gethash "TRANSMITTERS" cillo))		(setq numtrns (length colltrns));		(setq prefs (gethash "PREFERENCES" cillo))		(setq timeline (gethash "TIMELINE" cillo))		(setq duration (- (gethash "STOP" timeline) (gethash "START" timeline)))	; in seconds		(setq sensebuf-size (gethash "SENSEBUFSIZE" prefs))	; limited one		(setq sensebuf-size-h (/ sensebuf-size 2))		(setq sense-rate (gethash "SENSERATE" prefs))	; limited one		(setq audio-rate (gethash "AUDIORATE" prefs))		(setq diskbuf-size (gethash "DISKBUFSIZE" prefs))		(setq volume (pow 10 (/ main-gain 20)))		(setq distortion-volume (pow 10 (/ distortion-gain 20)))		(setq doppler-bool (eql doppler 1))		(setq normal (eql mapping-model "normal"))		(setq narrow (pow 8.0 (/ divergence 100)))		; find distortion transmitter		(setq trnsidx 0)		(setq distortion-idx -1)		(trnsiter			'(let ((obj (elt colltrns trnsidx)))				(let ((name (gethash "NAME" obj)))					(if (or (eql name "D") (eql name "d")) (progn						(setq distortion-idx trnsidx)						(setq trnsidx numtrns)	; break loop					))				)			)		)		(setq distortion-bool (and (eql distortion 1) (not (eql distortion-idx -1))));		(if distortion-bool (println "distortion mode"))		; find echo receiver		(setq rcvidx 0)		(setq echo-idx -1)		(rcviter			'(let ((obj (elt collrcv rcvidx)))				(let ((name (gethash "NAME" obj)))					(if (or (eql name "E") (eql name "e")) (progn						(setq echo-idx rcvidx)						(setq rcvidx numrcv)	; break loop					))				)			)		)		(setq echo-bool (not (eql echo-idx -1)))		(if echo-bool (println "echo mode"))		(setq numinputs (if normal numtrns numrcv))		(setq numoutputs (if normal numrcv numtrns))		(setq collin (if normal colltrns collrcv))		(setq collout (if normal collrcv colltrns))		; nodes		(setq main-group 1977)		(setq input-group (1+ main-group))		(setq doppler-group (1+ input-group))		(setq mix-group (1+ doppler-group))		(setq dist-group (1+ mix-group))		(setq phasor-node (1+ dist-group))		(setq input-node-off (1+ phasor-node))		(setq doppler-node-off (+ input-node-off numinputs))		(setq dist-node-off (+ doppler-node-off numtrns))		(setq echo-node-off (+ dist-node-off numrcv))		(setq mix-node-off (+ echo-node-off numoutputs))		; buffers		(setq sensebuf-off 0)		(setq diskbuf-off (+ sensebuf-off (* numtrns numrcv)))		(setq trajbuf-off (+ diskbuf-off numinputs))		; control busses		(setq phasor-bus 0)		; audio busses		(setq input-bus-off (+ (gethash "INPUTCHANNELS" prefs) (gethash "OUTPUTCHANNELS" prefs)))		(setq mixinput-bus-off input-bus-off)		(setq echo-bus (+ mixinput-bus-off numinputs))		(setq mixoutput-bus-off 0)		(setq doppler-bus-off (if normal input-bus-off 0))		; in-place		(setq distortion-bus-off (if normal 0 input-bus-off))	; in-place		(if realtime			(let ((sc-socket (gethash "SUPERCOLLIDEROSC" prefs)))				(datagram-channel-open 1977 (car sc-socket) (cdr sc-socket))				(datagram-channel-open 1980 (car sc-socket) (cdr sc-socket))				(osc-bundle-send 1977 0.0 (list (list "/dumpOSC" (* dump-osc 3))))				(osc-bundle-send-and-wait 1980 0.0 (list (list "/notify" 1)) "/done" 1000)				(target-request "SYNC" 0 1980)			)		; else			(progn				(setq stream-files NIL)				(setq stream-bufidx NIL);				(setq osc-cmd-file "/Users/rutz/Desktop/test.osc")				(setq osc-cmd-file (temp-file-make))				(file-open 1977 osc-cmd-file "w")			)		)		; create objects		(let ((sense-not-aligned (+ 13 sensebuf-size-h))			  (traj-not-aligned (+ 13 sensebuf-size)))			(setq sense-msgbuf-off (align sense-not-aligned 4))			(byte-buffer-alloc 1978 (+ (+ sense-msgbuf-off 12) (* sensebuf-size-h 4)))			(byte-buffer-write 1978 "/b_setn\\0x00,iii")			(byte-buffer-write 1978 "f" sensebuf-size-h)			(byte-buffer-write 1978 '(0) (+ 9 (- sense-msgbuf-off sense-not-aligned)))	; bufNum, bufOff (initially 0)			(byte-buffer-write 1978 sensebuf-size-h)	; constant # of samples			; trajectories are only used in doppler or distortion mode			(if (or doppler-bool distortion-bool) (progn				(setq traj-msgbuf-off (align traj-not-aligned 4))				(byte-buffer-alloc 1979 (+ (+ traj-msgbuf-off 12) (* sensebuf-size 4)))				(byte-buffer-write 1979 "/b_setn\\0x00,iii")				(byte-buffer-write 1979 "f" sensebuf-size)				(byte-buffer-write 1979 '(0) (+ 9 (- traj-msgbuf-off traj-not-aligned)))	; bufNum, bufOff (initially 0)				(byte-buffer-write 1979 sensebuf-size)		; constant # of samples			))		)		; create groups		(let ((synthdefs (path-concat (path-concat (car (path-split (gethash "BASEDIRECTORY" prefs))) "sc") "cillo-*.scsyndef")))			(if (my-bundle-send 0.0 (list						(if normal							(list								"/g_new" main-group 1 0								input-group   0 main-group								doppler-group 1 main-group								mix-group     1 main-group								dist-group    1 main-group							) ; else							(list								"/g_new" main-group 1 0								input-group   0 main-group								dist-group    1 main-group								mix-group     1 main-group								doppler-group 1 main-group							)						)						(list "/n_run" main-group 0)						(list "/d_load" synthdefs)						(list "/sync" 6))					)				(println "FAILED! group creation, definition load")			)		)		(setq outidx 0)		(outiter 			'(let ((obj (elt collout outidx)))				(let ((name (gethash "NAME" obj)))					(let ((result (regex-match "[\\d]+" name)))		; find integer number part						(setf-gethash "AUDIO-OUT-BUS" obj							(if (null result)								outidx							; else								(let ((result2 (car (last result))))									(1- (car (format-parse "{0,number,integer}"												(substring name (car result2) (cdr result2))))))							)						)					)				)			)		)		(let ((result (path-split input-files))) (progn			(setq input-path-parent (car result))			(setq input-path-child (cdr result))			(let ((result2 (regex-match "[\\d]+" input-path-child)))		; find integer number part				(setq inputname-replace					(if (null result2)						(cons (length input-path-child) (length input-path-child))					; else						(car (last result2))		; last occurance of an string-integer 					)				)			)		))		; propagation speed 344 m/s (20 degrees celsius)		; extent refers to the diagonale, i.e. extent = room-width (m) * sqrt(2) / 344		(setq extent (* 0.004111086 room-width))		; create mix matrix and disk + sense buffers		(setq inidx 0)		(initer			'(if (not (and normal (eql inidx distortion-idx)))				(let ((is-echo-input (and (not normal) (eql inidx echo-idx))))					(setq bundle '())					(if (not is-echo-input) (progn						(let ((obj (elt collin inidx)))							(let ((name (gethash "NAME" obj)))								(let ((result (regex-match "[\\d]+" name)))		; find integer number part									(setf-gethash "INPUT-FILE" obj (path-concat input-path-parent										(if (null result) (progn											(println (concatenate 'STRING "Input object name " name " doesn't contain a number\\n"												 "and cannot be mapped to a sound file path."))											input-path-child										) ; else											(let ((result2 (car (last result))))												(concatenate 'STRING													(substring input-path-child 0 (car inputname-replace))													(substring name (car result2) (cdr result2))													(substring input-path-child (cdr inputname-replace) (length input-path-child))												)											)										)									))								)							)						)						(setq bundle (append bundle (list							(list "/b_alloc" (+ inidx diskbuf-off) diskbuf-size)						)))					))					(setq outidx 0)					(outiter						'(if (or normal (not (eql outidx distortion-idx)))							(let ((obj (elt collout outidx))								  (matrix-id (+ (* outidx numinputs) inidx))								  (bufidx (+ sensebuf-off (+ (* outidx numinputs) inidx))))								  								(if realtime									(source-request "SENSE" (cons (if normal inidx outidx) (if normal outidx inidx)) 1978 (list										(list "INT" bufidx sense-msgbuf-off)										(list "VAR" "BUFOFF" (+ sense-msgbuf-off 4))										(list "STREAM" (+ sense-msgbuf-off 12))										(list "SEND" 1977)									))								; else									(let ((temp-file-name (temp-file-make)))										(audio-file-open bufidx temp-file-name "ircam" "float32" sense-rate)										(source-request "SENSE" (cons (if normal inidx outidx) (if normal outidx inidx)) bufidx)										(setq stream-files (append stream-files (list temp-file-name)))										(setq stream-bufidx (append stream-bufidx (list bufidx)))									)								)								(let ((is-echo-output (and normal (eql outidx echo-idx)))									  (is-echo-input (and (not normal) (eql inidx echo-idx))))									  									(let ((output-bus (if (or is-echo-input is-echo-output)															echo-bus														; else															(+ (gethash "AUDIO-OUT-BUS" obj) mixoutput-bus-off)													  )										 ))										 										(setq bundle (append bundle (list											(list "/b_alloc" bufidx sensebuf-size)											(list "/s_new" "cillo-mix" (+ matrix-id mix-node-off) 1 mix-group												"i_aInBus"		(+ inidx mixinput-bus-off)												"i_aOutBus"		output-bus												"i_kInBuf"		bufidx												"i_kPhasorBus"	phasor-bus											)										)))									)								)							)						)					)					(if (my-bundle-send 0.0 (append bundle (list (list "/sync" 0)))) (progn						(println "FAILED! buffer alloc, mix init")						(setq outidx numoutputs)	; break loop						(setq success NIL)			; failed					) ; else						(setq success T)			; succeeded					)				)			)		)		; create doppler inserts		(if (and success doppler-bool) (progn			(setq bundle '())			(setq trnsidx 0)			(trnsiter				'(if (not (eql trnsidx distortion-idx))					(let ((bufidx (+ trnsidx trajbuf-off)))						(if realtime							(source-request "TRAJ" trnsidx 1979 (list								(list "INT" bufidx traj-msgbuf-off)						; update buffer index								(list "VAR" "BUFOFF" (+ traj-msgbuf-off 4))				; update buffer offset								(list "STREAM" (+ traj-msgbuf-off 12))					; update buffer content								(list "SEND" 1977)							))						; else							(let ((temp-file-name (temp-file-make)))								(audio-file-open bufidx temp-file-name "ircam" "float32" sense-rate 2)								(source-request "TRAJ" trnsidx bufidx)								(setq stream-files (append stream-files (list temp-file-name)))								(setq stream-bufidx (append stream-bufidx (list bufidx)))							)						)						(let ((obj (elt colltrns trnsidx)))							(let ((audio-bus (+ doppler-bus-off (if normal trnsidx (gethash "AUDIO-OUT-BUS" obj)))))								(setq bundle (append bundle (list									(list "/b_alloc" bufidx sensebuf-size 2)									(list "/s_new" "cillo-doppler" (+ trnsidx doppler-node-off) 1 doppler-group										"i_aBus"		audio-bus										"i_kInBuf"		bufidx										"i_kPhasorBus"	phasor-bus										"i_extent"		extent									)								)))							)						)					)				)			)			(if (my-bundle-send 0.0 (append bundle (list (list "/sync" 1)))) (progn				(println "FAILED! buffer alloc, doppler init")				(setq success NIL)			; failed			) ; else				(setq success T)			; succeeded			)		))		; create distortion inserts		(if (and success distortion-bool)			(let ((bufidx (+ distortion-idx trajbuf-off)))				(if realtime					(source-request "TRAJ" distortion-idx 1979 (list						(list "INT" bufidx traj-msgbuf-off)							; update buffer index						(list "VAR" "BUFOFF" (+ traj-msgbuf-off 4))					; update buffer offset						(list "STREAM" (+ traj-msgbuf-off 12))						; update buffer content						(list "SEND" 1977)					))				; else					(let ((temp-file-name (temp-file-make)))						(audio-file-open bufidx temp-file-name "ircam" "float32" sense-rate 2)						(source-request "TRAJ" distortion-idx bufidx)						(setq stream-files (append stream-files (list temp-file-name)))						(setq stream-bufidx (append stream-bufidx (list bufidx)))					)				)					(setq rcvidx 0)				(setq bundle (list					(list "/b_alloc" bufidx sensebuf-size 2)				))				(rcviter					'(if (not (and normal (eql rcvidx echo-idx)))						(let ((obj (elt collrcv rcvidx)))							(let ((anchor (gethash "ANCHOR" obj))								  (audio-bus (+ distortion-bus-off (if normal (gethash "AUDIO-OUT-BUS" obj) rcvidx)))								  (bufidx (+ distortion-idx trajbuf-off)))	; redefined because in a quote block		;								(println (concatenate 'STRING "rcv " (gethash "NAME" obj) "; anchor = " (car anchor) " / " (cdr anchor)))								(setq bundle (append bundle (list									(list "/s_new" "cillo-distortion" (+ rcvidx dist-node-off) 1 dist-group										"i_aBus"		audio-bus										"i_kInBuf"		bufidx										"i_kPhasorBus"	phasor-bus										"i_locX"		(car anchor)										"i_locY"		(cdr anchor)										"i_narrow"		narrow										"i_gain"		distortion-volume									)								)))							)						)					)				)				(if (my-bundle-send 0.0 (append bundle (list (list "/sync" 2)))) (progn					(println "FAILED! distortion init")					(setq success NIL)			; failed				) ; else					(setq success T)			; succeeded				)			)		)		; create echo returns		(if (and success echo-bool) (progn			(setq bundle '())			(setq outidx 0)			(outiter				'(let ((obj (elt collout outidx)))					(setq bundle (append bundle (list						(list "/s_new" "cillo-echo" (+ outidx echo-node-off) 1 mix-group							"i_aInBus"		echo-bus							"i_aOutBus"		(+ (gethash "AUDIO-OUT-BUS" obj) mixoutput-bus-off)						)					)))				)			)			(if (my-bundle-send 0.0 (append bundle (list (list "/sync" 3)))) (progn				(println "FAILED! echo init")				(setq success NIL)			; failed			) ; else				(setq success T)			; succeeded			)		))				success	))(defun position (time-off) (progn	(setq frame-off (floor (* audio-rate time-off)))	; WARNING : time-off is not accessible within function calls!!	(setq bundle (list		(list "/g_freeAll" input-group)		(list "/s_new" "cillo-phasor" phasor-node 1 input-group			"i_rate"		sense-rate			"i_bufSize"		sensebuf-size			"i_kPhasorBus"	phasor-bus		)	))	(setq inidx 0)	(initer		'(if (not (or (and normal (eql inidx distortion-idx)) (and (not normal) (eql inidx echo-idx))))			(let ((obj (elt collin inidx))				  (bufidx (+ inidx diskbuf-off)))					(setq bundle (append bundle (list					(list "/b_close" bufidx)			; ATTENTION : buffers have to be closed before re-reading!!					(list "/b_read" bufidx (gethash "INPUT-FILE" obj) frame-off diskbuf-size 0 1)					(list "/s_new" "cillo-input" (+ inidx input-node-off) 1 input-group						"i_aInBuf"		bufidx						"i_aOutBus"		(+ inidx input-bus-off)						"i_gain"		volume					)				)))			)		)	)	(if (my-bundle-send 0.0 (append bundle (list (list "/sync" 4))))		(progn			(println "TIMEOUT! adjusting position")			NIL		; failure		)	; else		T	; success	)))							(defun playstop (toggle)	(if (my-bundle-send 0.0 (list					(list "/n_run" main-group toggle)					(list "/sync" 5)))		(progn			(println "FAILED! playstop")			NIL		; failure		)	; else		T	; success	))(defun play (time-off)	(if (position time-off)	(playstop 1) NIL))(defun stop (time-off) (playstop 0))(defun render NIL	(progn		; ---------------------------- START TIME ----------------------------		(setq even-odd T)		(setq read-pos 0)		(setq bundle-time 0.0)		(make-sense)	; preload first buffer half		(make-sense)	; preload second buffer half		(play (gethash "START" timeline))			; ---------------------------- RENDER TIME ----------------------------		(let ((half-bufdur (/ sensebuf-size-h sense-rate)))	; duration of half buffer in seconds			(setq bundle-time (* half-bufdur 1.5))			; slightly after entering the second buffer half			(senseiter half-bufdur duration '(make-sense))		)		; ---------------------------- STOP TIME ----------------------------		(setq bundle-time duration);		(setq trnsidx 0);		(trnsiter '(progn;			(setq bufidx (+ trnsidx diskbuf-off));			(osc-bundle-send 1977 bundle-time (list;				(list "/b_close" bufidx);			));		))		; dummy termination		(osc-bundle-send 1977 bundle-time (list			(list "/sync" 0)		))				; ---------------------------- SUPERCOLLIDER APP ----------------------------		(setq stream-idx 0)		(streamiter			'(let ((bufidx (elt stream-bufidx stream-idx)))				(file-close bufidx)			)		)		(file-close 1977)	; close osc file		(setq app-path (gethash "SUPERCOLLIDERAPP" prefs))		(let ((exec-args (append (append					(list app-path)					(regexiter (regex-match "(\\A|[\\s]+|\\Z)+" app-options) app-options))			  		(list  "-i" 1 "-o" (if (or (and normal echo-bool)			  								   (and (not normal) distortion-bool))			  								(1- numoutputs) numoutputs)			  			   "-N" osc-cmd-file "_" output-file audio-rate output-format output-res))))			(println (concatenate 'STRING "Execute: " (concat-args exec-args)))			(setq return-code (execute parse-sc-output exec-args NIL (car (path-split app-path))))			(println (concatenate 'STRING "Exited with return code " return-code))		)		(eql return-code 1)		; success if return code is 1	))(defun cleanup NIL	(progn		(if (oboundp 1980) (progn			(osc-bundle-send 1980 0.0 (list (list "/notify" 0)))			(datagram-channel-close 1980)		))		(if (oboundp 1978) (byte-buffer-free 1978) NIL)		(if (oboundp 1979) (byte-buffer-free 1979) NIL)		(if (oboundp 1977)			; -------------------------------- realtime --------------------------------			(if realtime (progn				(osc-bundle-send 1977 0.0 (list					(list "/n_run" main-group 0)					(list "/g_freeAll" main-group)				))				(setq inidx 0)				(initer					'(if (not (or (and normal (eql inidx distortion-idx)) (and (not normal) (eql inidx echo-idx)))) (progn						(setq bundle (list							(list "/b_free" (+ inidx diskbuf-off))	; audio file buffer						))						(setq outidx 0)						(outiter							'(setq bundle (append bundle (list								(list "/b_free" (+ sensebuf-off (+ (* outidx numinputs) inidx)))		; sense buffer							)))						)						(osc-bundle-send 1977 0.0 bundle)					))				)				(if doppler-bool (progn					(setq bundle '())					(setq trnsidx 0)					(trnsiter						'(setq bundle (append bundle (list							(list "/b_free" (+ trnsidx trajbuf-off))		; traj buffer						)))					)					(osc-bundle-send 1977 0.0 bundle)				))				; XXX free distortion				(datagram-channel-close 1977)			; -------------------------------- offline --------------------------------			) (progn	; else				(setq stream-idx 0)				(streamiter					'(let ((bufidx (elt stream-bufidx stream-idx)))						(file-close bufidx)					)				)				(file-close 1977)			))		)				T	; success	))(defun create-gui NIL	(progn		(gadget-make doppler "CHECKBOX" '(1 1) 1)		(gadget-make NIL "LABEL" '(2 1) "Doppler: Room Width")		(gadget-make room-width "NUMBER" '(3 1 2 1) 30.0 (list "m" 0.0 1000.0 0.01))		(gadget-make distortion "CHECKBOX" '(1 2) 1)		(gadget-make NIL "LABEL" '(2 2) "Distortion: Divergence")		(gadget-make divergence "NUMBER" '(3 2 2 1) 50.0 (list "%" 0.0 100.0 0.1))		(gadget-make mapping-model "CHOICE" '(1 3 2 1) "normal"			'(("normal" . "Transmitters = Sounds") ("reverse" . "Receivers = Sounds")))		(gadget-make input-files "PATH" '(3 3 3 1) "")		(gadget-make NIL "LABEL" '(1 4 2 1) "Main Gain")		(gadget-make main-gain "NUMBER" '(3 4) 0.0 '("dB"))		(gadget-make NIL "LABEL" '(1 5 2 1) "Distortion Gain")		(gadget-make distortion-gain "NUMBER" '(3 5) 6.0 '("dB"))		; -------------------------------- realtime --------------------------------		(if realtime (progn			(gadget-make NIL "LABEL" '(4 4) "  Dump OSC")			(gadget-make dump-osc "CHECKBOX" '(5 4) 0)		; -------------------------------- offline --------------------------------		) (progn ; else			(gadget-make NIL "LABEL" '(1 13 2 1) "Output Sound File")			(gadget-make output-file "PATH" '(3 13 3 1) "" '(NIL "OUTPUT"))			(gadget-make NIL "LABEL" '(1 14 2 1) "Output Format")			(gadget-make output-format "CHOICE" '(3 14) "aiff"				'(("aiff" . "AIFF") ("next" . "NeXT/Sun AU") ("ircam" . "IRCAM")))			(gadget-make output-res "CHOICE" '(4 14 2 1) "int24"				'(("int16" . "16-bit int") ("int24" . "24-bit int") ("int32" . "32-bit int")				  ("float" . "32-bit float") ("double" . "64-bit float")));			(gadget-make NIL "LABEL" '(4 15) "Audio Rate");			(gadget-make output-rate "NUMBER" '(5 15) 44100 '("Hz"))			(gadget-make NIL "LABEL" '(1 15 2 1) "SuperCollider Options")			(gadget-make app-options "TEXT" '(3 15) "")		))		T	; success	))