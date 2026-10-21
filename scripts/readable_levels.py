"""Simplify path interiors while preserving their heads and escape dependencies."""
import random
from weave_levels import expand,piece_of,compress
from spaced_levels import valid,inspect

def bends(walk):return len(compress(walk))-2

def line(a,b):
    if a==b:return [a]
    if a[0]!=b[0] and a[1]!=b[1]:return None
    dx=(b[0]>a[0])-(b[0]<a[0]);dy=(b[1]>a[1])-(b[1]<a[1])
    return [(a[0]+k*dx,a[1]+k*dy) for k in range(abs(b[0]-a[0])+abs(b[1]-a[1])+1)]

def candidates(p,forbidden):
    original=bends(p);seen=set();result=[]
    for i in range(len(p)-2):
        # Keep the final head edge fixed.
        for j in range(i+2,len(p)-1):
            a,b=p[i],p[j]
            corners=[(a[0],b[1]),(b[0],a[1])]
            for corner in corners:
                mid=line(a,corner)+line(corner,b)[1:]
                trial=p[:i]+mid+p[j+1:]
                if len(trial)>len(p) or len(set(trial))!=len(trial):continue
                if set(mid)&forbidden:continue
                count=bends(trial)
                if count>=original:continue
                key=tuple(trial)
                if key in seen:continue
                seen.add(key);result.append((count,-len(trial),trial))
    result.sort(key=lambda r:r[:2])
    return [r[2] for r in result]

def readable(w,h,seed,level,attempt=0):
    walks=[expand(p[2]) for p in seed]
    cx,cy=w//2,h//2
    shelter={(x,y) for x in range(cx-4,cx+5) for y in range(cy-4,cy+5)}
    exceptions=set(sorted((i for i,p in enumerate(walks) if bends(p)>3),
                          key=lambda i:len(walks[i]),reverse=True)[:2])
    rng=random.Random(level*1009+attempt*1000003)
    for _ in range(12):
        changed=False
        order=list(range(len(walks)))
        if attempt:rng.shuffle(order)
        else:order.sort(key=lambda j:bends(walks[j]),reverse=True)
        for i in order:
            limit=6 if i in exceptions else 3
            if bends(walks[i])<=limit:continue
            forbidden=shelter|{q for j,p in enumerate(walks) if j!=i for q in p}
            options=candidates(walks[i],forbidden)
            options.sort(key=lambda p:(bends(p)>limit,-len(p),bends(p)))
            for candidate in options:
                trial=walks.copy();trial[i]=candidate
                if valid(w,h,trial):walks=trial;changed=True;break
        if not changed:break
    counts=[bends(p) for p in walks]
    # Replace a rare stubborn body by independently directed short pieces.
    # The removed node between them prevents a joined-looking pseudo-arrow.
    for _ in range(6):
        bad=[i for i,p in enumerate(walks) if bends(p)>6]
        if not bad:break
        changed=False
        for i in bad:
            p=walks[i]
            for k in range(2,len(p)-3):
                prefix,suffix=p[:k],p[k+1:]
                if max(bends(prefix),bends(suffix))>=bends(p):continue
                for a in (prefix,list(reversed(prefix))):
                    for b in (suffix,list(reversed(suffix))):
                        trial=walks.copy();trial[i]=b;trial.append(a)
                        if valid(w,h,trial):
                            walks=trial;changed=True;break
                    if changed:break
                if changed:break
            if changed:break
        if not changed:break
    counts=[bends(p) for p in walks]
    # If a tail still forces extra turns, shorten it while retaining its head.
    # Losing a blocker is permitted only when the same difficulty gates pass.
    for i in sorted(range(len(walks)),key=lambda j:bends(walks[j]),reverse=True):
        limit=6 if i in exceptions else 3
        if bends(walks[i])<=limit:continue
        p=walks[i]
        for k in range(1,len(p)-2):
            candidate=p[k:]
            if bends(candidate)>limit:continue
            trial=walks.copy();trial[i]=candidate
            if valid(w,h,trial):walks=trial;break
    counts=[bends(p) for p in walks]
    if (max(counts)>6 or sum(n<=3 for n in counts)<len(counts)*.9) and attempt<12:
        return readable(w,h,seed,level,attempt+1)
    return [piece_of(p) for p in walks],counts
