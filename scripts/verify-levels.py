#!/usr/bin/env python3
"""Validate all 200 V17 dependency-chain levels under self-tail clearance."""
import hashlib, json, re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
LINES=(ROOT/'app/src/main/assets/levels.txt').read_text().splitlines()
BASE=json.loads((ROOT/'tests/v15-baseline.json').read_text())
assert len(LINES)==200

def self_profile(piece,w,h):
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
    for step in range(1,w+h+40):
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:return True,crossings
        own=distance.get((x,y))
        if own is not None:
            if own>=step:return False,crossings
            crossings+=1
    return True,crossings

def allowed_openings(level):
    if level==1:return 3
    if level<=4:return 2
    if level<=20 and level%5:return 2
    return 1

counts=[];safe_counts=[];depths=[];crossing_counts=[]

for level,line in enumerate(LINES,1):
    header,payload=line.split('|');w,h=map(int,header.split(','))
    pieces=[];occupied=set()

    for encoded in payload.split(';'):
        values=list(map(int,encoded.split(',')))
        assert len(values)>=6 and len(values)%2==0
        dx,dy=values[:2];pts=list(zip(values[2::2],values[3::2]))
        assert abs(dx)+abs(dy)==1

        nodes=set();seen=set()
        for (ax,ay),(bx,by) in zip(pts,pts[1:]):
            assert (ax==bx)!=(ay==by),f'Level {level}: non-orthogonal segment'
            sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
            for k in range(abs(bx-ax)+abs(by-ay)+1):
                q=(ax+sx*k,ay+sy*k)
                if q in seen and q not in (ax,ay):
                    raise AssertionError(f'Level {level}: self-overlapping arrow')
                seen.add(q);nodes.add(q)
        assert all(0<=x<=w and 0<=y<=h for x,y in nodes)
        assert not nodes&occupied,f'Level {level}: overlapping arrows'
        occupied|=nodes

        ax,ay=pts[-2];tx,ty=pts[-1]
        assert ((tx>ax)-(tx<ax),(ty>ay)-(ty<ay))==(dx,dy),f'Level {level}: direction mismatch'
        piece=(nodes,pts,dx,dy)
        ok,_=self_profile(piece,w,h)
        assert ok,f'Level {level}: permanently self-blocked arrow'
        pieces.append(piece)

    counts.append(len(pieces))
    minimum=30 if level<5 else 55 if level<20 else 60
    assert len(pieces)>=minimum,f'Level {level}: too sparse ({len(pieces)})'

    owner={node:i for i,p in enumerate(pieces) for node in p[0]}
    deps=[];crossing=0
    for i,piece in enumerate(pieces):
        nodes,pts,dx,dy=piece
        ok,cross=self_profile(piece,w,h);assert ok
        crossing+=int(cross>0)
        x,y=pts[-1];blockers=set()
        for _ in range(w+h+40):
            x+=dx;y+=dy
            if x<0 or x>w or y<0 or y>h:break
            j=owner.get((x,y))
            if j is not None and j!=i:blockers.add(j)
        deps.append(blockers)

    safe=sum(not d for d in deps)
    safe_counts.append(safe);crossing_counts.append(crossing)
    assert 1<=safe<=allowed_openings(level),f'Level {level}: opening moves {safe}, allowed {allowed_openings(level)}'

    todo=set(range(len(deps)));depth={}
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        assert ready,f'Level {level}: dependency cycle'
        for i in ready:depth[i]=1+max((depth[j] for j in deps[i]),default=0)
        todo-=set(ready)
    depths.append(max(depth.values(),default=0))

    # Full solve under the same rule as the app.
    active=list(pieces);active_nodes=set(occupied);removed=0
    while active:
        progress=False
        for piece in active[:]:
            own,pts,dx,dy=piece
            ok,_=self_profile(piece,w,h)
            if not ok:continue
            others=active_nodes-own
            x,y=pts[-1];clear=True
            for _ in range(w+h+40):
                x+=dx;y+=dy
                if x<0 or x>w or y<0 or y>h:break
                if (x,y) in others:
                    clear=False;break
            if clear:
                active_nodes-=own;active.remove(piece);removed+=1;progress=True
        assert progress,f'Level {level}: unsolvable under V17 rule'
    assert removed==len(pieces)

for level in range(5,201,5):
    assert safe_counts[level-1]==1,f'Level {level}: SUPER HARD must have exactly one opening'
    assert depths[level-1]>=20,f'Level {level}: dependency chain too shallow ({depths[level-1]})'

for level in (25,50,100,150,200):
    assert safe_counts[level-1]==1
    assert counts[level-1]>=70,f'Boss level {level}: not dense enough'

source=(ROOT/'app/src/main/java/com/arrowescape/pro/ArrowGameView.java').read_text()
for name,digest in BASE['movement_methods'].items():
    if name=='isClear':
        continue
    match=re.search(r'\b'+name+r'\s*\([^)]*\)\s*\{',source);assert match,name
    start=source.index('{',match.start());end=start+1;nest=1
    while nest:
        nest+=(source[end]=='{')-(source[end]=='}');end+=1
    assert hashlib.sha256(source[start:end].encode()).hexdigest()==digest,f'Approved V15 movement changed: {name}'

assert 'private boolean hasSelfClearance(Piece target)' in source
isclear=source[source.index('private boolean isClear(Piece target)'):source.index('private long nodeKey')]
assert 'hasSelfClearance(target)' in isclear
assert 'Tail blocks this escape' in source

print(
    f'V17 HARD verified: 200 solvable levels, opening moves {min(safe_counts)}–{max(safe_counts)}, '
    f'40 SUPER HARD levels with exactly one opening, dependency depth up to {max(depths)}, '
    f'{sum(crossing_counts)} safe self-crossing arrows, arrow counts {min(counts)}–{max(counts)}, '
    f'approved V15 snake movement preserved.'
)
