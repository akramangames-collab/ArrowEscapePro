#!/usr/bin/env python3
"""Generate 200 deterministic hard mazes for V17.

Construction is done in reverse solve order. We start with only a few safe
arrows. Every new arrow is itself removable but its body crosses exactly one
currently-safe arrow's exit ray, so the number of valid opening moves stays
small while the dependency chain becomes very deep.
"""
import random, json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DIRS=((1,0),(-1,0),(0,1),(0,-1))

def nodes(piece):
    dx,dy,pts=piece;out=set()
    for (ax,ay),(bx,by) in zip(pts,pts[1:]):
        sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
        for k in range(abs(bx-ax)+abs(by-ay)+1):
            out.add((ax+sx*k,ay+sy*k))
    return out

def ray(piece,w,h):
    dx,dy,pts=piece;x,y=pts[-1];out=[]
    for _ in range(w+h+30):
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:return out
        out.append((x,y))
    return out

def self_profile(piece,w,h):
    dx,dy,pts=piece
    distance={pts[0]:0};walked=0
    for (ax,ay),(bx,by) in zip(pts,pts[1:]):
        sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
        length=abs(bx-ax)+abs(by-ay)
        for k in range(1,length+1):
            walked+=1
            q=(ax+sx*k,ay+sy*k)
            distance[q]=max(distance.get(q,-1),walked)
    x,y=pts[-1];crossings=0
    for step in range(1,w+h+30):
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:return True,crossings
        own=distance.get((x,y))
        if own is not None:
            if own>=step:return False,crossings
            crossings+=1
    return True,crossings

def clear_indices(pieces,w,h):
    ns=[nodes(p) for p in pieces]
    occupancy=set().union(*ns) if ns else set()
    out=[]
    for i,p in enumerate(pieces):
        if not self_profile(p,w,h)[0]:continue
        if not (set(ray(p,w,h)) & (occupancy-ns[i])):out.append(i)
    return out

def straight_seed(rng,occupancy,w,h):
    for _ in range(3000):
        dx,dy=rng.choice(DIRS)
        if dx>0: hx=w-rng.randrange(2,6);hy=rng.randrange(2,h-1)
        elif dx<0: hx=rng.randrange(2,6);hy=rng.randrange(2,h-1)
        elif dy>0: hx=rng.randrange(2,w-1);hy=h-rng.randrange(2,6)
        else: hx=rng.randrange(2,w-1);hy=rng.randrange(2,6)
        length=rng.randrange(3,8)
        tx,ty=hx-dx*length,hy-dy*length
        if not (1<=tx<w and 1<=ty<h):continue
        p=(dx,dy,[(tx,ty),(hx,hy)])
        n=nodes(p)
        if n&occupancy:continue
        if set(ray(p,w,h))&occupancy:continue
        if not self_profile(p,w,h)[0]:continue
        return p
    return None

def targeted_piece(rng,pieces,occupancy,w,h):
    safe=clear_indices(pieces,w,h)
    if not safe:return None
    rng.shuffle(safe)
    safe_rays={i:set(ray(pieces[i],w,h)) for i in safe}

    for target in safe:
        cells=list(safe_rays[target])
        rng.shuffle(cells)
        for qx,qy in cells[:40]:
            for dx,dy in rng.sample(DIRS,len(DIRS)):
                # q is a bend in the body. The final segment must point in the
                # arrow direction so the existing movement engine stays exact.
                for head_len in rng.sample([2,3,4,5],4):
                    hx,hy=qx+dx*head_len,qy+dy*head_len
                    if not (1<=hx<w and 1<=hy<h):continue
                    # Head ray must be clear so this newly added blocker is the
                    # next removable arrow in reverse-construction order.
                    probe=(dx,dy,[(qx,qy),(hx,hy)])
                    if set(ray(probe,w,h))&occupancy:continue

                    perpendicular=[(-dy,dx),(dy,-dx)]
                    rng.shuffle(perpendicular)
                    for px,py in perpendicular:
                        for tail_len in rng.sample([2,3,4,5,6],5):
                            bx,by=qx+px*tail_len,qy+py*tail_len
                            if not (1<=bx<w and 1<=by<h):continue
                            pts=[(bx,by),(qx,qy),(hx,hy)]

                            # Roughly half the blockers get one extra bend to
                            # make the visual route less obvious.
                            if rng.random()<0.55:
                                back_len=rng.randrange(2,6)
                                ex,ey=bx-dx*back_len,by-dy*back_len
                                if 1<=ex<w and 1<=ey<h:
                                    pts=[(ex,ey),(bx,by),(qx,qy),(hx,hy)]

                            p=(dx,dy,pts)
                            n=nodes(p)
                            if n&occupancy:continue
                            if set(ray(p,w,h))&occupancy:continue
                            ok,_=self_profile(p,w,h)
                            if not ok:continue

                            # Important invariant: block exactly one current
                            # safe arrow. One old safe move disappears and this
                            # new piece becomes safe, keeping opening choices
                            # constant instead of making the board easier.
                            blocked=sum(bool(n&safe_rays[i]) for i in safe)
                            if blocked!=1:continue
                            return p
    return None

