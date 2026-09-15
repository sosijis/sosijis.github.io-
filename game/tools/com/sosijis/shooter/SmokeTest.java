package com.sosijis.shooter;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Безоконный прогон: каждый режим на каждой карте несколько игровых минут,
 * с отрисовкой в BufferedImage. Ловит исключения в симуляции, ИИ и рендере.
 *
 *   javac -d build $(find src -name '*.java') && javac -cp build -d build tools/com/sosijis/shooter/SmokeTest.java
 *   java -Djava.awt.headless=true -cp build com.sosijis.shooter.SmokeTest
 */
public class SmokeTest {

    public static void main(String[] args) {
        float seconds = args.length > 0 ? Float.parseFloat(args[0]) : 40f;
        int failures = 0;
        BufferedImage img = new BufferedImage(1280, 760, BufferedImage.TYPE_INT_RGB);

        for (GameMode.Type mode : GameMode.Type.values()) {
            for (String mapName : MapLibrary.names()) {
                Settings s = new Settings();
                s.applyQualityPreset(Settings.Quality.HIGH);
                s.modeType = mode;
                s.mapName = mapName;
                s.botCount = 9;
                s.roundTimeSec = 40;
                s.masterVolume = 0f;
                s.difficulty = Settings.Difficulty.HARD;

                Input in = new Input();
                Sfx sfx = new Sfx(s);
                Game game = new Game(s, in, sfx);
                long t0 = System.nanoTime();
                try {
                    game.startMatch();
                    Renderer r = new Renderer(game);
                    Hud hud = new Hud(game);
                    int steps = (int) (seconds * 60);
                    for (int i = 0; i < steps; i++) {
                        in.mouseX = 640 + (int) (Math.sin(i * 0.03) * 300);
                        in.mouseY = 380 + (int) (Math.cos(i * 0.021) * 200);
                        game.update(1f / 60f);
                        if (i % 7 == 0) {
                            Graphics2D g = img.createGraphics();
                            r.render(g, 1280, 760);
                            hud.render(g, 1280, 760, 60f);
                            g.dispose();
                        }
                    }
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    int kills = 0;
                    for (Actor a : game.actors) kills += a.kills;
                    System.out.printf("OK   %-16s %-14s  раундов=%d  счёт %d:%d  фрагов=%d  ботов_живо=%d  %d мс%n",
                            mode.name(), mapName, game.mode.round, game.mode.scoreT, game.mode.scoreCT,
                            kills, game.aliveCount(Actor.TEAM_T) + game.aliveCount(Actor.TEAM_CT), ms);
                } catch (Throwable e) {
                    failures++;
                    System.out.printf("FAIL %-16s %-14s  %s%n", mode.name(), mapName, e);
                    e.printStackTrace();
                } finally {
                    sfx.shutdown();
                }
            }
        }
        System.out.println(failures == 0 ? "\nВсе прогоны прошли без ошибок." : "\nОшибок: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
