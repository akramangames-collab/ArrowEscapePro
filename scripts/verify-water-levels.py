#!/usr/bin/env python3
"""Independent moving-tail simulation and exact unique-sequence verification."""
from pathlib import Path
import json,hashlib
from dense_campaign import water_seconds
ROOT=Path(__file__).resolve().parents[1];lines=(ROOT/'app/src/main/assets/levels.txt').read_text().splitlines();metrics=json.loads((ROOT/'tests/water-level-metrics.json').read_text());assert len(lines)==len(set(lines))==len(metrics)==1000
canonical=set();total=0
for level,(line,m) in enumerate(zip(lines,metrics),1):
    header,body=line.split('|');w,h=map(int,header.split(','));pieces=[];owner={}
    for i,raw in enumerate(body.split(';')):
        v=list(map(int,raw.split(',')));dx,dy=v[:2];pts=list(zip(v[2::2],v[3::2]));assert (dx,dy) in [(1,0),(-1,0),(0,1),(0,-1)];walk=[pts[0]]
        for a,b in zip(pts,pts[1:]):
            assert (a[0]==b[0])!=(a[1]==b[1]);n=abs(b[0]-a[0])+abs(b[1]-a[1]);sx=(b[0]>a[0])-(b[0]<a[0]);sy=(b[1]>a[1])-(b[1]<a[1]);walk.extend((a[0]+sx*k,a[1]+sy*k) for k in range(1,n+1))
        assert (walk[-1][0]-walk[-2][0],walk[-1][1]-walk[-2][1])==(dx,dy);assert len(walk)==len(set(walk));assert 1<=len(walk)-1<=71
        for x,y in walk:
            assert 0<=x<=w and 0<=y<=h;assert not(abs(x-w//2)<=4 and abs(y-h//2)<=4);assert (x,y) not in owner;owner[x,y]=i
        moving=walk.copy();x,y=walk[-1];forward=[]
        while 0<=x<=w and 0<=y<=h:
            x+=dx;y+=dy;moving.pop(0);assert (x,y) not in moving;moving.append((x,y))
            if 0<=x<=w and 0<=y<=h:forward.append((x,y))
        pieces.append((walk,forward))
    signature=(w,h,tuple(sorted(tuple(p[0]) for p in pieces)));assert signature not in canonical;canonical.add(signature)
    deps=[{owner[q] for q in ray if q in owner and owner[q]!=i} for i,(_,ray) in enumerate(pieces)];pending=set(range(len(pieces)));order=[]
    while pending:
        safe=[i for i in pending if not(deps[i]&pending)];assert len(safe)==1,(level,safe);pending.remove(safe[0]);order+=safe
    assert list(pieces[order[-1]][0][-1])==m['gold_key_head'];assert m['arrows']==len(pieces) and m['sha256']==hashlib.sha256(line.encode()).hexdigest()
    assert m['chapter']==(level-1)//40+1 and m['water_seconds']==water_seconds(level,len(pieces))
    assert m['occupied_cells']==len(owner) and m['playable_cells']==(w+1)*(h+1)-81
    assert abs(m['density']-len(owner)/m['playable_cells'])<.0001
    cx,cy=w//2,h//2;qs=list(owner);assert all([any(x<cx-4 for x,y in qs),any(x>cx+4 for x,y in qs),any(y<cy-4 for x,y in qs),any(y>cy+4 for x,y in qs)])
    if level<=5:assert len(pieces)==level+2 and m['water_seconds']==120
    else:
        assert len(pieces)>=24 and m['water_seconds']>len(pieces)*1.45+2.25
        # Density alone rewards overly long tails. Require a real length mix instead.
        lengths=[len(p[0])-1 for p in pieces]
        assert any(n<=8 for n in lengths), (level,'missing short arrows')
        assert any(8<n<=18 for n in lengths), (level,'missing medium arrows')
        assert any(n>18 for n in lengths), (level,'missing long arrows')
    total+=len(pieces)
print(f'PASS: 1000 distinct layouts; {total} unique-sequence moves; gold key last; self-tail clearance; four-sided cage shelter.')