def metrics(pieces,w,h):
    ns=[nodes(p) for p in pieces];owner={q:i for i,n in enumerate(ns) for q in n}
    deps=[];crossings=0
    for i,p in enumerate(pieces):
        ok,cross=self_profile(p,w,h)
        assert ok
        crossings+=int(cross>0)
        blockers=set()
        for q in ray(p,w,h):
            j=owner.get(q)
            if j is not None and j!=i:blockers.add(j)
        deps.append(blockers)
    todo=set(range(len(pieces)));depth={}
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        if not ready:raise AssertionError('cycle')
        for i in ready:depth[i]=1+max((depth[j] for j in deps[i]),default=0)
        todo-=set(ready)
    return {
        'arrows':len(pieces),
        'initial_safe_arrows':sum(not d for d in deps),
        'dependency_depth':max(depth.values(),default=0),
        'occupied_nodes':len(set().union(*ns)) if ns else 0,
        'self_crossing_arrows':crossings,
        'grid':[w,h]
    }

def desired_safe(level):
    if level%5==0:return 1
    if level<=4:return 4
    if level<=20:return 3
    return 2

def target_count(level):
    fraction=(level-1)/199
    base=48+int(fraction*140)
    if level%5==0:
        base+=38
        if level in (25,50,100,150,200):base+=16
    return min(215,base)

output=[];all_metrics=[]
for level in range(1,201):
    fraction=(level-1)/199
    goal=desired_safe(level)
    target=target_count(level)
    milestone=level%5==0
    boss=level in (25,50,100,150,200)

    # Keep density high enough to be challenging but preserve visual legibility.
    w=28+int(fraction*18)+(4 if milestone else 0)
    h=34+int(fraction*22)+(5 if milestone else 0)
    rng=random.Random(170000+level)

    pieces=[];occupancy=set()
    for _ in range(goal):
        seed=straight_seed(rng,occupancy,w,h)
        if seed is None:raise AssertionError(f'Level {level}: seed placement failed')
        pieces.append(seed);occupancy|=nodes(seed)

    misses=0
    while len(pieces)<target:
        p=targeted_piece(rng,pieces,occupancy,w,h)
        if p is None:
            misses+=1
            if misses<4:continue
            # More breathing room does not alter already-created geometry.
            w+=2;h+=2;misses=0
            if w>78 or h>92:
                raise AssertionError(f'Level {level}: could not place blocker {len(pieces)}/{target}')
            continue
        pieces.append(p);occupancy|=nodes(p);misses=0

    summary=metrics(pieces,w,h)
    summary.update(level=level,milestone=milestone,boss=boss,target_initial_safe=goal)
    assert summary['initial_safe_arrows']==goal,(level,summary)
    all_metrics.append(summary)

    defs=[]
    for dx,dy,pts in pieces:
        defs.append(','.join(map(str,[dx,dy]+[v for xy in pts for v in xy])))
    output.append(f'{w},{h}|'+ ';'.join(defs))
    if level<=5 or milestone:
        print('Level',level,summary,flush=True)

(ROOT/'app/src/main/assets/levels.txt').write_text('\n'.join(output)+'\n')
(ROOT/'tests/hard-level-metrics.json').write_text(json.dumps(all_metrics,indent=2)+'\n')
print('Generated 200 reverse-constructed V17 levels with constrained valid openings.',flush=True)
