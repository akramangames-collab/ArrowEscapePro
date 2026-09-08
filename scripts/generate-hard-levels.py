#!/usr/bin/env python3
"""Generate 200 genuinely hard V17 levels.

Construction is dependency-first instead of density-first.  Each chain is built
in reverse solve order: every newly-added arrow is itself removable, but its
body crosses the previous arrow's forward ray.  Therefore the finished chain
has only its final arrow available, and removing it exposes the previous arrow.

The same geometry is checked against V17 self-tail clearance before it is
accepted, so every shipped arrow is physically escapable under the approved
snake/path-following animation.
"""
import json, random
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
LEVELS=ROOT/'app/src/main/assets/levels.txt'
DIRS=((1,0),(-1,0),(0,1),(0,-1))

def inside(x,y,w,h):
    return 0<=x<=w and 0<=y<=h

def expand(pts):
    out=[];seen=set()
    if not pts:return out
    out.append(pts[0]);seen.add(pts[0])
    for (ax,ay),(bx,by) in zip(pts,pts[1:]):
        sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
        if not ((ax==bx)^(ay==by)):return None
        for k in range(1,abs(bx-ax)+abs(by-ay)+1):
            q=(ax+sx*k,ay+sy*k)
            if q in seen:return None
            seen.add(q);out.append(q)
    return out

def nodes(piece):
    cells=expand(piece[2])
    return set(cells) if cells else set()

def ray(piece,w,h):
    dx,dy,pts=piece;x,y=pts[-1];out=[]
    for _ in range(w+h+40):
        x+=dx;y+=dy
        if not inside(x,y,w,h):return out
        out.append((x,y))
    return out

def self_profile(piece,w,h):
    dx,dy,pts=piece
    cells=expand(pts)
    if not cells:return False,0
    distance={q:i for i,q in enumerate(cells)}
    x,y=pts[-1];crossings=0
    for step in range(1,w+h+40):
        x+=dx;y+=dy
        if not inside(x,y,w,h):return True,crossings
        own=distance.get((x,y))
        if own is not None:
            if own>=step:return False,crossings
            crossings+=1
    return True,crossings

def ray_clear(piece,occupancy,w,h):
    return not (set(ray(piece,w,h)) & occupancy)

def valid_piece(piece,occupancy,w,h):
    cells=nodes(piece)
    if len(cells)<3 or cells&occupancy:return False
    if not all(inside(x,y,w,h) for x,y in cells):return False
    dx,dy,pts=piece
    ax,ay=pts[-2];tx,ty=pts[-1]
    if ((tx>ax)-(tx<ax),(ty>ay)-(ty<ay))!=(dx,dy):return False
    ok,_=self_profile(piece,w,h)
    return ok and ray_clear(piece,occupancy,w,h)

def perpendicular(d):
    dx,dy=d
    return ((-dy,dx),(dy,-dx))

def make_standard(q,d,rng,w,h,occupancy):
    """L/zigzag blocker whose final segment crosses q."""
    qx,qy=q;dx,dy=d
    for _ in range(18):
        forward=rng.choice((1,2,2,3))
        back=rng.choice((2,3,4))
        tip=(qx+dx*forward,qy+dy*forward)
        elbow=(qx-dx*back,qy-dy*back)
        if not inside(*tip,w,h) or not inside(*elbow,w,h):continue

        pts=[elbow,tip]
        # Most arrows get one or two tail bends; the final segment still points d.
        if rng.random()<0.78:
            px,py=rng.choice(perpendicular(d));length=rng.choice((1,2,3,4))
            tail=(elbow[0]+px*length,elbow[1]+py*length)
            if inside(*tail,w,h):pts=[tail,elbow,tip]
            if rng.random()<0.34 and len(pts)==3:
                # Second bend makes S/zigzag bodies without changing the head.
                ex,ey=tail
                bx,by=-dx,-dy
                length2=rng.choice((1,2,3))
                tail2=(ex+bx*length2,ey+by*length2)
                if inside(*tail2,w,h):pts=[tail2,tail,elbow,tip]
        p=(dx,dy,pts)
        if q not in nodes(p):continue
        if valid_piece(p,occupancy,w,h):return p
    return None

