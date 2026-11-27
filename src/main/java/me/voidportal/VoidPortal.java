
package me.voidportal;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
    private NamespacedKey hologramKey;

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, String> selecting = new HashMap<>();
    private final Map<UUID, Location> firstCorners = new HashMap<>();
    private final Set<UUID> teleporting = new HashSet<>();

    private long animationTick = 0L;

    private static final List<String> COMMANDS = Arrays.asList(
            "create", "link", "size", "innersize", "remove",
            "setdestination", "list", "reload", "spawn", "glow", "holo"
    );

    private static final List<String> COLORS = Arrays.asList(
            "black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray", "dark_gray",
            "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white"
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();

        selectorKey = new NamespacedKey(this, "portal_selector");
        hologramKey = new NamespacedKey(this, "voidportal_hologram");

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

        loadHolograms();
        startPortalEffects();
        startPortalDetection();

        getLogger().info("VoidPortal enabled!");
        getLogger().info("Author: AimVortex");
    }

    @Override
    public void onDisable() {
        savePortals();
        cooldowns.clear();
        selecting.clear();
        firstCorners.clear();
        teleporting.clear();
    }

    private String message(String text) {
        String prefix = getConfig().getString(
                "messages.prefix", "&5&lVoidPortal &8» &r"
        );
        return color(prefix + text);
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

    private String path(String name) {
        return "portals." + name;
    }

    private boolean exists(String name) {
        return portalsConfig.contains(path(name));
    }

    private Location portalLocation(String name) {
        return portalsConfig.getLocation(path(name) + ".location");
    }

    private Location destination(String name) {
        return portalsConfig.getLocation(path(name) + ".destination");
    }

    private double outerRadius(String name) {
        return portalsConfig.getDouble(path(name) + ".outer-radius", 16.0);
    }

    private double innerRadius(String name) {
        return portalsConfig.getDouble(path(name) + ".inner-radius", 2.5);
    }

    private String glowColor(String name) {
        return portalsConfig.getString(path(name) + ".glow-color", "dark_purple");
    }

    private List<String> portalNames() {
        ConfigurationSection section = portalsConfig.getConfigurationSection("portals");
        if (section == null) return Collections.emptyList();
        return new ArrayList<>(section.getKeys(false));
    }

    private boolean validName(String name) {
        return name != null && name.matches("[a-z0-9_-]+");
    }

    private boolean isSelector(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(selectorKey, PersistentDataType.BYTE);
    }

    private ItemStack createSelector() {
        ItemStack axe = new ItemStack(org.bukkit.Material.WOODEN_AXE);
        ItemMeta meta = axe.getItemMeta();

        meta.setDisplayName(color("&d&lVoid Portal Selector"));
        meta.setLore(Arrays.asList(
                color("&7Left-click: First corner"),
                color("&7Right-click: Second corner"),
                color("&5AimVortex")
        ));
        meta.getPersistentDataContainer().set(
                selectorKey, PersistentDataType.BYTE, (byte) 1
        );

        axe.setItemMeta(meta);
        return axe;
    }

    @EventHandler
    public void onSelectorUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!isSelector(event.getItem())) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("voidportal.admin")) {
            player.sendMessage(message("&cYou don't have permission!"));
            return;
        }

        if (event.getClickedBlock() == null) return;

        String name = selecting.get(player.getUniqueId());
        if (name == null) {
            player.sendMessage(message("&cFirst use &f/vportal <name> pset"));
            return;
        }

        Location clicked = event.getClickedBlock().getLocation();

        if (action == Action.LEFT_CLICK_BLOCK) {
            firstCorners.put(player.getUniqueId(), clicked.clone());
            player.sendMessage(message("&aFirst corner selected."));
            player.sendMessage(message("&eNow right-click the opposite corner."));
            return;
        }

        Location first = firstCorners.get(player.getUniqueId());
        if (first == null) {
            player.sendMessage(message("&cLeft-click the first corner first."));
            return;
        }

        if (first.getWorld() == null || clicked.getWorld() == null
                || !first.getWorld().equals(clicked.getWorld())) {
            player.sendMessage(message("&cBoth corners must be in the same world."));
            return;
        }

        Location center = new Location(
                first.getWorld(),
                (first.getBlockX() + clicked.getBlockX()) / 2.0 + 0.5,
                (first.getBlockY() + clicked.getBlockY()) / 2.0 + 0.5,
                (first.getBlockZ() + clicked.getBlockZ()) / 2.0 + 0.5
        );

        portalsConfig.set(path(name) + ".pos1", first);
        portalsConfig.set(path(name) + ".pos2", clicked.clone());
        portalsConfig.set(path(name) + ".location", center);
        savePortals();

        selecting.remove(player.getUniqueId());
        firstCorners.remove(player.getUniqueId());

        player.sendMessage(message("&aPortal area saved for &f" + name));
        player.sendMessage(message("&7Use &f/portal setdestination " + name
                + " &7at the destination."));
        PortalEffect.spawnActivation(center, 5.0);
    }

    private boolean insidePortal(Player player, String name, Location portal) {
        Location first = portalsConfig.getLocation(path(name) + ".pos1");
        Location second = portalsConfig.getLocation(path(name) + ".pos2");
        Location here = player.getLocation();

        if (first != null && second != null
                && first.getWorld() != null
                && first.getWorld().equals(second.getWorld())
                && first.getWorld().equals(here.getWorld())) {

            int minX = Math.min(first.getBlockX(), second.getBlockX());
            int maxX = Math.max(first.getBlockX(), second.getBlockX());
            int minY = Math.min(first.getBlockY(), second.getBlockY());
            int maxY = Math.max(first.getBlockY(), second.getBlockY());
            int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
            int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

            return here.getBlockX() >= minX && here.getBlockX() <= maxX
                    && here.getBlockY() >= minY && here.getBlockY() <= maxY
                    && here.getBlockZ() >= minZ && here.getBlockZ() <= maxZ;
        }

        double radius = getConfig().getDouble("portal.activation-radius", 3.5);
        return here.distanceSquared(portal) <= radius * radius;
    }

    private void startPortalEffects() {
        new BukkitRunnable() {
            @Override
            public void run() {
                animationTick += 5L;

                if (!getConfig().getBoolean("settings.particle-effect", true)) return;

                for (String name : portalNames()) {
                    Location location = portalLocation(name);
                    if (location == null || location.getWorld() == null) continue;

                    PortalEffect.spawn(
                            location,
                            animationTick,
                            outerRadius(name),
                            innerRadius(name),
                            getConfig().getInt("portal.particle-count", 180),
                            glowColor(name)
                    );
                }
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    private void startPortalDetection() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (String name : portalNames()) {
                    Location portal = portalLocation(name);
                    Location target = destination(name);

                    if (portal == null || target == null
                            || portal.getWorld() == null
                            || target.getWorld() == null) continue;

                    for (Player player : portal.getWorld().getPlayers()) {
                        if (insidePortal(player, name, portal)) {
                            beginTeleport(player, target, portal);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    private void beginTeleport(Player player, Location target, Location portal) {
        UUID uuid = player.getUniqueId();
        if (teleporting.contains(uuid)) return;

        long until = cooldowns.getOrDefault(uuid, 0L);
        if (System.currentTimeMillis() < until) return;

        teleporting.add(uuid);

        int delay = Math.max(0, getConfig().getInt("settings.teleport-delay", 3));
        Location start = player.getLocation().clone();

        PortalEffect.spawnActivation(portal, 3.0);

        if (getConfig().getBoolean("settings.sound-effect", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 1F, 0.7F);
        }

        new BukkitRunnable() {
            int seconds = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    teleporting.remove(uuid);
                    cancel();
                    return;
                }

                if (getConfig().getBoolean("settings.cancel-on-move", true)
                        && player.getLocation().distanceSquared(start) > 0.25) {
                    player.sendMessage(message("&cTeleport cancelled because you moved!"));
                    teleporting.remove(uuid);
                    cancel();
                    return;
                }

                if (seconds <= 0) {
                    player.teleport(target.clone());
                    player.sendMessage(message("&dWelcome to the other side of the Void!"));
                    cooldowns.put(uuid, System.currentTimeMillis() + 5000L);

                    if (getConfig().getBoolean("settings.sound-effect", true)) {
                        player.playSound(player.getLocation(),
                                Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 0.7F);
                    }

                    teleporting.remove(uuid);
                    cancel();
                    return;
                }

                player.sendActionBar(color("&d&lTeleporting in &f&l" + seconds));
                seconds--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void removePluginHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer()
                        .has(hologramKey, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private void loadHolograms() {
        removePluginHolograms();

        ConfigurationSection section = portalsConfig.getConfigurationSection("holograms");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            Location location = section.getLocation(id + ".location");
            String text = section.getString(id + ".text", "");
            if (location != null && location.getWorld() != null) {
                createHologram(location, text);
            }
        }
    }

    private void createHologram(Location location, String text) {
        if (location == null || location.getWorld() == null) return;

        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
        display.setText(color(text));
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setDefaultBackground(false);
        display.setPersistent(true);
        display.getPersistentDataContainer().set(
                hologramKey, PersistentDataType.BYTE, (byte) 1
        );
    }

    private void removeHologramConfig() {
        portalsConfig.set("holograms", null);
        savePortals();
        removePluginHolograms();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
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

        if (args.length >= 2 && args[1].equalsIgnoreCase("pset")) {
            String name = args[0].toLowerCase(Locale.ROOT);
            if (!validName(name)) {
                player.sendMessage(message("&cInvalid portal name."));
                return true;
            }

            if (!exists(name)) {
                portalsConfig.set(path(name) + ".location", player.getLocation());
                portalsConfig.set(path(name) + ".outer-radius", 16.0);
                portalsConfig.set(path(name) + ".inner-radius", 2.5);
                savePortals();
            }

            selecting.put(player.getUniqueId(), name);
            firstCorners.remove(player.getUniqueId());
            player.getInventory().addItem(createSelector());

            player.sendMessage(message("&dVoid Portal Selector received."));
            player.sendMessage(message("&eLeft-click first corner, right-click second."));
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
                if (!validName(name)) {
                    player.sendMessage(message("&cInvalid portal name."));
                    return true;
                }
                if (exists(name)) {
                    player.sendMessage(message("&cPortal already exists."));
                    return true;
                }

                portalsConfig.set(path(name) + ".location", player.getLocation());
                portalsConfig.set(path(name) + ".outer-radius", 16.0);
                portalsConfig.set(path(name) + ".inner-radius", 2.5);
                savePortals();
                player.sendMessage(message("&aPortal created: &f" + name));
            }

            case "link" -> {
                if (args.length < 3) {
                    player.sendMessage(message("&c/portal link <portal1> <portal2>"));
                    return true;
                }

                String first = args[1].toLowerCase(Locale.ROOT);
                String second = args[2].toLowerCase(Locale.ROOT);

                if (first.equals(second) || !exists(first) || !exists(second)) {
                    player.sendMessage(message("&cChoose two different existing portals."));
                    return true;
                }

                Location a = portalLocation(first);
                Location b = portalLocation(second);
                if (a == null || b == null) {
                    player.sendMessage(message("&cPortal location is missing."));
                    return true;
                }

                portalsConfig.set(path(first) + ".destination", b);
                portalsConfig.set(path(second) + ".destination", a);
                savePortals();
                player.sendMessage(message("&aPortals connected: &f" + first + " &7↔ &f" + second));
            }

            case "size", "innersize" -> {
                if (args.length < 3) {
                    player.sendMessage(message("&c/portal " + action + " <name> <radius>"));
                    return true;
                }

                String name = args[1].toLowerCase(Locale.ROOT);
                if (!exists(name)) {
                    player.sendMessage(message("&cPortal not found."));
                    return true;
                }

                double radius;
                try {
                    radius = Double.parseDouble(args[2]);
                } catch (NumberFormatException exception) {
                    player.sendMessage(message("&cRadius must be a number."));
                    return true;
                }

                if (action.equals("size")) {
                    if (radius < 3 || radius > 64) {
                        player.sendMessage(message("&cRadius must be between 3 and 64."));
                        return true;
                    }
                    portalsConfig.set(path(name) + ".outer-radius", radius);
                    if (innerRadius(name) >= radius) {
                        portalsConfig.set(path(name) + ".inner-radius", radius - 0.5);
                    }
                } else {
                    if (radius < 0.5 || radius >= outerRadius(name)) {
                        player.sendMessage(message("&cInner radius must be smaller than outer radius."));
                        return true;
                    }
                    portalsConfig.set(path(name) + ".inner-radius", radius);
                }

                savePortals();
                player.sendMessage(message("&aPortal radius updated."));
            }

            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(message("&c/portal remove <name>"));
                    return true;
                }

                String name = args[1].toLowerCase(Locale.ROOT);
                if (!exists(name)) {
                    player.sendMessage(message("&cPortal not found."));
                    return true;
                }

                portalsConfig.set(path(name), null);
                savePortals();
                player.sendMessage(message("&cPortal removed: &f" + name));
            }

            case "setdestination" -> {
                if (args.length < 2) {
                    player.sendMessage(message("&c/portal setdestination <name>"));
                    return true;
                }

                String name = args[1].toLowerCase(Locale.ROOT);
                if (!exists(name)) {
                    player.sendMessage(message("&cPortal not found."));
                    return true;
                }

                portalsConfig.set(path(name) + ".destination", player.getLocation());
                savePortals();
                player.sendMessage(message("&aDestination set for &f" + name));
            }

            case "list" -> {
                if (portalNames().isEmpty()) {
                    player.sendMessage(message("&cNo portals created."));
                    return true;
                }

                player.sendMessage(message("&dSaved portals:"));
                for (String name : portalNames()) {
                    player.sendMessage(message("&f- " + name
                            + " &7(outer " + outerRadius(name)
                            + ", inner " + innerRadius(name) + ")"));
                }
            }

            case "reload" -> {
                reloadConfig();
                portalsConfig = YamlConfiguration.loadConfiguration(portalsFile);
                loadHolograms();
                player.sendMessage(message("&aVoidPortal configuration reloaded."));
            }

            case "spawn" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("glow")) {
                    String selected = selecting.get(player.getUniqueId());
                    if (selected == null || !exists(selected)) {
                        player.sendMessage(message("&cFirst use &f/vportal <name> pset"));
                        return true;
                    }

                    if (args.length >= 5 && args[2].equalsIgnoreCase("add")
                            && args[3].equalsIgnoreCase("color")) {
                        String selectedColor = args[4].toLowerCase(Locale.ROOT);
                        if (!COLORS.contains(selectedColor)) {
                            player.sendMessage(message("&cAvailable colors: &f"
                                    + String.join(", ", COLORS)));
                            return true;
                        }

                        portalsConfig.set(path(selected) + ".glow-color", selectedColor);
                        savePortals();
                        player.sendMessage(message("&aGlow color set to &f" + selectedColor));
                        return true;
                    }

                    if (args.length >= 3 && args[2].equalsIgnoreCase("remove")) {
                        portalsConfig.set(path(selected) + ".glow-color", null);
                        savePortals();
                        player.sendMessage(message("&aGlow color reset."));
                        return true;
                    }

                    player.sendMessage(message("&e/vportal spawn glow add color <color>"));
                    player.sendMessage(message("&e/vportal spawn glow remove"));
                    return true;
                }

                if (args.length >= 3 && args[1].equalsIgnoreCase("holo")) {
                    if (args[2].equalsIgnoreCase("set")) {
                        if (args.length < 4) {
                            player.sendMessage(message("&c/vportal spawn holo set <text>"));
                            return true;
                        }

                        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                        removeHologramConfig();
                        Location holoLocation = player.getLocation().clone().add(0, 2.2, 0);

                        portalsConfig.set("holograms.main.location", holoLocation);
                        portalsConfig.set("holograms.main.text", text);
                        savePortals();
                        createHologram(holoLocation, text);

                        player.sendMessage(message("&aHologram created."));
                        return true;
                    }

                    if (args[2].equalsIgnoreCase("remove")) {
                        removeHologramConfig();
                        player.sendMessage(message("&aHologram removed."));
                        return true;
                    }

                    player.sendMessage(message("&e/vportal spawn holo set <text>"));
                    player.sendMessage(message("&e/vportal spawn holo remove"));
                    return true;
                }

                if (args.length >= 3 && args[2].equalsIgnoreCase("pset")) {
                    player.performCommand("vportal " + args[1] + " pset");
                    return true;
                }

                player.sendMessage(message("&c/vportal spawn <name> pset"));
                player.sendMessage(message("&c/vportal spawn holo set <text>"));
            }

            case "glow" -> player.sendMessage(message("&eUse /vportal spawn glow add color <color>"));

            case "holo" -> {
                player.sendMessage(message("&e/vportal spawn holo set <text>"));
                player.sendMessage(message("&e/vportal spawn holo remove"));
            }

            default -> sendHelp(player);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("voidportal.admin")) return Collections.emptyList();

        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.addAll(COMMANDS);
            suggestions.addAll(portalNames());
        } else if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("link") || first.equals("size")
                    || first.equals("innersize") || first.equals("remove")
                    || first.equals("setdestination")) {
                suggestions.addAll(portalNames());
            } else if (first.equals("spawn")) {
                suggestions.addAll(Arrays.asList("glow", "holo"));
                suggestions.addAll(portalNames());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            if (args[1].equalsIgnoreCase("glow")) {
                suggestions.addAll(Arrays.asList("add", "remove"));
            } else if (args[1].equalsIgnoreCase("holo")) {
                suggestions.addAll(Arrays.asList("set", "remove"));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("spawn")
                && args[1].equalsIgnoreCase("glow")
                && args[2].equalsIgnoreCase("add")) {
            suggestions.add("color");
        } else if (args.length == 5 && args[0].equalsIgnoreCase("spawn")
                && args[1].equalsIgnoreCase("glow")
                && args[2].equalsIgnoreCase("add")
                && args[3].equalsIgnoreCase("color")) {
            suggestions.addAll(COLORS);
        }

        String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        suggestions.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(input));
        return suggestions;
    }

    private void sendHelp(Player player) {
        player.sendMessage(message("&d&lVoidPortal Commands"));
        player.sendMessage(message("&f/vportal <name> pset"));
        player.sendMessage(message("&f/portal create <name>"));
        player.sendMessage(message("&f/portal link <portal1> <portal2>"));
        player.sendMessage(message("&f/portal size <name> <radius>"));
        player.sendMessage(message("&f/portal innersize <name> <radius>"));
        player.sendMessage(message("&f/portal setdestination <name>"));
        player.sendMessage(message("&f/portal remove <name>"));
        player.sendMessage(message("&f/portal list"));
        player.sendMessage(message("&f/portal reload"));
        player.sendMessage(message("&f/vportal spawn glow add color <color>"));
        player.sendMessage(message("&f/vportal spawn holo set <text>"));
        player.sendMessage(message("&f/vportal spawn holo remove"));
    }
}
