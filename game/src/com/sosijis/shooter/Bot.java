package com.sosijis.shooter;

import java.util.ArrayList;
import java.util.List;

/**
 * Боевой ИИ. Боты видят конусом обзора с проверкой прямой видимости (дым им тоже мешает),
 * слышат шаги и выстрелы, ищут укрытия, бросают гранаты и выполняют задачу режима.
 */
public class Bot extends Actor {

    public enum State { ADVANCE, ENGAGE, COVER, INVESTIGATE, HOLD, BLIND, FLEE_FIRE, OBJECTIVE }

    public State state = State.ADVANCE;
    public Actor target;
    public final Vec2 lastKnown = new Vec2();
    public boolean hasLastKnown;
    public float lostTimer;
    public float reaction;
    public float aimJitterT;
    public float burstTimer, burstPause;
    public float nadeCooldown = MathUtil.rand(2f, 9f);
    public float decisionTimer;
    public float perceptionTimer;
    public float strafeDir = 1, strafeTimer;
    public float repathTimer;
    public List<Vec2> path = new ArrayList<>();
    public int pathIdx;
    public Vec2 goal = new Vec2();
    public Vec2 holdSpot;
    public float aggression = MathUtil.rand(0.3f, 1f);
    public float patience = MathUtil.rand(0.3f, 1f);
    public float skill = 0.7f;
    public float stuckTimer;
    private final Vec2 lastPos = new Vec2();
    public String tag = "";

    private static final String[] T_NAMES = {
            "Ара", "Волк", "Кабан", "Шейх", "Смок", "Кобра", "Хан", "Барс", "Тень", "Лом", "Ржавый", "Гюрза"
    };
    private static final String[] CT_NAMES = {
            "Альфа", "Витязь", "Гром", "Дельта", "Сокол", "Рысь", "Циклон", "Зубр", "Форт", "Сапёр", "Байкал", "Омега"
    };

    public Bot(int team, Settings.Difficulty diff, int index) {
        super(team, (team == TEAM_T ? T_NAMES[index % T_NAMES.length] : CT_NAMES[index % CT_NAMES.length]));
        skill = diff.aim;
        reaction = diff.reaction;
        viewDist = 820f + diff.aim * 260f;
        viewFov = 1.9f;
        tag = "[BOT]";
        lastPos.set(pos);
    }

    @Override
    public void update(float dt, Game game) {
        if (!alive) { vel.set(0, 0); updateCommon(dt, game); return; }

        perceptionTimer -= dt;
        decisionTimer -= dt;
        nadeCooldown -= dt;
        repathTimer -= dt;
        strafeTimer -= dt;
        aimJitterT += dt;

        if (perceptionTimer <= 0) {
            perceptionTimer = 0.07f + MathUtil.rand(0.05f);
            perceive(game);
        }
        if (decisionTimer <= 0) {
            decisionTimer = 0.20f + MathUtil.rand(0.18f);
            decide(game);
        }

        act(dt, game);
        updateCommon(dt, game);
    }

    // ------------------------------------------------------------ Восприятие
    private void perceive(Game game) {
        Actor best = null;
        float bestScore = Float.MAX_VALUE;
        for (Actor a : game.actors) {
            if (a == this || !a.alive) continue;
            if (!game.areEnemies(this, a)) continue;
            if (!game.canSee(this, a)) continue;
            float d = pos.distSq(a.pos);
            float score = d;
            if (a == target) score *= 0.55f;              // не дёргаемся между целями
            if (a.hp < 40) score *= 0.7f;
            if (a instanceof Player) score *= 0.85f;
            if (score < bestScore) { bestScore = score; best = a; }
        }

        if (best != null) {
            if (target != best) reaction = game.settings.difficulty.reaction * MathUtil.rand(0.7f, 1.35f);
            target = best;
            lastKnown.set(best.pos);
            hasLastKnown = true;
            lostTimer = 0;
        } else {
            if (target != null) {
                lostTimer += 0.1f;
                if (lostTimer > 2.5f) target = null;
            }
            // Слух: громкие шаги и выстрелы
            for (Actor a : game.actors) {
                if (a == this || !a.alive || !game.areEnemies(this, a)) continue;
                float d = pos.dist(a.pos);
                float hearRange = 300f + a.noiseLevel * 900f;
                if (game.map.lineOfSight(pos, a.pos)) hearRange *= 1.25f;
                if (a.noiseLevel > 0.12f && d < hearRange) {
                    lastKnown.set(a.pos.x + MathUtil.gauss(45), a.pos.y + MathUtil.gauss(45));
                    hasLastKnown = true;
                }
            }
        }
    }

