import os, re, sys
ids = set(l.strip() for l in open('ids.txt') if l.strip())
print("wanted ids:", len(ids))
cands = []
for root in ('build', os.path.expanduser('~/.gradle')):
    for dp, dn, fn in os.walk(root):
        for f in fn:
            if f.endswith(('.srg', '.tsrg', '.tiny')) or f in ('methods.csv', 'fields.csv'):
                p = os.path.join(dp, f)
                try:
                    if os.path.getsize(p) > 200000: cands.append(p)
                except OSError: pass
print("candidate mapping files:")
for p in cands: print("  ", p, os.path.getsize(p))
idre = re.compile(r'\b[mf]_\d+_\b')
ident = re.compile(r'^[A-Za-z_][A-Za-z0-9_$]*$')
found = {}
for p in cands:
    if '/.gradle/' in p and 'srg' not in p.lower(): continue
    shown = 0
    with open(p, errors='replace') as fh:
        for line in fh:
            hit = [i for i in idre.findall(line) if i in ids]
            if not hit: continue
            if shown < 3:
                print("RAW", os.path.basename(p), repr(line.rstrip())[:240]); shown += 1
            toks = line.split()
            names = []
            for t in toks:
                last = t.rsplit('/', 1)[-1]
                if t.endswith(':') or t.startswith('('): continue
                if ident.match(last) and not idre.fullmatch(last):
                    names.append(last)
            for i in hit:
                for n in names:
                    found.setdefault(i, set()).add(n)
print("resolved:", len(found), "of", len(ids))
for i in sorted(found): print("MAP", i, " ".join(sorted(found[i])))
print("unresolved:", " ".join(sorted(ids - set(found))))
