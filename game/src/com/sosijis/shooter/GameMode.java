package com.sosijis.shooter;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Базовый режим: фазы раунда, счёт, задачи для ботов и обработка клавиши «Использовать». */
public abstract class GameMode {

    public enum Type {
        DEFUSE("Закладка бомбы", "Террористы закладывают C4 на точке A или B, спецназ мешает и разминирует."),
        HOSTAGE("Спасение заложников", "Спецназ выводит заложников в зону эвакуации, террористы их удерживают."),
        TEAM_DEATHMATCH("Командный бой", "Две команды, бесконечные респавны, играем до лимита фрагов."),
        DEATHMATCH("Каждый за себя", "Все против всех. Оружие выдаётся случайно после каждого убийства."),
        GUNGAME("Гонка вооружений", "За каждое убийство — следующее оружие. Побеждает дошедший до ножа."),
        ZOMBIE("Оборона от волн", "Спецназ держит оборону против волн заражённых. Каждая волна сильнее.");

        public final String label, desc;
        Type(String label, String desc) { this.label = label; this.desc = desc; }

        public GameMode create() {
            return switch (this) {
                case DEFUSE -> new DefuseMode();
                case HOSTAGE -> new HostageMode();
                case TEAM_DEATHMATCH -> new TeamDeathmatchMode();
                case DEATHMATCH -> new DeathmatchMode();
                case GUNGAME -> new GunGameMode();
                case ZOMBIE -> new ZombieMode();
            };
        }
    }

    public enum Phase { FREEZE, LIVE, ENDED, MATCH_OVER }

    public Phase phase = Phase.FREEZE;
    public float phaseTimer;
    public int round;
    public int scoreT, scoreCT;
    public String subtitle = "";
    public int lastWinner = -1;

    public float freezeTime = 6f;
    public float endTime = 5f;

    public abstract Type type();

    public boolean roundBased() { return true; }
    public boolean respawnEnabled() { return false; }
    public boolean ffa() { return false; }
    public boolean buyEnabled() { return true; }

    public void start(Game g) {
        round = 0;
        scoreT = scoreCT = 0;
        beginRound(g);
    }

    public void beginRound(Game g) {
        round++;
        phase = Phase.FREEZE;
        phaseTimer = roundBased() ? freezeTime : 1.5f;
        g.resetRound(true);
        onRoundStart(g);
        g.announce(roundBased() ? "Раунд " + round : type().label, new Color(0xFFD27F));
    }

    protected void onRoundStart(Game g) { }

    public void update(Game g, float dt) {
        phaseTimer -= dt;
        switch (phase) {
            case FREEZE -> {
                if (phaseTimer <= 0) {
                    phase = Phase.LIVE;
                    phaseTimer = g.settings.roundTimeSec;
                    g.announce("В бой!", new Color(0x8FE08A));
                    onLiveStart(g);
                }
            }
            case LIVE -> {
                onLiveUpdate(g, dt);
                if (phaseTimer <= 0) onTimeExpired(g);
            }
            case ENDED -> {
                if (phaseTimer <= 0) {
                    if (matchOver()) {
                        phase = Phase.MATCH_OVER;
                        phaseTimer = 999999;
                        g.announce(matchResultText(), new Color(0xFFE08A));
                    } else beginRound(g);
                }
            }
            case MATCH_OVER -> { }
        }
    }

    protected void onLiveStart(Game g) { }
    protected abstract void onLiveUpdate(Game g, float dt);
    protected void onTimeExpired(Game g) { endRound(g, Actor.TEAM_CT, "Время вышло"); }

    public void endRound(Game g, int winner, String reason) {
        if (phase == Phase.ENDED || phase == Phase.MATCH_OVER) return;
        phase = Phase.ENDED;
        phaseTimer = endTime;
        lastWinner = winner;
        if (winner == Actor.TEAM_T) scoreT++;
        else if (winner == Actor.TEAM_CT) scoreCT++;
        subtitle = reason;
        String who = winner == Actor.TEAM_T ? "Террористы" : (winner == Actor.TEAM_CT ? "Спецназ" : "Ничья");
        g.announce(who + " побеждают — " + reason, winner == Actor.TEAM_T ? new Color(0xE0A05A) : new Color(0x7FB6E8));
        g.onRoundEnd(winner);
        awardMoney(g, winner);
    }

