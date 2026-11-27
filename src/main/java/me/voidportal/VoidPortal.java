
package me.voidportal;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VoidPortal extends JavaPlugin implements CommandExecutor {

    private File portalsFile;
    private YamlConfiguration portalsConfig;

    private final Map<UUID, Long> teleportCooldown = new HashMap<>();
    private long animationTick = 0L;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        portalsFile = new File(getDataFolder(), "portals.yml");

        if (!portalsFile.exists()) {
            try {
                getDataFolder().mkdirs();
                portalsFile.createNewFile();
            } catch (IOException exception) {
                getLogger().severe("Could not create portals.yml!");
                exception.printStackTrace();
            }
        }

        portalsConfig = YamlConfiguration.loadConfiguration(portalsFile);

        if (getCommand("portal") != null) {
            getCommand("portal").setExecutor(this);
        }

        startPortalAnimation();
        startPortalDetection();

        getLogger().info("VoidPortal enabled!");
        getLogger().info("Created by AimVortex");
    }

    @Override
    public void onDisable() {
        savePortals();
        teleportCooldown.clear();
        getLogger().info("VoidPortal disabled.");
    }

    private String message(String text) {
        String prefix = getConfig().getString(
                "messages.prefix",
                "&5&lVoidPortal &8» &r"
        );

        return ChatColor.translateAlternateColorCodes('&', prefix + text);
    }

    private void savePortals() {
        try {
            portalsConfig.save(portalsFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save portals.yml!");
            exception.printStackTrace();
        }
    }

    private Location getPortalLocation(String name) {
        return portalsConfig.getLocation("portals." + name + ".location");
    }

    private Location getPortalDestination(String name) {
        return portalsConfig.getLocation("portals." + name + ".destination");
    }

    private void startPortalAnimation() {
        new BukkitRunnable() {
            @Override
            public void run() {
                animationTick += 2L;

                if (!getConfig().getBoolean("settings.particle-effect", true)) {
                    return;
                }

                if (portalsConfig.getConfigurationSection("portals") == null) {
                    return;
                }

                double radius = getConfig().getDouble("portal.radius", 2.0);
                int red = getConfig().getInt("portal.red", 170);
                int green = getConfig().getInt("portal.green", 0);
                int blue = getConfig().getInt("portal.blue", 255);

                Color color = Color.fromRGB(
                        Math.max(0, Math.min(255, red)),
                        Math.max(0, Math.min(255, green)),
                        Math.max(0, Math.min(255, blue))
                );

                Particle.DustOptions dust =
                        new Particle.DustOptions(color, 1.5F);

                for (String name : portalsConfig
                        .getConfigurationSection("portals")
                        .getKeys(false)) {

                    Location portal = getPortalLocation(name);

                    if (portal == null || portal.getWorld() == null) {
                        continue;
                    }

                    World world = portal.getWorld();

                    // Outer glowing ring
                    for (int i = 0; i < 36; i++) {
                        double angle = (Math.PI * 2.0 * i / 36.0)
                                + animationTick * 0.025;

                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;

                        Location ringPoint = portal.clone().add(
                                x,
                                0.12 + Math.sin(animationTick * 0.08 + angle) * 0.04,
                                z
                        );

                        world.spawnParticle(
                                Particle.DUST,
                                ringPoint,
                                1,
                                0,
                                0,
                                0,
                                0,
                                dust
                        );
                    }

                    // Animated swirling vortex
                    for (int i = 0; i < 24; i++) {
                        double progress = i / 24.0;

                        double angle = progress * Math.PI * 2.0
                                + animationTick * 0.07;

                        double swirlRadius = radius * 0.65;

                        double x = Math.cos(angle) * swirlRadius;
                        double z = Math.sin(angle) * swirlRadius;

                        double y = 0.25
                                + progress * 2.0
                                + Math.sin(animationTick * 0.08 + angle) * 0.15;

                        Location swirlPoint = portal.clone().add(x, y, z);

                        world.spawnParticle(
                                Particle.PORTAL,
                                swirlPoint,
                                2,
                                0.08,
                                0.08,
                                0.08,
                                0.02
                        );

                        world.spawnParticle(
                                Particle.DUST,
                                swirlPoint,
                                1,
                                0,
                                0,
                                0,
                                0,
                                dust
                        );
                    }

                    // Magical aura above the portal
                    Location aura = portal.clone().add(0, 1.0, 0);

                    world.spawnParticle(
                            Particle.REVERSE_PORTAL,
                            aura,
                            8,
                            0.5,
                            0.8,
                            0.5,
                            0.02
                    );
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private void startPortalDetection() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (portalsConfig.getConfigurationSection("portals") == null) {
                    return;
                }

                double radius = getConfig().getDouble("portal.radius", 2.0);

                for (String name : portalsConfig
                        .getConfigurationSection("portals")
                        .getKeys(false)) {

                    Location portal = getPortalLocation(name);
                    Location destination = getPortalDestination(name);

                    if (portal == null
                            || destination == null
                            || portal.getWorld() == null
                            || destination.getWorld() == null) {
                        continue;
                    }

                    for (Player player : portal.getWorld().getPlayers()) {
                        if (player.getLocation().distanceSquared(portal)
                                <= radius * radius) {

                            beginTeleport(player, name, destination);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void beginTeleport(
            Player player,
            String portalName,
            Location destination
    ) {
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        long cooldownUntil = teleportCooldown.getOrDefault(uuid, 0L);

        if (now < cooldownUntil) {
            return;
        }

        teleportCooldown.put(uuid, now + 5000L);

        int delay = Math.max(
                0,
                getConfig().getInt("settings.teleport-delay", 3)
        );

        Location startingLocation = player.getLocation().clone();

        player.sendMessage(message(
                "&dTeleporting in &f" + delay + " &dseconds..."
        ));

        if (getConfig().getBoolean("settings.sound-effect", true)) {
            player.playSound(
                    player.getLocation(),
                    Sound.BLOCK_PORTAL_TRIGGER,
                    1.0F,
                    0.7F
            );
        }

        new BukkitRunnable() {
            int secondsLeft = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                if (getConfig().getBoolean("settings.cancel-on-move", true)
                        && player.getLocation().distanceSquared(startingLocation)
                        > 0.25) {

                    player.sendMessage(message(
                            "&cTeleport cancelled because you moved!"
                    ));

                    cancel();
                    return;
                }

                if (secondsLeft <= 0) {
                    player.teleport(destination);

                    player.sendMessage(message(
                            "&aYou have been teleported!"
                    ));

                    if (getConfig().getBoolean("settings.sound-effect", true)) {
                        player.playSound(
                                player.getLocation(),
                                Sound.ENTITY_ENDERMAN_TELEPORT,
                                1.0F,
                                0.7F
                        );
                    }

                    cancel();
                    return;
                }

                secondsLeft--;
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!sender.hasPermission("voidportal.admin")) {
            sender.sendMessage(message("&cYou don't have permission!"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(message("&dVoidPortal Commands"));
            player.sendMessage(message("&f/portal create <name>"));
            player.sendMessage(message("&f/portal remove <name>"));
            player.sendMessage(message("&f/portal setdestination <name>"));
            player.sendMessage(message("&f/portal list"));
            player.sendMessage(message("&f/portal reload"));
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(message("&c/portal create <name>"));
                    return true;
                }

                String name = args[1].toLowerCase();

                portalsConfig.set(
                        "portals." + name + ".location",
                        player.getLocation()
                );

                savePortals();

                player.sendMessage(message(
                        "&aPortal created: &f" + name
                ));
            }

            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(message("&c/portal remove <name>"));
                    return true;
                }

                String name = args[1].toLowerCase();

                if (!portalsConfig.contains("portals." + name)) {
                    player.sendMessage(message("&cPortal not found!"));
                    return true;
                }

                portalsConfig.set("portals." + name, null);
                savePortals();

                player.sendMessage(message(
                        "&cPortal removed: &f" + name
                ));
            }

            case "setdestination" -> {
                if (args.length < 2) {
                    player.sendMessage(message(
                            "&c/portal setdestination <name>"
                    ));
                    return true;
                }

                String name = args[1].toLowerCase();

                if (!portalsConfig.contains("portals." + name)) {
                    player.sendMessage(message(
                            "&cPortal not found! Create it first."
                    ));
                    return true;
                }

                portalsConfig.set(
                        "portals." + name + ".destination",
                        player.getLocation()
                );

                savePortals();

                player.sendMessage(message(
                        "&aDestination set for: &f" + name
                ));
            }

            case "list" -> {
                if (portalsConfig.getConfigurationSection("portals") == null) {
                    player.sendMessage(message("&cNo portals created."));
                    return true;
                }

                player.sendMessage(message("&dSaved portals:"));

                for (String name : portalsConfig
                        .getConfigurationSection("portals")
                        .getKeys(false)) {

                    player.sendMessage(message("&f- " + name));
                }
            }

            case "reload" -> {
                reloadConfig();
                portalsConfig = YamlConfiguration.loadConfiguration(portalsFile);

                player.sendMessage(message(
                        "&aVoidPortal configuration reloaded!"
                ));
            }

            default -> player.sendMessage(message(
                    "&cUnknown command. Use /portal"
            ));
        }

        return true;
    }
}
