package com.sosijis.shooter;

import java.awt.*;
import java.awt.event.MouseEvent;

/** Мини-фреймворк интерфейса в режиме immediate mode: кнопки, переключатели, слайдеры, списки. */
public class Ui {
    public Graphics2D g;
    public Input in;
    public Sfx sfx;
    public float scale = 1f;
    private int lastHover = -1;
    private int hoverId = -1;
    private int idCounter;

    public static final Color BG = new Color(0x0E1116);
    public static final Color PANEL = new Color(24, 28, 34, 230);
    public static final Color ACCENT = new Color(0xE0A05A);
    public static final Color ACCENT2 = new Color(0x7FB6E8);
    public static final Color TEXT = new Color(0xDCE3EA);
    public static final Color TEXT_DIM = new Color(0x8A929C);

    public void begin(Graphics2D g, Input in, Sfx sfx, float scale) {
        this.g = g; this.in = in; this.sfx = sfx; this.scale = scale;
        idCounter = 0;
        hoverId = -1;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    public void end() {
        if (hoverId != lastHover && hoverId != -1 && sfx != null) sfx.playUi("ui_hover");
        lastHover = hoverId;
    }

    public int s(float v) { return Math.round(v * scale); }

    public void font(int size, boolean bold) {
        g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, s(size)));
    }

    public void text(String t, int x, int y, Color c, int size, boolean bold) {
        font(size, bold);
        g.setColor(c);
        g.drawString(t, x, y);
    }

    public void textCenter(String t, int cx, int y, Color c, int size, boolean bold) {
        font(size, bold);
        g.setColor(c);
        int w = g.getFontMetrics().stringWidth(t);
        g.drawString(t, cx - w / 2f, y);
    }

    public void panel(int x, int y, int w, int h) {
        g.setColor(PANEL);
        g.fillRoundRect(x, y, w, h, s(12), s(12));
        g.setColor(new Color(255, 255, 255, 26));
        g.drawRoundRect(x, y, w, h, s(12), s(12));
    }

    private boolean hovered(int x, int y, int w, int h) {
        int id = idCounter++;
        boolean over = in.mouseX >= x && in.mouseY >= y && in.mouseX <= x + w && in.mouseY <= y + h;
        if (over) hoverId = id;
        return over;
    }

    public boolean button(int x, int y, int w, int h, String label) {
        return button(x, y, w, h, label, true);
    }

    public boolean button(int x, int y, int w, int h, String label, boolean enabled) {
        boolean over = enabled && hovered(x, y, w, h);
        g.setColor(over ? new Color(0x2C3D52) : new Color(255, 255, 255, enabled ? 16 : 8));
        g.fillRoundRect(x, y, w, h, s(8), s(8));
        g.setColor(over ? ACCENT : new Color(255, 255, 255, 30));
        g.drawRoundRect(x, y, w, h, s(8), s(8));
        font(15, true);
        g.setColor(enabled ? (over ? new Color(0xFFE2BC) : TEXT) : new Color(0x5A6068));
        int tw = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + w / 2f - tw / 2f, y + h / 2f + s(5));
        boolean clicked = over && in.mouseClicked(MouseEvent.BUTTON1);
        if (clicked && sfx != null) sfx.playUi("ui_click");
        return clicked;
    }

    /** Переключатель. Возвращает новое значение. */
    public boolean toggle(int x, int y, int w, int h, String label, boolean value) {
        boolean over = hovered(x, y, w, h);
        g.setColor(over ? new Color(255, 255, 255, 22) : new Color(255, 255, 255, 10));
        g.fillRoundRect(x, y, w, h, s(8), s(8));
        font(14, false);
        g.setColor(over ? TEXT : new Color(0xB8C0C8));
        g.drawString(label, x + s(12), y + h / 2f + s(5));

        int tw = s(46), th = s(22);
        int tx = x + w - tw - s(12), ty = y + h / 2 - th / 2;
        g.setColor(value ? new Color(0x3E7A52) : new Color(0x3A3F46));
        g.fillRoundRect(tx, ty, tw, th, th, th);
        g.setColor(value ? new Color(0xB8E8C0) : new Color(0x8A929C));
        g.fillOval(value ? tx + tw - th + s(2) : tx + s(2), ty + s(2), th - s(4), th - s(4));
        font(11, true);
        g.setColor(new Color(0x9AA4B0));
        String st = value ? "ВКЛ" : "ВЫКЛ";
        g.drawString(st, tx - g.getFontMetrics().stringWidth(st) - s(16), y + h / 2f + s(4));

        if (over && in.mouseClicked(MouseEvent.BUTTON1)) {
            if (sfx != null) sfx.playUi("ui_click");
            return !value;
        }
        return value;
    }

    public float slider(int x, int y, int w, int h, String label, float value, float min, float max, String fmt) {
        boolean over = hovered(x, y, w, h);
        g.setColor(over ? new Color(255, 255, 255, 22) : new Color(255, 255, 255, 10));
        g.fillRoundRect(x, y, w, h, s(8), s(8));
        font(14, false);
        g.setColor(over ? TEXT : new Color(0xB8C0C8));
        g.drawString(label, x + s(12), y + h / 2f + s(5));

        int bw = s(170);
        int bx = x + w - bw - s(70), by = y + h / 2 - s(3);
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(bx, by, bw, s(6), s(6), s(6));
        float t = MathUtil.clamp((value - min) / (max - min), 0, 1);
        g.setColor(ACCENT);
        g.fillRoundRect(bx, by, (int) (bw * t), s(6), s(6), s(6));
        g.setColor(new Color(0xF0D8B8));
        g.fillOval((int) (bx + bw * t) - s(7), by - s(5), s(14), s(16));

        font(13, true);
        g.setColor(new Color(0x9AA4B0));
        String val = String.format(fmt, value);
        g.drawString(val, x + w - s(58), y + h / 2f + s(5));

        if (over && in.mouse(MouseEvent.BUTTON1)) {
            float nt = MathUtil.clamp((in.mouseX - bx) / (float) bw, 0, 1);
            return min + nt * (max - min);
        }
        return value;
    }

    /** Список-переключатель со стрелками. Возвращает новый индекс. */
    public int select(int x, int y, int w, int h, String label, String[] options, int index) {
        boolean over = hovered(x, y, w, h);
        g.setColor(over ? new Color(255, 255, 255, 22) : new Color(255, 255, 255, 10));
        g.fillRoundRect(x, y, w, h, s(8), s(8));
        font(14, false);
        g.setColor(over ? TEXT : new Color(0xB8C0C8));
        g.drawString(label, x + s(12), y + h / 2f + s(5));

        int aw = s(26);
        int rx = x + w - s(12);
        int lx = x + w - s(280);
        boolean leftOver = hovered(lx - aw, y + s(4), aw, h - s(8));
        boolean rightOver = hovered(rx - aw, y + s(4), aw, h - s(8));

        g.setColor(leftOver ? ACCENT : new Color(0x7A828C));
        g.fillPolygon(new int[]{lx - aw + s(16), lx - aw + s(6), lx - aw + s(16)},
                new int[]{y + h / 2 - s(7), y + h / 2, y + h / 2 + s(7)}, 3);
        g.setColor(rightOver ? ACCENT : new Color(0x7A828C));
        g.fillPolygon(new int[]{rx - aw + s(10), rx - aw + s(20), rx - aw + s(10)},
                new int[]{y + h / 2 - s(7), y + h / 2, y + h / 2 + s(7)}, 3);

        font(14, true);
        g.setColor(new Color(0xE8EDF2));
        String val = options[MathUtil.clamp(index, 0, options.length - 1)];
        int tw = g.getFontMetrics().stringWidth(val);
        g.drawString(val, (lx + rx - aw) / 2f - tw / 2f, y + h / 2f + s(5));

        int n = options.length;
        if (in.mouseClicked(MouseEvent.BUTTON1)) {
            if (leftOver) { if (sfx != null) sfx.playUi("ui_click"); return (index - 1 + n) % n; }
            if (rightOver) { if (sfx != null) sfx.playUi("ui_click"); return (index + 1) % n; }
            if (over) { if (sfx != null) sfx.playUi("ui_click"); return (index + 1) % n; }
        }
        return index;
    }
}
