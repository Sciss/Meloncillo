; CSOUND TENDENCY MASK TRAJECTORY FILTE(println "CS Tendency Mask v0.75 (13-Jul-08)")(setq prefs (gethash "PREFERENCES" cillo)); iterate over all transmitters, where trnsidx; should be initially set to zero and increases up to (numtrns-1)(defun trnsiter (cmd)	(if (< trnsidx numtrns)		(progn			(eval cmd)			(setq trnsidx (1+ trnsidx))			(trnsiter cmd)		)	; else		NIL	)); iterate from startidx incl. to stopidx excl. using variable itervar and executing cmd; if success is NIL, iteration is aborted(defun iter (itervar startidx stopidx success cmd)	(if (and (eval success) (< startidx stopidx))		(progn			(set itervar startidx)			(eval cmd)			(iter itervar (1+ startidx) stopidx success cmd)		)	))(defun parse-cs-output (textline)	(if (and (eql 10 (length textline)) (eql "CILLO " (substring textline 0 6)))		(progression-set (car (format-parse "{0,number}" (substring textline 6 10))))	; percent	; else		(println textline)	))(defun concat-args (args)	(if (null args) "" (concatenate 'STRING (car args) " " (concat-args (cdr args)))))(defun prepare NIL (progn	(setq colltrns (gethash "TRANSMITTERS" cillo))	(setq numtrns (length colltrns))	(setq timeline (gethash "TIMELINE" cillo))	(setq duration (- (gethash "STOP" timeline) (gethash "START" timeline)))	; in seconds	(setq sense-rate (gethash "SENSERATE" prefs))	; busses	(setq trajxbus-off 1)	(setq trajybus-off (+ trajxbus-off numtrns))	(setq numaudiobusses (+ trajybus-off numtrns))	(setq traj-id-off 0)	(iter 'trnsidx 0 numtrns 'T		'(let ((trns (elt colltrns trnsidx))			   (temp-input-file (temp-file-make))			   (temp-output-file (temp-file-make)))			(setq bufidx (+ traj-id-off trnsidx))			(audio-file-open bufidx temp-input-file "raw" "float32" sense-rate 2); mere generator at the moment;			(source-request "TRAJ" trnsidx bufidx)			(target-request "TRAJ" trnsidx temp-output-file)			(setf-gethash "INPUT-FILE" trns temp-input-file)			(setf-gethash "OUTPUT-FILE" trns temp-output-file)		)	)			T	; success))(defun render NIL (progn	; because csound sucks and cannot write	; more than one stereo output file	; we iterate over the transmitters and	; call csound separately for each transmitter	(iter 'trnsidx 0 numtrns 'T		'(let ((trns (elt colltrns trnsidx))		       (bufidx (+ traj-id-off trnsidx))		       (include-file (path-concat (path-concat (car (path-split (gethash "BASEDIRECTORY" prefs))) "cs") "mask.orc"))		       (app-path (gethash "CSOUNDAPP" prefs))		       (csd-file (temp-file-make ".csd")))		(file-close bufidx)	; close traj data files		; ---------------------------- create CSound unified Orc/Sco file ----------------------------		; instr 12		;   read a transmitter path and write its content to zak variables		;   this is necessary because csound has a bug that forbids you to		;   use more than one text string in a score i statement.		;	p4 = traj path1; p5 = traj path2; p6 = za read index1 = (+ inputbus-off trnsidx);		;	p7 za read index2 = (+ inputbus-off (1+ trnsidx))		;	p8 = write index1 = (+ mixbus-off trnsidx); p9 = write index2 = (+ mixbus-off (1+ trnsidx))		; instr 88 : print progress information		; instr 36 : tendency mask		;	p4 = start min, p5 = start max, p6 = stop min, p7 = stop max		;   p8 = zac output bus		; instr 77 : soundfile output instrument		;   p4 = za read index; p5 = output channel; p6 = main gain		(file-open 'csd csd-file "w")		(file-write 'csd (concatenate 'STRING			"<CsoundSynthesizer>\\n<CsInstruments>\\n"			"#define SENSETOAUDIO #1.0#\\n"		; audio-rate will be set to sense-rate			"nchnls = 2\\n";			"zakinit " numaudiobusses ", 1\\n"			"zakinit 2, 1\\n"			"#include \"" include-file "\"\\n"			"</CsInstruments>\\n<CsScore>\\n"			"i88 0 " duration "\\n"				; prints progress information			"i12 0 " duration " \"" (gethash "INPUT-FILE" trns) "\" 1 2\\n"			"i36 0 " duration " " start-minx " " start-maxx " " stop-minx " " stop-maxx " 1\\n"			"i36 0 " duration " " start-miny " " start-maxy " " stop-miny " " stop-maxy " 2\\n"			"i77 0 " duration " 1 1 1.0\\n"			"i77 0 " duration " 2 2 1.0\\n"			"</CsScore>\\n</CsoundSynthesizer>\\n"		))		(file-close 'csd)		; ---------------------------- launch csound ----------------------------		(let ((exec-args (list app-path "-d" "-m7" "-A" "-3" "-o" (gethash "OUTPUT-FILE" trns)										"-r" sense-rate "-k" sense-rate csd-file)))			(println (concatenate 'STRING "\\n***** Transmitter \"" (gethash "NAME" trns) "\" executing:\\n" (concat-args exec-args)))			(setq return-code (execute parse-cs-output exec-args NIL (car (path-split csd-file))))			(println (concatenate 'STRING "Exited with return code " return-code))			(if (not (eql return-code 0)) (setq trnsidx numtrns) NIL)	; break loop if an error occurs		)	))	(eql return-code 0)		; success if return code is 0))(defun cleanup NIL (progn	(iter 'trnsidx 0 numtrns 'T		'(let ((bufidx (+ traj-id-off trnsidx)))			(if (oboundp bufidx) (file-close bufidx) NIL)		)	)	(if (oboundp 'csd) (file-close 'csd))	T	; success))(defun create-gui NIL (progn	(gadget-make NIL "LABEL" '(1 1 1 1) "Start Situation" NIL)	(gadget-make NIL "LABEL" '(1 2) "Min. X" NIL)	(gadget-make start-minx "NUMBER" '(2 2) 0.0 (list NIL 0.0 1.0 0.001))	(gadget-make NIL "LABEL" '(3 2) "Min. Y" NIL)	(gadget-make start-miny "NUMBER" '(4 2) 0.0 (list NIL 0.0 1.0 0.001))	(gadget-make NIL "LABEL" '(1 3) "Max. X" NIL)	(gadget-make start-maxx "NUMBER" '(2 3) 1.0 (list NIL 0.0 1.0 0.001))	(gadget-make NIL "LABEL" '(3 3) "Max. Y" NIL)	(gadget-make start-maxy "NUMBER" '(4 3) 1.0 (list NIL 0.0 1.0 0.001))		(gadget-make NIL "LABEL" '(1 11 1 1) "Stop Situation" NIL)	(gadget-make NIL "LABEL" '(1 12) "Min. X" NIL)	(gadget-make stop-minx "NUMBER" '(2 12) 0.0 (list NIL 0.0 1.0 0.001))	(gadget-make NIL "LABEL" '(3 12) "Min. Y" NIL)	(gadget-make stop-miny "NUMBER" '(4 12) 0.0 (list NIL 0.0 1.0 0.001))	(gadget-make NIL "LABEL" '(1 13) "Max. X" NIL)	(gadget-make stop-maxx "NUMBER" '(2 13) 1.0 (list NIL 0.0 1.0 0.001))	(gadget-make NIL "LABEL" '(3 13) "Max. Y" NIL)	(gadget-make stop-maxy "NUMBER" '(4 13) 1.0 (list NIL 0.0 1.0 0.001));	(gadget-make stop-maxy "NUMBER" '(4 3) 1.0 (list NIL 0.0 1.0 0.001));	(gadget-make NIL "LABEL" '(1 2) "Center Y" NIL);	(gadget-make center-y "NUMBER" '(2 2) center-x (list NIL 0.0 1.0 0.001));	(gadget-make NIL "LABEL" '(1 3) "Reverb Time" NIL);	(gadget-make reverb-time "NUMBER" '(2 3) 1.0 (list "sec." 0.0 100.0 0.001))	T	; success))