    protected void awardMoney(Game g, int winner) {
        for (Actor a : g.actors) {
            int gain = a.team == winner ? 3250 : 1400;
            if (a.team != winner && a.alive) gain += 500;
            a.money = Math.min(16000, a.money + gain);
        }
    }

    public boolean matchOver() {
        int limit = Math.max(1, currentScoreLimit());
        return scoreT >= limit || scoreCT >= limit;
    }

    protected int currentScoreLimit() { return 10; }

    public String matchResultText() {
        if (scoreT > scoreCT) return "Матч окончен — победа террористов " + scoreT + ":" + scoreCT;
        if (scoreCT > scoreT) return "Матч окончен — победа спецназа " + scoreCT + ":" + scoreT;
        return "Матч окончен — ничья";
    }

    public abstract void onKill(Game g, Actor victim, Actor killer);

    public void handleUse(Game g, Player p, boolean holding, float dt) { }
    public void botUse(Game g, Bot b, float dt) { }
    public Vec2 botObjective(Game g, Bot b) { return null; }

    public boolean buyAllowed(Game g, Actor a) {
        if (!buyEnabled() || !a.alive) return false;
        if (roundBased()) {
            // В раунде закупка открыта на подготовке и первые 15 секунд боя
            boolean window = phase == Phase.FREEZE
                    || (phase == Phase.LIVE && phaseTimer > g.settings.roundTimeSec - 15);
            if (!window) return false;
        }
        Tile t = g.map.tileAtWorld(a.pos.x, a.pos.y);
        return (a.team == Actor.TEAM_T && t == Tile.BUY_T) || (a.team == Actor.TEAM_CT && t == Tile.BUY_CT);
    }

    public String objectiveText(Game g) { return type().desc; }

    public float timeLeft() { return Math.max(0, phaseTimer); }

    /** Проверяет, не закончился ли раунд из-за истребления команды. */
    protected boolean checkElimination(Game g) {
        if (phase != Phase.LIVE) return false;
        int t = g.aliveCount(Actor.TEAM_T), ct = g.aliveCount(Actor.TEAM_CT);
        if (t == 0 && ct == 0) { endRound(g, -1, "Взаимное уничтожение"); return true; }
        if (t == 0) { endRound(g, Actor.TEAM_CT, "Террористы уничтожены"); return true; }
        if (ct == 0) { endRound(g, Actor.TEAM_T, "Спецназ уничтожен"); return true; }
        return false;
    }

    // =============================================================== Закладка
    public static class DefuseMode extends GameMode {
        @Override public Type type() { return Type.DEFUSE; }
        @Override protected int currentScoreLimit() { return 10; }

        @Override protected void onRoundStart(Game g) {
            g.bomb = new Bomb();
            List<Actor> ts = g.livingActors(Actor.TEAM_T);
            for (Actor a : g.actors) a.hasBomb = false;
            if (!ts.isEmpty()) {
                Actor carrier = g.player != null && g.player.team == Actor.TEAM_T && MathUtil.chance(0.45f)
                        ? g.player : ts.get(MathUtil.randInt(ts.size()));
                carrier.hasBomb = true;
            }
        }

        @Override protected void onLiveUpdate(Game g, float dt) {
            if (g.bomb != null && g.bomb.planted) {
                g.bomb.update(dt, g);
                if (g.bomb.exploded) { endRound(g, Actor.TEAM_T, "Бомба взорвалась"); return; }
                if (g.bomb.defused) { endRound(g, Actor.TEAM_CT, "Бомба обезврежена"); return; }
                if (g.aliveCount(Actor.TEAM_CT) == 0) { endRound(g, Actor.TEAM_T, "Спецназ уничтожен"); return; }
                return;
            }
            checkElimination(g);
        }

        @Override protected void onTimeExpired(Game g) {
            if (g.bomb != null && g.bomb.planted) return;
            endRound(g, Actor.TEAM_CT, "Время вышло");
        }

        @Override public void onKill(Game g, Actor victim, Actor killer) {
            if (victim.hasBomb) {
                victim.hasBomb = false;
                g.dropBombAt(victim.pos);
            }
        }

        @Override public void handleUse(Game g, Player p, boolean holding, float dt) {
            interact(g, p, holding, dt);
        }

