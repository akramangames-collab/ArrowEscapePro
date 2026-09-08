#!/usr/bin/env python3
"""Generate 200 deterministic, solvable mazes for the V17 self-clearance rules."""
import random, json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

def unpack(line):
    header,body=line.split('|')
    w,h=map(int,header.split(','))
    pieces=[]
    for v in body.split(';'):
        a=list(map(int,v.split(',')))
        pieces.append((a[0],a[1],list(zip(a[2::2],a[3::2]))))
    return w,h,pieces

def nodes(piece):
    out=set();pts=piece[2]
    for (ax,ay),(bx,by) in zip(pts,pts[1:]):
        dx=(bx>ax)-(bx<ax);dy=(by>ay)-(by<ay)
        for k in range(abs(bx-ax)+abs(by-ay)+1):
            out.add((ax+k*dx,ay+k*dy))
    return out

def ray(piece,w,h):
    dx,dy,pts=piece;x,y=pts[-1];out=[]
    while True:
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:return out
        out.append((x,y))

def self_profile(piece,w,h):
    """Return (can fully escape, safe self-crossings).

    The head moves one grid unit forward for each unit the tail advances along
    the existing polyline. A self intersection is a collision only if that body
    cell has not yet been vacated when the head reaches it.
    """
    dx,dy,pts=piece
    if len(pts)<2:return False,0
    distance={}
    walked=0
    distance[pts[0]]=0
    for (ax,ay),(bx,by) in zip(pts,pts[1:]):
        sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
        length=abs(bx-ax)+abs(by-ay)
        for k in range(1,length+1):
            walked+=1
            q=(ax+sx*k,ay+sy*k)
            distance[q]=max(distance.get(q,-1),walked)
    crossings=0
    x,y=pts[-1]
    for step in range(1,w+h+20):
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:return True,crossings
        own=distance.get((x,y))
        if own is not None:
            if own>=step:return False,crossings
            crossings+=1
    return True,crossings

def stats(pieces,w,h):
    ns=[nodes(p) for p in pieces]
    rs=[set(ray(p,w,h)) for p in pieces]
    profiles=[self_profile(p,w,h) for p in pieces]
    assert all(ok for ok,_ in profiles),'self-blocked piece entered final level'
    deps=[{j for j,n in enumerate(ns) if i!=j and n&rs[i]} for i in range(len(pieces))]
    initial=sum(not d for d in deps)
    todo=set(range(len(pieces)));depth={}
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        if not ready:raise AssertionError('Dependency cycle')
        for i in ready:depth[i]=1+max((depth[j] for j in deps[i]),default=0)
        todo-=set(ready)
    return {
        'arrows':len(pieces),
        'initial_safe_arrows':initial,
        'dependency_depth':max(depth.values(),default=0),
        'occupied_nodes':len(set().union(*ns)) if ns else 0,
        'self_crossing_arrows':sum(crossings>0 for _,crossings in profiles),
        'grid':[w,h],
    }

def available_indices(pieces,occupancy,rays):
    return [i for i,p in enumerate(pieces) if not set(rays[i])&(occupancy-nodes(p))]

def make_candidate(rng,pieces,occupancy,rays,w,h,milestone,aggressive=False):
    available=available_indices(pieces,occupancy,rays)
    best=None;best_score=-1e18
    attempts=(360 if milestone else 240) if aggressive else (220 if milestone else 140)
    for _ in range(attempts):
        tx=rng.randrange(1,w);ty=rng.randrange(1,h)
        if (tx,ty) in occupancy:continue
        dx,dy=rng.choice(((1,0),(-1,0),(0,1),(0,-1)))
        seed=(dx,dy,[(tx,ty)])
        if set(ray(seed,w,h))&occupancy:continue

        pts=[(tx,ty)];taken={(tx,ty)};cx,cy=tx,ty;bx,by=-dx,-dy
        segment_count=rng.randrange(5,9) if milestone else rng.randrange(3,8)
        for segment in range(segment_count):
            if segment:
                opts=[(0,1),(0,-1)] if bx else [(1,0),(-1,0)]
                rng.shuffle(opts)
                selected=next(((vx,vy) for vx,vy in opts
                               if (cx+vx,cy+vy) not in occupancy|taken
                               and 0<=cx+vx<=w and 0<=cy+vy<=h),None)
                if selected is None:break
                bx,by=selected
            length=0
            for _ in range(rng.randrange(2,7 if milestone else 6)):
                nx,ny=cx+bx,cy+by
                if not (0<=nx<=w and 0<=ny<=h) or (nx,ny) in occupancy or (nx,ny) in taken:break
                cx,cy=nx,ny;taken.add((cx,cy));length+=1
            if not length:break
            pts.append((cx,cy))

        if len(pts)<3 or len(taken)<5:continue
        p=(dx,dy,list(reversed(pts)))
        ok,self_crossings=self_profile(p,w,h)
        if not ok:continue
        if set(ray(p,w,h))&occupancy:continue  # new piece must be removable in reverse construction order

        blocked_safe=sum(bool(taken&set(rays[i])) for i in available)
        blocked_any=sum(bool(taken&set(r)) for r in rays)
        turns=max(0,len(pts)-2)
        score=(
            blocked_safe*(480 if aggressive else (300 if milestone else 170))
            +blocked_any*(12 if milestone else 6)
            +turns*(12 if milestone else 4)
            +self_crossings*(18 if milestone else 7)
            +len(taken)*.55
            +rng.random()
        )
        if aggressive and blocked_safe<2:
            score-=1000
        if score>best_score:
            best=(p,taken,blocked_safe);best_score=score
    return best

