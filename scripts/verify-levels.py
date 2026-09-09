#!/usr/bin/env python3
"""Validate all 200 V17 levels under self-tail clearance."""
import hashlib, json, re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
LINES=(ROOT/'app/src/main/assets/levels.txt').read_text().splitlines()
BASE=json.loads((ROOT/'tests/v15-baseline.json').read_text())
assert len(LINES)==200
assert len(set(LINES))==200, 'Duplicate levels'
METRICS=json.loads((ROOT/'tests/hard-level-metrics.json').read_text())
assert len(METRICS)==200

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
    for step in range(1,w+h+30):
        x+=dx;y+=dy
        if x<0 or x>w or y<0 or y>h:return True,crossings
        own=distance.get((x,y))
        if own is not None:
            if own>=step:return False,crossings
            crossings+=1
    return True,crossings

def simulate_self(pts,dx,dy,w,h):
    """Independent tail-advance simulation, including simultaneous contact."""
    body=[pts[0]]
    for (ax,ay),(bx,by) in zip(pts,pts[1:]):
        sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
        body += [(ax+k*sx,ay+k*sy) for k in range(1,abs(bx-ax)+abs(by-ay)+1)]
    x,y=body[-1]
    while 0<=x<=w and 0<=y<=h:
        x+=dx;y+=dy
        body.pop(0)
        if (x,y) in body:return False
        body.append((x,y))
    return True

def frontier_bound(deps):
    """Exact maximum antichain (Dilworth): bounds choices in EVERY solve state.

    Compute reachability, then maximum matching in the comparable-node graph.
    Unlike sampling one removal order, this includes adversarial tap orders.
    """
    reachable={}
    def visit(i):
        if i not in reachable:
            reachable[i]=set()
            for j in deps[i]:reachable[i].add(j);reachable[i].update(visit(j))
        return reachable[i]
    for i in range(len(deps)):visit(i)
    matched={}
    def augment(i,seen):
        for j in sorted(reachable[i]):
            if j in seen:continue
            seen.add(j)
            if j not in matched or augment(matched[j],seen):
                matched[j]=i;return True
        return False
    return len(deps)-sum(augment(i,set()) for i in range(len(deps)))

# A tail that has cleared must pass; an early/late hook collision and exact
# simultaneous tail contact must fail. These do not use generator templates.
for pts,expected in [
    ([(5,1),(5,3),(0,3),(0,1),(2,1)],True),
    ([(0,0),(4,0),(4,2),(1,2),(1,1),(2,1)],False),
    ([(4,0),(4,4),(0,4),(0,2),(2,2)],False),
    ([(0,0),(8,0),(8,3),(0,3),(0,1),(2,1)],False),
]:
    # Last fixture has a later head-ray encounter; own-body timing is checked
    # by an explicit moving body, not only a cached distance comparison.
    sim=simulate_self(pts,1,0,20,20)
    assert sim==expected,(pts,sim)
    assert self_profile((set(),pts,1,0),20,20)[0]==sim

counts=[];safe_counts=[];depths=[];crossing_counts=[];widths=[]

