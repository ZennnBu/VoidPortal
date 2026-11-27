
package me.voidportal;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class VoidPortal extends JavaPlugin
        implements CommandExecutor, Listener, TabCompleter {

    private File portalsFile;
    private YamlConfiguration portalsConfig;
    private NamespacedKey selectorKey;

    private final Map<UUID, Long> teleportCooldown = new HashMap<>();
    private final Set<UUID> teleporting = new HashSet<>();
    private final Map<UUID, String> selectionPortal = new HashMap<>();
    private final Map<UUID, Location> firstCorner = new HashMap<>();

    private long animationTick = 0L;

    private static final List<String> COMMANDS = Arrays.asList(
            "create",
            "link",
            "size",
            "innersize",
            "remove",
            "setdestination",
            "list",
            "reload",
            "spawn",
            "glow",
            "holo"
    );

    private static final List<String> COLORS = Arrays.asList(
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white"
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();

        selectorKey = new NamespacedKey(this, "portal_selector");
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
            getCommand("portal").setTabCompleter(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        startPortalEffects();
        startPortalDetection();

        getLogger().info("VoidPortal enabled!");
        getLogger().info("Author: AimVortex");
    }

    @Override
    public void onDisable() {
        savePortals();

        teleportCooldown.clear();
        teleporting.clear();
        selectionPortal.clear();
        firstCorner.clear();
    }

    private String message(String text) {
        String prefix = getConfig().getString(
                "messages.prefix",
                "&5&lVoidPortal &8» &r"
        );

        return ChatColor.translateAlternateColorCodes(
                '&',
                prefix + text
        );
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
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

    private String getGlowColor(String name) {
        return portalsConfig.getString(
                portalPath(name) + ".glow-color",
                "dark_purple"
        );
    }

    private boolean isSelector(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(selectorKey, PersistentDataType.BYTE);
    }

    private ItemStack createSelector() {
        ItemStack axe = new ItemStack(
                org.bukkit.Material.WOODEN_AXE
        );

        ItemMeta meta = axe.getItemMeta();

        meta.setDisplayName(
                ChatColor.LIGHT_PURPLE
                        + ""
                        + ChatColor.BOLD
                        + "Void Portal Selector"
        );

        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Left-click: First corner",
                ChatColor.GRAY + "Right-click: Second corner",
                ChatColor.DARK_PURPLE + "AimVortex"
        ));

        meta.getPersistentDataContainer().set(
                selectorKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        axe.setItemMeta(meta);

        return axe;
    }

    @EventHandler
    public void onSelectorUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();

        if (!isSelector(event.getItem())) {
            return;
        }

        Action action = event.getAction();

        if (action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        event.setCancelled(true);

        if (!player.hasPermission("voidportal.admin")) {
            player.sendMessage(message(
                    "&cYou don't have permission!"
            ));
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Location clicked = event.getClickedBlock().getLocation();

        String portalName = selectionPortal.get(
                player.getUniqueId()
        );

        if (portalName == null) {
            player.sendMessage(message(
                    "&cFirst use &f/vportal <name> pset"
            ));
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {
            firstCorner.put(
                    player.getUniqueId(),
                    clicked.clone()
            );

            player.sendMessage(message(
                    "&aFirst corner selected!"
            ));

            player.sendMessage(message(
                    "&eNow right-click the opposite corner."
            ));

            return;
        }

        Location first = firstCorner.get(
                player.getUniqueId()
        );

        if (first == null) {
            player.sendMessage(message(
                    "&cSelect the first corner with left-click first!"
            ));
            return;
        }

        if (first.getWorld() == null
                || !first.getWorld().equals(clicked.getWorld())) {
            player.sendMessage(message(
                    "&cBoth corners must be in the same world!"
            ));
            return;
        }

        Location second = clicked.clone();

        portalsConfig.set(
                portalPath(portalName) + ".pos1",
                first
        );

        portalsConfig.set(
                portalPath(portalName) + ".pos2",
                second
        );

        Location center = new Location(
                first.getWorld(),
                (first.getBlockX() + second.getBlockX()) / 2.0 + 0.5,
                (first.getBlockY() + second.getBlockY()) / 2.0 + 0.5,
                (first.getBlockZ() + second.getBlockZ()) / 2.0 + 0.5
        );

        portalsConfig.set(
                portalPath(portalName) + ".location",
                center
        );

        savePortals();

        selectionPortal.remove(player.getUniqueId());
        firstCorner.remove(player.getUniqueId());

        player.sendMessage(message(
                "&dPortal area saved for &f" + portalName
        ));

        player.sendMessage(message(
                "&7Use &f/portal link "
                        + portalName
                        + " <destination> &7to connect it."
        ));

        PortalEffect.spawnActivation(center, 5.0);

        if (getConfig().getBoolean(
                "settings.sound-effect",
                true
        )) {
            player.playSound(
                    center,
                    Sound.BLOCK_PORTAL_AMBIENT,
                    1.0F,
                    0.8F
            );
        }
    }

    private boolean isInsidePortal(
            Player player,
            String name,
            Location portal
    ) {
        Location pos1 = portalsConfig.getLocation(
                portalPath(name) + ".pos1"
        );

        Location pos2 = portalsConfig.getLocation(
                portalPath(name) + ".pos2"
        );

        Location playerLocation = player.getLocation();

        if (pos1 != null && pos2 != null
                && pos1.getWorld() != null
                && pos1.getWorld().equals(pos2.getWorld())
                && pos1.getWorld().equals(playerLocation.getWorld())) {

            int minX = Math.min(
                    pos1.getBlockX(),
                    pos2.getBlockX()
            );

            int maxX = Math.max(
                    pos1.getBlockX(),
                    pos2.getBlockX()
            );

            int minY = Math.min(
                    pos1.getBlockY(),
                    pos2.getBlockY()
            );

            int maxY = Math.max(
                    pos1.getBlockY(),
                    pos2.getBlockY()
            );

            int minZ = Math.min(
                    pos1.getBlockZ(),
                    pos2.getBlockZ()
            );

            int maxZ = Math.max(
                    pos1.getBlockZ(),
                    pos2.getBlockZ()
            );

            return playerLocation.getBlockX() >= minX
                    && playerLocation.getBlockX() <= maxX
                    && playerLocation.getBlockY() >= minY
                    && playerLocation.getBlockY() <= maxY
                    && playerLocation.getBlockZ() >= minZ
                    && playerLocation.getBlockZ() <= maxZ;
        }

        double activationRadius = getConfig().getDouble(
                "portal.activation-radius",
                3.5
        );

        return playerLocation.distanceSquared(portal)
                <= activationRadius * activationRadius;
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
                            ),
                            getGlowColor(name)
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
                        if (isInsidePortal(player, name, portal)) {
                            beginTeleport(player, destination, portal);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void beginTeleport(
            Player player,
            Location destination,
            Location portal
    ) {
        UUID uuid = player.getUniqueId();

        if (teleporting.contains(uuid)) {
            return;
        }

        long now = System.currentTimeMillis();

        long cooldownUntil = teleportCooldown.getOrDefault(
                uuid,
                0L
        );

        if (now < cooldownUntil) {
            return;
        }

        teleporting.add(uuid);

        int delay = Math.max(
                0,
                getConfig().getInt(
                        "settings.teleport-delay",
                        3
                )
        );

        Location startLocation = player.getLocation().clone();

        PortalEffect.spawnActivation(portal, 3.0);

        if (getConfig().getBoolean(
                "settings.sound-effect",
                true
        )) {
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
                    teleporting.remove(uuid);
                    cancel();
                    return;
                }

                if (getConfig().getBoolean(
                        "settings.cancel-on-move",
                        true
                ) && player.getLocation().distanceSquared(
                        startLocation
                ) > 0.25) {

                    player.sendMessage(message(
                            "&cTeleport cancelled because you moved!"
                    ));

                    teleporting.remove(uuid);
                    cancel();
                    return;
                }

                if (secondsLeft <= 0) {
                    player.teleport(destination.clone());

                    player.sendMessage(message(
                            "&dWelcome to the other side of the Void!"
                    ));

                    teleportCooldown.put(
                            uuid,
                            System.currentTimeMillis() + 5000L
                    );

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

                    teleporting.remove(uuid);
                    cancel();
                    return;
                }

                player.sendActionBar(color(
                        "&a&lTeleporting in "
                                + secondsLeft
                                + " Seconds"
                ));

                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!sender.hasPermission("voidportal.admin")) {
            sender.sendMessage(message(
                    "&cYou don't have permission!"
            ));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    "This command can only be used in-game."
            );
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        if (args.length >= 2
                && args[1].equalsIgnoreCase("pset")) {

            String name = args[0].toLowerCase(Locale.ROOT);

            if (!name.matches("[a-z0-9_-]+")) {
                player.sendMessage(message(
                        "&cPortal name can only use letters, numbers, _ and -."
                ));
                return true;
            }

            if (!portalExists(name)) {
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
            }

            selectionPortal.put(
                    player.getUniqueId(),
                    name
            );

            firstCorner.remove(player.getUniqueId());

            player.getInventory().addItem(createSelector());

            player.sendMessage(message(
                    "&dVoid Portal Selector received!"
            ));

            player.sendMessage(message(
                    "&eLeft-click the first corner."
            ));

            player.sendMessage(message(
                    "&eRight-click the opposite corner."
            ));

            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(message(
                            "&c/portal create <name>"
                    ));
                    return true;
                }

                String name = args[1].toLowerCase(Locale.ROOT);

                if (!name.matches("[a-z0-9_-]+")) {
                    player.sendMessage(message(
                            "&cInvalid portal name!"
                    ));
                    return true;
                }

                if (portalExists(name)) {
                    player.sendMessage(message(
                            "&cThat portal already exists!"
                    ));
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

                if (!portalExists(first)
                        || !portalExists(second)) {
                    player.sendMessage(message(
                            "&cBoth portals must exist first!"
                    ));
                    return true;
                }

                Location firstLocation = getPortalLocation(first);
                Location secondLocation = getPortalLocation(second);

                if (firstLocation == null
                        || secondLocation == null) {
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
                    player.sendMessage(message(
                            "&cPortal not found!"
                    ));
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

                if (getInnerRadius(name) >= radius) {
                    portalsConfig.set(
                            portalPath(name) + ".inner-radius",
                            Math.max(0.5, radius - 0.5)
                    );
                }

                savePortals();

                player.sendMessage(message(
                        "&aOuter radius updated!"
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
                    player.sendMessage(message(
                            "&cPortal not found!"
                    ));
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

                if (radius < 0.5
                        || radius >= getOuterRadius(name)) {
                    player.sendMessage(message(
                            "&cInner radius must be smaller than outer radius!"
                    ));
                    return true;
                }

                portalsConfig.set(
                        portalPath(name) + ".inner-radius",
                        radius
                );

                savePortals();

                player.sendMessage(message(
                        "&aInner radius updated!"
                ));
            }

            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(message(
                            "&c/portal remove <name>"
                    ));
                    return true;
                }

                String name = args[1].toLowerCase(Locale.ROOT);

                if (!portalExists(name)) {
                    player.sendMessage(message(
                            "&cPortal not found!"
                    ));
                    return true;
                }

                portalsConfig.set(
                        portalPath(name),
                        null
                );

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
                    player.sendMessage(message(
                            "&cPortal not found!"
                    ));
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
                if (portalsConfig.getConfigurationSection(
                        "portals"
                ) == null) {
                    player.sendMessage(message(
                            "&cNo portals created."
                    ));
                    return true;
                }

                player.sendMessage(message(
                        "&dSaved portals:"
                ));

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

                portalsConfig = YamlConfiguration.loadConfiguration(
                        portalsFile
                );

                player.sendMessage(message(
                        "&aVoidPortal configuration reloaded!"
                ));
            }

            case "spawn" -> {
                if (args.length >= 3
                        && args[1].equalsIgnoreCase("glow")) {

                    String selectedPortal = selectionPortal.get(
                            player.getUniqueId()
                    );

                    if (selectedPortal == null
                            || !portalExists(selectedPortal)) {
                        player.sendMessage(message(
                                "&cFirst select a portal using &f/vportal <name> pset"
                        ));
                        return true;
                    }

                    if (args.length >= 5
                            && args[2].equalsIgnoreCase("add")
                            && args[3].equalsIgnoreCase("color")) {

                        String glowColor = args[4].toLowerCase(
                                Locale.ROOT
                        );

                        if (!COLORS.contains(glowColor)) {
                            player.sendMessage(message(
                                    "&cInvalid color! Available colors: &f"
                                            + String.join(", ", COLORS)
                            ));
                            return true;
                        }

                        portalsConfig.set(
                                portalPath(selectedPortal)
                                        + ".glow-color",
                                glowColor
                        );

                        savePortals();

                        player.sendMessage(message(
                                "&aGlow color set to &f"
                                        + glowColor
                                        + " &afor portal &f"
                                        + selectedPortal
                        ));

                        return true;
                    }

                    if (args.length >= 3
                            && args[2].equalsIgnoreCase("remove")) {

                        portalsConfig.set(
                                portalPath(selectedPortal)
                                        + ".glow-color",
                                null
                        );

                        savePortals();

                        player.sendMessage(message(
                                "&aGlow color reset to default for &f"
                                        + selectedPortal
                        ));

                        return true;
                    }

                    player.sendMessage(message(
                            "&e/vportal spawn glow add color <color>"
                    ));

                    player.sendMessage(message(
                            "&e/vportal spawn glow remove"
                    ));

                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(message(
                            "&c/vportal spawn <name> pset"
                    ));
                    return true;
                }

                String name = args[1].toLowerCase(Locale.ROOT);

                if (args.length >= 3
                        && args[2].equalsIgnoreCase("pset")) {
                    player.performCommand(
                            "vportal " + name + " pset"
                    );
                    return true;
                }

                sendHelp(player);
            }

            case "glow" -> {
                player.sendMessage(message(
                        "&eUse: &f/vportal spawn glow add color <color>"
                ));
            }

            case "holo" -> {
                player.sendMessage(message(
                        "&eHologram commands will be added in the next step."
                ));
            }

            default -> sendHelp(player);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (!sender.hasPermission("voidportal.admin")) {
            return Collections.emptyList();
        }

        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.addAll(COMMANDS);

            if (portalsConfig.getConfigurationSection(
                    "portals"
            ) != null) {
                suggestions.addAll(
                        portalsConfig.getConfigurationSection(
                                "portals"
                        ).getKeys(false)
                );
            }
        } else if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);

            if (first.equals("link")) {
                suggestions.addAll(getPortalNames());
            } else if (first.equals("size")
                    || first.equals("innersize")
                    || first.equals("remove")
                    || first.equals("setdestination")) {
                suggestions.addAll(getPortalNames());
            } else if (first.equals("spawn")) {
                suggestions.add("glow");
                suggestions.add("holo");
            }
        } else if (args.length == 3
                && args[0].equalsIgnoreCase("spawn")
                && args[1].equalsIgnoreCase("glow")) {
            suggestions.add("add");
            suggestions.add("remove");
        } else if (args.length == 4
                && args[0].equalsIgnoreCase("spawn")
                && args[1].equalsIgnoreCase("glow")
                && args[2].equalsIgnoreCase("add")) {
            suggestions.add("color");
        } else if (args.length == 5
                && args[0].equalsIgnoreCase("spawn")
                && args[1].equalsIgnoreCase("glow")
                && args[2].equalsIgnoreCase("add")
                && args[3].equalsIgnoreCase("color")) {
            suggestions.addAll(COLORS);
        }

        String input = args[args.length - 1].toLowerCase(
                Locale.ROOT
        );

        suggestions.removeIf(value ->
                !value.toLowerCase(Locale.ROOT).startsWith(input)
        );

        return suggestions;
    }

    private List<String> getPortalNames() {
        if (portalsConfig.getConfigurationSection(
                "portals"
        ) == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(
                portalsConfig.getConfigurationSection(
                        "portals"
                ).getKeys(false)
        );
    }

    private void sendHelp(Player player) {
        player.sendMessage(message(
                "&dVoidPortal Commands"
        ));

        player.sendMessage(message(
                "&f/vportal <name> pset"
        ));

        player.sendMessage(message(
                "&f/portal create <name>"
        ));

        player.sendMessage(message(
                "&f/portal link <portal1> <portal2>"
        ));

        player.sendMessage(message(
                "&f/portal size <name> <radius>"
        ));

        player.sendMessage(message(
                "&f/portal innersize <name> <radius>"
        ));

        player.sendMessage(message(
                "&f/portal remove <name>"
        ));

        player.sendMessage(message(
                "&f/portal setdestination <name>"
        ));

        player.sendMessage(message(
                "&f/portal list"
        ));

        player.sendMessage(message(
                "&f/portal reload"
        ));

        player.sendMessage(message(
                "&f/vportal spawn glow add color <color>"
        ));
    }
}
