#!/usr/bin/env python3
"""Prepare the V17 level pack for the self-tail clearance rule.

The V16.0.3 pack is already dense and solvable. V17 changes removal physics:
an arrow is rejected if its head would hit its own still-present tail while the
snake body advances. A permanently self-blocked shape could never become valid
later, so we repair only those shapes by trimming the minimum number of oldest
tail segments. Trimming removes occupancy and therefore cannot break the
existing solution order or introduce a new collision.
"""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
LEVELS=ROOT/'app/src/main/assets/levels.txt'

def parse(line):
    header,body=line.split('|',1)
    w,h=map(int,header.split(','))
    pieces=[]
    for raw in body.split(';'):
        a=list(map(int,raw.split(',')))
        pieces.append((a[0],a[1],list(zip(a[2::2],a[3::2]))))
    return w,h,pieces

def cells(piece):
    out=set();pts=piece[2]
    for (ax,ay),(bx,by) in zip(pts,pts[1:]):
        sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
        for k in range(abs(bx-ax)+abs(by-ay)+1):
            out.add((ax+sx*k,ay+sy*k))
    return out

def ray(piece,w,h):
    dx,dy,pts=piece;x,y=pts[-1];out=[]
    for _ in range(w+h+20):
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:break
        out.append((x,y))
    return out

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
    x,y=pts[-1];crossings=0
    for step in range(1,w+h+20):
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:return True,crossings
        own=distance.get((x,y))
        if own is not None:
            if own>=step:return False,crossings
            crossings+=1
    return True,crossings

def repair(piece,w,h):
    ok,_=self_profile(piece,w,h)
    if ok:return piece,False
    dx,dy,pts=piece
    # Preserve the head and as much of the bent body as possible.
    for cut in range(1,len(pts)-1):
        candidate=(dx,dy,pts[cut:])
        if self_profile(candidate,w,h)[0]:
            return candidate,True
    # The final head segment always points opposite the remaining tail, so this
    # last-segment fallback is guaranteed to be self-clear.
    return (dx,dy,pts[-2:]),True

source=LEVELS.read_text().splitlines()
assert len(source)==200
out=[];metrics=[];total_repairs=0

for level,line in enumerate(source,1):
    w,h,pieces=parse(line)
    repaired=[];repairs=0
    for p in pieces:
        q,changed=repair(p,w,h)
        repaired.append(q);repairs+=int(changed)
    total_repairs+=repairs

    ns=[cells(p) for p in repaired]
    occupied=set().union(*ns) if ns else set()
    assert sum(len(n) for n in ns)==len(occupied),f'Level {level}: overlap introduced'

    safe=0;self_crossing=0
    for i,p in enumerate(repaired):
        ok,cross=self_profile(p,w,h);assert ok
        self_crossing+=int(cross>0)
        own=ns[i]
        if not any(q in occupied and q not in own for q in ray(p,w,h)):
            safe+=1

    metrics.append({
        'level':level,'arrows':len(repaired),'initial_safe_arrows':safe,
        'repaired_self_blocks':repairs,'self_crossing_arrows':self_crossing,
        'grid':[w,h],'milestone':level%5==0,
        'boss':level in (25,50,100,150,200)
    })

    defs=[]
    for dx,dy,pts in repaired:
        defs.append(','.join(map(str,[dx,dy]+[v for xy in pts for v in xy])))
    out.append(f'{w},{h}|'+ ';'.join(defs))

LEVELS.write_text('\n'.join(out)+'\n')
(ROOT/'tests/hard-level-metrics.json').write_text(json.dumps(metrics,indent=2)+'\n')
print(f'V17 self-clearance pack ready: 200 levels, {total_repairs} impossible self-tail shapes minimally repaired.')
