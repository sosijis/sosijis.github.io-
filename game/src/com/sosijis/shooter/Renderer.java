package com.sosijis.shooter;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/** Отрисовка мира: пол, стены с псевдообъёмом, эффекты, дым, огонь и туман войны. */
public class Renderer {

    private final Game game;
    private final Settings s;
    private BufferedImage grain;
    private BufferedImage overlayCache;
    private int overlayW = -1, overlayH = -1;
    private boolean overlayGrain;
    private final float[] rayDist;
    private final float[] rayCos, raySin;
    private int rayCount = -1;
    public float renderTimeMs;

    public Renderer(Game game) {
        this.game = game;
        this.s = game.settings;
        rayDist = new float[1024];
        rayCos = new float[1024];
        raySin = new float[1024];
        buildGrain();
    }

    /** Виньетка и зерно не меняются от кадра к кадру — рисуем их один раз в текстуру. */
    private BufferedImage overlay(int w, int h) {
        if (overlayCache != null && overlayW == w && overlayH == h && overlayGrain == s.grain) return overlayCache;
        overlayW = w; overlayH = h; overlayGrain = s.grain;
        overlayCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D og = overlayCache.createGraphics();
        RadialGradientPaint vg = new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f),
                Math.max(w, h) * 0.75f,
                new float[]{0.45f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 120)});
        og.setPaint(vg);
        og.fillRect(0, 0, w, h);
        og.setPaint(new TexturePaint(grain, new Rectangle(0, 0, 128, 128)));
        og.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.05f));
        og.fillRect(0, 0, w, h);
        og.dispose();
        return overlayCache;
    }

    private void buildGrain() {
        grain = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                int v = MathUtil.randInt(255);
                grain.setRGB(x, y, ((v / 8) << 24) | 0x808080);
            }
    }

    public void render(Graphics2D g, int w, int h) {
        long t0 = System.nanoTime();
        Camera cam = game.camera;
        cam.resize(w, h);

        g.setColor(game.map.ambient);
        g.fillRect(0, 0, w, h);

        if (s.antialias) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        } else {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        AffineTransform old = g.getTransform();
        g.translate(cam.offX(), cam.offY());
        g.scale(cam.zoom, cam.zoom);

        int x0 = Math.max(0, (int) (cam.screenToWorldX(0) / GameMap.TS) - 1);
        int y0 = Math.max(0, (int) (cam.screenToWorldY(0) / GameMap.TS) - 1);
        int x1 = Math.min(game.map.w - 1, (int) (cam.screenToWorldX(w) / GameMap.TS) + 1);
        int y1 = Math.min(game.map.h - 1, (int) (cam.screenToWorldY(h) / GameMap.TS) + 1);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawFloor(g, x0, y0, x1, y1);
        drawDecals(g);
        drawObjectives(g);
        drawWalls(g, x0, y0, x1, y1);
        if (s.antialias) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawPickups(g);
        drawFires(g);
        drawHostages(g);
        drawCorpses(g);
        drawActors(g);
        drawGrenades(g);
        drawParticles(g);
        drawTracers(g);
        if (s.dynamicLight) drawLights(g);
        drawSmoke(g);

        g.setTransform(old);

        if (s.fogOfWar) drawFog(g, w, h);
        drawNameTags(g, w, h);
        drawOverlays(g, w, h);

        renderTimeMs = (System.nanoTime() - t0) / 1e6f;
    }

    // ------------------------------------------------------------------ Пол
    private void drawFloor(Graphics2D g, int x0, int y0, int x1, int y1) {
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                Tile t = game.map.tiles[tx][ty];
                if (t.solid) continue;
                Color c = ((tx + ty) & 1) == 0 ? t.color : t.colorDark;
                g.setColor(c);
                g.fillRect(tx * GameMap.TS, ty * GameMap.TS, GameMap.TS, GameMap.TS);
                if (t == Tile.WATER) {
                    float wobble = (float) Math.sin(game.time * 1.6 + tx * 0.7 + ty * 0.5) * 0.5f + 0.5f;
                    g.setColor(new Color(0.42f, 0.66f, 0.82f, 0.10f + wobble * 0.09f));
                    g.fillRect(tx * GameMap.TS, ty * GameMap.TS, GameMap.TS, GameMap.TS);
                }
            }
        }
    }

    private void drawObjectives(Graphics2D g) {
        // Точки закладки и зона спасения — подсвеченные контуры
        markZone(g, game.map.siteACells, new Color(0xFF, 0x8A, 0x5A), "A");
        markZone(g, game.map.siteBCells, new Color(0x6F, 0xB6, 0xFF), "B");
        markZone(g, game.map.rescueCells, new Color(0x8F, 0xE0, 0x8A), "R");
    }

    private void markZone(Graphics2D g, java.util.List<Vec2> cells, Color c, String label) {
        if (cells.isEmpty()) return;
        float pulse = 0.12f + 0.06f * (float) Math.sin(game.time * 2.2);
        g.setColor(new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, pulse));
        for (Vec2 v : cells)
            g.fillRect((int) (v.x - GameMap.TS / 2f), (int) (v.y - GameMap.TS / 2f), GameMap.TS, GameMap.TS);
        Vec2 center = cells.get(cells.size() / 2);
        g.setColor(new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.45f));
        g.setFont(new Font("Dialog", Font.BOLD, 46));
        g.drawString(label, center.x - 14, center.y + 16);
    }

    private void drawDecals(Graphics2D g) {
        for (Fx.Decal d : game.fx.decals) {
            if (!game.camera.visible(d.x, d.y, 80)) continue;
            Color c = d.color;
            g.setColor(new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, d.alpha * 0.75f));
            if (d.type == 1) {
                g.fillOval((int) (d.x - d.size / 2), (int) (d.y - d.size / 2), (int) d.size, (int) d.size);
            } else {
                AffineTransform t = g.getTransform();
                g.translate(d.x, d.y);
                g.rotate(d.rot);
                g.fillOval((int) (-d.size), (int) (-d.size * 0.6f), (int) (d.size * 2), (int) (d.size * 1.2f));
                g.setTransform(t);
            }
        }
    }

    // ---------------------------------------------------------------- Стены
    private void drawWalls(Graphics2D g, int x0, int y0, int x1, int y1) {
        Camera cam = game.camera;
        float cx = cam.pos.x, cy = cam.pos.y;
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                Tile t = game.map.tiles[tx][ty];
                if (!t.solid) continue;
                float wx = tx * GameMap.TS, wy = ty * GameMap.TS;
                float ox = (wx + 24 - cx) * 0.035f;
                float oy = (wy + 24 - cy) * 0.035f;
                float height = switch (t) {
                    case CRATE, CRATE_METAL, BARREL -> 0.5f;
                    case FENCE -> 0.25f;
                    case GLASS -> 0.7f;
                    default -> 1f;
                };
                ox *= height; oy *= height;

                if (s.shadows) {
                    g.setColor(new Color(0, 0, 0, 110));
                    g.fillRect((int) (wx + ox * 1.6f), (int) (wy + oy * 1.6f), GameMap.TS, GameMap.TS);
                }
                if (t == Tile.FENCE) {
                    g.setColor(t.colorDark);
                    for (int i = 0; i < 4; i++) {
                        g.fillRect((int) (wx + i * 12 + 2), (int) wy, 3, GameMap.TS);
                        g.fillRect((int) wx, (int) (wy + i * 12 + 2), GameMap.TS, 3);
                    }
                    continue;
                }
                if (t == Tile.GLASS) {
                    g.setColor(new Color(0.54f, 0.78f, 0.88f, 0.42f));
                    g.fillRect((int) wx, (int) wy, GameMap.TS, GameMap.TS);
                    g.setColor(new Color(0.8f, 0.94f, 1f, 0.5f));
                    g.drawLine((int) wx, (int) wy, (int) (wx + GameMap.TS), (int) (wy + GameMap.TS));
                    continue;
                }

                // «Стена» — верхняя грань со смещением
                g.setColor(t.colorDark);
                g.fillRect((int) wx, (int) wy, GameMap.TS, GameMap.TS);
                g.setColor(t.color);
                g.fillRect((int) (wx + ox), (int) (wy + oy), GameMap.TS, GameMap.TS);

                if (t == Tile.BARREL) {
                    g.setColor(new Color(0xC65545));
                    g.fillOval((int) (wx + ox + 6), (int) (wy + oy + 6), GameMap.TS - 12, GameMap.TS - 12);
                    g.setColor(new Color(0x2A1410));
                    g.drawOval((int) (wx + ox + 6), (int) (wy + oy + 6), GameMap.TS - 12, GameMap.TS - 12);
                } else if (t == Tile.CRATE || t == Tile.CRATE_METAL) {
                    g.setColor(new Color(0, 0, 0, 60));
                    g.drawRect((int) (wx + ox + 5), (int) (wy + oy + 5), GameMap.TS - 10, GameMap.TS - 10);
                    g.drawLine((int) (wx + ox), (int) (wy + oy), (int) (wx + ox + GameMap.TS), (int) (wy + oy + GameMap.TS));
                } else {
                    g.setColor(new Color(0, 0, 0, 40));
                    g.drawRect((int) (wx + ox), (int) (wy + oy), GameMap.TS, GameMap.TS);
                }

                float dmg = game.map.damage[tx][ty];
                if (dmg > 5) {
                    g.setColor(new Color(0, 0, 0, Math.min(140, (int) (dmg * 0.6f))));
                    for (int i = 0; i < 3; i++) {
                        int px = (int) (wx + ox + 8 + (i * 13 + (int) dmg) % 30);
                        int py = (int) (wy + oy + 8 + (i * 19 + (int) dmg) % 30);
                        g.fillOval(px, py, 5, 5);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ Сущности
    private void drawPickups(Graphics2D g) {
        for (Pickup p : game.pickups) {
            if (!game.camera.visible(p.pos.x, p.pos.y, 60)) continue;
            float bob = (float) Math.sin(p.bob) * 2f;
            AffineTransform t = g.getTransform();
            g.translate(p.pos.x, p.pos.y + bob);
            g.rotate(p.angle);
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRoundRect(-16, -2, 34, 8, 4, 4);
            g.setColor(new Color(0x39, 0x3B, 0x40));
            g.fillRoundRect(-16, -4, 32, 7, 4, 4);
            g.setColor(p.type.tracer);
            g.fillRect(2, -3, 10, 3);
            g.setTransform(t);
        }
        if (game.looseBomb != null) drawBombIcon(g, game.looseBomb, false);
        if (game.bomb != null && game.bomb.planted && !game.bomb.exploded) drawBombIcon(g, game.bomb.pos, true);
    }

    private void drawBombIcon(Graphics2D g, Vec2 p, boolean planted) {
        float pulse = planted ? (0.5f + 0.5f * (float) Math.sin(game.time * 8)) : 0.4f;
        g.setColor(new Color(1f, 0.25f, 0.2f, 0.25f + pulse * 0.35f));
        g.fillOval((int) (p.x - 20), (int) (p.y - 20), 40, 40);
        g.setColor(new Color(0x2A2A2E));
        g.fillRoundRect((int) (p.x - 9), (int) (p.y - 7), 18, 14, 4, 4);
        g.setColor(new Color(1f, 0.3f, 0.25f, 0.4f + pulse * 0.6f));
        g.fillOval((int) (p.x - 3), (int) (p.y - 3), 6, 6);
    }

    private void drawHostages(Graphics2D g) {
        for (Hostage hst : game.hostages) {
            if (hst.rescued) continue;
            if (!game.camera.visible(hst.pos.x, hst.pos.y, 60)) continue;
            g.setColor(new Color(0, 0, 0, 80));
            g.fillOval((int) (hst.pos.x - 13), (int) (hst.pos.y - 9), 26, 20);
            if (!hst.alive) {
                g.setColor(new Color(0x5A3A3A));
                g.fillOval((int) (hst.pos.x - 12), (int) (hst.pos.y - 8), 24, 17);
                continue;
            }
            float wob = (float) Math.sin(hst.anim * 2.4) * 1.5f;
            g.setColor(new Color(0xE8C46A));
            g.fillOval((int) (hst.pos.x - 11), (int) (hst.pos.y - 11 + wob), 22, 22);
            g.setColor(new Color(0x6B5220));
            g.drawOval((int) (hst.pos.x - 11), (int) (hst.pos.y - 11 + wob), 22, 22);
            g.setColor(new Color(0x2A2A2A));
            g.fillRect((int) (hst.pos.x - 7), (int) (hst.pos.y - 4 + wob), 14, 5);
        }
    }

    private void drawCorpses(Graphics2D g) {
        for (Actor a : game.actors) {
            if (a.alive) continue;
            if (!game.camera.visible(a.pos.x, a.pos.y, 60)) continue;
            float fade = MathUtil.clamp(1f - (a.deathTimer - 12f) / 4f, 0, 1);
            if (fade <= 0) continue;
            AffineTransform t = g.getTransform();
            g.translate(a.pos.x, a.pos.y);
            g.rotate(a.bodyAngle);
            g.setColor(new Color(0.18f, 0.15f, 0.14f, 0.55f * fade));
            g.fillOval(-16, -10, 32, 20);
            Color c = a.color;
            g.setColor(new Color(c.getRed() / 255f * 0.5f, c.getGreen() / 255f * 0.5f, c.getBlue() / 255f * 0.5f, 0.85f * fade));
            g.fillOval(-13, -8, 26, 16);
            g.setTransform(t);
        }
    }

    private void drawActors(Graphics2D g) {
        for (Actor a : game.actors) {
            if (!a.alive) continue;
            if (!game.camera.visible(a.pos.x, a.pos.y, 80)) continue;
            if (!game.visibleToPlayer(a)) continue;
            drawActor(g, a);
        }
    }

    private void drawActor(Graphics2D g, Actor a) {
        AffineTransform old = g.getTransform();
        g.translate(a.pos.x, a.pos.y);

        // Тень
        g.setColor(new Color(0, 0, 0, 90));
        g.fillOval((int) -a.radius - 2, (int) -a.radius + 2, (int) a.radius * 2 + 4, (int) a.radius * 2);

        g.rotate(a.bodyAngle);

        // Ноги (простая анимация шага)
        float legPhase = a.moving ? (float) Math.sin(a.stepPhase * 22f) * 5f : 0;
        g.setColor(new Color(0x2E3034));
        g.fillRoundRect(-6, (int) (-a.radius + 1 + legPhase * 0.4f), 12, 6, 4, 4);
        g.fillRoundRect(-6, (int) (a.radius - 7 - legPhase * 0.4f), 12, 6, 4, 4);

        // Корпус
        Color c = a.color;
        if (a.hurtFlash > 0.02f) {
            float k = a.hurtFlash;
            c = new Color(MathUtil.clamp(c.getRed() / 255f + k * 0.6f, 0, 1),
                    MathUtil.clamp(c.getGreen() / 255f * (1 - k * 0.4f), 0, 1),
                    MathUtil.clamp(c.getBlue() / 255f * (1 - k * 0.4f), 0, 1));
        }
        float bodyR = a.crouching ? a.radius * 0.82f : a.radius;
        g.setColor(c.darker());
        g.fillOval((int) -bodyR - 1, (int) -bodyR - 1, (int) bodyR * 2 + 2, (int) bodyR * 2 + 2);
        g.setColor(c);
        g.fillOval((int) -bodyR, (int) -bodyR, (int) bodyR * 2, (int) bodyR * 2);

        // Броня / шлем
        if (a.armor > 0) {
            g.setColor(new Color(0.85f, 0.88f, 0.95f, 0.30f));
            g.fillOval((int) -bodyR + 3, (int) -bodyR + 3, (int) bodyR * 2 - 6, (int) bodyR * 2 - 6);
        }
        // Голова со взглядом
        g.setColor(new Color(0x1E2024));
        g.fillOval(1, -5, 10, 10);

        // Оружие
        WeaponType wt = a.currentType();
        int len = switch (wt.cat) {
            case SNIPER, HEAVY -> 30;
            case RIFLE -> 25;
            case SMG -> 19;
            case SHOTGUN -> 23;
            case PISTOL -> 13;
            default -> 10;
        };
        if (a.slot == Actor.SLOT_NADE) {
            g.setColor(new Color(0x4A5A3A));
            g.fillOval(8, -4, 9, 9);
        } else if (a.slot == Actor.SLOT_BOMB) {
            g.setColor(new Color(0x40403A));
            g.fillRect(6, -6, 12, 12);
        } else {
            g.setColor(new Color(0x24262A));
            g.fillRect(6, -2, len, 5);
            g.setColor(new Color(0x3A3D42));
            g.fillRect(6, -2, len / 2, 2);
        }

        // Вспышка у дульного среза
        Weapon w = a.currentWeapon();
        if (s.muzzleFlash && w.flashTimer > 0 && a.isHoldingGun()) {
            float k = w.flashTimer / 0.055f;
            g.setColor(new Color(1f, 0.85f, 0.45f, 0.75f * k));
            g.fillOval(6 + len - 4, (int) (-7 * k), (int) (16 * k), (int) (14 * k));
        }

        g.setTransform(old);

        // Горение
        if (a.burn > 0) {
            g.setColor(new Color(1f, 0.5f, 0.15f, 0.25f + 0.15f * (float) Math.sin(game.time * 18)));
            g.fillOval((int) (a.pos.x - 18), (int) (a.pos.y - 18), 36, 36);
        }
        // Ослепление
        if (a.flash > 0.4f && a != game.player) {
            g.setColor(new Color(1f, 1f, 1f, 0.25f));
            g.fillOval((int) (a.pos.x - 20), (int) (a.pos.y - 20), 40, 40);
        }
        // Индикатор закладки/разминирования
        if (a.planting || a.defusing) {
            float p = a.planting ? a.plantProgress : a.defuseProgress;
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect((int) (a.pos.x - 22), (int) (a.pos.y - 30), 44, 6);
            g.setColor(a.planting ? new Color(0xFF8A5A) : new Color(0x7FD0FF));
            g.fillRect((int) (a.pos.x - 21), (int) (a.pos.y - 29), (int) (42 * p), 4);
        }
        // Бомба в руках
        if (a.hasBomb) {
            g.setColor(new Color(0xFF6B4A));
            g.fillRect((int) (a.pos.x - 4), (int) (a.pos.y - a.radius - 10), 8, 6);
        }
    }

    private void drawGrenades(Graphics2D g) {
        for (Grenade gr : game.grenades) {
            if (!game.camera.visible(gr.pos.x, gr.pos.y, 60)) continue;
            g.setColor(new Color(0, 0, 0, 80));
            float shadowScale = 1f - MathUtil.clamp(gr.z / 220f, 0, 0.6f);
            g.fillOval((int) (gr.pos.x - 5 * shadowScale), (int) (gr.pos.y - 3 * shadowScale),
                    (int) (10 * shadowScale), (int) (7 * shadowScale));
            AffineTransform t = g.getTransform();
            g.translate(gr.pos.x, gr.pos.y - gr.z * 0.5f);
            g.rotate(gr.spin);
            Color c = switch (gr.kind) {
                case Actor.NADE_HE -> new Color(0x4A6B3A);
                case Actor.NADE_FLASH -> new Color(0xB0B4BA);
                case Actor.NADE_SMOKE -> new Color(0x5A7A8A);
                default -> new Color(0xB05A2A);
            };
            g.setColor(c);
            g.fillRoundRect(-5, -6, 10, 13, 5, 5);
            g.setColor(c.darker());
            g.drawRoundRect(-5, -6, 10, 13, 5, 5);
            if (gr.kind == Actor.NADE_MOLOTOV) {
                g.setColor(new Color(1f, 0.6f, 0.2f, 0.9f));
                g.fillOval(-2, -9, 4, 4);
            }
            g.setTransform(t);
        }
    }

    private void drawFires(Graphics2D g) {
        for (FireArea f : game.fires) {
            for (FireArea.Patch p : f.patches) {
                if (p.health <= 0) continue;
                if (!game.camera.visible(p.p.x, p.p.y, 80)) continue;
                float k = f.intensity * MathUtil.clamp(p.health, 0, 1);
                float r = p.r * (0.85f + 0.15f * (float) Math.sin(game.time * 5 + p.phase));
                RadialGradientPaint paint = new RadialGradientPaint(
                        new Point2D.Float(p.p.x, p.p.y), Math.max(4f, r),
                        new float[]{0f, 0.55f, 1f},
                        new Color[]{new Color(1f, 0.55f, 0.15f, 0.55f * k),
                                new Color(1f, 0.32f, 0.08f, 0.32f * k),
                                new Color(0.4f, 0.12f, 0.03f, 0f)});
                g.setPaint(paint);
                g.fillOval((int) (p.p.x - r), (int) (p.p.y - r), (int) (r * 2), (int) (r * 2));
            }
        }
        g.setPaint(null);
    }

    private void drawLights(Graphics2D g) {
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        for (Actor a : game.actors) {
            if (!a.alive) continue;
            Weapon w = a.currentWeapon();
            if (w.flashTimer <= 0) continue;
            if (!game.visibleToPlayer(a)) continue;
            Vec2 m = a.muzzlePos();
            float k = w.flashTimer / 0.055f;
            float r = 120 + w.type.damage * 1.2f;
            RadialGradientPaint p = new RadialGradientPaint(new Point2D.Float(m.x, m.y), r,
                    new float[]{0f, 1f},
                    new Color[]{new Color(1f, 0.85f, 0.5f, 0.30f * k), new Color(1f, 0.7f, 0.3f, 0f)});
            g.setPaint(p);
            g.fillOval((int) (m.x - r), (int) (m.y - r), (int) (r * 2), (int) (r * 2));
        }
        g.setPaint(null);
        g.setComposite(old);
    }

    private void drawParticles(Graphics2D g) {
        for (Fx.Particle p : game.fx.particles()) {
            if (!p.active) continue;
            if (!game.camera.visible(p.x, p.y, 60)) continue;
            float k = MathUtil.clamp(p.life / p.maxLife, 0, 1);
            Color c = p.color;
            float alpha = switch (p.type) {
                case SMOKE -> 0.35f * k;
                case FIRE -> 0.75f * k;
                case DUST -> 0.30f * k;
                default -> Math.min(1f, 0.9f * k + 0.1f);
            };
            g.setColor(new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, MathUtil.clamp(alpha, 0, 1)));
            float size = Math.max(1f, p.size);
            if (p.type == Fx.PType.SHELL) {
                AffineTransform t = g.getTransform();
                g.translate(p.x, p.y - p.z * 0.4f);
                g.rotate(p.rot);
                g.fillRect(-3, -1, 6, 2);
                g.setTransform(t);
            } else if (p.type == Fx.PType.SMOKE || p.type == Fx.PType.FIRE || p.type == Fx.PType.DUST) {
                g.fillOval((int) (p.x - size), (int) (p.y - size), (int) size * 2, (int) size * 2);
            } else {
                g.fillOval((int) (p.x - size / 2), (int) (p.y - size / 2 - p.z * 0.4f), (int) size, (int) size);
            }
        }
    }

    private void drawTracers(Graphics2D g) {
        Stroke old = g.getStroke();
        for (Fx.Tracer t : game.fx.tracers) {
            float k = t.life / t.maxLife;
            Color c = t.color;
            g.setColor(new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.75f * k));
            g.setStroke(new BasicStroke(t.width * k + 0.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Float(t.x0, t.y0, t.x1, t.y1));
        }
        g.setStroke(old);
    }

    private void drawSmoke(Graphics2D g) {
        for (SmokeCloud sc : game.smokes) {
            float scale = sc.radius / sc.targetRadius;
            for (int i = 0; i < sc.puffs.size(); i++) {
                Vec2 c = sc.puffs.get(i);
                float r = sc.puffR.get(i) * scale;
                if (r < 2) continue;
                if (!game.camera.visible(c.x, c.y, r + 60)) continue;
                float a = MathUtil.clamp(sc.density, 0, 1);
                if (s.smokeVolumetric) {
                    for (int layer = 0; layer < 3; layer++) {
                        float lr = r * (1f - layer * 0.18f);
                        float ph = game.time * 0.6f + i * 1.7f + layer;
                        float ox = (float) Math.sin(ph) * 5f, oy = (float) Math.cos(ph * 0.8) * 5f;
                        RadialGradientPaint p = new RadialGradientPaint(
                                new Point2D.Float(c.x + ox, c.y + oy), Math.max(3f, lr),
                                new float[]{0f, 0.6f, 1f},
                                new Color[]{new Color(0.80f, 0.80f, 0.82f, 0.50f * a),
                                        new Color(0.74f, 0.74f, 0.77f, 0.36f * a),
                                        new Color(0.70f, 0.70f, 0.73f, 0f)});
                        g.setPaint(p);
                        g.fillOval((int) (c.x + ox - lr), (int) (c.y + oy - lr), (int) (lr * 2), (int) (lr * 2));
                    }
                } else {
                    g.setColor(new Color(0.76f, 0.76f, 0.79f, 0.72f * a));
                    g.fillOval((int) (c.x - r), (int) (c.y - r), (int) (r * 2), (int) (r * 2));
                }
            }
        }
        g.setPaint(null);
    }

    // ------------------------------------------------------------ Туман войны
    private void drawFog(Graphics2D g, int w, int h) {
        Actor eye = game.player.alive ? game.player : game.spectating;
        if (eye == null) return;

        int n = MathUtil.clamp(s.fovRays, 48, 1000);
        if (n != rayCount) {
            rayCount = n;
            for (int i = 0; i < n; i++) {
                float a = MathUtil.TAU * i / n;
                rayCos[i] = (float) Math.cos(a);
                raySin[i] = (float) Math.sin(a);
            }
        }

        float maxView = eye.effectiveViewDist();
        float near = 130f;
        float halfFov = eye.viewFov * 0.5f;
        Camera cam = game.camera;

        Path2D.Float poly = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        poly.moveTo(0, 0);
        poly.lineTo(w, 0);
        poly.lineTo(w, h);
        poly.lineTo(0, h);
        poly.closePath();

        Path2D.Float vis = new Path2D.Float();
        for (int i = 0; i < n; i++) {
            float ang = MathUtil.TAU * i / n;
            boolean inCone = Math.abs(MathUtil.angleDiff(eye.aim, ang)) <= halfFov;
            float limit = inCone ? maxView : near;
            float d = castRay(eye.pos.x, eye.pos.y, rayCos[i], raySin[i], limit);
            float px = cam.worldToScreenX(eye.pos.x + rayCos[i] * d);
            float py = cam.worldToScreenY(eye.pos.y + raySin[i] * d);
            if (i == 0) vis.moveTo(px, py); else vis.lineTo(px, py);
            rayDist[i] = d;
        }
        vis.closePath();
        poly.append(vis, false);

        g.setColor(new Color(0.015f, 0.02f, 0.035f, 0.76f));
        g.fill(poly);

        // Лёгкая кромка по краю конуса (тонкая — толстая обводка стоит дороже всей заливки)
        g.setColor(new Color(0f, 0f, 0f, 0.30f));
        g.setStroke(new BasicStroke(5f));
        g.draw(vis);
        g.setStroke(new BasicStroke(1f));
    }

    /** Луч до ближайшей стены или дыма. */
    private float castRay(float ox, float oy, float dx, float dy, float maxDist) {
        GameMap.RayHit hit = game.map.raycastVision(ox, oy, dx, dy, maxDist);
        float d = hit.dist;
        for (SmokeCloud sc : game.smokes) {
            if (sc.density < 0.2f) continue;
            float scale = sc.radius / sc.targetRadius;
            for (int i = 0; i < sc.puffs.size(); i++) {
                Vec2 c = sc.puffs.get(i);
                float r = sc.puffR.get(i) * scale * 0.86f;
                float t = rayCircle(ox, oy, dx, dy, c.x, c.y, r);
                if (t >= 0 && t < d) d = t;
            }
        }
        return d;
    }

    private float rayCircle(float ox, float oy, float dx, float dy, float cx, float cy, float r) {
        float mx = ox - cx, my = oy - cy;
        float b = mx * dx + my * dy;
        float c = mx * mx + my * my - r * r;
        if (c > 0 && b > 0) return -1;
        float disc = b * b - c;
        if (disc < 0) return -1;
        float t = -b - (float) Math.sqrt(disc);
        return Math.max(t, 0);
    }

    // -------------------------------------------------------------- Подписи
    private void drawNameTags(Graphics2D g, int w, int h) {
        Camera cam = game.camera;
        g.setFont(new Font("Dialog", Font.BOLD, 11));
        for (Actor a : game.actors) {
            if (!a.alive || a == game.player) continue;
            if (!game.visibleToPlayer(a)) continue;
            boolean ally = !game.mode.ffa() && a.team == game.player.team;
            boolean aimedAt = false;
            if (game.player.alive) {
                float ang = (float) Math.atan2(a.pos.y - game.player.pos.y, a.pos.x - game.player.pos.x);
                aimedAt = Math.abs(MathUtil.angleDiff(game.player.aim, ang)) < 0.13f
                        && game.losClear(game.player.pos, a.pos);
            }
            if (!ally && !aimedAt) continue;

            float sx = cam.worldToScreenX(a.pos.x);
            float sy = cam.worldToScreenY(a.pos.y) - 26 * cam.zoom;
            if (sx < -60 || sy < -30 || sx > w + 60 || sy > h + 30) continue;

            String label = a.name;
            int tw = g.getFontMetrics().stringWidth(label);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect((int) (sx - tw / 2 - 4), (int) (sy - 11), tw + 8, 14, 4, 4);
            g.setColor(ally ? new Color(0x9FE0A8) : new Color(0xFFB0A0));
            g.drawString(label, sx - tw / 2f, sy);

            if (ally) {
                g.setColor(new Color(0, 0, 0, 140));
                g.fillRect((int) (sx - 18), (int) (sy + 4), 36, 4);
                g.setColor(new Color(0x7FD08A));
                g.fillRect((int) (sx - 17), (int) (sy + 5), (int) (34 * MathUtil.clamp(a.hp / a.maxHp, 0, 1)), 2);
            }
        }

        // Маркеры союзников за экраном
        if (game.player.alive && !game.mode.ffa()) {
            for (Actor a : game.actors) {
                if (!a.alive || a == game.player || a.team != game.player.team) continue;
                float sx = cam.worldToScreenX(a.pos.x), sy = cam.worldToScreenY(a.pos.y);
                if (sx > 0 && sy > 0 && sx < w && sy < h) continue;
                float cx = w / 2f, cy = h / 2f;
                float ang = (float) Math.atan2(sy - cy, sx - cx);
                float rx = cx + (float) Math.cos(ang) * (Math.min(w, h) / 2f - 40);
                float ry = cy + (float) Math.sin(ang) * (Math.min(w, h) / 2f - 40);
                g.setColor(new Color(0x6FD08A));
                AffineTransform t = g.getTransform();
                g.translate(rx, ry);
                g.rotate(ang);
                g.fillPolygon(new int[]{8, -5, -5}, new int[]{0, -5, 5}, 3);
                g.setTransform(t);
            }
        }
    }

    // ------------------------------------------------------------- Оверлеи
    private void drawOverlays(Graphics2D g, int w, int h) {
        Player p = game.player;

        // Виньетка и зерно — одной готовой текстурой
        if (s.grain) g.drawImage(overlay(w, h), 0, 0, null);

        // Огонь под ногами
        if (p.alive && p.burn > 0) {
            float k = MathUtil.clamp(p.burn / 3f, 0, 1);
            g.setColor(new Color(1f, 0.35f, 0.1f, 0.10f + 0.12f * k));
            g.fillRect(0, 0, w, h);
        }

        // Ослепление
        if (p.alive && p.flash > 0.01f) {
            float a = MathUtil.clamp(p.flash, 0, 1);
            g.setColor(new Color(1f, 1f, 1f, a * 0.97f));
            g.fillRect(0, 0, w, h);
        }

        // Урон
        if (p.alive && (p.hp < 40 || game.damageIndicator > 0)) {
            float k = p.hp < 40 ? (1f - p.hp / 40f) * (0.55f + 0.45f * (float) Math.sin(game.lowHpPulse * 4)) : 0;
            k = Math.max(k * 0.45f, game.damageIndicator * 0.5f);
            RadialGradientPaint rg = new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f),
                    Math.max(w, h) * 0.6f,
                    new float[]{0.4f, 1f},
                    new Color[]{new Color(0.6f, 0f, 0f, 0f), new Color(0.6f, 0f, 0f, MathUtil.clamp(k, 0, 0.75f))});
            g.setPaint(rg);
            g.fillRect(0, 0, w, h);
            g.setPaint(null);
        }

        // Направление последнего урона
        if (game.damageIndicator > 0.02f) {
            float ang = game.lastDamageAngle;
            AffineTransform t = g.getTransform();
            g.translate(w / 2f, h / 2f);
            g.rotate(ang);
            g.setColor(new Color(1f, 0.25f, 0.2f, game.damageIndicator * 0.8f));
            g.fillArc(-110, -110, 220, 220, -18, 36);
            g.setTransform(t);
        }
    }
}