def make_safe_hook(q,d,rng,w,h,occupancy):
    """A U-shaped arrow whose very tail lies ahead of its own head.

    It looks self-blocked, but the crossing is at tail-distance zero, so the
    tail vacates before the head reaches that cell.  This exercises V17's full
    self-clearance simulation without creating impossible pieces.
    """
    qx,qy=q;dx,dy=d
    for _ in range(14):
        tip=(qx+dx*rng.choice((1,2)), qy+dy*rng.choice((1,2)))
        # Correct the independent random above to stay collinear with d.
        if dx:
            tip=(qx+dx*rng.choice((1,2)),qy)
        else:
            tip=(qx,qy+dy*rng.choice((1,2)))
        elbow=(qx-dx*rng.choice((2,3)),qy-dy*rng.choice((2,3)))
        if dx:elbow=(qx-dx*rng.choice((2,3)),qy)
        else:elbow=(qx,qy-dy*rng.choice((2,3)))
        cross=(tip[0]+dx*rng.choice((4,5,6)),tip[1]+dy*rng.choice((4,5,6)))
        if dx:cross=(tip[0]+dx*rng.choice((4,5,6)),tip[1])
        else:cross=(tip[0],tip[1]+dy*rng.choice((4,5,6)))
        px,py=rng.choice(perpendicular(d));side=rng.choice((2,3))
        p1=(cross[0]+px*side,cross[1]+py*side)
        p2=(elbow[0]+px*side,elbow[1]+py*side)
        pts=[cross,p1,p2,elbow,tip]
        if not all(inside(x,y,w,h) for x,y in pts):continue
        p=(dx,dy,pts)
        if q not in nodes(p):continue
        ok,crossings=self_profile(p,w,h)
        if crossings<1 or not ok:continue
        if valid_piece(p,occupancy,w,h):return p
    return None

def candidate_blocker(prev,occupancy,w,h,rng,prefer_hook):
    r=[q for q in ray(prev,w,h) if q not in occupancy]
    if not r:return None
    # Avoid the boundary-most cell so the new arrow has room to develop.
    pool=r[:-1] if len(r)>1 else r
    # Prefer mid/early ray positions, keeping the next arrow's ray long.
    weighted=pool[:max(1,int(len(pool)*0.78))]
    attempts=list(weighted)
    rng.shuffle(attempts)
    pd=(prev[0],prev[1])
    dirs=list(perpendicular(pd));rng.shuffle(dirs)
    for q in attempts:
        for d in dirs:
            # Try the richer hook on a minority of pieces, then fall back.
            if prefer_hook:
                p=make_safe_hook(q,d,rng,w,h,occupancy)
                if p is not None:return p
            p=make_standard(q,d,rng,w,h,occupancy)
            if p is not None:return p
    return None

def seed_piece(occupancy,w,h,rng):
    for _ in range(900):
        d=rng.choice(DIRS);dx,dy=d
        # Keep the seed away from the boundary so its chain has room.
        tx=rng.randrange(5,max(6,w-4));ty=rng.randrange(5,max(6,h-4))
        tip=(tx,ty)
        back=rng.choice((3,4,5));elbow=(tx-dx*back,ty-dy*back)
        if not inside(*elbow,w,h):continue
        pts=[elbow,tip]
        if rng.random()<.8:
            px,py=rng.choice(perpendicular(d));ln=rng.choice((1,2,3))
            tail=(elbow[0]+px*ln,elbow[1]+py*ln)
            if inside(*tail,w,h):pts=[tail,elbow,tip]
        p=(dx,dy,pts)
        if valid_piece(p,occupancy,w,h):return p
    return None

