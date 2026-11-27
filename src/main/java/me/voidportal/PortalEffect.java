
package me.voidportal;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public final class PortalEffect {

    private PortalEffect() {
    }

    public static void spawn(Location center, long tick) {
        if (center == null || center.getWorld() == null) {
            return;
        }

        VoidPortal plugin = VoidPortal.getPlugin(VoidPortal.class);
        FileConfiguration config = plugin.getConfig();

        World world = center.getWorld();

        // Configurable ring sizes
        double outerRadius = config.getDouble(
                "portal.outer-radius",
                16.0
        );

        double innerRadius = config.getDouble(
                "portal.inner-radius",
                2.5
        );

        int particleCount = config.getInt(
                "portal.particle-count",
                220
        );

        int red = config.getInt("portal.red", 255);
        int green = config.getInt("portal.green", 225);
        int blue = config.getInt("portal.blue", 190);

        // Keep settings within safe limits
        outerRadius = Math.max(3.0, Math.min(outerRadius, 64.0));
        innerRadius = Math.max(0.5, Math.min(innerRadius, outerRadius - 1.0));
        particleCount = Math.max(40, Math.min(particleCount, 360));

        Particle.DustOptions outerDust = new Particle.DustOptions(
                Color.fromRGB(red, green, blue),
                1.5F
        );

        Particle.DustOptions innerDust = new Particle.DustOptions(
                Color.fromRGB(190, 90, 255),
                1.4F
        );

        Particle.DustOptions glowDust = new Particle.DustOptions(
                Color.fromRGB(255, 250, 230),
                1.2F
        );

        // Ground-level ring center
        Location groundCenter = center.clone().add(0, 0.15, 0);

        // Large irregular outer ring
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

            // Bright accents around the perimeter
            if (i % 4 == 0) {
                world.spawnParticle(
                        Particle.END_ROD,
                        point,
                        1,
                        0.015,
                        0.025,
                        0.015,
                        0
                );
            }
        }

        // Small circular ring around the center
        int innerCount = 64;

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

        // Subtle energy vortex at the center
        for (int i = 0; i < 24; i++) {
            double progress = i / 24.0;

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
                        glowDust
                );
            }
        }
    }
}