        @Override public void botUse(Game g, Bot b, float dt) {
            boolean want = false;
            if (b.team == Actor.TEAM_T && b.hasBomb && inSite(g, b.pos)) want = g.aliveCount(Actor.TEAM_CT) >= 0;
            if (b.team == Actor.TEAM_CT && g.bomb != null && g.bomb.planted && b.pos.dist(g.bomb.pos) < 46) want = true;
            if (b.team == Actor.TEAM_T && !b.hasBomb && g.looseBomb != null && b.pos.dist(g.looseBomb) < 40) want = true;
            interact(g, b, want, dt);
        }

        private boolean inSite(Game g, Vec2 p) {
            Tile t = g.map.tileAtWorld(p.x, p.y);
            return t == Tile.SITE_A || t == Tile.SITE_B;
        }

        private void interact(Game g, Actor a, boolean holding, float dt) {
            Bomb bomb = g.bomb;
            if (bomb == null) return;

            // Подобрать брошенную C4
            if (a.team == Actor.TEAM_T && !bomb.planted && g.looseBomb != null && a.pos.dist(g.looseBomb) < 34) {
                a.hasBomb = true;
                g.looseBomb = null;
                g.sfx.playAt("pickup", a.pos, 0.6f, 1f);
            }

            if (a.team == Actor.TEAM_T && a.hasBomb && !bomb.planted) {
                boolean can = inSite(g, a.pos) && holding && a.vel.len() < 40;
                a.planting = can;
                if (can) {
                    a.plantProgress += dt / 3.2f;
                    if (a.plantProgress >= 1f) {
                        bomb.plant(a.pos, a);
                        a.hasBomb = false;
                        a.plantProgress = 0;
                        a.planting = false;
                        a.money += 300;
                        g.sfx.playAt("bomb_plant", a.pos, 0.9f, 1f);
                        g.announce("Бомба заложена!", new Color(0xFF8A5A));
                        phaseTimer = Math.max(phaseTimer, 41f);
                        for (Actor o : g.actors) if (o instanceof Bot bb) { bb.path.clear(); bb.repathTimer = 0; }
                    }
                } else a.plantProgress = Math.max(0, a.plantProgress - dt * 0.8f);
            }

            if (a.team == Actor.TEAM_CT && bomb.planted && !bomb.defused) {
                boolean can = a.pos.dist(bomb.pos) < 46 && holding && a.vel.len() < 40;
                a.defusing = can;
                if (can) {
                    float speed = a.hasKit ? 1f / 5f : 1f / 10f;
                    a.defuseProgress += dt * speed;
                    bomb.defuseProgress = a.defuseProgress;
                    if (a.defuseProgress >= 1f) {
                        bomb.defused = true;
                        a.money += 300;
                        g.sfx.playAt("bomb_defuse", a.pos, 0.9f, 1f);
                    }
                } else {
                    a.defuseProgress = Math.max(0, a.defuseProgress - dt * 0.6f);
                    bomb.defuseProgress = a.defuseProgress;
                }
            }
        }

        @Override public Vec2 botObjective(Game g, Bot b) {
            Bomb bomb = g.bomb;
            if (bomb == null) return null;
            if (b.team == Actor.TEAM_T) {
                if (bomb.planted) {
                    // охраняем точку
                    return bomb.pos.cpy().add(MathUtil.gauss(120f), MathUtil.gauss(120f));
                }
                if (b.hasBomb) return g.nearestSite(b.pos);
                if (g.looseBomb != null) return g.looseBomb.cpy();
                Actor carrier = g.bombCarrier();
                if (carrier != null) return carrier.pos.cpy().add(MathUtil.gauss(90f), MathUtil.gauss(90f));
                return g.nearestSite(b.pos);
            } else {
                if (bomb.planted) return bomb.pos.cpy();
                Vec2 site = (b.hashCode() % 2 == 0) ? g.siteCenter(true) : g.siteCenter(false);
                return site;
            }
        }

        @Override public String objectiveText(Game g) {
            if (g.bomb != null && g.bomb.planted) return "C4 заложена — до взрыва " + String.format("%.0f", g.bomb.timer) + " с";
            return g.player != null && g.player.hasBomb ? "У вас C4 — заложите её на точке A или B (E)" : Type.DEFUSE.desc;
        }
    }

    // ============================================================== Заложники
    public static class HostageMode extends GameMode {
        @Override public Type type() { return Type.HOSTAGE; }

