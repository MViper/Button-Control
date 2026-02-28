package viper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Note;
import org.bukkit.Note.Tone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ButtonControl extends JavaPlugin {
    private ConfigManager configManager;
    private DataManager dataManager;

    // Bewegungsmelder-State
    private final Map<String, Long> lastMotionDetections = new HashMap<>();
    private final Set<String> activeSensors = new HashSet<>();

    // Zeitgesteuerte Automation – verhindert mehrfaches Auslösen pro Zustandswechsel
    private final Map<String, Boolean> timedControllerLastState = new HashMap<>();

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        dataManager   = new DataManager(this);

        // Update-Checker beim Start
        new UpdateChecker(this, 127702).getVersion(version -> {
            String current = getDescription().getVersion();
            if (isNewerVersion(strip(version), strip(current))) {
                getLogger().info("Update verfügbar: v" + version);
                Bukkit.getScheduler().runTask(this, () ->
                    Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("buttoncontrol.update"))
                        .forEach(p -> sendUpdateMessage(p, current, version)));
            } else {
                getLogger().info("ButtonControl ist auf dem neuesten Stand (v" + current + ").");
            }
        });

        // Update beim Joinen
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                Player player = event.getPlayer();
                if (!player.hasPermission("buttoncontrol.update")) return;
                new UpdateChecker(ButtonControl.this, 127702).getVersion(version -> {
                    String current = getDescription().getVersion();
                    if (isNewerVersion(strip(version), strip(current)))
                        sendUpdateMessage(player, current, version);
                });
            }
        }, this);

        if (getCommand("bc") != null) {
            getCommand("bc").setExecutor(this);
            getCommand("bc").setTabCompleter(new ButtonTabCompleter());
        }

        getServer().getPluginManager().registerEvents(
            new ButtonListener(this, configManager, dataManager), this);

        registerRecipes();
        MetricsHandler.startMetrics(this);

        getServer().getScheduler().runTaskTimer(this, this::checkDaylightSensors,  0L, 20L * 10);
        getServer().getScheduler().runTaskTimer(this, this::checkMotionSensors,    0L, 10L);
        getServer().getScheduler().runTaskTimer(this, this::checkTimedControllers, 0L, 20L * 5);

        getLogger().info("ButtonControl v" + getDescription().getVersion() + " wurde erfolgreich aktiviert!");
    }

    // -----------------------------------------------------------------------
    //  Update-Hilfe
    // -----------------------------------------------------------------------

    private String strip(String v) {
        return v.replaceFirst("(?i)^(version\\s*|v\\.?\\s*)", "").trim();
    }

    private void sendUpdateMessage(Player player, String current, String latest) {
        player.sendMessage("");
        player.sendMessage("§8§m-----------------------------------------");
        player.sendMessage("   §6§lButtonControl §7- §e§lUpdate verfügbar!");
        player.sendMessage("");
        player.sendMessage("   §7Aktuelle Version: §c" + current);
        player.sendMessage("   §7Neue Version:     §a" + latest);
        player.sendMessage("");
        player.sendMessage("   §eDownload: §bhttps://www.spigotmc.org/resources/127702/");
        player.sendMessage("§8§m-----------------------------------------");
        player.sendMessage("");
    }

    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] lp = latest.split("\\.");
            String[] cp = current.split("\\.");
            int len = Math.max(lp.length, cp.length);
            for (int i = 0; i < len; i++) {
                int l = i < lp.length ? Integer.parseInt(lp[i]) : 0;
                int c = i < cp.length ? Integer.parseInt(cp[i]) : 0;
                if (l > c) return true;
                if (l < c) return false;
            }
            return false;
        } catch (NumberFormatException e) {
            return !latest.equalsIgnoreCase(current);
        }
    }

    // -----------------------------------------------------------------------
    //  Rezepte
    // -----------------------------------------------------------------------

    private void registerRecipes() {
        // Alle Holz/Stein-Buttons
        for (Material mat : Material.values()) {
            if (!mat.name().endsWith("_BUTTON")) continue;
            registerColumnRecipe(
                "control_" + mat.name().toLowerCase(), mat,
                "§6Steuer-" + friendlyName(mat, "_BUTTON"),
                Arrays.asList("§7Ein universeller Controller.",
                              "§7Verbindet Türen, Lampen und mehr.")
            );
        }

        // Tageslichtsensor
        registerColumnRecipe("control_daylight", Material.DAYLIGHT_DETECTOR,
            "§6Steuer-Tageslichtsensor",
            Arrays.asList("§7Öffnet/schließt nach Tageszeit."));

        // Notenblock
        registerColumnRecipe("control_noteblock", Material.NOTE_BLOCK,
            "§6Steuer-Notenblock",
            Arrays.asList("§7Spielt einen Klingelton ab."));

        // Bewegungsmelder
        registerColumnRecipe("control_motion", Material.TRIPWIRE_HOOK,
            "§6Steuer-Bewegungsmelder",
            Arrays.asList("§7Erkennt Spieler und Mobs in der Nähe."));

        // NEU: Schild-Controller
        registerColumnRecipe("control_sign", Material.OAK_SIGN,
            "§6Steuer-Schild",
            Arrays.asList("§7Wandmontierbarer Controller.",
                          "§7Funktioniert wie ein Button."));

        // NEU: Teppich-Sensoren (alle 16 Farben) – NUR Spieler
        for (Material mat : Material.values()) {
            if (!mat.name().endsWith("_CARPET")) continue;
            registerColumnRecipe(
                "control_carpet_" + mat.name().toLowerCase(), mat,
                "§6Steuer-Teppich §8(" + friendlyName(mat, "_CARPET") + "§8)",
                Arrays.asList("§7Erkennt NUR Spieler (keine Mobs).",
                              "§7Bodenbasierter Bewegungsmelder.")
            );
        }
    }

    private void registerColumnRecipe(String keyName, Material mat, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(new ArrayList<>(lore));
            item.setItemMeta(meta);
        }
        NamespacedKey key = new NamespacedKey(this, keyName);
        if (Bukkit.getRecipe(key) != null) Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, item);
        recipe.shape(" X ", " X ", " X ");
        recipe.setIngredient('X', mat);
        Bukkit.addRecipe(recipe);
    }

    /** IRON_DOOR → "§7Iron Door"  |  OAK_BUTTON → "§7Oak Button" */
    String friendlyName(Material mat, String stripSuffix) {
        String[] parts = mat.name().replace(stripSuffix, "").split("_");
        StringBuilder sb = new StringBuilder("§7");
        for (String p : parts) {
            if (sb.length() > 2) sb.append(" ");
            sb.append(Character.toUpperCase(p.charAt(0)))
              .append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    //  Tageslichtsensor
    // -----------------------------------------------------------------------

    public void checkDaylightSensors() {
        for (String loc : dataManager.getAllPlacedControllers()) {
            String buttonId = dataManager.getButtonIdForPlacedController(loc);
            if (buttonId == null) continue;
            Location location = parseLocation(loc);
            if (location == null) continue;
            if (location.getBlock().getType() != Material.DAYLIGHT_DETECTOR) continue;

            long time  = location.getWorld().getTime();
            boolean isDay = time >= 0 && time < 13000;
            List<String> connected = dataManager.getConnectedBlocks(buttonId);
            if (connected == null) continue;

            for (String ts : connected) {
                Location tl = parseLocation(ts);
                if (tl == null) continue;
                Block tb = tl.getBlock();
                if (tb.getType() == Material.REDSTONE_LAMP) {
                    Lightable lamp = (Lightable) tb.getBlockData();
                    lamp.setLit(!isDay);
                    tb.setBlockData(lamp);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Zeitgesteuerte Automation (NEU)
    // -----------------------------------------------------------------------

    /**
     * Prüft alle 5 Sekunden ob ein Zeitplan (open-time / close-time) für einen Controller
     * aktiv ist und öffnet/schließt die verbundenen Blöcke bei Wechsel.
     *
     * Ingame-Zeit: 0 = Sonnenaufgang, 6000 = Mittag, 13000 = Sonnenuntergang, 18000 = Mitternacht
     * Anzeige: ticksToTime() wandelt in "HH:MM" um (Tag beginnt um 06:00).
     */
    public void checkTimedControllers() {
        for (String controllerLoc : dataManager.getAllPlacedControllers()) {
            String buttonId = dataManager.getButtonIdForPlacedController(controllerLoc);
            if (buttonId == null) continue;

            long openTime  = dataManager.getScheduleOpenTime(buttonId);
            long closeTime = dataManager.getScheduleCloseTime(buttonId);
            if (openTime < 0 || closeTime < 0) continue;

            Location loc = parseLocation(controllerLoc);
            if (loc == null) continue;

            long worldTime   = loc.getWorld().getTime() % 24000;
            boolean shouldBeOpen;

            if (openTime <= closeTime) {
                // Normales Intervall: z.B. öffnen 6000, schließen 18000
                shouldBeOpen = worldTime >= openTime && worldTime < closeTime;
            } else {
                // Über Mitternacht: z.B. öffnen 20000, schließen 4000
                shouldBeOpen = worldTime >= openTime || worldTime < closeTime;
            }

            Boolean lastState = timedControllerLastState.get(controllerLoc);
            if (lastState != null && lastState == shouldBeOpen) continue;

            timedControllerLastState.put(controllerLoc, shouldBeOpen);
            List<String> connected = dataManager.getConnectedBlocks(buttonId);
            if (connected != null && !connected.isEmpty()) {
                setOpenables(connected, shouldBeOpen);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Bewegungsmelder
    // -----------------------------------------------------------------------

    public void checkMotionSensors() {
        long now = System.currentTimeMillis();

        for (String controllerLoc : dataManager.getAllPlacedControllers()) {
            Location loc = parseLocation(controllerLoc);
            if (loc == null) continue;
            Material bType = loc.getBlock().getType();

            boolean isTripwire = bType == Material.TRIPWIRE_HOOK;
            boolean isCarpet   = bType.name().endsWith("_CARPET");
            if (!isTripwire && !isCarpet) continue;

            String buttonId = dataManager.getButtonIdForPlacedController(controllerLoc);
            if (buttonId == null) continue;

            double radius = dataManager.getMotionSensorRadius(controllerLoc);
            if (radius == -1) radius = configManager.getConfig().getDouble("motion-detection-radius", 5.0);
            long delay = dataManager.getMotionSensorDelay(controllerLoc);
            if (delay == -1) delay = configManager.getConfig().getLong("motion-close-delay-ms", 5000L);

            final double r = radius;
            boolean detected;
            if (isCarpet) {
                // NEU: Teppich erkennt NUR Spieler
                detected = !loc.getWorld()
                    .getNearbyEntities(loc, r, r, r, e -> e instanceof Player).isEmpty();
            } else {
                // Tripwire: alle lebenden Entitäten
                detected = !loc.getWorld()
                    .getNearbyEntities(loc, r, r, r,
                        e -> e instanceof org.bukkit.entity.LivingEntity).isEmpty();
            }

            List<String> connected = dataManager.getConnectedBlocks(buttonId);
            if (connected == null || connected.isEmpty()) continue;

            if (detected) {
                if (!activeSensors.contains(controllerLoc)) {
                    setOpenables(connected, true);
                    activeSensors.add(controllerLoc);
                }
                lastMotionDetections.put(controllerLoc, now);
            } else {
                Long last = lastMotionDetections.get(controllerLoc);
                if (last != null && now - last >= delay) {
                    setOpenables(connected, false);
                    lastMotionDetections.remove(controllerLoc);
                    activeSensors.remove(controllerLoc);
                }
            }
        }
    }

    void setOpenables(List<String> connectedBlocks, boolean open) {
        for (String locStr : connectedBlocks) {
            Location tl = parseLocation(locStr);
            if (tl == null) continue;
            Block tb = tl.getBlock();
            if (tb.getBlockData() instanceof org.bukkit.block.data.Openable) {
                org.bukkit.block.data.Openable o = (org.bukkit.block.data.Openable) tb.getBlockData();
                o.setOpen(open);
                tb.setBlockData(o);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Befehle
    // -----------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("bc")) return false;

        if (args.length == 0) {
            sender.sendMessage("§6[BC] §7/bc <info|reload|note|list|rename|schedule|trust|untrust|public|private>");
            return true;
        }

        String sub = args[0].toLowerCase();

        // INFO
        if (sub.equals("info")) {
            sender.sendMessage("§6§lButtonControl §7v" + getDescription().getVersion() + " §8by §7M_Viper");
            sender.sendMessage("§7Features: §fTüren · Lampen · Notenblöcke · Sensoren · Teppiche · Schilder · Zeitpläne");
            sender.sendMessage("§7Controller aktiv: §f" + dataManager.getAllPlacedControllers().size());
            return true;
        }

        // RELOAD
        if (sub.equals("reload")) {
            if (!sender.hasPermission("buttoncontrol.reload")) {
                sender.sendMessage(configManager.getMessage("keine-berechtigung")); return true;
            }
            configManager.reloadConfig();
            dataManager.reloadData();
            timedControllerLastState.clear();
            sender.sendMessage(configManager.getMessage("konfiguration-neugeladen"));
            return true;
        }

        // NOTE
        if (sub.equals("note") && sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length < 2) { player.sendMessage("§7/bc note <Instrument>"); return true; }
            try {
                org.bukkit.Instrument.valueOf(args[1].toUpperCase());
                dataManager.setPlayerInstrument(player.getUniqueId(), args[1].toUpperCase());
                player.sendMessage(String.format(configManager.getMessage("instrument-gesetzt"), args[1].toUpperCase()));
            } catch (Exception e) {
                player.sendMessage(configManager.getMessage("ungueltiges-instrument"));
            }
            return true;
        }

        // Alle folgenden Befehle erfordern Spieler + angescauten Controller
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cNur Spieler können diesen Befehl verwenden.");
            return true;
        }
        Player player = (Player) sender;

        if (sub.equals("list") || sub.equals("rename") || sub.equals("schedule")
                || sub.equals("trust") || sub.equals("untrust")
                || sub.equals("public") || sub.equals("private")) {

            Block target = player.getTargetBlockExact(5);
            if (!isValidController(target)) {
                player.sendMessage(configManager.getMessage("kein-controller-im-blick")); return true;
            }
            String targetLoc = toLoc(target);
            String buttonId  = dataManager.getButtonIdForLocation(targetLoc);
            if (buttonId == null) {
                player.sendMessage(configManager.getMessage("keine-bloecke-verbunden")); return true;
            }
            boolean isAdmin = player.hasPermission("buttoncontrol.admin");
            boolean isOwner = dataManager.isOwner(buttonId, player.getUniqueId());

            switch (sub) {
                case "list":
                    if (!dataManager.canAccess(buttonId, player.getUniqueId()) && !isAdmin) {
                        player.sendMessage(configManager.getMessage("keine-berechtigung-controller")); return true;
                    }
                    sendListInfo(player, buttonId);
                    break;

                case "rename":
                    if (!isOwner && !isAdmin) { player.sendMessage(configManager.getMessage("nur-besitzer-abbauen")); return true; }
                    if (args.length < 2) { player.sendMessage("§7/bc rename <Name>"); return true; }
                    String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    if (newName.length() > 32) { player.sendMessage("§cName zu lang (max. 32 Zeichen)."); return true; }
                    dataManager.setControllerName(buttonId, newName);
                    player.sendMessage(String.format(configManager.getMessage("controller-umbenannt"), newName));
                    break;

                case "schedule":
                    if (!isOwner && !isAdmin) { player.sendMessage(configManager.getMessage("nur-besitzer-abbauen")); return true; }
                    new ScheduleGUI(this, player, buttonId).open();
                    break;

                case "trust":
                    if (!isOwner && !isAdmin) { player.sendMessage(configManager.getMessage("nur-besitzer-abbauen")); return true; }
                    if (args.length < 2) { player.sendMessage("§7/bc trust <Spieler>"); return true; }
                    org.bukkit.OfflinePlayer tp = Bukkit.getOfflinePlayer(args[1]);
                    if (!tp.hasPlayedBefore() && !tp.isOnline()) {
                        player.sendMessage(configManager.getMessage("spieler-nicht-gefunden")); return true;
                    }
                    dataManager.addTrustedPlayer(buttonId, tp.getUniqueId());
                    player.sendMessage(String.format(configManager.getMessage("trust-hinzugefuegt"), args[1]));
                    break;

                case "untrust":
                    if (!isOwner && !isAdmin) { player.sendMessage(configManager.getMessage("nur-besitzer-abbauen")); return true; }
                    if (args.length < 2) { player.sendMessage("§7/bc untrust <Spieler>"); return true; }
                    dataManager.removeTrustedPlayer(buttonId, Bukkit.getOfflinePlayer(args[1]).getUniqueId());
                    player.sendMessage(String.format(configManager.getMessage("trust-entfernt"), args[1]));
                    break;

                default: // public / private
                    if (!isOwner && !isAdmin) { player.sendMessage(configManager.getMessage("nur-besitzer-abbauen")); return true; }
                    boolean pub = sub.equals("public");
                    dataManager.setPublic(buttonId, pub);
                    player.sendMessage(String.format(configManager.getMessage("status-geandert"),
                        pub ? "§aÖffentlich" : "§cPrivat"));
                    break;
            }
        }
        return true;
    }

    private void sendListInfo(Player player, String buttonId) {
        String name = dataManager.getControllerName(buttonId);
        String header = name != null
            ? "§6§l" + name
            : "§6§lController §8§o(ID: " + buttonId.substring(0, 8) + "...)";
        player.sendMessage(header);

        List<String> connected = dataManager.getConnectedBlocks(buttonId);
        if (connected == null || connected.isEmpty()) {
            player.sendMessage("  §cKeine Blöcke verbunden.");
        } else {
            player.sendMessage("§7Verbundene Blöcke §8(" + connected.size() + ")§7:");
            for (int i = 0; i < connected.size(); i++) {
                String ls = connected.get(i);
                Location l = parseLocation(ls);
                String typeLabel = l != null ? "§e" + l.getBlock().getType().name() : "§8unbekannt";
                String[] p = ls.split(",");
                String coords = p.length == 4
                    ? "§8(" + p[1] + "§7, §8" + p[2] + "§7, §8" + p[3] + " §7in §f" + p[0] + "§8)" : "";
                player.sendMessage("  §8" + (i + 1) + ". " + typeLabel + " " + coords);
            }
        }

        player.sendMessage("§7Status: " + (dataManager.isPublic(buttonId) ? "§aÖffentlich" : "§cPrivat"));

        long openT  = dataManager.getScheduleOpenTime(buttonId);
        long closeT = dataManager.getScheduleCloseTime(buttonId);
        if (openT >= 0 && closeT >= 0) {
            player.sendMessage("§7Zeitplan: §aÖffnet §7um §e" + ticksToTime(openT)
                + " §7· §cSchließt §7um §e" + ticksToTime(closeT));
        } else {
            player.sendMessage("§7Zeitplan: §8Nicht gesetzt §7(§e/bc schedule§7)");
        }
    }

    // -----------------------------------------------------------------------
    //  Utility
    // -----------------------------------------------------------------------

    /** Wandelt Minecraft-Ticks (0–23999) in "HH:MM" um. Ingame-Tag startet um 06:00. */
    public String ticksToTime(long ticks) {
        long shifted = (ticks + 6000) % 24000;
        long hours   = shifted / 1000;
        long minutes = (shifted % 1000) * 60 / 1000;
        return String.format("%02d:%02d", hours, minutes);
    }

    /** Wandelt "HH:MM" zurück in Minecraft-Ticks */
    public long timeToTicks(int hours, int minutes) {
        long totalMinutes = hours * 60L + minutes;
        long ticks = (totalMinutes * 1000L / 60L - 6000 + 24000) % 24000;
        return ticks;
    }

    public boolean isValidController(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        return m.name().endsWith("_BUTTON")
            || m == Material.DAYLIGHT_DETECTOR
            || m == Material.TRIPWIRE_HOOK
            || m.name().endsWith("_SIGN")
            || m.name().endsWith("_CARPET");
    }

    public String toLoc(Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    public Location parseLocation(String locStr) {
        String[] parts = locStr.split(",");
        if (parts.length != 4) return null;
        World world = getServer().getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world,
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) { return null; }
    }

    public void playDoorbellSound(Location loc, String instrument) {
        Block block = loc.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) return;
        NoteBlock noteBlock = (NoteBlock) block.getBlockData();
        try {
            org.bukkit.Instrument inst = org.bukkit.Instrument.valueOf(instrument.toUpperCase());
            noteBlock.setInstrument(inst);
            noteBlock.setNote(new Note(0, Tone.C, false));
            block.setBlockData(noteBlock);
            loc.getWorld().playSound(loc, inst.getSound(), 1.0f, 1.0f);
            if (configManager.getConfig().getBoolean("double-note-enabled", true)) {
                long delayTicks = (long)(configManager.getConfig().getInt("double-note-delay-ms", 1000) / 50.0);
                getServer().getScheduler().runTaskLater(this, () -> {
                    if (block.getType() == Material.NOTE_BLOCK)
                        loc.getWorld().playSound(loc, inst.getSound(), 1.0f, 1.0f);
                }, delayTicks);
            }
        } catch (IllegalArgumentException e) {
            getLogger().warning("Ungültiges Instrument: " + instrument);
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DataManager   getDataManager()   { return dataManager; }
}