
package me.voidportal;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
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

        getCommand("portal").setExecutor(this);

        startPortalEffects();

        getLogger().info("VoidPortal enabled! Author: AimVortex");
    }

    @Override
    public void onDisable() {
        savePortals();
        getLogger().info("VoidPortal disabled.");
    }

    private void savePortals() {
        try {
            portalsConfig.save(portalsFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save portals.yml!");
            exception.printStackTrace();
        }
    }

    private String color(String message) {
        String prefix = getConfig().getString("messages.prefix", "&5&lVoidPortal &8» &r");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    private Location getPortalLocation(String name) {
        return portalsConfig.getLocation("portals." + name + ".location");
    }

    private Location getDestination(String name) {
        return portalsConfig.getLocation("portals." + name + ".destination");
    }

    private void startPortalEffects() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!getConfig().getBoolean("settings.particle-effect", true)) {
                    return;
                }

                double radius = getConfig().getDouble("portal.radius", 2.0);
                int count = getConfig().getInt("portal.particle-count", 50);

                int red = getConfig().getInt("portal.red", 170);
                int green = getConfig().getInt("portal.green", 0);
                int blue = getConfig().getInt("portal.blue", 255);

                Color portalColor = Color.fromRGB(red, green, blue);
                Particle.DustOptions dust = new Particle.DustOptions(portalColor, 1.5F);

                for (String name : portalsConfig.getConfigurationSection("portals") == null
                        ? new java.util.HashSet<String>()
                        : portalsConfig.getConfigurationSection("portals").getKeys(false)) {

                    Location location = getPortalLocation(name);

                    if (location == null || location.getWorld() == null) {
                        continue;
                    }

                    World world = location.getWorld();

                    for (int i = 0; i < count; i++) {
                        double angle = 2.0 * Math.PI * i / count;

                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;

                        Location particleLocation = location.clone().add(x, 0.15, z);

                        world.spawnParticle(
                                Particle.DUST,
                                particleLocation,
                                1,
                                0,
                                0,
                                0,
                                0,
                                dust
                        );
                    }

                    Location destination = getDestination(name);

                    if (destination == null || destination.getWorld() == null) {
                        continue;
                    }

                    for (Player player : world.getPlayers()) {
                        if (player.getLocation().distanceSquared(location) <= radius * radius) {
                            tryTeleport(player, name, destination);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void tryTeleport(Player player, String portalName, Location destination) {
        UUID uuid = player.getUniqueId();

        long currentTime = System.currentTimeMillis();
        long cooldown = teleportCooldown.getOrDefault(uuid, 0L);

        if (currentTime < cooldown) {
            return;
        }

        teleportCooldown.put(uuid, currentTime + 5000L);

        int delay = getConfig().getInt("settings.teleport-delay", 3);

        player.sendMessage(color("&dTeleporting in &f" + delay + " &dseconds..."));

        Location startLocation = player.getLocation().clone();

        new BukkitRunnable() {
            int secondsLeft = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                if (getConfig().getBoolean("settings.cancel-on-move", true)
                        && player.getLocation().distanceSquared(startLocation) > 0.25) {

                    player.sendMessage(color("&cTeleport cancelled because you moved!"));
                    cancel();
                    return;
                }

                if (secondsLeft <= 0) {
                    player.teleport(destination);
                    player.sendMessage(color("&aYou have been teleported!"));

                    if (getConfig().getBoolean("settings.sound-effect", true)) {
                        player.playSound(
                                player.getLocation(),
                                org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT,
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
            sender.sendMessage(color("&cYou don't have permission!"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(color("&eCommands:"));
            player.sendMessage(color("&f/portal create <name>"));
            player.sendMessage(color("&f/portal remove <name>"));
            player.sendMessage(color("&f/portal setdestination <name>"));
            player.sendMessage(color("&f/portal list"));
            player.sendMessage(color("&f/portal reload"));
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(color("&cUsage: /portal create <name>"));
                    return true;
                }

                String name = args[1].toLowerCase();

                portalsConfig.set("portals." + name + ".location", player.getLocation());
                savePortals();

                player.sendMessage(color("&aPortal created: &f" + name));
            }

            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(color("&cUsage: /portal remove <name>"));
                    return true;
                }

                String name = args[1].toLowerCase();

                if (!portalsConfig.contains("portals." + name)) {
                    player.sendMessage(color("&cPortal not found!"));
                    return true;
                }

                portalsConfig.set("portals." + name, null);
                savePortals();

                player.sendMessage(color("&cPortal removed: &f" + name));
            }

            case "setdestination" -> {
                if (args.length < 2) {
                    player.sendMessage(color("&cUsage: /portal setdestination <name>"));
                    return true;
                }

                String name = args[1].toLowerCase();

                if (!portalsConfig.contains("portals." + name)) {
                    player.sendMessage(color("&cPortal not found! Create it first."));
                    return true;
                }

                portalsConfig.set("portals." + name + ".destination", player.getLocation());
                savePortals();

                player.sendMessage(color("&aDestination set for: &f" + name));
            }

            case "list" -> {
                if (portalsConfig.getConfigurationSection("portals") == null) {
                    player.sendMessage(color("&cNo portals created."));
                    return true;
                }

                player.sendMessage(color("&dPortals:"));

                for (String name : portalsConfig.getConfigurationSection("portals").getKeys(false)) {
                    player.sendMessage(color("&f- " + name));
                }
            }

            case "reload" -> {
                reloadConfig();
                portalsConfig = YamlConfiguration.loadConfiguration(portalsFile);
                player.sendMessage(color("&aVoidPortal configuration reloaded!"));
            }

            default -> player.sendMessage(color("&cUnknown command. Use /portal"));
        }

        return true;
    }
                          }
