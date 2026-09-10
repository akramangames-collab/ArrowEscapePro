#!/usr/bin/env python3
"""Generate a dedicated offline Daily Challenge pack.

These puzzles are NOT copied from levels.txt and never participate in the normal
1-200 progression.  The constructive dependency graph guarantees solvability;
each challenge has exactly one opening move and full self-tail clearance.
"""
import argparse
import json
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/daily_challenges.txt"
METRICS = ROOT / "tests/daily-challenge-metrics.json"
COUNT = 366
DIRS = ((1, 0), (-1, 0), (0, 1), (0, -1))


def expand(pts):
    out = [pts[0]]
    for (x, y), (u, v) in zip(pts, pts[1:]):
        assert (x == u) != (y == v)
        dx, dy = (u > x) - (u < x), (v > y) - (v < y)
        out.extend((x + dx*k, y + dy*k) for k in range(1, abs(u-x) + abs(v-y) + 1))
    assert len(out) == len(set(out)), "self-intersecting starting geometry"
    return out


def ray(piece, w, h):
    dx, dy, pts, _ = piece
    x, y = pts[-1]
    out = []
    x, y = x + dx, y + dy
    while 0 <= x <= w and 0 <= y <= h:
        out.append((x, y))
        x, y = x + dx, y + dy
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
        for b in (-2, 2):
            for c in (3, 4):
                shapes.append([(c, 0), (c, b), (-a, b), (-a, 0), (0, 0)])
    return {(dx, dy): [
        ([(x*dx-y*dy, x*dy+y*dx) for x, y in pts],
         [(x*dx-y*dy, x*dy+y*dx) for x, y in expand(pts)])
        for pts in shapes] for dx, dy in DIRS}


TEMPLATES = templates()


