package com.sosijis.shooter;

import java.util.Random;

/** Математические хелперы и общий генератор случайных чисел. */
public final class MathUtil {
    public static final Random RNG = new Random();
    public static final float PI = (float) Math.PI;
    public static final float TAU = PI * 2f;

    private MathUtil() {}

    public static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
    public static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Кадро-независимое сглаживание: rate — доля, остающаяся за секунду. */
    public static float damp(float a, float b, float rate, float dt) {
        return lerp(a, b, 1f - (float) Math.pow(rate, dt));
    }

    public static float rand(float a, float b) { return a + RNG.nextFloat() * (b - a); }
    public static float rand(float m) { return RNG.nextFloat() * m; }
    public static int randInt(int n) { return RNG.nextInt(Math.max(1, n)); }
    public static boolean chance(float p) { return RNG.nextFloat() < p; }
    public static float gauss(float sigma) { return (float) RNG.nextGaussian() * sigma; }

    /** Нормализует угол в диапазон (-PI, PI]. */
    public static float wrapAngle(float a) {
        while (a > PI) a -= TAU;
        while (a <= -PI) a += TAU;
        return a;
    }

    /** Кратчайшая угловая разница from -> to. */
    public static float angleDiff(float from, float to) { return wrapAngle(to - from); }

    /** Поворот угла к цели не более чем на maxStep. */
    public static float approachAngle(float cur, float target, float maxStep) {
        float d = angleDiff(cur, target);
        if (Math.abs(d) <= maxStep) return wrapAngle(target);
        return wrapAngle(cur + Math.signum(d) * maxStep);
    }

    public static float smoothstep(float e0, float e1, float x) {
        float t = clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Кратчайшее расстояние от точки p до отрезка a-b. */
    public static float distPointSegment(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay;
        float wx = px - ax, wy = py - ay;
        float len2 = vx * vx + vy * vy;
        float t = len2 < 1e-6f ? 0f : clamp((wx * vx + wy * vy) / len2, 0f, 1f);
        float dx = px - (ax + vx * t), dy = py - (ay + vy * t);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
