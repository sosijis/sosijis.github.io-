package com.sosijis.shooter;

/** Заложенная C4: таймер, ускоряющиеся сигналы, разминирование. */
public class Bomb {
    public final Vec2 pos = new Vec2();
    public boolean planted, defused, exploded;
    public float timer = 40f;
    public Actor planter, defuser;
    public float defuseProgress;
    public float defuseTime = 10f;
    private float beep;
    public float lastBeepFlash;

    public void plant(Vec2 p, Actor by) {
        pos.set(p);
        planted = true;
        planter = by;
        timer = 40f;
    }

    public void update(float dt, Game game) {
        if (!planted || defused || exploded) return;
        timer -= dt;
        lastBeepFlash = Math.max(0, lastBeepFlash - dt * 3f);
        beep -= dt;
        if (beep <= 0) {
            float t = MathUtil.clamp(timer / 40f, 0, 1);
            beep = 0.14f + t * 1.05f;
            game.sfx.playAt("bomb_beep", pos, 0.55f, 1f + (1f - t) * 0.5f);
            lastBeepFlash = 1f;
        }
        if (timer <= 0) {
            exploded = true;
            game.explodeHE(pos, planter, 500f, 720f);
            game.explodeHE(pos, planter, 220f, 1100f);
            game.shake(2.4f);
            game.sfx.earRing = 1f;
        }
    }
}