        @Override protected void onRoundStart(Game g) { g.spawnHostages(4); }

        @Override protected void onLiveUpdate(Game g, float dt) {
            int rescued = 0, aliveH = 0;
            for (Hostage hst : g.hostages) {
                hst.update(dt, g);
                if (hst.rescued) rescued++;
                else if (hst.alive) aliveH++;
                if (!hst.rescued && hst.alive && hst.follower != null) {
                    Tile t = g.map.tileAtWorld(hst.pos.x, hst.pos.y);
                    if (t == Tile.RESCUE) {
                        hst.rescued = true;
                        hst.follower.money += 1000;
                        g.sfx.play("pickup", 0.7f, 1.2f);
                        g.announce("Заложник спасён (" + (rescued + 1) + "/" + g.hostages.size() + ")", new Color(0x8FE08A));
                    }
                }
            }
            if (rescued >= Math.max(1, g.hostages.size() - 1)) { endRound(g, Actor.TEAM_CT, "Заложники спасены"); return; }
            if (aliveH == 0 && rescued == 0) { endRound(g, Actor.TEAM_T, "Все заложники погибли"); return; }
            checkElimination(g);
        }

        @Override protected void onTimeExpired(Game g) { endRound(g, Actor.TEAM_T, "Время вышло"); }

        @Override public void onKill(Game g, Actor victim, Actor killer) {
            for (Hostage hst : g.hostages) if (hst.follower == victim) hst.follower = null;
        }

        @Override public void handleUse(Game g, Player p, boolean holding, float dt) { grab(g, p, holding); }

        @Override public void botUse(Game g, Bot b, float dt) {
            if (b.team != Actor.TEAM_CT) return;
            grab(g, b, true);
        }

        private void grab(Game g, Actor a, boolean holding) {
            if (a.team != Actor.TEAM_CT || !holding) return;
            for (Hostage hst : g.hostages) {
                if (!hst.alive || hst.rescued || hst.follower != null) continue;
                if (hst.pos.dist(a.pos) < 46) {
                    hst.follower = a;
                    g.sfx.playAt("pickup", a.pos, 0.6f, 0.9f);
                    g.announce("Заложник следует за вами", new Color(0x9FD0E8));
                    break;
                }
            }
        }

        @Override public Vec2 botObjective(Game g, Bot b) {
            if (b.team == Actor.TEAM_CT) {
                for (Hostage hst : g.hostages) if (hst.follower == b) return g.rescueCenter();
                Hostage free = null;
                float bd = Float.MAX_VALUE;
                for (Hostage hst : g.hostages) {
                    if (!hst.alive || hst.rescued || hst.follower != null) continue;
                    float d = hst.pos.distSq(b.pos);
                    if (d < bd) { bd = d; free = hst; }
                }
                if (free != null) return free.pos.cpy();
                return g.rescueCenter();
            }
            // Террористы охраняют заложников
            for (Hostage hst : g.hostages) {
                if (hst.alive && !hst.rescued)
                    return hst.pos.cpy().add(MathUtil.gauss(130f), MathUtil.gauss(130f));
            }
            return null;
        }
    }

    // ============================================================ Командный бой
    public static class TeamDeathmatchMode extends GameMode {
        @Override public Type type() { return Type.TEAM_DEATHMATCH; }
        @Override public boolean roundBased() { return false; }
        @Override public boolean respawnEnabled() { return true; }
        @Override public boolean buyEnabled() { return true; }

        @Override protected void onRoundStart(Game g) {
            for (Actor a : g.actors) { a.money = 16000; g.autoGear(a); }
        }

        @Override protected void onLiveUpdate(Game g, float dt) {
            int limit = g.settings.scoreLimit * 5;
            if (scoreT >= limit) { endMatch(g, Actor.TEAM_T); }
            else if (scoreCT >= limit) { endMatch(g, Actor.TEAM_CT); }
        }

        private void endMatch(Game g, int team) {
            phase = Phase.MATCH_OVER;
            phaseTimer = 999999;
            lastWinner = team;
            g.announce((team == Actor.TEAM_T ? "Террористы" : "Спецназ") + " побеждают!", new Color(0xFFE08A));
        }

        @Override protected void onTimeExpired(Game g) {
            phase = Phase.MATCH_OVER;
            lastWinner = scoreT > scoreCT ? Actor.TEAM_T : Actor.TEAM_CT;
            g.announce("Время вышло — " + (scoreT > scoreCT ? "победа террористов" : "победа спецназа"), new Color(0xFFE08A));
        }

