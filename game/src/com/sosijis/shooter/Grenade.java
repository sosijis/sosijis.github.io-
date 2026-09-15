package com.sosijis.shooter;

/**
 * Летящая граната. Высота z имитируется отдельно от плоскости, поэтому граната
 * перелетает низкие препятствия, отскакивает от стен и катится по полу.
 */
public class Grenade {
    public final int kind;
    public final Actor owner;
    public final Vec2 pos = new Vec2();
    public final Vec2 vel = new Vec2();
    public float z, zVel;
    public float fuse;
    public boolean exploded;
    public float spin, spinSpeed;
    public float age;
    public float trailTimer;
    public boolean impactFuse;   // молотов взрывается от удара

    private static final float GRAVITY = 900f;

    public Grenade(int kind, Actor owner, Vec2 from, float angle, float power, float cooked) {
        this.kind = kind;
        this.owner = owner;
        pos.set(from);
        float speed = 620f * power;
        vel.set(Vec2.fromAngle(angle, speed));
        vel.addScaled(owner.vel, 0.35f);
        z = 22f;
        zVel = 210f * MathUtil.clamp(power, 0.4f, 1.2f);
        spinSpeed = MathUtil.rand(-16f, 16f);
        switch (kind) {
            case Actor.NADE_HE -> fuse = Math.max(0.25f, 2.0f - cooked);
            case Actor.NADE_FLASH -> fuse = 1.55f;
            case Actor.NADE_SMOKE -> fuse = 1.7f;
            default -> { fuse = 6f; impactFuse = true; }
        }
    }

    public void update(float dt, Game game) {
        age += dt;
        spin += spinSpeed * dt;
        zVel -= GRAVITY * dt;
        z += zVel * dt;

        float prevX = pos.x, prevY = pos.y;
        float nx = pos.x + vel.x * dt;
        float ny = pos.y + vel.y * dt;

        boolean bounced = false;
        // На высоте перелетаем невысокие преграды (ящики), но не полноценные стены
        boolean highFlight = z > 34f;
        if (blocked(game, nx, pos.y, highFlight)) { vel.x = -vel.x * 0.45f; nx = prevX; bounced = true; }
        if (blocked(game, pos.x, ny, highFlight)) { vel.y = -vel.y * 0.45f; ny = prevY; bounced = true; }
        pos.set(nx, ny);
        pos.x = MathUtil.clamp(pos.x, 6, game.map.pixelW() - 6);
        pos.y = MathUtil.clamp(pos.y, 6, game.map.pixelH() - 6);

        if (z <= 0) {
            z = 0;
            if (impactFuse && (zVel < -80f || bounced)) { explode(game); return; }
            if (zVel < -40f) {
                zVel = -zVel * 0.36f;
                vel.mul(0.62f);
                bounced = true;
            } else {
                zVel = 0;
                vel.mul(1f - Math.min(1f, dt * 4.6f));
            }
        }
        if (bounced && game != null) {
            game.sfx.playAt("nade_bounce", pos, 0.28f, MathUtil.rand(0.9f, 1.2f));
            if (impactFuse) explode(game);
        }

        trailTimer -= dt;
        if (trailTimer <= 0) {
            trailTimer = 0.03f;
            if (kind == Actor.NADE_MOLOTOV) game.fx.smokePuff(pos.x, pos.y, 0.18f, 0.5f);
            else if (kind == Actor.NADE_HE) game.fx.spark(pos.x, pos.y, 0.15f);
        }

        fuse -= dt;
        if (fuse <= 0 && !exploded) explode(game);
    }

    private boolean blocked(Game game, float x, float y, boolean high) {
        Tile t = game.map.tileAtWorld(x, y);
        if (!t.solid) return false;
        if (high && (t == Tile.CRATE || t == Tile.CRATE_METAL || t == Tile.FENCE || t == Tile.BARREL)) return false;
        return true;
    }

    public void explode(Game game) {
        if (exploded) return;
        exploded = true;
        switch (kind) {
            case Actor.NADE_HE -> game.explodeHE(pos, owner, 100f, 330f);
            case Actor.NADE_FLASH -> game.detonateFlash(pos, owner);
            case Actor.NADE_SMOKE -> game.spawnSmoke(pos, owner);
            default -> game.spawnFire(pos, owner);
        }
    }

    public String label() { return Actor.NADE_NAMES[kind]; }
}
