package com.sosijis.shooter;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Встроенные карты. Символы:
 *  '#' бетон, '=' кирпич, 'W' дерево, 'X' ящик, 'M' металлический ящик, 'G' стекло,
 *  'F' решётка, 'O' бочка, '.' бетонный пол, ',' песок, '"' трава, '_' металл, 'w' доски,
 *  '~' вода, 'A'/'B' точки закладки, 'R' зона спасения, 'h' заложник,
 *  't' спавн террористов, 'c' спавн спецназа, 'b'/'y' зоны закупки.
 */
public final class MapLibrary {

    private static final Map<String, GameMap> CACHE = new LinkedHashMap<>();
    private static final List<String> ORDER = new ArrayList<>();
    private static final List<String> LABELS = new ArrayList<>();

    static {
        register("de_dust_lite", "Пыль — Dust Lite");
        register("de_office", "Офис — Office");
        register("cs_warehouse", "Склад — Warehouse");
        register("de_village", "Деревня — Village");
        register("ar_arena", "Арена — Arena");
    }

    private MapLibrary() {}

    private static void register(String id, String label) { ORDER.add(id); LABELS.add(label); }

    public static List<String> names() { return ORDER; }
    public static String label(String id) {
        int i = ORDER.indexOf(id);
        return i < 0 ? id : LABELS.get(i);
    }

    public static GameMap get(String id) {
        GameMap m = CACHE.get(id);
        if (m == null) {
            m = build(id);
            CACHE.put(id, m);
        }
        return m;
    }

    /** Свежая копия карты (разрушения не переносятся между матчами). */
    public static GameMap fresh(String id) {
        CACHE.remove(id);
        return get(id);
    }

    private static GameMap build(String id) {
        switch (id) {
            case "de_office": return office();
            case "cs_warehouse": return warehouse();
            case "de_village": return village();
            case "ar_arena": return arena();
            default: return dust();
        }
    }

    // ------------------------------------------------------------------ Dust
    private static GameMap dust() {
        Random rnd = new Random(1337);
        int W = 48, H = 36;
        MapBuilder b = new MapBuilder(W, H, ',');
        b.border(0, 0, W, H, '#');

        // Внешние массивы зданий
        b.rect(6, 6, 9, 6, '=');      // блок у Т-спавна
        b.rect(20, 4, 7, 5, '=');     // центральный домик сверху
        b.rect(33, 7, 9, 7, '=');     // блок у CT
        b.rect(6, 24, 10, 6, '=');
        b.rect(32, 23, 10, 7, '=');

        // Коридоры / стены
        b.rect(17, 12, 2, 12, '#');   // левая стена мида
        b.rect(29, 12, 2, 12, '#');   // правая стена мида
        b.rect(17, 12, 14, 1, '#');   // верх мида
        b.rect(21, 12, 6, 1, '.');    // проём в мид сверху
        b.rect(17, 23, 14, 1, '#');
        b.rect(21, 23, 6, 1, '.');    // проём в мид снизу
        b.rect(19, 14, 10, 8, '.');   // сам мид

        // Окно в мид (стекло)
        b.rect(17, 16, 2, 3, 'G');
        b.rect(29, 17, 2, 3, 'G');

        // Точка A (верх справа)
        b.rect(34, 2, 12, 9, ',');
        b.rect(36, 3, 8, 6, 'A');
        b.rect(33, 2, 1, 5, '#');
        b.set(33, 4, ',');
        b.rect(34, 11, 12, 1, '#');
        b.rect(38, 11, 4, 1, ',');
        b.rect(38, 5, 2, 2, 'X');
        b.set(42, 7, 'X');
        b.set(37, 7, 'O');

        // Точка B (низ слева)
        b.rect(2, 26, 13, 8, ',');
        b.rect(4, 28, 8, 5, 'B');
        b.rect(15, 26, 1, 8, '#');
        b.set(15, 30, ',');
        b.rect(2, 25, 13, 1, '#');
        b.rect(6, 25, 4, 1, ',');
        b.rect(5, 29, 2, 2, 'X');
        b.set(11, 32, 'X');
        b.set(9, 27, 'O');

        // Длинный коридор справа
        b.rect(43, 12, 4, 12, '.');
        b.rect(42, 12, 1, 12, '#');
        b.set(42, 18, '.');

        // Короткий проход слева
        b.rect(2, 12, 4, 12, '.');
        b.rect(6, 12, 1, 12, '#');
        b.set(6, 17, '.');

        // Укрытия на открытых участках
        b.rect(21, 17, 2, 2, 'X');
        b.set(26, 20, 'X');
        b.set(19, 27, 'M');
        b.set(35, 17, 'M');
        b.set(24, 30, 'X');
        b.set(24, 8, 'X');
        b.set(12, 18, 'O');
        b.set(37, 20, 'O');

        // Решётки (видно, но не пройти)
        b.vline(24, 25, 4, 'F');
        b.hline(10, 14, 4, 'F');

        // Спавны
        for (int i = 0; i < 5; i++) b.set(3 + i * 2, 3, 't');
        b.rect(2, 2, 12, 3, 'b');
        for (int i = 0; i < 5; i++) b.set(3 + i * 2, 3, 't');

        for (int i = 0; i < 5; i++) b.set(34 + i * 2, 33, 'c');
        b.rect(33, 32, 12, 3, 'y');
        for (int i = 0; i < 5; i++) b.set(34 + i * 2, 33, 'c');

        // Заложники и зона спасения (для режима захвата)
        b.set(38, 4, 'h'); b.set(40, 4, 'h'); b.set(39, 6, 'h');
        b.rect(33, 33, 4, 2, 'R');

        // Немного мусора
        b.scatter(18, 14, 12, 8, 'O', 0.015f, rnd);

        return new GameMap("de_dust_lite", "Пыль — Dust Lite", b.rows(),
                new Color(0x30, 0x2A, 0x20), new Color(0xFF, 0xD9, 0x9B));
    }

