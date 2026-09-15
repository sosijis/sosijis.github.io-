package com.sosijis.shooter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Все настройки игры: графика, звук, управление, правила матча.
 * Сохраняются в ~/.sosijis-shooter/settings.properties и подхватываются при следующем запуске.
 */
public class Settings {

    // ---------- Графика ----------
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    public Quality quality = Quality.HIGH;
    public boolean fullscreen = false;
    public int windowW = 1280, windowH = 760;
    public int fpsCap = 144;           // 0 = без ограничения
    public boolean vsyncHint = true;
    public boolean antialias = true;
    public boolean dynamicLight = true;     // вспышки выстрелов, огонь, фонарь
    public boolean fogOfWar = true;         // конус обзора + туман войны
    public int fovRays = 168;               // качество полигона видимости
    public boolean shadows = true;          // мягкие тени от стен
    public boolean bloodDecals = true;
    public boolean bulletHoles = true;
    public int maxDecals = 400;
    public float particleScale = 1.0f;      // множитель количества частиц
    public boolean screenShake = true;
    public boolean muzzleFlash = true;
    public boolean smokeVolumetric = true;  // много слоёв дыма вместо плоского круга
    public boolean showFps = true;
    public boolean showMinimap = true;
    public boolean showDamageNumbers = true;
    public boolean showHitmarker = true;
    public boolean grain = true;            // лёгкое зерно/виньетка
    public float uiScale = 1.0f;
    public float gamma = 1.0f;

    // ---------- Звук ----------
    public float masterVolume = 0.85f;
    public float sfxVolume = 0.9f;
    public float musicVolume = 0.35f;
    public float uiVolume = 0.7f;
    public boolean positionalAudio = true;  // панорама и затухание по расстоянию
    public boolean occlusion = true;        // приглушение звука за стенами
    public boolean tinnitus = true;         // звон в ушах после флешки/взрыва

    // ---------- Управление ----------
    public float mouseSensitivity = 1.0f;
    public boolean holdToWalk = true;       // Shift — тихий шаг
    public boolean autoReload = true;
    public boolean toggleCrouch = false;

    // ---------- Правила матча ----------
    public GameMode.Type modeType = GameMode.Type.DEFUSE;
    public String mapName = "de_dust_lite";
    public int botCount = 9;                // всего ботов в матче
    public Difficulty difficulty = Difficulty.NORMAL;
    public boolean friendlyFire = false;
    public int roundTimeSec = 115;
    public int scoreLimit = 10;             // побед раундов / фрагов
    public int startMoney = 800;
    public boolean infiniteNades = false;
    public boolean realisticDamage = true;  // пробитие, падение урона, броня

    public enum Difficulty {
        EASY("Лёгкий", 0.55f, 0.45f, 0.30f),
        NORMAL("Обычный", 0.78f, 0.28f, 0.55f),
        HARD("Сложный", 0.90f, 0.18f, 0.78f),
        EXPERT("Эксперт", 0.97f, 0.10f, 0.95f);

        public final String label;
        public final float aim;        // точность прицеливания
        public final float reaction;   // время реакции (сек)
        public final float tactics;    // как охотно используют гранаты/укрытия

        Difficulty(String label, float aim, float reaction, float tactics) {
            this.label = label; this.aim = aim; this.reaction = reaction; this.tactics = tactics;
        }
    }

    // ---------- Производные от качества ----------
    public void applyQualityPreset(Quality q) {
        quality = q;
        switch (q) {
            case LOW -> {
                antialias = false; dynamicLight = false; shadows = false; fogOfWar = true;
                fovRays = 90; bloodDecals = false; bulletHoles = false; maxDecals = 80;
                particleScale = 0.35f; smokeVolumetric = false; grain = false;
            }
            case MEDIUM -> {
                antialias = true; dynamicLight = true; shadows = false; fogOfWar = true;
                fovRays = 140; bloodDecals = true; bulletHoles = true; maxDecals = 200;
                particleScale = 0.7f; smokeVolumetric = false; grain = false;
            }
            case HIGH -> {
                antialias = true; dynamicLight = true; shadows = true; fogOfWar = true;
                fovRays = 168; bloodDecals = true; bulletHoles = true; maxDecals = 400;
                particleScale = 1.0f; smokeVolumetric = true; grain = true;
            }
            case ULTRA -> {
                antialias = true; dynamicLight = true; shadows = true; fogOfWar = true;
                fovRays = 320; bloodDecals = true; bulletHoles = true; maxDecals = 900;
                particleScale = 1.8f; smokeVolumetric = true; grain = true;
            }
        }
    }

