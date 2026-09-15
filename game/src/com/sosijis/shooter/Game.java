package com.sosijis.shooter;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ядро матча: мир, сущности, баллистика, взрывы, раунды. */
public class Game {

    public final Settings settings;
    public final Input input;
    public final Sfx sfx;
    public final Fx fx;
    public final Camera camera = new Camera();

    public GameMap map;
    public Pathfinder pathfinder;
    public GameMode mode;

    public final List<Actor> actors = new ArrayList<>();
    public final List<Grenade> grenades = new ArrayList<>();
    public final List<SmokeCloud> smokes = new ArrayList<>();
    public final List<FireArea> fires = new ArrayList<>();
    public final List<Hostage> hostages = new ArrayList<>();
    public final List<Pickup> pickups = new ArrayList<>();
    public Player player;
    public Bomb bomb;
    public Vec2 looseBomb;

    public boolean paused;
    public boolean showScoreboard;
    public boolean buyOpen;
    public boolean movementLocked;
    public float time;
    public float hitmarker;
    public boolean hitmarkerKill;
    public float lastDamageAngle;
    public float damageIndicator;
    public float lowHpPulse;
    public float renderMs;
    public Actor spectating;

    public static class Feed {
        public String killer, victim, weapon;
        public boolean headshot, teamkill;
        public Color killerColor, victimColor;
        public float life = 6f;
    }

    public final List<Feed> killFeed = new ArrayList<>();
    public String announceText = "";
    public Color announceColor = Color.WHITE;
    public float announceTime;

    public Game(Settings settings, Input input, Sfx sfx) {
        this.settings = settings;
        this.input = input;
        this.sfx = sfx;
        this.fx = new Fx(settings);
        sfx.losTest = (a, b) -> map == null || map.lineOfSight(a, b);
    }

    // ------------------------------------------------------------- Старт матча

    public void startMatch() {
        map = MapLibrary.fresh(settings.mapName);
        pathfinder = new Pathfinder(map);
        actors.clear();
        grenades.clear(); smokes.clear(); fires.clear(); hostages.clear(); pickups.clear();
        killFeed.clear();
        fx.clear();
        bomb = null;
        looseBomb = null;
        time = 0;

        mode = settings.modeType.create();

        int playerTeam = settings.modeType == GameMode.Type.ZOMBIE ? Actor.TEAM_CT
                : (MathUtil.chance(0.5f) ? Actor.TEAM_T : Actor.TEAM_CT);
        if (settings.modeType == GameMode.Type.DEFUSE || settings.modeType == GameMode.Type.HOSTAGE)
            playerTeam = MathUtil.chance(0.5f) ? Actor.TEAM_T : Actor.TEAM_CT;

        player = new Player(playerTeam, "Вы");
        player.money = settings.startMoney;
        actors.add(player);
        spectating = player;

        int bots = MathUtil.clamp(settings.botCount, 0, 19);
        boolean ffa = mode.ffa();
        for (int i = 0; i < bots; i++) {
            int team;
            if (settings.modeType == GameMode.Type.ZOMBIE) team = Actor.TEAM_CT;
            else if (ffa) team = i % 2 == 0 ? Actor.TEAM_T : Actor.TEAM_CT;
            else team = (i % 2 == 0) ? Actor.TEAM_T : Actor.TEAM_CT;
            Bot b = new Bot(team, settings.difficulty, i);
            b.money = settings.startMoney;
            actors.add(b);
        }

        camera.targetZoom = 1f;
        mode.start(this);
        sfx.startMusic();
    }

    // ----------------------------------------------------------------- Раунд