    // ------------------------------------------------------------- Решения
    private void decide(Game game) {
        if (flash > 0.75f) { state = State.BLIND; return; }

        // Горим — надо выбежать из огня
        if (game.inFire(pos) || burn > 0.6f) { state = State.FLEE_FIRE; return; }

        Weapon w = currentWeapon();
        if (w.isEmptyCompletely() && isHoldingGun()) {
            if (primary != null && slot == SLOT_PRIMARY && secondary != null && !secondary.isEmptyCompletely()) selectSlot(SLOT_SECONDARY);
            else if (secondary == null || secondary.isEmptyCompletely()) selectSlot(SLOT_KNIFE);
        } else if (w.needsReload() && !w.reloading) {
            boolean safe = target == null || pos.dist(target.pos) > 420;
            if (safe || w.ammo == 0) w.startReload();
        }

        if (target != null) {
            float d = pos.dist(target.pos);
            boolean lowHp = hp < 38;
            boolean outgunned = currentType().cat == WeaponType.Cat.MELEE && d > 90;
            if ((lowHp && patience > 0.35f) || outgunned) state = State.COVER;
            else state = State.ENGAGE;
            return;
        }

        if (hasLastKnown && pos.distSq(lastKnown) > 90 * 90 && patience < 0.85f) {
            state = State.INVESTIGATE;
            return;
        }

        Vec2 obj = game.mode.botObjective(game, this);
        if (obj != null) {
            goal.set(obj);
            state = State.OBJECTIVE;
        } else {
            state = State.ADVANCE;
        }
    }

    // ---------------------------------------------------------------- Действие
    private void act(float dt, Game game) {
        Vec2 desired = new Vec2();
        boolean wantShoot = false;
        walking = false;
        crouching = false;

        if (game.movementLocked) {
            vel.set(0, 0);
            moving = false;
            aim += (float) Math.sin(aimJitterT * 1.1f) * dt * 0.6f;
            return;
        }

        switch (state) {
            case BLIND -> {
                desired.set(vel).mul(0.2f);
                aim += MathUtil.gauss(2.2f) * dt;
                if (target != null && MathUtil.chance(dt * 0.9f) && isHoldingGun()) wantShoot = true;
            }
            case FLEE_FIRE -> {
                Vec2 away = game.escapeFireDir(pos);
                desired.set(away).mul(moveSpeed());
                aim = MathUtil.approachAngle(aim, away.angle(), dt * 6f);
                repathTimer = 0;
            }
            case ENGAGE -> {
                wantShoot = combat(dt, game, desired);
            }
            case COVER -> {
                Vec2 spot = findCover(game);
                if (spot != null) navigate(game, spot, dt, desired);
                else wantShoot = combat(dt, game, desired);
                if (target != null) {
                    aimAt(dt, game, target.pos, 0);
                    if (pos.dist(target.pos) < 260) wantShoot = readyToFire(game, target);
                }
            }
            case INVESTIGATE -> {
                navigate(game, lastKnown, dt, desired);
                if (pos.dist(lastKnown) < 70) { hasLastKnown = false; }
                walking = patience > 0.6f;
                lookAlongMotion(dt, desired);
            }
            case OBJECTIVE, ADVANCE -> {
                Vec2 g = state == State.OBJECTIVE ? goal : wanderGoal(game);
                navigate(game, g, dt, desired);
                lookAlongMotion(dt, desired);
                game.mode.botUse(game, this, dt);
            }
            case HOLD -> {
                if (holdSpot != null && pos.dist(holdSpot) > 40) navigate(game, holdSpot, dt, desired);
                else aim += (float) Math.sin(aimJitterT * 0.8f) * dt * 0.7f;
            }
        }

        // Уклонение от гранат под ногами
        Vec2 danger = game.nearestDangerDir(pos, 170f);
        if (danger != null) desired.addScaled(danger, moveSpeed() * 1.2f);

        // Движение
        desired.limit(moveSpeed());
        vel.x = MathUtil.damp(vel.x, desired.x, 0.0006f, dt * 12f);
        vel.y = MathUtil.damp(vel.y, desired.y, 0.0006f, dt * 12f);
        moving = vel.lenSq() > 800;

        Vec2 before = pos.cpy();
        Tile floor = game.map.tileAtWorld(pos.x, pos.y);
        float surf = floor.mat == Tile.Mat.WATER ? 0.62f : 1f;
        game.map.moveCircle(pos, vel.x * dt * surf, vel.y * dt * surf, radius);
        game.pushApart(this);

        // Антизастревание
        if (desired.lenSq() > 100 && pos.dist(before) < 6f * dt) {
            stuckTimer += dt;
            if (stuckTimer > 0.65f) {
                stuckTimer = 0;
                path.clear();
                repathTimer = 0;
                strafeDir = -strafeDir;
                vel.set(Vec2.fromAngle(MathUtil.rand(MathUtil.TAU), moveSpeed()));
            }
        } else stuckTimer = Math.max(0, stuckTimer - dt);

        if (wantShoot) tryShoot(game);
        tryGrenade(game);
    }

