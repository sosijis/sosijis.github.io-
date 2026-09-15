package com.sosijis.shooter;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Частицы, следы пуль, декали и всплывающие цифры урона. */
public class Fx {

    public enum PType { SPARK, BLOOD, SMOKE, FIRE, DEBRIS, SHELL, WATER, GLASS, DUST }

    public static class Particle {
        public PType type;
        public float x, y, vx, vy, z, vz;
        public float life, maxLife, size, rot, rotSpd;
        public Color color;
        public boolean active;
    }

    public static class Tracer {
        public float x0, y0, x1, y1, life, maxLife, width;
        public Color color;
    }

    public static class Decal {
        public float x, y, size, rot, alpha;
        public Color color;
        public int type;   // 0 — кровь, 1 — след пули, 2 — копоть
    }

    public static class Popup {
        public float x, y, life, vy;
        public String text;
        public Color color;
        public float size;
    }

    private final Particle[] pool;
    private int cursor;
    public final List<Tracer> tracers = new ArrayList<>();
    public final List<Decal> decals = new ArrayList<>();
    public final List<Popup> popups = new ArrayList<>();
    private final Settings settings;

    public Fx(Settings settings) {
        this.settings = settings;
        pool = new Particle[4096];
        for (int i = 0; i < pool.length; i++) pool[i] = new Particle();
    }

    public Particle[] particles() { return pool; }

    private Particle alloc() {
        for (int i = 0; i < pool.length; i++) {
            cursor = (cursor + 1) % pool.length;
            if (!pool[cursor].active) return pool[cursor];
        }
        return pool[cursor];
    }

    private int scale(int n) {
        return Math.max(1, Math.round(n * settings.particleScale));
    }

    public void clear() {
        for (Particle p : pool) p.active = false;
        tracers.clear();
        decals.clear();
        popups.clear();
    }

