
package me.voidportal;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

public final class PortalEffect {

    private PortalEffect() {
    }

    // Backward-compatible method for the existing main class
    public static void spawn(Location center, long tick) {
        spawn(center, tick, 16.0, 2.5, 180);
    }

    public static void spawn(
            Location center,
            long tick,
            double outerRadius,
            double innerRadius,
            int particleCount
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

        Particle.DustOptions outerDust = new Particle.DustOptions(
                Color.fromRGB(255, 230, 195),
                1.5F
        );

        Particle.DustOptions innerDust = new Particle.DustOptions(
                Color.fromRGB(185, 65, 255),
                1.4F
        );

        Particle.DustOptions brightDust = new Particle.DustOptions(
                Color.fromRGB(255, 250, 235),
                1.2F
        );

        Location groundCenter = center.clone().add(0, 0.12, 0);

        // Large irregular ground ring
        for (int i = 0; i < particleCount; i++) {
            double angle = Math.PI * 2.0 * i / particleCount;

            double wave1 = Math.sin(angle * 5.0 + tick * 0.01) * 0.45;
            double wave2 = Math.cos(angle * 9.0 - tick * 0.008) * 0.25;
            double wave3 = Math.sin(angle * 13.0) * 0.12;

            double radius = outerRadius + wave1 + wave2 + wave3;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location point = groundCenter.clone().add(x, 0, z);

            world.spawnParticle(
                    Particle.DUST,
                    point,
                    1,
                    0,
                    0,
                    0,
                    0,
                    outerDust
            );

            if (i % 4 == 0) {
                world.spawnParticle(
                        Particle.END_ROD,
                        point,
                        1,
                        0.02,
                        0.025,
                        0.02,
                        0
                );
            }
        }

        // Inner purple ring
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

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location point = groundCenter.clone().add(x, 0.08, z);

            world.spawnParticle(
                    Particle.DUST,
                    point,
                    1,
                    0,
                    0,
                    0,
                    0,
                    innerDust
            );
        }

        // Subtle center vortex
        for (int i = 0; i < 28; i++) {
            double progress = i / 28.0;

            double angle = progress * Math.PI * 4.0
                    + tick * 0.035;

            double radius = progress * innerRadius * 0.65;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location point = groundCenter.clone().add(x, 0.12, z);

            world.spawnParticle(
                    Particle.PORTAL,
                    point,
                    1,
                    0.03,
                    0.04,
                    0.03,
                    0.01
            );

            if (i % 4 == 0) {
                world.spawnParticle(
                        Particle.DUST,
                        point,
                        1,
                        0,
                        0,
                        0,
                        0,
                        brightDust
                );
            }
        }
    }
}
