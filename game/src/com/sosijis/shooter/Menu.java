package com.sosijis.shooter;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/** Главное меню, настройка матча, настройки графики/звука/игры, справка по управлению и пауза. */
public class Menu {

    public enum Screen { MAIN, PLAY, VIDEO, AUDIO, GAMEPLAY, CONTROLS, PAUSE }

    public Screen screen = Screen.MAIN;
    public boolean startRequested, quitRequested, resumeRequested, backToMenuRequested, applyVideoRequested;
    public boolean inGame;

    private final Settings s;
    private final Ui ui = new Ui();
    private final Sfx sfx;
    private float t;
    private final float[] blobX = new float[9], blobY = new float[9], blobR = new float[9], blobS = new float[9];

    private static final String[] QUALITY_LABELS = {"Низкое", "Среднее", "Высокое", "Ультра"};
    private static final String[] DIFF_LABELS = {"Лёгкий", "Обычный", "Сложный", "Эксперт"};
    private static final String[] RES_LABELS = {"1024×640", "1280×760", "1440×860", "1600×900", "1920×1080"};
    private static final int[][] RES = {{1024, 640}, {1280, 760}, {1440, 860}, {1600, 900}, {1920, 1080}};
    private static final String[] FPS_LABELS = {"60", "75", "120", "144", "240", "Без лимита"};
    private static final int[] FPS_VALUES = {60, 75, 120, 144, 240, 0};

    public Menu(Settings s, Sfx sfx) {
        this.s = s;
        this.sfx = sfx;
        for (int i = 0; i < blobX.length; i++) {
            blobX[i] = MathUtil.rand(1f);
            blobY[i] = MathUtil.rand(1f);
            blobR[i] = MathUtil.rand(140f, 420f);
            blobS[i] = MathUtil.rand(0.02f, 0.08f) * (MathUtil.chance(0.5f) ? 1 : -1);
        }
    }

    public void update(float dt) { t += dt; }

    public void render(Graphics2D g, Input in, int w, int h) {
        ui.begin(g, in, sfx, s.uiScale);
        if (screen == Screen.PAUSE) {
            g.setColor(new Color(6, 8, 11, 190));
            g.fillRect(0, 0, w, h);
        } else {
            drawBackground(g, w, h);
        }

        switch (screen) {
            case MAIN -> drawMain(g, w, h);
            case PLAY -> drawPlay(g, w, h);
            case VIDEO -> drawVideo(g, w, h);
            case AUDIO -> drawAudio(g, w, h);
            case GAMEPLAY -> drawGameplay(g, w, h);
            case CONTROLS -> drawControls(g, w, h);
            case PAUSE -> drawPause(g, w, h);
        }
        ui.end();

        if (in.keyPressed(KeyEvent.VK_ESCAPE)) {
            switch (screen) {
                case MAIN -> { }
                case PAUSE -> resumeRequested = true;
                default -> screen = inGame ? Screen.PAUSE : Screen.MAIN;
            }
        }
    }