    // ---------- Сохранение ----------
    private static File file() {
        File dir = new File(System.getProperty("user.home"), ".sosijis-shooter");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "settings.properties");
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("quality", quality.name());
        p.setProperty("fullscreen", "" + fullscreen);
        p.setProperty("windowW", "" + windowW);
        p.setProperty("windowH", "" + windowH);
        p.setProperty("fpsCap", "" + fpsCap);
        p.setProperty("antialias", "" + antialias);
        p.setProperty("dynamicLight", "" + dynamicLight);
        p.setProperty("fogOfWar", "" + fogOfWar);
        p.setProperty("fovRays", "" + fovRays);
        p.setProperty("shadows", "" + shadows);
        p.setProperty("bloodDecals", "" + bloodDecals);
        p.setProperty("bulletHoles", "" + bulletHoles);
        p.setProperty("particleScale", "" + particleScale);
        p.setProperty("screenShake", "" + screenShake);
        p.setProperty("smokeVolumetric", "" + smokeVolumetric);
        p.setProperty("showFps", "" + showFps);
        p.setProperty("showMinimap", "" + showMinimap);
        p.setProperty("showDamageNumbers", "" + showDamageNumbers);
        p.setProperty("grain", "" + grain);
        p.setProperty("uiScale", "" + uiScale);
        p.setProperty("gamma", "" + gamma);
        p.setProperty("masterVolume", "" + masterVolume);
        p.setProperty("sfxVolume", "" + sfxVolume);
        p.setProperty("musicVolume", "" + musicVolume);
        p.setProperty("uiVolume", "" + uiVolume);
        p.setProperty("positionalAudio", "" + positionalAudio);
        p.setProperty("occlusion", "" + occlusion);
        p.setProperty("tinnitus", "" + tinnitus);
        p.setProperty("mouseSensitivity", "" + mouseSensitivity);
        p.setProperty("autoReload", "" + autoReload);
        p.setProperty("toggleCrouch", "" + toggleCrouch);
        p.setProperty("modeType", modeType.name());
        p.setProperty("mapName", mapName);
        p.setProperty("botCount", "" + botCount);
        p.setProperty("difficulty", difficulty.name());
        p.setProperty("friendlyFire", "" + friendlyFire);
        p.setProperty("roundTimeSec", "" + roundTimeSec);
        p.setProperty("scoreLimit", "" + scoreLimit);
        p.setProperty("realisticDamage", "" + realisticDamage);
        try (FileOutputStream out = new FileOutputStream(file())) {
            p.store(out, "Sosijis Tactical Shooter");
        } catch (Exception ignored) { }
    }

    public static Settings load() {
        Settings s = new Settings();
        File f = file();
        if (!f.exists()) return s;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (Exception e) {
            return s;
        }
        s.quality = en(p, "quality", Quality.class, s.quality);
        s.fullscreen = b(p, "fullscreen", s.fullscreen);
        s.windowW = i(p, "windowW", s.windowW);
        s.windowH = i(p, "windowH", s.windowH);
        s.fpsCap = i(p, "fpsCap", s.fpsCap);
        s.antialias = b(p, "antialias", s.antialias);
        s.dynamicLight = b(p, "dynamicLight", s.dynamicLight);
        s.fogOfWar = b(p, "fogOfWar", s.fogOfWar);
        s.fovRays = i(p, "fovRays", s.fovRays);
        s.shadows = b(p, "shadows", s.shadows);
        s.bloodDecals = b(p, "bloodDecals", s.bloodDecals);
        s.bulletHoles = b(p, "bulletHoles", s.bulletHoles);
        s.particleScale = f(p, "particleScale", s.particleScale);
        s.screenShake = b(p, "screenShake", s.screenShake);
        s.smokeVolumetric = b(p, "smokeVolumetric", s.smokeVolumetric);
        s.showFps = b(p, "showFps", s.showFps);
        s.showMinimap = b(p, "showMinimap", s.showMinimap);
        s.showDamageNumbers = b(p, "showDamageNumbers", s.showDamageNumbers);
        s.grain = b(p, "grain", s.grain);
        s.uiScale = f(p, "uiScale", s.uiScale);
        s.gamma = f(p, "gamma", s.gamma);
        s.masterVolume = f(p, "masterVolume", s.masterVolume);
        s.sfxVolume = f(p, "sfxVolume", s.sfxVolume);
        s.musicVolume = f(p, "musicVolume", s.musicVolume);
        s.uiVolume = f(p, "uiVolume", s.uiVolume);
        s.positionalAudio = b(p, "positionalAudio", s.positionalAudio);
        s.occlusion = b(p, "occlusion", s.occlusion);
        s.tinnitus = b(p, "tinnitus", s.tinnitus);
        s.mouseSensitivity = f(p, "mouseSensitivity", s.mouseSensitivity);
        s.autoReload = b(p, "autoReload", s.autoReload);
        s.toggleCrouch = b(p, "toggleCrouch", s.toggleCrouch);
        s.modeType = en(p, "modeType", GameMode.Type.class, s.modeType);
        s.mapName = p.getProperty("mapName", s.mapName);
        s.botCount = i(p, "botCount", s.botCount);
        s.difficulty = en(p, "difficulty", Difficulty.class, s.difficulty);
        s.friendlyFire = b(p, "friendlyFire", s.friendlyFire);
        s.roundTimeSec = i(p, "roundTimeSec", s.roundTimeSec);
        s.scoreLimit = i(p, "scoreLimit", s.scoreLimit);
        s.realisticDamage = b(p, "realisticDamage", s.realisticDamage);
        return s;
    }

    private static boolean b(Properties p, String k, boolean d) {
        String v = p.getProperty(k); return v == null ? d : Boolean.parseBoolean(v);
    }
    private static int i(Properties p, String k, int d) {
        try { return Integer.parseInt(p.getProperty(k)); } catch (Exception e) { return d; }
    }
    private static float f(Properties p, String k, float d) {
        try { return Float.parseFloat(p.getProperty(k)); } catch (Exception e) { return d; }
    }
    private static <E extends Enum<E>> E en(Properties p, String k, Class<E> cls, E d) {
        try { return Enum.valueOf(cls, p.getProperty(k)); } catch (Exception e) { return d; }
    }
}
