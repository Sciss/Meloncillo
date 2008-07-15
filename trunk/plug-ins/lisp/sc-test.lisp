; SUPERCOLLIDER PLUG-IN FOR MULTIPLE DISCRETE LOUDSPEAKERS AND INTENSITY PANNING;; java object ids used: scosc	= datagram channel;						scsense = bytebuffer for sense data;						scsync	= datagram listening channel(println "SC Amplitude Matrix v0.74 (23-May-05)"); iterate from startidx incl. to stopidx excl. using variable itervar and executing cmd; if success is NIL, iteration is aborted(defun iter (itervar startidx stopidx success cmd)	(if (and (eval success) (< startidx stopidx))		(progn			(set itervar startidx)			(eval cmd)			(iter itervar (1+ startidx) stopidx success cmd)		)	)); iterate from startidx incl. to stopidx excl. using variable itervar and executing cmd; if success evaluates to NIL, iteration is aborted(defun cycle (itervar alist success cmd)	(if (and (eval success) (not (null alist)))		(progn			(set itervar (car alist))			(eval cmd)			(cycle itervar (cdr alist) success cmd)		)	)); utility method for osc message block alignment(defun align (val block) (* (floor (/ (+ val (1- block)) block)) block))(defun concat-args (args)	(if (null args) "" (concatenate 'STRING (car args) " " (concat-args (cdr args))))); allocate next free node(defun alloc-node NIL	(let ((result node-counter))		(setq node-counter (1+ node-counter))		result	)); allocate next free buffer(defun alloc-buf NIL	(let ((result buf-counter))		(setq buf-counter (1+ buf-counter))		result	)); allocate next free audio bus(defun alloc-abus NIL	(let ((result abus-counter))		(setq abus-counter (1+ abus-counter))		result	)); wrapper for (source-request) that; automatically handles realtime/bounce mode; and trns/rcv index lookup for given objects(defun request-sense (obj1 obj2 bufidx)	(let ((trnsidx (- (length colltrns) (length (member (if (null (member obj1 colltrns)) obj2 obj1) colltrns))))		  (rcvidx (- (length collrcv) (length (member (if (null (member obj1 collrcv)) obj2 obj1) collrcv)))))		(if realtime			(source-request "SENSE" (cons trnsidx rcvidx) 'sensemsg (list				(list "INT" bufidx sense-msgbuf-off)				(list "VAR" "BUFOFF" (+ sense-msgbuf-off 4))				(list "STREAM" (+ sense-msgbuf-off 12))				(list "SEND" 'scosc)			))		; else;			(let ((temp-file-name (temp-file-make))			(let ((temp-file-name (concatenate 'STRING "/Users/rutz/Desktop/sense" bufidx))				  (temp-file-id (concatenate 'STRING "sense" bufidx)))				  				(audio-file-open temp-file-id temp-file-name "ircam" "float32" sense-rate)				(source-request "SENSE" (cons trnsidx rcvidx) temp-file-id)				(setq stream-files (append stream-files (list temp-file-name)))				(setq stream-bufidx (append stream-bufidx (list bufidx)))				(setq stream-fileids (append stream-fileids (list temp-file-id)))			)		)	)); looks up a session object in; a collection, using its name.; returns NIL if not found(defun find-by-name (coll name) (progn	(setq found-obj NIL)	(setq name-to-find name)	; must be global var	(cycle 'obj coll '(null found-obj)		'(if (eql (gethash "NAME" obj) name-to-find) (setq found-obj obj))	)	found-obj)); level (dB) to linear amplitude(defun db-to-lin (db) (pow 10 (/ db 20)))(defun prepare NIL (progn	(setq success T)	(setq collgrp (gethash "GROUPS" cillo))	(if (null collgrp) (progn		(setq collgrp (list cillo))		; all-group (cillo also has RECEIVERS and TRANSMITTERS keys)		(setf-gethash "FLAGS" cillo 0)	))	(setq collrcv (gethash "RECEIVERS" cillo))	(setq colltrns (gethash "TRANSMITTERS" cillo))	;	(setq prefs (gethash "PREFERENCES" cillo))	(setq timeline (gethash "TIMELINE" cillo))	(setq duration (- (gethash "STOP" timeline) (gethash "START" timeline)))	; in seconds	(setq sensebuf-size (gethash "SENSEBUFSIZE" prefs))	; limited one	(setq sensebuf-size-h (/ sensebuf-size 2))	(setq sense-rate (gethash "SENSERATE" prefs))	; limited one	(setq audio-rate (gethash "AUDIORATE" prefs))	(setq diskbuf-size (gethash "DISKBUFSIZE" prefs))	(setq master-amp (db-to-lin master-gain))	; nodes	(setq node-counter 1978)	(setq master-group (alloc-node))	(setq input-group (alloc-node))	(setq insert-group (alloc-node))	(setq mix-group (alloc-node))	(setq phasor-node (alloc-node))	; buffers	(setq buf-counter 0)	; control busses	(setq phasor-bus 0)		; audio busses	(setq live-bus-off (gethash "OUTPUTCHANNELS" prefs))	(setq input-bus-off (+ live-bus-off (gethash "INPUTCHANNELS" prefs)))	(setq abus-counter input-bus-off)	(if realtime		(let ((sc-socket (gethash "SUPERCOLLIDEROSC" prefs)))			(datagram-channel-open 'scosc (car sc-socket) (cdr sc-socket))			(datagram-channel-open 'scsync (car sc-socket) (cdr sc-socket))			(osc-bundle-send 'scosc 0.0 (list (list "/dumpOSC" (* dump-osc 3))))			(target-request "SYNC" 0 'scsync)			(setq success (not (null (osc-bundle-send-and-wait 'scsync 0.0				(list (list "/notify" 1)) "/done" 1000))))		)	; else		(progn			(setq stream-files NIL)			(setq stream-bufidx NIL)			(setq stream-fileids NIL)			(setq osc-cmd-file "/Users/rutz/Desktop/test.osc");			(setq osc-cmd-file (temp-file-make))			(file-open 'scosc osc-cmd-file "w")			(setq success T)		)	)	(if (not success) (println "FAILED! supercollider osc notification"))	; create message buffers	(if success		(let ((sense-not-aligned (+ 13 sensebuf-size-h))			  (traj-not-aligned (+ 13 sensebuf-size)))			(setq sense-msgbuf-off (align sense-not-aligned 4))			(byte-buffer-alloc 'sensemsg (+ (+ sense-msgbuf-off 12) (* sensebuf-size-h 4)))			(byte-buffer-write 'sensemsg "/b_setn\\0x00,iii")			(byte-buffer-write 'sensemsg "f" sensebuf-size-h)			(byte-buffer-write 'sensemsg '(0) (+ 9 (- sense-msgbuf-off sense-not-aligned)))	; bufNum, bufOff (initially 0)			(byte-buffer-write 'sensemsg sensebuf-size-h)	; constant # of samples;			(setq traj-msgbuf-off (align traj-not-aligned 4));			(byte-buffer-alloc 'trajmsg (+ (+ traj-msgbuf-off 12) (* sensebuf-size 4)));			(byte-buffer-write 'trajmsg "/b_setn\\0x00,iii");			(byte-buffer-write 'trajmsg "f" sensebuf-size);			(byte-buffer-write 'trajmsg '(0) (+ 9 (- traj-msgbuf-off traj-not-aligned)))	; bufNum, bufOff (initially 0);			(byte-buffer-write 'trajmsg sensebuf-size)		; constant # of samples		)	)		; create groups	(if success		(if (not (setq success			(let ((synthdefs (path-concat (path-concat (car (path-split (gethash "BASEDIRECTORY" prefs))) "sc") "cillo-*.scsyndef")))				(my-bundle-send 0.0 (list					(list						"/g_new" master-group 1 0								input-group   1 master-group								insert-group  1 master-group								mix-group     1 master-group					)					(list "/n_run" master-group 0)					(list "/d_load" synthdefs)					(list "/sync" 6)				))			)		)) (println "FAILED! group creation, definition load"))	)		; init objects and create mix synths	(setq collin (if normal colltrns collrcv))	(setq collout (if normal collrcv colltrns))	(cycle 'inobj collin 'success		'(progn			(setf-gethash "INIT" inobj NIL)			(if (not (eql (gethash "INPROC" inobj) "slave"))										(setf-gethash "BUSIDX" inobj (if (gethash "AUX" inobj)					(1- (gethash "AUXBUS" inobj))				; else					(alloc-abus)				))			)		)	)	(cycle 'outobj collout 'success '(setf-gethash "INIT" outobj NIL))	(cycle 'grp collgrp 'success		'(if (zerop (logand (gethash "FLAGS" grp) 10))	; is playing?			(cycle 'inobj (gethash (if normal "TRANSMITTERS" "RECEIVERS") grp) 'success				'(if (zerop (logand (gethash "FLAGS" inobj) 10))	; is playing?					(progn						(setq bundle NIL)						(if (null (gethash "INIT" inobj))							(let ((proc (gethash "INPROC" inobj)))								(setf-gethash "INIT" inobj T)			; init once								(setf-gethash "NODEID" inobj (alloc-node))								(if (eql proc "slave")									(let ((master (find-by-name collin (gethash "MASTER" inobj))))										(if (null master)											(println (concatenate 'STRING "ERROR! No master '" (gethash "MASTER" inobj)														"' for slave '" (gethash "NAME" inobj) "'."))										; else											(setf-gethash "BUSIDX" inobj (gethash "BUSIDX" master))										)									)								; else								(if (eql proc "diskin")									(let ((bufidx (alloc-buf)))										(setf-gethash "BUFIDX" inobj bufidx)										(setq bundle (append bundle (list											(list "/b_alloc" bufidx diskbuf-size)										)))									)								))							)						)						(cycle 'outobj (gethash (if normal "RECEIVERS" "TRANSMITTERS") grp) 'success							'(if (zerop (logand (gethash "FLAGS" outobj) 10))	; is playing?								(let ((proc (gethash "OUTPROC" outobj))									  (bufidx (alloc-buf))									  (nodeid (alloc-node)))									 									(request-sense inobj outobj bufidx)									(setq bundle (append bundle (list										(list "/b_alloc" bufidx sensebuf-size)										(if (eql proc "matrix")											(list "/s_new" "cillo-mix" nodeid 1 mix-group												"i_aInBus"		(gethash "BUSIDX" inobj)												"i_aOutBus"		(1- (gethash "AUDIOBUS" outobj))												"i_kInBuf"		bufidx												"i_kPhasorBus"	phasor-bus											)										; else										(if (eql proc "volume")											(list "/s_new" "cillo-volume" nodeid 1 insert-group												"i_aBus"		(gethash "BUSIDX" inobj)												"i_aOutBus"		(1- (gethash "AUDIOBUS" outobj))												"i_kInBuf"		bufidx												"i_kPhasorBus"	phasor-bus											)										; else										(if (eql proc "delay")											(list "/s_new" "cillo-delay" nodeid 1 insert-group												"i_aBus"		(gethash "BUSIDX" inobj)												"i_aOutBus"		(1- (gethash "AUDIOBUS" outobj))												"i_kInBuf"		bufidx												"i_kPhasorBus"	phasor-bus												"i_MaxDelay"	(/ (gethash "DELAY" outobj) 1000)											)										; else										(if (eql proc "hpf")											(list "/s_new" "cillo-highpass2" nodeid 1 insert-group												"i_aBus"		(gethash "BUSIDX" inobj)												"i_kInBuf"		bufidx												"i_kPhasorBus"	phasor-bus											)										; else										(if (eql proc "lpf")											(list "/s_new" "cillo-lowpass2" nodeid 1 insert-group												"i_aBus"		(gethash "BUSIDX" inobj)												"i_kInBuf"		bufidx												"i_kPhasorBus"	phasor-bus											)										; else											(println (concatenate 'STRING "WARNING! unknown output process: " proc))										)))))									)))								)							)						)						(if (not (or (null bundle) (setq success (my-bundle-send 0.0							(append bundle (list (list "/sync" 8))))))) (println "FAILED! sense buffer alloc, mix init"))					)				)			)		)	)	success))(defun seekpos (time-off) (progn	(setq frame-off (floor (* audio-rate time-off)))	; WARNING : local var time-off is not accessible within quoted function bodies!!	(setq bundle (list		(list "/g_freeAll" input-group)		(list "/s_new" "cillo-phasor" phasor-node 1 input-group			"i_rate"		sense-rate			"i_bufSize"		sensebuf-size			"i_kPhasorBus"	phasor-bus		)	))	(cycle 'inobj collin 'T		'(if (gethash "INIT" inobj)			(let ((proc (gethash "INPROC" inobj))				  (busidx (gethash "BUSIDX" inobj))				  (nodeid (gethash "NODEID" inobj))				  (amp (* master-amp (db-to-lin (gethash "GAIN" inobj)))))				(if (eql proc "diskin")					(let ((bufidx (gethash "BUFIDX" inobj)))						(setq bundle (append bundle (list							(list "/b_close" bufidx)			; ATTENTION : buffers have to be closed before re-reading!!							(list "/b_read" bufidx (gethash "AUDIOFILE" inobj) frame-off diskbuf-size 0 1)							(list "/s_new" "cillo-input" nodeid 1 input-group								"i_aInBuf"		bufidx								"i_aOutBus"		busidx								"i_gain"		amp							)						)))					)				; else				(if (eql proc "livein")					(setq bundle (append bundle (list						(list "/s_new" "cillo-live" nodeid 1 input-group							"i_aInBus"		(+ live-bus-off (1- (gethash "AUDIOBUS" inobj)))							"i_aOutBus"		busidx							"i_gain"		amp						)					)))				; else				(if (eql proc "test")					(setq bundle (append bundle (list						(list "/s_new" "cillo-testtone" nodeid 1 input-group							"i_aOutBus"		busidx							"i_gain"		amp						)					)))				; slave : do nothing				)))			)		)	)	(if (not (setq success (my-bundle-send 0.0 (append bundle (list (list "/sync" 4))))))		(println "TIMEOUT! adjusting position"))	success))(defun playstop (toggle)	(if (not (setq success (my-bundle-send 0.0 (list					(list "/n_run" master-group toggle)					(list "/sync" 5)))))		(println "FAILED! playstop"))	success)(defun play (time-off)	(if (seekpos time-off) (playstop 1) NIL))(defun stop (time-off)	(if (playstop 0) (progn		; clear buffers to prevent noise burst in loop mode		(setq bundle NIL)		(iter 'bufidx 0 buf-counter 'T			'(setq bundle (append bundle (list				(list "/b_zero" bufidx)			)))		)		(osc-bundle-send 'scosc 0.0 bundle)		T	)))(defun position (time-off)	(if (stop time-off) (play time-off) NIL))(defun create-gui NIL (progn	(gadget-make NIL "LABEL" '(1 4 2 1) "Master Gain")	(gadget-make master-gain "NUMBER" '(3 4) 0.0 '("dB"))	; -------------------------------- realtime --------------------------------	(if realtime (progn		(gadget-make NIL "LABEL" '(4 4) "  Dump OSC")		(gadget-make dump-osc "CHECKBOX" '(5 4) 0)	; -------------------------------- offline --------------------------------	) (progn ; else		(gadget-make NIL "LABEL" '(1 13 2 1) "Output Sound File")		(gadget-make output-file "PATH" '(3 13 3 1) "" '(NIL "OUTPUT"))		(gadget-make NIL "LABEL" '(1 14 2 1) "Output Format")		(gadget-make output-format "CHOICE" '(3 14) "aiff"			'(("aiff" . "AIFF") ("next" . "NeXT/Sun AU") ("ircam" . "IRCAM")))		(gadget-make output-res "CHOICE" '(4 14 2 1) "int24"			'(("int16" . "16-bit int") ("int24" . "24-bit int") ("int32" . "32-bit int")			  ("float" . "32-bit float") ("double" . "64-bit float")));		(gadget-make NIL "LABEL" '(4 15) "Audio Rate");		(gadget-make output-rate "NUMBER" '(5 15) 44100 '("Hz"))		(gadget-make NIL "LABEL" '(1 15 2 1) "SuperCollider Options")		(gadget-make app-options "TEXT" '(3 15) "")	))	; declare object properties	(let ((inkey (if normal "TRANSMITTERS" "RECEIVERS"))		  (outkey (if normal "RECEIVERS" "TRANSMITTERS")))			(session-property-add inkey "Process" "INPROC" "STRING" "diskin"			'(("diskin" . "Play Soundfile") ("livein" . "Live Input")			  ("test" . "Test Signal") ("slave" . "Slave Control"))		)		(session-property-add inkey "Audio In Bus" "AUDIOBUS" "INTEGER" 1 '(1 1024 1))		(session-property-add inkey "Audio In File" "AUDIOFILE" "FILE" "")			(session-property-add inkey "Slaved to" "MASTER" "STRING" "")			(session-property-add inkey "Gain [dB]" "GAIN" "DOUBLE" 0.0 '(-256.0 256.0 0.01))		(session-property-add inkey "Direct Out" "AUX" "BOOLEAN" 0)		(session-property-add inkey "Direct Out Bus" "AUXBUS" "INTEGER" 1 '(1 1024 1))		(session-property-add outkey "Process" "OUTPROC" "STRING" "matrix"			'(("matrix" . "Volume Matrix") ("delay" . "Delay Insert")			  ("hpf" . "HPF Insert") ("lpf" . "LPF Insert") ("volume" . "Volume Insert"))		)		(session-property-add outkey "Delay [ms]" "DELAY" "DOUBLE" 0.0 '(0.0 1024.0 0.001))		(session-property-add outkey "Audio Out Bus" "AUDIOBUS" "INTEGER" 1 '(1 1024 1))	)	T	; success))