        @Override public void onKill(Game g, Actor victim, Actor killer) {
            if (killer == null) return;
            if (killer.team == Actor.TEAM_T) scoreT++;
            else if (killer.team == Actor.TEAM_CT) scoreCT++;
        }

        @Override public boolean matchOver() { return phase == Phase.MATCH_OVER; }
        @Override public String objectiveText(Game g) {
            return "До победы: Т " + (g.settings.scoreLimit * 5 - scoreT) + " / СН " + (g.settings.scoreLimit * 5 - scoreCT);
        }
    }

    // ========================================================== Каждый за себя
    public static class DeathmatchMode extends GameMode {
        @Override public Type type() { return Type.DEATHMATCH; }
        @Override public boolean roundBased() { return false; }
        @Override public boolean respawnEnabled() { return true; }
        @Override public boolean ffa() { return true; }
        @Override public boolean buyEnabled() { return false; }

        @Override protected void onRoundStart(Game g) {
            for (Actor a : g.actors) g.giveRandomLoadout(a);
        }

        @Override protected void onLiveUpdate(Game g, float dt) {
            int limit = g.settings.scoreLimit * 3;
            for (Actor a : g.actors) {
                if (a.kills >= limit) {
                    phase = Phase.MATCH_OVER;
                    g.announce(a.name + " побеждает! (" + a.kills + " фрагов)", new Color(0xFFE08A));
                    return;
                }
            }
        }

        @Override protected void onTimeExpired(Game g) {
            phase = Phase.MATCH_OVER;
            g.announce("Время вышло", new Color(0xFFE08A));
        }

        @Override public void onKill(Game g, Actor victim, Actor killer) {
            g.giveRandomLoadout(victim);
            if (killer != null && killer != victim) {
                killer.hp = Math.min(killer.maxHp, killer.hp + 25);
                g.giveRandomLoadout(killer);
            }
        }

        @Override public boolean matchOver() { return phase == Phase.MATCH_OVER; }
        @Override public String objectiveText(Game g) {
            return "Все против всех — до " + (g.settings.scoreLimit * 3) + " фрагов";
        }
    }

    // ========================================================= Гонка вооружений
    public static class GunGameMode extends GameMode {
        public static final WeaponType[] LADDER = {
                WeaponType.GLOCK, WeaponType.P250, WeaponType.DEAGLE, WeaponType.MAC10, WeaponType.MP9,
                WeaponType.UMP45, WeaponType.P90, WeaponType.NOVA, WeaponType.XM1014, WeaponType.GALIL,
                WeaponType.FAMAS, WeaponType.AK47, WeaponType.M4A4, WeaponType.AUG, WeaponType.SG553,
                WeaponType.SCOUT, WeaponType.AWP, WeaponType.M249, WeaponType.KNIFE
        };
        private final java.util.Map<Actor, Integer> level = new java.util.HashMap<>();

        @Override public Type type() { return Type.GUNGAME; }
        @Override public boolean roundBased() { return false; }
        @Override public boolean respawnEnabled() { return true; }
        @Override public boolean ffa() { return true; }
        @Override public boolean buyEnabled() { return false; }

        public int levelOf(Actor a) { return level.getOrDefault(a, 0); }

        @Override protected void onRoundStart(Game g) {
            level.clear();
            for (Actor a : g.actors) { level.put(a, 0); equip(a); }
        }

        private void equip(Actor a) {
            int lv = MathUtil.clamp(levelOf(a), 0, LADDER.length - 1);
            WeaponType wt = LADDER[lv];
            a.primary = null; a.secondary = null;
            a.nades[0] = a.nades[1] = a.nades[2] = a.nades[3] = 0;
            if (wt == WeaponType.KNIFE) { a.slot = Actor.SLOT_KNIFE; }
            else { a.giveWeapon(wt); }
            a.armor = 100;
        }

        @Override protected void onLiveUpdate(Game g, float dt) { }

        @Override protected void onTimeExpired(Game g) {
            phase = Phase.MATCH_OVER;
            g.announce("Время вышло", new Color(0xFFE08A));
        }