    public void update(float dt) {
        for (Particle p : pool) {
            if (!p.active) continue;
            p.life -= dt;
            if (p.life <= 0) { p.active = false; continue; }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.rotSpd * dt;
            switch (p.type) {
                case SMOKE -> { p.vx *= 1f - dt * 1.1f; p.vy *= 1f - dt * 1.1f; p.size += dt * 26f; }
                case FIRE -> { p.vy -= dt * 26f; p.vx *= 1f - dt * 1.6f; p.size -= dt * 8f; }
                case SPARK -> { p.vx *= 1f - dt * 3.2f; p.vy *= 1f - dt * 3.2f; }
                case BLOOD, DEBRIS, GLASS -> { p.vx *= 1f - dt * 2.2f; p.vy *= 1f - dt * 2.2f; }
                case SHELL -> {
                    p.vz -= dt * 700f;
                    p.z += p.vz * dt;
                    if (p.z < 0) { p.z = 0; p.vz = -p.vz * 0.3f; p.vx *= 0.6f; p.vy *= 0.6f; p.rotSpd *= 0.5f; }
                    p.vx *= 1f - dt * 1.2f; p.vy *= 1f - dt * 1.2f;
                }
                case WATER -> { p.vz -= dt * 900f; p.z += p.vz * dt; if (p.z < 0) p.life = 0; }
                case DUST -> { p.size += dt * 14f; p.vx *= 1f - dt * 1.4f; p.vy *= 1f - dt * 1.4f; }
            }
        }
        for (int i = tracers.size() - 1; i >= 0; i--) {
            Tracer t = tracers.get(i);
            t.life -= dt;
            if (t.life <= 0) tracers.remove(i);
        }
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.get(i);
            p.life -= dt;
            p.y += p.vy * dt;
            p.vy *= 1f - dt * 1.4f;
            if (p.life <= 0) popups.remove(i);
        }
        while (decals.size() > settings.maxDecals) decals.remove(0);
    }

    private Particle spawn(PType type, float x, float y, float vx, float vy, float life, float size, Color c) {
        Particle p = alloc();
        p.active = true;
        p.type = type;
        p.x = x; p.y = y; p.vx = vx; p.vy = vy;
        p.z = 0; p.vz = 0;
        p.life = p.maxLife = life;
        p.size = size;
        p.color = c;
        p.rot = MathUtil.rand(MathUtil.TAU);
        p.rotSpd = MathUtil.rand(-6f, 6f);
        return p;
    }

    // ------------------------------------------------------------- Эффекты

    public void muzzle(float x, float y, float angle, float power) {
        int n = scale((int) (4 + power * 5));
        for (int i = 0; i < n; i++) {
            float a = angle + MathUtil.gauss(0.22f);
            float sp = MathUtil.rand(160f, 460f) * power;
            spawn(PType.SPARK, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    MathUtil.rand(0.05f, 0.14f), MathUtil.rand(1.6f, 3.4f), new Color(0xFFD98A));
        }
        for (int i = 0; i < scale(2); i++) {
            float a = angle + MathUtil.gauss(0.5f);
            spawn(PType.SMOKE, x, y, (float) Math.cos(a) * 60f, (float) Math.sin(a) * 60f,
                    MathUtil.rand(0.35f, 0.7f), MathUtil.rand(5f, 10f), new Color(220, 210, 200));
        }
    }

    public void shell(float x, float y, float angle) {
        float a = angle + MathUtil.PI * 0.5f + MathUtil.gauss(0.3f);
        Particle p = spawn(PType.SHELL, x, y, (float) Math.cos(a) * MathUtil.rand(70f, 150f),
                (float) Math.sin(a) * MathUtil.rand(70f, 150f), 6f, 3.2f, new Color(0xC8A030));
        p.z = 14f;
        p.vz = MathUtil.rand(40f, 110f);
        p.rotSpd = MathUtil.rand(-22f, 22f);
    }

    public void impact(float x, float y, float angle, Tile.Mat mat) {
        Color c = switch (mat) {
            case METAL -> new Color(0xFFE08A);
            case WOOD -> new Color(0xB98A4F);
            case GLASS -> new Color(0xBFE8FF);
            case SAND -> new Color(0xC9B189);
            case GRASS -> new Color(0x8FBF6E);
            case WATER -> new Color(0x9FD0E8);
            default -> new Color(0xCFCFCF);
        };
        int n = scale(mat == Tile.Mat.GLASS ? 12 : 7);
        for (int i = 0; i < n; i++) {
            float a = angle + MathUtil.gauss(0.8f);
            float sp = MathUtil.rand(60f, 280f);
            PType t = mat == Tile.Mat.GLASS ? PType.GLASS : (mat == Tile.Mat.METAL ? PType.SPARK : PType.DEBRIS);
            spawn(t, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    MathUtil.rand(0.15f, 0.5f), MathUtil.rand(1.4f, 3f), c);
        }
        for (int i = 0; i < scale(2); i++) {
            spawn(PType.DUST, x, y, MathUtil.gauss(30f), MathUtil.gauss(30f),
                    MathUtil.rand(0.3f, 0.75f), MathUtil.rand(4f, 9f), new Color(200, 195, 185));
        }
        if (settings.bulletHoles && mat != Tile.Mat.WATER) addDecal(x, y, MathUtil.rand(3f, 5f), new Color(20, 18, 16), 1);
    }

    public void blood(float x, float y, float angle, float amount) {
        int n = scale((int) (5 + amount * 9));
        for (int i = 0; i < n; i++) {
            float a = angle + MathUtil.gauss(0.75f);
            float sp = MathUtil.rand(70f, 330f) * (0.5f + amount);
            spawn(PType.BLOOD, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    MathUtil.rand(0.25f, 0.6f), MathUtil.rand(1.8f, 4.2f), new Color(0x9E1B1B));
        }
        if (settings.bloodDecals && MathUtil.chance(0.75f)) {
            float d = MathUtil.rand(6f, 26f);
            addDecal(x + (float) Math.cos(angle) * d, y + (float) Math.sin(angle) * d,
                    MathUtil.rand(7f, 15f) * (0.6f + amount), new Color(0x7A1414), 0);
        }
    }

    public void gib(float x, float y) {
        for (int i = 0; i < scale(26); i++) {
            float a = MathUtil.rand(MathUtil.TAU);
            float sp = MathUtil.rand(60f, 420f);
            spawn(PType.BLOOD, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    MathUtil.rand(0.3f, 0.9f), MathUtil.rand(2f, 6f), new Color(0x8E1616));
        }
        if (settings.bloodDecals)
            for (int i = 0; i < 4; i++)
                addDecal(x + MathUtil.gauss(18f), y + MathUtil.gauss(18f), MathUtil.rand(12f, 26f), new Color(0x6E1010), 0);
    }

    public void explosion(float x, float y, float power) {
        for (int i = 0; i < scale((int) (28 * power)); i++) {
            float a = MathUtil.rand(MathUtil.TAU);
            float sp = MathUtil.rand(120f, 620f) * power;
            spawn(PType.SPARK, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    MathUtil.rand(0.15f, 0.5f), MathUtil.rand(2f, 5f), new Color(0xFFC451));
        }
        for (int i = 0; i < scale((int) (16 * power)); i++) {
            float a = MathUtil.rand(MathUtil.TAU);
            float sp = MathUtil.rand(40f, 220f) * power;
            spawn(PType.SMOKE, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    MathUtil.rand(0.8f, 2.0f), MathUtil.rand(10f, 24f), new Color(90, 85, 80));
        }
        for (int i = 0; i < scale((int) (10 * power)); i++) {
            float a = MathUtil.rand(MathUtil.TAU);
            spawn(PType.FIRE, x, y, (float) Math.cos(a) * MathUtil.rand(30f, 180f),
                    (float) Math.sin(a) * MathUtil.rand(30f, 180f),
                    MathUtil.rand(0.2f, 0.45f), MathUtil.rand(8f, 18f), new Color(0xFF8A30));
        }
        addDecal(x, y, 34f * power, new Color(18, 16, 14), 2);
    }

    public void flashPop(float x, float y) {
        for (int i = 0; i < scale(30); i++) {
            float a = MathUtil.rand(MathUtil.TAU);
            float sp = MathUtil.rand(200f, 700f);
            spawn(PType.SPARK, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    MathUtil.rand(0.1f, 0.3f), MathUtil.rand(2f, 4f), new Color(0xFFFFFF));
        }
        for (int i = 0; i < scale(6); i++) {
            spawn(PType.SMOKE, x, y, MathUtil.gauss(70f), MathUtil.gauss(70f),
                    MathUtil.rand(0.5f, 1.2f), MathUtil.rand(8f, 16f), new Color(235, 235, 240));
        }
    }

    public void smokePuff(float x, float y, float life, float size) {
        spawn(PType.SMOKE, x, y, MathUtil.gauss(18f), MathUtil.gauss(18f), life, size * 10f, new Color(190, 190, 190));
    }

    public void fire(float x, float y, float intensity) {
        Color c = MathUtil.chance(0.5f) ? new Color(0xFF9A2E) : new Color(0xFFD24A);
        spawn(PType.FIRE, x, y, MathUtil.gauss(22f), -MathUtil.rand(20f, 70f),
                MathUtil.rand(0.25f, 0.6f), MathUtil.rand(5f, 12f) * (0.5f + intensity), c);
        if (MathUtil.chance(0.25f))
            spawn(PType.SMOKE, x, y, MathUtil.gauss(14f), -MathUtil.rand(15f, 45f),
                    MathUtil.rand(0.7f, 1.6f), MathUtil.rand(6f, 13f), new Color(60, 55, 52));
    }

    public void spark(float x, float y, float life) {
        spawn(PType.SPARK, x, y, MathUtil.gauss(25f), MathUtil.gauss(25f), life, 1.8f, new Color(0xFFE08A));
    }

    public void splash(float x, float y) {
        for (int i = 0; i < scale(6); i++) {
            float a = MathUtil.rand(MathUtil.TAU);
            Particle p = spawn(PType.WATER, x, y, (float) Math.cos(a) * MathUtil.rand(30f, 110f),
                    (float) Math.sin(a) * MathUtil.rand(30f, 110f), 0.6f, MathUtil.rand(1.5f, 3f), new Color(0xA8D8F0));
            p.vz = MathUtil.rand(60f, 160f);
        }
    }

    public void tracer(float x0, float y0, float x1, float y1, Color c, float width) {
        Tracer t = new Tracer();
        t.x0 = x0; t.y0 = y0; t.x1 = x1; t.y1 = y1;
        t.color = c;
        t.width = width;
        t.life = t.maxLife = 0.07f;
        tracers.add(t);
    }

    public void addDecal(float x, float y, float size, Color c, int type) {
        if (type == 0 && !settings.bloodDecals) return;
        if (type == 1 && !settings.bulletHoles) return;
        Decal d = new Decal();
        d.x = x; d.y = y; d.size = size; d.color = c; d.type = type;
        d.rot = MathUtil.rand(MathUtil.TAU);
        d.alpha = MathUtil.rand(0.55f, 0.9f);
        decals.add(d);
        while (decals.size() > settings.maxDecals) decals.remove(0);
    }

    public void popup(float x, float y, String text, Color c, float size) {
        if (!settings.showDamageNumbers) return;
        Popup p = new Popup();
        p.x = x + MathUtil.gauss(6f); p.y = y;
        p.text = text; p.color = c; p.size = size;
        p.life = 0.85f;
        p.vy = -46f;
        popups.add(p);
    }
}
