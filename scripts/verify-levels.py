#!/usr/bin/env python3
"""Validate all 200 V17 mazes against the real self-tail clearance rule."""
import hashlib, json, re
from pathlib import Path

root=Path(__file__).resolve().parents[1]
lines=(root/'app/src/main/assets/levels.txt').read_text().splitlines()
expected=json.loads((root/'tests/v15-baseline.json').read_text())
assert len(lines)==200

def self_profile(piece,width,height):
    nodes,pts,dx,dy=piece
    distance={pts[0]:0};walked=0
    for (ax,ay),(bx,by) in zip(pts,pts[1:]):
        sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
        length=abs(bx-ax)+abs(by-ay)
        for k in range(1,length+1):
            walked+=1
            q=(ax+sx*k,ay+sy*k)
            distance[q]=max(distance.get(q,-1),walked)
    x,y=pts[-1];crossings=0
    for step in range(1,width+height+20):
        x+=dx;y+=dy
        if x<0 or x>width or y<0 or y>height:return True,crossings
        own=distance.get((x,y))
        if own is not None:
            if own>=step:return False,crossings
            crossings+=1
    return True,crossings

counts=[];safe_counts=[];depths=[];self_crossing_counts=[]

for index,line in enumerate(lines,1):
    header,payload=line.split('|');width,height=map(int,header.split(','))
    pieces=[];occupied=set()

    for encoded in payload.split(';'):
        values=list(map(int,encoded.split(',')));dx,dy=values[:2];pts=list(zip(values[2::2],values[3::2]))
        assert len(values)>=6 and len(values)%2==0 and abs(dx)+abs(dy)==1
        nodes=set()
        for (ax,ay),(bx,by) in zip(pts,pts[1:]):
            assert (ax==bx)!=(ay==by),f'Level {index}: non-orthogonal segment'
            sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
            for step in range(abs(bx-ax)+abs(by-ay)+1):
                nodes.add((ax+sx*step,ay+sy*step))
        assert all(0<=x<=width and 0<=y<=height for x,y in nodes)
        assert not nodes&occupied,f'Level {index}: overlapping arrows'
        occupied|=nodes
        ax,ay=pts[-2];tx,ty=pts[-1]
        assert ((tx>ax)-(tx<ax),(ty>ay)-(ty<ay))==(dx,dy),f'Level {index}: arrow direction mismatch'
        piece=(nodes,pts,dx,dy)
        ok,_=self_profile(piece,width,height)
        assert ok,f'Level {index}: permanently self-blocked arrow'
        pieces.append(piece)

    assert len(pieces)>=48,f'Level {index}: below hard-mode arrow count'
    counts.append(len(pieces))

    owner={node:j for j,p in enumerate(pieces) for node in p[0]}
    deps=[];crossing=0
    for i,piece in enumerate(pieces):
        nodes,pts,dx,dy=piece
        ok,cross=self_profile(piece,width,height);assert ok
        crossing+=int(cross>0)
        x,y=pts[-1];blockers=set()
        for step in range(1,width+height+20):
            x+=dx;y+=dy
            if x<0 or x>width or y<0 or y>height:break
            j=owner.get((x,y))
            if j is not None and j!=i:blockers.add(j)
        deps.append(blockers)
    self_crossing_counts.append(crossing)
    safe_counts.append(sum(not d for d in deps))

    todo=set(range(len(deps)));node_depth={}
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        assert ready,f'Level {index}: dependency graph cycle'
        for i in ready:
            node_depth[i]=1+max((node_depth[j] for j in deps[i]),default=0)
        todo-=set(ready)
    depths.append(max(node_depth.values(),default=0))

    # Full solve using the same two conditions as the app:
    # own tail must clear, and the head ray must not hit another active arrow.
    active=list(pieces);active_nodes=set(occupied)
    while active:
        progress=False
        for piece in active[:]:
            own_nodes,pts,dx,dy=piece
            ok,_=self_profile(piece,width,height)
            if not ok:continue
            others=active_nodes-own_nodes
            x,y=pts[-1];clear=True
            for step in range(1,width+height+20):
                x+=dx;y+=dy
                if x<0 or x>width or y<0 or y>height:break
                if (x,y) in others:
                    clear=False;break
            if clear:
                active_nodes-=own_nodes;active.remove(piece);progress=True
        assert progress,f'Level {index}: unsolvable under V17 self-clearance'

for level in range(1,201):
    milestone=level%5==0
    allowed=1 if milestone else (5 if level<=4 else 4 if level<=20 else 3 if level<=80 else 2)
    assert safe_counts[level-1]<=allowed,f'Level {level}: {safe_counts[level-1]} obvious first moves > {allowed}'

for level in range(5,201,5):
    previous=max(counts[level-5:level-1])
    assert counts[level-1]-previous>=30,f'Level {level}: SUPER HARD density spike is too small'
    assert depths[level-1]>=8,f'Level {level}: dependency chain too shallow'

for level in (25,50,100,150,200):
    assert counts[level-1]>=150,f'Boss level {level}: not dense enough'
    assert safe_counts[level-1]==1,f'Boss level {level}: must have exactly one valid opening move'

source=(root/'app/src/main/java/com/arrowescape/pro/ArrowGameView.java').read_text()
for name,digest in expected['movement_methods'].items():
    if name=='isClear':
        continue  # intentionally upgraded in V17
    match=re.search(r'\b'+name+r'\s*\([^)]*\)\s*\{',source);assert match,name
    start=source.index('{',match.start());end=start+1;depth=1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}');end+=1
    assert hashlib.sha256(source[start:end].encode()).hexdigest()==digest,f'Approved V15 movement changed: {name}'

assert 'private boolean hasSelfClearance(Piece target)' in source
isclear=re.search(r'private boolean isClear\(Piece target\)\s*\{([^}]|\}(?!\n\n))*',source,re.S)
assert 'hasSelfClearance(target)' in source[source.index('private boolean isClear(Piece target)'):source.index('private long nodeKey')]
assert 'Tail blocks this escape' in source

print(
    f'V17 verified: 200 solvable mazes, zero permanent self-blocks, '
    f'{sum(self_crossing_counts)} safe self-crossing arrows, constrained openings, '
    f'40 SUPER HARD milestones and approved V15 snake movement preserved.'
)
