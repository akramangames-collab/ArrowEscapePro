#!/usr/bin/env python3
"""Validate all 200 packed mazes and the preserved V15 movement methods."""
import hashlib, json, re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
lines=(root/'app/src/main/assets/levels.txt').read_text().splitlines()
expected=json.loads((root/'tests/v15-baseline.json').read_text())
assert len(lines)==200
counts=[];safe_counts=[];depths=[]
for index,line in enumerate(lines,1):
    header,payload=line.split('|');width,height=map(int,header.split(','))
    assert len(payload.split(';')) >= 48, f'Level {index}: below hard-mode arrow count'
    pieces=[]
    occupied=set()
    for encoded in payload.split(';'):
        values=list(map(int,encoded.split(',')));dx,dy=values[:2];points=list(zip(values[2::2],values[3::2]))
        assert len(values)>=6 and len(values)%2==0 and abs(dx)+abs(dy)==1
        nodes=set()
        for (ax,ay),(bx,by) in zip(points,points[1:]):
            assert (ax==bx) != (ay==by), f'Level {index}: non-orthogonal segment'
            sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
            for step in range(abs(bx-ax)+abs(by-ay)+1):nodes.add((ax+sx*step,ay+sy*step))
        assert all(0<=x<=width and 0<=y<=height for x,y in nodes)
        assert not nodes & occupied, f'Level {index}: overlapping arrows'
        occupied|=nodes
        ax,ay=points[-2];tx,ty=points[-1]
        assert ((tx>ax)-(tx<ax),(ty>ay)-(ty<ay))==(dx,dy)
        pieces.append((nodes,(tx,ty),dx,dy))
    counts.append(len(pieces))
    all_nodes=[p[0] for p in pieces]
    owner={node:j for j,nodes in enumerate(all_nodes) for node in nodes}
    deps=[]
    for i,(nodes,(x,y),dx,dy) in enumerate(pieces):
        blockers=set()
        for step in range(1,width+height+20):
            nx=x+dx*step;ny=y+dy*step
            if nx<0 or nx>width or ny<0 or ny>height:break
            j=owner.get((nx,ny))
            if j is not None and j!=i:blockers.add(j)
        deps.append(blockers)
    safe_counts.append(sum(not d for d in deps))
    todo=set(range(len(deps)));node_depth={}
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        assert ready,f'Level {index}: dependency graph cycle'
        for i in ready:node_depth[i]=1+max((node_depth[j] for j in deps[i]),default=0)
        todo-=set(ready)
    depths.append(max(node_depth.values(),default=0))
    while pieces:
        progress=False
        for piece in pieces[:]:
            nodes,(x,y),dx,dy=piece;others=occupied-nodes
            clear=True
            for step in range(1,width+height+20):
                nx=x+dx*step;ny=y+dy*step
                if nx<0 or nx>width or ny<0 or ny>height:break
                if (nx,ny) in others:clear=False;break
            if clear:occupied-=nodes;pieces.remove(piece);progress=True
        assert progress,f'Level {index}: unsolvable arrow dependency cycle'
for level in range(5,201,5):
    previous=max(counts[level-5:level-1])
    boost=counts[level-1]-previous
    assert boost>=30, f'Level {level}: SUPER HARD arrow-count spike {boost} < 30'
    assert safe_counts[level-1] <= max(8,int(counts[level-1]*.22)), f'Level {level}: too many obvious exits'
    assert depths[level-1] >= 7, f'Level {level}: dependency depth {depths[level-1]} is too shallow'
for level in (25,50,100,150,200):
    assert counts[level-1]>=150, f'Boss level {level}: not dense enough'
source=(root/'app/src/main/java/com/arrowescape/pro/ArrowGameView.java').read_text()
for name,digest in expected['movement_methods'].items():
    match=re.search(r'\b'+name+r'\s*\([^)]*\)\s*\{',source);assert match,name
    start=source.index('{',match.start());end=start+1;depth=1
    while depth:depth+=(source[end]=='{')-(source[end]=='}');end+=1
    assert hashlib.sha256(source[start:end].encode()).hexdigest()==digest, f'V15 movement changed: {name}'
print(f'200 mazes valid and solvable; 40 SUPER HARD dependency milestones + 5 boss spikes verified; arrow counts {counts[0]} → {counts[-1]}; V15 path/collision methods passed.')
