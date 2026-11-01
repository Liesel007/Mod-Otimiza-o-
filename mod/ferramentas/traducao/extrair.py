"""Lista os textos traduziveis do codigo: modelos de LOGGER, Msg.t, Msg.f e m(), e textos para revisar
(constantes traduzidas na hora de mostrar). Uso: extrair.py <pasta do codigo> <saida.json>"""
import os, re, json, sys
raiz = sys.argv[1]
STR = r'"((?:[^"\\\n]|\\.)*)"'
def jdec(p):
    out=[];i=0
    while i<len(p):
        c=p[i]
        if c=='\\':
            n=p[i+1]
            if n=='u': out.append(chr(int(p[i+2:i+6],16))); i+=6; continue
            out.append({'n':'\n','t':'\t','"':'"','\\':'\\',"'":"'",'r':'\r'}.get(n,n)); i+=2; continue
        out.append(c); i+=1
    return ''.join(out)
def ler_args(s, i):
    depth=0; args=[]; cur=[]; j=i
    while j<len(s):
        c=s[j]
        if c=='"':
            k=j+1
            while s[k]!='"':
                if s[k]=='\\': k+=1
                k+=1
            cur.append(s[j:k+1]); j=k+1; continue
        if c=="'":
            k=j+1
            while s[k]!="'":
                if s[k]=='\\': k+=1
                k+=1
            cur.append(s[j:k+1]); j=k+1; continue
        if c in '([{': depth+=1
        if c in ')]}':
            if depth==0: args.append(''.join(cur)); return args,j
            depth-=1
        if c==',' and depth==0: args.append(''.join(cur)); cur=[]; j+=1; continue
        cur.append(c); j+=1
    return args,j
def literal(expr):
    partes=re.findall(STR, expr); resto=re.sub(STR,'',expr)
    if resto.replace('+','').strip()!='' or not partes: return None
    return ''.join(jdec(p) for p in partes)
chamada = re.compile(r'(?:LOGGER\.(?:info|warn|error|debug)|Msg\.t|Msg\.f|(?<![\w.])m)\(')
modelos={}; revisar=[]
CONST_FILES={'Modulo.java','DiagnosticoAmbiente.java','DiagnosticoLag.java'}
for d,_,fs in os.walk(raiz):
    for f in fs:
        if not f.endswith('.java'): continue
        s=open(os.path.join(d,f)).read()
        for mt in chamada.finditer(s):
            args,_=ler_args(s,mt.end())
            if not args or not args[0].strip(): continue
            lit=literal(args[0])
            if lit is not None: modelos.setdefault(lit,f); continue
            # nao literal: pega strings literais inteiras dentro (ternarios, switch)
            for p in re.findall(STR, args[0]):
                v=jdec(p)
                if re.search(r'[A-Za-zÀ-ú]{3}', v): revisar.append((f,v))
        if f in CONST_FILES or f=='Config.java':
            # concatena literais adjacentes "a" + "b" e pega textos longos
            for m in re.finditer(r'(?:'+STR+r'\s*\+\s*)*'+STR, s):
                v=literal(m.group(0))
                if v and len(v)>=12 and ' ' in v: revisar.append((f,v))
json.dump({'modelos':modelos,'revisar':revisar}, open(sys.argv[2],'w'), ensure_ascii=False, indent=1)
print(len(modelos),'modelos;',len(revisar),'para revisar')
