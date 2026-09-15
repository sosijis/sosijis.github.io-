package com.sosijis.shooter;

/** Экземпляр оружия: магазин, перезарядка, накопленная отдача и паттерн разброса. */
public class Weapon {
    public final WeaponType type;
    public int ammo;
    public int reserve;
    public float cooldown;
    public float reloadTimer;
    public boolean reloading;
    public int sprayIndex;
    public float sprayIdle;
    public int zoom;
    public float heat;          // текущий накопленный разброс
    public float flashTimer;    // остаток вспышки у дульного среза

    public Weapon(WeaponType type) {
        this.type = type;
        this.ammo = type.magSize;
        this.reserve = type.reserveAmmo;
    }

    public void update(float dt) {
        cooldown -= dt;
        sprayIdle += dt;
        flashTimer = Math.max(0, flashTimer - dt);
        if (sprayIdle > 0.35f) { sprayIndex = 0; }
        heat = Math.max(0, heat - type.recoilRecover * 0.02f * dt * 60f);
        if (reloading) {
            reloadTimer -= dt;
            if (reloadTimer <= 0) finishReload();
        }
    }

    public boolean canFire() {
        return !reloading && cooldown <= 0 && (ammo > 0 || type.cat == WeaponType.Cat.MELEE);
    }

    public boolean needsReload() { return ammo <= 0 && reserve > 0; }
    public boolean isEmptyCompletely() { return ammo <= 0 && reserve <= 0; }

    public void consume() {
        if (type.cat == WeaponType.Cat.MELEE) return;
        ammo--;
        cooldown = type.shotInterval();
        flashTimer = 0.055f;
        sprayIndex++;
        sprayIdle = 0;
        heat = Math.min(1.6f, heat + type.spreadPerShot * 12f);
    }

    public void startReload() {
        if (reloading || ammo >= type.magSize || reserve <= 0 || type.cat == WeaponType.Cat.MELEE) return;
        reloading = true;
        reloadTimer = type.reloadTime;
        zoom = 0;
    }

    public void cancelReload() { reloading = false; reloadTimer = 0; }

    private void finishReload() {
        reloading = false;
        int need = type.magSize - ammo;
        int take = Math.min(need, reserve);
        ammo += take;
        reserve -= take;
        sprayIndex = 0;
        heat = 0;
    }

    public void refill() {
        ammo = type.magSize;
        reserve = type.reserveAmmo;
        reloading = false;
        reloadTimer = 0;
        heat = 0;
        sprayIndex = 0;
    }

    public float reloadProgress() {
        return type.reloadTime <= 0 ? 1 : 1f - MathUtil.clamp(reloadTimer / type.reloadTime, 0, 1);
    }

    /**
     * Паттерн отдачи. Первые выстрелы уводят ствол вверх, дальше добавляется
     * характерный «зигзаг» по горизонтали — как у автоматов в тактических шутерах.
     */
    public float[] sprayOffset() {
        int i = Math.max(0, sprayIndex - 1);
        float up = type.recoilUp * (float) Math.min(1.0, 0.35 + i * 0.22);
        float vert = 0;
        for (int k = 0; k <= i; k++) vert += type.recoilUp * (float) Math.exp(-k * 0.16);
        vert = Math.min(vert, type.recoilUp * 7.5f);
        float side = (float) (Math.sin(i * 0.9) + Math.sin(i * 0.31) * 0.7) * type.recoilSide * Math.min(1f, i / 4f);
        return new float[]{vert * 0.01f, side * 0.012f, up};
    }

    public float currentSpread(boolean moving, boolean crouching, boolean airborne) {
        float s = type.spreadBase + heat * type.spreadPerShot * 2.2f;
        if (moving) s += type.spreadMove;
        if (crouching) s *= 0.55f;
        if (airborne) s *= 3.0f;
        if (zoom > 0) s *= 0.18f;
        return s;
    }
}
