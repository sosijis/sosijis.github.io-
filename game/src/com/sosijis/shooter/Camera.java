package com.sosijis.shooter;

/** Камера с плавным следованием, «подглядыванием» в сторону прицела и тряской. */
public class Camera {
    public final Vec2 pos = new Vec2();
    public float zoom = 1f;
    public float targetZoom = 1f;
    public int viewW = 1280, viewH = 760;
    public float shake;
    private float shakeX, shakeY, shakeT;

    public void resize(int w, int h) { viewW = w; viewH = h; }

    public void follow(Vec2 target, Vec2 lookAt, float dt, boolean instant) {
        Vec2 want = target.cpy();
        if (lookAt != null) {
            Vec2 d = lookAt.cpy().sub(target);
            d.limit(230f);
            want.addScaled(d, 0.32f);
        }
        if (instant) pos.set(want);
        else {
            pos.x = MathUtil.damp(pos.x, want.x, 0.0001f, dt);
            pos.y = MathUtil.damp(pos.y, want.y, 0.0001f, dt);
        }
        zoom = MathUtil.damp(zoom, targetZoom, 0.002f, dt);

        shakeT += dt;
        shake = Math.max(0, shake - dt * 2.4f);
        float amp = shake * 16f;
        shakeX = (float) (Math.sin(shakeT * 47.3) + Math.sin(shakeT * 91.1) * 0.6) * amp;
        shakeY = (float) (Math.cos(shakeT * 53.7) + Math.cos(shakeT * 83.3) * 0.6) * amp;
    }

    /** Небольшой выход за границы карты, чтобы боец у стены не оказывался вплотную к краю экрана. */
    public void clampTo(GameMap map) {
        float over = 170f;
        float halfW = viewW / (2f * zoom) - over, halfH = viewH / (2f * zoom) - over;
        if (map.pixelW() > halfW * 2 && halfW > 0) pos.x = MathUtil.clamp(pos.x, halfW, map.pixelW() - halfW);
        else pos.x = map.pixelW() / 2f;
        if (map.pixelH() > halfH * 2 && halfH > 0) pos.y = MathUtil.clamp(pos.y, halfH, map.pixelH() - halfH);
        else pos.y = map.pixelH() / 2f;
    }

    public float offX() { return viewW / 2f - (pos.x + shakeX) * zoom; }
    public float offY() { return viewH / 2f - (pos.y + shakeY) * zoom; }

    public float worldToScreenX(float x) { return x * zoom + offX(); }
    public float worldToScreenY(float y) { return y * zoom + offY(); }
    public float screenToWorldX(float x) { return (x - offX()) / zoom; }
    public float screenToWorldY(float y) { return (y - offY()) / zoom; }

    public boolean visible(float x, float y, float margin) {
        float sx = worldToScreenX(x), sy = worldToScreenY(y);
        return sx > -margin && sy > -margin && sx < viewW + margin && sy < viewH + margin;
    }

    public void addShake(float amount) { shake = Math.min(3f, shake + amount); }
}
