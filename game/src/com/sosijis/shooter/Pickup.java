package com.sosijis.shooter;

/** Оружие, выпавшее из рук убитого. Можно подобрать, подойдя и нажав E. */
public class Pickup {
    public final Vec2 pos = new Vec2();
    public final WeaponType type;
    public int ammo, reserve;
    public float life = 30f;
    public float bob;
    public float angle;

    public Pickup(Vec2 p, WeaponType type, int ammo, int reserve) {
        pos.set(p);
        this.type = type;
        this.ammo = ammo;
        this.reserve = reserve;
        this.angle = MathUtil.rand(MathUtil.TAU);
    }

    public void update(float dt) {
        life -= dt;
        bob += dt * 3f;
    }

    public boolean expired() { return life <= 0; }
}
