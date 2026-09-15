package com.sosijis.shooter;

/** Простой изменяемый 2D-вектор. Игра активно создаёт вектора, поэтому здесь минимум аллокаций. */
public final class Vec2 {
    public float x, y;

    public Vec2() { this(0, 0); }
    public Vec2(float x, float y) { this.x = x; this.y = y; }
    public Vec2(Vec2 o) { this.x = o.x; this.y = o.y; }

    public static Vec2 fromAngle(float a) { return new Vec2((float) Math.cos(a), (float) Math.sin(a)); }
    public static Vec2 fromAngle(float a, float len) { return new Vec2((float) Math.cos(a) * len, (float) Math.sin(a) * len); }

    public Vec2 set(float x, float y) { this.x = x; this.y = y; return this; }
    public Vec2 set(Vec2 o) { this.x = o.x; this.y = o.y; return this; }
    public Vec2 add(Vec2 o) { x += o.x; y += o.y; return this; }
    public Vec2 add(float dx, float dy) { x += dx; y += dy; return this; }
    public Vec2 sub(Vec2 o) { x -= o.x; y -= o.y; return this; }
    public Vec2 mul(float s) { x *= s; y *= s; return this; }
    public Vec2 addScaled(Vec2 o, float s) { x += o.x * s; y += o.y * s; return this; }

    public float len() { return (float) Math.sqrt(x * x + y * y); }
    public float lenSq() { return x * x + y * y; }
    public float dot(Vec2 o) { return x * o.x + y * o.y; }
    public float angle() { return (float) Math.atan2(y, x); }

    public Vec2 norm() {
        float l = len();
        if (l > 1e-6f) { x /= l; y /= l; }
        return this;
    }

    public Vec2 limit(float max) {
        float l2 = lenSq();
        if (l2 > max * max && l2 > 1e-9f) {
            float l = (float) Math.sqrt(l2);
            x = x / l * max; y = y / l * max;
        }
        return this;
    }

    public Vec2 rotate(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float nx = x * c - y * s, ny = x * s + y * c;
        x = nx; y = ny; return this;
    }

    public Vec2 cpy() { return new Vec2(x, y); }

    public float dist(Vec2 o) { float dx = x - o.x, dy = y - o.y; return (float) Math.sqrt(dx * dx + dy * dy); }
    public float dist(float ox, float oy) { float dx = x - ox, dy = y - oy; return (float) Math.sqrt(dx * dx + dy * dy); }
    public float distSq(Vec2 o) { float dx = x - o.x, dy = y - o.y; return dx * dx + dy * dy; }

    @Override public String toString() { return String.format("(%.1f, %.1f)", x, y); }
}
