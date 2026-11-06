#!/bin/bash
set -e
export JAVA_TOOL_OPTIONS=
L=/mnt/user-data/uploads/Install/libraries
ASM=$(ls $L/org/ow2/asm/*/9.7.1/*.jar | tr '\n' ':')
R=/home/claude/mod-otimiza-o-/mod-1.18.2/src/main/resources
V=0.4.0
rm -rf jar && mkdir -p jar
python3 /home/claude/b118/junta.py
java -cp "tool:$ASM" Remap reobf reobf.tsrg jar/classes.jar mc118-official.jar out/classes
cd jar && mkdir x && cd x && unzip -q ../classes.jar && rm -f hlotimizacao.refmap.json
cp /home/claude/b118/out/classes/hlotimizacao.refmap.json .
cp -r $R/assets $R/hlotimizacao.mixins.json $R/hlotimizacao_logo.png .
mkdir -p META-INF
python3 - "$R" "$V" <<'PY'
import sys,string
R,V=sys.argv[1],sys.argv[2]
p=dict(minecraft_version_range='[1.18.2,1.19)',forge_version_range='[40,)',loader_version_range='[40,)',mod_id='hlotimizacao',
 mod_name='HLO: HL Optimization',mod_license='MIT',mod_version=V,mod_authors='Liesel007',
 mod_description='Modular performance and stability fixes for Forge 1.18.2 modpacks (port of the 1.20.1 version). Every module turns itself off when a mod that does the same job is installed.')
t=open(R+'/META-INF/mods.toml').read()
for k,v in p.items(): t=t.replace('${'+k+'}',v)
open('META-INF/mods.toml','w').write(t)
m=open(R+'/pack.mcmeta').read().replace('${mod_id}','hlotimizacao').replace('"pack_format": 15','"pack_format": 8')
open('pack.mcmeta','w').write(m)
PY
cp /home/claude/mod-otimiza-o-/LICENSE LICENSE_hlotimizacao
cat > ../MANIFEST.MF <<M
Manifest-Version: 1.0
Specification-Title: hlotimizacao
Specification-Vendor: Liesel007
Specification-Version: 1
Implementation-Title: hlotimizacao
Implementation-Version: $V
Implementation-Vendor: Liesel007
MixinConfigs: hlotimizacao.mixins.json
M
jar cfm ../hlotimizacao-1.18.2-$V.jar ../MANIFEST.MF .
cd .. && ls -la hlotimizacao-1.18.2-$V.jar
