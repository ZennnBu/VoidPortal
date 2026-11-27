
package me.voidportal;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
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
import java.util.Locale;
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

        startPortalEffects();
        startPortalDetection();

        getLogger().info("VoidPortal enabled!");
        getLogger().info("Author: AimVortex");
    }

    @Override
    public void onDisable() {
        savePortals();
        teleportCooldown.clear();
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

    private String portalPath(String name) {
        return "portals." + name;
    }

    private boolean portalExists(String name) {
        return portalsConfig.contains(portalPath(name));
    }

    private Location getPortalLocation(String name) {
        return portalsConfig.getLocation(
                portalPath(name) + ".location"
        );
    }

    private Location getPortalDestination(String name) {
        return portalsConfig.getLocation(
                portalPath(name) + ".destination"
        );
    }

    private double getOuterRadius(String name) {
        return portalsConfig.getDouble(
                portalPath(name) + ".outer-radius",
                16.0
        );
    }

    private double getInnerRadius(String name) {
        return portalsConfig.getDouble(
                portalPath(name) + ".inner-radius",
                2.5
        );
    }

    private void startPortalEffects() {
        new BukkitRunnable() {
            @Override
            public void run() {
                animationTick += 5L;

                if (!getConfig().getBoolean(
                        "settings.particle-effect",
                        true
                )) {
                    return;
                }

                if (portalsConfig.getConfigurationSection("portals") == null) {
                    return;
                }

                for (String name : portalsConfig
                        .getConfigurationSection("portals")
                        .getKeys(false)) {

                    Location portal = getPortalLocation(name);

                    if (portal == null || portal.getWorld() == null) {
                        continue;
                    }

                    PortalEffect.spawn(
                            portal,
                            animationTick,
                            getOuterRadius(name),
                            getInnerRadius(name),
                            getConfig().getInt(
                                    "portal.particle-count",
                                    180
                            )
                    );
                }
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    private void startPortalDetection() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (portalsConfig.getConfigurationSection("portals") == null) {
                    return;
                }

                double activationRadius = getConfig().getDouble(
                        "portal.activation-radius",
                        3.5
                );

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
                                <= activationRadius * activationRadius) {

                            beginTeleport(player, destination);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void beginTeleport(Player player, Location destination) {
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        long cooldownUntil = teleportCooldown.getOrDefault(uuid, 0L);

        if (now < cooldownUntil) {
            return;
        }

        teleportCooldown.put(uuid, now + 6000L);

        int delay = Math.max(
                0,
                getConfig().getInt("settings.teleport-delay", 3)
        );

        Location startLocation = player.getLocation().clone();

        player.sendMessage(message(
                "&dEntering VoidPortal in &f" + delay + " &dseconds..."
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

                if (getConfig().getBoolean(
                        "settings.cancel-on-move",
                        true
                ) && player.getLocation().distanceSquared(startLocation) > 0.25) {

                    player.sendMessage(message(
                            "&cTeleport cancelled because you moved!"
                    ));

                    cancel();
                    return;
                }

                if (secondsLeft <= 0) {
                    player.teleport(destination.clone());

                    player.sendMessage(message(
                            "&dWelcome to the other side of the Void!"
                    ));

                    if (getConfig().getBoolean(
                            "settings.sound-effect",
                            true
                    )) {
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
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(message("&c/portal create <name>"));
                    return true;
                }

                String name = args[1].toLowerCase(Locale.ROOT);

                if (portalExists(name)) {
                    player.sendMessage(message("&cThat portal already exists!"));
                    return true;
                }

                portalsConfig.set(
                        portalPath(name) + ".location",
                        player.getLocation()
                );

                portalsConfig.set(
                        portalPath(name) + ".outer-radius",
                        16.0
                );

                portalsConfig.set(
                        portalPath(name) + ".inner-radius",
                        2.5
                );

                savePortals();

                player.sendMessage(message(
                        "&aPortal created: &f" + name
                ));
            }

            case "link" -> {
                if (args.length < 3) {
                    player.sendMessage(message(
                            "&c/portal link <portal1> <portal2>"
                    ));
                    return true;
                }

                String first = args[1].toLowerCase(Locale.ROOT);
                String second = args[2].toLowerCase(Locale.ROOT);

                if (first.equals(second)) {
                    player.sendMessage(message(
                            "&cYou cannot link a portal to itself!"
                    ));
                    return true;
                }

                if (!portalExists(first) || !portalExists(second)) {
                    player.sendMessage(message(
                            "&cBoth portals must exist first!"
                    ));
                    return true;
                }

                Location firstLocation = getPortalLocation(first);
                Location secondLocation = getPortalLocation(second);

                if (firstLocation == null || secondLocation == null) {
                    player.sendMessage(message(
                            "&cPortal location is missing!"
                    ));
                    return true;
                }

                portalsConfig.set(
                        portalPath(first) + ".destination",
                        secondLocation
                );

                portalsConfig.set(
                        portalPath(second) + ".destination",
                        firstLocation
                );

                savePortals();

                player.sendMessage(message(
                        "&dPortals &f" + first
                                + " &dand &f" + second
                                + " &dare now connected!"
                ));
            }

            case "size" -> {
                if (args.length < 3) {
                    player.sendMessage(message(
                            "&c/portal size <name> <radius>"
                    ));
                    return true;
                }

                String name = args[1].toLowerCase(Locale.ROOT);

                if (!portalExists(name)) {
                    player.sendMessage(message("&cPortal not found!"));
                    return true;
                }

                double radius;

                try {
                    radius = Double.parseDouble(args[2]);
                } catch (NumberFormatException exception) {
                    player.sendMessage(message(
                            "&cRadius must be a number!"
                    ));
                    return true;
                }

                if (radius < 3.0 || radius > 64.0) {
                    player.sendMessage(message(
                            "&cOuter radius must be between 3 and 64 blocks!"
                    ));
                    return true;
                }

                portalsConfig.set(
                        portalPath(name) + ".outer-radius",
                        radius
                );

                double innerRadius = getInnerRadius(name);

                if (innerRadius >= radius) {
                    portalsConfig.set(
                            portalPath(name) + ".inner-radius",
                            Math.max(0.5, radius - 0.5)
                    );
                }

                savePortals();

                player.sendMessage(message(
                        "&aOuter ring size for &f" + name
                                + " &ais now &f" + radius
                                + " &ablocks."
                ));
            }

            case "innersize" -> {
                if (args.length < 3) {
                    player.sendMessage(message(
                            "&c/portal innersize <name> <radius>"
                    ));
                    return true;
                }

                String name = args[1].toLowerCase(Locale.ROOT);

                if (!portalExists(name)) {
                    player.sendMessage(message("&cPortal not found!"));
                    return true;
                }

                double radius;

                try {
                    radius = Double.parseDouble(args[2]);
                } catch (NumberFormatException exception) {
                    player.sendMessage(message(
                            "&cRadius must be a number!"
                    ));
                    return true;
                }

                double outerRadius = getOuterRadius(name);

                if (radius < 0.5 || radius >= outerRadius) {
                    player.sendMessage(message(
                            "&cInner radius must be at least 0.5 and smaller than the outer ring!"
                    ));
                    return true;
                }

                portalsConfig.set(
                        portalPath(name) + ".inner-radius",
                        radius
                );

                savePortals();

                player.sendMessage(message(
                        "&aInner ring size for &f" + name
                                + " &ais now &f" + radius
                                + " &ablocks."
                ));
            }

            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(message("&c/portal remove <name>"));
                    return true;
                }

                String name = args[1].toLowerCase(Locale.ROOT);

                if (!portalExists(name)) {
                    player.sendMessage(message("&cPortal not found!"));
                    return true;
                }

                portalsConfig.set(portalPath(name), null);
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

                String name = args[1].toLowerCase(Locale.ROOT);

                if (!portalExists(name)) {
                    player.sendMessage(message("&cPortal not found!"));
                    return true;
                }

                portalsConfig.set(
                        portalPath(name) + ".destination",
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

                    player.sendMessage(message(
                            "&f- " + name
                                    + " &7(outer: "
                                    + getOuterRadius(name)
                                    + ", inner: "
                                    + getInnerRadius(name)
                                    + ")"
                    ));
                }
            }

            case "reload" -> {
                reloadConfig();
                portalsConfig = YamlConfiguration.loadConfiguration(portalsFile);

                player.sendMessage(message(
                        "&aVoidPortal configuration reloaded!"
                ));
            }

            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(message("&dVoidPortal Commands"));
        player.sendMessage(message("&f/portal create <name>"));
        player.sendMessage(message("&f/portal link <portal1> <portal2>"));
        player.sendMessage(message("&f/portal size <name> <radius>"));
        player.sendMessage(message("&f/portal innersize <name> <radius>"));
        player.sendMessage(message("&f/portal remove <name>"));
        player.sendMessage(message("&f/portal setdestination <name>"));
        player.sendMessage(message("&f/portal list"));
        player.sendMessage(message("&f/portal reload"));
    }
}
