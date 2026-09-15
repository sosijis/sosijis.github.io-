package com.sosijis.shooter;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/** Управляемый игроком боец. */
public class Player extends Actor {

    public float recoilOffset;       // увод прицела от отдачи (восстанавливается)
    public int lastSlot = SLOT_SECONDARY;
    public float interactProgress;
    public boolean wantsBuyMenu;
    public float zoomFov = 1f;
    public Vec2 aimWorld = new Vec2();

    public Player(int team, String name) {
        super(team, name);
        viewDist = 1020f;
        viewFov = 1.85f;
    }

    @Override
    protected void applyRecoilToAim(float[] recoil) {
        recoilOffset += recoil[0] * 0.55f + recoil[1] * 0.4f;
        recoilOffset = MathUtil.clamp(recoilOffset, -0.32f, 0.32f);
    }

    @Override
    public void update(float dt, Game game) {
        Input in = game.input;
        if (!alive) {
            vel.set(0, 0);
            deathTimer += dt;
            updateCommon(dt, game);
            return;
        }

        // --- Прицеливание ---
        aimWorld.set(game.camera.screenToWorldX(in.mouseX), game.camera.screenToWorldY(in.mouseY));
        float mouseAngle = (float) Math.atan2(aimWorld.y - pos.y, aimWorld.x - pos.x);
        recoilOffset = MathUtil.damp(recoilOffset, 0, 0.02f, dt);
        aim = MathUtil.wrapAngle(mouseAngle + recoilOffset);

        // --- Движение ---
        crouching = game.settings.toggleCrouch ? crouching : (in.key(KeyEvent.VK_CONTROL) || in.key(KeyEvent.VK_C));
        if (game.settings.toggleCrouch && in.keyPressed(KeyEvent.VK_CONTROL)) crouching = !crouching;
        walking = in.key(KeyEvent.VK_SHIFT);

        float mx = 0, my = 0;
        if (in.key(KeyEvent.VK_W)) my -= 1;
        if (in.key(KeyEvent.VK_S)) my += 1;
        if (in.key(KeyEvent.VK_A)) mx -= 1;
        if (in.key(KeyEvent.VK_D)) mx += 1;
        if (flash > 1.05f) { mx *= 0.7f; my *= 0.7f; }

        Vec2 want = new Vec2(mx, my);
        if (want.lenSq() > 0) want.norm().mul(moveSpeed());
        if (game.movementLocked) { want.set(0, 0); vel.set(0, 0); }
        float accel = want.lenSq() > 0 ? 14f : 18f;
        vel.x = MathUtil.damp(vel.x, want.x, 0.0005f, dt * accel / 14f);
        vel.y = MathUtil.damp(vel.y, want.y, 0.0005f, dt * accel / 14f);
        moving = vel.lenSq() > 900;

        Tile floor = game.map.tileAtWorld(pos.x, pos.y);
        float surf = floor.mat == Tile.Mat.WATER ? 0.62f : 1f;
        game.map.moveCircle(pos, vel.x * dt * surf, vel.y * dt * surf, radius);
        game.pushApart(this);

        // --- Оружие ---
        if (in.keyPressed(KeyEvent.VK_1)) switchTo(SLOT_PRIMARY);
        if (in.keyPressed(KeyEvent.VK_2)) switchTo(SLOT_SECONDARY);
        if (in.keyPressed(KeyEvent.VK_3)) switchTo(SLOT_KNIFE);
        if (in.keyPressed(KeyEvent.VK_4)) { if (slot == SLOT_NADE) cycleNade(); else switchTo(SLOT_NADE); }
        if (in.keyPressed(KeyEvent.VK_5)) switchTo(SLOT_BOMB);
        if (in.keyPressed(KeyEvent.VK_Q)) switchTo(lastSlot);
        if (in.wheel != 0) {
            int dir = in.wheel > 0 ? 1 : -1;
            for (int i = 0; i < 5; i++) {
                int s = ((slot + dir * (i + 1)) % 5 + 5) % 5;
                if (canSelect(s)) { switchTo(s); break; }
            }
        }
        if (in.keyPressed(KeyEvent.VK_R)) currentWeapon().startReload();

        Weapon w = currentWeapon();
        if (in.mouseClicked(MouseEvent.BUTTON3) && w.type.zoomLevels > 0 && isHoldingGun()) {
            w.zoom = (w.zoom + 1) % (w.type.zoomLevels + 1);
            game.sfx.play("zoom", 0.5f, 1f);
        }
        zoomFov = MathUtil.damp(zoomFov, w.zoom == 0 ? 1f : (w.zoom == 1 ? 0.62f : 0.42f), 0.002f, dt);

        // --- Гранаты ---
        if (slot == SLOT_NADE && nades[nadeSel] > 0 && !game.movementLocked) {
            boolean lmb = in.mouse(MouseEvent.BUTTON1);
            boolean rmb = in.mouse(MouseEvent.BUTTON3);
            if (lmb || rmb) { nadeHeld = true; nadeCook += dt; }
            else if (nadeHeld) {
                float power = rmb ? 0.45f : MathUtil.clamp(0.55f + nadeCook * 0.9f, 0.55f, 1.25f);
                game.throwGrenade(this, nadeSel, power, nadeCook);
                nades[nadeSel]--;
                nadeHeld = false;
                nadeCook = 0;
                if (nades[nadeSel] == 0) {
                    if (totalNades() > 0) cycleNade();
                    else switchTo(primary != null ? SLOT_PRIMARY : SLOT_SECONDARY);
                }
            }
        } else if (slot != SLOT_NADE) {
            nadeHeld = false; nadeCook = 0;
        }

        // --- Стрельба ---
        if ((isHoldingGun() || slot == SLOT_KNIFE) && !game.movementLocked) {
            boolean auto = w.type.automatic || slot == SLOT_KNIFE;
            boolean want2 = auto ? in.mouseHeldOrClicked(MouseEvent.BUTTON1) : in.mouseClicked(MouseEvent.BUTTON1);
            if (want2) tryShoot(game);
        }

        // --- Взаимодействие (закладка / разминирование / заложники) ---
        boolean use = in.key(KeyEvent.VK_E);
        game.mode.handleUse(game, this, use, dt);

        if (in.keyPressed(KeyEvent.VK_B)) wantsBuyMenu = true;

        updateCommon(dt, game);
    }

    private boolean canSelect(int s) {
        return switch (s) {
            case SLOT_PRIMARY -> primary != null;
            case SLOT_SECONDARY -> secondary != null;
            case SLOT_KNIFE -> true;
            case SLOT_NADE -> totalNades() > 0;
            case SLOT_BOMB -> hasBomb;
            default -> false;
        };
    }

    private void switchTo(int s) {
        if (!canSelect(s) || s == slot) return;
        lastSlot = slot;
        selectSlot(s);
    }
}
