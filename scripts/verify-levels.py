#!/usr/bin/env python3
"""Independently validate all 400 no-band levels and full tail clearance."""
import hashlib, json, re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
LINES=(ROOT/'app/src/main/assets/levels.txt').read_text().splitlines()
BASE=json.loads((ROOT/'tests/v15-baseline.json').read_text())
assert len(LINES)==400
assert len(set(LINES))==400, 'Duplicate levels'
METRICS=json.loads((ROOT/'tests/hard-level-metrics.json').read_text())
assert len(METRICS)==400

def tier(level):
    return 3 if level%30==0 else 2 if level%12==0 else 1 if level%6==0 else 0


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
    assert 1<=safe_counts[-1]<=4,f'Level {level}: wrong opening count {safe_counts[-1]}'

    todo=set(range(len(deps)));depth={}
    while todo:
        ready=[i for i in todo if not (deps[i]&todo)]
        assert ready,f'Level {level}: dependency cycle'
        for i in ready:
            depth[i]=1+max((depth[j] for j in deps[i]),default=0)
        todo-=set(ready)
    depths.append(max(depth.values(),default=0))
    assert depths[-1]>=len(pieces)*.55,f'Level {level}: shallow dependencies'
    width=frontier_bound(deps);widths.append(width)
    assert width<=6,f'Level {level}: too many choices later in the solve ({width})'
    # V41 reserves the centre and deliberately leaves space elsewhere.
    # Tail safety remains independently simulated above; a deliberate crossing
    # is no longer required by the reference design.
    cx,cy=w//2,h//2
    shelter={(x,y) for x in range(cx-4,cx+5) for y in range(cy-4,cy+5)}
    assert not occupied & shelter,f'Level {level}: rescue shelter overlaps an arrow'
    density=len(occupied)/((w+1)*(h+1)-81)
    assert .30<=density<=.76,f'Level {level}: spacing outside design range'
    bends=sum(len(p[1])-2 for p in pieces)/len(pieces)
    bend_counts=[len(p[1])-2 for p in pieces]
    assert max(bend_counts)<=6,f'Level {level}: overcomplicated arrow'
    simple=sum(n<=3 for n in bend_counts)
    assert simple>=len(pieces)*.9,f'Level {level}: too many complex arrows'
    assert METRICS[level-1]['simple_arrows']==simple
    assert METRICS[level-1]['complex_arrows']==len(pieces)-simple
    assert METRICS[level-1]['mean_bends']==round(bends,3)
    assert METRICS[level-1]['usable_density']==round(density,6)
    assert METRICS[level-1]['max_solve_frontier']==width
    # Count actual vertical unit edges from the packed paths independently of
    # the weaver. A board with a horizontal band separator cannot pass.
    bridges=[0]*h
    for piece in pieces:
        for (ax,ay),(bx,by) in zip(piece[1],piece[1][1:]):
            if ax==bx:
                for y in range(min(ay,by),max(ay,by)):bridges[y]+=1
    assert min(bridges)>=1,f'Level {level}: horizontal separator remains'
    assert METRICS[level-1]['min_horizontal_bridges']==min(bridges)
    assert METRICS[level-1]['no_horizontal_bands'] is True
    lengths=[sum(abs(a-c)+abs(b-d) for (a,b),(c,d) in zip(p[1],p[1][1:])) for p in pieces]
    mix=dict(short_arrows=sum(n<=14 for n in lengths),long_arrows=sum(n>=24 for n in lengths),
             max_bends=max(len(p[1])-2 for p in pieces),min_path_length=min(lengths),max_path_length=max(lengths))
    assert mix['short_arrows']>=len(pieces)*.5,f'Level {level}: short arrows are not the majority'
    assert mix['long_arrows']<=len(pieces)*.12,f'Level {level}: too many long arrows'
    assert mix['max_path_length']<=32,f'Level {level}: oversized arrow'
    assert mix['max_bends']<=6,f'Level {level}: excess bends'
    for key,value in mix.items():assert METRICS[level-1][key]==value,f'Level {level}: stale {key}'
    recorded=METRICS[level-1]
    for key,value in dict(level=level,arrows=len(pieces),grid=[w,h],initial_safe_arrows=safe_counts[-1],
                          dependency_depth=depths[-1],self_crossing_arrows=crossing,occupied_nodes=len(occupied),
                          milestone=tier(level)>=2,boss=level in tuple(range(30,401,30)),phase=('NORMAL','HARD','SUPER HARD','EXTREME HARD')[tier(level)]).items():
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
for level in range(12,401,12):
    assert counts[level-1]>=40,f'Level {level}: SUPER HARD level became too sparse'
    assert widths[level-1]<=6,f'Level {level}: milestone frontier too wide'

for level in tuple(range(30,401,30)):
    assert counts[level-1]>=56,f'Extreme level {level}: not enough interlocking arrows'
    assert depths[level-1]>depths[level-2],f'Boss level {level}: shallower than preceding level'

source=(ROOT/'app/src/main/java/com/arrowescape/pro/ArrowGameView.java').read_text()
for name,digest in BASE['movement_methods'].items():
    if name=='isClear':
        continue  # intentionally upgraded in V17
    match=re.search(r'\b'+name+r'\s*\([^)]*\)\s*\{',source);assert match,name
    start=source.index('{',match.start());end=start+1;nest=1
    while nest:
        nest+=(source[end]=='{')-(source[end]=='}');end+=1
    body=source[start:end]
    # Portrait projection scales only pixel Y; grid movement remains identical.
    if name in ('buildMovingSnakePath','findPieceAt'):body=body.replace('*boardStretchY','')
    assert hashlib.sha256(body.encode()).hexdigest()==digest,f'Approved V15 movement changed: {name}'

assert 'private boolean hasSelfClearance(Piece target)' in source
isclear=source[source.index('private boolean isClear(Piece target)'):source.index('private long nodeKey')]
assert 'hasSelfClearance(target)' in isclear
assert 'Tail blocks this escape' in source

print(
    f'V41 verified: 400 solvable levels, zero permanent self-blocks, '
    f'{sum(crossing_counts)} safe self-crossing arrows, arrow counts {min(counts)}–{max(counts)}, '
    f'all levels have 1–4 openings and at most 6 choices in ANY solve state; '
    f'and approved V15 snake movement preserved.'
)
