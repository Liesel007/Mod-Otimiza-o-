"""Gera config/MensagensEn.java a partir de en.py e confere a cobertura.

Uso (de qualquer pasta): python3 mod/ferramentas/traducao/gerar.py
Falha (codigo 1) se algum texto do codigo nao tiver traducao ou se os {} / %d nao baterem.
"""
import json, os, re, sys, subprocess, tempfile
AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
from en import TR
raiz = os.path.normpath(os.path.join(AQUI, '..', '..', 'src', 'main', 'java', 'com', 'hlotimizacao'))
saida = os.path.join(tempfile.gettempdir(), 'hlo_textos.json')
subprocess.run([sys.executable, os.path.join(AQUI, 'extrair.py'), raiz, saida], check=True)
d = json.load(open(saida))
IGNORAR={'HL Otimizacao - diagnostico de lag','pehkui 85% (virtuoel...ScaleData.getScale), avaritia 15% (...)','%s %d%% (%s)','Minecraft/Forge',
         "\n# limite_enxame: spawn natural, de geracao de chunk e de spawner e cancelado quando ja ha 'numero' entidades daquele\n",'nao necessario','desligado: Icterine já faz isso'}
precisa=set(d['modelos'])|{v for f,v in d['revisar'] if v not in IGNORAR}
falta=[k for k in precisa if k not in TR]
sobra=[k for k in TR if k not in precisa]
spec=re.compile(r'\{\}|%[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]')
ruim=[(k,v) for k,v in TR.items() if sorted(spec.findall(k))!=sorted(spec.findall(v)) or spec.findall(k)!=spec.findall(v)]
print('faltam',len(falta)); [print('  ',repr(k)) for k in falta]
print('sobram',len(sobra)); [print('  ',repr(k)) for k in sobra]
print('placeholders diferentes',len(ruim)); [print('  ',repr(k)) for k,_ in ruim]
def jlit(s): return '"'+s.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n')+'"'
linhas=',\n'.join(f'            Map.entry({jlit(k)},\n                    {jlit(v)})' for k,v in TR.items())
java=f'''package com.hlotimizacao.config;

import java.util.Map;

/**
 * Traducao para ingles dos textos do mod. A chave e o modelo exatamente como esta no codigo (em portugues).
 * Gerado a partir da lista de modelos do codigo; um modelo sem traducao aparece em portugues.
 */
final class MensagensEn {{
    static final Map<String, String> MAPA = Map.ofEntries(
{linhas});

    private MensagensEn() {{
    }}
}}
'''
open(os.path.join(raiz, 'config', 'MensagensEn.java'), 'w').write(java)
if falta or ruim:
    sys.exit(1)