        @Override public void onKill(Game g, Actor victim, Actor killer) {
            equip(victim);
            if (killer == null || killer == victim) return;
            int lv = levelOf(killer) + 1;
            level.put(killer, lv);
            if (lv >= LADDER.length) {
                phase = Phase.MATCH_OVER;
                g.announce(killer.name + " прошёл всю гонку вооружений!", new Color(0xFFE08A));
                return;
            }
            equip(killer);
            killer.hp = Math.min(killer.maxHp, killer.hp + 20);
            g.sfx.play("buy", 0.5f, 1.2f);
            g.announce(killer.name + ": уровень " + (lv + 1) + "/" + LADDER.length + " — " + LADDER[lv].label,
                    new Color(0xBFD4FF));
        }

        @Override public boolean matchOver() { return phase == Phase.MATCH_OVER; }
        @Override public String objectiveText(Game g) {
            int lv = g.player == null ? 0 : levelOf(g.player);
            return "Уровень " + (lv + 1) + " из " + LADDER.length + " — " + LADDER[Math.min(lv, LADDER.length - 1)].label;
        }
    }

    // ============================================================ Волны зомби
    public static class ZombieMode extends GameMode {
        public int wave = 0;
        private float spawnTimer;
        private int pendingSpawns;

        @Override public Type type() { return Type.ZOMBIE; }
        @Override public boolean roundBased() { return true; }
        @Override public boolean respawnEnabled() { return false; }
        @Override public boolean buyEnabled() { return true; }
        @Override protected int currentScoreLimit() { return 99; }

        @Override protected void onRoundStart(Game g) {
            wave = round;
            g.removeZombies();
            pendingSpawns = 4 + wave * 2;
            spawnTimer = 0;
            freezeTime = 8f;
            for (Actor a : g.actors) {
                if (a.team == Actor.TEAM_CT) a.money = Math.min(16000, a.money + 1200 + wave * 250);
            }
            subtitle = "Волна " + wave;
        }

        @Override protected void onLiveUpdate(Game g, float dt) {
            spawnTimer -= dt;
            if (pendingSpawns > 0 && spawnTimer <= 0) {
                spawnTimer = MathUtil.rand(0.6f, 1.8f);
                int batch = Math.min(pendingSpawns, 1 + MathUtil.randInt(3));
                g.spawnZombies(batch, wave);
                pendingSpawns -= batch;
            }
            if (g.aliveCount(Actor.TEAM_CT) == 0) {
                endRound(g, Actor.TEAM_T, "Оборона пала на волне " + wave);
                return;
            }
            if (pendingSpawns <= 0 && g.aliveCount(Actor.TEAM_ZOMBIE) == 0) {
                endRound(g, Actor.TEAM_CT, "Волна " + wave + " отбита");
            }
        }

        @Override protected void onTimeExpired(Game g) {
            phaseTimer = 60f; // волна не по таймеру
        }

        @Override protected void awardMoney(Game g, int winner) {
            for (Actor a : g.actors) if (a.team == Actor.TEAM_CT) a.money = Math.min(16000, a.money + 2000);
        }

        @Override public void onKill(Game g, Actor victim, Actor killer) {
            if (killer != null && victim.team == Actor.TEAM_ZOMBIE) {
                killer.money = Math.min(16000, killer.money + 120);
                killer.hp = Math.min(killer.maxHp, killer.hp + 3);
            }
        }

        @Override public Vec2 botObjective(Game g, Bot b) {
            if (b.team == Actor.TEAM_ZOMBIE) {
                Actor best = null; float bd = Float.MAX_VALUE;
                for (Actor a : g.actors) {
                    if (!a.alive || a.team == Actor.TEAM_ZOMBIE) continue;
                    float d = a.pos.distSq(b.pos);
                    if (d < bd) { bd = d; best = a; }
                }
                return best != null ? best.pos.cpy() : null;
            }
            return null;
        }

        @Override public String objectiveText(Game g) {
            return "Волна " + wave + " — осталось заражённых: " + (g.aliveCount(Actor.TEAM_ZOMBIE) + pendingSpawns);
        }

        @Override public boolean matchOver() { return lastWinner == Actor.TEAM_T && phase == Phase.ENDED; }
        @Override public String matchResultText() { return "Оборона пала на волне " + wave; }
    }

    public static List<String> allLabels() {
        List<String> out = new ArrayList<>();
        for (Type t : Type.values()) out.add(t.label);
        return out;
    }
}
