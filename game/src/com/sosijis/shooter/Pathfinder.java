package com.sosijis.shooter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A* по тайловой сетке с кэшем «стоимости близости к стенам», чтобы боты не тёрлись об углы. */
public class Pathfinder {

    private final GameMap map;
    private final int w, h;
    private final float[] gScore;
    private final int[] cameFrom;
    private final boolean[] closed;
    private final int[] heap;
    private final float[] heapF;
    private final int[] heapPos;
    private int heapSize;
    private final float[] clearanceCost;

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};

    public Pathfinder(GameMap map) {
        this.map = map;
        this.w = map.w; this.h = map.h;
        int n = w * h;
        gScore = new float[n];
        cameFrom = new int[n];
        closed = new boolean[n];
        heap = new int[n + 1];
        heapF = new float[n + 1];
        heapPos = new int[n];
        clearanceCost = new float[n];
        rebuildClearance();
    }

    /** Пересчитать штраф за прижатие к стенам (после разрушений). */
    public void rebuildClearance() {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int solidNeighbours = 0;
                for (int i = 0; i < 8; i++) {
                    if (map.tileAt(x + DX[i], y + DY[i]).solid) solidNeighbours++;
                }
                clearanceCost[y * w + x] = solidNeighbours * 0.45f;
            }
        }
    }

    private int idx(int x, int y) { return y * w + x; }

    public List<Vec2> findPath(Vec2 from, Vec2 to) {
        int sx = MathUtil.clamp((int) (from.x / GameMap.TS), 0, w - 1);
        int sy = MathUtil.clamp((int) (from.y / GameMap.TS), 0, h - 1);
        int gx = MathUtil.clamp((int) (to.x / GameMap.TS), 0, w - 1);
        int gy = MathUtil.clamp((int) (to.y / GameMap.TS), 0, h - 1);
        if (!map.isWalkable(gx, gy)) {
            int[] near = nearestWalkable(gx, gy);
            if (near == null) return new ArrayList<>();
            gx = near[0]; gy = near[1];
        }
        if (!map.isWalkable(sx, sy)) {
            int[] near = nearestWalkable(sx, sy);
            if (near == null) return new ArrayList<>();
            sx = near[0]; sy = near[1];
        }
        return astar(sx, sy, gx, gy);
    }

    private int[] nearestWalkable(int x, int y) {
        for (int r = 1; r < 12; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (map.isWalkable(x + dx, y + dy)) return new int[]{x + dx, y + dy};
                }
            }
        }
        return null;
    }

    private List<Vec2> astar(int sx, int sy, int gx, int gy) {
        Arrays.fill(gScore, Float.MAX_VALUE);
        Arrays.fill(closed, false);
        Arrays.fill(cameFrom, -1);
        Arrays.fill(heapPos, -1);
        heapSize = 0;

        int start = idx(sx, sy), goal = idx(gx, gy);
        gScore[start] = 0;
        push(start, heuristic(sx, sy, gx, gy));

        int guard = 0;
        while (heapSize > 0 && guard++ < 60000) {
            int cur = pop();
            if (cur == goal) return reconstruct(cur, sx, sy);
            closed[cur] = true;
            int cx = cur % w, cy = cur / w;
            for (int i = 0; i < 8; i++) {
                int nx = cx + DX[i], ny = cy + DY[i];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                if (!map.isWalkable(nx, ny)) continue;
                if (i >= 4) { // диагональ — только если оба ортогональных свободны
                    if (!map.isWalkable(cx + DX[i], cy) || !map.isWalkable(cx, cy + DY[i])) continue;
                }
                int ni = idx(nx, ny);
                if (closed[ni]) continue;
                float step = i >= 4 ? 1.414f : 1f;
                Tile t = map.tileAt(nx, ny);
                if (t.mat == Tile.Mat.WATER) step *= 1.8f;
                float tentative = gScore[cur] + step + clearanceCost[ni];
                if (tentative < gScore[ni]) {
                    gScore[ni] = tentative;
                    cameFrom[ni] = cur;
                    push(ni, tentative + heuristic(nx, ny, gx, gy));
                }
            }
        }
        return new ArrayList<>();
    }

    private float heuristic(int x, int y, int gx, int gy) {
        float dx = Math.abs(x - gx), dy = Math.abs(y - gy);
        return (dx + dy) + (1.414f - 2f) * Math.min(dx, dy);
    }

    private List<Vec2> reconstruct(int goal, int sx, int sy) {
        List<Vec2> out = new ArrayList<>();
        int cur = goal;
        int guard = 0;
        while (cur != -1 && guard++ < 6000) {
            int cx = cur % w, cy = cur / w;
            out.add(0, new Vec2(cx * GameMap.TS + GameMap.TS / 2f, cy * GameMap.TS + GameMap.TS / 2f));
            if (cx == sx && cy == sy) break;
            cur = cameFrom[cur];
        }
        if (!out.isEmpty()) out.remove(0);
        return smooth(out);
    }

    /** Убираем лишние точки: если между i и i+2 есть прямая видимость, средняя не нужна. */
    private List<Vec2> smooth(List<Vec2> path) {
        if (path.size() < 3) return path;
        List<Vec2> out = new ArrayList<>();
        int i = 0;
        while (i < path.size()) {
            out.add(path.get(i));
            int j = path.size() - 1;
            for (; j > i + 1; j--) {
                if (clearLine(path.get(i), path.get(j))) break;
            }
            if (j > i + 1) i = j; else i++;
        }
        return out;
    }

    private boolean clearLine(Vec2 a, Vec2 b) {
        float dist = a.dist(b);
        int steps = (int) (dist / 10f) + 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t;
            if (map.circleHitsSolid(x, y, 16f)) return false;
        }
        return true;
    }

    // --------- Двоичная куча ---------
    private void push(int node, float f) {
        int p = heapPos[node];
        if (p != -1) {
            if (heapF[p] <= f) return;
            heapF[p] = f;
            siftUp(p);
            return;
        }
        heapSize++;
        heap[heapSize] = node;
        heapF[heapSize] = f;
        heapPos[node] = heapSize;
        siftUp(heapSize);
    }

    private int pop() {
        int top = heap[1];
        heapPos[top] = -1;
        heap[1] = heap[heapSize];
        heapF[1] = heapF[heapSize];
        if (heapSize > 1) heapPos[heap[1]] = 1;
        heapSize--;
        siftDown(1);
        return top;
    }

    private void siftUp(int i) {
        while (i > 1) {
            int p = i >> 1;
            if (heapF[p] <= heapF[i]) break;
            swap(i, p);
            i = p;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int l = i << 1, r = l + 1, m = i;
            if (l <= heapSize && heapF[l] < heapF[m]) m = l;
            if (r <= heapSize && heapF[r] < heapF[m]) m = r;
            if (m == i) break;
            swap(i, m);
            i = m;
        }
    }

    private void swap(int a, int b) {
        int na = heap[a], nb = heap[b];
        float fa = heapF[a];
        heap[a] = nb; heap[b] = na;
        heapF[a] = heapF[b]; heapF[b] = fa;
        heapPos[nb] = a; heapPos[na] = b;
    }
}
