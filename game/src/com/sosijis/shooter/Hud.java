package com.sosijis.shooter;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Интерфейс: прицел, состояние бойца, миникарта, киллфид, таблица счёта и меню закупки. */
public class Hud {

    private final Game game;
    private final Settings s;
    private BufferedImage minimap;
    private String minimapFor = "";
    public boolean buyOpen;
    private int buyCategory = 3;
    private final List<Rectangle> buyItemRects = new ArrayList<>();
    private final List<WeaponType> buyItems = new ArrayList<>();
    private final List<Rectangle> buyCatRects = new ArrayList<>();
    private final List<Rectangle> buyGearRects = new ArrayList<>();
    private int hoverItem = -1, hoverCat = -1, hoverGear = -1;

    private static final String[] CATEGORIES = {
            "Пистолеты", "Пистолеты-пулемёты", "Дробовики", "Винтовки", "Снайперские", "Тяжёлое", "Снаряжение"
    };

    public Hud(Game game) {
        this.game = game;
        this.s = game.settings;
    }

    public void update(Input in, float dt) {
        Player p = game.player;
        if (p.wantsBuyMenu) {
            p.wantsBuyMenu = false;
            if (game.mode.buyAllowed(game, p)) { buyOpen = !buyOpen; game.sfx.playUi("ui_click"); }
            else game.announce("Закупка недоступна: нужна зона закупки в начале раунда", new Color(0xFFB0A0));
        }
        if (buyOpen && (in.keyPressed(KeyEvent.VK_ESCAPE) || !game.mode.buyAllowed(game, p))) buyOpen = false;
        game.buyOpen = buyOpen;
    }

    // ---------------------------------------------------------------- Отрисовка

    public void render(Graphics2D g, int w, int h, float fps) {
        Player p = game.player;
        float ui = s.uiScale;

        drawCrosshair(g, w, h);
        drawBottomBars(g, w, h, ui);
        drawTopBar(g, w, h, ui);
        drawKillFeed(g, w, ui);
        if (s.showMinimap) drawMinimap(g, w, h, ui);
        drawObjective(g, w, h, ui);
        drawPopups(g);
        drawAnnounce(g, w, h, ui);

        if (!p.alive) drawDeathScreen(g, w, h, ui);
        if (game.showScoreboard || game.mode.phase == GameMode.Phase.MATCH_OVER) drawScoreboard(g, w, h, ui);
        if (buyOpen) drawBuyMenu(g, w, h, ui);

        if (s.showFps) {
            g.setFont(new Font("Monospaced", Font.PLAIN, (int) (12 * ui)));
            g.setColor(new Color(0x8FE08A));
            g.drawString(String.format("FPS %.0f  |  кадр %.1f мс  |  частиц: %d", fps, game.renderMs, countParticles()), 10, (int) (18 * ui));
        }
    }

    private int countParticles() {
        int n = 0;
        for (Fx.Particle pp : game.fx.particles()) if (pp.active) n++;
        return n;
    }

