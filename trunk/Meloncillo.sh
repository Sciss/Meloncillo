#!/bin/sh

followlink()
{
	prg="$1"
	while [ -h "$prg" ] ; do
		ls=`ls -ld "$prg"`
		link=`expr "$ls" : '.*-> \(.*\)$'`
		if expr "$link" : '.*/.*' > /dev/null; then
			prg="$link"
		else
			prg=`dirname "$prg"`/"$link"
		fi
	done
	echo $prg
}

absdir() 
{ 
	[ -n "$1" ] && ( cd "$1" 2> /dev/null && pwd ; ) 
}

where=`followlink $0`
where=`dirname ${where}`
where=`absdir ${where}`
cd ${where}

# CLASSPATH="Meloncillo.app/Contents/Resources/Java/Meloncillo.jar:libraries/MRJAdapter.jar:libraries/NetUtil.jar:libraries/jatha.jar"
CLASSPATH="bin:resources:libraries/MRJAdapter.jar:libraries/NetUtil.jar:libraries/jatha.jar"

java -Dapple.laf.useScreenMenuBar=true -Dcom.apple.mrj.application.live-resize=true -Xmx128m -ea -cp "$CLASSPATH" de.sciss.meloncillo.Main "$@"
