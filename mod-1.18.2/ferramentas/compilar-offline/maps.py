import re,sys,json
L='/mnt/user-data/uploads/Install/libraries'
merged=L+'/de/oceanlabs/mcp/mcp_config/1.18.2-20220404.173914/mcp_config-1.18.2-20220404.173914-mappings-merged.txt'
moj=L+'/net/minecraft/client/1.18.2-20220404.173914/client-1.18.2-20220404.173914-mappings.txt'
# merged: obf -> official class, srg members (desc in obf)
obf2off={}; msrg={}  # (obfcls,obfname,obfdesc)->srg ; fields (obfcls,obfname)->srg
cur=None
for line in open(merged):
    if line.startswith('tsrg2'): continue
    if not line.startswith('\t'):
        a,b=line.split(); obf2off[a]=b; cur=a; continue
    if line.startswith('\t\t'): continue
    p=line.strip().split()
    if len(p)==2: msrg[(cur,p[0])]=p[1]
    elif len(p)==3: msrg[(cur,p[0],p[1])]=p[2]
off2obf={v:k for k,v in obf2off.items()}
prim={'int':'I','boolean':'Z','void':'V','long':'J','float':'F','double':'D','byte':'B','short':'S','char':'C'}
def jt(t):
    d=0
    while t.endswith('[]'): d+=1;t=t[:-2]
    s=prim.get(t) or 'L'+t.replace('.','/')+';'
    return '['*d+s
def toobf(desc):
    return re.sub(r'L([^;]+);',lambda m:'L'+off2obf.get(m.group(1),m.group(1))+';',desc)
srg2off={}; tsrg={}
cls=None
for line in open(moj):
    if line.startswith('#'): continue
    if not line.startswith(' '):
        off,obf=line.strip().rstrip(':').split(' -> '); cls=off.replace('.','/'); ob=obf.replace('.','/'); tsrg.setdefault(cls,[]); continue
    s=line.strip(); left,obfn=s.split(' -> ')
    left=re.sub(r'^\d+:\d+:','',left)
    if '(' in left:
        rt,rest=left.split(' ',1); name,args=rest.split('(',1); args=args.rstrip(')')
        desc='('+''.join(jt(a) for a in args.split(',') if a)+')'+jt(rt)
        sg=msrg.get((ob,obfn,toobf(desc)))
        if sg and sg.startswith('m_'):
            srg2off[sg]=name; tsrg[cls].append('\t%s %s %s'%(name,desc,sg))
    else:
        t,name=left.split(' ')
        sg=msrg.get((ob,obfn))
        if sg and sg.startswith('f_'):
            srg2off[sg]=name; tsrg[cls].append('\t%s %s'%(name,sg))
json.dump(srg2off,open('srg2off.json','w'))
with open('off2srg.tsrg','w') as f:
    for c,ms in tsrg.items():
        f.write('%s %s\n'%(c,c)); f.write('\n'.join(ms)+('\n' if ms else ''))
print(len(srg2off), sum(len(v) for v in tsrg.values()))
