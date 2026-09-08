#!/usr/bin/env python3
"""Validate all 200 levels under V17 full self-tail clearance."""
import hashlib,json,re
from pathlib import Path

root=Path(__file__).resolve().parents[1]
lines=(root/'app/src/main/assets/levels.txt').read_text().splitlines()
expected=json.loads((root/'tests/v15-baseline.json').read_text())
assert len(lines)==200

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
    for step in range(1,w+h+20):
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:return True,crossings
        own=distance.get((x,y))
        if own is not None:
            if own>=step:return False,crossings
            crossings+=1
    return True,crossings

counts=[];safe_counts=[];self_crossings=[]

for level,line in enumerate(lines,1):
    header,payload=line.split('|',1);w,h=map(int,header.split(','))
    pieces=[];occupied=set()

    for encoded in payload.split(';'):
        a=list(map(int,encoded.split(',')));dx,dy=a[:2];pts=list(zip(a[2::2],a[3::2]))
        assert len(pts)>=2 and abs(dx)+abs(dy)==1
        ns=set()
        for (ax,ay),(bx,by) in zip(pts,pts[1:]):
            assert (ax==bx)!=(ay==by),f'Level {level}: non-orthogonal segment'
            sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
            for k in range(abs(bx-ax)+abs(by-ay)+1):
                ns.add((ax+sx*k,ay+sy*k))
        assert all(0<=x<=w and 0<=y<=h for x,y in ns)
        assert not ns&occupied,f'Level {level}: overlapping arrows'
        occupied|=ns
        ax,ay=pts[-2];tx,ty=pts[-1]
        assert ((tx>ax)-(tx<ax),(ty>ay)-(ty<ay))==(dx,dy),f'Level {level}: direction mismatch'
        piece=(ns,pts,dx,dy)
        ok,_=self_profile(piece,w,h)
        assert ok,f'Level {level}: permanently self-blocked arrow'
        pieces.append(piece)

    counts.append(len(pieces))
    owner={q:i for i,p in enumerate(pieces) for q in p[0]}
    safe=0;crossing=0
    for i,p in enumerate(pieces):
        _,pts,dx,dy=p
        ok,cross=self_profile(p,w,h);assert ok
        crossing+=int(cross>0)
        x,y=pts[-1];blocked=False
        for _ in range(w+h+20):
            x+=dx;y+=dy
            if x<0 or x>w or y<0 or y>h:break
            j=owner.get((x,y))
            if j is not None and j!=i:
                blocked=True;break
        safe+=int(not blocked)
    safe_counts.append(safe);self_crossings.append(crossing)
    assert 1<=safe<=max(8,int(len(pieces)*.25)),f'Level {level}: opening too loose ({safe}/{len(pieces)})'

    # Greedy elimination is valid because removing an arrow only removes
    # blockers. It must be possible to clear every board under the exact V17
    # self-tail + other-arrow rule.
    active=list(pieces);active_nodes=set(occupied)
    while active:
        progress=False
        for p in active[:]:
            own,pts,dx,dy=p
            if not self_profile(p,w,h)[0]:continue
            others=active_nodes-own
            x,y=pts[-1];clear=True
            for _ in range(w+h+20):
                x+=dx;y+=dy
                if x<0 or x>w or y<0 or y>h:break
                if (x,y) in others:
                    clear=False;break
            if clear:
                active_nodes-=own;active.remove(p);progress=True
        assert progress,f'Level {level}: unsolvable under V17 clearance'

# Milestones remain materially denser than their immediate neighborhood for
# most of the progression; late-game boards are already near the density cap.
for level in range(5,151,5):
    prev=max(counts[level-k-1] for k in (1,2,3,4))
    assert counts[level-1]>=prev+15,f'Level {level}: milestone density spike too small'
for level,minimum in ((25,100),(50,120),(100,150),(150,170),(200,180)):
    assert counts[level-1]>=minimum,f'Boss {level}: insufficient density'

source=(root/'app/src/main/java/com/arrowescape/pro/ArrowGameView.java').read_text()
for name,digest in expected['movement_methods'].items():
    if name=='isClear':continue
    m=re.search(r'\b'+name+r'\s*\([^)]*\)\s*\{',source);assert m,name
    start=source.index('{',m.start());end=start+1;depth=1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}');end+=1
    assert hashlib.sha256(source[start:end].encode()).hexdigest()==digest,f'Approved V15 movement changed: {name}'
assert 'private boolean hasSelfClearance(Piece target)' in source
block=source[source.index('private boolean isClear(Piece target)'):source.index('private long nodeKey')]
assert 'hasSelfClearance(target)' in block
assert 'Tail blocks this escape' in source

print(f'V17 verified: 200 solvable levels, no permanent self-tail traps, {sum(self_crossings)} safe self-crossing arrows, V15 snake animation preserved.')
