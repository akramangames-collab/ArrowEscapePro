#!/usr/bin/env python3
"""Extend approved mazes with solvable blocking chains. Deterministic, offline."""
import random, math, json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def unpack(line):
    header,body=line.split('|');w,h=map(int,header.split(','));pieces=[]
    for v in body.split(';'):
        a=list(map(int,v.split(',')));pieces.append((a[0],a[1],list(zip(a[2::2],a[3::2]))))
    return w,h,pieces

def nodes(piece):
    out=set();pts=piece[2]
    for (ax,ay),(bx,by) in zip(pts,pts[1:]):
        dx=(bx>ax)-(bx<ax);dy=(by>ay)-(by<ay)
        for k in range(abs(bx-ax)+abs(by-ay)+1):out.add((ax+k*dx,ay+k*dy))
    return out

def ray(piece,w,h):
    dx,dy,pts=piece;x,y=pts[-1];out=set()
    while True:
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:return out
        out.add((x,y))

def stats(pieces,w,h):
    ns=[nodes(p) for p in pieces];rs=[ray(p,w,h) for p in pieces]
    deps=[{j for j,n in enumerate(ns) if i!=j and n&rs[i]} for i in range(len(pieces))]
    initial=sum(not d for d in deps);todo=set(range(len(pieces)));depth={};peak=0
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        if not ready:raise AssertionError('Cycle')
        for i in ready:depth[i]=1+max((depth[j] for j in deps[i]),default=0)
        todo-=set(ready);peak+=1
    return {'arrows':len(pieces),'initial_safe_arrows':initial,'dependency_depth':max(depth.values()),'occupied_nodes':len(set().union(*ns)),'grid':[w,h]}

output=[];metrics=[]
for level,line in enumerate((ROOT/'tests/v15-levels.txt').read_text().splitlines(),1):
    original_w,original_h,original=unpack(line);fraction=(level-1)/199
    base_target=max(48+int(fraction*140), len(original)+30)
    milestone=level%5==0
    boss=level in (25,50,100,150,200)
    target=base_target
    if milestone:
        target=min(225,base_target+max(55,int(base_target*.25))+(25 if boss else 0))
    extra_space=6 if milestone else 0
    w=max(original_w+4,22+int(fraction*12))+extra_space;h=max(original_h+4,26+int(fraction*16))+extra_space
    sx=(w-original_w)//2;sy=(h-original_h)//2
    pieces=[(dx,dy,[(x+sx,y+sy) for x,y in pts]) for dx,dy,pts in original]
    occupancy=set().union(*(nodes(p) for p in pieces));rays=[ray(p,w,h) for p in pieces]
    rng=random.Random(160000+level);misses=0
    while len(pieces)<target:
        available=[i for i,p in enumerate(pieces) if not rays[i]&(occupancy-nodes(p))]
        best=None;best_score=-1
        for attempt in range(170 if milestone else 100):
            tx=rng.randrange(1,w);ty=rng.randrange(1,h)
            if (tx,ty) in occupancy:continue
            dx,dy=rng.choice(((1,0),(-1,0),(0,1),(0,-1)))
            piece=(dx,dy,[(tx,ty)])
            if ray(piece,w,h)&occupancy:continue
            pts=[(tx,ty)];taken={(tx,ty)};cx,cy=tx,ty;bx,by=-dx,-dy
            segment_count=rng.randrange(4,8) if milestone else rng.randrange(3,7)
            for segment in range(segment_count):
                if segment:
                    opts=[(0,1),(0,-1)] if bx else [(1,0),(-1,0)];rng.shuffle(opts)
                    selected=next(((vx,vy) for vx,vy in opts if (cx+vx,cy+vy) not in occupancy|taken and 0<=cx+vx<=w and 0<=cy+vy<=h),None)
                    if selected is None:break
                    bx,by=selected
                length=0
                for k in range(rng.randrange(2,6)):
                    nx,ny=cx+bx,cy+by
                    if not (0<=nx<=w and 0<=ny<=h) or (nx,ny) in occupancy or (nx,ny) in taken:break
                    cx,cy=nx,ny;taken.add((cx,cy));length+=1
                if not length:break
                pts.append((cx,cy))
            if len(pts)<3 or len(taken)<5:continue
            p=(dx,dy,list(reversed(pts)))
            blocked_safe=sum(bool(taken&rays[i]) for i in available)
            blocked_any=sum(bool(taken&r) for r in rays)
            turns=max(0,len(pts)-2)
            score=blocked_safe*(240 if milestone else 120)+blocked_any*(9 if milestone else 4)+turns*(8 if milestone else 2)+len(taken)*.45+rng.random()
            if score>best_score:best=(p,taken);best_score=score
        if best is None:
            misses+=1
            if misses<20:continue
            # Add space, preserving all existing geometry and its solvable order.
            w+=2;h+=2;rays=[ray(p,w,h) for p in pieces];misses=0
            continue
        p,taken=best;pieces.append(p);occupancy|=taken;rays.append(ray(p,w,h));misses=0
    summary=stats(pieces,w,h);summary['level']=level;summary['milestone']=milestone;summary['boss']=boss;metrics.append(summary)
    defs=[]
    for dx,dy,pts in pieces:defs.append(','.join(map(str,[dx,dy]+[v for xy in pts for v in xy])))
    output.append(f'{w},{h}|'+ ';'.join(defs))
    if milestone or level==1:print('Level',level,summary,flush=True)
(ROOT/'app/src/main/assets/levels.txt').write_text('\n'.join(output)+'\n')
(ROOT/'tests/hard-level-metrics.json').write_text(json.dumps(metrics,indent=2)+'\n')
print('Generated 200 dependency-driven mazes with 40 SUPER HARD milestones and 5 boss spikes.',flush=True)
