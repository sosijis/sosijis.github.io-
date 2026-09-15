package com.sosijis.shooter;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * Прогон настоящего окна: запускает GameWindow, кликает «Играть» → «Начать бой»,
 * играет несколько секунд и сохраняет снимки экрана. Запускать под Xvfb.
 */
public class UiSmoke {
    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "shots");
        out.mkdirs();

        Settings s = Settings.load();
        s.applyQualityPreset(Settings.Quality.HIGH);
        s.windowW = 1280; s.windowH = 760;
        s.fullscreen = false;
        s.uiScale = 1f;
        s.modeType = GameMode.Type.DEFUSE;
        s.mapName = "de_dust_lite";
        s.botCount = 9;
        s.masterVolume = 0f;

        final GameWindow[] win = new GameWindow[1];
        SwingUtilities.invokeAndWait(() -> { win[0] = new GameWindow(s); win[0].start(); });
        Thread.sleep(1500);

        Robot robot = new Robot();
        Point frame = win[0].getLocationOnScreen();
        Insets ins = win[0].getInsets();
        int cx = frame.x + ins.left, cy = frame.y + ins.top;
        int w = 1280, h = 760;

        click(robot, cx + w / 2, cy + 224);           // ИГРАТЬ
        Thread.sleep(700);
        shot(robot, win[0], out, "live_menu_play");

        click(robot, cx + w / 2 + 110, cy + 651);     // НАЧАТЬ БОЙ
        Thread.sleep(1200);

        robot.mouseMove(cx + w / 2 + 180, cy + h / 2 - 120);
        Thread.sleep(6500);                            // подготовка + бой
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(900);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.keyPress(KeyEvent.VK_W);
        Thread.sleep(1200);
        robot.keyRelease(KeyEvent.VK_W);
        Thread.sleep(400);
        shot(robot, win[0], out, "live_game");

        robot.keyPress(KeyEvent.VK_TAB);
        Thread.sleep(500);
        shot(robot, win[0], out, "live_scoreboard");
        robot.keyRelease(KeyEvent.VK_TAB);

        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        Thread.sleep(600);
        shot(robot, win[0], out, "live_pause");

        System.out.println("UI-прогон завершён без ошибок");
        System.exit(0);
    }

    static void click(Robot r, int x, int y) throws Exception {
        r.mouseMove(x, y);
        Thread.sleep(200);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(80);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(150);
    }

    static void shot(Robot r, Window win, File dir, String name) throws Exception {
        Rectangle b = win.getBounds();
        ImageIO.write(r.createScreenCapture(b), "png", new File(dir, name + ".png"));
        System.out.println("  " + name + ".png");
    }
}