    private void lookAlongMotion(float dt, Vec2 desired) {
        if (desired.lenSq() > 400) aim = MathUtil.approachAngle(aim, desired.angle(), dt * 5.5f);
        else aim += (float) Math.sin(aimJitterT * 1.3f) * dt * 0.8f;
    }

    // ---------------------------------------------------------------- Бой
    private boolean combat(float dt, Game game, Vec2 desired) {
        if (target == null) return false;
        float d = pos.dist(target.pos);
        aimAt(dt, game, target.pos, d);

        // Позиционирование: держим дистанцию по типу оружия
        float ideal = switch (currentType().cat) {
            case SNIPER -> 700f;
            case SHOTGUN -> 170f;
            case MELEE -> 40f;
            case SMG -> 300f;
            default -> 430f;
        };
        Vec2 toTarget = target.pos.cpy().sub(pos).norm();
        float diff = d - ideal;
        if (Math.abs(diff) > 90) desired.addScaled(toTarget, Math.signum(diff) * moveSpeed() * 0.8f * aggression);

        // Стрейф
        if (strafeTimer <= 0) { strafeTimer = MathUtil.rand(0.45f, 1.3f); strafeDir = MathUtil.chance(0.5f) ? 1 : -1; }
        Vec2 side = new Vec2(-toTarget.y, toTarget.x).mul(strafeDir * moveSpeed() * 0.55f);
        desired.add(side);

        if (currentType().cat == WeaponType.Cat.SNIPER && d > 350) {
            crouching = true;
            desired.mul(0.25f);
        }
        return readyToFire(game, target);
    }

    private void aimAt(float dt, Game game, Vec2 p, float dist) {
        // Упреждение по скорости цели
        Vec2 aimPoint = p.cpy();
        if (target != null && dist > 120) {
            float lead = MathUtil.clamp(dist / 1400f, 0, 0.25f) * skill;
            aimPoint.addScaled(target.vel, lead);
        }
        float err = (1f - skill) * 0.30f + (flash * 0.5f) + (burn > 0 ? 0.08f : 0);
        if (moving) err += 0.05f;
        float jitter = (float) (Math.sin(aimJitterT * 7.3f) * 0.5 + Math.sin(aimJitterT * 3.1f) * 0.5) * err;
        float want = (float) Math.atan2(aimPoint.y - pos.y, aimPoint.x - pos.x) + jitter;
        float turn = (2.6f + skill * 7.5f) * dt;
        aim = MathUtil.approachAngle(aim, want, turn);
    }

