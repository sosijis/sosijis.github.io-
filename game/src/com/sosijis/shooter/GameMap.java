package com.sosijis.shooter;

import java.util.ArrayList;
import java.util.List;

/**
 * Тайловая карта: сетка, спавны, точки закладки, коллизии и трассировка лучей.
 */
public class GameMap {
    public static final int TS = 48;   // размер тайла в пикселях

    public final String name;
    public final String displayName;
    public final int w, h;
    public final Tile[][] tiles;
    public final float[][] damage;      // накопленный урон по разрушаемым тайлам

    public final List<Vec2> spawnT = new ArrayList<>();
    public final List<Vec2> spawnCT = new ArrayList<>();
    public final List<Vec2> hostageSpots = new ArrayList<>();
    public final List<Vec2> siteACells = new ArrayList<>();
    public final List<Vec2> siteBCells = new ArrayList<>();
    public final List<Vec2> rescueCells = new ArrayList<>();
    public final List<Vec2> openCells = new ArrayList<>();   // все проходимые центры тайлов

    public final java.awt.Color ambient;
    public final java.awt.Color skyTint;

    public GameMap(String name, String displayName, String[] rows, java.awt.Color ambient, java.awt.Color skyTint) {
        this.name = name;
        this.displayName = displayName;
        this.ambient = ambient;
        this.skyTint = skyTint;
        this.h = rows.length;
        int maxW = 0;
        for (String r : rows) maxW = Math.max(maxW, r.length());
        this.w = maxW;
        tiles = new Tile[w][h];
        damage = new float[w][h];

        for (int y = 0; y < h; y++) {
            String row = rows[y];
            for (int x = 0; x < w; x++) {
                char c = x < row.length() ? row.charAt(x) : '#';
                tiles[x][y] = parse(c, x, y);
            }
        }
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (!tiles[x][y].solid) openCells.add(new Vec2(x * TS + TS / 2f, y * TS + TS / 2f));
    }

    private Tile parse(char c, int x, int y) {
        Vec2 center = new Vec2(x * TS + TS / 2f, y * TS + TS / 2f);
        switch (c) {
            case '#': return Tile.WALL_CONCRETE;
            case '=': return Tile.WALL_BRICK;
            case 'W': return Tile.WALL_WOOD;
            case 'X': return Tile.CRATE;
            case 'M': return Tile.CRATE_METAL;
            case 'G': return Tile.GLASS;
            case 'F': return Tile.FENCE;
            case 'O': return Tile.BARREL;
            case ',': return Tile.FLOOR_SAND;
            case '"': return Tile.FLOOR_GRASS;
            case '_': return Tile.FLOOR_METAL;
            case 'w': return Tile.FLOOR_WOOD;
            case '~': return Tile.WATER;
            case 'A': siteACells.add(center); return Tile.SITE_A;
            case 'B': siteBCells.add(center); return Tile.SITE_B;
            case 'R': rescueCells.add(center); return Tile.RESCUE;
            case 'h': hostageSpots.add(center); return Tile.FLOOR_CONCRETE;
            case 't': spawnT.add(center); return Tile.BUY_T;
            case 'c': spawnCT.add(center); return Tile.BUY_CT;
            case 'b': return Tile.BUY_T;
            case 'y': return Tile.BUY_CT;
            default: return Tile.FLOOR_CONCRETE;
        }
    }

    public float pixelW() { return w * TS; }
    public float pixelH() { return h * TS; }

    public boolean inBounds(int tx, int ty) { return tx >= 0 && ty >= 0 && tx < w && ty < h; }

    public Tile tileAt(int tx, int ty) {
        if (!inBounds(tx, ty)) return Tile.WALL_CONCRETE;
        return tiles[tx][ty];
    }

    public Tile tileAtWorld(float x, float y) { return tileAt((int) (x / TS), (int) (y / TS)); }

    public boolean solidAtWorld(float x, float y) { return tileAtWorld(x, y).solid; }

    public void setTile(int tx, int ty, Tile t) {
        if (inBounds(tx, ty)) { tiles[tx][ty] = t; damage[tx][ty] = 0; }
    }

    /** Наносит урон разрушаемому тайлу (стекло, ящики, бочки). Возвращает true, если тайл разрушен. */
    public boolean damageTile(int tx, int ty, float dmg) {
        if (!inBounds(tx, ty)) return false;
        Tile t = tiles[tx][ty];
        float hp;
        switch (t) {
            case GLASS -> hp = 10;
            case CRATE -> hp = 180;
            case WALL_WOOD -> hp = 260;
            case BARREL -> hp = 60;
            case FENCE -> hp = 240;
            default -> { return false; }
        }
        damage[tx][ty] += dmg;
        if (damage[tx][ty] >= hp) {
            tiles[tx][ty] = (t == Tile.FENCE || t == Tile.GLASS) ? Tile.FLOOR_CONCRETE : Tile.FLOOR_CONCRETE;
            damage[tx][ty] = 0;
            return true;
        }
        return false;
    }

    // ---------------- Коллизии ----------------

