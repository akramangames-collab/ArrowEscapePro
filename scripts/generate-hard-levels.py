#!/usr/bin/env python3
"""Build V17 mazes in solve order with a bounded dependency frontier.

New arrows aim through earlier arrows, while their bodies may not cross any
earlier escape ray. Dependencies therefore only point backwards: cycles are
impossible, and only the original roots can be opening moves. Extending the
leaves of this forest with at most one fork also constrains later moves.
Precomputed shapes and ray occupancy replace the failed reverse-chain search.
No difficulty threshold is relaxed when an attempt fails.
"""
import argparse
import json
import random
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEVELS = ROOT / 'app/src/main/assets/levels.txt'
DIRS = ((1, 0), (-1, 0), (0, 1), (0, -1))
BOSSES = (25, 50, 100, 150, 200)


def expand(pts):
    out = [pts[0]]
    for (x, y), (u, v) in zip(pts, pts[1:]):
        assert (x == u) != (y == v)
        dx, dy = (u > x) - (u < x), (v > y) - (v < y)
        out.extend((x + dx*k, y + dy*k)
                   for k in range(1, abs(u-x) + abs(v-y) + 1))
    assert len(out) == len(set(out)), 'self-intersecting starting geometry'
    return out


def ray(piece, w, h):
    dx, dy, pts, _ = piece
    x, y = pts[-1]
    out = []
    x, y = x+dx, y+dy
    while 0 <= x <= w and 0 <= y <= h:
        out.append((x, y))
        x, y = x+dx, y+dy
    return out


def self_profile(piece, w, h):
    distance = {q: i for i, q in enumerate(expand(piece[2]))}
    crossings = 0
    for step, q in enumerate(ray(piece, w, h), 1):
        if q in distance:
            if distance[q] >= step:
                return False, crossings
            crossings += 1
    return True, crossings


def templates():
    shapes = []
    for a in (1, 2, 3):
        for b in (-3, -2, 2, 3):
            shapes.append([(-a, b), (-a, 0), (0, 0)])
            for c in (1, 2):
                shapes.append([(-a-c, b), (-a, b), (-a, 0), (0, 0)])
        # The hook crossing is at tail-distance zero: it clears before the
        # advancing head arrives 3/4 cells later.
        for b in (-2, 2):
            for c in (3, 4):
                shapes.append([(c, 0), (c, b), (-a, b), (-a, 0), (0, 0)])
    return {(dx, dy): [
        ([(x*dx-y*dy, x*dy+y*dx) for x, y in pts],
         [(x*dx-y*dy, x*dy+y*dx) for x, y in expand(pts)])
        for pts in shapes] for dx, dy in DIRS}


TEMPLATES = templates()


def opening_target(level):
    return 1 if level % 5 == 0 else 3 if level == 1 else 2


def arrow_target(level):
    return 24 + round((level-1)*0.24) + (16 if level % 5 == 0 else 0) + (16 if level in BOSSES else 0)


def attempt(level, w, h, seed):
    rng = random.Random(seed)
    pieces, occupied, reserved = [], set(), set()
    roots = opening_target(level)
    for i in range(roots):
        x = (i+1)*w//(roots+1)
        pts = [(x+2, 4), (x, 4), (x, 2)]
        piece = (0, -1, pts, set(expand(pts)))
        pieces.append(piece)
        occupied.update(piece[3])
        reserved.update(ray(piece, w, h))
    leaves, forks = set(range(roots)), 0
    while len(pieces) < arrow_target(level):
        forbidden = occupied | reserved
        parents = sorted(leaves, reverse=True)
        if forks < 1:
            parents += sorted(set(range(len(pieces))) - leaves, reverse=True)
        choices = []
        for parent in parents:
            heads = set()
            for x, y in pieces[parent][3]:
                for dx, dy in DIRS:
                    for k in (1, 2, 3):
                        q = x-dx*k, y-dy*k
                        if 0 <= q[0] <= w and 0 <= q[1] <= h and q not in forbidden:
                            heads.add((q, dx, dy))
            # Sort before shuffling, independent of Python hash iteration order.
            heads = sorted(heads)
            rng.shuffle(heads)
            for (x, y), dx, dy in heads:
                shapes = list(TEMPLATES[dx, dy])
                rng.shuffle(shapes)
                for pts, nodes in shapes[:18]:
                    translated = {(x+u, y+v) for u, v in nodes}
                    if translated & forbidden or any(not (0 <= u <= w and 0 <= v <= h) for u, v in translated):
                        continue
                    piece = dx, dy, [(x+u, y+v) for u, v in pts], translated
                    escape = set(ray(piece, w, h))
                    unavailable = forbidden | translated | escape
                    frontier = sum((u+sx, v+sy) not in unavailable
                                   and 0 <= u+sx <= w and 0 <= v+sy <= h
                                   for u, v in translated for sx, sy in DIRS)
                    score = frontier + len(translated)*0.3 - len(escape-forbidden)*0.5 + rng.random()*6
                    choices.append((score, piece, escape))
                    break
                if len(choices) >= 16:
                    break
            if choices:
                break
        if not choices:
            return None
        _, piece, escape = max(choices, key=lambda choice: choice[0])
        if parent not in leaves:
            forks += 1
        leaves.discard(parent)
        leaves.add(len(pieces))
        pieces.append(piece)
        occupied.update(piece[3])
        reserved.update(escape)
    return pieces


