package viper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ButtonControl extends JavaPlugin {
    private static final String TIMED_CONTAINER_MODE_SIMULTANEOUS = "simultaneous";
    private static final String TIMED_CONTAINER_MODE_SEQUENTIAL = "sequential";

    private ConfigManager configManager;
    private DataManager dataManager;

    // Bewegungsmelder-State
    private final Map<String, Long> lastMotionDetections = new HashMap<>();
    private final Set<String> activeSensors = new HashSet<>();

    // Zeitgesteuerte Automation – verhindert mehrfaches Auslösen pro Zustandswechsel
    private final Map<String, Boolean> timedControllerLastState = new HashMap<>();

    // Actionbar-Status pro Spieler für die Namensanzeige
    private final Map<java.util.UUID, String> lastControllerActionbar = new HashMap<>();

    // Undo-System: letzte Aktion pro Controller (wird nach 5 Minuten gelöscht)
    private final Map<String, UndoAction> lastActions = new HashMap<>();

    // Secret-Wall Runtime (offene Wände + Originalzustand)
    private final Map<String, List<SecretBlockSnapshot>> openSecretWalls = new HashMap<>();

    // Knarrherz-Override: aktivierte Herzen werden zyklisch auf "active=true" gehalten
    private final Set<String> forcedActiveCreakingHearts = new HashSet<>();

    // Geöffnete Gitter (AIR) mit Originalmaterial für die Wiederherstellung
    private final Map<String, Material> openGrates = new HashMap<>();

    // Laufende Zeitplan-Shows für Werfer/Spender pro Controller
    private final Map<String, Integer> timedContainerTasks = new HashMap<>();
    private final Map<String, Integer> timedContainerTaskShotDelays = new HashMap<>();
    private final Map<String, String> timedContainerTaskModes = new HashMap<>();
    private final Map<String, Integer> timedContainerNextIndices = new HashMap<>();

    // Secret-Editor: Spieler -> ausgewählter Controller
    private final Map<java.util.UUID, String> selectedSecretController = new HashMap<>();

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
        getServer().getScheduler().runTaskTimer(this, this::enforceCreakingHeartStates, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, this::updateControllerNameActionBar, 0L, 5L);

        // Undo-Actions nach 5 Minuten aufräumen
        getServer().getScheduler().runTaskTimer(this, this::cleanupOldUndoActions, 0L, 20L * 60 * 5);

        getLogger().info("ButtonControl v" + getDescription().getVersion() + " wurde erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        stopAllTimedContainerTasks();
        openGrates.clear();
        forcedActiveCreakingHearts.clear();
        if (dataManager != null) {
            dataManager.shutdown();
        }
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
                if (isLamp(tb.getType()) && tb.getBlockData() instanceof Lightable) {
                    Lightable lamp = (Lightable) tb.getBlockData();
                    lamp.setLit(!isDay);
                    tb.setBlockData(lamp);
                }
            }

            // Secret Wall: bei Tag öffnen, bei Nacht schließen
            if (isDay) {
                triggerSecretWall(buttonId, false);
            } else {
                closeSecretWall(buttonId);
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
        Set<String> activeScheduleButtons = new HashSet<>();

        for (String controllerLoc : dataManager.getAllPlacedControllers()) {
            String buttonId = dataManager.getButtonIdForPlacedController(controllerLoc);
            if (buttonId == null) continue;

            long openTime  = dataManager.getScheduleOpenTime(buttonId);
            long closeTime = dataManager.getScheduleCloseTime(buttonId);
            if (openTime < 0 || closeTime < 0) {
                stopTimedContainerTask(buttonId);
                continue;
            }

            activeScheduleButtons.add(buttonId);

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
            List<String> connected = dataManager.getConnectedBlocks(buttonId);
            if (connected != null && !connected.isEmpty()) {
                updateTimedContainerAutomation(buttonId, connected, shouldBeOpen);
            } else {
                stopTimedContainerTask(buttonId);
            }

            if (lastState != null && lastState == shouldBeOpen) continue;

            timedControllerLastState.put(controllerLoc, shouldBeOpen);
            if (connected != null && !connected.isEmpty()) {
                setOpenables(connected, shouldBeOpen);
            }
        }

        // Falls Zeitpläne entfernt wurden, zugehörige Show-Tasks sauber stoppen.
        List<String> inactiveTaskButtons = new ArrayList<>(timedContainerTasks.keySet());
        for (String buttonId : inactiveTaskButtons) {
            if (!activeScheduleButtons.contains(buttonId)) {
                stopTimedContainerTask(buttonId);
            }
        }
    }

    private void updateTimedContainerAutomation(String buttonId, List<String> connected, boolean activeWindow) {
        if (!containsTimedContainer(connected)) {
            stopTimedContainerTask(buttonId);
            return;
        }

        if (!activeWindow) {
            stopTimedContainerTask(buttonId);
            return;
        }

        int configuredDelay = dataManager.getScheduleShotDelayTicks(buttonId);
        int shotDelay = configuredDelay >= 0
            ? Math.max(0, configuredDelay)
            : Math.max(0, configManager.getConfig().getInt(
                "timed-container-shot-delay-ticks",
                configManager.getConfig().getInt("timed-container-interval-ticks", 40)));
        String configuredMode = normalizeTimedContainerMode(dataManager.getScheduleTriggerMode(buttonId));
        String taskMode = configuredMode != null
            ? configuredMode
            : normalizeTimedContainerMode(configManager.getConfig().getString(
                "timed-container-trigger-mode",
                TIMED_CONTAINER_MODE_SIMULTANEOUS));
        int taskPeriod = Math.max(1, shotDelay);

        Integer existingTaskId = timedContainerTasks.get(buttonId);
        if (existingTaskId != null) {
            Integer existingDelay = timedContainerTaskShotDelays.get(buttonId);
            String existingMode = timedContainerTaskModes.get(buttonId);
            if (existingDelay != null && existingDelay == taskPeriod && taskMode.equals(existingMode)) {
                return;
            }
            stopTimedContainerTask(buttonId);
        }

        int taskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            List<String> latestConnected = dataManager.getConnectedBlocks(buttonId);
            List<String> timedContainers = getTimedContainerLocations(latestConnected);
            if (timedContainers.isEmpty()) {
                stopTimedContainerTask(buttonId);
                return;
            }

            if (TIMED_CONTAINER_MODE_SEQUENTIAL.equals(taskMode)) {
                int nextIndex = timedContainerNextIndices.getOrDefault(buttonId, 0);
                if (nextIndex >= timedContainers.size()) nextIndex = 0;

                String locStr = timedContainers.get(nextIndex);
                Location l = parseLocation(locStr);
                if (l != null) {
                    Block b = l.getBlock();
                    if (b.getType() == Material.DISPENSER) {
                        triggerContainer(b, "dispense");
                    } else if (b.getType() == Material.DROPPER) {
                        triggerContainer(b, "drop");
                    }
                }
                timedContainerNextIndices.put(buttonId, (nextIndex + 1) % timedContainers.size());
            } else {
                for (String locStr : timedContainers) {
                    Location l = parseLocation(locStr);
                    if (l == null) continue;

                    Block b = l.getBlock();
                    if (b.getType() == Material.DISPENSER) {
                        triggerContainer(b, "dispense");
                    } else if (b.getType() == Material.DROPPER) {
                        triggerContainer(b, "drop");
                    }
                }
            }
        }, 0L, taskPeriod);

        timedContainerTasks.put(buttonId, taskId);
        timedContainerTaskShotDelays.put(buttonId, taskPeriod);
        timedContainerTaskModes.put(buttonId, taskMode);
        timedContainerNextIndices.put(buttonId, 0);
    }

    private boolean containsTimedContainer(List<String> connected) {
        return !getTimedContainerLocations(connected).isEmpty();
    }

    private List<String> getTimedContainerLocations(List<String> connected) {
        List<String> timedContainers = new ArrayList<>();
        for (String locStr : connected) {
            Location l = parseLocation(locStr);
            if (l == null) continue;
            Material m = l.getBlock().getType();
            if (m == Material.DISPENSER || m == Material.DROPPER) {
                timedContainers.add(locStr);
            }
        }
        return timedContainers;
    }

    private String normalizeTimedContainerMode(String mode) {
        if (mode == null) return null;
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (TIMED_CONTAINER_MODE_SEQUENTIAL.equals(normalized)) return TIMED_CONTAINER_MODE_SEQUENTIAL;
        return TIMED_CONTAINER_MODE_SIMULTANEOUS;
    }

    private void stopTimedContainerTask(String buttonId) {
        Integer taskId = timedContainerTasks.remove(buttonId);
        if (taskId != null) getServer().getScheduler().cancelTask(taskId);
        timedContainerTaskShotDelays.remove(buttonId);
        timedContainerTaskModes.remove(buttonId);
        timedContainerNextIndices.remove(buttonId);
    }

    private void stopAllTimedContainerTasks() {
        for (Integer taskId : timedContainerTasks.values()) {
            getServer().getScheduler().cancelTask(taskId);
        }
        timedContainerTasks.clear();
        timedContainerTaskShotDelays.clear();
        timedContainerTaskModes.clear();
        timedContainerNextIndices.clear();
    }

    private boolean triggerContainer(Block block, String methodName) {
        try {
            Object state = block.getState();
            java.lang.reflect.Method method = state.getClass().getMethod(methodName);
            Object result = method.invoke(state);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // -----------------------------------------------------------------------
    //  Controller-Name bei Blickkontakt (Actionbar)
    // -----------------------------------------------------------------------

    private void updateControllerNameActionBar() {
        if (!configManager.getConfig().getBoolean("controller-name-display.enabled", true)) {
            clearAllControllerActionBars();
            return;
        }

        int maxDistance = Math.max(1,
            configManager.getConfig().getInt("controller-name-display.max-look-distance", 8));
        String format = configManager.getConfig().getString(
            "controller-name-display.format", "§6Controller: §f%s");

        for (Player player : getServer().getOnlinePlayers()) {
            String message = null;
            Block target = player.getTargetBlockExact(maxDistance);
            if (isValidController(target)) {
                String targetLoc = toLoc(target);
                String buttonId = dataManager.getButtonIdForLocation(targetLoc);
                if (buttonId != null) {
                    boolean canSee = dataManager.canAccess(buttonId, player.getUniqueId())
                        || player.hasPermission("buttoncontrol.admin");
                    String name = dataManager.getControllerName(buttonId);
                    if (canSee && name != null && !name.trim().isEmpty()) {
                        message = String.format(format, name);
                    }
                }
            }

            java.util.UUID uuid = player.getUniqueId();
            String previous = lastControllerActionbar.get(uuid);
            if (message == null) {
                if (previous != null) {
                    sendActionBar(player, " ");
                    lastControllerActionbar.remove(uuid);
                }
            } else if (!message.equals(previous)) {
                sendActionBar(player, message);
                lastControllerActionbar.put(uuid, message);
            }
        }
    }

    private void clearAllControllerActionBars() {
        if (lastControllerActionbar.isEmpty()) return;
        for (Player player : getServer().getOnlinePlayers()) {
            if (lastControllerActionbar.containsKey(player.getUniqueId())) {
                sendActionBar(player, " ");
            }
        }
        lastControllerActionbar.clear();
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
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
            boolean hasConnected = connected != null && !connected.isEmpty();
            List<String> secretBlocks = dataManager.getSecretBlocks(buttonId);
            boolean hasSecret = secretBlocks != null && !secretBlocks.isEmpty();
            if (!hasConnected && !hasSecret) continue;

            if (detected) {
                if (!activeSensors.contains(controllerLoc)) {
                    if (hasConnected) setOpenables(connected, true);
                    triggerSecretWall(buttonId, false);
                    activeSensors.add(controllerLoc);
                }
                lastMotionDetections.put(controllerLoc, now);
            } else {
                Long last = lastMotionDetections.get(controllerLoc);
                if (last != null && now - last >= delay) {
                    if (hasConnected) setOpenables(connected, false);
                    closeSecretWall(buttonId);
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
            } else if (isGrate(tb.getType()) || (tb.getType() == Material.AIR && openGrates.containsKey(locStr))) {
                setGrateOpenState(tb, locStr, open);
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
            sender.sendMessage("§6[BC] §7/bc <info|reload|note|list|rename|schedule|trust|untrust|public|private|undo|secret>");
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
            stopAllTimedContainerTasks();
            timedControllerLastState.clear();
            openGrates.clear();
            forcedActiveCreakingHearts.clear();
            clearAllControllerActionBars();
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

        if (sub.equals("secret")) {
            return handleSecretCommand(player, args);
        }

        if (sub.equals("list") || sub.equals("rename") || sub.equals("schedule")
                || sub.equals("trust") || sub.equals("untrust")
            || sub.equals("public") || sub.equals("private") || sub.equals("undo")) {

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
                    if (!isOwner && !isAdmin) { 
                        player.sendMessage("§c✖ Nur der Besitzer oder Admins können das tun."); 
                        return true; 
                    }
                    if (args.length < 2) { player.sendMessage("§7/bc rename <Name>"); return true; }
                    String oldRename = dataManager.getControllerName(buttonId);
                    String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    if (newName.length() > 32) { player.sendMessage("§cName zu lang (max. 32 Zeichen)."); return true; }
                    String coloredName = ChatColor.translateAlternateColorCodes('&', newName);
                    dataManager.setControllerName(buttonId, coloredName);
                    saveUndoAction(buttonId, new UndoAction(UndoAction.Type.RENAME, oldRename, coloredName));
                    player.sendMessage(String.format(configManager.getMessage("controller-umbenannt"), coloredName));
                    break;

                case "schedule":
                    if (!isOwner && !isAdmin) { player.sendMessage(configManager.getMessage("nur-besitzer-abbauen")); return true; }
                    new ScheduleGUI(this, player, buttonId).open();
                    break;

                case "trust":
                    if (!isOwner && !isAdmin) { 
                        player.sendMessage("§c✖ Nur der Besitzer oder Admins können das tun."); 
                        return true; 
                    }
                    if (args.length < 2) { player.sendMessage("§7/bc trust <Spieler>"); return true; }
                    org.bukkit.OfflinePlayer tp = Bukkit.getOfflinePlayer(args[1]);
                    if (!tp.hasPlayedBefore() && !tp.isOnline()) {
                        player.sendMessage(configManager.getMessage("spieler-nicht-gefunden")); return true;
                    }
                    dataManager.addTrustedPlayer(buttonId, tp.getUniqueId());
                    saveUndoAction(buttonId, new UndoAction(UndoAction.Type.TRUST_ADD, tp.getUniqueId().toString(), null));
                    player.sendMessage(String.format(configManager.getMessage("trust-hinzugefuegt"), args[1]));
                    break;

                case "untrust":
                    if (!isOwner && !isAdmin) { 
                        player.sendMessage("§c✖ Nur der Besitzer oder Admins können das tun."); 
                        return true; 
                    }
                    if (args.length < 2) { player.sendMessage("§7/bc untrust <Spieler>"); return true; }
                    org.bukkit.OfflinePlayer untp = Bukkit.getOfflinePlayer(args[1]);
                    java.util.UUID uuid = untp.getUniqueId();
                    dataManager.removeTrustedPlayer(buttonId, uuid);
                    saveUndoAction(buttonId, new UndoAction(UndoAction.Type.TRUST_REMOVE, uuid.toString(), null));
                    player.sendMessage(String.format(configManager.getMessage("trust-entfernt"), args[1]));
                    break;

                default: // public / private
                    if (!isOwner && !isAdmin) { 
                        player.sendMessage("§c✖ Nur der Besitzer oder Admins können das tun."); 
                        return true; 
                    }
                    boolean pub = sub.equals("public");
                    boolean oldStatus = dataManager.isPublic(buttonId);
                    dataManager.setPublic(buttonId, pub);
                    saveUndoAction(buttonId, new UndoAction(pub ? UndoAction.Type.PUBLIC : UndoAction.Type.PRIVATE, 
                        String.valueOf(oldStatus), String.valueOf(pub)));
                    player.sendMessage(String.format(configManager.getMessage("status-geandert"),
                        pub ? "§aÖffentlich" : "§cPrivat"));
                    break;

                case "undo":
                    if (lastActions.containsKey(buttonId)) {
                        UndoAction action = lastActions.get(buttonId);
                        boolean canUndo = dataManager.isOwner(buttonId, player.getUniqueId()) || player.hasPermission("buttoncontrol.admin");
                        if (!canUndo) {
                            player.sendMessage("§c✖ Du kannst nur Aktionen deiner eigenen Controller rückgängig machen.");
                            return true;
                        }
                        undoAction(buttonId, action, player);
                    } else {
                        player.sendMessage("§c✖ Keine Aktion zum Rückgängigmachen für diesen Controller.");
                    }
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

    private boolean isLamp(Material m) {
        return m == Material.REDSTONE_LAMP
            || "COPPER_BULB".equals(m.name())
            || m.name().endsWith("_COPPER_BULB");
    }

    public boolean isGrate(Material m) {
        return m == Material.IRON_BARS || m.name().endsWith("_GRATE");
    }

    public boolean isManagedOpenGrateLocation(String locStr) {
        return openGrates.containsKey(locStr);
    }

    public Boolean toggleGrate(Block block) {
        if (block == null) return null;

        String locStr = toLoc(block);
        Material type = block.getType();

        if (isGrate(type)) {
            openGrates.put(locStr, type);
            block.setType(Material.AIR, false);
            return true;
        }

        if (type == Material.AIR) {
            Material original = openGrates.get(locStr);
            if (original != null) {
                block.setType(original, false);
                openGrates.remove(locStr);
                return false;
            }
        }

        return null;
    }

    private void setGrateOpenState(Block block, String locStr, boolean open) {
        if (open) {
            if (isGrate(block.getType())) {
                openGrates.put(locStr, block.getType());
                block.setType(Material.AIR, false);
            }
            return;
        }

        Material original = openGrates.get(locStr);
        if (original != null) {
            block.setType(original, false);
            openGrates.remove(locStr);
        }
    }

    private boolean isCreakingHeart(Material m) {
        return "CREAKING_HEART".equals(m.name());
    }

    public Boolean togglePersistentCreakingHeart(Block block) {
        if (block == null || !isCreakingHeart(block.getType())) return null;

        String loc = toLoc(block);
        boolean targetActive = !forcedActiveCreakingHearts.contains(loc);
        if (!applyCreakingHeartActive(block, targetActive)) return null;

        if (targetActive) forcedActiveCreakingHearts.add(loc);
        else forcedActiveCreakingHearts.remove(loc);

        return targetActive;
    }

    private void enforceCreakingHeartStates() {
        if (forcedActiveCreakingHearts.isEmpty()) return;

        java.util.Iterator<String> it = forcedActiveCreakingHearts.iterator();
        while (it.hasNext()) {
            String loc = it.next();
            Location l = parseLocation(loc);
            if (l == null) {
                it.remove();
                continue;
            }

            Block block = l.getBlock();
            if (!isCreakingHeart(block.getType())) {
                it.remove();
                continue;
            }

            if (!applyCreakingHeartActive(block, true)) {
                it.remove();
            }
        }
    }

    private boolean applyCreakingHeartActive(Block block, boolean active) {
        org.bukkit.block.data.BlockData data = block.getBlockData();
        try {
            java.lang.reflect.Method setActive = data.getClass().getMethod("setActive", boolean.class);
            setActive.invoke(data, active);
            block.setBlockData(data);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
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

    private boolean handleSecretCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§7/bc secret <select|info|add|remove|clear|delay|animation>");
            return true;
        }

        String mode = args[1].toLowerCase();
        String buttonId = resolveSecretController(player);

        if (mode.equals("select")) {
            Block target = player.getTargetBlockExact(5);
            if (!isValidController(target)) {
                player.sendMessage(configManager.getMessage("kein-controller-im-blick"));
                return true;
            }
            String targetLoc = toLoc(target);
            String targetButtonId = dataManager.getButtonIdForLocation(targetLoc);
            if (targetButtonId == null) {
                player.sendMessage(configManager.getMessage("keine-bloecke-verbunden"));
                return true;
            }
            boolean isAdmin = player.hasPermission("buttoncontrol.admin");
            boolean isOwner = dataManager.isOwner(targetButtonId, player.getUniqueId());
            if (!isOwner && !isAdmin) {
                player.sendMessage("§c✖ Nur der Besitzer oder Admins können Secret-Türen verwalten.");
                return true;
            }
            selectedSecretController.put(player.getUniqueId(), targetButtonId);
            player.sendMessage("§6[Secret] §7Controller ausgewählt. Jetzt auf Wandblock schauen + /bc secret add");
            return true;
        }

        if (buttonId == null) {
            player.sendMessage("§c✖ Kein Controller ausgewählt. Schau auf den Controller und nutze §7/bc secret select§c.");
            return true;
        }

        boolean isAdmin = player.hasPermission("buttoncontrol.admin");
        boolean isOwner = dataManager.isOwner(buttonId, player.getUniqueId());
        if (!isOwner && !isAdmin) {
            player.sendMessage("§c✖ Nur der Besitzer oder Admins können Secret-Türen verwalten.");
            return true;
        }

        if (mode.equals("info")) {
            List<String> sb = dataManager.getSecretBlocks(buttonId);
            long delayMs = dataManager.getSecretRestoreDelayMs(buttonId);
            String animation = normalizeSecretAnimation(dataManager.getSecretAnimation(buttonId));
            player.sendMessage("§6[Secret] §7Blöcke: §f" + sb.size() + " §8| §7Delay: §f" + (delayMs / 1000.0) + "s");
            player.sendMessage("§6[Secret] §7Animation: §f" + animation);
            return true;
        }

        if (mode.equals("clear")) {
            dataManager.clearSecret(buttonId);
            closeSecretWall(buttonId);
            player.sendMessage("§6[Secret] §7Alle Secret-Blöcke entfernt.");
            return true;
        }

        if (mode.equals("delay")) {
            if (args.length < 3) {
                player.sendMessage("§7/bc secret delay <sekunden>");
                return true;
            }
            try {
                int sec = Integer.parseInt(args[2]);
                if (sec < 1 || sec > 300) {
                    player.sendMessage("§c✖ Delay muss zwischen 1 und 300 Sekunden liegen.");
                    return true;
                }
                dataManager.setSecretRestoreDelayMs(buttonId, sec * 1000L);
                player.sendMessage("§6[Secret] §7Wiederherstellung: §f" + sec + "s");
            } catch (NumberFormatException e) {
                player.sendMessage("§c✖ Ungültige Zahl.");
            }
            return true;
        }

        if (mode.equals("animation")) {
            if (args.length < 3) {
                player.sendMessage("§7/bc secret animation <instant|wave|reverse|center>");
                return true;
            }
            String animation = normalizeSecretAnimation(args[2]);
            if (!isValidSecretAnimation(animation)) {
                player.sendMessage("§c✖ Nutze: instant, wave, reverse oder center");
                return true;
            }
            dataManager.setSecretAnimation(buttonId, animation);
            player.sendMessage("§6[Secret] §7Animation gesetzt: §f" + animation);
            return true;
        }

        Block wallTarget = player.getTargetBlockExact(6);
        if (wallTarget == null || wallTarget.getType() == Material.AIR) {
            player.sendMessage("§c✖ Schau auf einen Block innerhalb von 6 Blöcken.");
            return true;
        }

        String wallLoc = toLoc(wallTarget);
        List<String> secretBlocks = new ArrayList<>(dataManager.getSecretBlocks(buttonId));

        if (mode.equals("add")) {
            if (isUnsafeSecretBlock(wallTarget)) {
                player.sendMessage("§c✖ Dieser Block trägt/enthält einen Controller oder Schalter. Nicht als Secret-Block erlaubt.");
                return true;
            }
            if (secretBlocks.contains(wallLoc)) {
                player.sendMessage("§c✖ Dieser Block ist bereits als Secret-Block gesetzt.");
                return true;
            }
            secretBlocks.add(wallLoc);
            dataManager.setSecretBlocks(buttonId, secretBlocks);
            player.sendMessage("§6[Secret] §7Block hinzugefügt. §8(" + secretBlocks.size() + ")");
            return true;
        }

        if (mode.equals("remove")) {
            if (!secretBlocks.remove(wallLoc)) {
                player.sendMessage("§c✖ Dieser Block ist kein Secret-Block.");
                return true;
            }
            dataManager.setSecretBlocks(buttonId, secretBlocks);
            player.sendMessage("§6[Secret] §7Block entfernt. §8(" + secretBlocks.size() + ")");
            return true;
        }

        player.sendMessage("§7/bc secret <select|info|add|remove|clear|delay|animation>");
        return true;
    }

    private String resolveSecretController(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (isValidController(target)) {
            String id = dataManager.getButtonIdForLocation(toLoc(target));
            if (id != null) {
                selectedSecretController.put(player.getUniqueId(), id);
                return id;
            }
        }
        return selectedSecretController.get(player.getUniqueId());
    }

    private boolean isUnsafeSecretBlock(Block wallBlock) {
        String wallLoc = toLoc(wallBlock);
        for (String controllerLoc : dataManager.getAllPlacedControllers()) {
            Location l = parseLocation(controllerLoc);
            if (l == null) continue;
            Block controller = l.getBlock();

            // Controller selbst nie entfernen
            if (controllerLoc.equals(wallLoc)) return true;

            // Trägerblock des Controllers nie entfernen
            Block support = getSupportBlock(controller);
            if (support != null && toLoc(support).equals(wallLoc)) return true;
        }
        return false;
    }

    private Block getSupportBlock(Block controller) {
        Material m = controller.getType();

        // Aufliegende Controller: Teppich / Tageslichtsensor / Tripwire Hook
        if (m.name().endsWith("_CARPET") || m == Material.DAYLIGHT_DETECTOR || m == Material.TRIPWIRE_HOOK) {
            return controller.getRelative(BlockFace.DOWN);
        }

        // Buttons & wall-attached Blöcke
        if (controller.getBlockData() instanceof org.bukkit.block.data.FaceAttachable) {
            org.bukkit.block.data.FaceAttachable fa = (org.bukkit.block.data.FaceAttachable) controller.getBlockData();
            switch (fa.getAttachedFace()) {
                case FLOOR:
                    return controller.getRelative(BlockFace.DOWN);
                case CEILING:
                    return controller.getRelative(BlockFace.UP);
                case WALL:
                    if (controller.getBlockData() instanceof org.bukkit.block.data.Directional) {
                        org.bukkit.block.data.Directional d = (org.bukkit.block.data.Directional) controller.getBlockData();
                        return controller.getRelative(d.getFacing().getOppositeFace());
                    }
                    break;
                default:
                    break;
            }
        }

        // Schilder
        if (m.name().endsWith("_SIGN")) {
            if (m.name().contains("WALL") && controller.getBlockData() instanceof org.bukkit.block.data.Directional) {
                org.bukkit.block.data.Directional d = (org.bukkit.block.data.Directional) controller.getBlockData();
                return controller.getRelative(d.getFacing().getOppositeFace());
            }
            return controller.getRelative(BlockFace.DOWN);
        }

        return null;
    }

    /** Löst eine Secret Wall aus. autoClose=false: kein automatisches Schließen (für Sensoren). */
    public boolean triggerSecretWall(String buttonId) {
        return triggerSecretWall(buttonId, true);
    }

    public boolean triggerSecretWall(String buttonId, boolean autoClose) {
        if (buttonId == null) return false;
        List<String> secretBlocks = dataManager.getSecretBlocks(buttonId);
        if (secretBlocks == null || secretBlocks.isEmpty()) return false;
        if (openSecretWalls.containsKey(buttonId)) return false;

        List<SecretBlockSnapshot> snapshots = new ArrayList<>();
        for (String locStr : secretBlocks) {
            Location l = parseLocation(locStr);
            if (l == null) continue;
            Block b = l.getBlock();
            if (b.getType() == Material.AIR) continue;
            snapshots.add(new SecretBlockSnapshot(locStr, b.getType().name(), b.getBlockData().getAsString(), l.getBlockX(), l.getBlockY(), l.getBlockZ()));
        }

        if (snapshots.isEmpty()) return false;
        openSecretWalls.put(buttonId, snapshots);

        String animation = normalizeSecretAnimation(dataManager.getSecretAnimation(buttonId));
        List<SecretBlockSnapshot> openOrder = getOrderedSecretSnapshots(snapshots, animation, true);
        long openStepTicks = getSecretStepTicks(animation);
        long[] openDelays = computeDelays(openOrder, animation, openStepTicks);
        scheduleSecretOpen(openOrder, openDelays);

        if (autoClose) {
            long delayMs = Math.max(1000L, dataManager.getSecretRestoreDelayMs(buttonId));
            long delayTicks = Math.max(1L, delayMs / 50L);
            long openDurationTicks = openDelays.length == 0 ? 0L : openDelays[openDelays.length - 1];
            getServer().getScheduler().runTaskLater(this,
                () -> closeSecretWall(buttonId), openDurationTicks + delayTicks);
        }
        return true;
    }

    public void closeSecretWall(String buttonId) {
        List<SecretBlockSnapshot> snapshots = openSecretWalls.remove(buttonId);
        if (snapshots == null || snapshots.isEmpty()) return;

        String animation = normalizeSecretAnimation(dataManager.getSecretAnimation(buttonId));
        List<SecretBlockSnapshot> closeOrder = getOrderedSecretSnapshots(snapshots, animation, false);
        long closeStepTicks = getSecretStepTicks(animation);
        long[] closeDelays = computeDelays(closeOrder, animation, closeStepTicks);
        scheduleSecretClose(closeOrder, closeDelays);
    }

    private void scheduleSecretOpen(List<SecretBlockSnapshot> snapshots, long[] delays) {
        for (int i = 0; i < snapshots.size(); i++) {
            SecretBlockSnapshot s = snapshots.get(i);
            long when = delays[i];
            getServer().getScheduler().runTaskLater(this, () -> {
                Location l = parseLocation(s.loc);
                if (l == null) return;
                Block b = l.getBlock();
                if (b.getType() != Material.AIR) {
                    b.setType(Material.AIR, false);
                }
            }, when);
        }
    }

    private void scheduleSecretClose(List<SecretBlockSnapshot> snapshots, long[] delays) {
        for (int i = 0; i < snapshots.size(); i++) {
            SecretBlockSnapshot s = snapshots.get(i);
            long when = delays[i];
            getServer().getScheduler().runTaskLater(this, () -> {
                Location l = parseLocation(s.loc);
                if (l == null) return;
                Block b = l.getBlock();
                Material m = Material.matchMaterial(s.materialName);
                if (m == null || m == Material.AIR) return;
                b.setType(m, false);
                try {
                    b.setBlockData(Bukkit.createBlockData(s.blockData), false);
                } catch (IllegalArgumentException ignored) {
                    // Fallback: Material wurde bereits gesetzt
                }
            }, when);
        }
    }

    private List<SecretBlockSnapshot> getOrderedSecretSnapshots(List<SecretBlockSnapshot> source, String animation, boolean opening) {
        List<SecretBlockSnapshot> ordered = new ArrayList<>(source);
        switch (animation) {
            case "instant":
            case "wave":
                if (!opening) {
                    java.util.Collections.reverse(ordered);
                }
                return ordered;
            case "reverse":
                if (opening) {
                    java.util.Collections.reverse(ordered);
                }
                return ordered;
            case "center":
                double centerX = 0;
                double centerY = 0;
                double centerZ = 0;
                for (SecretBlockSnapshot s : ordered) {
                    centerX += s.x;
                    centerY += s.y;
                    centerZ += s.z;
                }
                centerX /= ordered.size();
                centerY /= ordered.size();
                centerZ /= ordered.size();
                final double cx = centerX;
                final double cy = centerY;
                final double cz = centerZ;
                ordered.sort((a, b) -> Double.compare(distanceSquared(a, cx, cy, cz), distanceSquared(b, cx, cy, cz)));
                if (!opening) {
                    java.util.Collections.reverse(ordered);
                }
                return ordered;
            default:
                return ordered;
        }
    }

    private long[] computeDelays(List<SecretBlockSnapshot> ordered, String animation, long stepTicks) {
        long[] delays = new long[ordered.size()];
        if (!animation.equals("center")) {
            for (int i = 0; i < ordered.size(); i++) {
                delays[i] = i * stepTicks;
            }
            return delays;
        }
        // Für center: Blöcke im gleichen Abstandsring bekommen denselben Tick
        double cx = 0, cy = 0, cz = 0;
        for (SecretBlockSnapshot s : ordered) { cx += s.x; cy += s.y; cz += s.z; }
        cx /= ordered.size(); cy /= ordered.size(); cz /= ordered.size();
        final double fcx = cx, fcy = cy, fcz = cz;
        double prev = -1;
        int rank = -1;
        for (int i = 0; i < ordered.size(); i++) {
            double d = distanceSquared(ordered.get(i), fcx, fcy, fcz);
            if (i == 0 || Math.abs(d - prev) > 0.01) {
                rank++;
                prev = d;
            }
            delays[i] = rank * stepTicks;
        }
        return delays;
    }

    private double distanceSquared(SecretBlockSnapshot s, double centerX, double centerY, double centerZ) {
        double dx = s.x - centerX;
        double dy = s.y - centerY;
        double dz = s.z - centerZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private long getSecretStepTicks(String animation) {
        return animation.equals("instant") ? 0L : 2L;
    }

    private boolean isValidSecretAnimation(String animation) {
        return animation.equals("instant") || animation.equals("wave")
            || animation.equals("reverse") || animation.equals("center");
    }

    private String normalizeSecretAnimation(String animation) {
        if (animation == null) return "wave";
        return animation.trim().toLowerCase();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Undo-System
    // ─────────────────────────────────────────────────────────────────────────

    private void saveUndoAction(String buttonId, UndoAction action) {
        lastActions.put(buttonId, action);
    }

    private void undoAction(String buttonId, UndoAction action, Player player) {
        switch (action.type) {
            case RENAME:
                dataManager.setControllerName(buttonId, (String) action.oldValue);
                player.sendMessage("§a✔ Rename rückgängig gemacht.");
                break;
            case TRUST_ADD:
                dataManager.removeTrustedPlayer(buttonId, java.util.UUID.fromString((String) action.oldValue));
                player.sendMessage("§a✔ Trust-Hinzufügung rückgängig gemacht.");
                break;
            case TRUST_REMOVE:
                dataManager.addTrustedPlayer(buttonId, java.util.UUID.fromString((String) action.oldValue));
                player.sendMessage("§a✔ Trust-Entfernung rückgängig gemacht.");
                break;
            case PUBLIC:
            case PRIVATE:
                boolean wasPublic = Boolean.parseBoolean((String) action.oldValue);
                dataManager.setPublic(buttonId, wasPublic);
                player.sendMessage("§a✔ Status rückgängig gemacht: " + (wasPublic ? "§aÖffentlich" : "§cPrivat"));
                break;
        }
        lastActions.remove(buttonId);
    }

    private void cleanupOldUndoActions() {
        long currentTime = System.currentTimeMillis();
        long timeout = 5 * 60 * 1000; // 5 Minuten
        lastActions.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > timeout);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Undo Action Klasse
    // ─────────────────────────────────────────────────────────────────────────

    public static class UndoAction {
        public enum Type { RENAME, TRUST_ADD, TRUST_REMOVE, PUBLIC, PRIVATE }

        public Type type;
        public Object oldValue;
        public Object newValue;
        public long timestamp;

        public UndoAction(Type type, Object oldValue, Object newValue) {
            this.type = type;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class SecretBlockSnapshot {
        public final String loc;
        public final String materialName;
        public final String blockData;
        public final int x;
        public final int y;
        public final int z;

        public SecretBlockSnapshot(String loc, String materialName, String blockData, int x, int y, int z) {
            this.loc = loc;
            this.materialName = materialName;
            this.blockData = blockData;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

}