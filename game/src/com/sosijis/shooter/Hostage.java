package com.sosijis.shooter;

/** Заложник: стоит на месте, следует за освободившим его спецназовцем, может погибнуть. */
public class Hostage {
    public final Vec2 pos = new Vec2();
    public final Vec2 vel = new Vec2();
    public Actor follower;
    public boolean rescued, alive = true;
    public float hp = 100;
    public float radius = 12f;
    public float anim;
    public float panic;

    public Hostage(Vec2 p) { pos.set(p); }

    public void update(float dt, Game game) {
        if (!alive || rescued) return;
        anim += dt;
        panic = Math.max(0, panic - dt * 0.4f);
        if (follower != null && (!follower.alive)) follower = null;
        if (follower != null) {
            float d = pos.dist(follower.pos);
            if (d > 56) {
                Vec2 dir = follower.pos.cpy().sub(pos).norm().mul(Math.min(215f, d * 3f));
                vel.x = MathUtil.damp(vel.x, dir.x, 0.0007f, dt * 10f);
                vel.y = MathUtil.damp(vel.y, dir.y, 0.0007f, dt * 10f);
            } else {
                vel.mul(1f - Math.min(1f, dt * 8f));
            }
            game.map.moveCircle(pos, vel.x * dt, vel.y * dt, radius);
        } else {
            vel.mul(1f - Math.min(1f, dt * 6f));
        }
    }

    public void damage(float d, Game game, Actor src) {
        if (!alive) return;
        hp -= d;
        panic = 1f;
        game.fx.blood(pos.x, pos.y, MathUtil.rand(MathUtil.TAU), 0.6f);
        if (hp <= 0) {
            alive = false;
            game.sfx.playAt("death", pos, 0.7f, 1.1f);
            game.announce("Заложник погиб!", new java.awt.Color(0xFF6B6B));
            if (src != null && src.team == Actor.TEAM_CT) src.money = Math.max(0, src.money - 1000);
        }
    }
}
