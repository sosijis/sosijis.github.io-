package com.sosijis.shooter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;

/** Окно, игровой цикл и переключение состояний меню/бой/пауза. */
public class GameWindow extends JFrame implements Runnable {

    public enum State { MENU, PLAYING, PAUSED }

    private final Settings settings;
    private final Input input = new Input();
    private final Sfx sfx;
    private final Menu menu;
    private Canvas canvas;
    private BufferStrategy strategy;
    private Thread loop;
    private volatile boolean running;

    private State state = State.MENU;
    private Game game;
    private Renderer renderer;
    private Hud hud;

    private float fps = 60;
    private float fpsAccum;
    private int fpsFrames;
    private final Cursor blankCursor;

    public GameWindow(Settings settings) {
        this.settings = settings;
        this.sfx = new Sfx(settings);
        this.menu = new Menu(settings, sfx);

        BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(blank, new Point(0, 0), "blank");

        setTitle("Тактический штурм — Sosijis Tactical Assault");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });
        buildCanvas();
        sfx.startMusic();
    }

    private void buildCanvas() {
        if (canvas != null) remove(canvas);
        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(settings.windowW, settings.windowH));
        canvas.setBackground(Color.BLACK);
        canvas.setFocusable(true);
        canvas.setIgnoreRepaint(true);
        add(canvas, BorderLayout.CENTER);
        input.attach(canvas);
        pack();
        setLocationRelativeTo(null);
    }

    public void applyVideoSettings() {
        boolean wasVisible = isVisible();
        dispose();
        setUndecorated(settings.fullscreen);
        if (settings.fullscreen) {
            Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            canvas.setPreferredSize(new Dimension(b.width, b.height));
            pack();
            setLocation(b.x, b.y);
        } else {
            canvas.setPreferredSize(new Dimension(settings.windowW, settings.windowH));
            pack();
            setLocationRelativeTo(null);
        }
        if (wasVisible) setVisible(true);
        strategy = null;
        canvas.requestFocusInWindow();
    }

    public void start() {
        setVisible(true);
        canvas.requestFocusInWindow();
        running = true;
        loop = new Thread(this, "game-loop");
        loop.start();
    }

    private void shutdown() {
        running = false;
        settings.save();
        sfx.shutdown();
        dispose();
        System.exit(0);
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (float) ((now - last) / 1e9);
            last = now;
            if (dt > 1f / 20f) dt = 1f / 20f;

            input.poll();
            try {
                tick(dt);
                draw();
            } catch (Exception e) {
                e.printStackTrace();
            }

            fpsAccum += dt;
            fpsFrames++;
            if (fpsAccum >= 0.35f) {
                fps = fpsFrames / fpsAccum;
                fpsAccum = 0;
                fpsFrames = 0;
            }

            if (settings.fpsCap > 0) {
                long frameNanos = (long) (1e9 / settings.fpsCap);
                long elapsed = System.nanoTime() - now;
                long sleep = frameNanos - elapsed;
                if (sleep > 0) {
                    try { Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L)); }
                    catch (InterruptedException ignored) { }
                }
            } else {
                Thread.yield();
            }
        }
    }

    // ------------------------------------------------------------------ Цикл

    private void tick(float dt) {
        if (input.keyPressed(KeyEvent.VK_F11)) {
            settings.fullscreen = !settings.fullscreen;
            SwingUtilities.invokeLater(this::applyVideoSettings);
        }

        switch (state) {
            case MENU -> {
                menu.inGame = false;
                menu.update(dt);
                canvas.setCursor(Cursor.getDefaultCursor());
                if (menu.startRequested) {
                    menu.startRequested = false;
                    startGame();
                }
                if (menu.quitRequested) shutdown();
            }
            case PLAYING -> {
                canvas.setCursor(blankCursor);
                game.showScoreboard = input.key(KeyEvent.VK_TAB);
                hud.update(input, dt);
                hud.handleBuyInput(input);
                if (!hud.buyOpen) game.update(dt);
                else game.update(dt * 0.0f);
                if (input.keyPressed(KeyEvent.VK_ESCAPE)) {
                    if (hud.buyOpen) hud.buyOpen = false;
                    else if (game.mode.phase == GameMode.Phase.MATCH_OVER) backToMenu();
                    else pause();
                }
            }
            case PAUSED -> {
                canvas.setCursor(Cursor.getDefaultCursor());
                menu.inGame = true;
                menu.update(dt);
                sfx.setDuck(0.3f);
                if (menu.resumeRequested) {
                    menu.resumeRequested = false;
                    state = State.PLAYING;
                    game.paused = false;
                    input.clearAll();
                }
                if (menu.backToMenuRequested) {
                    menu.backToMenuRequested = false;
                    backToMenu();
                }
                if (menu.quitRequested) shutdown();
            }
        }

        if (menu.applyVideoRequested) {
            menu.applyVideoRequested = false;
            SwingUtilities.invokeLater(this::applyVideoSettings);
        }
    }

    private void startGame() {
        game = new Game(settings, input, sfx);
        game.startMatch();
        renderer = new Renderer(game);
        hud = new Hud(game);
        state = State.PLAYING;
        input.clearAll();
        sfx.setDuck(1f);
    }

    private void pause() {
        state = State.PAUSED;
        game.paused = true;
        menu.screen = Menu.Screen.PAUSE;
        menu.inGame = true;
        input.clearAll();
    }

    private void backToMenu() {
        state = State.MENU;
        game = null;
        renderer = null;
        hud = null;
        menu.screen = Menu.Screen.MAIN;
        menu.inGame = false;
        sfx.setDuck(1f);
        sfx.stopAllLoops();
        sfx.startMusic();
        input.clearAll();
    }

    private void draw() {
        if (strategy == null) {
            try {
                canvas.createBufferStrategy(2);
                strategy = canvas.getBufferStrategy();
            } catch (Exception e) {
                return;
            }
        }
        int w = canvas.getWidth(), h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        do {
            do {
                Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
                try {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, w, h);
                    switch (state) {
                        case MENU -> menu.render(g, input, w, h);
                        case PLAYING -> {
                            renderer.render(g, w, h);
                            game.renderMs = renderer.renderTimeMs;
                            hud.render(g, w, h, fps);
                        }
                        case PAUSED -> {
                            renderer.render(g, w, h);
                            hud.render(g, w, h, fps);
                            menu.render(g, input, w, h);
                        }
                    }
                } finally {
                    g.dispose();
                }
            } while (strategy.contentsRestored());
            strategy.show();
        } while (strategy.contentsLost());
    }
}
