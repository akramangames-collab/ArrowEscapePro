"""Global path splicing removes every horizontal separator in the seed.

Cut opposite directed edges in a grid square, swap tails, and try both endpoint
orientations. Every accepted proposal preserves complete node coverage and
passes self-tail clearance, dependency depth, one-root and exact frontier gates.
"""
import random

def expand(pts):
    out = [pts[0]]
    for (x, y), (u, v) in zip(pts, pts[1:]):
        dx, dy = ((u > x) - (u < x), (v > y) - (v < y))
        out.extend(((x + dx * k, y + dy * k) for k in range(1, abs(u - x) + abs(v - y) + 1)))
    return out

def compress(nodes):
    out = [nodes[0]]
    for i in range(1, len(nodes) - 1):
        a, b, c = nodes[i - 1:i + 2]
        if (b[0] - a[0], b[1] - a[1]) != (c[0] - b[0], c[1] - b[1]):
            out.append(b)
    out.append(nodes[-1])
    return out

def ray(piece, w, h):
    dx, dy, pts, _ = piece
    x, y = pts[-1]
    out = []
    x, y = (x + dx, y + dy)
    while 0 <= x <= w and 0 <= y <= h:
        out.append((x, y))
        x, y = (x + dx, y + dy)
    return out

def self_profile(piece, w, h):
    distance = {q: i for i, q in enumerate(expand(piece[2]))}
    return all((q not in distance or distance[q] < step for step, q in enumerate(ray(piece, w, h), 1)))

def horizontal_bridges(walks, h):
    bridges = [0] * h
    for walk in walks:
        for a, b in zip(walk, walk[1:]):
            if a[0] == b[0]:
                bridges[min(a[1], b[1])] += 1
    return bridges

def graph_depth(deps):
    n = len(deps)
    followers = [[] for _ in range(n)]
    counts = [len(d) for d in deps]
    depth = [1] * n
    for i, ds in enumerate(deps):
        for j in ds:
            followers[j].append(i)
    todo = [i for i, c in enumerate(counts) if not c]
    opening = len(todo)
    visited = 0
    while todo:
        j = todo.pop()
        visited += 1
        for i in followers[j]:
            depth[i] = max(depth[i], depth[j] + 1)
            counts[i] -= 1
            if counts[i] == 0:
                todo.append(i)
    return (max(depth), opening) if visited == n else (0, 0)

def frontier_bound(deps):
    reachable = {}

    def visit(i):
        if i not in reachable:
            reachable[i] = set()
            for j in deps[i]:
                reachable[i].add(j)
                reachable[i].update(visit(j))
        return reachable[i]
    for i in range(len(deps)):
        visit(i)
    matched = {}

    def augment(i, seen):
        for j in sorted(reachable[i]):
            if j in seen:
                continue
            seen.add(j)
            if j not in matched or augment(matched[j], seen):
                matched[j] = i
                return True
        return False
    return len(deps) - sum((augment(i, set()) for i in range(len(deps))))

def piece_of(walk):
    a, b = walk[-2:]
    dx = b[0] - a[0]
    dy = b[1] - a[1]
    return (dx, dy, compress(walk), set(walk))

def weave(w, h, pieces, rng, min_depth=0.8, iterations=2500):
    walks = [expand(p[2]) for p in pieces]
    accepted = 0
    joins = 0
    cached = None
    for step in range(iterations):
        crossings = [0] * h
        for walk in walks:
            for a, b in zip(walk, walk[1:]):
                if a[0] == b[0]:
                    crossings[min(a[1], b[1])] += 1
        if min(crossings) >= 4 and accepted >= len(pieces):
            break
        if cached is None:
            edges = {(a, b): (i, k) for i, walk in enumerate(walks) for k, (a, b) in enumerate(zip(walk, walk[1:]))}
            candidates = []
            for (a, b), (i, k) in edges.items():
                dx, dy = (b[0] - a[0], b[1] - a[1])
                for nx, ny in ((-dy, dx), (dy, -dx)):
                    c, d = ((a[0] + nx, a[1] + ny), (b[0] + nx, b[1] + ny))
                    for same, key in ((False, (d, c)), (True, (c, d))):
                        match = edges.get(key)
                        if match and i != match[0]:
                            score = 100 - crossings[min(a[1], c[1])] if ny else 0
                            candidates.append((score, i, k, *match, same))
            cached = candidates
        candidates = cached
        if not candidates:
            break
        best = max((c[0] for c in candidates))
        chosen = rng.choice([c for c in candidates if c[0] >= best - 1] if step % 4 else candidates)
        cached.remove(chosen)
        _, i, k, j, l, same = chosen
        wa, wb = (walks[i], walks[j])
        if same:
            aa = list(reversed(wa[k + 1:])) + wb[l + 1:]
            bb = wa[:k + 1] + list(reversed(wb[:l + 1]))
        else:
            aa = wb[:l + 1] + wa[k + 1:]
            bb = wa[:k + 1] + wb[l + 1:]
        if min(len(aa), len(bb)) < 4 or max(len(aa), len(bb)) > 180:
            continue
        variants = [(aa, bb), (aa, list(reversed(bb))), (list(reversed(aa)), bb), (list(reversed(aa)), list(reversed(bb)))]
        rng.shuffle(variants)
        for pa, pb in variants:
            newa, newb = (piece_of(pa), piece_of(pb))
            if not (self_profile(newa, w, h) and self_profile(newb, w, h)):
                continue
            trial = pieces.copy()
            trial[i], trial[j] = (newa, newb)
            own = {q: ix for ix, p in enumerate(trial) for q in p[3]}
            deps = [{own[q] for q in ray(p, w, h) if q in own and own[q] != ix} for ix, p in enumerate(trial)]
            depth, roots = graph_depth(deps)
            if roots != 1 or depth < len(trial) * min_depth:
                continue
            if frontier_bound(deps) > 3:
                continue
            pieces = trial
            walks[i], walks[j] = (pa, pb)
            accepted += 1
            cached = None
            if chosen[0] > 90:
                joins += 1
            break
    crossings = horizontal_bridges(walks, h)
    if min(crossings) < 4:
        raise ValueError('A horizontal separator remains')
    return (pieces, dict(min_horizontal_bridges=min(crossings), path_splices=accepted))
