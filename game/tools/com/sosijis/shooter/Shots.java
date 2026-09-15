package com.sosijis.shooter;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** Рендерит кадры меню и боя в PNG — для быстрой визуальной проверки без запуска окна. */
public class Shots {
    static final int W = 1280, H = 760;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "shots");
        out.mkdirs();

        Settings s = new Settings();
        s.applyQualityPreset(Settings.Quality.HIGH);
        s.masterVolume = 0f;
        s.modeType = GameMode.Type.DEFUSE;
        s.mapName = "de_dust_lite";
        s.botCount = 9;
        s.difficulty = Settings.Difficulty.HARD;
        Input in = new Input();
        Sfx sfx = new Sfx(s);

        Menu menu = new Menu(s, sfx);
        in.mouseX = -100; in.mouseY = -100;
        shot(out, "menu_main", g -> { menu.screen = Menu.Screen.MAIN; menu.update(0.016f); menu.render(g, in, W, H); });
        shot(out, "menu_play", g -> { menu.screen = Menu.Screen.PLAY; menu.render(g, in, W, H); });
        shot(out, "menu_video", g -> { menu.screen = Menu.Screen.VIDEO; menu.render(g, in, W, H); });
        shot(out, "menu_audio", g -> { menu.screen = Menu.Screen.AUDIO; menu.render(g, in, W, H); });
        shot(out, "menu_controls", g -> { menu.screen = Menu.Screen.CONTROLS; menu.render(g, in, W, H); });

        Game game = new Game(s, in, sfx);
        game.startMatch();
        Renderer r = new Renderer(game);
        Hud hud = new Hud(game);
        in.mouseX = 800; in.mouseY = 300;

        // Пропускаем подготовку и даём боту-времени развернуться
        for (int i = 0; i < 60 * 22; i++) {
            game.update(1f / 60f);
            if (i == 60 * 6) {
                game.player.giveWeapon(WeaponType.AK47);
                game.player.armor = 100; game.player.helmet = true;
                game.player.nades[Actor.NADE_HE] = 1;
                game.player.nades[Actor.NADE_FLASH] = 2;
                game.player.nades[Actor.NADE_SMOKE] = 1;
                game.player.nades[Actor.NADE_MOLOTOV] = 1;
            }
        }
        // Дым и огонь рядом с игроком, чтобы попали в кадр
        Vec2 p = game.player.pos;
        game.spawnSmoke(new Vec2(p.x + 170, p.y - 40), game.player);
        game.spawnFire(new Vec2(p.x + 60, p.y + 150), game.player);
        game.fx.blood(p.x + 40, p.y + 20, 1.2f, 1f);
        for (int i = 0; i < 90; i++) game.update(1f / 60f);

        shot(out, "game_smoke_fire", g -> { r.render(g, W, H); hud.render(g, W, H, 144f); });

        hud.buyOpen = true;
        shot(out, "game_buymenu", g -> { r.render(g, W, H); hud.render(g, W, H, 144f); });
        hud.buyOpen = false;

        game.showScoreboard = true;
        shot(out, "game_scoreboard", g -> { r.render(g, W, H); hud.render(g, W, H, 144f); });
        game.showScoreboard = false;

        // Кадр без тумана войны — видно всю геометрию карты
        s.fogOfWar = false;
        game.camera.targetZoom = 0.46f;
        game.camera.zoom = 0.46f;
        for (int i = 0; i < 4; i++) game.update(1f / 60f);
        shot(out, "game_overview", g -> { r.render(g, W, H); hud.render(g, W, H, 144f); });

        sfx.shutdown();
        System.out.println("Готово: " + out.getAbsolutePath());
        System.exit(0);
    }

    interface Draw { void draw(Graphics2D g); }

    static void shot(File dir, String name, Draw d) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        d.draw(g);
        g.dispose();
        ImageIO.write(img, "png", new File(dir, name + ".png"));
        System.out.println("  " + name + ".png");
    }
}
