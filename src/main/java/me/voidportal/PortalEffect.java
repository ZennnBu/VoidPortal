
package me.voidportal;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.Locale;

public final class PortalEffect {

    private PortalEffect() {
    }

    public static void spawn(Location center, long tick) {
        spawn(center, tick, 16.0, 2.5, 180, "dark_purple");
    }

    public static void spawn(
            Location center,
            long tick,
            double outerRadius,
            double innerRadius,
            int particleCount
    ) {
        spawn(
                center,
                tick,
                outerRadius,
                innerRadius,
                particleCount,
                "dark_purple"
        );
    }

    public static void spawn(
            Location center,
            long tick,
            double outerRadius,
            double innerRadius,
            int particleCount,
            String glowColor
    ) {
        if (center == null || center.getWorld() == null) {
            return;
        }

        World world = center.getWorld();

        outerRadius = Math.max(3.0, Math.min(outerRadius, 64.0));
        innerRadius = Math.max(
                0.5,
                Math.min(innerRadius, outerRadius - 0.5)
        );

        particleCount = Math.max(80, Math.min(particleCount, 360));

        Color selectedColor = getColor(glowColor);

        Particle.DustOptions outerDust =
                new Particle.DustOptions(selectedColor, 1.5F);

        Particle.DustOptions innerDust =
                new Particle.DustOptions(selectedColor, 1.4F);

        Particle.DustOptions brightDust =
                new Particle.DustOptions(
                        Color.fromRGB(255, 250, 235),
                        1.2F
                );

        Particle.DustOptions runeDust =
                new Particle.DustOptions(selectedColor, 1.1F);

        Location base = center.clone().add(0, 0.12, 0);

        // 1. Animated outer ground ring
        for (int i = 0; i < particleCount; i++) {
            double angle = Math.PI * 2.0 * i / particleCount;

            double wave1 =
                    Math.sin(angle * 5.0 + tick * 0.01) * 0.45;

            double wave2 =
                    Math.cos(angle * 9.0 - tick * 0.008) * 0.25;

            double wave3 = Math.sin(angle * 13.0) * 0.12;

            double radius = outerRadius + wave1 + wave2 + wave3;

            Location point = base.clone().add(
                    Math.cos(angle) * radius,
                    0,
                    Math.sin(angle) * radius
            );

            world.spawnParticle(
                    Particle.DUST,
                    point,
                    1,
                    0, 0, 0, 0,
                    outerDust
            );

            if (i % 4 == 0) {
                world.spawnParticle(
                        Particle.END_ROD,
                        point,
                        1,
                        0.02, 0.025, 0.02,
                        0
                );
            }

            if (i % 12 == 0) {
                world.spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        point.clone().add(0, 0.25, 0),
                        3,
                        0.12, 0.35, 0.12,
                        0.02
                );
            }
        }

        // 2. Floating magical rune ring
        int runeCount = 12;

        for (int i = 0; i < runeCount; i++) {
            double angle = Math.PI * 2.0 * i / runeCount
                    + tick * 0.006;

            double runeRadius = outerRadius * 0.78;

            double x = Math.cos(angle) * runeRadius;
            double z = Math.sin(angle) * runeRadius;

            double height = 0.65
                    + Math.sin(tick * 0.04 + i) * 0.18;

            Location rune = base.clone().add(x, height, z);

            world.spawnParticle(
                    Particle.ENCHANT,
                    rune,
                    5,
                    0.12, 0.18, 0.12,
                    0.4
            );

            world.spawnParticle(
                    Particle.DUST,
                    rune,
                    2,
                    0.04, 0.08, 0.04,
                    0,
                    runeDust
            );
        }

        // 3. Inner glow ring
        int innerCount = Math.max(
                48,
                (int) (innerRadius * 24.0)
        );

        innerCount = Math.min(innerCount, 160);

