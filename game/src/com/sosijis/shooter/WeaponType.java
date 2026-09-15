package com.sosijis.shooter;

import java.awt.Color;

/**
 * Каталог оружия. Баллистика приближена к реальной: падение урона по дистанции,
 * пробитие материалов, отдача с паттерном разброса, штраф точности в движении.
 */
public enum WeaponType {

    KNIFE("Нож", Cat.MELEE, Team.ANY, 0,
            55, 0.0f, 60, 60, 300, false, 0, 0, 0f,
            0.00f, 0.00f, 0.00f, 0f, 0f, 0f, 1, 0.0f, 1.10f, "knife", new Color(0xCCCCCC)),

    GLOCK("Glock-18", Cat.PISTOL, Team.T, 200,
            28, 0.47f, 900, 1400, 400, false, 20, 120, 2.2f,
            0.016f, 0.055f, 0.012f, 0.9f, 0.5f, 7.0f, 1, 0.55f, 1.00f, "pistol", new Color(0xFFD27F)),

    USP("USP-S", Cat.PISTOL, Team.CT, 200,
            34, 0.50f, 950, 1500, 400, false, 12, 100, 2.2f,
            0.012f, 0.050f, 0.013f, 1.0f, 0.5f, 7.0f, 1, 0.58f, 1.00f, "pistol_s", new Color(0xFFD27F)),

    P250("P250", Cat.PISTOL, Team.ANY, 300,
            38, 0.64f, 800, 1300, 400, false, 13, 78, 2.4f,
            0.014f, 0.055f, 0.016f, 1.2f, 0.6f, 6.5f, 1, 0.60f, 1.00f, "pistol", new Color(0xFFD27F)),

    DEAGLE("Desert Eagle", Cat.PISTOL, Team.ANY, 700,
            63, 0.93f, 1200, 1900, 267, false, 7, 35, 2.8f,
            0.010f, 0.085f, 0.055f, 4.2f, 1.6f, 4.5f, 2, 0.75f, 0.98f, "deagle", new Color(0xFFC04D)),

    REVOLVER("R8 Revolver", Cat.PISTOL, Team.ANY, 600,
            78, 0.93f, 1400, 2100, 180, false, 8, 40, 3.2f,
            0.008f, 0.090f, 0.070f, 5.0f, 1.8f, 4.0f, 2, 0.85f, 0.96f, "deagle", new Color(0xFFC04D)),

    MP5("MP5-SD", Cat.SMG, Team.ANY, 1500,
            27, 0.56f, 750, 1250, 750, true, 30, 120, 2.6f,
            0.018f, 0.070f, 0.010f, 1.5f, 0.9f, 8.0f, 1, 0.50f, 0.96f, "smg_s", new Color(0xFFE08A)),

    MP9("MP9", Cat.SMG, Team.CT, 1250,
            26, 0.56f, 700, 1150, 857, true, 30, 120, 2.1f,
            0.020f, 0.075f, 0.011f, 1.6f, 1.0f, 8.5f, 1, 0.48f, 0.96f, "smg", new Color(0xFFE08A)),

    MAC10("MAC-10", Cat.SMG, Team.T, 1050,
            29, 0.50f, 650, 1100, 800, true, 30, 100, 2.3f,
            0.024f, 0.085f, 0.014f, 1.9f, 1.3f, 8.0f, 1, 0.46f, 0.96f, "smg", new Color(0xFFE08A)),

    UMP45("UMP-45", Cat.SMG, Team.ANY, 1200,
            35, 0.65f, 700, 1200, 666, true, 25, 100, 3.0f,
            0.019f, 0.072f, 0.014f, 1.8f, 1.0f, 7.5f, 1, 0.55f, 0.95f, "smg", new Color(0xFFE08A)),

    P90("P90", Cat.SMG, Team.ANY, 2350,
            26, 0.69f, 800, 1300, 857, true, 50, 100, 3.4f,
            0.021f, 0.078f, 0.010f, 1.4f, 1.1f, 8.5f, 1, 0.52f, 0.95f, "smg", new Color(0xFFE08A)),

