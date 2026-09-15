package com.sosijis.shooter;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Звуковой движок: все звуки синтезируются процедурно (никаких внешних файлов),
 * микшируются в один поток с панорамой, затуханием по расстоянию и приглушением за стенами.
 */
public class Sfx {

    private static final float SR = 44100f;
    private static final int BUF_FRAMES = 1024;

    private final Settings settings;
    private final Map<String, float[]> bank = new HashMap<>();
    private final List<Voice> voices = new ArrayList<>();
    private SourceDataLine line;
    private Thread mixThread;
    private volatile boolean running;
    private final byte[] outBytes = new byte[BUF_FRAMES * 4];
    private final float[] mixL = new float[BUF_FRAMES];
    private final float[] mixR = new float[BUF_FRAMES];

    public Vec2 listener = new Vec2();
    public BiPredicate<Vec2, Vec2> losTest = (a, b) -> true;
    public float earRing = 0f;       // 0..1, звон после взрыва/флешки
    private float ringPhase;
    private float duck = 1f;         // общее приглушение (пауза, меню)

    private Voice musicVoice;

    private static class Voice {
        float[] data;
        double pos;
        double rate = 1;
        float volL, volR;
        boolean loop;
        boolean active = true;
        float fade = 1f;
        float fadeTarget = 1f;
        String name;
    }

