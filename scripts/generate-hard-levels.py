#!/usr/bin/env python3
"""Generate 200 deterministic V17 mazes with full self-tail clearance.

Levels are constructed in reverse-removal order. Every newly-added arrow can
escape against the arrows already placed, which guarantees a solution by
removing arrows in reverse construction order. Later arrows deliberately block
earlier safe arrows so the finished board exposes only a few valid choices.
"""
import random, json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

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
    dx,dy,pts=piece
    if len(pts)<2:return False,0
    distance={pts[0]:0};walked=0
    for (ax,ay),(bx,by) in zip(pts,pts[1:]):
        sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
        length=abs(bx-ax)+abs(by-ay)
        for k in range(1,length+1):
            walked+=1
            q=(ax+sx*k,ay+sy*k)
            distance[q]=max(distance.get(q,-1),walked)
    crossings=0;x,y=pts[-1]
    for step in range(1,w+h+20):
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:return True,crossings
        own=distance.get((x,y))
        if own is not None:
            if own>=step:return False,crossings
            crossings+=1
    return True,crossings

def safe_indices(pieces,occupancy,rays):
    out=[]
    for i,p in enumerate(pieces):
        own=nodes(p)
        if not any(q in occupancy and q not in own for q in rays[i]):
            out.append(i)
    return out

def open_ray_cells(piece,w,h,occupancy):
    return [q for q in ray(piece,w,h) if q not in occupancy]

def build_body(rng,tip,direction,occupancy,w,h,milestone):
    dx,dy=direction;tx,ty=tip
    pts=[(tx,ty)];taken={(tx,ty)};cx,cy=tx,ty;bx,by=-dx,-dy
    segments=rng.randrange(5,9) if milestone else rng.randrange(3,8)
    for segment in range(segments):
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
    if len(pts)<3 or len(taken)<5:return None
    p=(dx,dy,list(reversed(pts)))
    ok,crossings=self_profile(p,w,h)
    if not ok:return None
    if set(ray(p,w,h))&occupancy:return None
    return p,taken,crossings

def candidate(rng,pieces,occupancy,rays,w,h,milestone,want_block):
    safe=safe_indices(pieces,occupancy,rays)
    dirs=((1,0),(-1,0),(0,1),(0,-1))
    safe_ray_union=set()
    if not want_block:
        for i in safe:safe_ray_union.update(rays[i])

    # We accept the first strong valid candidate instead of scoring hundreds
    # of alternatives. This keeps all 200 levels fast to generate in CI.
    attempts=70 if milestone else 48
    for _ in range(attempts):
        if want_block and safe:
            victim=rng.choice(safe)
            cells=[q for q in rays[victim] if q not in occupancy]
            if not cells:continue
            tx,ty=rng.choice(cells[:min(len(cells),10)])
        else:
            tx=rng.randrange(1,w);ty=rng.randrange(1,h)
            if (tx,ty) in occupancy:continue

        choices=list(dirs);rng.shuffle(choices)
        direction=None
        for d in choices:
            probe=(d[0],d[1],[(tx,ty)])
            if all(q not in occupancy for q in ray(probe,w,h)):
                direction=d;break
        if direction is None:continue

        built=build_body(rng,(tx,ty),direction,occupancy,w,h,milestone)
        if built is None:continue
        p,taken,crossings=built

        if not want_block and taken&safe_ray_union:
            continue
        # In blocking mode the tip itself sits on the victim's open ray, so
        # this new piece definitely removes at least one previously-safe move.
        return p,taken,1 if want_block and safe else 0
    return None

def target_safe(level):
    if level%5==0:return 1
    if level<=4:return 5
    if level<=20:return 4
    if level<=80:return 3
    return 2

def target_arrows(level):
    fraction=(level-1)/199
    base=48+int(fraction*140)
    if level%5==0:
        base+=max(55,int(base*.28))
    if level in (25,50,100,150,200):
        base+=35
    return min(225,base)

def level_stats(pieces,w,h):
    ns=[nodes(p) for p in pieces];rs=[set(ray(p,w,h)) for p in pieces]
    deps=[{j for j,n in enumerate(ns) if i!=j and n&rs[i]} for i in range(len(pieces))]
    todo=set(range(len(pieces)));depth={}
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        if not ready:raise AssertionError('cycle')
        for i in ready:depth[i]=1+max((depth[j] for j in deps[i]),default=0)
        todo-=set(ready)
    profiles=[self_profile(p,w,h) for p in pieces]
    assert all(ok for ok,_ in profiles)
    return {
        'arrows':len(pieces),
        'initial_safe_arrows':sum(not d for d in deps),
        'dependency_depth':max(depth.values(),default=0),
        'occupied_nodes':len(set().union(*ns)),
        'self_crossing_arrows':sum(c>0 for _,c in profiles),
        'grid':[w,h],
    }

output=[];metrics=[]

for level in range(1,201):
    fraction=(level-1)/199
    milestone=level%5==0
    boss=level in (25,50,100,150,200)
    target=target_arrows(level)
    goal=target_safe(level)
    w=24+int(fraction*19)+(7 if milestone else 0)
    h=30+int(fraction*21)+(7 if milestone else 0)

    rng=random.Random(170000+level)
    pieces=[];occupancy=set();rays=[];misses=0

    while len(pieces)<target:
        current_safe=len(safe_indices(pieces,occupancy,rays))
        want_block=bool(pieces) and current_safe>=goal
        best=candidate(rng,pieces,occupancy,rays,w,h,milestone,want_block)

        if best is None:
            misses+=1
            # A little more room makes long bent arrows possible without
            # weakening the dependency rule.
            if misses>=12:
                w+=2;h+=2;rays=[ray(p,w,h) for p in pieces];misses=0
            continue

        p,taken,_=best
        pieces.append(p);occupancy|=taken;rays.append(ray(p,w,h));misses=0

    summary=level_stats(pieces,w,h)
    summary.update(level=level,milestone=milestone,boss=boss,target_initial_safe=goal)
    if summary['initial_safe_arrows']>goal:
        raise AssertionError(f"Level {level}: opening {summary['initial_safe_arrows']} > {goal}")
    metrics.append(summary)

    defs=[','.join(map(str,[dx,dy]+[v for xy in pts for v in xy])) for dx,dy,pts in pieces]
    output.append(f'{w},{h}|'+ ';'.join(defs))
    if level<=4 or milestone:
        print('Level',level,summary,flush=True)

(ROOT/'app/src/main/assets/levels.txt').write_text('\n'.join(output)+'\n')
(ROOT/'tests/hard-level-metrics.json').write_text(json.dumps(metrics,indent=2)+'\n')
print('Generated 200 V17 self-clearance mazes with deliberately constrained opening moves.',flush=True)