    NOVA("Nova", Cat.SHOTGUN, Team.ANY, 1050,
            26, 0.50f, 320, 700, 160, false, 8, 32, 0.6f,
            0.055f, 0.110f, 0.020f, 3.0f, 1.4f, 5.0f, 1, 0.45f, 0.92f, "shotgun", new Color(0xFFB067)),

    XM1014("XM1014", Cat.SHOTGUN, Team.ANY, 2000,
            20, 0.80f, 340, 720, 333, true, 7, 32, 3.4f,
            0.060f, 0.115f, 0.030f, 3.6f, 1.8f, 5.0f, 1, 0.45f, 0.90f, "shotgun", new Color(0xFFB067)),

    GALIL("Galil AR", Cat.RIFLE, Team.T, 1800,
            30, 0.77f, 1100, 1900, 666, true, 35, 90, 3.0f,
            0.014f, 0.085f, 0.016f, 2.4f, 1.3f, 7.0f, 2, 0.60f, 0.92f, "rifle", new Color(0xFFE9A6)),

    FAMAS("FAMAS", Cat.RIFLE, Team.CT, 2050,
            30, 0.70f, 1100, 1900, 666, true, 25, 90, 3.3f,
            0.013f, 0.082f, 0.016f, 2.3f, 1.2f, 7.0f, 2, 0.62f, 0.92f, "rifle", new Color(0xFFE9A6)),

    AK47("AK-47", Cat.RIFLE, Team.T, 2700,
            36, 0.775f, 1300, 2200, 600, true, 30, 90, 2.5f,
            0.011f, 0.090f, 0.021f, 3.1f, 1.7f, 6.2f, 2, 0.70f, 0.90f, "ak", new Color(0xFFDD8C)),

    M4A4("M4A4", Cat.RIFLE, Team.CT, 2900,
            33, 0.70f, 1250, 2100, 666, true, 30, 90, 3.1f,
            0.010f, 0.085f, 0.018f, 2.6f, 1.4f, 6.8f, 2, 0.68f, 0.91f, "m4", new Color(0xFFDD8C)),

    AUG("AUG", Cat.RIFLE, Team.CT, 3300,
            28, 0.90f, 1400, 2300, 666, true, 30, 90, 3.8f,
            0.009f, 0.080f, 0.016f, 2.4f, 1.2f, 7.0f, 2, 0.72f, 0.90f, "m4", new Color(0xFFDD8C)),

    SG553("SG 553", Cat.RIFLE, Team.T, 3000,
            30, 1.00f, 1400, 2300, 600, true, 30, 90, 3.8f,
            0.009f, 0.082f, 0.018f, 2.6f, 1.3f, 6.8f, 3, 0.74f, 0.89f, "ak", new Color(0xFFDD8C)),

    SCOUT("SSG 08 «Скаут»", Cat.SNIPER, Team.ANY, 1700,
            88, 0.85f, 2000, 3000, 85, false, 10, 90, 3.7f,
            0.004f, 0.140f, 0.090f, 6.0f, 1.0f, 3.5f, 3, 0.90f, 1.02f, "scout", new Color(0xFFFFFF)),

    AWP("AWP", Cat.SNIPER, Team.ANY, 4750,
            115, 0.975f, 2600, 3600, 60, false, 5, 30, 3.7f,
            0.003f, 0.160f, 0.120f, 8.0f, 1.2f, 3.0f, 4, 0.98f, 0.85f, "awp", new Color(0xFFFFFF)),

    AUTO_SNIPER("SCAR-20", Cat.SNIPER, Team.ANY, 5000,
            80, 0.95f, 2200, 3200, 240, true, 20, 90, 5.0f,
            0.005f, 0.150f, 0.075f, 5.5f, 1.4f, 3.2f, 3, 0.92f, 0.82f, "scout", new Color(0xFFFFFF)),

