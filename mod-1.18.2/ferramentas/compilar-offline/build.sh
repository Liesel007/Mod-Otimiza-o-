#!/bin/bash
export JAVA_TOOL_OPTIONS=
L=/mnt/user-data/uploads/Install/libraries
CP=$(find $L -name '*.jar' ! -path '*forge/1.18.2*' ! -name '*client-1.18.2*srg*' | tr '\n' ':')/home/claude/b118/mc118-official.jar
SRC=/home/claude/mod-otimiza-o-/mod-1.18.2/src/main
rm -rf out && mkdir -p out/classes
javac -Xmaxerrs 3000 --release 17 -encoding UTF-8 -cp "$CP" -processorpath "$L/org/spongepowered/mixin/0.8.5/mixin-0.8.5.jar:$(ls $L/org/ow2/asm/*/9.7.1/*.jar|tr '\n' ':')$L/com/google/guava/guava/31.0.1-jre/guava-31.0.1-jre.jar:$L/com/google/code/gson/gson/2.8.9/gson-2.8.9.jar" \
  -AreobfTsrgFile=/home/claude/b118/off2srg.tsrg -AoutRefMapFile=/home/claude/b118/out/classes/hlotimizacao.refmap.json -AdefaultObfuscationEnv=searge -AmappingTypes=tsrg -AoutTsrgFile=/home/claude/b118/out/mixin-out.tsrg \
  -d out/classes $(find $SRC/java -name '*.java') 2>&1 | grep -v "^Note:" > out/errors.txt
echo "erros: $(grep -c 'error:' out/errors.txt)"
