import os, re
ids = set(l.strip() for l in open('ids.txt') if l.strip())
idre = re.compile(r'\b[mf]_\d+_\b')
files = []
for dp, dn, fn in os.walk('build'):
    for f in fn:
        if f in ('output.srg', 'output.tsrg'):
            files.append(os.path.join(dp, f))
print("files:", files)
found = {}
for p in files:
    for line in open(p, errors='replace'):
        line = line.rstrip('\n')
        if line.startswith(('MD:', 'FD:')):
            t = line.split()
            # MD: srgOwner/m_1_ (desc)R mojOwner/name (desc)R     FD: srgOwner/f_1_ mojOwner/name
            first = t[1].rsplit('/', 1)[-1]
            second = t[3].rsplit('/', 1)[-1] if line.startswith('MD:') else t[2].rsplit('/', 1)[-1]
            if first in ids:
                found.setdefault(first, set()).add(second)
        elif line.startswith('\t'):
            t = line.split()
            # \tmojName (desc) srgId   (methods)     \tmojName srgId (fields)
            if len(t) >= 2 and t[-1] in ids:
                found.setdefault(t[-1], set()).add(t[0])
print("resolved:", len(found), "of", len(ids))
amb = {i: n for i, n in found.items() if len(n) > 1}
print("ambiguous:", amb)
for i in sorted(found): print("MAP", i, " ".join(sorted(found[i])))
print("unresolved:", " ".join(sorted(ids - set(found))))
