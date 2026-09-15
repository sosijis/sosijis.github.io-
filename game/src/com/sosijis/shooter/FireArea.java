package com.sosijis.shooter;

import java.util.ArrayList;
import java.util.List;

/** Зона огня от молотова: несколько очагов, урон по времени, тушится дымом. */
public class FireArea {
    public final Vec2 pos = new Vec2();
    public final Actor owner;
    public float life = 7.5f;
    public float radius = 26f;
    public float intensity = 1f;
    public float age;
    private float tick;
    private float crackle;

    public static class Patch {
        public final Vec2 p = new Vec2();
        public float r, phase, health = 1f;
    }

    public final List<Patch> patches = new ArrayList<>();

    public FireArea(Vec2 p, Actor owner) {
        pos.set(p);
        this.owner = owner;
    }

    public void addPatch(float x, float y, float r) {
        Patch pa = new Patch();
        pa.p.set(x, y);
        pa.r = r;
        pa.phase = MathUtil.rand(MathUtil.TAU);
        patches.add(pa);
        radius = Math.max(radius, pos.dist(pa.p) + r);
    }

    public void update(float dt, Game game) {
        age += dt;
        life -= dt;
        intensity = MathUtil.clamp(Math.min(age * 3f, life / 1.6f), 0, 1);

        tick -= dt;
        if (tick <= 0) {
            tick = 0.25f;
            for (Actor a : game.actors) {
                if (!a.alive) continue;
                if (covers(a.pos)) {
                    a.ignite(2.6f, owner);
                    game.damage(a, owner, 7f * intensity, a.pos.cpy(), false, DamageType.FIRE, null);
                }
            }
        }

        crackle -= dt;
        if (crackle <= 0 && intensity > 0.2f) {
            crackle = MathUtil.rand(0.25f, 0.6f);
            game.sfx.playAt("fire", pos, 0.20f * intensity, MathUtil.rand(0.8f, 1.3f));
        }

        int spawn = (int) (patches.size() * 1.4f * game.settings.particleScale);
        for (int i = 0; i < spawn; i++) {
            if (!MathUtil.chance(dt * 9f)) continue;
            Patch pa = patches.get(MathUtil.randInt(patches.size()));
            if (pa.health <= 0) continue;
            float a = MathUtil.rand(MathUtil.TAU), d = MathUtil.rand(pa.r);
            game.fx.fire(pa.p.x + (float) Math.cos(a) * d, pa.p.y + (float) Math.sin(a) * d, intensity);
        }
    }

    public void douse(float amount) {
        life -= amount * 2.2f;
        for (Patch p : patches) p.health -= amount * 0.5f;
    }

    public boolean covers(Vec2 p) {
        if (intensity <= 0.05f) return false;
        for (Patch pa : patches) {
            if (pa.health <= 0) continue;
            if (pa.p.distSq(p) < pa.r * pa.r) return true;
        }
        return false;
    }

    public boolean expired() { return life <= 0; }
}