        for (int i = 0; i < innerCount; i++) {
            double angle = Math.PI * 2.0 * i / innerCount
                    - tick * 0.018;

            double radius = innerRadius
                    + Math.sin(angle * 4.0 + tick * 0.02) * 0.08;

            Location point = base.clone().add(
                    Math.cos(angle) * radius,
                    0.08,
                    Math.sin(angle) * radius
            );

            world.spawnParticle(
                    Particle.DUST,
                    point,
                    1,
                    0, 0, 0, 0,
                    innerDust
            );

            if (i % 8 == 0) {
                world.spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        point.clone().add(0, 0.2, 0),
                        2,
                        0.08, 0.15, 0.08,
                        0.01
                );
            }
        }

        // 4. Void Core Vortex
        for (int i = 0; i < 36; i++) {
            double progress = i / 36.0;

            double angle = progress * Math.PI * 6.0
                    + tick * 0.035;

            double radius = progress * innerRadius * 0.7;

            Location point = base.clone().add(
                    Math.cos(angle) * radius,
                    0.15 + progress * 0.45,
                    Math.sin(angle) * radius
            );

            world.spawnParticle(
                    Particle.PORTAL,
                    point,
                    1,
                    0.025, 0.04, 0.025,
                    0.01
            );

            if (i % 4 == 0) {
                world.spawnParticle(
                        Particle.DUST,
                        point,
                        1,
                        0, 0, 0, 0,
                        brightDust
                );
            }
        }
    }

    public static Color getColor(String name) {
        if (name == null) {
            return Color.fromRGB(185, 65, 255);
        }

        return switch (name.toLowerCase(Locale.ROOT)) {
            case "black" -> Color.fromRGB(0, 0, 0);
            case "dark_blue" -> Color.fromRGB(0, 0, 170);
            case "dark_green" -> Color.fromRGB(0, 170, 0);
            case "dark_aqua" -> Color.fromRGB(0, 170, 170);
            case "dark_red" -> Color.fromRGB(170, 0, 0);
            case "dark_purple" -> Color.fromRGB(170, 0, 170);
            case "gold" -> Color.fromRGB(255, 170, 0);
            case "gray" -> Color.fromRGB(170, 170, 170);
            case "dark_gray" -> Color.fromRGB(85, 85, 85);
            case "blue" -> Color.fromRGB(85, 85, 255);
            case "green" -> Color.fromRGB(85, 255, 85);
            case "aqua" -> Color.fromRGB(85, 255, 255);
            case "red" -> Color.fromRGB(255, 85, 85);
            case "light_purple" -> Color.fromRGB(255, 85, 255);
            case "yellow" -> Color.fromRGB(255, 255, 85);
            case "white" -> Color.fromRGB(255, 255, 255);
            default -> Color.fromRGB(185, 65, 255);
        };
    }

    public static void spawnActivation(
            Location center,
            double radius
    ) {
        if (center == null || center.getWorld() == null) {
            return;
        }

        World world = center.getWorld();

        radius = Math.max(1.0, Math.min(radius, 64.0));

        Particle.DustOptions shockDust =
                new Particle.DustOptions(
                        Color.fromRGB(190, 80, 255),
                        2.0F
                );

        for (int i = 0; i < 100; i++) {
            double angle = Math.PI * 2.0 * i / 100.0;

            Location point = center.clone().add(
                    Math.cos(angle) * radius,
                    0.15,
                    Math.sin(angle) * radius
            );

            world.spawnParticle(
                    Particle.DUST,
                    point,
                    1,
                    0, 0, 0, 0,
                    shockDust
            );

            if (i % 5 == 0) {
                world.spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        point.clone().add(0, 0.3, 0),
                        4,
                        0.15, 0.25, 0.15,
                        0.02
                );
            }
        }

        world.spawnParticle(
                Particle.FLASH,
                center.clone().add(0, 0.5, 0),
                1
        );
    }

    public static void spawnOpening(
            Location center,
            long tick,
            double radius
    ) {
        if (center == null || center.getWorld() == null) {
            return;
        }

        World world = center.getWorld();

        double progress = Math.min(1.0, tick / 60.0);
        double currentRadius = Math.max(0.5, radius * progress);

        for (int i = 0; i < 48; i++) {
            double angle = Math.PI * 2.0 * i / 48.0
                    + tick * 0.03;

            Location point = center.clone().add(
                    Math.cos(angle) * currentRadius,
                    0.15 + progress,
                    Math.sin(angle) * currentRadius
            );

            world.spawnParticle(
                    Particle.PORTAL,
                    point,
                    2,
                    0.04, 0.08, 0.04,
                    0.02
            );

            world.spawnParticle(
                    Particle.END_ROD,
                    point,
                    1,
                    0.03, 0.04, 0.03,
                    0
            );
        }
    }
}
