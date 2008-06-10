; SUPERCOLLIDER REALTIME BINAURAL PLUG-IN(setq realtime T)(setq prefs (gethash "PREFERENCES" cillo)); returns NIL if an error occurs(defun my-bundle-send (timetag bundle)	(not (null (osc-bundle-send-and-wait 'scosc timetag bundle "/synced" 5000))) ; lot's of prints if dumping OSC!)(load (path-concat (gethash "BASEDIRECTORY" prefs) "sc-binaural.lisp"))(defun cleanup NIL	(progn		(if (oboundp 'scsync) (progn			(osc-bundle-send 'scsync 0.0 (list (list "/notify" 0)))			(datagram-channel-close 'scsync)		))		(if (oboundp 'scsense) (byte-buffer-free 'scsense) NIL)		(if (oboundp 'scosc)			(osc-bundle-send 'scosc 0.0 (list				(list "/n_run" master-group 0)				(list "/g_freeAll" master-group)			))			; XXX free buffers up to buf-counter			(datagram-channel-close 'scosc)		)				T	; success	))