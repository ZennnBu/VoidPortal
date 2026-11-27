
package me.voidportal;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

public final class PortalEffect {

    private PortalEffect() {
    }

    public static void spawn(Location center, long tick) {
        if (center == null || center.getWorld() == null) {
            return;
        }

        World world = center.getWorld();

        // Galaxy colors
        Particle.DustOptions purple = new Particle.DustOptions(
                Color.fromRGB(155, 0, 255),
                2.0F
        );

        Particle.DustOptions brightPurple = new Particle.DustOptions(
                Color.fromRGB(220, 80, 255),
                1.5F
        );

        Particle.DustOptions white = new Particle.DustOptions(
                Color.fromRGB(255, 240, 255),
                1.2F
        );

        // Giant outer ring
        double outerRadius = 16.0;

        for (int i = 0; i < 180; i++) {
            double angle = (Math.PI * 2.0 * i / 180.0)
                    + tick * 0.006;

            double irregularity =
                    Math.sin(angle * 7.0 + tick * 0.03) * 0.25
                    + Math.cos(angle * 13.0) * 0.15;

            double radius = outerRadius + irregularity;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location point = center.clone().add(x, 0.15, z);

            world.spawnParticle(
                    Particle.DUST,
                    point,
                    1,
                    0,
                    0,
                    0,
                    0,
                    purple
            );

            if (i % 3 == 0) {
                world.spawnParticle(
                        Particle.END_ROD,
                        point,
                        1,
                        0.03,
                        0.02,
                        0.03,
                        0
                );
            }
        }

        // Inner glowing ring
        double innerRadius = 4.0;

        for (int i = 0; i < 72; i++) {
            double angle = Math.PI * 2.0 * i / 72.0
                    - tick * 0.025;

            double x = Math.cos(angle) * innerRadius;
            double z = Math.sin(angle) * innerRadius;

            Location point = center.clone().add(x, 0.25, z);

            world.spawnParticle(
                    Particle.DUST,
                    point,
                    1,
                    0,
                    0,
                    0,
                    0,
                    brightPurple
            );
        }

        // Galaxy spiral
        for (int arm = 0; arm < 3; arm++) {
            for (int i = 0; i < 90; i++) {
                double progress = i / 90.0;

                double angle = progress * Math.PI * 4.0
                        + arm * (Math.PI * 2.0 / 3.0)
                        + tick * 0.035;

                double radius = progress * 4.0;

                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                Location point = center.clone().add(
                        x,
                        0.3 + Math.sin(angle + tick * 0.04) * 0.12,
                        z
                );

                world.spawnParticle(
                        Particle.DUST,
                        point,
                        1,
                        0,
                        0,
                        0,
                        0,
                        brightPurple
                );

                if (i % 5 == 0) {
                    world.spawnParticle(
                            Particle.PORTAL,
                            point,
                            2,
                            0.08,
                            0.08,
                            0.08,
                            0.02
                    );
                }
            }
        }

        // Bright galaxy center
        Location core = center.clone().add(0, 0.35, 0);

        world.spawnParticle(
                Particle.END_ROD,
                core,
                12,
                0.35,
                0.12,
                0.35,
                0.01
        );

        world.spawnParticle(
                Particle.DUST,
                core,
                20,
                0.4,
                0.1,
                0.4,
                0,
                white
        );
    }
}