def summarize(level, w, h, pieces):
    owner = {q: i for i, piece in enumerate(pieces) for q in piece[3]}
    deps = [{owner[q] for q in ray(p, w, h) if q in owner and owner[q] != i}
            for i, p in enumerate(pieces)]
    pending, depth = set(range(len(pieces))), {}
    while pending:
        ready = [i for i in sorted(pending) if not deps[i] & pending]
        assert ready, f'Level {level}: dependency cycle'
        for i in ready:
            depth[i] = 1 + max((depth[j] for j in deps[i]), default=0)
        pending.difference_update(ready)
    return dict(level=level, arrows=len(pieces), grid=[w, h],
                initial_safe_arrows=sum(not d for d in deps),
                dependency_depth=max(depth.values()),
                self_crossing_arrows=sum(self_profile(p, w, h)[1] > 0 for p in pieces),
                occupied_nodes=len(owner), milestone=level % 5 == 0, boss=level in BOSSES)


def build_level(level):
    fraction = (level-1)/199
    w = 24 + round(fraction*24) + (4 if level % 5 == 0 else 0) + (6 if level in BOSSES else 0)
    h = w + 8
    for restart in range(160):
        pieces = attempt(level, w, h, 17010000 + level*1009 + restart*7919)
        if pieces is None:
            continue
        # Remove unused margins, then mirror to vary the opening location.
        all_nodes = set().union(*(p[3] for p in pieces))
        minx, miny = min(x for x, y in all_nodes)-1, min(y for x, y in all_nodes)-1
        cw, ch = max(x for x, y in all_nodes)-minx+1, max(y for x, y in all_nodes)-miny+1
        transformed = []
        for dx, dy, pts, _ in pieces:
            pts = [(x-minx, y-miny) for x, y in pts]
            if level % 2:
                pts = [(cw-x, y) for x, y in pts]
                dx = -dx
            if level % 4 < 2:
                pts = [(x, ch-y) for x, y in pts]
                dy = -dy
            transformed.append((dx, dy, pts, set(expand(pts))))
        summary = summarize(level, cw, ch, transformed)
        if summary['dependency_depth'] < len(pieces)*(0.75 if level % 5 == 0 else 0.50):
            continue
        if summary['self_crossing_arrows'] < (2 if level % 5 == 0 else 1):
            continue
        assert summary['initial_safe_arrows'] == opening_target(level)
        assert all(self_profile(p, cw, ch)[0] for p in transformed)
        random.Random(level*1777).shuffle(transformed)
        summary['generation_attempts'] = restart+1
        return cw, ch, transformed, summary
    raise AssertionError(f'Level {level}: exhausted bounded generation; difficulty unchanged')


def pack(w, h, pieces):
    return f'{w},{h}|' + ';'.join(','.join(map(str, [dx, dy] + [v for xy in pts for v in xy]))
                                for dx, dy, pts, _ in pieces)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true', help='Regenerate and compare committed assets')
    args = parser.parse_args()
    started = time.monotonic()
    output, metrics = [], []
    for level in range(1, 201):
        w, h, pieces, summary = build_level(level)
        output.append(pack(w, h, pieces))
        metrics.append(summary)
        if level <= 5 or level % 25 == 0:
            print(summary, flush=True)
    assets = {LEVELS: '\n'.join(output)+'\n',
              ROOT/'tests/hard-level-metrics.json': json.dumps(metrics, indent=2)+'\n'}
    for path, content in assets.items():
        if args.check:
            assert path.read_text() == content, f'Generated asset drift: {path}'
        else:
            path.write_text(content)
    print(f'Generated 200 constrained V17 mazes in {time.monotonic()-started:.1f}s; '
          'all milestones/bosses have exactly one opening.', flush=True)


if __name__ == '__main__':
    main()