def desired_chains(level):
    if level==1:return 3
    if level<=4:return 2
    if level<=20 and level%5:return 2
    return 1

def target_arrows(level):
    f=(level-1)/199
    target=34+round(f*126)  # 34 -> 160
    if level%5==0:target+=24
    if level in (25,50,100,150,200):target+=12
    return min(190,target)

def build_level(level):
    target=target_arrows(level);chains=desired_chains(level)
    frac=(level-1)/199
    base_w=32+round(frac*16)+(4 if level%5==0 else 0)
    base_h=36+round(frac*18)+(4 if level%5==0 else 0)

    for restart in range(28):
        w=base_w+(restart//5)*2
        h=base_h+(restart//5)*2
        rng=random.Random(17000000+level*1009+restart*7919)
        pieces=[];occupancy=set();ok=True
        sizes=[target//chains]*chains
        for i in range(target%chains):sizes[i]+=1

        for chain_size in sizes:
            seed=seed_piece(occupancy,w,h,rng)
            if seed is None:ok=False;break
            pieces.append(seed);occupancy|=nodes(seed);prev=seed

            for i in range(chain_size-1):
                prefer_hook=((i+level)%7==0 or (level%5==0 and (i+level)%5==0))
                nxt=candidate_blocker(prev,occupancy,w,h,rng,prefer_hook)
                if nxt is None:
                    ok=False;break
                pieces.append(nxt);occupancy|=nodes(nxt);prev=nxt
            if not ok:break

        if ok and len(pieces)==target:
            return w,h,pieces
    raise AssertionError(f'Level {level}: could not build {target} arrows after retries')

def dependencies(pieces,w,h):
    ns=[nodes(p) for p in pieces]
    owner={q:i for i,n in enumerate(ns) for q in n}
    deps=[]
    for i,p in enumerate(pieces):
        blockers=set()
        for q in ray(p,w,h):
            j=owner.get(q)
            if j is not None and j!=i:blockers.add(j)
        deps.append(blockers)
    return deps

def summarize(level,w,h,pieces):
    deps=dependencies(pieces,w,h)
    todo=set(range(len(pieces)));depth={}
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        if not ready:raise AssertionError(f'Level {level}: dependency cycle')
        for i in ready:depth[i]=1+max((depth[j] for j in deps[i]),default=0)
        todo-=set(ready)
    crossings=sum(self_profile(p,w,h)[1]>0 for p in pieces)
    return {
        'level':level,'arrows':len(pieces),
        'initial_safe_arrows':sum(not d for d in deps),
        'dependency_depth':max(depth.values(),default=0),
        'self_crossing_arrows':crossings,
        'occupied_nodes':len(set().union(*(nodes(p) for p in pieces))),
        'grid':[w,h],'milestone':level%5==0,
        'boss':level in (25,50,100,150,200)
    }

def pack(w,h,pieces):
    defs=[]
    for dx,dy,pts in pieces:
        defs.append(','.join(map(str,[dx,dy]+[v for xy in pts for v in xy])))
    return f'{w},{h}|'+ ';'.join(defs)

output=[];metrics=[]
for level in range(1,201):
    w,h,pieces=build_level(level)
    summary=summarize(level,w,h,pieces)
    allowed=desired_chains(level)
    if summary['initial_safe_arrows']>allowed:
        raise AssertionError(f"Level {level}: {summary['initial_safe_arrows']} opening moves > {allowed}")
    output.append(pack(w,h,pieces));metrics.append(summary)
    if level<=5 or level%5==0:
        print('Level',level,summary,flush=True)

LEVELS.write_text('\n'.join(output)+'\n')
(ROOT/'tests/hard-level-metrics.json').write_text(json.dumps(metrics,indent=2)+'\n')
print('Generated 200 dependency-chain levels: 1–3 valid opening moves, self-clearance safe, milestone/boss difficulty locked.',flush=True)