    private void drawBackground(Graphics2D g, int w, int h) {
        g.setColor(new Color(0x0B0D11));
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < blobX.length; i++) {
            float bx = (blobX[i] + t * blobS[i]) % 1.4f - 0.2f;
            float by = (blobY[i] + t * blobS[i] * 0.6f) % 1.4f - 0.2f;
            float cx = bx * w, cy = by * h, r = blobR[i] * s.uiScale;
            RadialGradientPaint p = new RadialGradientPaint(new java.awt.geom.Point2D.Float(cx, cy), r,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0.16f, 0.22f, 0.30f, 0.32f), new Color(0.1f, 0.12f, 0.16f, 0f)});
            g.setPaint(p);
            g.fillOval((int) (cx - r), (int) (cy - r), (int) (r * 2), (int) (r * 2));
        }
        g.setPaint(null);
        g.setColor(new Color(255, 255, 255, 8));
        int step = ui.s(48);
        for (int x = 0; x < w; x += step) g.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += step) g.drawLine(0, y, w, y);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, 0, w, h);
    }

    private void title(Graphics2D g, int w, String sub) {
        ui.textCenter("ТАКТИЧЕСКИЙ ШТУРМ", w / 2, ui.s(96), new Color(0xF0E0C8), 46, true);
        ui.textCenter("SOSIJIS TACTICAL ASSAULT", w / 2, ui.s(122), new Color(0x7A828C), 14, false);
        if (sub != null) ui.textCenter(sub, w / 2, ui.s(154), Ui.ACCENT, 16, true);
    }

    private void drawMain(Graphics2D g, int w, int h) {
        title(g, w, null);
        int bw = ui.s(300), bh = ui.s(48);
        int x = w / 2 - bw / 2, y = ui.s(200);
        if (ui.button(x, y, bw, bh, "ИГРАТЬ")) { screen = Screen.PLAY; }
        y += bh + ui.s(12);
        if (ui.button(x, y, bw, bh, "ГРАФИКА")) screen = Screen.VIDEO;
        y += bh + ui.s(12);
        if (ui.button(x, y, bw, bh, "ЗВУК")) screen = Screen.AUDIO;
        y += bh + ui.s(12);
        if (ui.button(x, y, bw, bh, "ИГРОВОЙ ПРОЦЕСС")) screen = Screen.GAMEPLAY;
        y += bh + ui.s(12);
        if (ui.button(x, y, bw, bh, "УПРАВЛЕНИЕ")) screen = Screen.CONTROLS;
        y += bh + ui.s(12);
        if (ui.button(x, y, bw, bh, "ВЫХОД")) quitRequested = true;

        ui.textCenter("Стрельба · дым · флешки · молотовы · спецназ против террористов",
                w / 2, h - ui.s(40), new Color(0x6A727C), 13, false);
    }

    private void drawPlay(Graphics2D g, int w, int h) {
        title(g, w, "НАСТРОЙКА МАТЧА");
        int pw = ui.s(720), ph = ui.s(430);
        int x = w / 2 - pw / 2, y = ui.s(180);
        ui.panel(x, y, pw, ph);

        int rowH = ui.s(44), rw = pw - ui.s(40);
        int rx = x + ui.s(20), ry = y + ui.s(24);

        String[] modeLabels = new String[GameMode.Type.values().length];
        for (int i = 0; i < modeLabels.length; i++) modeLabels[i] = GameMode.Type.values()[i].label;
        int mi = s.modeType.ordinal();
        int nmi = ui.select(rx, ry, rw, rowH, "Режим", modeLabels, mi);
        if (nmi != mi) s.modeType = GameMode.Type.values()[nmi];
        ry += rowH + ui.s(6);

        ui.text(s.modeType.desc, rx + ui.s(12), ry + ui.s(6), Ui.TEXT_DIM, 12, false);
        ry += ui.s(22);

        List<String> maps = MapLibrary.names();
        String[] mapLabels = new String[maps.size()];
        for (int i = 0; i < maps.size(); i++) mapLabels[i] = MapLibrary.label(maps.get(i));
        int idx = Math.max(0, maps.indexOf(s.mapName));
        int nidx = ui.select(rx, ry, rw, rowH, "Карта", mapLabels, idx);
        if (nidx != idx) s.mapName = maps.get(nidx);
        ry += rowH + ui.s(8);

        int di = s.difficulty.ordinal();
        int ndi = ui.select(rx, ry, rw, rowH, "Сложность ботов", DIFF_LABELS, di);
        if (ndi != di) s.difficulty = Settings.Difficulty.values()[ndi];
        ry += rowH + ui.s(8);

        s.botCount = Math.round(ui.slider(rx, ry, rw, rowH, "Количество ботов", s.botCount, 1, 19, "%.0f"));
        ry += rowH + ui.s(8);

        s.scoreLimit = Math.round(ui.slider(rx, ry, rw, rowH, "Лимит счёта", s.scoreLimit, 3, 30, "%.0f"));
        ry += rowH + ui.s(8);

        s.roundTimeSec = Math.round(ui.slider(rx, ry, rw, rowH, "Время раунда (с)", s.roundTimeSec, 45, 300, "%.0f"));
        ry += rowH + ui.s(8);

        s.friendlyFire = ui.toggle(rx, ry, rw, rowH, "Огонь по своим", s.friendlyFire);

        int bw = ui.s(200), bh = ui.s(46);
        if (ui.button(w / 2 - bw - ui.s(10), y + ph + ui.s(18), bw, bh, "НАЗАД")) screen = Screen.MAIN;
        if (ui.button(w / 2 + ui.s(10), y + ph + ui.s(18), bw, bh, "НАЧАТЬ БОЙ")) { s.save(); startRequested = true; }
    }

    private void drawVideo(Graphics2D g, int w, int h) {
        title(g, w, "ГРАФИКА");
        int pw = ui.s(760), ph = ui.s(470);
        int x = w / 2 - pw / 2, y = ui.s(178);
        ui.panel(x, y, pw, ph);
        int rowH = ui.s(38), rw = pw - ui.s(40);
        int rx = x + ui.s(20), ry = y + ui.s(20);

        int qi = s.quality.ordinal();
        int nqi = ui.select(rx, ry, rw, rowH, "Пресет качества", QUALITY_LABELS, qi);
        if (nqi != qi) s.applyQualityPreset(Settings.Quality.values()[nqi]);
        ry += rowH + ui.s(6);

        int ri = 1;
        for (int i = 0; i < RES.length; i++) if (RES[i][0] == s.windowW && RES[i][1] == s.windowH) ri = i;
        int nri = ui.select(rx, ry, rw, rowH, "Разрешение окна", RES_LABELS, ri);
        if (nri != ri) { s.windowW = RES[nri][0]; s.windowH = RES[nri][1]; applyVideoRequested = true; }
        ry += rowH + ui.s(4);

        boolean fs = ui.toggle(rx, ry, rw, rowH, "Полноэкранный режим (F11)", s.fullscreen);
        if (fs != s.fullscreen) { s.fullscreen = fs; applyVideoRequested = true; }
        ry += rowH + ui.s(4);

        int fi = 3;
        for (int i = 0; i < FPS_VALUES.length; i++) if (FPS_VALUES[i] == s.fpsCap) fi = i;
        int nfi = ui.select(rx, ry, rw, rowH, "Ограничение FPS", FPS_LABELS, fi);
        if (nfi != fi) s.fpsCap = FPS_VALUES[nfi];
        ry += rowH + ui.s(4);

        s.antialias = ui.toggle(rx, ry, rw, rowH, "Сглаживание", s.antialias); ry += rowH + ui.s(4);
        s.fogOfWar = ui.toggle(rx, ry, rw, rowH, "Туман войны и конус обзора", s.fogOfWar); ry += rowH + ui.s(4);
        s.fovRays = Math.round(ui.slider(rx, ry, rw, rowH, "Качество обзора (лучей)", s.fovRays, 48, 640, "%.0f")); ry += rowH + ui.s(4);
        s.dynamicLight = ui.toggle(rx, ry, rw, rowH, "Динамический свет", s.dynamicLight); ry += rowH + ui.s(4);
        s.shadows = ui.toggle(rx, ry, rw, rowH, "Тени от стен", s.shadows); ry += rowH + ui.s(4);
        s.smokeVolumetric = ui.toggle(rx, ry, rw, rowH, "Объёмный дым", s.smokeVolumetric); ry += rowH + ui.s(4);
        s.particleScale = ui.slider(rx, ry, rw, rowH, "Плотность частиц", s.particleScale, 0.1f, 2.5f, "%.1fx"); ry += rowH + ui.s(4);

        int col2 = x + pw / 2;
        int ry2 = ry;
        s.bloodDecals = ui.toggle(rx, ry2, rw, rowH, "Следы крови", s.bloodDecals); ry2 += rowH + ui.s(4);
        s.bulletHoles = ui.toggle(rx, ry2, rw, rowH, "Следы от пуль", s.bulletHoles); ry2 += rowH + ui.s(4);
        s.screenShake = ui.toggle(rx, ry2, rw, rowH, "Тряска экрана", s.screenShake); ry2 += rowH + ui.s(4);
        s.grain = ui.toggle(rx, ry2, rw, rowH, "Зерно и виньетка", s.grain);

        int bw = ui.s(200), bh = ui.s(44);
        if (ui.button(w / 2 - bw / 2, y + ph + ui.s(16), bw, bh, "НАЗАД")) {
            s.save();
            screen = inGame ? Screen.PAUSE : Screen.MAIN;
        }
    }

    private void drawAudio(Graphics2D g, int w, int h) {
        title(g, w, "ЗВУК");
        int pw = ui.s(700), ph = ui.s(330);
        int x = w / 2 - pw / 2, y = ui.s(190);
        ui.panel(x, y, pw, ph);
        int rowH = ui.s(44), rw = pw - ui.s(40);
        int rx = x + ui.s(20), ry = y + ui.s(24);

        s.masterVolume = ui.slider(rx, ry, rw, rowH, "Общая громкость", s.masterVolume, 0, 1, "%.2f"); ry += rowH + ui.s(8);
        s.sfxVolume = ui.slider(rx, ry, rw, rowH, "Эффекты", s.sfxVolume, 0, 1, "%.2f"); ry += rowH + ui.s(8);
        s.musicVolume = ui.slider(rx, ry, rw, rowH, "Музыка", s.musicVolume, 0, 1, "%.2f"); ry += rowH + ui.s(8);
        s.uiVolume = ui.slider(rx, ry, rw, rowH, "Интерфейс", s.uiVolume, 0, 1, "%.2f"); ry += rowH + ui.s(8);
        s.positionalAudio = ui.toggle(rx, ry, rw, rowH, "Позиционный звук (панорама)", s.positionalAudio); ry += rowH + ui.s(6);
        s.occlusion = ui.toggle(rx, ry, rw, rowH, "Приглушение за стенами", s.occlusion); ry += rowH + ui.s(6);
        s.tinnitus = ui.toggle(rx, ry, rw, rowH, "Звон в ушах после взрывов", s.tinnitus);

        int bw = ui.s(200), bh = ui.s(44);
        if (ui.button(w / 2 - bw / 2, y + ph + ui.s(18), bw, bh, "НАЗАД")) {
            s.save();
            screen = inGame ? Screen.PAUSE : Screen.MAIN;
        }
    }

    private void drawGameplay(Graphics2D g, int w, int h) {
        title(g, w, "ИГРОВОЙ ПРОЦЕСС");
        int pw = ui.s(700), ph = ui.s(330);
        int x = w / 2 - pw / 2, y = ui.s(190);
        ui.panel(x, y, pw, ph);
        int rowH = ui.s(42), rw = pw - ui.s(40);
        int rx = x + ui.s(20), ry = y + ui.s(24);

        s.mouseSensitivity = ui.slider(rx, ry, rw, rowH, "Чувствительность мыши", s.mouseSensitivity, 0.2f, 3f, "%.2f"); ry += rowH + ui.s(6);
        s.uiScale = ui.slider(rx, ry, rw, rowH, "Масштаб интерфейса", s.uiScale, 0.7f, 1.6f, "%.2f"); ry += rowH + ui.s(6);
        s.autoReload = ui.toggle(rx, ry, rw, rowH, "Автоперезарядка", s.autoReload); ry += rowH + ui.s(6);
        s.toggleCrouch = ui.toggle(rx, ry, rw, rowH, "Присед переключателем", s.toggleCrouch); ry += rowH + ui.s(6);
        s.realisticDamage = ui.toggle(rx, ry, rw, rowH, "Реалистичная баллистика (пробитие, падение урона)", s.realisticDamage); ry += rowH + ui.s(6);
        s.showDamageNumbers = ui.toggle(rx, ry, rw, rowH, "Цифры урона", s.showDamageNumbers); ry += rowH + ui.s(6);
        s.showMinimap = ui.toggle(rx, ry, rw, rowH, "Миникарта", s.showMinimap);

        int bw = ui.s(200), bh = ui.s(44);
        if (ui.button(w / 2 - bw / 2, y + ph + ui.s(18), bw, bh, "НАЗАД")) {
            s.save();
            screen = inGame ? Screen.PAUSE : Screen.MAIN;
        }
    }

    private void drawControls(Graphics2D g, int w, int h) {
        title(g, w, "УПРАВЛЕНИЕ");
        int pw = ui.s(640), ph = ui.s(420);
        int x = w / 2 - pw / 2, y = ui.s(190);
        ui.panel(x, y, pw, ph);

        String[][] rows = {
                {"W A S D", "движение"},
                {"Мышь", "прицеливание"},
                {"ЛКМ", "огонь"},
                {"ПКМ", "оптика / короткий бросок гранаты"},
                {"R", "перезарядка"},
                {"1 / 2 / 3", "основное · пистолет · нож"},
                {"4", "гранаты (повторное нажатие — следующая)"},
                {"5", "C4 (у террориста с бомбой)"},
                {"Q", "предыдущее оружие"},
                {"Shift", "тихий шаг"},
                {"Ctrl / C", "присед (точнее стрельба)"},
                {"E", "заложить · разминировать · подобрать"},
                {"B", "меню закупки (в зоне закупки)"},
                {"Tab", "таблица счёта"},
                {"Esc", "пауза"},
                {"F11", "полный экран"},
        };
        int ry = y + ui.s(36);
        for (String[] r : rows) {
            ui.text(r[0], x + ui.s(28), ry, Ui.ACCENT, 14, true);
            ui.text(r[1], x + ui.s(200), ry, Ui.TEXT, 14, false);
            ry += ui.s(24);
        }

        int bw = ui.s(200), bh = ui.s(44);
        if (ui.button(w / 2 - bw / 2, y + ph + ui.s(18), bw, bh, "НАЗАД"))
            screen = inGame ? Screen.PAUSE : Screen.MAIN;
    }

    private void drawPause(Graphics2D g, int w, int h) {
        ui.textCenter("ПАУЗА", w / 2, ui.s(150), new Color(0xF0E0C8), 40, true);
        int bw = ui.s(300), bh = ui.s(46);
        int x = w / 2 - bw / 2, y = ui.s(210);
        if (ui.button(x, y, bw, bh, "ПРОДОЛЖИТЬ")) resumeRequested = true;
        y += bh + ui.s(10);
        if (ui.button(x, y, bw, bh, "ГРАФИКА")) screen = Screen.VIDEO;
        y += bh + ui.s(10);
        if (ui.button(x, y, bw, bh, "ЗВУК")) screen = Screen.AUDIO;
        y += bh + ui.s(10);
        if (ui.button(x, y, bw, bh, "ИГРОВОЙ ПРОЦЕСС")) screen = Screen.GAMEPLAY;
        y += bh + ui.s(10);
        if (ui.button(x, y, bw, bh, "УПРАВЛЕНИЕ")) screen = Screen.CONTROLS;
        y += bh + ui.s(10);
        if (ui.button(x, y, bw, bh, "В ГЛАВНОЕ МЕНЮ")) { s.save(); backToMenuRequested = true; }
    }
}