    // ---------------------------------------------------------------- Office
    private static GameMap office() {
        int W = 46, H = 34;
        MapBuilder b = new MapBuilder(W, H, '.');
        b.border(0, 0, W, H, '#');

        // Внешние стены офиса
        b.rect(8, 4, 30, 26, '.');
        b.border(8, 4, 30, 26, '=');

        // Внутренние кабинеты
        b.border(10, 6, 9, 8, 'W'); b.rect(11, 7, 7, 6, 'w');
        b.set(14, 13, 'w'); b.set(15, 13, 'w');
        b.border(27, 6, 9, 8, 'W'); b.rect(28, 7, 7, 6, 'w');
        b.set(31, 13, 'w'); b.set(32, 13, 'w');
        b.border(10, 20, 11, 9, 'W'); b.rect(11, 21, 9, 7, 'w');
        b.set(15, 20, 'w'); b.set(16, 20, 'w');
        b.border(26, 20, 10, 9, 'W'); b.rect(27, 21, 8, 7, 'w');
        b.set(30, 20, 'w'); b.set(31, 20, 'w');

        // Стеклянные перегородки в холле
        b.hline(21, 10, 5, 'G');
        b.hline(21, 23, 5, 'G');
        b.vline(23, 11, 3, 'G');

        // Коридоры снаружи здания
        b.rect(2, 2, 6, 30, '.');
        b.rect(38, 2, 6, 30, '.');
        b.rect(8, 16, 1, 4, '.');     // вход слева
        b.rect(37, 16, 1, 4, '.');    // вход справа
        b.rect(20, 4, 4, 1, '.');     // вход сверху
        b.rect(20, 29, 4, 1, '.');    // вход снизу

        // Мебель-укрытия
        b.rect(21, 15, 2, 2, 'X');
        b.set(25, 17, 'X'); b.set(19, 18, 'X');
        b.set(13, 16, 'M'); b.set(33, 17, 'M');
        b.set(12, 8, 'O'); b.set(34, 27, 'O');
        b.vline(24, 15, 3, 'F');

        // Точки закладки
        b.rect(12, 22, 6, 5, 'A');
        b.rect(28, 22, 6, 5, 'B');

        // Спавны и закупка
        b.rect(2, 2, 6, 5, 'b');
        for (int i = 0; i < 5; i++) b.set(3 + (i % 3) * 2, 3 + (i / 3) * 2, 't');
        b.rect(38, 27, 6, 5, 'y');
        for (int i = 0; i < 5; i++) b.set(39 + (i % 3) * 2, 28 + (i / 3) * 2, 'c');

        // Заложники в кабинетах
        b.set(13, 9, 'h'); b.set(16, 11, 'h'); b.set(31, 9, 'h'); b.set(33, 11, 'h');
        b.rect(38, 2, 4, 3, 'R');

        return new GameMap("de_office", "Офис — Office", b.rows(),
                new Color(0x1E, 0x22, 0x2A), new Color(0xBF, 0xD4, 0xFF));
    }

