package com.sosijis.shooter;

import java.awt.Color;

/** Базовый боец: здоровье, броня, инвентарь, стрельба, эффекты флешки и огня. */
public abstract class Actor {

    public static final int TEAM_T = 0;
    public static final int TEAM_CT = 1;
    public static final int TEAM_ZOMBIE = 2;

    public static final int NADE_HE = 0, NADE_FLASH = 1, NADE_SMOKE = 2, NADE_MOLOTOV = 3;
    public static final String[] NADE_NAMES = {"Осколочная", "Светошумовая", "Дымовая", "Молотов"};
    public static final int[] NADE_PRICE = {300, 200, 300, 400};

    public static final int SLOT_PRIMARY = 0, SLOT_SECONDARY = 1, SLOT_KNIFE = 2, SLOT_NADE = 3, SLOT_BOMB = 4;

    public static final float BASE_SPEED = 232f;

    public final Vec2 pos = new Vec2();
    public final Vec2 vel = new Vec2();
    public float aim;                 // направление взгляда/ствола
    public float bodyAngle;           // корпус доворачивается плавнее
    public float recoilPitch;         // визуальный «подброс», влияет на разброс
    public float recoilYaw;

    public int team;
    public String name = "boec";
    public int skin;

    public float hp = 100, maxHp = 100;
    public float armor = 0;
    public boolean helmet = false;
    public boolean alive = true;
    public float radius = 13f;

    public Weapon knife = new Weapon(WeaponType.KNIFE);
    public Weapon primary;
    public Weapon secondary;
    public int slot = SLOT_PRIMARY;

    public final int[] nades = new int[4];
    public int nadeSel = NADE_HE;
    public float nadeCook;            // сколько удерживается чека
    public boolean nadeHeld;

    public int money = 800;
    public int kills, deaths, assists;
    public int roundKills;

    public float flash;               // ослепление 0..1
    public float flashTotal;
    public float burn;                // остаток горения
    public Actor burnSource;
    private float burnTick;

    public boolean crouching, walking, moving;
    public float stepPhase;
    public float noiseLevel;          // «громкость» бойца для ИИ, затухает

    public boolean hasBomb, hasKit;
    public float plantProgress, defuseProgress;
    public boolean planting, defusing;

    public float deathTimer, respawnTimer;
    public Actor lastAttacker;
    public float lastAttackerTime;
    public float hurtFlash;
    public float viewDist = 980f;
    public float viewFov = 1.75f;     // рад, полный угол конуса
    public float speedMul = 1f;
    public float shootCooldownGlobal; // задержка после смены оружия

    public Color color;

    public Actor(int team, String name) {
        this.team = team;
        this.name = name;
        this.color = team == TEAM_T ? new Color(0xD08A3E) : (team == TEAM_CT ? new Color(0x5E9BD6) : new Color(0x7ED06A));
        this.skin = MathUtil.randInt(4);
    }

    public Weapon currentWeapon() {
        return switch (slot) {
            case SLOT_PRIMARY -> primary != null ? primary : (secondary != null ? secondary : knife);
            case SLOT_SECONDARY -> secondary != null ? secondary : knife;
            default -> knife;
        };
    }

    public WeaponType currentType() { return currentWeapon().type; }

    public boolean isHoldingGun() { return slot == SLOT_PRIMARY || slot == SLOT_SECONDARY; }

    public void giveWeapon(WeaponType t) {
        Weapon w = new Weapon(t);
        if (t.cat == WeaponType.Cat.PISTOL) { secondary = w; if (primary == null) slot = SLOT_SECONDARY; }
        else if (t.cat == WeaponType.Cat.MELEE) knife = w;
        else { primary = w; slot = SLOT_PRIMARY; }
        shootCooldownGlobal = 0.35f;
    }

    public void selectSlot(int s) {
        if (s == slot) return;
        if (s == SLOT_PRIMARY && primary == null) return;
        if (s == SLOT_SECONDARY && secondary == null) return;
        if (s == SLOT_NADE && totalNades() == 0) return;
        if (s == SLOT_BOMB && !hasBomb) return;
        Weapon cur = currentWeapon();
        cur.cancelReload();
        cur.zoom = 0;
        slot = s;
        shootCooldownGlobal = 0.28f;
        nadeHeld = false;
        nadeCook = 0;
    }

