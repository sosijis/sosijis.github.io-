package com.sosijis.shooter;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Точка входа.
 *
 * Сборка:  javac -d build $(find src -name '*.java')
 * Запуск:  java -cp build com.sosijis.shooter.Main
 */
public class Main {
    public static void main(String[] args) {
        // Аппаратный конвейер включаем только по явному запросу: на части драйверов
        // он медленнее программного (запуск с -Dsun.java2d.opengl=true).
        if (System.getProperty("sun.java2d.noddraw") == null) System.setProperty("sun.java2d.noddraw", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }

        Settings settings = Settings.load();
        for (String a : args) {
            if (a.equals("--fullscreen")) settings.fullscreen = true;
            if (a.equals("--windowed")) settings.fullscreen = false;
            if (a.startsWith("--map=")) settings.mapName = a.substring(6);
            if (a.startsWith("--bots=")) {
                try { settings.botCount = Integer.parseInt(a.substring(7)); } catch (Exception ignored) { }
            }
            if (a.equals("--low")) settings.applyQualityPreset(Settings.Quality.LOW);
            if (a.equals("--ultra")) settings.applyQualityPreset(Settings.Quality.ULTRA);
        }

        SwingUtilities.invokeLater(() -> {
            GameWindow win = new GameWindow(settings);
            if (settings.fullscreen) win.applyVideoSettings();
            win.start();
        });
    }
}
