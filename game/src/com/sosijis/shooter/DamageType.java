package com.sosijis.shooter;

/** Источник урона — влияет на броню, звук и отображение в киллфиде. */
public enum DamageType {
    BULLET("пуля"),
    MELEE("нож"),
    EXPLOSION("взрыв"),
    FIRE("огонь"),
    FALL("падение"),
    BOMB("бомба");

    public final String label;
    DamageType(String label) { this.label = label; }
}