    private boolean readyToFire(Game game, Actor t) {
        if (!isHoldingGun() && slot != SLOT_KNIFE) return false;
        reaction -= 1f / 60f;
        if (reaction > 0) return false;
        if (!game.canSee(this, t)) return false;
        float angErr = Math.abs(MathUtil.angleDiff(aim, (float) Math.atan2(t.pos.y - pos.y, t.pos.x - pos.x)));
        float tolerance = 0.05f + 0.10f * (1f - skill);
        if (angErr > tolerance) return false;

        WeaponType wt = currentType();
        if (wt.automatic) {
            burstTimer -= 1f / 60f;
            burstPause -= 1f / 60f;
            if (burstPause > 0) return false;
            if (burstTimer <= 0) {
                float d = pos.dist(t.pos);
                burstTimer = d > 600 ? MathUtil.rand(0.10f, 0.22f) : MathUtil.rand(0.28f, 0.7f);
                burstPause = burstTimer + (d > 600 ? MathUtil.rand(0.28f, 0.5f) : MathUtil.rand(0.12f, 0.3f));
                return true;
            }
            return true;
        }
        return true;
    }

    // ---------------------------------------------------------- Навигация
    private void navigate(Game game, Vec2 dest, float dt, Vec2 desired) {
        if (repathTimer <= 0 || path.isEmpty()) {
            repathTimer = MathUtil.rand(0.6f, 1.2f);
            if (!path.isEmpty() && pathIdx < path.size() && path.get(path.size() - 1).dist(dest) < 60) {
                // цель прежняя — не пересчитываем
            } else {
                path = game.pathfinder.findPath(pos, dest);
                pathIdx = 0;
            }
        }
        if (path.isEmpty()) {
            desired.set(dest.cpy().sub(pos).norm().mul(moveSpeed()));
            return;
        }
        while (pathIdx < path.size() && pos.dist(path.get(pathIdx)) < 34) pathIdx++;
        if (pathIdx >= path.size()) {
            path.clear();
            desired.set(dest.cpy().sub(pos));
            if (desired.lenSq() > 400) desired.norm().mul(moveSpeed());
            return;
        }
        Vec2 wp = path.get(pathIdx);
        desired.set(wp.cpy().sub(pos).norm().mul(moveSpeed()));
    }

    private Vec2 wanderTarget;
    private float wanderTimer;

    private Vec2 wanderGoal(Game game) {
        wanderTimer -= 1f / 60f;
        if (wanderTarget == null || wanderTimer <= 0 || pos.dist(wanderTarget) < 70) {
            wanderTimer = MathUtil.rand(5f, 12f);
            wanderTarget = game.map.randomOpenCell();
        }
        return wanderTarget;
    }

