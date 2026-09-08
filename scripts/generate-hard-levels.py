#!/usr/bin/env python3
"""Prepare the V17 level pack for self-tail clearance.

V16.0.3 already contains 200 dense, solver-verified dependency mazes. V17 adds
a stricter physics rule: an arrow whose head reaches its own body before the
tail has vacated that cell cannot escape. Such geometry is permanently
impossible, so it must never ship in a solvable level.

This pass keeps the approved dense level geometry and removes only arrows that
fail the new self-clearance simulation. Removing an impossible arrow cannot
create a dependency cycle; it can only free existing arrows. The verifier then
re-solves all 200 levels using the exact V17 rule.
"""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
LEVELS=ROOT/'app/src/main/assets/levels.txt'

def unpack(line):
    header,body=line.split('|')
    w,h=map(int,header.split(','))
    pieces=[]
    for v in body.split(';'):
        a=list(map(int,v.split(',')))
        pieces.append((a[0],a[1],list(zip(a[2::2],a[3::2]))))
    return w,h,pieces

def pack(w,h,pieces):
    defs=[]
    for dx,dy,pts in pieces:
        defs.append(','.join(map(str,[dx,dy]+[v for xy in pts for v in xy])))
    return f'{w},{h}|'+ ';'.join(defs)

def nodes(piece):
    out=set();pts=piece[2]
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

def metrics(pieces,w,h):
    ns=[nodes(p) for p in pieces]
    owner={q:i for i,n in enumerate(ns) for q in n}
    deps=[];safe_crossings=0
    for i,p in enumerate(pieces):
        ok,cross=self_profile(p,w,h)
        assert ok
        safe_crossings+=int(cross>0)
        blockers=set()
        for q in ray(p,w,h):
            j=owner.get(q)
            if j is not None and j!=i:blockers.add(j)
        deps.append(blockers)
    todo=set(range(len(pieces)));depth={}
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        if not ready:raise AssertionError('dependency cycle')
        for i in ready:
            depth[i]=1+max((depth[j] for j in deps[i]),default=0)
        todo-=set(ready)
    return {
        'arrows':len(pieces),
        'initial_safe_arrows':sum(not d for d in deps),
        'dependency_depth':max(depth.values(),default=0),
        'self_crossing_arrows':safe_crossings,
        'occupied_nodes':len(set().union(*ns)) if ns else 0,
        'grid':[w,h],
    }

source=LEVELS.read_text().splitlines()
assert len(source)==200
output=[];all_metrics=[];removed_total=0

for level,line in enumerate(source,1):
    w,h,pieces=unpack(line)
    kept=[];removed=0
    for p in pieces:
        ok,_=self_profile(p,w,h)
        if ok:kept.append(p)
        else:removed+=1
    if not kept:raise AssertionError(f'Level {level}: no arrows left after V17 filtering')
    summary=metrics(kept,w,h)
    summary.update(level=level,removed_self_blocked=removed,milestone=level%5==0,boss=level in (25,50,100,150,200))
    all_metrics.append(summary);removed_total+=removed
    output.append(pack(w,h,kept))
    if level<=5 or level%5==0:
        print('Level',level,summary,flush=True)

LEVELS.write_text('\n'.join(output)+'\n')
(ROOT/'tests/hard-level-metrics.json').write_text(json.dumps(all_metrics,indent=2)+'\n')
print(f'Prepared 200 V17 levels; removed {removed_total} permanently self-blocked arrows and preserved every remaining dependency maze.',flush=True)