    // ------------------------------------------------------------- Warehouse
    private static GameMap warehouse() {
        Random rnd = new Random(99);
        int W = 44, H = 32;
        MapBuilder b = new MapBuilder(W, H, '_');
        b.border(0, 0, W, H, '#');

        // Ряды стеллажей
        for (int r = 0; r < 4; r++) {
            int x = 6 + r * 9;
            b.rect(x, 5, 5, 8, 'X');
            b.rect(x, 18, 5, 8, 'X');
            b.set(x + 2, 8, '_');
            b.set(x + 2, 22, '_');
        }
        // Металлические контейнеры
        b.rect(18, 14, 8, 3, 'M');
        b.set(21, 15, '_');
        b.rect(3, 14, 3, 3, 'M');
        b.rect(38, 14, 3, 3, 'M');

        // Разлитая вода
        b.rect(20, 27, 6, 3, '~');
        b.rect(16, 3, 5, 2, '~');

        // Офис-надстройка со стеклом
        b.rect(32, 2, 10, 7, 'w');
        b.border(32, 2, 10, 7, '=');
        b.hline(33, 8, 8, 'G');
        b.set(36, 8, 'w');

        // Бочки
        b.scatter(2, 2, W - 4, H - 4, 'O', 0.012f, rnd);

        // Точки закладки
        b.rect(4, 27, 7, 4, 'A');
        b.rect(33, 25, 7, 5, 'B');

        // Спавны
        b.rect(2, 2, 6, 5, 'b');
        for (int i = 0; i < 5; i++) b.set(3 + (i % 3) * 2, 3 + (i / 3) * 2, 't');
        b.rect(36, 18, 6, 5, 'y');
        for (int i = 0; i < 5; i++) b.set(37 + (i % 3) * 2, 19 + (i / 3) * 2, 'c');

        // Заложники + зона спасения
        b.set(34, 4, 'h'); b.set(37, 4, 'h'); b.set(40, 6, 'h');
        b.rect(2, 8, 4, 3, 'R');

        return new GameMap("cs_warehouse", "Склад — Warehouse", b.rows(),
                new Color(0x1A, 0x1C, 0x20), new Color(0xD6, 0xE2, 0xF0));
    }

