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
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.ArrayList;
import java.util.List;

import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class ButtonControl extends JavaPlugin {
    private ConfigManager configManager;
    private DataManager dataManager;
    private Map<String, Long> lastMotionDetections = new HashMap<>();

    @Override
    public void onEnable() {
        // Initialisierung der Manager
        configManager = new ConfigManager(this);
        dataManager = new DataManager(this);

        // Spigot Update Checker beim Serverstart ausführen
            new UpdateChecker(this, 127702).getVersion(version -> {
                String currentVersion = this.getDescription().getVersion();
                String normalizedLatest = version.replaceFirst("(?i)^(version\\s*|v\\.?\\s*)", "").trim();
                String normalizedCurrent = currentVersion.replaceFirst("(?i)^(version\\s*|v\\.?\\s*)", "").trim();

                if (isNewerVersion(normalizedLatest, normalizedCurrent)) {
                    // Konsole bleibt sachlich
                    getLogger().info("Update verfügbar: v" + version);

                    // Schicke die stylische Nachricht an Admins
                    Bukkit.getScheduler().runTask(this, () -> {
                        Bukkit.getOnlinePlayers().stream()
                                .filter(p -> p.hasPermission("buttoncontrol.update"))
                                .forEach(p -> {
                                    p.sendMessage(""); // Leerzeile für Abstand
                                    p.sendMessage("§8§m-----------------------------------------");
                                    p.sendMessage("   §6§lButtonControl §7- §e§lUpdate verfügbar!");
                                    p.sendMessage("");
                                    p.sendMessage("   §7Aktuelle Version: §c" + currentVersion);
                                    p.sendMessage("   §7Neue Version:     §a" + version);
                                    p.sendMessage("");
                                    p.sendMessage("   §eDownload hier:");
                                    p.sendMessage("   §bhttps://www.spigotmc.org/resources/127702/");
                                    p.sendMessage("§8§m-----------------------------------------");
                                    p.sendMessage("");
                                });
                    });
                } else {
                    getLogger().info("ButtonControl ist auf dem neuesten Stand (v" + currentVersion + ").");
                }
            });

        // Listener für Spieler-Joins (Update-Benachrichtigung beim Betreten des Servers)
            getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                    Player player = event.getPlayer();
                    if (!player.hasPermission("buttoncontrol.update")) return;
                    
                    new UpdateChecker(ButtonControl.this, 127702).getVersion(version -> {
                        String currentVersion = getDescription().getVersion();
                        String normalizedLatest = version.replaceFirst("(?i)^(version\\s*|v\\.?\\s*)", "").trim();
                        String normalizedCurrent = currentVersion.replaceFirst("(?i)^(version\\s*|v\\.?\\s*)", "").trim();

                        if (isNewerVersion(normalizedLatest, normalizedCurrent)) {
                            // Stylische Box für den Spieler beim Joinen
                            player.sendMessage(""); 
                            player.sendMessage("§8§m-----------------------------------------");
                            player.sendMessage("   §6§lButtonControl §7- §e§lUpdate verfügbar!");
                            player.sendMessage("");
                            player.sendMessage("   §7Aktuelle Version: §c" + currentVersion);
                            player.sendMessage("   §7Neue Version:     §a" + version);
                            player.sendMessage("");
                            player.sendMessage("   §eDownload hier:");
                            player.sendMessage("   §bhttps://www.spigotmc.org/resources/127702/");
                            player.sendMessage("§8§m-----------------------------------------");
                            player.sendMessage("");
                        }
                    });
                }
            }, this);

            // Konfiguration und Spielmechaniken laden
            updateConfigWithDefaults();
        
        // --- COMMANDS & TAB-COMPLETER ---
        // Registriert den Executor (Befehlsverarbeitung) und den TabCompleter (Vorschläge)
        if (getCommand("bc") != null) {
            getCommand("bc").setExecutor(this); // 'this' setzt voraus, dass ButtonControl 'onCommand' enthält
            getCommand("bc").setTabCompleter(new ButtonTabCompleter());
        }

        // Event-Listener registrieren
        getServer().getPluginManager().registerEvents(new ButtonListener(this, configManager, dataManager), this);
        
        // Rezepte und bStats/Metrics
        registerRecipes();
        MetricsHandler.startMetrics(this);
        
        // --- AUTOMATISIERUNG-TIMER ---
        // Daylight Sensoren: Prüfung alle 200 Ticks (10 Sekunden)
        getServer().getScheduler().runTaskTimer(this, this::checkDaylightSensors, 0L, 20L * 10);
        
        // Bewegungsmelder (Motion Sensors): Prüfung alle 10 Ticks (0.5 Sekunden) für flüssige Erkennung
        getServer().getScheduler().runTaskTimer(this, this::checkMotionSensors, 0L, 10L);

        getLogger().info("ButtonControl v" + getDescription().getVersion() + " wurde erfolgreich aktiviert!");
    }

    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");
            int length = Math.max(latestParts.length, currentParts.length);

            for (int i = 0; i < length; i++) {
                int latestPart = (i < latestParts.length) ? Integer.parseInt(latestParts[i]) : 0;
                int currentPart = (i < currentParts.length) ? Integer.parseInt(currentParts[i]) : 0;
                if (latestPart > currentPart) return true;
                if (latestPart < currentPart) return false;
            }
            return false;
        } catch (NumberFormatException e) {
            return !latest.equalsIgnoreCase(current);
        }
    }

    private void updateConfigWithDefaults() {
        if (!configManager.getConfig().contains("default-note")) {
            configManager.getConfig().set("default-note", "PIANO");
        }
        if (!configManager.getConfig().contains("double-note-enabled")) {
            configManager.getConfig().set("double-note-enabled", true);
        }
        if (!configManager.getConfig().contains("double-note-delay-ms")) {
            configManager.getConfig().set("double-note-delay-ms", 1000);
        }
        if (!configManager.getConfig().contains("motion-detection-radius")) {
            configManager.getConfig().set("motion-detection-radius", 5.0);
        }
        if (!configManager.getConfig().contains("motion-close-delay-ms")) {
            configManager.getConfig().set("motion-close-delay-ms", 5000);
        }
        configManager.saveConfig();
    }

    private void registerRecipes() {
    // --- 1. Dynamische Steuer-Buttons für JEDE Holz/Stein-Art ---
    for (Material mat : Material.values()) {
        if (mat.name().endsWith("_BUTTON")) {
            // Wir erstellen für jeden Button-Typ ein eigenes Ergebnis
            ItemStack controlButton = new ItemStack(mat);
            ItemMeta buttonMeta = controlButton.getItemMeta();
            if (buttonMeta != null) {
                buttonMeta.setDisplayName("§6Steuer-Button");
                List<String> lore = new ArrayList<>();
                lore.add("§7Ein universeller Controller für");
                lore.add("§7Türen, Lampen und mehr.");
                buttonMeta.setLore(lore);
                controlButton.setItemMeta(buttonMeta);
            }

            // Wir brauchen für jedes Material einen eindeutigen Key (z.B. control_button_oak_button)
            NamespacedKey key = new NamespacedKey(this, "control_" + mat.name().toLowerCase());
            if (Bukkit.getRecipe(key) != null) Bukkit.removeRecipe(key);

            ShapedRecipe recipe = new ShapedRecipe(key, controlButton);
            recipe.shape("123", "456", "789");
            // Das Material muss an allen drei Stellen gleich sein (z.B. 3x Eiche)
            recipe.setIngredient('2', mat);
            recipe.setIngredient('5', mat);
            recipe.setIngredient('8', mat);
            Bukkit.addRecipe(recipe);
        }
    }

    // --- 2. Steuer-Tageslichtsensor Rezept ---
    ItemStack controlDaylight = new ItemStack(Material.DAYLIGHT_DETECTOR);
    ItemMeta daylightMeta = controlDaylight.getItemMeta();
    if (daylightMeta != null) {
        daylightMeta.setDisplayName("§6Steuer-Tageslichtsensor");
        controlDaylight.setItemMeta(daylightMeta);
    }
    NamespacedKey daylightKey = new NamespacedKey(this, "control_daylight");
    if (Bukkit.getRecipe(daylightKey) != null) Bukkit.removeRecipe(daylightKey);
    ShapedRecipe daylightRecipe = new ShapedRecipe(daylightKey, controlDaylight);
    daylightRecipe.shape("123", "456", "789");
    daylightRecipe.setIngredient('2', Material.DAYLIGHT_DETECTOR);
    daylightRecipe.setIngredient('5', Material.DAYLIGHT_DETECTOR);
    daylightRecipe.setIngredient('8', Material.DAYLIGHT_DETECTOR);
    Bukkit.addRecipe(daylightRecipe);

    // --- 3. Steuer-Notenblock Rezept ---
    ItemStack controlNoteBlock = new ItemStack(Material.NOTE_BLOCK);
    ItemMeta noteBlockMeta = controlNoteBlock.getItemMeta();
    if (noteBlockMeta != null) {
        noteBlockMeta.setDisplayName("§6Steuer-Notenblock");
        controlNoteBlock.setItemMeta(noteBlockMeta);
    }
    NamespacedKey noteBlockKey = new NamespacedKey(this, "control_noteblock");
    if (Bukkit.getRecipe(noteBlockKey) != null) Bukkit.removeRecipe(noteBlockKey);
    ShapedRecipe noteBlockRecipe = new ShapedRecipe(noteBlockKey, controlNoteBlock);
    noteBlockRecipe.shape("123", "456", "789");
    noteBlockRecipe.setIngredient('2', Material.NOTE_BLOCK);
    noteBlockRecipe.setIngredient('5', Material.NOTE_BLOCK);
    noteBlockRecipe.setIngredient('8', Material.NOTE_BLOCK);
    Bukkit.addRecipe(noteBlockRecipe);

    // --- 4. Steuer-Bewegungsmelder Rezept ---
    ItemStack controlMotion = new ItemStack(Material.TRIPWIRE_HOOK);
    ItemMeta motionMeta = controlMotion.getItemMeta();
    if (motionMeta != null) {
        motionMeta.setDisplayName("§6Steuer-Bewegungsmelder");
        controlMotion.setItemMeta(motionMeta);
    }
    NamespacedKey motionKey = new NamespacedKey(this, "control_motion");
    if (Bukkit.getRecipe(motionKey) != null) Bukkit.removeRecipe(motionKey);
    ShapedRecipe motionRecipe = new ShapedRecipe(motionKey, controlMotion);
    motionRecipe.shape("123", "456", "789");
    motionRecipe.setIngredient('2', Material.TRIPWIRE_HOOK);
    motionRecipe.setIngredient('5', Material.TRIPWIRE_HOOK);
    motionRecipe.setIngredient('8', Material.TRIPWIRE_HOOK);
    Bukkit.addRecipe(motionRecipe);
}

    public void checkDaylightSensors() {
        List<String> allControllers = dataManager.getAllPlacedControllers();
        for (String controllerLoc : allControllers) {
            String buttonId = dataManager.getButtonIdForPlacedController(controllerLoc);
            if (buttonId == null) continue;

            Location loc = parseLocation(controllerLoc);
            if (loc == null) continue;

            Block block = loc.getBlock();
            if (block.getType() != Material.DAYLIGHT_DETECTOR) continue;

            long time = loc.getWorld().getTime();
            boolean isDay = time >= 0 && time < 13000;

            List<String> connectedBlocks = dataManager.getConnectedBlocks(buttonId);
            if (connectedBlocks == null) continue;

            for (String targetLocStr : connectedBlocks) {
                Location targetLoc = parseLocation(targetLocStr);
                if (targetLoc == null) continue;

                Block targetBlock = targetLoc.getBlock();
                if (targetBlock.getType() == Material.REDSTONE_LAMP) {
                    Lightable lamp = (Lightable) targetBlock.getBlockData();
                    lamp.setLit(!isDay);
                    targetBlock.setBlockData(lamp);
                }
            }
        }
    }

    public void checkMotionSensors() {
        long now = System.currentTimeMillis();
        List<String> allControllers = dataManager.getAllPlacedControllers();
        for (String controllerLoc : allControllers) {
            Location loc = parseLocation(controllerLoc);
            if (loc == null) continue;

            Block block = loc.getBlock();
            if (block.getType() != Material.TRIPWIRE_HOOK) continue;

            String buttonId = dataManager.getButtonIdForPlacedController(controllerLoc);
            if (buttonId == null) continue;

            double radius = dataManager.getMotionSensorRadius(controllerLoc);
            if (radius == -1) radius = configManager.getConfig().getDouble("motion-detection-radius", 5.0);
            
            long delay = dataManager.getMotionSensorDelay(controllerLoc);
            if (delay == -1) delay = configManager.getConfig().getLong("motion-close-delay-ms", 5000L);

            boolean detected = !loc.getWorld().getNearbyEntities(loc, radius, radius, radius, e -> e instanceof Player).isEmpty();
            List<String> connectedBlocks = dataManager.getConnectedBlocks(buttonId);
            
            if (connectedBlocks == null || connectedBlocks.isEmpty()) continue;

            if (detected) {
                setOpenables(connectedBlocks, true);
                lastMotionDetections.put(controllerLoc, now);
            } else {
                Long last = lastMotionDetections.get(controllerLoc);
                if (last != null && now - last >= delay) {
                    setOpenables(connectedBlocks, false);
                    lastMotionDetections.remove(controllerLoc);
                }
            }
        }
    }

    private void setOpenables(List<String> connectedBlocks, boolean open) {
        for (String targetLocStr : connectedBlocks) {
            Location targetLoc = parseLocation(targetLocStr);
            if (targetLoc == null) continue;

            Block targetBlock = targetLoc.getBlock();
            if (targetBlock.getBlockData() instanceof org.bukkit.block.data.Openable) {
                org.bukkit.block.data.Openable openable = (org.bukkit.block.data.Openable) targetBlock.getBlockData();
                openable.setOpen(open);
                targetBlock.setBlockData(openable);
            }
        }
    }

    private Location parseLocation(String locStr) {
        String[] parts = locStr.split(",");
        if (parts.length != 4) return null;
        World world = getServer().getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void playDoorbellSound(Location loc, String instrument) {
        Block block = loc.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) return;

        NoteBlock noteBlock = (NoteBlock) block.getBlockData();
        try {
            org.bukkit.Instrument bukkitInstrument = org.bukkit.Instrument.valueOf(instrument.toUpperCase());
            noteBlock.setInstrument(bukkitInstrument);
            noteBlock.setNote(new Note(0, Tone.C, false));
            block.setBlockData(noteBlock);
            loc.getWorld().playSound(loc, bukkitInstrument.getSound(), 1.0f, 1.0f);

            if (configManager.getConfig().getBoolean("double-note-enabled", true)) {
                int delayMs = configManager.getConfig().getInt("double-note-delay-ms", 1000);
                long delayTicks = (long) (delayMs / 50.0);
                getServer().getScheduler().runTaskLater(this, () -> {
                    if (block.getType() == Material.NOTE_BLOCK) {
                        loc.getWorld().playSound(loc, bukkitInstrument.getSound(), 1.0f, 1.0f);
                    }
                }, delayTicks);
            }
        } catch (IllegalArgumentException e) {
            getLogger().warning("Ungültiges Instrument: " + instrument);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("bc")) return false;
        
        if (args.length == 0) {
            sender.sendMessage("§6[ButtonControl] §7Verwende: /bc <info|reload|note|trust|untrust|public|private>");
            return true;
        }

        // --- INFO BEFEHL ---
        if (args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("§6§lButtonControl §7- v" + getDescription().getVersion());
            sender.sendMessage("§eAuthor: §fM_Viper");
            sender.sendMessage("§eFeatures: §fTüren, Lampen, Notenblöcke, Sensoren");
            return true;
        }

        // --- RELOAD BEFEHL ---
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("buttoncontrol.reload")) {
                sender.sendMessage(configManager.getMessage("keine-berechtigung"));
                return true;
            }
            configManager.reloadConfig();
            updateConfigWithDefaults();
            dataManager.reloadData();
            sender.sendMessage(configManager.getMessage("konfiguration-neugeladen"));
            return true;
        }

        // --- NOTE BEFEHL ---
        if (args[0].equalsIgnoreCase("note") && sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length < 2) {
                player.sendMessage("§6[ButtonControl] §7Verwende: /bc note <Instrument>");
                return true;
            }
            try {
                org.bukkit.Instrument.valueOf(args[1].toUpperCase());
                dataManager.setPlayerInstrument(player.getUniqueId(), args[1].toUpperCase());
                player.sendMessage(String.format(configManager.getMessage("instrument-gesetzt"), args[1].toUpperCase()));
            } catch (Exception e) {
                player.sendMessage(configManager.getMessage("ungueltiges-instrument"));
            }
            return true;
        }

        // --- TRUST / PUBLIC / PRIVATE SYSTEM ---
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust") || 
                args[0].equalsIgnoreCase("public") || args[0].equalsIgnoreCase("private")) {
                
                Block target = player.getTargetBlockExact(5);
                
                // Erkennt nun Stein- und alle Holzbuttons sowie Sensoren
                if (target == null || (!target.getType().name().endsWith("_BUTTON") && 
                    target.getType() != Material.DAYLIGHT_DETECTOR && 
                    target.getType() != Material.TRIPWIRE_HOOK)) {
                    
                    player.sendMessage(configManager.getMessage("kein-controller-im-blick"));
                    return true;
                }

                String targetLoc = target.getWorld().getName() + "," + target.getX() + "," + target.getY() + "," + target.getZ();
                String buttonId = dataManager.getButtonIdForLocation(targetLoc);

                if (buttonId == null) {
                    player.sendMessage(configManager.getMessage("keine-bloecke-verbunden"));
                    return true;
                }

                if (!dataManager.isOwner(buttonId, player.getUniqueId())) {
                    player.sendMessage(configManager.getMessage("nur-besitzer-abbauen"));
                    return true;
                }

                // Spieler hinzufügen
                if (args[0].equalsIgnoreCase("trust")) {
                    if (args.length < 2) {
                        player.sendMessage("§6[ButtonControl] §7Verwende: /bc trust <Spieler>");
                        return true;
                    }
                    Player targetPlayer = Bukkit.getPlayer(args[1]);
                    if (targetPlayer == null) {
                        player.sendMessage(configManager.getMessage("spieler-nicht-gefunden"));
                        return true;
                    }
                    dataManager.addTrustedPlayer(buttonId, targetPlayer.getUniqueId());
                    player.sendMessage(String.format(configManager.getMessage("trust-hinzugefuegt"), targetPlayer.getName()));
                } 
                // Spieler entfernen
                else if (args[0].equalsIgnoreCase("untrust")) {
                    if (args.length < 2) {
                        player.sendMessage("§6[ButtonControl] §7Verwende: /bc untrust <Spieler>");
                        return true;
                    }
                    UUID targetUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                    dataManager.removeTrustedPlayer(buttonId, targetUUID);
                    player.sendMessage(String.format(configManager.getMessage("trust-entfernt"), args[1]));
                } 
                // Status umschalten (Public) oder erzwingen (Private)
                else if (args[0].equalsIgnoreCase("public") || args[0].equalsIgnoreCase("private")) {
                    boolean newState = args[0].equalsIgnoreCase("public") ? !dataManager.isPublic(buttonId) : false;
                    dataManager.setPublic(buttonId, newState);
                    String statusColor = newState ? "§aÖffentlich" : "§cPrivat";
                    player.sendMessage(String.format(configManager.getMessage("status-geandert"), statusColor));
                }
                return true;
            }
        }
        return true;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}