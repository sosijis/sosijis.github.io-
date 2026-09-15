package com.sosijis.shooter;

import java.util.ArrayList;
import java.util.List;

/**
 * Дымовая завеса. Расширяется, полностью перекрывает линию обзора и тушит огонь.
 * Состоит из нескольких «пузырей» — так она обтекает стены, а не висит идеальным кругом.
 */
public class SmokeCloud {
    public final Vec2 origin = new Vec2();
    public final List<Vec2> puffs = new ArrayList<>();
    public final List<Float> puffR = new ArrayList<>();
    public float radius;
    public float targetRadius = 156f;
    public float life = 17f;
    public float age;
    public final Actor owner;
    public float density;   // 0..1, влияет и на прозрачность, и на блокировку обзора

    public SmokeCloud(Vec2 pos, Actor owner) {
        origin.set(pos);
        this.owner = owner;
        int n = 9;
        for (int i = 0; i < n; i++) {
            float a = MathUtil.TAU * i / n + MathUtil.rand(0.4f);
            float d = i == 0 ? 0 : MathUtil.rand(24f, 74f);
            puffs.add(Vec2.fromAngle(a, d).add(origin));
            puffR.add(MathUtil.rand(52f, 84f));
        }
    }

    public void update(float dt, Game game) {
        age += dt;
        life -= dt;
        float grow = MathUtil.clamp(age / 1.35f, 0, 1);
        radius = targetRadius * grow;
        density = MathUtil.clamp(grow, 0, 1) * MathUtil.clamp(life / 3.2f, 0, 1);

        // Гасим огонь внутри облака
        for (FireArea f : game.fires) {
            if (f.pos.dist(origin) < radius + f.radius * 0.5f) f.douse(dt * 2.4f);
        }
        for (Actor a : game.actors) {
            if (a.alive && a.burn > 0 && contains(a.pos)) a.burn = Math.max(0, a.burn - dt * 3f);
        }
    }

    public boolean expired() { return life <= 0; }

    public boolean contains(Vec2 p) {
        return blocksPoint(p.x, p.y);
    }

    public boolean blocksPoint(float x, float y) {
        if (density < 0.15f) return false;
        for (int i = 0; i < puffs.size(); i++) {
            Vec2 c = puffs.get(i);
            float r = puffR.get(i) * (radius / targetRadius);
            float dx = x - c.x, dy = y - c.y;
            if (dx * dx + dy * dy < r * r) return true;
        }
        return false;
    }

    /** Пересекает ли отрезок непрозрачную часть облака. */
    public boolean blocksSegment(float x0, float y0, float x1, float y1) {
        if (density < 0.2f) return false;
        for (int i = 0; i < puffs.size(); i++) {
            Vec2 c = puffs.get(i);
            float r = puffR.get(i) * (radius / targetRadius) * 0.86f;
            if (MathUtil.distPointSegment(c.x, c.y, x0, y0, x1, y1) < r) return true;
        }
        return false;
    }
}