    public int totalNades() { return nades[0] + nades[1] + nades[2] + nades[3]; }

    public void cycleNade() {
        for (int i = 1; i <= 4; i++) {
            int idx = (nadeSel + i) % 4;
            if (nades[idx] > 0) { nadeSel = idx; return; }
        }
    }

    public float moveSpeed() {
        float s = BASE_SPEED * speedMul;
        WeaponType t = currentType();
        s *= slot == SLOT_NADE || slot == SLOT_BOMB ? 1.02f : t.moveSpeed;
        if (currentWeapon().zoom > 0) s *= 0.36f;
        if (crouching) s *= 0.36f;
        else if (walking) s *= 0.54f;
        if (burn > 0) s *= 1.06f;               // паника: чуть быстрее
        if (hp < 30) s *= 0.94f;
        return s;
    }

    /** Множитель дальности видимости с учётом ослепления. */
    public float effectiveViewDist() {
        return viewDist * (1f - MathUtil.clamp(flash, 0, 1) * 0.92f);
    }

    public void updateCommon(float dt, Game game) {
        knife.update(dt);
        if (primary != null) primary.update(dt);
        if (secondary != null) secondary.update(dt);
        shootCooldownGlobal -= dt;

        recoilPitch = MathUtil.damp(recoilPitch, 0, 0.0015f, dt);
        recoilYaw = MathUtil.damp(recoilYaw, 0, 0.0015f, dt);
        hurtFlash = Math.max(0, hurtFlash - dt * 2.2f);
        noiseLevel = Math.max(0, noiseLevel - dt * 1.6f);
        lastAttackerTime -= dt;

        if (flash > 0) {
            flash -= dt * (0.30f + 0.55f * (1f - flash));
            if (flash < 0) flash = 0;
        }

        if (burn > 0) {
            burn -= dt;
            burnTick -= dt;
            if (burnTick <= 0) {
                burnTick = 0.28f;
                game.damage(this, burnSource, 4.5f, pos.cpy(), false, DamageType.FIRE, null);
            }
            if (MathUtil.chance(dt * 18f)) game.fx.fire(pos.x + MathUtil.gauss(7), pos.y + MathUtil.gauss(7), 0.4f);
        }

        bodyAngle = MathUtil.approachAngle(bodyAngle, aim, dt * 9f);

        // Шаги
        Tile floor = game.map.tileAtWorld(pos.x, pos.y);
        float speed = vel.len();
        if (alive && speed > 12) {
            float interval = walking ? 0.55f : (crouching ? 0.75f : 0.38f);
            interval *= 232f / Math.max(60f, speed) * 0.9f;
            stepPhase += dt;
            if (stepPhase >= interval) {
                stepPhase = 0;
                float vol = walking ? 0.16f : (crouching ? 0.10f : 0.62f);
                if (floor.mat == Tile.Mat.WATER) vol *= 1.7f;
                if (floor.mat == Tile.Mat.GRASS) vol *= 0.8f;
                noiseLevel = Math.max(noiseLevel, vol);
                game.sfx.playAt(stepSound(floor.mat), pos, vol * 0.55f, MathUtil.rand(0.92f, 1.10f));
                if (floor.mat == Tile.Mat.WATER) game.fx.splash(pos.x, pos.y);
            }
        } else {
            stepPhase = 0.9f;
        }
    }

    private String stepSound(Tile.Mat m) {
        return switch (m) {
            case METAL -> "step_metal";
            case WOOD -> "step_wood";
            case GRASS -> "step_grass";
            case SAND -> "step_sand";
            case WATER -> "step_water";
            default -> "step_concrete";
        };
    }