    private void drawCrosshair(Graphics2D g, int w, int h) {
        Player p = game.player;
        if (!p.alive) return;
        Weapon wp = p.currentWeapon();
        if (wp.zoom > 0) {
            // Оптический прицел
            g.setColor(new Color(0, 0, 0, 210));
            int r = (int) (Math.min(w, h) * 0.42f);
            Shape old = g.getClip();
            java.awt.geom.Area a = new java.awt.geom.Area(new Rectangle(0, 0, w, h));
            a.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(w / 2f - r, h / 2f - r, r * 2, r * 2)));
            g.fill(a);
            g.setClip(old);
            g.setColor(new Color(0x1A1A1A));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(w / 2 - r, h / 2 - r, r * 2, r * 2);
            g.setColor(new Color(0, 0, 0, 200));
            g.drawLine(w / 2, h / 2 - r, w / 2, h / 2 + r);
            g.drawLine(w / 2 - r, h / 2, w / 2 + r, h / 2);
            g.setStroke(new BasicStroke(1f));
            return;
        }

        // Прицел рисуем не под курсором, а по реальному направлению ствола —
        // так видно увод от отдачи.
        float psx = game.camera.worldToScreenX(p.pos.x), psy = game.camera.worldToScreenY(p.pos.y);
        float mdx = game.input.mouseX - psx, mdy = game.input.mouseY - psy;
        float mdist = Math.max(40f, (float) Math.sqrt(mdx * mdx + mdy * mdy));
        int cx = Math.round(psx + (float) Math.cos(p.aim) * mdist);
        int cy = Math.round(psy + (float) Math.sin(p.aim) * mdist);
        float spread = wp.currentSpread(p.moving && !p.walking, p.crouching, false);
        float gap = 5 + spread * 900f;
        gap = MathUtil.clamp(gap, 4, 60);
        int len = 7;
        g.setStroke(new BasicStroke(2f));
        Color c = new Color(0x7CFC7C);
        for (Actor a : game.actors) {
            if (a.alive && game.areEnemies(p, a) && game.visibleToPlayer(a)) {
                float ang = (float) Math.atan2(a.pos.y - p.pos.y, a.pos.x - p.pos.x);
                if (Math.abs(MathUtil.angleDiff(p.aim, ang)) < 0.06f && game.losClear(p.pos, a.pos)) {
                    c = new Color(0xFF6B6B);
                    break;
                }
            }
        }
        g.setColor(new Color(0, 0, 0, 160));
        drawCross(g, cx, cy, gap + 1, len + 1);
        g.setColor(c);
        drawCross(g, cx, cy, gap, len);
        g.setStroke(new BasicStroke(1f));
        g.fillRect(cx - 1, cy - 1, 2, 2);

        if (game.hitmarker > 0) {
            float k = game.hitmarker;
            g.setColor(game.hitmarkerKill ? new Color(1f, 0.3f, 0.3f, k) : new Color(1f, 1f, 1f, k));
            g.setStroke(new BasicStroke(2f));
            int o = (int) (7 + (1 - k) * 6);
            g.drawLine(cx - o - 5, cy - o - 5, cx - o, cy - o);
            g.drawLine(cx + o, cy - o, cx + o + 5, cy - o - 5);
            g.drawLine(cx - o - 5, cy + o + 5, cx - o, cy + o);
            g.drawLine(cx + o, cy + o, cx + o + 5, cy + o + 5);
            g.setStroke(new BasicStroke(1f));
        }
    }

    private void drawCross(Graphics2D g, int cx, int cy, float gap, int len) {
        g.drawLine((int) (cx - gap - len), cy, (int) (cx - gap), cy);
        g.drawLine((int) (cx + gap), cy, (int) (cx + gap + len), cy);
        g.drawLine(cx, (int) (cy - gap - len), cx, (int) (cy - gap));
        g.drawLine(cx, (int) (cy + gap), cx, (int) (cy + gap + len));
    }

    private void drawBottomBars(Graphics2D g, int w, int h, float ui) {
        Player p = game.player;
        int pad = (int) (18 * ui);
        int baseY = h - pad;

        // Здоровье и броня
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (26 * ui)));
        panel(g, pad, baseY - (int) (66 * ui), (int) (230 * ui), (int) (66 * ui));
        g.setColor(p.hp > 35 ? new Color(0xE8EDF2) : new Color(0xFF7A6A));
        g.drawString(String.valueOf(Math.max(0, Math.round(p.hp))), pad + (int) (44 * ui), baseY - (int) (36 * ui));
        g.setColor(new Color(0x8FD08A));
        g.fillRect(pad + (int) (14 * ui), baseY - (int) (52 * ui), (int) (20 * ui), (int) (20 * ui));
        g.setColor(new Color(0x9FC8E8));
        g.fillRect(pad + (int) (14 * ui), baseY - (int) (26 * ui), (int) (20 * ui), (int) (14 * ui));
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (18 * ui)));
        g.setColor(new Color(0xBFD4E8));
        g.drawString(Math.round(p.armor) + (p.helmet ? " ●" : ""), pad + (int) (44 * ui), baseY - (int) (12 * ui));

        // Полоски
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(pad + (int) (108 * ui), baseY - (int) (52 * ui), (int) (110 * ui), (int) (12 * ui));
        g.setColor(new Color(0x6FD08A));
        g.fillRect(pad + (int) (108 * ui), baseY - (int) (52 * ui), (int) (110 * ui * MathUtil.clamp(p.hp / p.maxHp, 0, 1)), (int) (12 * ui));
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(pad + (int) (108 * ui), baseY - (int) (34 * ui), (int) (110 * ui), (int) (8 * ui));
        g.setColor(new Color(0x7FB6E8));
        g.fillRect(pad + (int) (108 * ui), baseY - (int) (34 * ui), (int) (110 * ui * MathUtil.clamp(p.armor / 100f, 0, 1)), (int) (8 * ui));

        // Деньги
        if (game.mode.buyEnabled()) {
            g.setFont(new Font("SansSerif", Font.BOLD, (int) (20 * ui)));
            g.setColor(new Color(0x9FE0A8));
            g.drawString("$" + p.money, pad + (int) (4 * ui), baseY - (int) (76 * ui));
        }

        // Оружие и патроны
        Weapon wp = p.currentWeapon();
        int bw = (int) (280 * ui), bh = (int) (76 * ui);
        int bx = w - pad - bw, by = baseY - bh;
        panel(g, bx, by, bw, bh);
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (16 * ui)));
        g.setColor(new Color(0xD8DEE6));
        String wname = p.slot == Actor.SLOT_NADE ? Actor.NADE_NAMES[p.nadeSel]
                : (p.slot == Actor.SLOT_BOMB ? "C4" : wp.type.label);
        g.drawString(wname, bx + (int) (14 * ui), by + (int) (24 * ui));

        g.setFont(new Font("SansSerif", Font.BOLD, (int) (30 * ui)));
        if (p.slot == Actor.SLOT_NADE) {
            g.setColor(new Color(0xE8EDF2));
            g.drawString("x" + p.nades[p.nadeSel], bx + (int) (14 * ui), by + (int) (60 * ui));
        } else if (p.slot == Actor.SLOT_KNIFE || p.slot == Actor.SLOT_BOMB) {
            g.setColor(new Color(0x9AA4B0));
            g.drawString("—", bx + (int) (14 * ui), by + (int) (60 * ui));
        } else {
            g.setColor(wp.ammo == 0 ? new Color(0xFF7A6A) : new Color(0xE8EDF2));
            g.drawString(String.valueOf(wp.ammo), bx + (int) (14 * ui), by + (int) (60 * ui));
            g.setFont(new Font("SansSerif", Font.PLAIN, (int) (18 * ui)));
            g.setColor(new Color(0x9AA4B0));
            g.drawString("/ " + wp.reserve, bx + (int) (70 * ui), by + (int) (60 * ui));
        }

        if (wp.reloading) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(bx + (int) (14 * ui), by + (int) (66 * ui), bw - (int) (28 * ui), (int) (5 * ui));
            g.setColor(new Color(0xFFD27F));
            g.fillRect(bx + (int) (14 * ui), by + (int) (66 * ui), (int) ((bw - 28 * ui) * wp.reloadProgress()), (int) (5 * ui));
        }

        // Гранаты
        int gx = bx - (int) (14 * ui);
        for (int i = 3; i >= 0; i--) {
            if (p.nades[i] <= 0) continue;
            gx -= (int) (40 * ui);
            boolean sel = p.slot == Actor.SLOT_NADE && p.nadeSel == i;
            g.setColor(sel ? new Color(0.9f, 0.8f, 0.4f, 0.35f) : new Color(0, 0, 0, 120));
            g.fillRoundRect(gx, baseY - (int) (40 * ui), (int) (34 * ui), (int) (36 * ui), 6, 6);
            Color nc = switch (i) {
                case Actor.NADE_HE -> new Color(0x6B8A4A);
                case Actor.NADE_FLASH -> new Color(0xD0D4DA);
                case Actor.NADE_SMOKE -> new Color(0x7A9AAA);
                default -> new Color(0xD07A3A);
            };
            g.setColor(nc);
            g.fillRoundRect(gx + (int) (11 * ui), baseY - (int) (34 * ui), (int) (12 * ui), (int) (18 * ui), 6, 6);
            g.setFont(new Font("SansSerif", Font.BOLD, (int) (11 * ui)));
            g.setColor(Color.WHITE);
            g.drawString("x" + p.nades[i], gx + (int) (10 * ui), baseY - (int) (8 * ui));
        }

        // Подсказка по взаимодействию
        String hint = interactionHint();
        if (hint != null) {
            g.setFont(new Font("SansSerif", Font.BOLD, (int) (16 * ui)));
            int tw = g.getFontMetrics().stringWidth(hint);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(w / 2 - tw / 2 - 12, h - (int) (150 * ui), tw + 24, (int) (28 * ui), 8, 8);
            g.setColor(new Color(0xFFE08A));
            g.drawString(hint, w / 2f - tw / 2f, h - (int) (131 * ui));
        }
    }

    private String interactionHint() {
        Player p = game.player;
        if (!p.alive) return null;
        if (game.mode.type() == GameMode.Type.DEFUSE && game.bomb != null) {
            if (p.team == Actor.TEAM_T && p.hasBomb) {
                Tile t = game.map.tileAtWorld(p.pos.x, p.pos.y);
                if (t == Tile.SITE_A || t == Tile.SITE_B) return "Удерживайте [E] — заложить C4";
            }
            if (p.team == Actor.TEAM_CT && game.bomb.planted && !game.bomb.defused && p.pos.dist(game.bomb.pos) < 46)
                return "Удерживайте [E] — обезвредить" + (p.hasKit ? " (с кусачками)" : "");
            if (p.team == Actor.TEAM_T && !p.hasBomb && game.looseBomb != null && p.pos.dist(game.looseBomb) < 40)
                return "[E] — подобрать C4";
        }
        if (game.mode.type() == GameMode.Type.HOSTAGE && p.team == Actor.TEAM_CT) {
            for (Hostage hst : game.hostages)
                if (hst.alive && !hst.rescued && hst.follower == null && hst.pos.dist(p.pos) < 46)
                    return "[E] — освободить заложника";
        }
        for (Pickup pk : game.pickups)
            if (pk.pos.dist(p.pos) < 30) return "[E] — подобрать " + pk.type.label;
        if (game.mode.buyAllowed(game, p) && !buyOpen) return "[B] — закупка";
        return null;
    }

    private void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(10, 12, 15, 150));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(new Color(255, 255, 255, 22));
        g.drawRoundRect(x, y, w, h, 10, 10);
    }

    private void drawTopBar(Graphics2D g, int w, int h, float ui) {
        GameMode m = game.mode;
        int bw = (int) (300 * ui), bh = (int) (52 * ui);
        int bx = w / 2 - bw / 2;
        panel(g, bx, 0, bw, bh);

        String time;
        if (game.bomb != null && game.bomb.planted && !game.bomb.defused && !game.bomb.exploded) {
            time = String.format("%.1f", Math.max(0, game.bomb.timer));
        } else {
            int t = (int) Math.ceil(m.timeLeft());
            time = String.format("%d:%02d", t / 60, t % 60);
        }
        g.setFont(new Font("Monospaced", Font.BOLD, (int) (26 * ui)));
        boolean urgent = (game.bomb != null && game.bomb.planted && !game.bomb.defused) || m.timeLeft() < 20;
        g.setColor(urgent ? new Color(0xFF7A5A) : new Color(0xE8EDF2));
        int tw = g.getFontMetrics().stringWidth(time);
        g.drawString(time, w / 2f - tw / 2f, (int) (34 * ui));

        g.setFont(new Font("SansSerif", Font.BOLD, (int) (24 * ui)));
        g.setColor(new Color(0xE0A05A));
        g.drawString(String.valueOf(m.scoreT), bx + (int) (24 * ui), (int) (34 * ui));
        g.setColor(new Color(0x7FB6E8));
        String ctS = String.valueOf(m.scoreCT);
        g.drawString(ctS, bx + bw - (int) (24 * ui) - g.getFontMetrics().stringWidth(ctS), (int) (34 * ui));

        g.setFont(new Font("SansSerif", Font.PLAIN, (int) (11 * ui)));
        g.setColor(new Color(0x9AA4B0));
        String alive = game.aliveCount(Actor.TEAM_T) + " живых";
        g.drawString(alive, bx + (int) (16 * ui), (int) (48 * ui));
        String aliveCt = game.aliveCount(Actor.TEAM_CT) + " живых";
        g.drawString(aliveCt, bx + bw - (int) (16 * ui) - g.getFontMetrics().stringWidth(aliveCt), (int) (48 * ui));

        if (m.phase == GameMode.Phase.FREEZE && m.roundBased()) {
            g.setFont(new Font("SansSerif", Font.BOLD, (int) (15 * ui)));
            g.setColor(new Color(0xFFD27F));
            String txt = "Подготовка — " + String.format("%.0f", Math.max(0, m.phaseTimer)) + " с  ·  [B] закупка";
            int tww = g.getFontMetrics().stringWidth(txt);
            g.drawString(txt, w / 2f - tww / 2f, bh + (int) (22 * ui));
        }
    }

    private void drawObjective(Graphics2D g, int w, int h, float ui) {
        g.setFont(new Font("SansSerif", Font.PLAIN, (int) (13 * ui)));
        g.setColor(new Color(0xA8B2BE));
        String txt = game.mode.objectiveText(game);
        g.drawString(txt, (int) (18 * ui), (int) (36 * ui));
    }

    private void drawKillFeed(Graphics2D g, int w, float ui) {
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (13 * ui)));
        int y = (int) (62 * ui);
        for (int i = game.killFeed.size() - 1; i >= 0; i--) {
            Game.Feed f = game.killFeed.get(i);
            float alpha = MathUtil.clamp(f.life, 0, 1);
            String left = f.killer.isEmpty() ? "" : f.killer;
            String mid = "  ‹" + f.weapon + (f.headshot ? " ★" : "") + "›  ";
            String right = f.victim;
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(left) + fm.stringWidth(mid) + fm.stringWidth(right);
            int x = w - (int) (18 * ui) - tw;
            g.setColor(new Color(0, 0, 0, (int) (120 * alpha)));
            g.fillRoundRect(x - 8, y - (int) (14 * ui), tw + 16, (int) (20 * ui), 6, 6);
            g.setColor(withAlpha(f.teamkill ? new Color(0xFF6B6B) : f.killerColor, alpha));
            g.drawString(left, x, y);
            x += fm.stringWidth(left);
            g.setColor(withAlpha(new Color(0xC8CED6), alpha));
            g.drawString(mid, x, y);
            x += fm.stringWidth(mid);
            g.setColor(withAlpha(f.victimColor, alpha));
            g.drawString(right, x, y);
            y += (int) (24 * ui);
        }
    }

    private Color withAlpha(Color c, float a) {
        return new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, MathUtil.clamp(a, 0, 1));
    }

    private void drawPopups(Graphics2D g) {
        Camera cam = game.camera;
        for (Fx.Popup p : game.fx.popups) {
            float a = MathUtil.clamp(p.life / 0.85f, 0, 1);
            g.setFont(new Font("SansSerif", Font.BOLD, (int) p.size));
            g.setColor(withAlpha(p.color, a));
            g.drawString(p.text, cam.worldToScreenX(p.x), cam.worldToScreenY(p.y));
        }
    }

    private void drawAnnounce(Graphics2D g, int w, int h, float ui) {
        if (game.announceTime <= 0) return;
        float a = MathUtil.clamp(game.announceTime, 0, 1);
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (30 * ui)));
        int tw = g.getFontMetrics().stringWidth(game.announceText);
        g.setColor(new Color(0, 0, 0, (int) (140 * a)));
        g.fillRoundRect(w / 2 - tw / 2 - 20, (int) (h * 0.22f) - (int) (34 * ui), tw + 40, (int) (48 * ui), 10, 10);
        g.setColor(withAlpha(game.announceColor, a));
        g.drawString(game.announceText, w / 2f - tw / 2f, (int) (h * 0.22f));
    }

    // ------------------------------------------------------------- Миникарта
    private void buildMinimap() {
        GameMap m = game.map;
        int scale = 3;
        minimap = new BufferedImage(m.w * scale, m.h * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = minimap.createGraphics();
        for (int y = 0; y < m.h; y++) {
            for (int x = 0; x < m.w; x++) {
                Tile t = m.tiles[x][y];
                Color c;
                if (t.solid) c = new Color(t.color.getRed(), t.color.getGreen(), t.color.getBlue(), 235);
                else if (t == Tile.SITE_A) c = new Color(255, 138, 90, 160);
                else if (t == Tile.SITE_B) c = new Color(111, 182, 255, 160);
                else if (t == Tile.RESCUE) c = new Color(143, 224, 138, 160);
                else c = new Color(t.color.getRed(), t.color.getGreen(), t.color.getBlue(), 110);
                mg.setColor(c);
                mg.fillRect(x * scale, y * scale, scale, scale);
            }
        }
        mg.dispose();
        minimapFor = game.map.name;
    }

    private void drawMinimap(Graphics2D g, int w, int h, float ui) {
        if (minimap == null || !minimapFor.equals(game.map.name)) buildMinimap();
        int size = (int) (190 * ui);
        int x = w - size - (int) (18 * ui), y = (int) (62 * ui);
        if (!game.killFeed.isEmpty()) y = (int) (62 * ui) + (int) (24 * ui) * Math.min(6, game.killFeed.size()) + 8;

        g.setColor(new Color(6, 8, 10, 170));
        g.fillRoundRect(x - 4, y - 4, size + 8, size + 8, 8, 8);
        float sx = size / (float) minimap.getWidth();
        float sy = size / (float) minimap.getHeight();
        g.drawImage(minimap, x, y, size, size, null);

        float mx = size / game.map.pixelW(), my = size / game.map.pixelH();

        if (game.bomb != null && game.bomb.planted) {
            float px = x + game.bomb.pos.x * mx, py = y + game.bomb.pos.y * my;
            g.setColor(new Color(1f, 0.3f, 0.25f, 0.5f + 0.5f * (float) Math.sin(game.time * 8)));
            g.fillOval((int) px - 4, (int) py - 4, 8, 8);
        }
        for (Hostage hst : game.hostages) {
            if (!hst.alive || hst.rescued) continue;
            g.setColor(new Color(0xE8C46A));
            g.fillOval((int) (x + hst.pos.x * mx) - 2, (int) (y + hst.pos.y * my) - 2, 5, 5);
        }
        for (SmokeCloud sc : game.smokes) {
            g.setColor(new Color(0.8f, 0.8f, 0.82f, 0.35f * sc.density));
            float r = sc.radius * mx;
            g.fillOval((int) (x + sc.origin.x * mx - r), (int) (y + sc.origin.y * my - r), (int) (r * 2), (int) (r * 2));
        }
        for (FireArea f : game.fires) {
            g.setColor(new Color(1f, 0.45f, 0.15f, 0.4f * f.intensity));
            float r = f.radius * mx * 0.6f;
            g.fillOval((int) (x + f.pos.x * mx - r), (int) (y + f.pos.y * my - r), (int) (r * 2), (int) (r * 2));
        }

        for (Actor a : game.actors) {
            if (!a.alive) continue;
            boolean ally = !game.mode.ffa() && a.team == game.player.team;
            boolean show = ally || game.visibleToPlayer(a) || a.noiseLevel > 0.8f;
            if (!show) continue;
            float px = x + a.pos.x * mx, py = y + a.pos.y * my;
            if (a == game.player) {
                g.setColor(Color.WHITE);
                java.awt.geom.AffineTransform t = g.getTransform();
                g.translate(px, py);
                g.rotate(a.aim);
                g.fillPolygon(new int[]{5, -3, -3}, new int[]{0, -3, 3}, 3);
                g.setTransform(t);
            } else {
                g.setColor(ally ? new Color(0x6FD08A) : new Color(0xFF6B6B));
                g.fillOval((int) px - 2, (int) py - 2, 5, 5);
            }
        }
        g.setColor(new Color(255, 255, 255, 40));
        g.drawRoundRect(x - 4, y - 4, size + 8, size + 8, 8, 8);
    }

    // ---------------------------------------------------------- Экран смерти
    private void drawDeathScreen(Graphics2D g, int w, int h, float ui) {
        g.setColor(new Color(40, 0, 0, 90));
        g.fillRect(0, 0, w, h);
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (40 * ui)));
        String t = "ВЫ УБИТЫ";
        int tw = g.getFontMetrics().stringWidth(t);
        g.setColor(new Color(0xFF7A6A));
        g.drawString(t, w / 2f - tw / 2f, h * 0.36f);

        g.setFont(new Font("SansSerif", Font.PLAIN, (int) (16 * ui)));
        g.setColor(new Color(0xC8CED6));
        String sub;
        if (game.player.lastAttacker != null)
            sub = "Вас убил: " + game.player.lastAttacker.name + " (осталось HP: "
                    + Math.max(0, Math.round(game.player.lastAttacker.hp)) + ")";
        else sub = "Причина: окружение";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.drawString(sub, w / 2f - sw / 2f, h * 0.36f + (int) (30 * ui));

        String sp = game.mode.respawnEnabled()
                ? "Возрождение через " + String.format("%.1f", Math.max(0, 3.2f - game.player.deathTimer)) + " с"
                : "Наблюдение за: " + (game.spectating != null ? game.spectating.name : "—");
        int pw = g.getFontMetrics().stringWidth(sp);
        g.setColor(new Color(0x9AA4B0));
        g.drawString(sp, w / 2f - pw / 2f, h * 0.36f + (int) (56 * ui));
    }

    // ------------------------------------------------------------ Таблица
    private void drawScoreboard(Graphics2D g, int w, int h, float ui) {
        int bw = (int) (720 * ui), bh = (int) (460 * ui);
        int x = w / 2 - bw / 2, y = h / 2 - bh / 2;
        g.setColor(new Color(8, 10, 13, 225));
        g.fillRoundRect(x, y, bw, bh, 14, 14);
        g.setColor(new Color(255, 255, 255, 30));
        g.drawRoundRect(x, y, bw, bh, 14, 14);

        g.setFont(new Font("SansSerif", Font.BOLD, (int) (20 * ui)));
        g.setColor(new Color(0xE8EDF2));
        g.drawString(game.mode.type().label + "  ·  " + MapLibrary.label(game.map.name), x + 20, y + (int) (32 * ui));
        g.setFont(new Font("SansSerif", Font.PLAIN, (int) (13 * ui)));
        g.setColor(new Color(0x9AA4B0));
        g.drawString("Сложность: " + s.difficulty.label + "   ·   Счёт " + game.mode.scoreT + " : " + game.mode.scoreCT,
                x + 20, y + (int) (52 * ui));

        int col = x + 20;
        int top = y + (int) (76 * ui);
        drawTeamTable(g, col, top, bw / 2 - 30, Actor.TEAM_T, "ТЕРРОРИСТЫ", new Color(0xE0A05A), ui);
        drawTeamTable(g, x + bw / 2 + 10, top, bw / 2 - 30, Actor.TEAM_CT, "СПЕЦНАЗ", new Color(0x7FB6E8), ui);

        if (game.mode.phase == GameMode.Phase.MATCH_OVER) {
            g.setFont(new Font("SansSerif", Font.BOLD, (int) (18 * ui)));
            g.setColor(new Color(0xFFD27F));
            String t = game.mode.matchResultText() + "   ·   [Esc] — выход в меню";
            int tw = g.getFontMetrics().stringWidth(t);
            g.drawString(t, w / 2f - tw / 2f, y + bh - (int) (18 * ui));
        }
    }

    private void drawTeamTable(Graphics2D g, int x, int y, int w, int team, String title, Color c, float ui) {
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (15 * ui)));
        g.setColor(c);
        g.drawString(title, x, y);
        g.setFont(new Font("SansSerif", Font.PLAIN, (int) (11 * ui)));
        g.setColor(new Color(0x7A828C));
        g.drawString("Игрок", x, y + (int) (20 * ui));
        g.drawString("У", x + w - (int) (110 * ui), y + (int) (20 * ui));
        g.drawString("С", x + w - (int) (75 * ui), y + (int) (20 * ui));
        g.drawString("$", x + w - (int) (42 * ui), y + (int) (20 * ui));

        int row = y + (int) (40 * ui);
        List<Actor> list = new ArrayList<>();
        for (Actor a : game.actors) if (a.team == team) list.add(a);
        list.sort((a, b) -> Integer.compare(b.kills, a.kills));
        g.setFont(new Font("SansSerif", Font.PLAIN, (int) (13 * ui)));
        for (Actor a : list) {
            if (a == game.player) {
                g.setColor(new Color(255, 255, 255, 22));
                g.fillRect(x - 6, row - (int) (13 * ui), w, (int) (19 * ui));
            }
            g.setColor(a.alive ? new Color(0xD8DEE6) : new Color(0x6A727C));
            String nm = a.name + (a instanceof Bot b && !b.tag.isEmpty() ? " " + b.tag : "");
            if (a.hasBomb) nm = "◆ " + nm;
            g.drawString(nm, x, row);
            g.drawString(String.valueOf(a.kills), x + w - (int) (110 * ui), row);
            g.drawString(String.valueOf(a.deaths), x + w - (int) (75 * ui), row);
            g.setColor(new Color(0x6FA87A));
            g.drawString(String.valueOf(a.money), x + w - (int) (42 * ui), row);
            row += (int) (21 * ui);
        }
    }

    // ----------------------------------------------------------- Меню закупки
    public void handleBuyInput(Input in) {
        if (!buyOpen) return;
        hoverItem = hoverCat = hoverGear = -1;
        for (int i = 0; i < buyCatRects.size(); i++)
            if (buyCatRects.get(i).contains(in.mouseX, in.mouseY)) hoverCat = i;
        for (int i = 0; i < buyItemRects.size(); i++)
            if (buyItemRects.get(i).contains(in.mouseX, in.mouseY)) hoverItem = i;
        for (int i = 0; i < buyGearRects.size(); i++)
            if (buyGearRects.get(i).contains(in.mouseX, in.mouseY)) hoverGear = i;

        if (in.mouseClicked(MouseEvent.BUTTON1)) {
            if (hoverCat >= 0) { buyCategory = hoverCat; game.sfx.playUi("ui_click"); }
            else if (hoverItem >= 0 && hoverItem < buyItems.size()) buyWeapon(buyItems.get(hoverItem));
            else if (hoverGear >= 0) buyGear(hoverGear);
        }
        for (int i = 0; i < 9; i++)
            if (in.keyPressed(KeyEvent.VK_1 + i)) {
                if (buyCategory == 6) { if (i < 6) buyGear(i); }
                else if (i < buyItems.size()) buyWeapon(buyItems.get(i));
            }
    }

    private void buyWeapon(WeaponType wt) {
        Player p = game.player;
        if (p.money < wt.price) { game.sfx.playUi("ui_hover"); game.announce("Недостаточно денег", new Color(0xFF9A8A)); return; }
        p.money -= wt.price;
        p.giveWeapon(wt);
        game.sfx.play("buy", 0.6f, 1f);
    }

    private void buyGear(int index) {
        Player p = game.player;
        switch (index) {
            case 0 -> { // броня
                if (p.money < 350 || p.armor >= 100) return;
                p.money -= 350; p.armor = 100;
            }
            case 1 -> { // броня + шлем
                if (p.money < 650 || (p.armor >= 100 && p.helmet)) return;
                p.money -= 650; p.armor = 100; p.helmet = true;
            }
            case 2, 3, 4, 5 -> {
                int kind = index - 2;
                int max = kind == Actor.NADE_FLASH ? 2 : 1;
                if (p.nades[kind] >= max || p.money < Actor.NADE_PRICE[kind]) return;
                p.money -= Actor.NADE_PRICE[kind];
                p.nades[kind]++;
                if (p.totalNades() == 1) p.nadeSel = kind;
            }
            case 6 -> {
                if (p.team != Actor.TEAM_CT || p.hasKit || p.money < 400) return;
                p.money -= 400; p.hasKit = true;
            }
        }
        game.sfx.play("buy", 0.6f, 1f);
    }

    private void drawBuyMenu(Graphics2D g, int w, int h, float ui) {
        Player p = game.player;
        int bw = (int) (900 * ui), bh = (int) (450 * ui);
        int x = w / 2 - bw / 2, y = h / 2 - bh / 2;
        g.setColor(new Color(6, 8, 11, 235));
        g.fillRoundRect(x, y, bw, bh, 14, 14);
        g.setColor(new Color(255, 255, 255, 30));
        g.drawRoundRect(x, y, bw, bh, 14, 14);

        g.setFont(new Font("SansSerif", Font.BOLD, (int) (20 * ui)));
        g.setColor(new Color(0xE8EDF2));
        g.drawString("ЗАКУПКА", x + 22, y + (int) (34 * ui));
        g.setColor(new Color(0x9FE0A8));
        String money = "$" + p.money;
        g.drawString(money, x + bw - 22 - g.getFontMetrics().stringWidth(money), y + (int) (34 * ui));
        g.setFont(new Font("SansSerif", Font.PLAIN, (int) (12 * ui)));
        g.setColor(new Color(0x7A828C));
        g.drawString("[B] или [Esc] — закрыть   ·   цифры 1-9 — быстрый выбор", x + 22, y + (int) (52 * ui));

        buyCatRects.clear();
        int cw = (int) (200 * ui), ch = (int) (34 * ui);
        int cy = y + (int) (66 * ui);
        for (int i = 0; i < CATEGORIES.length; i++) {
            Rectangle r = new Rectangle(x + 18, cy, cw, ch - 4);
            buyCatRects.add(r);
            boolean sel = buyCategory == i;
            g.setColor(sel ? new Color(0x2F4A6A) : (hoverCat == i ? new Color(255, 255, 255, 25) : new Color(255, 255, 255, 10)));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
            g.setFont(new Font("SansSerif", Font.BOLD, (int) (13 * ui)));
            g.setColor(sel ? new Color(0xD8E8FF) : new Color(0xA8B2BE));
            g.drawString(CATEGORIES[i], r.x + 12, r.y + (int) (20 * ui));
            cy += ch;
        }

        buyItemRects.clear();
        buyItems.clear();
        buyGearRects.clear();
        int lx = x + (int) (238 * ui), ly = y + (int) (66 * ui);
        int lw = bw - (int) (258 * ui);

        if (buyCategory == 6) {
            String[] gear = {"Бронежилет — $350", "Бронежилет + шлем — $650",
                    "Осколочная граната — $300", "Светошумовая — $200", "Дымовая — $300", "Молотов — $400",
                    "Набор сапёра — $400 (только спецназ)"};
            for (int i = 0; i < gear.length; i++) {
                Rectangle r = new Rectangle(lx, ly + i * (int) (36 * ui), lw, (int) (32 * ui));
                buyGearRects.add(r);
                boolean owned = switch (i) {
                    case 0 -> p.armor >= 100;
                    case 1 -> p.armor >= 100 && p.helmet;
                    case 6 -> p.hasKit;
                    default -> p.nades[i - 2] >= (i - 2 == Actor.NADE_FLASH ? 2 : 1);
                };
                g.setColor(hoverGear == i ? new Color(255, 255, 255, 28) : new Color(255, 255, 255, 12));
                g.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
                g.setFont(new Font("SansSerif", Font.PLAIN, (int) (14 * ui)));
                g.setColor(owned ? new Color(0x5E8A6A) : new Color(0xD8DEE6));
                g.drawString((i + 1) + ". " + gear[i] + (owned ? "  ✓" : ""), r.x + 12, r.y + (int) (21 * ui));
            }
        } else {
            WeaponType.Cat want = switch (buyCategory) {
                case 0 -> WeaponType.Cat.PISTOL;
                case 1 -> WeaponType.Cat.SMG;
                case 2 -> WeaponType.Cat.SHOTGUN;
                case 3 -> WeaponType.Cat.RIFLE;
                case 4 -> WeaponType.Cat.SNIPER;
                default -> WeaponType.Cat.HEAVY;
            };
            for (WeaponType wt : WeaponType.buyable(p.team)) if (wt.cat == want) buyItems.add(wt);
            for (int i = 0; i < buyItems.size(); i++) {
                WeaponType wt = buyItems.get(i);
                Rectangle r = new Rectangle(lx, ly + i * (int) (36 * ui), lw, (int) (32 * ui));
                buyItemRects.add(r);
                boolean afford = p.money >= wt.price;
                g.setColor(hoverItem == i ? new Color(255, 255, 255, 28) : new Color(255, 255, 255, 12));
                g.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
                g.setFont(new Font("SansSerif", Font.BOLD, (int) (14 * ui)));
                g.setColor(afford ? new Color(0xE8EDF2) : new Color(0x6A5050));
                g.drawString((i + 1) + ". " + wt.label, r.x + 12, r.y + (int) (21 * ui));
                g.setFont(new Font("SansSerif", Font.BOLD, (int) (13 * ui)));
                g.setColor(afford ? new Color(0x9FE0A8) : new Color(0xC08A8A));
                String pr = "$" + wt.price;
                int prX = r.x + r.width - (int) (14 * ui) - g.getFontMetrics().stringWidth(pr);
                g.drawString(pr, prX, r.y + (int) (21 * ui));
                g.setFont(new Font("SansSerif", Font.PLAIN, (int) (11 * ui)));
                g.setColor(new Color(0x8A929C));
                String stats = String.format("урон %.0f · %.0f/мин · маг %d · бронеб. %.0f%%",
                        wt.damage, wt.rpm, wt.magSize, wt.armorPen * 100);
                int statX = r.x + (int) (210 * ui);
                if (statX + g.getFontMetrics().stringWidth(stats) < prX - (int) (10 * ui))
                    g.drawString(stats, statX, r.y + (int) (21 * ui));
            }
        }
    }
}