    /** Ищет точку рядом, откуда враг не видит. */
    private Vec2 findCover(Game game) {
        Vec2 threat = target != null ? target.pos : (hasLastKnown ? lastKnown : null);
        if (threat == null) return null;
        Vec2 best = null;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < 22; i++) {
            float ang = MathUtil.rand(MathUtil.TAU);
            float rad = MathUtil.rand(70f, 330f);
            Vec2 c = Vec2.fromAngle(ang, rad).add(pos);
            if (c.x < 20 || c.y < 20 || c.x > game.map.pixelW() - 20 || c.y > game.map.pixelH() - 20) continue;
            if (game.map.circleHitsSolid(c.x, c.y, radius + 3)) continue;
            if (game.map.lineOfSight(c, threat)) continue;
            if (game.inFire(c)) continue;
            float score = c.dist(pos) + c.dist(threat) * 0.25f;
            if (score < bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    // ---------------------------------------------------------- Гранаты
    private void tryGrenade(Game game) {
        if (nadeCooldown > 0 || totalNades() == 0) return;
        if (flash > 0.4f) return;
        float tactics = game.settings.difficulty.tactics;
        if (!MathUtil.chance(tactics * 0.02f)) return;

        Vec2 spot = null;
        int kind = -1;
        Actor visible = target;

        if (visible != null && game.canSee(this, visible)) {
            float d = pos.dist(visible.pos);
            if (d > 200 && d < 850) {
                if (nades[NADE_HE] > 0 && MathUtil.chance(0.45f)) { kind = NADE_HE; spot = visible.pos.cpy(); }
                else if (nades[NADE_MOLOTOV] > 0 && MathUtil.chance(0.35f)) { kind = NADE_MOLOTOV; spot = visible.pos.cpy(); }
                else if (nades[NADE_FLASH] > 0) { kind = NADE_FLASH; spot = visible.pos.cpy(); }
            } else if (d >= 850 && nades[NADE_SMOKE] > 0 && hp < 60) {
                kind = NADE_SMOKE; spot = pos.cpy().addScaled(Vec2.fromAngle(aim), 260);
            }
        } else if (hasLastKnown) {
            float d = pos.dist(lastKnown);
            if (d > 180 && d < 900) {
                if (nades[NADE_FLASH] > 0 && MathUtil.chance(0.5f)) { kind = NADE_FLASH; spot = lastKnown.cpy(); }
                else if (nades[NADE_HE] > 0 && MathUtil.chance(0.4f)) { kind = NADE_HE; spot = lastKnown.cpy(); }
                else if (nades[NADE_MOLOTOV] > 0) { kind = NADE_MOLOTOV; spot = lastKnown.cpy(); }
            }
        }

        if (kind < 0 || spot == null || nades[kind] <= 0) return;
        // Не бросаем в стену рядом с собой
        if (!game.map.lineOfSight(pos, spot) && pos.dist(spot) < 200) return;
        // Не жарим своих
        for (Actor a : game.actors) {
            if (a.alive && !game.areEnemies(this, a) && a.pos.dist(spot) < 110) return;
        }
        float dist = pos.dist(spot);
        float power = MathUtil.clamp(dist / 520f, 0.42f, 1.3f);
        float prevAim = aim;
        aim = (float) Math.atan2(spot.y - pos.y, spot.x - pos.x);
        game.throwGrenade(this, kind, power, kind == NADE_HE ? MathUtil.rand(0.3f, 0.9f) : 0f);
        aim = prevAim;
        nades[kind]--;
        nadeCooldown = MathUtil.rand(7f, 18f) / (0.4f + tactics);
    }

    /** Закупка снаряжения на старте раунда. */
    public void autoBuy(Game game) {
        List<WeaponType> options = new ArrayList<>();
        for (WeaponType wt : WeaponType.buyable(team)) {
            if (wt.price <= money && wt.cat != WeaponType.Cat.PISTOL) options.add(wt);
        }
        float greed = aggression;
        if (primary == null || MathUtil.chance(0.35f)) {
            WeaponType pick = null;
            // сортируем по цене и берём что-то в верхней половине доступного
            options.sort((a, b) -> Integer.compare(b.price, a.price));
            if (!options.isEmpty()) {
                int idx = (int) (MathUtil.rand(0f, Math.min(options.size(), 4f) * (0.4f + greed * 0.6f)));
                pick = options.get(MathUtil.clamp(idx, 0, options.size() - 1));
            }
            if (pick != null && pick.price <= money) {
                money -= pick.price;
                giveWeapon(pick);
            }
        }
        if (secondary == null) {
            WeaponType p = team == TEAM_T ? WeaponType.GLOCK : WeaponType.USP;
            giveWeapon(p);
            slot = primary != null ? SLOT_PRIMARY : SLOT_SECONDARY;
        }
        if (armor < 50 && money >= 650 && MathUtil.chance(0.8f)) { money -= 650; armor = 100; helmet = true; }
        else if (armor < 50 && money >= 350) { money -= 350; armor = 100; }

        for (int attempt = 0; attempt < 8 && money >= 200; attempt++) {
            if (!MathUtil.chance(0.6f)) break;
            int kind = MathUtil.randInt(4);
            if (nades[kind] >= (kind == NADE_FLASH ? 2 : 1)) continue;
            int price = NADE_PRICE[kind];
            if (price > money) continue;
            money -= price;
            nades[kind]++;
        }
        if (team == TEAM_CT && !hasKit && money >= 400 && MathUtil.chance(0.5f)) { money -= 400; hasKit = true; }
        nadeSel = NADE_HE;
        for (int i = 0; i < 4; i++) if (nades[i] > 0) { nadeSel = i; break; }
    }
}
