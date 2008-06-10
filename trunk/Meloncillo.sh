#!/bin/sh

APPHOME="/Applications/Meloncillo"

CLASSPATH="$APPHOME/Meloncillo.app/Contents/Resources/Java/Meloncillo.jar:$APPHOME/libraries/MRJAdapter.jar:$APPHOME/libraries/NetUtil.jar:$APPHOME/libraries/jatha.jar"

cd "$APPHOME"
java -Dapple.laf.useScreenMenuBar=true -Dcom.apple.mrj.application.live-resize=true -Xmx128m -ea -cp "$CLASSPATH" de.sciss.meloncillo.Main -laf Liquid com.birosoft.liquid.LiquidLookAndFeel "$@"