    /** Попытка выстрела текущим оружием. */
    public boolean tryShoot(Game game) {
        if (!alive || shootCooldownGlobal > 0) return false;
        Weapon w = currentWeapon();
        if (slot == SLOT_KNIFE || w.type.cat == WeaponType.Cat.MELEE) return meleeAttack(game, w);
        if (!w.canFire()) {
            if (w.ammo <= 0 && !w.reloading) {
                game.sfx.playAt("dryfire", pos, 0.4f, 1f);
                w.cooldown = 0.25f;
                if (game.settings.autoReload) w.startReload();
            }
            return false;
        }

        float spread = w.currentSpread(moving && !walking, crouching, false);
        spread *= (1f + flash * 1.4f);
        if (burn > 0) spread *= 1.25f;
        float[] recoil = w.sprayOffset();

        int pellets = Math.max(1, w.type.pellets);
        for (int i = 0; i < pellets; i++) {
            float dev = MathUtil.gauss(spread * 0.5f);
            if (pellets > 1) dev = MathUtil.rand(-spread, spread);
            float angle = aim + dev + recoil[1] * 0.35f;
            game.fireHitscan(this, muzzlePos(), angle, w.type, 1f);
        }

        w.consume();
        recoilPitch += recoil[2] * 0.9f;
        recoilYaw += recoil[1] * 14f;
        applyRecoilToAim(recoil);

        game.onWeaponFired(this, w);
        noiseLevel = 1.6f;
        if (game.settings.autoReload && w.ammo == 0 && w.reserve > 0) w.startReload();
        return true;
    }

    protected void applyRecoilToAim(float[] recoil) {
        aim += recoil[1] * 0.05f;
    }

    private boolean meleeAttack(Game game, Weapon w) {
        if (w.cooldown > 0) return false;
        w.cooldown = 0.42f;
        game.sfx.playAt("knife", pos, 0.6f, MathUtil.rand(0.95f, 1.08f));
        Vec2 tip = Vec2.fromAngle(aim, 40f).add(pos);
        boolean hit = false;
        for (Actor a : game.actors) {
            if (a == this || !a.alive) continue;
            if (!game.canDamage(this, a)) continue;
            if (a.pos.dist(tip) < a.radius + 18) {
                boolean back = Math.abs(MathUtil.angleDiff(a.aim, aim)) < 1.2f;
                float dmg = back ? 180f : 55f;
                game.damage(a, this, dmg, tip, false, DamageType.MELEE, w.type);
                hit = true;
                break;
            }
        }
        if (!hit) {
            Tile t = game.map.tileAtWorld(tip.x, tip.y);
            if (t.solid) {
                game.map.damageTile((int) (tip.x / GameMap.TS), (int) (tip.y / GameMap.TS), 30);
                game.fx.impact(tip.x, tip.y, aim + MathUtil.PI, t.mat);
            }
        }
        noiseLevel = 0.5f;
        return true;
    }

    public Vec2 muzzlePos() {
        float off = switch (currentType().cat) {
            case SNIPER, HEAVY -> 30f;
            case RIFLE -> 26f;
            case SMG, SHOTGUN -> 22f;
            default -> 18f;
        };
        return Vec2.fromAngle(aim, off).add(pos);
    }

    // ---------------- Урон ----------------

    public void applyFlash(float amount) {
        flash = Math.min(1.6f, flash + amount);
        flashTotal = Math.max(flashTotal, flash);
    }

    public void ignite(float seconds, Actor source) {
        burn = Math.max(burn, seconds);
        burnSource = source;
    }

    public void extinguish() { burn = 0; }

    public void reset(Vec2 spawn, boolean fullGear) {
        pos.set(spawn);
        vel.set(0, 0);
        hp = maxHp;
        alive = true;
        flash = 0; burn = 0;
        plantProgress = defuseProgress = 0;
        planting = defusing = false;
        hurtFlash = 0;
        roundKills = 0;
        deathTimer = 0;
        if (fullGear) {
            if (primary != null) primary.refill();
            if (secondary != null) secondary.refill();
        }
        knife.refill();
        slot = primary != null ? SLOT_PRIMARY : SLOT_SECONDARY;
        if (secondary == null && primary == null) slot = SLOT_KNIFE;
    }

    public abstract void update(float dt, Game game);

    public String teamName() {
        return switch (team) {
            case TEAM_T -> "Террористы";
            case TEAM_CT -> "Спецназ";
            default -> "Зомби";
        };
    }
}
