from collections import defaultdict
d=defaultdict(list)
for l in open('/home/claude/b118/out/mixin-out.tsrg'):
    p=l.split()
    if len(p)==3: d[p[0]].append('\t%s %s'%(p[1],p[2]))
    elif len(p)==4: d[p[0]].append('\t%s %s %s'%(p[1],p[2],p[3]))
with open('/home/claude/b118/reobf.tsrg','w') as f:
    f.write(open('/home/claude/b118/off2srg.tsrg').read())
    for c,ms in d.items(): f.write(c+' '+c+'\n'+'\n'.join(ms)+'\n')
