package com.sosijis.shooter;

import java.awt.Component;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Состояние клавиатуры и мыши.
 *
 * События приходят в потоке AWT, а читает их игровой цикл в своём потоке, поэтому
 * нажатия копятся в «ожидающих» наборах и разом переносятся в кадр вызовом {@link #poll()}.
 * Без этого короткое нажатие, пришедшее уже после чтения ввода, терялось бы целиком.
 */
public class Input implements KeyListener, MouseListener, MouseMotionListener, MouseWheelListener {

    private final Object lock = new Object();

    private final Set<Integer> down = new HashSet<>();
    private final Set<Integer> pendingPressed = new HashSet<>();
    private final Set<Integer> pendingReleased = new HashSet<>();
    private final Set<Integer> pressed = new HashSet<>();
    private final Set<Integer> released = new HashSet<>();

    private static final int BTN = 6;
    private final boolean[] mouseDown = new boolean[BTN];
    private final boolean[] pendingMousePressed = new boolean[BTN];
    private final boolean[] pendingMouseReleased = new boolean[BTN];
    private final boolean[] mousePressed = new boolean[BTN];
    private final boolean[] mouseReleased = new boolean[BTN];

    private int pendingWheel;
    private final StringBuilder pendingTyped = new StringBuilder();

    public volatile int mouseX, mouseY;
    public int wheel;
    public final StringBuilder typed = new StringBuilder();

    public void attach(Component c) {
        c.addKeyListener(this);
        c.addMouseListener(this);
        c.addMouseMotionListener(this);
        c.addMouseWheelListener(this);
        c.setFocusable(true);
    }

    /** Переносит накопленные события в текущий кадр. Вызывается один раз в начале кадра. */
    public void poll() {
        synchronized (lock) {
            pressed.clear();
            pressed.addAll(pendingPressed);
            pendingPressed.clear();

            released.clear();
            released.addAll(pendingReleased);
            pendingReleased.clear();

            for (int i = 0; i < BTN; i++) {
                mousePressed[i] = pendingMousePressed[i];
                mouseReleased[i] = pendingMouseReleased[i];
                pendingMousePressed[i] = false;
                pendingMouseReleased[i] = false;
            }

            wheel = pendingWheel;
            pendingWheel = 0;

            typed.setLength(0);
            typed.append(pendingTyped);
            pendingTyped.setLength(0);
        }
    }

    public void clearAll() {
        synchronized (lock) {
            down.clear();
            pressed.clear(); released.clear();
            pendingPressed.clear(); pendingReleased.clear();
            for (int i = 0; i < BTN; i++) {
                mouseDown[i] = false;
                mousePressed[i] = false; mouseReleased[i] = false;
                pendingMousePressed[i] = false; pendingMouseReleased[i] = false;
            }
            pendingWheel = 0;
            wheel = 0;
            pendingTyped.setLength(0);
            typed.setLength(0);
        }
    }

    public boolean key(int code) { synchronized (lock) { return down.contains(code); } }
    public boolean keyPressed(int code) { synchronized (lock) { return pressed.contains(code); } }
    public boolean keyReleased(int code) { synchronized (lock) { return released.contains(code); } }
    public boolean mouse(int btn) { synchronized (lock) { return btn >= 0 && btn < BTN && mouseDown[btn]; } }
    public boolean mouseClicked(int btn) { synchronized (lock) { return btn >= 0 && btn < BTN && mousePressed[btn]; } }
    public boolean mouseUp(int btn) { synchronized (lock) { return btn >= 0 && btn < BTN && mouseReleased[btn]; } }

    /** Кнопка удерживается или была нажата в этом кадре — короткий «клик» не теряется. */
    public boolean mouseHeldOrClicked(int btn) { return mouse(btn) || mouseClicked(btn); }

    @Override public void keyPressed(KeyEvent e) {
        synchronized (lock) {
            if (down.add(e.getKeyCode())) pendingPressed.add(e.getKeyCode());
        }
    }

    @Override public void keyReleased(KeyEvent e) {
        synchronized (lock) {
            down.remove(e.getKeyCode());
            pendingReleased.add(e.getKeyCode());
        }
    }

    @Override public void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();
        synchronized (lock) {
            if (c >= 32 && c != 127) pendingTyped.append(c);
            else if (c == '\b') pendingTyped.append('\b');
        }
    }

    @Override public void mousePressed(MouseEvent e) {
        int b = e.getButton();
        synchronized (lock) {
            if (b >= 0 && b < BTN) {
                if (!mouseDown[b]) pendingMousePressed[b] = true;
                mouseDown[b] = true;
            }
        }
        mouseX = e.getX(); mouseY = e.getY();
    }

    @Override public void mouseReleased(MouseEvent e) {
        int b = e.getButton();
        synchronized (lock) {
            if (b >= 0 && b < BTN) {
                mouseDown[b] = false;
                pendingMouseReleased[b] = true;
            }
        }
    }

    @Override public void mouseMoved(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mouseDragged(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mouseWheelMoved(MouseWheelEvent e) { synchronized (lock) { pendingWheel += e.getWheelRotation(); } }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
}
