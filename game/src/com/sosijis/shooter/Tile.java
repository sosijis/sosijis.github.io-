package com.sosijis.shooter;

import java.awt.Color;

/**
 * Типы тайлов. Каждый описывает, блокирует ли он движение, обзор и пули,
 * насколько хорошо пробивается и как звучит под ногами.
 */
public enum Tile {
    //                solid  opaque  blocksBullet  penetration  material
    FLOOR_CONCRETE   (false, false, false, 1.00f, Mat.CONCRETE, new Color(0x3A3C40), new Color(0x333539)),
    FLOOR_SAND       (false, false, false, 1.00f, Mat.SAND,     new Color(0x6B5C3E), new Color(0x60532F)),
    FLOOR_GRASS      (false, false, false, 1.00f, Mat.GRASS,    new Color(0x3C5233), new Color(0x35492C)),
    FLOOR_METAL      (false, false, false, 1.00f, Mat.METAL,    new Color(0x44484F), new Color(0x3C4048)),
    FLOOR_WOOD       (false, false, false, 1.00f, Mat.WOOD,     new Color(0x5A452C), new Color(0x513E27)),
    WATER            (false, false, false, 1.00f, Mat.WATER,    new Color(0x24455C), new Color(0x1E3B50)),

    WALL_CONCRETE    (true,  true,  true,  0.08f, Mat.CONCRETE, new Color(0x6E7278), new Color(0x555a60)),
    WALL_BRICK       (true,  true,  true,  0.22f, Mat.CONCRETE, new Color(0x7A4A3C), new Color(0x633a2f)),
    WALL_WOOD        (true,  true,  true,  0.62f, Mat.WOOD,     new Color(0x8A6234), new Color(0x6f4e29)),
    CRATE            (true,  true,  true,  0.48f, Mat.WOOD,     new Color(0x9A763E), new Color(0x7c5e31)),
    CRATE_METAL      (true,  true,  true,  0.18f, Mat.METAL,    new Color(0x6A7078), new Color(0x555b62)),
    GLASS            (true,  false, true,  0.90f, Mat.GLASS,    new Color(0x89C8E0), new Color(0x6fa9c0)),
    FENCE            (true,  false, false, 1.00f, Mat.METAL,    new Color(0x8B8F96), new Color(0x74787e)),
    BARREL           (true,  true,  true,  0.55f, Mat.METAL,    new Color(0xA33A2A), new Color(0x822e21)),

    SITE_A           (false, false, false, 1.00f, Mat.CONCRETE, new Color(0x4A3A34), new Color(0x413330)),
    SITE_B           (false, false, false, 1.00f, Mat.CONCRETE, new Color(0x34404A), new Color(0x2e3841)),
    RESCUE           (false, false, false, 1.00f, Mat.CONCRETE, new Color(0x2F4A3A), new Color(0x294034)),
    BUY_T            (false, false, false, 1.00f, Mat.CONCRETE, new Color(0x45373A), new Color(0x3d3033)),
    BUY_CT           (false, false, false, 1.00f, Mat.CONCRETE, new Color(0x35404A), new Color(0x2f3941));

    public enum Mat { CONCRETE, SAND, GRASS, METAL, WOOD, GLASS, WATER }

    public final boolean solid;        // блокирует движение
    public final boolean opaque;       // блокирует обзор
    public final boolean blocksBullet; // пуля должна пробивать
    public final float penetration;    // доля урона, проходящая сквозь тайл (1 = свободно)
    public final Mat mat;
    public final Color color, colorDark;

    Tile(boolean solid, boolean opaque, boolean blocksBullet, float penetration, Mat mat, Color c, Color cd) {
        this.solid = solid; this.opaque = opaque; this.blocksBullet = blocksBullet;
        this.penetration = penetration; this.mat = mat; this.color = c; this.colorDark = cd;
    }

    public boolean isWallLike() { return solid; }
}
