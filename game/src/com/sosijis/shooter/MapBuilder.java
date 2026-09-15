package com.sosijis.shooter;

/** Небольшой конструктор карт: рисуем геометрию прямоугольниками вместо ручного ASCII. */
public class MapBuilder {
    private final char[][] g;
    public final int w, h;

    public MapBuilder(int w, int h, char fill) {
        this.w = w; this.h = h;
        g = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) g[y][x] = fill;
    }

    public MapBuilder set(int x, int y, char c) {
        if (x >= 0 && y >= 0 && x < w && y < h) g[y][x] = c;
        return this;
    }

    public char get(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return '#';
        return g[y][x];
    }

    public MapBuilder rect(int x, int y, int rw, int rh, char c) {
        for (int j = y; j < y + rh; j++) for (int i = x; i < x + rw; i++) set(i, j, c);
        return this;
    }

    public MapBuilder border(int x, int y, int rw, int rh, char c) {
        for (int i = x; i < x + rw; i++) { set(i, y, c); set(i, y + rh - 1, c); }
        for (int j = y; j < y + rh; j++) { set(x, j, c); set(x + rw - 1, j, c); }
        return this;
    }

    public MapBuilder hline(int x, int y, int len, char c) {
        for (int i = x; i < x + len; i++) set(i, y, c);
        return this;
    }

    public MapBuilder vline(int x, int y, int len, char c) {
        for (int j = y; j < y + len; j++) set(x, j, c);
        return this;
    }

    /** Разбрасывает символ по прямоугольнику с вероятностью p, не трогая непустые препятствия. */
    public MapBuilder scatter(int x, int y, int rw, int rh, char c, float p, java.util.Random rnd) {
        for (int j = y; j < y + rh; j++)
            for (int i = x; i < x + rw; i++)
                if (rnd.nextFloat() < p) set(i, j, c);
        return this;
    }

    public String[] rows() {
        String[] out = new String[h];
        for (int y = 0; y < h; y++) out[y] = new String(g[y]);
        return out;
    }
}
