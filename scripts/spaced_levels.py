"""Shorten interwoven coverage seeds into deliberately spaced puzzles.

Only remove tails, or restore small fragments of removed tails. Original heads
remain in place. Every accepted edit retains a solvable dependency graph and
timed own-tail clearance; no arrow may exceed 32 grid steps.
"""
import random
from weave_levels import expand, piece_of, ray, graph_depth, frontier_bound, horizontal_bridges, self_profile

def inspect(w, h, walks):
    pieces = [piece_of(p) for p in walks]
    owner = {q:i for i,p in enumerate(pieces) for q in p[3]}
    deps = [{owner[q] for q in ray(p,w,h) if q in owner and owner[q]!=i}
            for i,p in enumerate(pieces)]
    depth, roots = graph_depth(deps)
    return pieces, deps, depth, roots, horizontal_bridges(walks,h)

def valid(w, h, walks, bridges=True):
    pieces,deps,depth,roots,crossings=inspect(w,h,walks)
    if not depth or roots>4 or depth<len(walks)*.55:
        return False
    if bridges and min(crossings)<1:
        return False
    if not all(self_profile(p,w,h) for p in pieces):
        return False
    return frontier_bound(deps)<=6

def spaced(w,h,seed,level):
    rng=random.Random(400000+level*1009)
    original=[expand(p[2]) for p in seed]
    walks=[p[-33:] for p in original]
    # A rare cap removes the only cross-row link. Restore a small, separately
    # directed fragment, never the oversized original body.
    for _ in range(h):
        bridges=horizontal_bridges(walks,h)
        if min(bridges)>=1:break
        row=bridges.index(0)
        occupied={q for p in walks for q in p}
        repaired=False
        for old in original:
            for k,(a,b) in enumerate(zip(old,old[1:])):
                if a[0]!=b[0] or min(a[1],b[1])!=row:continue
                lo=k;hi=k+2
                if a in occupied or b in occupied:continue
                while lo>0 and k-lo<3 and old[lo-1] not in occupied:lo-=1
                while hi<len(old) and hi-k<6 and old[hi] not in occupied:hi+=1
                part=old[lo:hi]
                for variant in (part,list(reversed(part))):
                    trial=walks+[variant]
                    if valid(w,h,trial,bridges=False):
                        walks=trial;repaired=True;break
                if repaired:break
            if repaired:break
        if not repaired:raise ValueError(f'Cannot restore row connection {row}')
    if not valid(w,h,walks):raise ValueError('Capped seed fails dependency gates')
    usable=(w+1)*(h+1)-81
    target=rng.uniform(.64,.71)
    # Reduce each tail in small steps so local interlocks survive. Prefer long
    # bodies; a few medium-long arrows remain, rather than filling empty cells.
    for _ in range(12):
        changed=False
        order=list(range(len(walks)));rng.shuffle(order)
        order.sort(key=lambda i:len(walks[i]),reverse=True)
        for i in order:
            if sum(map(len,walks))/usable<=target:break
            p=walks[i]
            if len(p)<=5:continue
            cut=min(len(p)-4,rng.randint(2,5))
            trial=walks.copy();trial[i]=p[cut:]
            if valid(w,h,trial):walks=trial;changed=True
        if not changed or sum(map(len,walks))/usable<=target:break
    # Keep medium-long bodies a small minority. When their removed tail was
    # an important blocker, turn it into a separate short arrow with a gap.
    for i in sorted(range(len(walks)),key=lambda i:len(walks[i])):
        if sum(len(p)>24 for p in walks)<=max(2,int(len(walks)*.12)):break
        p=walks[i]
        if len(p)<=24:continue
        cut=len(p)-24
        trial=walks.copy();trial[i]=p[cut:]
        if valid(w,h,trial):walks=trial;continue
        # Leave one unoccupied node between the two independent bodies.
        prefix=p[:cut-1]
        if len(prefix)<2:continue
        for part in (prefix,list(reversed(prefix))):
            if valid(w,h,trial+[part]):walks=trial+[part];break
    if not valid(w,h,walks):raise ValueError('Final spaced layout fails gates')
    pieces,deps,depth,roots,bridges=inspect(w,h,walks)
    lengths=[len(p)-1 for p in walks]
    return pieces,dict(usable_density=round(sum(map(len,walks))/usable,6),
        initial_safe_arrows=roots,dependency_depth=depth,max_solve_frontier=frontier_bound(deps),
        min_horizontal_bridges=min(bridges),no_horizontal_bands=True,
        short_arrows=sum(n<=14 for n in lengths),long_arrows=sum(n>=24 for n in lengths),
        max_bends=max(len(p[2])-2 for p in pieces),
        mean_bends=round(sum(len(p[2])-2 for p in pieces)/len(pieces),3),
        min_path_length=min(lengths),max_path_length=max(lengths))