def attempt(w, h, target, seed):
    rng = random.Random(seed)
    pieces, occupied, reserved = [], set(), set()
    x = rng.randint(max(4, w//4), min(w-4, 3*w//4))
    pts = [(x+2, 4), (x, 4), (x, 2)]
    root = (0, -1, pts, set(expand(pts)))
    pieces.append(root)
    occupied.update(root[3])
    reserved.update(ray(root, w, h))
    leaves, forks = {0}, 0

    while len(pieces) < target:
        forbidden = occupied | reserved
        parents = sorted(leaves, reverse=True)
        if forks < 2:
            parents += sorted(set(range(len(pieces))) - leaves, reverse=True)
        choices = []
        for parent in parents:
            heads = set()
            for x, y in pieces[parent][3]:
                for dx, dy in DIRS:
                    for k in (1, 2, 3):
                        q = (x-dx*k, y-dy*k)
                        if 0 <= q[0] <= w and 0 <= q[1] <= h and q not in forbidden:
                            heads.add((q, dx, dy))
            heads = sorted(heads)
            rng.shuffle(heads)
            for (x, y), dx, dy in heads:
                shapes = list(TEMPLATES[dx, dy])
                rng.shuffle(shapes)
                for pts, nodes in shapes[:20]:
                    translated = {(x+u, y+v) for u, v in nodes}
                    if translated & forbidden or any(not (0 <= u <= w and 0 <= v <= h) for u, v in translated):
                        continue
                    piece = (dx, dy, [(x+u, y+v) for u, v in pts], translated)
                    escape = set(ray(piece, w, h))
                    unavailable = forbidden | translated | escape
                    frontier = sum((u+sx, v+sy) not in unavailable and 0 <= u+sx <= w and 0 <= v+sy <= h
                                   for u, v in translated for sx, sy in DIRS)
                    score = frontier + len(translated)*0.35 - len(escape-forbidden)*0.55 + rng.random()*6
                    choices.append((score, piece, escape, parent))
                    break
                if len(choices) >= 18:
                    break
            if choices:
                break
        if not choices:
            return None
        _, piece, escape, parent = max(choices, key=lambda c: c[0])
        if parent not in leaves:
            forks += 1
        leaves.discard(parent)
        leaves.add(len(pieces))
        pieces.append(piece)
        occupied.update(piece[3])
        reserved.update(escape)
    return pieces


def trim_and_mirror(pieces, variant):
    all_nodes = set().union(*(p[3] for p in pieces))
    minx = min(x for x, y in all_nodes) - 1
    miny = min(y for x, y in all_nodes) - 1
    cw = max(x for x, y in all_nodes) - minx + 1
    ch = max(y for x, y in all_nodes) - miny + 1
    out = []
    for dx, dy, pts, _ in pieces:
        pts = [(x-minx, y-miny) for x, y in pts]
        if variant & 1:
            pts = [(cw-x, y) for x, y in pts]
            dx = -dx
        if variant & 2:
            pts = [(x, ch-y) for x, y in pts]
            dy = -dy
        out.append((dx, dy, pts, set(expand(pts))))
    return cw, ch, out


def summarize(w, h, pieces):
    owner = {q: i for i, p in enumerate(pieces) for q in p[3]}
    deps = [{owner[q] for q in ray(p, w, h) if q in owner and owner[q] != i}
            for i, p in enumerate(pieces)]
    pending, depth = set(range(len(pieces))), {}
    while pending:
        ready = [i for i in sorted(pending) if not deps[i] & pending]
        assert ready, "dependency cycle"
        for i in ready:
            depth[i] = 1 + max((depth[j] for j in deps[i]), default=0)
        pending.difference_update(ready)
    return {
        "arrows": len(pieces),
        "grid": [w, h],
        "initial_safe_arrows": sum(not d for d in deps),
        "dependency_depth": max(depth.values()),
        "self_crossing_arrows": sum(self_profile(p, w, h)[1] > 0 for p in pieces),
    }


def pack(w, h, pieces):
    return f"{w},{h}|" + ";".join(",".join(map(str, [dx, dy] + [v for xy in pts for v in xy]))
                                  for dx, dy, pts, _ in pieces)


def build(challenge):
    w = 42 + (challenge % 4) * 2
    h = w + 8
    target = 62 + (challenge % 5) * 2
    for restart in range(200):
        pieces = attempt(w, h, target, 930000000 + challenge*10007 + restart*7919)
        if pieces is None:
            continue
        cw, ch, pieces = trim_and_mirror(pieces, challenge % 4)
        summary = summarize(cw, ch, pieces)
        if summary["initial_safe_arrows"] != 1:
            continue
        if summary["dependency_depth"] < int(target * 0.72):
            continue
        if summary["self_crossing_arrows"] < 2:
            continue
        if not all(self_profile(p, cw, ch)[0] for p in pieces):
            continue
        random.Random(770000 + challenge*1777).shuffle(pieces)
        summary["challenge"] = challenge
        summary["generation_attempts"] = restart + 1
        return pack(cw, ch, pieces), summary
    raise AssertionError(f"Daily Challenge {challenge}: generation exhausted")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    lines, metrics = [], []
    for challenge in range(1, COUNT + 1):
        line, summary = build(challenge)
        lines.append(line)
        metrics.append(summary)
    assert len(lines) == COUNT and len(set(lines)) == COUNT

    normal = {line.strip() for line in (ROOT / "app/src/main/assets/levels.txt").read_text().splitlines() if line.strip()}
    assert not (set(lines) & normal), "Daily pack must never reuse a normal level"

    outputs = {
        OUT: "\n".join(lines) + "\n",
        METRICS: json.dumps(metrics, indent=2) + "\n",
    }
    for path, content in outputs.items():
        if args.check:
            assert path.read_text() == content, f"Generated asset drift: {path}"
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
    print(f"Verified {COUNT} unique dedicated Daily Challenges; zero overlap with the 200 normal levels.")


if __name__ == "__main__":
    main()
