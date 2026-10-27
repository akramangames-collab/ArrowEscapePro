#!/usr/bin/env python3
"""Deterministic 1000-level dense campaign with one physically legal solve sequence."""
import hashlib,json,random,argparse
from pathlib import Path
from weave_levels import expand,piece_of,ray
from dense_campaign import densify,water_seconds
ROOT=Path(__file__).resolve().parents[1]
def unpack(line):
    header,payload=line.split('|');w,h=map(int,header.split(','));walks=[]
    for raw in payload.split(';'):
        v=list(map(int,raw.split(',')));walks.append(expand(list(zip(v[2::2],v[3::2]))))
    return w,h,walks
def dependencies(w,h,walks):
    owner={q:i for i,p in enumerate(walks) for q in p}
    return [{owner[q] for q in ray(piece_of(p),w,h) if q in owner and owner[q]!=i} for i,p in enumerate(walks)]
def longest(w,h,walks):
    deps=dependencies(w,h,walks);memo={}
    def visit(i):
        if i not in memo:memo[i]=max((visit(j) for j in sorted(deps[i])),key=len,default=[])+[i]
        return memo[i]
    return [walks[i] for i in max((visit(i) for i in range(len(walks))),key=len)]
def forced(w,h,walks):
    return all(not ds if i==0 else i-1 in ds and all(j<i for j in ds) for i,ds in enumerate(dependencies(w,h,walks)))
def pack(w,h,walks):
    return f'{w},{h}|'+ ';'.join(','.join(map(str,[dx,dy]+[v for q in pts for v in q])) for dx,dy,pts,_ in [piece_of(p) for p in walks])
def tutorial(level):
    a=[[(16,14),(16,3),(10,3)]] if level==1 else ([[(13,3),(10,3)],[(16,3),(15,3)],[(16,14),(16,5)]] if level==5 else [[(16,3),(10,3)],[(16,14),(16,5)]])
    b=[[(2,19),(16,19),(16,16)]] if level<=2 else [[(16,19),(16,16)],[(2,19),(14,19)]]
    c=[[(5,5),(2,5),(2,17)]] if level<=3 else [[(2,14),(2,17)],[(5,5),(2,5),(2,12)]]
    return 18,22,[expand(p) for p in a+b+c]
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args();seeds=[]
    for line in (ROOT/'scripts/seeds/water-source-v50.txt').read_text().splitlines():
        w,h,walks=unpack(line);seeds.append((w,h,longest(w,h,walks)))
    seeds.sort(key=lambda s:len(s[2]));outputs=[];metrics=[];seen=set()
    for level in range(1,1001):
        rng=random.Random(510000+level);rank=min(399,max(0,120+int((level-6)/994*279)))
        w,h,source=seeds[rank];walks=[p.copy() for p in source]
        if level<=5:w,h,walks=tutorial(level)
        else:
            for i in rng.sample(range(len(walks)),len(walks)):
                cut=rng.randint(0,min(3,len(walks[i])-2))
                if cut:
                    trial=walks.copy();trial[i]=walks[i][cut:]
                    if forced(w,h,trial):walks=trial
        flipx=rng.choice([False,True]);flipy=rng.choice([False,True])
        walks=[[(2*(w//2)-x if flipx else x,2*(h//2)-y if flipy else y) for x,y in p] for p in walks]
        assert forced(w,h,walks);solved=walks[-1][-1];rng.shuffle(walks)
        density=densify(w,h,walks,level)
        output=pack(w,h,walks)
        assert output not in seen;seen.add(output);outputs.append(output)
        area=(w+1)*(h+1)-81
        used=sum(len(p) for p in walks)
        metrics.append(dict(level=level,chapter=(level-1)//40+1,arrows=len(walks),occupied_cells=used,playable_cells=area,density=round(used/area,4),unique_solution=True,gold_key_head=solved,water_seconds=water_seconds(level,len(walks)),sha256=hashlib.sha256(output.encode()).hexdigest()))
    for path,data in {ROOT/'app/src/main/assets/levels.txt':'\n'.join(outputs)+'\n',ROOT/'tests/water-level-metrics.json':json.dumps(metrics,indent=2)+'\n'}.items():
        if args.check:assert path.read_text()==data
        else:path.write_text(data)
    print('1000 deterministic dense chapter layouts generated/verified.')
if __name__=='__main__':main()