    // --------------------------------------------------------------- Village
    private static GameMap village() {
        Random rnd = new Random(2024);
        int W = 50, H = 38;
        MapBuilder b = new MapBuilder(W, H, '"');
        b.border(0, 0, W, H, '#');

        // Дома
        int[][] houses = {
                {5, 5, 8, 6}, {18, 4, 9, 7}, {33, 6, 9, 6},
                {6, 20, 9, 7}, {21, 22, 10, 8}, {36, 21, 9, 8},
        };
        for (int[] hs : houses) {
            b.rect(hs[0], hs[1], hs[2], hs[3], 'w');
            b.border(hs[0], hs[1], hs[2], hs[3], 'W');
            b.set(hs[0] + hs[2] / 2, hs[1] + hs[3] - 1, 'w');  // дверь
            b.set(hs[0], hs[1] + hs[3] / 2, 'G');              // окно
        }

        // Грунтовая дорога
        b.rect(2, 15, W - 4, 3, ',');
        b.vline(24, 2, 34, ',');
        b.vline(25, 2, 34, ',');

        // Речка с мостом
        b.rect(14, 2, 2, 13, '~');
        b.rect(14, 15, 2, 3, ',');

        // Заборы и телеги
        b.hline(30, 13, 8, 'F');
        b.hline(8, 30, 9, 'F');
        b.vline(44, 4, 7, 'F');
        b.set(28, 18, 'X'); b.set(19, 19, 'X'); b.set(35, 17, 'X');
        b.set(12, 12, 'M'); b.set(40, 33, 'M');
        b.scatter(2, 2, W - 4, H - 4, 'O', 0.008f, rnd);

        // Деревья (непрозрачные преграды)
        for (int i = 0; i < 26; i++) {
            int x = 2 + rnd.nextInt(W - 4), y = 2 + rnd.nextInt(H - 4);
            if (b.get(x, y) == '"') b.set(x, y, 'W');
        }

        // Точки закладки
        b.rect(22, 24, 6, 4, 'A');
        b.rect(37, 23, 6, 4, 'B');

        // Спавны
        b.rect(2, 2, 7, 5, 'b');
        for (int i = 0; i < 5; i++) b.set(3 + (i % 3) * 2, 3 + (i / 3) * 2, 't');
        b.rect(42, 31, 7, 5, 'y');
        for (int i = 0; i < 5; i++) b.set(43 + (i % 3) * 2, 32 + (i / 3) * 2, 'c');

        b.set(23, 8, 'h'); b.set(20, 7, 'h'); b.set(37, 9, 'h');
        b.rect(45, 2, 4, 3, 'R');

        return new GameMap("de_village", "Деревня — Village", b.rows(),
                new Color(0x22, 0x2A, 0x24), new Color(0xE8, 0xF0, 0xC8));
    }

    // ----------------------------------------------------------------- Arena
    private static GameMap arena() {
        int W = 34, H = 26;
        MapBuilder b = new MapBuilder(W, H, '.');
        b.border(0, 0, W, H, '#');

        // Симметричные укрытия
        b.rect(6, 6, 3, 3, 'X');
        b.rect(W - 9, 6, 3, 3, 'X');
        b.rect(6, H - 9, 3, 3, 'X');
        b.rect(W - 9, H - 9, 3, 3, 'X');
        b.rect(W / 2 - 3, H / 2 - 2, 6, 4, 'M');
        b.rect(W / 2 - 1, H / 2 - 1, 2, 2, '.');

        b.vline(W / 2, 3, 4, 'F');
        b.vline(W / 2, H - 7, 4, 'F');
        b.hline(3, H / 2, 4, 'G');
        b.hline(W - 7, H / 2, 4, 'G');

        b.set(12, 12, 'O'); b.set(21, 13, 'O');
        b.set(4, 20, 'O'); b.set(29, 5, 'O');

        b.rect(13, 4, 8, 3, 'A');
        b.rect(13, H - 7, 8, 3, 'B');

        b.rect(2, 2, 5, 4, 'b');
        for (int i = 0; i < 4; i++) b.set(3 + (i % 2) * 2, 3 + (i / 2) * 2, 't');
        b.rect(W - 7, H - 6, 5, 4, 'y');
        for (int i = 0; i < 4; i++) b.set(W - 6 + (i % 2) * 2, H - 5 + (i / 2) * 2, 'c');

        b.set(16, 12, 'h'); b.set(18, 12, 'h');
        b.rect(2, H - 5, 3, 3, 'R');

        return new GameMap("ar_arena", "Арена — Arena", b.rows(),
                new Color(0x20, 0x20, 0x28), new Color(0xC8, 0xC8, 0xFF));
    }
}