    M249("M249", Cat.HEAVY, Team.ANY, 5200,
            32, 0.80f, 1200, 2000, 750, true, 100, 200, 5.7f,
            0.026f, 0.100f, 0.014f, 2.2f, 1.6f, 6.0f, 2, 0.60f, 0.80f, "heavy", new Color(0xFFD27F));

    public enum Cat { MELEE, PISTOL, SMG, SHOTGUN, RIFLE, SNIPER, HEAVY }
    public enum Team { ANY, T, CT }

    public final String label;
    public final Cat cat;
    public final Team team;
    public final int price;

    public final float damage;
    public final float armorPen;      // 0..1 — сколько урона проходит сквозь броню
    public final float falloffStart;  // с какой дистанции урон падает
    public final float maxRange;      // дальше урона нет
    public final float rpm;           // выстрелов в минуту
    public final boolean automatic;
    public final int magSize;
    public final int reserveAmmo;
    public final float reloadTime;

    public final float spreadBase;    // разброс стоя (рад)
    public final float spreadMove;    // добавка при беге
    public final float spreadPerShot; // рост разброса за выстрел
    public final float recoilUp;      // вертикальная отдача (град)
    public final float recoilSide;    // горизонтальная отдача (град)
    public final float recoilRecover; // скорость восстановления
    public final int penPower;        // сколько «слоёв» материала пробивает
    public final float accuracy;      // базовая меткость для ботов
    public final float moveSpeed;     // множитель скорости передвижения
    public final String sound;
    public final Color tracer;

    public int pellets = 1;
    public int zoomLevels = 0;

    WeaponType(String label, Cat cat, Team team, int price,
               float damage, float armorPen, float falloffStart, float maxRange, float rpm, boolean automatic,
               int magSize, int reserveAmmo, float reloadTime,
               float spreadBase, float spreadMove, float spreadPerShot,
               float recoilUp, float recoilSide, float recoilRecover,
               int penPower, float accuracy, float moveSpeed, String sound, Color tracer) {
        this.label = label; this.cat = cat; this.team = team; this.price = price;
        this.damage = damage; this.armorPen = armorPen; this.falloffStart = falloffStart;
        this.maxRange = maxRange; this.rpm = rpm; this.automatic = automatic;
        this.magSize = magSize; this.reserveAmmo = reserveAmmo; this.reloadTime = reloadTime;
        this.spreadBase = spreadBase; this.spreadMove = spreadMove; this.spreadPerShot = spreadPerShot;
        this.recoilUp = recoilUp; this.recoilSide = recoilSide; this.recoilRecover = recoilRecover;
        this.penPower = penPower; this.accuracy = accuracy; this.moveSpeed = moveSpeed;
        this.sound = sound; this.tracer = tracer;
    }

    static {
        NOVA.pellets = 9;
        XM1014.pellets = 9;
        SCOUT.zoomLevels = 1;
        AWP.zoomLevels = 2;
        AUTO_SNIPER.zoomLevels = 1;
        AUG.zoomLevels = 1;
        SG553.zoomLevels = 1;
    }

    public float shotInterval() { return 60f / rpm; }

    /** Награда за убийство этим оружием. */
    public int killReward() {
        return switch (cat) {
            case MELEE -> 1500;
            case PISTOL -> 300;
            case SMG -> 600;
            case SHOTGUN -> 900;
            case SNIPER -> cat == Cat.SNIPER && this == AWP ? 100 : 300;
            default -> 300;
        };
    }

    public boolean allowedFor(int team) {
        if (this.team == Team.ANY) return true;
        return (this.team == Team.T) == (team == Actor.TEAM_T);
    }

    public static java.util.List<WeaponType> buyable(int team) {
        java.util.List<WeaponType> out = new java.util.ArrayList<>();
        for (WeaponType w : values()) {
            if (w.cat == Cat.MELEE) continue;
            if (w.allowedFor(team)) out.add(w);
        }
        return out;
    }
}