    public void resetRound(boolean fullGear) {
        grenades.clear();
        smokes.clear();
        fires.clear();
        pickups.clear();
        looseBomb = null;
        hostages.clear();
        for (Feed f : killFeed) f.life = Math.min(f.life, 1.5f);

        // Погибший в прошлом раунде теряет всё снаряжение — за него придётся платить снова
        if (mode.roundBased() && mode.buyEnabled()) {
            for (Actor a : actors) {
                if (a.alive) continue;
                a.primary = null;
                a.armor = 0;
                a.helmet = false;
                a.hasKit = false;
                java.util.Arrays.fill(a.nades, 0);
                if (a.secondary == null) giveDefaultGear(a);
            }
        }

        List<Actor> ts = new ArrayList<>(), cts = new ArrayList<>();
        for (Actor a : actors) {
            if (a.team == Actor.TEAM_T) ts.add(a); else cts.add(a);
        }
        for (int i = 0; i < ts.size(); i++) ts.get(i).reset(spawnPoint(Actor.TEAM_T, i), fullGear);
        for (int i = 0; i < cts.size(); i++) cts.get(i).reset(spawnPoint(Actor.TEAM_CT, i), fullGear);

        for (Actor a : actors) {
            a.armor = mode.type() == GameMode.Type.GUNGAME ? 100 : a.armor;
            if (a instanceof Bot b) {
                b.path.clear();
                b.target = null;
                b.hasLastKnown = false;
                b.state = Bot.State.ADVANCE;
                if (mode.buyEnabled() && a.team != Actor.TEAM_ZOMBIE) b.autoBuy(this);
            }
        }
        if (player.primary == null && player.secondary == null) giveDefaultGear(player);
        camera.pos.set(player.pos);
        damageIndicator = 0;
    }

    public void onRoundEnd(int winner) {
        if (player.alive) sfx.play(winner == player.team ? "round_win" : "round_lose", 0.6f, 1f);
        else sfx.play(winner == player.team ? "round_win" : "round_lose", 0.45f, 1f);
        buyOpen = false;
    }

    public Vec2 spawnPoint(int team, int index) {
        List<Vec2> list = team == Actor.TEAM_T ? map.spawnT : map.spawnCT;
        if (list.isEmpty()) return map.randomOpenCell();
        Vec2 base = list.get(index % list.size()).cpy();
        base.add(MathUtil.gauss(16f), MathUtil.gauss(16f));
        if (map.circleHitsSolid(base.x, base.y, 14)) base.set(list.get(index % list.size()));
        return base;
    }

    /** Точка респавна подальше от врагов (для режимов с возрождением). */
    public Vec2 safeSpawn(int team) {
        List<Vec2> list = team == Actor.TEAM_T ? map.spawnT : map.spawnCT;
        List<Vec2> pool = list.isEmpty() ? map.openCells : list;
        Vec2 best = null;
        float bestScore = -1;
        for (int i = 0; i < 26; i++) {
            Vec2 c = pool.get(MathUtil.randInt(pool.size()));
            float near = Float.MAX_VALUE;
            for (Actor a : actors) {
                if (!a.alive || (!mode.ffa() && a.team == team)) continue;
                near = Math.min(near, a.pos.dist(c));
            }
            if (near > bestScore) { bestScore = near; best = c; }
            if (bestScore > 900) break;
        }
        return best == null ? map.randomOpenCell() : best.cpy().add(MathUtil.gauss(18f), MathUtil.gauss(18f));
    }

    // -------------------------------------------------------------- Обновление

    public void update(float dt) {
        if (paused) { sfx.setDuck(0.35f); return; }
        sfx.setDuck(1f);
        time += dt;

        movementLocked = mode.roundBased() && mode.phase == GameMode.Phase.FREEZE;

        sfx.listener.set(camera.pos);
        sfx.earRing = Math.max(0, sfx.earRing - dt * 0.35f);
        if (player.alive && settings.tinnitus) sfx.earRing = Math.max(sfx.earRing, player.flash * 0.6f);

        mode.update(this, dt);

        // Копия списка: режим (например волны зомби) может менять состав прямо по ходу кадра
        List<Actor> tickList = new ArrayList<>(actors);
        for (Actor a : tickList) {
            if (a.alive) a.update(dt, this);
            else {
                a.deathTimer += dt;
                if (mode.respawnEnabled() && a.deathTimer > 3.2f) respawn(a);
            }
        }

        for (int i = grenades.size() - 1; i >= 0; i--) {
            Grenade gr = grenades.get(i);
            gr.update(dt, this);
            if (gr.exploded) grenades.remove(i);
        }
        for (int i = smokes.size() - 1; i >= 0; i--) {
            SmokeCloud s = smokes.get(i);
            s.update(dt, this);
            if (s.expired()) smokes.remove(i);
        }
        for (int i = fires.size() - 1; i >= 0; i--) {
            FireArea f = fires.get(i);
            f.update(dt, this);
            if (f.expired()) fires.remove(i);
        }
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup p = pickups.get(i);
            p.update(dt);
            if (p.expired()) pickups.remove(i);
        }
        handlePickups();

        fx.update(dt);

