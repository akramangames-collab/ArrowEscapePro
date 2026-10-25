#!/usr/bin/env python3
"""Deterministic 1000-level campaign with one physically legal solve sequence."""
import hashlib,json,random,argparse
from pathlib import Path
from weave_levels import expand,piece_of,ray,self_profile
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

def water_seconds(level,arrows):
    if level<=5:return 120
    rate=23 if level<=40 else 20 if level<=160 else 18 if level<=400 else 16 if level<=700 else 15
    return max(45,(arrows*rate+5)//10+8)

def grow_tails(w,h,walks,level,attempt,target=.82):
    """Fill empty grid cells with physical arrow bodies while preserving one winning order."""
    rng=random.Random(610000+11*level+attempt)
    occupied={q for walk in walks for q in walk}
    ray_owners={}
    for i,walk in enumerate(walks):
        for q in ray(piece_of(walk),w,h):ray_owners.setdefault(q,set()).add(i)
    usable=(w+1)*(h+1)-81
    directions=[(0,1),(0,-1),(1,0),(-1,0)]
    for sweep in range(34):
        order=list(range(len(walks)));rng.shuffle(order);changed=False
        for i in order:
            if len(occupied)>=int(target*usable):return round(len(occupied)/usable,5)
            path=walks[i]
            if len(path)>=33:continue
            x,y=path[0];previous=(x-path[1][0],y-path[1][1]);options=[]
            for dx,dy in directions:
                q=(x+dx,y+dy)
                if not(0<=q[0]<=w and 0<=q[1]<=h):continue
                if abs(q[0]-w//2)<=4 and abs(q[1]-h//2)<=4:continue
                if q in occupied or any(j<i for j in ray_owners.get(q,())):continue
                if not self_profile(piece_of([q]+path),w,h):continue
                free=sum(0<=q[0]+a<=w and 0<=q[1]+b<=h
                    and (q[0]+a,q[1]+b) not in occupied for a,b in directions)
                score=free+(3 if (dx,dy)==previous else 0)+rng.random()*2
                options.append((score,q))
            if options:
                q=max(options)[1];occupied.add(q);walks[i]=[q]+path;changed=True
        if not changed:break
    return round(len(occupied)/usable,5)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args();seeds=[]
    for line in (ROOT/'scripts/seeds/water-source-v50.txt').read_text().splitlines():
        w,h,walks=unpack(line);seeds.append((w,h,longest(w,h,walks)))
    seeds.sort(key=lambda s:len(s[2]));outputs=[];metrics=[];seen=set()
    for level in range(1,1001):
        rng=random.Random(510000+level)
        rank=min(399,max(0,round(25+(level-6)/994*374)))
        w,h,source=seeds[rank];walks=[p.copy() for p in source]
        if level<=5:w,h,walks=tutorial(level)
        else:
            best=(-1,None)
            for attempt in range(3):
                trial=[p.copy() for p in source]
                score=grow_tails(w,h,trial,level,attempt)
                if score>best[0]:best=(score,trial)
                if score>=.82:break
            _,walks=best
        flipx=rng.choice([False,True]);flipy=rng.choice([False,True])
        walks=[[(w-x if flipx else x,h-y if flipy else y) for x,y in p] for p in walks]
        assert forced(w,h,walks),(level,'non-unique')
        solved=walks[-1][-1];rng.shuffle(walks);output=pack(w,h,walks)
        assert output not in seen;seen.add(output);outputs.append(output)
        occupied_cells=sum(map(len,walks));usable_cells=(w+1)*(h+1)-81
        metrics.append(dict(level=level,chapter=1+(level-1)//20,arrows=len(walks),
            occupied_cells=occupied_cells,usable_cells=usable_cells,
            board_density=round(occupied_cells/usable_cells,5),
            unique_solution=True,gold_key_head=solved,
            water_seconds=water_seconds(level,len(walks)),
            sha256=hashlib.sha256(output.encode()).hexdigest()))
    for path,data in {ROOT/'app/src/main/assets/levels.txt':'\n'.join(outputs)+'\n',ROOT/'tests/water-level-metrics.json':json.dumps(metrics,indent=2)+'\n'}.items():
        if args.check:assert path.read_text()==data
        else:path.write_text(data)
    print('PASS: 1000 dense unique-sequence rescue boards; density %.1f%%-%.1f%%'%(min(m['board_density'] for m in metrics[5:])*100,max(m['board_density'] for m in metrics[5:])*100))
if __name__=='__main__':main()