    public Sfx(Settings settings) {
        this.settings = settings;
        buildBank();
        try {
            AudioFormat fmt = new AudioFormat(SR, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(fmt, BUF_FRAMES * 8);
            line.start();
            running = true;
            mixThread = new Thread(this::mixLoop, "sfx-mixer");
            mixThread.setDaemon(true);
            mixThread.start();
        } catch (Exception e) {
            line = null;
            System.err.println("[звук] аудиоустройство недоступно, играем без звука: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        if (line != null) { line.stop(); line.close(); }
    }

    public void setDuck(float d) { duck = MathUtil.clamp(d, 0, 1); }

    // ----------------------------------------------------------- Воспроизведение

    public void play(String name, float vol, float pitch) {
        addVoice(name, vol, vol, pitch, false);
    }

    public void playUi(String name) {
        addVoice(name, settings.uiVolume, settings.uiVolume, 1f, false);
    }

    public void playAt(String name, Vec2 pos, float vol, float pitch) {
        if (!settings.positionalAudio) { play(name, vol * 0.7f, pitch); return; }
        float dx = pos.x - listener.x, dy = pos.y - listener.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float maxD = 1500f;
        if (dist > maxD) return;
        float att = 1f - MathUtil.clamp(dist / maxD, 0, 1);
        att *= att;
        if (settings.occlusion && dist > 40 && !losTest.test(listener, pos)) att *= 0.38f;
        float pan = MathUtil.clamp(dx / 700f, -1f, 1f);
        float l = vol * att * (float) Math.sqrt(0.5 * (1 - pan));
        float r = vol * att * (float) Math.sqrt(0.5 * (1 + pan));
        addVoice(name, l * 1.4f, r * 1.4f, pitch, false);
    }

    private Voice addVoice(String name, float vl, float vr, float pitch, boolean loop) {
        float[] data = bank.get(name);
        if (data == null || line == null) return null;
        if (vl + vr < 0.0015f) return null;
        Voice v = new Voice();
        v.data = data;
        v.volL = vl; v.volR = vr;
        v.rate = MathUtil.clamp(pitch, 0.25f, 4f);
        v.loop = loop;
        v.name = name;
        synchronized (voices) {
            if (voices.size() > 96) voices.remove(0);
            voices.add(v);
        }
        return v;
    }

    public void startMusic() {
        if (musicVoice != null) return;
        musicVoice = addVoice("music", 1f, 1f, 1f, true);
        if (musicVoice != null) musicVoice.loop = true;
    }

    public void stopMusic() {
        if (musicVoice != null) { musicVoice.fadeTarget = 0; musicVoice = null; }
    }

    public void stopAllLoops() {
        synchronized (voices) {
            for (Voice v : voices) if (v.loop) v.fadeTarget = 0;
        }
        musicVoice = null;
    }

    // ----------------------------------------------------------- Микшер

    private void mixLoop() {
        while (running) {
            try {
                mixBuffer();
                line.write(outBytes, 0, outBytes.length);
            } catch (Exception e) {
                running = false;
            }
        }
    }

    private void mixBuffer() {
        java.util.Arrays.fill(mixL, 0f);
        java.util.Arrays.fill(mixR, 0f);
        float master = settings.masterVolume * duck;
        float sfxV = settings.sfxVolume * master;
        float musV = settings.musicVolume * master;

        synchronized (voices) {
            for (int i = voices.size() - 1; i >= 0; i--) {
                Voice v = voices.get(i);
                float gain = "music".equals(v.name) ? musV : sfxV;
                for (int n = 0; n < BUF_FRAMES; n++) {
                    int idx = (int) v.pos;
                    if (idx >= v.data.length - 1) {
                        if (v.loop) { v.pos -= v.data.length; idx = (int) v.pos; if (idx < 0) { idx = 0; v.pos = 0; } }
                        else { v.active = false; break; }
                    }
                    float frac = (float) (v.pos - idx);
                    float s = v.data[idx] * (1 - frac) + v.data[Math.min(idx + 1, v.data.length - 1)] * frac;
                    v.fade += (v.fadeTarget - v.fade) * 0.0008f;
                    s *= gain * v.fade;
                    mixL[n] += s * v.volL;
                    mixR[n] += s * v.volR;
                    v.pos += v.rate;
                }
                if (!v.active || (v.fadeTarget == 0 && v.fade < 0.01f)) voices.remove(i);
            }
        }

        // Звон в ушах
        if (settings.tinnitus && earRing > 0.01f) {
            float amp = earRing * 0.10f * master;
            for (int n = 0; n < BUF_FRAMES; n++) {
                ringPhase += 4300f / SR * MathUtil.TAU;
                float s = (float) Math.sin(ringPhase) * amp;
                s += (float) Math.sin(ringPhase * 1.51) * amp * 0.4f;
                mixL[n] += s; mixR[n] += s;
                // общий приглушённый «ватный» эффект
                mixL[n] *= (1f - earRing * 0.55f);
                mixR[n] *= (1f - earRing * 0.55f);
            }
        }

        for (int n = 0; n < BUF_FRAMES; n++) {
            int l = clip16(mixL[n]);
            int r = clip16(mixR[n]);
            outBytes[n * 4] = (byte) (l & 0xFF);
            outBytes[n * 4 + 1] = (byte) ((l >> 8) & 0xFF);
            outBytes[n * 4 + 2] = (byte) (r & 0xFF);
            outBytes[n * 4 + 3] = (byte) ((r >> 8) & 0xFF);
        }
    }

    private static int clip16(float v) {
        // мягкое ограничение, чтобы плотный бой не хрипел
        float x = (float) Math.tanh(v * 0.9) * 0.92f;
        int s = (int) (x * 32767);
        return MathUtil.clamp(s, -32768, 32767);
    }

    // ----------------------------------------------------------- Синтез

    private void buildBank() {
        bank.put("pistol", gunshot(0.28f, 210f, 0.55f, 0.9f, 1600f));
        bank.put("pistol_s", gunshot(0.20f, 260f, 0.35f, 0.6f, 2400f));
        bank.put("deagle", gunshot(0.42f, 130f, 0.95f, 1.25f, 1100f));
        bank.put("smg", gunshot(0.20f, 240f, 0.48f, 0.8f, 2100f));
        bank.put("smg_s", gunshot(0.16f, 300f, 0.28f, 0.5f, 3000f));
        bank.put("rifle", gunshot(0.32f, 170f, 0.72f, 1.0f, 1500f));
        bank.put("ak", gunshot(0.36f, 140f, 0.85f, 1.12f, 1250f));
        bank.put("m4", gunshot(0.30f, 185f, 0.70f, 0.95f, 1700f));
        bank.put("shotgun", gunshot(0.50f, 105f, 1.00f, 1.3f, 900f));
        bank.put("scout", gunshot(0.44f, 150f, 0.88f, 1.15f, 1300f));
        bank.put("awp", gunshot(0.62f, 95f, 1.10f, 1.5f, 850f));
        bank.put("heavy", gunshot(0.28f, 160f, 0.78f, 1.05f, 1400f));
        bank.put("knife", swoosh());
        bank.put("dryfire", click(0.09f, 2600f, 0.35f));
        bank.put("reload_out", click(0.14f, 900f, 0.5f));
        bank.put("reload_in", click(0.16f, 700f, 0.6f));
        bank.put("reload_done", click(0.12f, 1500f, 0.55f));
        bank.put("zoom", click(0.07f, 3200f, 0.3f));
        bank.put("explosion", explosion(1.9f));
        bank.put("flash", flashbang());
        bank.put("smoke_pop", smokePop());
        bank.put("fire", fireCrackle());
        bank.put("nade_bounce", bounce());
        bank.put("nade_pin", click(0.10f, 2200f, 0.4f));
        bank.put("hit", hitmarker());
        bank.put("hit_armor", armorHit());
        bank.put("headshot", headshot());
        bank.put("hurt", hurt());
        bank.put("death", death());
        bank.put("bomb_beep", beep(1400f, 0.09f));
        bank.put("bomb_plant", beep(760f, 0.5f));
        bank.put("bomb_defuse", beep(980f, 0.5f));
        bank.put("round_win", chime(true));
        bank.put("round_lose", chime(false));
        bank.put("ui_click", beep(1150f, 0.05f));
        bank.put("ui_hover", beep(760f, 0.03f));
        bank.put("buy", beep(1500f, 0.12f));
        bank.put("pickup", beep(1900f, 0.08f));
        bank.put("glass", glassBreak());
        bank.put("step_concrete", step(1200f, 0.075f, 0.55f));
        bank.put("step_metal", step(2600f, 0.09f, 0.75f));
        bank.put("step_wood", step(850f, 0.085f, 0.6f));
        bank.put("step_grass", step(3400f, 0.10f, 0.35f));
        bank.put("step_sand", step(2000f, 0.11f, 0.3f));
        bank.put("step_water", step(1500f, 0.14f, 0.5f));
        bank.put("music", music());
    }

    private static int n(float sec) { return (int) (sec * SR); }

    private static float noise() { return MathUtil.RNG.nextFloat() * 2f - 1f; }

    /** Однополюсный ФНЧ. */
    private static void lowpass(float[] b, float cutoff) {
        float rc = 1f / (MathUtil.TAU * cutoff);
        float dt = 1f / SR;
        float a = dt / (rc + dt);
        float prev = 0;
        for (int i = 0; i < b.length; i++) { prev += a * (b[i] - prev); b[i] = prev; }
    }

    private static void highpass(float[] b, float cutoff) {
        float rc = 1f / (MathUtil.TAU * cutoff);
        float dt = 1f / SR;
        float a = rc / (rc + dt);
        float prevIn = 0, prevOut = 0;
        for (int i = 0; i < b.length; i++) {
            float out = a * (prevOut + b[i] - prevIn);
            prevIn = b[i]; prevOut = out; b[i] = out;
        }
    }

    private static void normalize(float[] b, float peak) {
        float max = 1e-6f;
        for (float v : b) max = Math.max(max, Math.abs(v));
        float k = peak / max;
        for (int i = 0; i < b.length; i++) b[i] *= k;
    }

    /** Выстрел: щелчок + шумовой хвост + низкочастотный «удар». */
    private float[] gunshot(float len, float bodyHz, float bass, float loud, float bright) {
        float[] b = new float[n(len)];
        float[] crack = new float[b.length];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            crack[i] = noise() * (float) Math.exp(-t * 55);
        }
        highpass(crack, bright * 0.5f);
        lowpass(crack, bright * 3.2f);

        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 18);
            float thump = (float) Math.sin(MathUtil.TAU * bodyHz * t * (1 - t * 2.2)) * (float) Math.exp(-t * 26) * bass;
            float tail = noise() * (float) Math.exp(-t * 9) * 0.32f;
            b[i] = crack[i] * 1.5f * env + thump + tail;
        }
        // Короткое эхо для «объёма»
        int d = n(0.045f);
        for (int i = d; i < b.length; i++) b[i] += b[i - d] * 0.22f;
        int d2 = n(0.11f);
        for (int i = d2; i < b.length; i++) b[i] += b[i - d2] * 0.13f;
        normalize(b, Math.min(0.98f, 0.62f * loud));
        return b;
    }

