; SUPERCOLLIDER OFFLINE PLUG-IN FOR MULTIPLE DISCRETE LOUDSPEAKERS AND INTENSITY PANNING; NORMAL MODE (TRANSMITTERS = INPUTS, RECEIVERS = OUTPUTS)(setq normal T)(setq prefs (gethash "PREFERENCES" cillo))(load (path-concat (gethash "BASEDIRECTORY" prefs) "scol-amplitudematrix.lisp"))