def desired_safe(level,milestone):
    if milestone:return 1
    if level<=4:return 5
    if level<=20:return 4
    if level<=80:return 3
    return 2

output=[];metrics=[]
source=(ROOT/'tests/v15-levels.txt').read_text().splitlines()

for level,line in enumerate(source,1):
    original_w,original_h,original=unpack(line)
    fraction=(level-1)/199
    base_target=max(48+int(fraction*140),len(original)+30)
    milestone=level%5==0
    boss=level in (25,50,100,150,200)
    target=base_target
    if milestone:
        target=min(225,base_target+max(55,int(base_target*.25))+(25 if boss else 0))

    extra_space=8 if milestone else 5
    w=max(original_w+4,22+int(fraction*12))+extra_space
    h=max(original_h+4,26+int(fraction*16))+extra_space
    sx=(w-original_w)//2;sy=(h-original_h)//2

    shifted=[(dx,dy,[(x+sx,y+sy) for x,y in pts]) for dx,dy,pts in original]
    pieces=[p for p in shifted if self_profile(p,w,h)[0]]
    occupancy=set().union(*(nodes(p) for p in pieces)) if pieces else set()
    rays=[ray(p,w,h) for p in pieces]

    rng=random.Random(170000+level)
    misses=0
    while len(pieces)<target:
        best=make_candidate(rng,pieces,occupancy,rays,w,h,milestone,False)
        if best is None:
            misses+=1
            if misses<15:continue
            w+=2;h+=2
            rays=[ray(p,w,h) for p in pieces]
            misses=0
            continue
        p,taken,_=best
        pieces.append(p);occupancy|=taken;rays.append(ray(p,w,h));misses=0

    # Tighten the opening. Each added arrow is itself removable, but is chosen
    # to block at least two currently-safe arrows, reducing obvious first moves.
    goal=desired_safe(level,milestone)
    for _ in range(55):
        safe_now=len(available_indices(pieces,occupancy,rays))
        if safe_now<=goal:break
        best=make_candidate(rng,pieces,occupancy,rays,w,h,milestone,True)
        if best is None or best[2]<2:
            w+=2;h+=2;rays=[ray(p,w,h) for p in pieces]
            continue
        p,taken,_=best
        pieces.append(p);occupancy|=taken;rays.append(ray(p,w,h))

    summary=stats(pieces,w,h)
    summary['level']=level;summary['milestone']=milestone;summary['boss']=boss
    summary['target_initial_safe']=goal
    if summary['initial_safe_arrows']>goal:
        raise AssertionError(f"Level {level}: could not tighten opening to {goal}; got {summary['initial_safe_arrows']}")
    metrics.append(summary)

    defs=[]
    for dx,dy,pts in pieces:
        defs.append(','.join(map(str,[dx,dy]+[v for xy in pts for v in xy])))
    output.append(f'{w},{h}|'+ ';'.join(defs))
    if milestone or level<=4:
        print('Level',level,summary,flush=True)

(ROOT/'app/src/main/assets/levels.txt').write_text('\n'.join(output)+'\n')
(ROOT/'tests/hard-level-metrics.json').write_text(json.dumps(metrics,indent=2)+'\n')
print('Generated 200 V17 self-clearance mazes with constrained opening choices.',flush=True)