for level,line in enumerate(LINES,1):
    header,payload=line.split('|');w,h=map(int,header.split(','))
    pieces=[];occupied=set()
    for encoded in payload.split(';'):
        values=list(map(int,encoded.split(',')))
        assert len(values)>=6 and len(values)%2==0
        dx,dy=values[:2];pts=list(zip(values[2::2],values[3::2]))
        assert abs(dx)+abs(dy)==1

        nodes=set()
        for (ax,ay),(bx,by) in zip(pts,pts[1:]):
            assert (ax==bx)!=(ay==by),f'Level {level}: non-orthogonal segment'
            sx=(bx>ax)-(bx<ax);sy=(by>ay)-(by<ay)
            for k in range(abs(bx-ax)+abs(by-ay)+1):
                nodes.add((ax+sx*k,ay+sy*k))
        assert all(0<=x<=w and 0<=y<=h for x,y in nodes)
        assert not nodes&occupied,f'Level {level}: overlapping arrows'
        path_length=sum(abs(bx-ax)+abs(by-ay) for (ax,ay),(bx,by) in zip(pts,pts[1:]))
        assert len(nodes)==path_length+1,f'Level {level}: self-intersecting starting body'
        occupied|=nodes

        ax,ay=pts[-2];tx,ty=pts[-1]
        assert ((tx>ax)-(tx<ax),(ty>ay)-(ty<ay))==(dx,dy),f'Level {level}: direction mismatch'
        piece=(nodes,pts,dx,dy)
        ok,_=self_profile(piece,w,h)
        assert ok,f'Level {level}: permanently self-blocked arrow remained'
        assert simulate_self(pts,dx,dy,w,h),f'Level {level}: moving-body simulation failed'
        pieces.append(piece)

    counts.append(len(pieces))
    assert len(pieces)>=24,f'Level {level}: became too sparse'

    owner={node:i for i,p in enumerate(pieces) for node in p[0]}
    deps=[];crossing=0
    for i,piece in enumerate(pieces):
        nodes,pts,dx,dy=piece
        ok,cross=self_profile(piece,w,h);assert ok
        crossing+=int(cross>0)
        x,y=pts[-1];blockers=set()
        for _ in range(w+h+30):
            x+=dx;y+=dy
            if x<0 or x>w or y<0 or y>h:break
            j=owner.get((x,y))
            if j is not None and j!=i:blockers.add(j)
        deps.append(blockers)
    crossing_counts.append(crossing)
    safe_counts.append(sum(not d for d in deps))
    expected_openings=1 if level%5==0 else 3 if level==1 else 2
    assert safe_counts[-1]==expected_openings,f'Level {level}: wrong opening count {safe_counts[-1]}'

    todo=set(range(len(deps)));depth={}
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        assert ready,f'Level {level}: dependency cycle'
        for i in ready:
            depth[i]=1+max((depth[j] for j in deps[i]),default=0)
        todo-=set(ready)
    depths.append(max(depth.values(),default=0))
    assert depths[-1]>=len(pieces)*(.75 if level%5==0 else .50),f'Level {level}: shallow dependencies'
    width=frontier_bound(deps);widths.append(width)
    assert width<=expected_openings+1,f'Level {level}: too many choices later in the solve ({width})'
    assert crossing>=(2 if level%5==0 else 1),f'Level {level}: missing safe self-tail crossings'
    recorded=METRICS[level-1]
    for key,value in dict(level=level,arrows=len(pieces),grid=[w,h],initial_safe_arrows=safe_counts[-1],
                          dependency_depth=depths[-1],self_crossing_arrows=crossing,occupied_nodes=len(occupied),
                          milestone=level%5==0,boss=level in (25,50,100,150,200)).items():
        assert recorded[key]==value,f'Level {level}: stale metric {key}'

    # Full solve: every removal must pass both own-tail and other-arrow checks.
    active=list(pieces);active_nodes=set(occupied)
    while active:
        progress=False
        for piece in active[:]:
            own,pts,dx,dy=piece
            ok,_=self_profile(piece,w,h)
            if not ok:continue
            others=active_nodes-own
            x,y=pts[-1];clear=True
            for _ in range(w+h+30):
                x+=dx;y+=dy
                if x<0 or x>w or y<0 or y>h:break
                if (x,y) in others:
                    clear=False;break
            if clear:
                active_nodes-=own
                active.remove(piece)
                progress=True
        assert progress,f'Level {level}: unsolvable under V17 rule'

# Milestones have only one opening and remain constrained throughout play.
for level in range(5,201,5):
    assert counts[level-1]>=40,f'Level {level}: SUPER HARD level became too sparse'
    assert widths[level-1]<=2,f'Level {level}: milestone frontier too wide'

for level in (25,50,100,150,200):
    assert counts[level-1]>=60,f'Boss level {level}: not enough interlocking arrows'
    assert depths[level-1]>depths[level-2],f'Boss level {level}: shallower than preceding level'

source=(ROOT/'app/src/main/java/com/arrowescape/pro/ArrowGameView.java').read_text()
for name,digest in BASE['movement_methods'].items():
    if name=='isClear':
        continue  # intentionally upgraded in V17
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
    f'V17 verified: 200 solvable levels, zero permanent self-blocks, '
    f'{sum(crossing_counts)} safe self-crossing arrows, arrow counts {min(counts)}–{max(counts)}, '
    f'all 40 milestones have exactly 1 opening and at most 2 choices in ANY solve state; '
    f'and approved V15 snake movement preserved.'
)