        for (int i = killFeed.size() - 1; i >= 0; i--) {
            killFeed.get(i).life -= dt;
            if (killFeed.get(i).life <= 0) killFeed.remove(i);
        }
        announceTime -= dt;
        hitmarker = Math.max(0, hitmarker - dt * 3.2f);
        damageIndicator = Math.max(0, damageIndicator - dt * 0.9f);
        lowHpPulse += dt;

        // Камера
        Actor view = player.alive ? player : (spectating != null && spectating.alive ? spectating : pickSpectate());
        spectating = view;
        Weapon pw = player.currentWeapon();
        camera.targetZoom = player.alive && pw.zoom > 0 ? (pw.zoom == 1 ? 1.65f : 2.4f) : 1f;
        camera.follow(view.pos, view == player && player.alive ? player.aimWorld : null, dt, false);
        camera.clampTo(map);
    }

    private Actor pickSpectate() {
        for (Actor a : actors) if (a.alive && a.team == player.team) return a;
        for (Actor a : actors) if (a.alive) return a;
        return player;
    }

    public void respawn(Actor a) {
        a.reset(safeSpawn(a.team), true);
        a.deathTimer = 0;
        if (mode.type() == GameMode.Type.DEATHMATCH) giveRandomLoadout(a);
        else if (mode.type() == GameMode.Type.GUNGAME) mode.onKill(this, a, null);
        else if (a instanceof Bot b) { b.autoBuy(this); }
        else giveDefaultGear(a);
        fx.explosion(a.pos.x, a.pos.y, 0.25f);
    }

    private void handlePickups() {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup p = pickups.get(i);
            for (Actor a : actors) {
                if (!a.alive || a.team == Actor.TEAM_ZOMBIE) continue;
                if (a.pos.dist(p.pos) > 30) continue;
                boolean want = a.primary == null;
                if (a == player && !want) want = input.key(KeyEvent.VK_E);
                if (a instanceof Bot && !want) want = a.primary != null && a.primary.isEmptyCompletely();
                if (!want) continue;
                if (a.primary != null) {
                    Pickup drop = new Pickup(a.pos.cpy(), a.primary.type, a.primary.ammo, a.primary.reserve);
                    pickups.add(drop);
                }
                Weapon w = new Weapon(p.type);
                w.ammo = p.ammo; w.reserve = p.reserve;
                a.primary = w;
                a.slot = Actor.SLOT_PRIMARY;
                a.shootCooldownGlobal = 0.4f;
                pickups.remove(i);
                sfx.playAt("pickup", a.pos, 0.55f, 1f);
                break;
            }
        }
    }

    // ------------------------------------------------------------- Баллистика

    public void onWeaponFired(Actor shooter, Weapon w) {
        Vec2 m = shooter.muzzlePos();
        if (settings.muzzleFlash) fx.muzzle(m.x, m.y, shooter.aim, w.type.cat == WeaponType.Cat.SNIPER ? 1.4f : 1f);
        fx.shell(shooter.pos.x, shooter.pos.y, shooter.aim);
        float vol = switch (w.type.cat) {
            case SNIPER -> 1.0f;
            case SHOTGUN, HEAVY -> 0.95f;
            case RIFLE -> 0.9f;
            case SMG -> 0.75f;
            default -> 0.7f;
        };
        sfx.playAt(w.type.sound, shooter.pos, vol, MathUtil.rand(0.96f, 1.05f));
        if (shooter == player && settings.screenShake) {
            float amp = switch (w.type.cat) {
                case SNIPER -> 0.55f;
                case SHOTGUN -> 0.45f;
                case HEAVY, RIFLE -> 0.22f;
                default -> 0.13f;
            };
            camera.addShake(amp);
        }
        shooter.noiseLevel = 2.0f;
    }

    public void fireHitscan(Actor shooter, Vec2 origin, float angle, WeaponType wt, float mul) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        float step = 4f;
        float dist = 0;
        float maxRange = wt.maxRange;
        float dmgScale = mul;
        int pen = wt.penPower;
        Set<Actor> hitAlready = new HashSet<>();
        int lastTx = -1, lastTy = -1;
        float x = origin.x, y = origin.y;
        float endX = x, endY = y;
        boolean stopped = false;

        while (dist < maxRange && !stopped) {
            x += dx * step; y += dy * step; dist += step;
            endX = x; endY = y;

            int tx = (int) (x / GameMap.TS), ty = (int) (y / GameMap.TS);
            if (!map.inBounds(tx, ty)) { stopped = true; break; }
            Tile t = map.tiles[tx][ty];
            if (t.blocksBullet && (tx != lastTx || ty != lastTy)) {
                lastTx = tx; lastTy = ty;
                boolean destroyed = map.damageTile(tx, ty, wt.damage * 0.55f);
                if (destroyed) {
                    if (t == Tile.GLASS) sfx.playAt("glass", new Vec2(x, y), 0.6f, MathUtil.rand(0.9f, 1.2f));
                    if (t == Tile.BARREL) {
                        fx.explosion(tx * GameMap.TS + 24, ty * GameMap.TS + 24, 1.1f);
                        explodeHE(new Vec2(tx * GameMap.TS + 24, ty * GameMap.TS + 24), shooter, 95f, 240f);
                    }
                    pathfinder.rebuildClearance();
                }
                fx.impact(x, y, angle + MathUtil.PI, t.mat);
                if (!settings.realisticDamage) { stopped = true; break; }
                dmgScale *= t.penetration;
                pen--;
                if (pen < 0 || dmgScale < 0.10f) { stopped = true; break; }
            }

            for (Actor a : actors) {
                if (!a.alive || a == shooter || hitAlready.contains(a)) continue;
                if (!canDamage(shooter, a)) continue;
                float ddx = a.pos.x - x, ddy = a.pos.y - y;
                float d2 = ddx * ddx + ddy * ddy;
                if (d2 > a.radius * a.radius) continue;
                hitAlready.add(a);
                float perp = Math.abs(-dy * (a.pos.x - origin.x) + dx * (a.pos.y - origin.y));
                boolean head = perp < a.radius * 0.30f && MathUtil.chance(0.78f);
                float dmg = wt.damage * dmgScale * falloff(wt, dist);
                damage(a, shooter, dmg, new Vec2(x, y), head, DamageType.BULLET, wt);
                dmgScale *= 0.62f;
                pen--;
                if (pen < 0 || dmgScale < 0.10f) { stopped = true; endX = x; endY = y; break; }
            }

            for (Hostage hst : hostages) {
                if (!hst.alive || hst.rescued) continue;
                if (hst.pos.dist(x, y) < hst.radius) {
                    hst.damage(wt.damage * dmgScale * 0.7f, this, shooter);
                    dmgScale *= 0.5f;
                }
            }
        }

        Color tc = wt.tracer;
        boolean show = wt.cat != WeaponType.Cat.MELEE;
        if (show) fx.tracer(origin.x, origin.y, endX, endY, tc, wt.cat == WeaponType.Cat.SNIPER ? 2.6f : 1.6f);
    }

    private float falloff(WeaponType wt, float dist) {
        if (!settings.realisticDamage) return 1f;
        if (dist <= wt.falloffStart) return 1f;
        float over = (dist - wt.falloffStart) / 220f;
        return Math.max(0.32f, (float) Math.pow(0.80, over));
    }

    // ------------------------------------------------------------------ Урон

    public void damage(Actor victim, Actor src, float dmg, Vec2 point, boolean headshot,
                       DamageType dtype, WeaponType weapon) {
        if (!victim.alive || dmg <= 0) return;
        if (!canDamage(src, victim) && src != victim && src != null) return;

        if (headshot) dmg *= 4f;

        if (victim.armor > 0 && dtype != DamageType.FIRE) {
            boolean protectedHit = !headshot || victim.helmet;
            if (protectedHit) {
                float pass = weapon != null ? weapon.armorPen : 0.55f;
                if (!settings.realisticDamage) pass = 0.7f;
                float after = dmg * pass;
                float absorbed = dmg - after;
                victim.armor = Math.max(0, victim.armor - absorbed * 0.5f);
                dmg = after;
                if (victim == player || src == player) sfx.playAt("hit_armor", victim.pos, 0.5f, 1f);
            }
        }

        victim.hp -= dmg;
        victim.hurtFlash = 1f;
        victim.lastAttacker = src;
        victim.lastAttackerTime = 5f;

        float ang = point != null ? (float) Math.atan2(victim.pos.y - point.y, victim.pos.x - point.x) : MathUtil.rand(MathUtil.TAU);
        fx.blood(victim.pos.x, victim.pos.y, ang, headshot ? 1.1f : 0.6f);

        if (src == player && victim != player) {
            hitmarker = 1f;
            hitmarkerKill = victim.hp <= 0;
            sfx.play(headshot ? "headshot" : "hit", 0.45f, 1f);
            fx.popup(victim.pos.x, victim.pos.y - 18, String.valueOf(Math.round(dmg)),
                    headshot ? new Color(0xFF6B6B) : new Color(0xFFE08A), headshot ? 17 : 14);
        }
        if (victim == player) {
            sfx.play("hurt", 0.55f, MathUtil.rand(0.9f, 1.1f));
            if (settings.screenShake) camera.addShake(Math.min(0.6f, dmg * 0.012f));
            lastDamageAngle = point != null ? (float) Math.atan2(point.y - victim.pos.y, point.x - victim.pos.x) : 0;
            damageIndicator = 1f;
        }

        if (victim.hp <= 0) kill(victim, src, headshot, dtype, weapon);
    }

    public void kill(Actor victim, Actor killer, boolean headshot, DamageType dtype, WeaponType weapon) {
        if (!victim.alive) return;
        victim.alive = false;
        victim.hp = 0;
        victim.deaths++;
        victim.deathTimer = 0;
        victim.planting = victim.defusing = false;
        victim.vel.set(0, 0);

        fx.gib(victim.pos.x, victim.pos.y);
        sfx.playAt("death", victim.pos, 0.8f, MathUtil.rand(0.9f, 1.1f));

        if (killer != null && killer != victim) {
            boolean tk = !mode.ffa() && killer.team == victim.team;
            if (tk) killer.money = Math.max(0, killer.money - 300);
            else {
                killer.kills++;
                killer.roundKills++;
                int reward = weapon != null ? weapon.killReward() : 300;
                killer.money = Math.min(16000, killer.money + reward);
            }
            addFeed(killer, victim, weapon, headshot, tk, dtype);
        } else {
            addFeed(null, victim, weapon, headshot, false, dtype);
        }

        // Выбрасываем оружие
        if (victim.primary != null && victim.team != Actor.TEAM_ZOMBIE && mode.type() != GameMode.Type.GUNGAME) {
            pickups.add(new Pickup(victim.pos.cpy(), victim.primary.type, victim.primary.ammo, victim.primary.reserve));
        }
        if (victim.hasBomb) { victim.hasBomb = false; dropBombAt(victim.pos); }

        mode.onKill(this, victim, killer);
    }

    private void addFeed(Actor killer, Actor victim, WeaponType weapon, boolean hs, boolean tk, DamageType dt) {
        Feed f = new Feed();
        f.killer = killer == null ? "" : killer.name;
        f.victim = victim.name;
        f.weapon = weapon != null ? weapon.label : dt.label;
        f.headshot = hs;
        f.teamkill = tk;
        f.killerColor = killer == null ? Color.GRAY : killer.color;
        f.victimColor = victim.color;
        killFeed.add(f);
        while (killFeed.size() > 6) killFeed.remove(0);
    }

    // ---------------------------------------------------------------- Гранаты

    public void throwGrenade(Actor owner, int kind, float power, float cook) {
        Vec2 from = Vec2.fromAngle(owner.aim, 20f).add(owner.pos);
        Grenade g = new Grenade(kind, owner, from, owner.aim, power, cook);
        grenades.add(g);
        sfx.playAt("nade_pin", owner.pos, 0.4f, 1f);
        owner.noiseLevel = 0.6f;
    }

    public void explodeHE(Vec2 at, Actor owner, float baseDamage, float radius) {
        fx.explosion(at.x, at.y, MathUtil.clamp(radius / 300f, 0.5f, 2.2f));
        sfx.playAt("explosion", at, 1f, MathUtil.rand(0.92f, 1.08f));
        if (settings.screenShake) {
            float d = at.dist(camera.pos);
            camera.addShake(MathUtil.clamp(1.6f - d / 700f, 0, 1.6f));
        }
        if (at.dist(player.pos) < radius * 0.8f && settings.tinnitus)
            sfx.earRing = Math.max(sfx.earRing, MathUtil.clamp(1f - at.dist(player.pos) / radius, 0, 1) * 0.8f);

        for (Actor a : actors) {
            if (!a.alive) continue;
            float d = a.pos.dist(at);
            if (d > radius) continue;
            float f = 1f - d / radius;
            f *= f;
            if (!map.lineOfSight(at, a.pos)) f *= 0.35f;
            float dmg = baseDamage * f;
            if (dmg > 1) damage(a, owner, dmg, at.cpy(), false, DamageType.EXPLOSION, null);
        }
        for (Hostage h : hostages) {
            if (!h.alive) continue;
            float d = h.pos.dist(at);
            if (d < radius) h.damage(baseDamage * (1f - d / radius) * 0.8f, this, owner);
        }
        // Разрушения вокруг
        int tr = (int) (radius / GameMap.TS);
        int cx = (int) (at.x / GameMap.TS), cy = (int) (at.y / GameMap.TS);
        boolean changed = false;
        for (int ty = cy - tr; ty <= cy + tr; ty++) {
            for (int tx = cx - tr; tx <= cx + tr; tx++) {
                if (!map.inBounds(tx, ty)) continue;
                float d = new Vec2(tx * GameMap.TS + 24, ty * GameMap.TS + 24).dist(at);
                if (d > radius * 0.7f) continue;
                if (map.damageTile(tx, ty, baseDamage * (1f - d / radius) * 1.6f)) changed = true;
            }
        }
        if (changed) pathfinder.rebuildClearance();
    }

    public void detonateFlash(Vec2 at, Actor owner) {
        fx.flashPop(at.x, at.y);
        sfx.playAt("flash", at, 1f, MathUtil.rand(0.95f, 1.05f));
        for (Actor a : actors) {
            if (!a.alive) continue;
            float d = a.pos.dist(at);
            if (d > 760) continue;
            if (!map.lineOfSight(at, a.pos)) continue;
            if (smokeBlocks(at, a.pos)) continue;
            float distFactor = MathUtil.clamp(1f - d / 760f, 0, 1);
            float toFlash = (float) Math.atan2(at.y - a.pos.y, at.x - a.pos.x);
            float view = Math.abs(MathUtil.angleDiff(a.aim, toFlash));
            float viewFactor = MathUtil.clamp(1f - view / MathUtil.PI, 0, 1);
            viewFactor = 0.18f + viewFactor * viewFactor * 1.15f;
            float amount = distFactor * viewFactor * 2.5f;
            if (amount > 0.12f) {
                a.applyFlash(amount);
                if (a == player && settings.tinnitus) sfx.earRing = Math.max(sfx.earRing, MathUtil.clamp(amount, 0, 1));
            }
        }
    }

    public void spawnSmoke(Vec2 at, Actor owner) {
        smokes.add(new SmokeCloud(at, owner));
        sfx.playAt("smoke_pop", at, 0.8f, 1f);
        for (int i = 0; i < 40 * settings.particleScale; i++)
            fx.smokePuff(at.x + MathUtil.gauss(30f), at.y + MathUtil.gauss(30f), MathUtil.rand(1.5f, 3f), 1.4f);
        // Дым тушит огонь
        for (FireArea f : fires) if (f.pos.dist(at) < 220) f.douse(1.2f);
    }

    public void spawnFire(Vec2 at, Actor owner) {
        FireArea f = new FireArea(at, owner);
        f.addPatch(at.x, at.y, 34);
        int n = 7;
        for (int i = 0; i < n; i++) {
            float ang = MathUtil.TAU * i / n + MathUtil.rand(0.5f);
            float d = MathUtil.rand(35f, 95f);
            float px = at.x + (float) Math.cos(ang) * d;
            float py = at.y + (float) Math.sin(ang) * d;
            if (map.solidAtWorld(px, py)) continue;
            if (!map.lineOfSight(at, new Vec2(px, py))) continue;
            f.addPatch(px, py, MathUtil.rand(26f, 42f));
        }
        fires.add(f);
        sfx.playAt("explosion", at, 0.45f, 1.6f);
        fx.explosion(at.x, at.y, 0.5f);
        // Свежий огонь гаснет в дыму
        for (SmokeCloud s : smokes) if (s.contains(at)) f.douse(2.5f);
    }

    public boolean inFire(Vec2 p) {
        for (FireArea f : fires) if (f.covers(p)) return true;
        return false;
    }

    public Vec2 escapeFireDir(Vec2 p) {
        Vec2 away = new Vec2();
        for (FireArea f : fires) {
            for (FireArea.Patch pa : f.patches) {
                float d = pa.p.dist(p);
                if (d < pa.r + 90) {
                    Vec2 v = p.cpy().sub(pa.p);
                    if (v.lenSq() < 1) v.set(MathUtil.rand(-1, 1), MathUtil.rand(-1, 1));
                    away.addScaled(v.norm(), (pa.r + 90 - d) / (pa.r + 90));
                }
            }
        }
        if (away.lenSq() < 0.01f) away.set(Vec2.fromAngle(MathUtil.rand(MathUtil.TAU)));
        return away.norm();
    }

    public Vec2 nearestDangerDir(Vec2 p, float radius) {
        Vec2 away = new Vec2();
        boolean any = false;
        for (Grenade g : grenades) {
            if (g.kind == Actor.NADE_SMOKE) continue;
            float d = g.pos.dist(p);
            if (d < radius) {
                Vec2 v = p.cpy().sub(g.pos);
                if (v.lenSq() < 1) v.set(1, 0);
                away.addScaled(v.norm(), (radius - d) / radius);
                any = true;
            }
        }
        return any ? away.norm() : null;
    }

    public void shake(float amount) { if (settings.screenShake) camera.addShake(amount); }

    // ---------------------------------------------------------- Видимость

    public boolean smokeBlocks(Vec2 a, Vec2 b) {
        for (SmokeCloud s : smokes) if (s.blocksSegment(a.x, a.y, b.x, b.y)) return true;
        return false;
    }

    public boolean losClear(Vec2 a, Vec2 b) {
        return map.lineOfSight(a, b) && !smokeBlocks(a, b);
    }

    public boolean canSee(Actor observer, Actor target) {
        if (!target.alive) return false;
        float d = observer.pos.dist(target.pos);
        if (d > observer.effectiveViewDist()) return false;
        if (d > 110) {
            float ang = (float) Math.atan2(target.pos.y - observer.pos.y, target.pos.x - observer.pos.x);
            if (Math.abs(MathUtil.angleDiff(observer.aim, ang)) > observer.viewFov * 0.5f) return false;
        }
        return losClear(observer.pos, target.pos);
    }

    public boolean visibleToPlayer(Actor a) {
        if (a == player) return true;
        if (!settings.fogOfWar) return true;
        Actor eye = player.alive ? player : spectating;
        if (eye == null) return true;
        if (!player.alive) return losClear(eye.pos, a.pos);
        if (a.team == player.team && !mode.ffa()) return true;   // союзников видим по радио
        return canSee(eye, a) || a.pos.dist(eye.pos) < 60;
    }

    public boolean areEnemies(Actor a, Actor b) {
        if (a == null || b == null || a == b) return false;
        if (mode.ffa()) return true;
        return a.team != b.team;
    }

    public boolean canDamage(Actor src, Actor victim) {
        if (src == null) return true;
        if (src == victim) return true;
        if (mode.ffa()) return true;
        if (src.team == victim.team) return settings.friendlyFire;
        return true;
    }

    public void pushApart(Actor a) {
        for (Actor o : actors) {
            if (o == a || !o.alive) continue;
            float dx = a.pos.x - o.pos.x, dy = a.pos.y - o.pos.y;
            float d2 = dx * dx + dy * dy;
            float min = a.radius + o.radius;
            if (d2 < min * min && d2 > 0.001f) {
                float d = (float) Math.sqrt(d2);
                float push = (min - d) * 0.5f;
                a.pos.x += dx / d * push;
                a.pos.y += dy / d * push;
            }
        }
    }

    // ----------------------------------------------------------- Помощники

    public int aliveCount(int team) {
        int n = 0;
        for (Actor a : actors) if (a.alive && a.team == team) n++;
        return n;
    }

    public List<Actor> livingActors(int team) {
        List<Actor> out = new ArrayList<>();
        for (Actor a : actors) if (a.alive && a.team == team) out.add(a);
        return out;
    }

    public Actor bombCarrier() {
        for (Actor a : actors) if (a.alive && a.hasBomb) return a;
        return null;
    }

    public void dropBombAt(Vec2 p) { looseBomb = p.cpy(); }

    public Vec2 nearestSite(Vec2 from) {
        Vec2 a = siteCenter(true), b = siteCenter(false);
        if (a == null) return b;
        if (b == null) return a;
        return from.dist(a) < from.dist(b) ? a : b;
    }

    public Vec2 siteCenter(boolean siteA) {
        List<Vec2> cells = siteA ? map.siteACells : map.siteBCells;
        if (cells.isEmpty()) return null;
        float sx = 0, sy = 0;
        for (Vec2 c : cells) { sx += c.x; sy += c.y; }
        return new Vec2(sx / cells.size(), sy / cells.size());
    }

    public Vec2 rescueCenter() {
        if (map.rescueCells.isEmpty()) return map.randomOpenCell();
        float sx = 0, sy = 0;
        for (Vec2 c : map.rescueCells) { sx += c.x; sy += c.y; }
        return new Vec2(sx / map.rescueCells.size(), sy / map.rescueCells.size());
    }

    public void spawnHostages(int n) {
        hostages.clear();
        List<Vec2> spots = new ArrayList<>(map.hostageSpots);
        for (int i = 0; i < n; i++) {
            Vec2 p = spots.isEmpty() ? map.randomOpenCell() : spots.get(i % spots.size()).cpy();
            p.add(MathUtil.gauss(12f), MathUtil.gauss(12f));
            hostages.add(new Hostage(p));
        }
    }

    public void removeZombies() {
        actors.removeIf(a -> a.team == Actor.TEAM_ZOMBIE);
    }

    public void spawnZombies(int n, int wave) {
        for (int i = 0; i < n; i++) {
            Bot z = new Bot(Actor.TEAM_ZOMBIE, settings.difficulty, MathUtil.randInt(12));
            z.name = "Заражённый";
            z.tag = "";
            z.maxHp = z.hp = 80 + wave * 22;
            z.speedMul = 0.85f + Math.min(0.75f, wave * 0.05f);
            z.primary = null;
            z.secondary = null;
            z.slot = Actor.SLOT_KNIFE;
            z.viewDist = 1600;
            z.viewFov = MathUtil.TAU;
            z.aggression = 1f;
            z.patience = 0f;
            z.color = new Color(0x6FA84E);
            z.pos.set(farSpawnFromCts());
            actors.add(z);
        }
    }

    private Vec2 farSpawnFromCts() {
        Vec2 best = null; float bestD = -1;
        for (int i = 0; i < 40; i++) {
            Vec2 c = map.randomOpenCell();
            float near = Float.MAX_VALUE;
            for (Actor a : actors) if (a.alive && a.team == Actor.TEAM_CT) near = Math.min(near, a.pos.dist(c));
            if (near > bestD && near > 260) { bestD = near; best = c; }
        }
        return best == null ? map.randomOpenCell() : best;
    }

    public void giveDefaultGear(Actor a) {
        a.giveWeapon(a.team == Actor.TEAM_T ? WeaponType.GLOCK : WeaponType.USP);
        a.knife.refill();
        a.slot = Actor.SLOT_SECONDARY;
    }

    public void giveRandomLoadout(Actor a) {
        WeaponType[] pool = {WeaponType.AK47, WeaponType.M4A4, WeaponType.GALIL, WeaponType.FAMAS,
                WeaponType.MP9, WeaponType.MAC10, WeaponType.UMP45, WeaponType.P90, WeaponType.NOVA,
                WeaponType.XM1014, WeaponType.SCOUT, WeaponType.AUG, WeaponType.SG553, WeaponType.M249,
                WeaponType.DEAGLE, WeaponType.AWP};
        WeaponType wt = pool[MathUtil.randInt(pool.length)];
        a.primary = null;
        a.secondary = null;
        if (wt.cat == WeaponType.Cat.PISTOL) a.giveWeapon(wt);
        else { a.giveWeapon(wt); a.giveWeapon(a.team == Actor.TEAM_T ? WeaponType.GLOCK : WeaponType.USP); }
        a.slot = a.primary != null ? Actor.SLOT_PRIMARY : Actor.SLOT_SECONDARY;
        a.armor = 100;
        a.helmet = true;
        a.nades[Actor.NADE_HE] = 1;
        a.nades[Actor.NADE_FLASH] = 1;
        a.nadeSel = Actor.NADE_HE;
    }

    public void autoGear(Actor a) {
        if (a instanceof Bot b) b.autoBuy(this);
        else if (a.primary == null) {
            a.giveWeapon(a.team == Actor.TEAM_T ? WeaponType.AK47 : WeaponType.M4A4);
            giveDefaultGear(a);
            a.slot = Actor.SLOT_PRIMARY;
            a.armor = 100; a.helmet = true;
            a.nades[Actor.NADE_HE] = 1;
            a.nades[Actor.NADE_FLASH] = 2;
            a.nades[Actor.NADE_SMOKE] = 1;
            a.nades[Actor.NADE_MOLOTOV] = 1;
        }
    }

    public void announce(String text, Color c) {
        announceText = text;
        announceColor = c;
        announceTime = 3.2f;
    }
}
