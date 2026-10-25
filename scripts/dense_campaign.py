"""Grow legal arrow tails into empty grid cells while retaining one forced escape order."""
import random

def water_seconds(level, arrows):
    if level <= 5: return 120
    rate = 210 if level <= 40 else 190 if level <= 200 else 170 if level <= 600 else 155
    bonus = 9 if level <= 200 else 10
    seconds = (arrows * rate + 50) // 100 + bonus
    if level % 40 == 0: seconds = (seconds * 90 + 50) // 100
    return seconds

def density_target(level):
    return .64 if level <= 40 else .70 if level <= 200 else .77 if level <= 600 else .82

def _ray(w,h,path):
    x,y=path[-1];dx,dy=x-path[-2][0],y-path[-2][1]
    result=[];x+=dx;y+=dy
    while 0<=x<=w and 0<=y<=h:
        result.append((x,y));x+=dx;y+=dy
    return result

def unique_order(w,h,walks):
    owner={q:i for i,p in enumerate(walks) for q in p}
    rays=[_ray(w,h,p) for p in walks]
    deps=[{owner[q] for q in ray if q in owner and owner[q]!=i} for i,ray in enumerate(rays)]
    pending=set(range(len(walks)));order=[]
    while pending:
        ready=[i for i in pending if not (deps[i]&pending)]
        if len(ready)!=1: raise ValueError('Multiple legal escapes or physical deadlock')
        order.append(ready[0]);pending.remove(ready[0])
    return order,rays

def densify(w,h,walks,level):
    """Tail-only growth never enters cage, older exit rays, or occupied cells."""
    if level<=5: return 0
    rng=random.Random(63127+level*100003)
    sequence,rays=unique_order(w,h,walks)
    ray_min={}
    for rank,i in enumerate(sequence):
        for q in rays[i]: ray_min[q]=min(rank,ray_min.get(q,rank))
    positions={i:rank for rank,i in enumerate(sequence)}
    original_lengths=[len(p) for p in walks]
    headroom={}
    for i,p in enumerate(walks):
        old={q:j for j,q in enumerate(p)}
        headroom[i]=min((step-old[q]-1 for step,q in enumerate(rays[i],1) if q in old),default=100000)
    occupied={q:i for i,p in enumerate(walks) for q in p}
    playable=(w+1)*(h+1)-81
    wanted=int(density_target(level)*playable)
    dirs=((1,0),(-1,0),(0,1),(0,-1))
    def shelter(q):
        return abs(q[0]-w//2)<=4 and abs(q[1]-h//2)<=4
    for _ in range(90):
        if len(occupied)>=wanted: break
        indices=list(range(len(walks)));rng.shuffle(indices)
        grown=0
        for i in indices:
            p=walks[i]
            if len(p)>=72 or len(occupied)>=wanted or len(p)-original_lengths[i]>=headroom[i]: continue
            x,y=p[0];choices=[]
            for dx,dy in dirs:
                q=(x+dx,y+dy)
                if not(0<=q[0]<=w and 0<=q[1]<=h): continue
                if q in occupied or shelter(q) or ray_min.get(q,9999)<=positions[i]: continue
                onward=sum(0<=q[0]+vx<=w and 0<=q[1]+vy<=h
                    and (q[0]+vx,q[1]+vy) not in occupied
                    and not shelter((q[0]+vx,q[1]+vy))
                    and ray_min.get((q[0]+vx,q[1]+vy),9999)>positions[i]
                    for vx,vy in dirs)
                bend=(q[0]-x,q[1]-y)!=(x-p[1][0],y-p[1][1])
                score=(3 if onward>=2 else 1 if onward==1 else -2)+rng.random()*.9+(.12 if bend else 0)
                choices.append((score,q))
            if choices:
                q=max(choices)[1];p.insert(0,q);occupied[q]=i;grown+=1
        if not grown: break
    assert len(occupied)==sum(map(len,walks))
    unique_order(w,h,walks)
    return round(len(occupied)/playable,4)