    /** Сдвигает круг радиуса r из pos на (dx,dy) с раздельным разрешением по осям. */
    public void moveCircle(Vec2 pos, float dx, float dy, float r) {
        float nx = pos.x + dx;
        if (!circleHitsSolid(nx, pos.y, r)) pos.x = nx;
        else {
            // подскальзывание к стене
            float step = Math.signum(dx);
            for (int i = 0; i < 6 && step != 0; i++) {
                float test = pos.x + step * (Math.abs(dx) / 6f);
                if (circleHitsSolid(test, pos.y, r)) break;
                pos.x = test;
            }
        }
        float ny = pos.y + dy;
        if (!circleHitsSolid(pos.x, ny, r)) pos.y = ny;
        else {
            float step = Math.signum(dy);
            for (int i = 0; i < 6 && step != 0; i++) {
                float test = pos.y + step * (Math.abs(dy) / 6f);
                if (circleHitsSolid(pos.x, test, r)) break;
                pos.y = test;
            }
        }
        pos.x = MathUtil.clamp(pos.x, r, pixelW() - r);
        pos.y = MathUtil.clamp(pos.y, r, pixelH() - r);
    }

    public boolean circleHitsSolid(float cx, float cy, float r) {
        int x0 = (int) ((cx - r) / TS), x1 = (int) ((cx + r) / TS);
        int y0 = (int) ((cy - r) / TS), y1 = (int) ((cy + r) / TS);
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                if (!tileAt(tx, ty).solid) continue;
                float rx = tx * TS, ry = ty * TS;
                float nearX = MathUtil.clamp(cx, rx, rx + TS);
                float nearY = MathUtil.clamp(cy, ry, ry + TS);
                float ddx = cx - nearX, ddy = cy - nearY;
                if (ddx * ddx + ddy * ddy < r * r) return true;
            }
        }
        return false;
    }

    public boolean isWalkable(int tx, int ty) { return inBounds(tx, ty) && !tileAt(tx, ty).solid; }

    /** Проходим ли тайл с запасом (для ботов, чтобы не тереться об углы). */
    public boolean isWalkableWide(int tx, int ty) {
        if (!isWalkable(tx, ty)) return false;
        return true;
    }

    // ---------------- Трассировка лучей ----------------

    /** Результат трассировки до непрозрачного тайла. */
    public static class RayHit {
        public float dist;
        public float x, y;
        public int tx, ty;
        public boolean hitWall;
    }

    private final RayHit scratch = new RayHit();

    /**
     * DDA-трассировка до первого непрозрачного тайла. Возвращает переиспользуемый объект,
     * так что копируйте значения сразу.
     */
    public RayHit raycastVision(float ox, float oy, float dirX, float dirY, float maxDist) {
        return ddaRay(ox, oy, dirX, dirY, maxDist, true);
    }

    public RayHit raycastSolid(float ox, float oy, float dirX, float dirY, float maxDist) {
        return ddaRay(ox, oy, dirX, dirY, maxDist, false);
    }

    private RayHit ddaRay(float ox, float oy, float dirX, float dirY, float maxDist, boolean vision) {
        RayHit hit = scratch;
        hit.hitWall = false;
        int tx = (int) (ox / TS), ty = (int) (oy / TS);
        float invDx = dirX == 0 ? Float.MAX_VALUE : Math.abs(1f / dirX);
        float invDy = dirY == 0 ? Float.MAX_VALUE : Math.abs(1f / dirY);
        int stepX = dirX < 0 ? -1 : 1, stepY = dirY < 0 ? -1 : 1;
        float sideX = dirX < 0 ? (ox - tx * TS) * invDx : ((tx + 1) * TS - ox) * invDx;
        float sideY = dirY < 0 ? (oy - ty * TS) * invDy : ((ty + 1) * TS - oy) * invDy;
        float dX = TS * invDx, dY = TS * invDy;
        float dist = 0;

        for (int guard = 0; guard < 4096; guard++) {
            if (sideX < sideY) { dist = sideX; sideX += dX; tx += stepX; }
            else { dist = sideY; sideY += dY; ty += stepY; }
            if (dist > maxDist) { dist = maxDist; break; }
            if (!inBounds(tx, ty)) { hit.hitWall = true; break; }
            Tile t = tiles[tx][ty];
            boolean blocked = vision ? t.opaque : t.blocksBullet;
            if (blocked) { hit.hitWall = true; break; }
        }
        hit.dist = Math.min(dist, maxDist);
        hit.x = ox + dirX * hit.dist;
        hit.y = oy + dirY * hit.dist;
        hit.tx = tx; hit.ty = ty;
        return hit;
    }

    /** Есть ли прямая видимость между двумя точками (без учёта дыма). */
    public boolean lineOfSight(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return true;
        dx /= len; dy /= len;
        RayHit h = ddaRay(x0, y0, dx, dy, len, true);
        return !h.hitWall || h.dist >= len - 1f;
    }

    public boolean lineOfSight(Vec2 a, Vec2 b) { return lineOfSight(a.x, a.y, b.x, b.y); }

    /** Свободна ли линия для пуль (стекло и забор не считаются). */
    public boolean bulletPath(Vec2 a, Vec2 b) {
        float dx = b.x - a.x, dy = b.y - a.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return true;
        dx /= len; dy /= len;
        RayHit h = ddaRay(a.x, a.y, dx, dy, len, false);
        return !h.hitWall || h.dist >= len - 1f;
    }

    public Vec2 randomOpenCell() {
        return openCells.get(MathUtil.randInt(openCells.size())).cpy();
    }

    /** Ближайшая проходимая точка к заданной (например, после телепорта в стену). */
    public Vec2 nearestOpen(Vec2 p) {
        if (!solidAtWorld(p.x, p.y)) return p.cpy();
        Vec2 best = null; float bd = Float.MAX_VALUE;
        for (Vec2 c : openCells) {
            float d = c.distSq(p);
            if (d < bd) { bd = d; best = c; }
        }
        return best == null ? new Vec2(TS * 1.5f, TS * 1.5f) : best.cpy();
    }
}