    private float[] swoosh() {
        float[] b = new float[n(0.22f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            b[i] = noise() * (float) Math.sin(Math.PI * t) * 0.8f;
        }
        bandpassSweep(b, 500f, 3500f);
        normalize(b, 0.4f);
        return b;
    }

    private static void bandpassSweep(float[] b, float f0, float f1) {
        float prev = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            float cutoff = MathUtil.lerp(f0, f1, t);
            float rc = 1f / (MathUtil.TAU * cutoff);
            float dt = 1f / SR;
            float a = dt / (rc + dt);
            prev += a * (b[i] - prev);
            b[i] = prev;
        }
    }

    private float[] click(float len, float freq, float amp) {
        float[] b = new float[n(len)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 60);
            b[i] = (noise() * 0.6f + (float) Math.sin(MathUtil.TAU * freq * t) * 0.4f) * env * amp;
        }
        return b;
    }

    private float[] explosion(float len) {
        float[] b = new float[n(len)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 4.0);
            float rumble = (float) (Math.sin(MathUtil.TAU * 42 * t) + Math.sin(MathUtil.TAU * 63 * t + 1)) * 0.5f;
            b[i] = (noise() * 0.9f + rumble * 1.1f) * env;
        }
        lowpass(b, 900f);
        float[] crack = new float[n(0.25f)];
        for (int i = 0; i < crack.length; i++) {
            float t = i / SR;
            crack[i] = noise() * (float) Math.exp(-t * 40);
        }
        highpass(crack, 1800f);
        for (int i = 0; i < crack.length; i++) b[i] += crack[i] * 0.7f;
        int d = n(0.16f);
        for (int i = d; i < b.length; i++) b[i] += b[i - d] * 0.3f;
        normalize(b, 0.95f);
        return b;
    }

    private float[] flashbang() {
        float[] b = new float[n(1.4f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 16);
            b[i] = noise() * env;
        }
        highpass(b, 900f);
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            b[i] += (float) Math.sin(MathUtil.TAU * 3800 * t) * (float) Math.exp(-t * 2.2) * 0.35f;
            b[i] += (float) Math.sin(MathUtil.TAU * 70 * t) * (float) Math.exp(-t * 9) * 0.5f;
        }
        normalize(b, 0.95f);
        return b;
    }

    private float[] smokePop() {
        float[] b = new float[n(1.6f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float pop = (float) Math.exp(-t * 30);
            float hiss = noise() * (float) Math.min(1, t * 6) * (float) Math.exp(-t * 1.5f) * 0.5f;
            b[i] = noise() * pop * 0.8f + hiss;
        }
        highpass(b, 1200f);
        normalize(b, 0.55f);
        return b;
    }

    private float[] fireCrackle() {
        float[] b = new float[n(0.6f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 6);
            float crack = MathUtil.chance(0.004f) ? 1.6f : 0f;
            b[i] = (noise() * 0.5f + crack * noise()) * env;
        }
        lowpass(b, 2400f);
        highpass(b, 220f);
        normalize(b, 0.5f);
        return b;
    }

    private float[] bounce() {
        float[] b = new float[n(0.18f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 34);
            b[i] = ((float) Math.sin(MathUtil.TAU * 1450 * t) * 0.6f
                  + (float) Math.sin(MathUtil.TAU * 2300 * t) * 0.3f
                  + noise() * 0.3f) * env;
        }
        normalize(b, 0.45f);
        return b;
    }

    private float[] hitmarker() {
        float[] b = new float[n(0.1f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            b[i] = (float) Math.sin(MathUtil.TAU * 1800 * t) * (float) Math.exp(-t * 55) * 0.5f;
        }
        return b;
    }

    private float[] armorHit() {
        float[] b = new float[n(0.14f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 42);
            b[i] = ((float) Math.sin(MathUtil.TAU * 2400 * t) * 0.5f + noise() * 0.6f) * env;
        }
        highpass(b, 1400f);
        normalize(b, 0.5f);
        return b;
    }

    private float[] headshot() {
        float[] b = new float[n(0.22f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 26);
            b[i] = ((float) Math.sin(MathUtil.TAU * 520 * t) * 0.5f + noise() * 0.7f) * env;
        }
        lowpass(b, 3000f);
        normalize(b, 0.62f);
        return b;
    }

    private float[] hurt() {
        float[] b = new float[n(0.35f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 9);
            float f = 180f - t * 90f;
            b[i] = ((float) Math.sin(MathUtil.TAU * f * t) * 0.6f + noise() * 0.5f) * env;
        }
        lowpass(b, 1200f);
        normalize(b, 0.5f);
        return b;
    }

    private float[] death() {
        float[] b = new float[n(0.9f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 3.2);
            float f = 160f - t * 70f;
            b[i] = ((float) Math.sin(MathUtil.TAU * f * t) * 0.55f + noise() * 0.45f) * env;
        }
        lowpass(b, 900f);
        normalize(b, 0.55f);
        return b;
    }

    private float[] beep(float freq, float len) {
        float[] b = new float[n(len)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.min(1, t * 80) * (float) Math.exp(-t * (3f / len));
            b[i] = (float) Math.sin(MathUtil.TAU * freq * t) * env * 0.4f;
        }
        return b;
    }

    private float[] chime(boolean win) {
        float[] notes = win ? new float[]{523.25f, 659.25f, 783.99f, 1046.5f}
                            : new float[]{440f, 415.3f, 349.23f, 261.6f};
        float[] b = new float[n(1.3f)];
        for (int k = 0; k < notes.length; k++) {
            int off = n(0.11f * k);
            for (int i = 0; i + off < b.length; i++) {
                float t = i / SR;
                float env = (float) Math.exp(-t * 3.4);
                b[i + off] += (float) Math.sin(MathUtil.TAU * notes[k] * t) * env * 0.22f;
                b[i + off] += (float) Math.sin(MathUtil.TAU * notes[k] * 2 * t) * env * 0.07f;
            }
        }
        normalize(b, 0.55f);
        return b;
    }

    private float[] glassBreak() {
        float[] b = new float[n(0.7f)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 7);
            float shards = 0;
            for (int k = 0; k < 5; k++) shards += (float) Math.sin(MathUtil.TAU * (2600 + k * 1370) * t) * 0.2f;
            b[i] = (noise() * 0.8f + shards) * env;
        }
        highpass(b, 2200f);
        normalize(b, 0.55f);
        return b;
    }

    private float[] step(float bright, float len, float amp) {
        float[] b = new float[n(len)];
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float env = (float) Math.exp(-t * 55);
            b[i] = noise() * env * amp;
        }
        lowpass(b, bright);
        highpass(b, 150f);
        normalize(b, 0.4f * amp);
        return b;
    }

    /** Мрачный тактический луп для меню и матча. */
    private float[] music() {
        float bpm = 92f;
        float beat = 60f / bpm;
        float len = beat * 32;
        float[] b = new float[n(len)];

        float[] scale = {110f, 123.47f, 130.81f, 155.56f, 164.81f, 196f, 220f};
        int[] pattern = {0, 2, 4, 2, 5, 4, 2, 0};

        for (int step = 0; step < 32; step++) {
            int off = n(step * beat * 0.5f);
            // бас
            float f = scale[pattern[step % pattern.length]] * 0.5f;
            int dur = n(beat * 0.45f);
            for (int i = 0; i < dur && i + off < b.length; i++) {
                float t = i / SR;
                float env = (float) (Math.min(1, t * 120) * Math.exp(-t * 5.5));
                float s = (float) Math.sin(MathUtil.TAU * f * t);
                s += (float) Math.sin(MathUtil.TAU * f * 2.01 * t) * 0.25f;
                s = (float) Math.tanh(s * 1.4);
                b[i + off] += s * env * 0.22f;
            }
            // пэд
            if (step % 8 == 0) {
                int pdur = n(beat * 3.6f);
                float pf = scale[pattern[(step / 8) % pattern.length]];
                for (int i = 0; i < pdur && i + off < b.length; i++) {
                    float t = i / SR;
                    float env = (float) (Math.min(1, t * 1.6) * Math.exp(-t * 0.5));
                    float s = (float) (Math.sin(MathUtil.TAU * pf * t) * 0.4
                            + Math.sin(MathUtil.TAU * pf * 1.498 * t) * 0.3
                            + Math.sin(MathUtil.TAU * pf * 2 * t + Math.sin(t * 3)) * 0.2);
                    b[i + off] += s * env * 0.07f;
                }
            }
            // бочка
            if (step % 4 == 0) {
                int kd = n(0.25f);
                for (int i = 0; i < kd && i + off < b.length; i++) {
                    float t = i / SR;
                    float env = (float) Math.exp(-t * 16);
                    float fk = 140f * (float) Math.exp(-t * 26) + 44f;
                    b[i + off] += (float) Math.sin(MathUtil.TAU * fk * t) * env * 0.42f;
                }
            }
            // хэт
            if (step % 2 == 1) {
                int hd = n(0.06f);
                for (int i = 0; i < hd && i + off < b.length; i++) {
                    float t = i / SR;
                    b[i + off] += noise() * (float) Math.exp(-t * 90) * 0.10f;
                }
            }
        }
        normalize(b, 0.62f);
        // мягкая склейка петли
        int fade = n(0.12f);
        for (int i = 0; i < fade; i++) {
            float k = i / (float) fade;
            b[i] *= k;
            b[b.length - 1 - i] *= k;
        }
        return b;
    }
}
