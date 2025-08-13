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
        configManager = new ConfigManager(this);
        dataManager = new DataManager(this);

        // Spigot Update Checker starten
        new UpdateChecker(this, 127702).getVersion(version -> {
            String currentVersion = this.getDescription().getVersion();
            String normalizedLatest = version.replaceFirst("(?i)^(version\\s*|v\\.?\\s*)", "").trim();
            String normalizedCurrent = currentVersion.replaceFirst("(?i)^(version\\s*|v\\.?\\s*)", "").trim();

            if (isNewerVersion(normalizedLatest, normalizedCurrent)) {
                getLogger().info("Neue Version verfügbar: " + version);
                getLogger().info("Download: https://www.spigotmc.org/resources/buttoncontrol.127702/");
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> p.hasPermission("buttoncontrol.update"))
                            .forEach(p -> {
                                p.sendMessage("§6[ButtonControl] §eEine neue Version ist verfügbar: §f" + version);
                                p.sendMessage("§6[ButtonControl] §eDownload: §fhttps://www.spigotmc.org/resources/buttoncontrol.127702/");
                            });
                });
            } else {
                getLogger().info("Keine neue Version verfügbar.");
            }
        });

        // Listener für Spieler-Joins
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                Player player = event.getPlayer();
                if (!player.hasPermission("buttoncontrol.update")) return;
                new UpdateChecker(ButtonControl.this, 127702).getVersion(version -> {
                    String currentVersion = getDescription().getVersion();
                    String normalizedLatest = version.replaceFirst("(?i)^(version\\s*|v\\.?\\s*)", "").trim();
                    String normalizedCurrent = currentVersion.replaceFirst("(?i)^(version\\s*|v\\.?\\s*)", "").trim();

                    if (isNewerVersion(normalizedLatest, normalizedCurrent)) {
                        player.sendMessage("§6[ButtonControl] §eEine neue Version ist verfügbar: §f" + version);
                        player.sendMessage("§6[ButtonControl] §eDownload: §fhttps://www.spigotmc.org/resources/buttoncontrol.127702/");
                    }
                });
            }
        }, this);

        updateConfigWithDefaults();
        getServer().getPluginManager().registerEvents(new ButtonListener(this, configManager, dataManager), this);
        registerRecipes();
        getServer().getScheduler().runTaskTimer(this, this::checkDaylightSensors, 0L, 20L * 10);
        getServer().getScheduler().runTaskTimer(this, this::checkMotionSensors, 0L, 10L);
        MetricsHandler.startMetrics(this);
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
        ItemStack controlButton = new ItemStack(Material.STONE_BUTTON);
        ItemMeta buttonMeta = controlButton.getItemMeta();
        buttonMeta.setDisplayName("§6Steuer-Button");
        controlButton.setItemMeta(buttonMeta);

        NamespacedKey buttonKey = new NamespacedKey(this, "control_button");
        ShapedRecipe buttonRecipe = new ShapedRecipe(buttonKey, controlButton);
        buttonRecipe.shape("123", "456", "789");
        buttonRecipe.setIngredient('2', Material.STONE_BUTTON);
        buttonRecipe.setIngredient('5', Material.STONE_BUTTON);
        buttonRecipe.setIngredient('8', Material.STONE_BUTTON);
        Bukkit.addRecipe(buttonRecipe);

        ItemStack controlDaylight = new ItemStack(Material.DAYLIGHT_DETECTOR);
        ItemMeta daylightMeta = controlDaylight.getItemMeta();
        daylightMeta.setDisplayName("§6Steuer-Tageslichtsensor");
        controlDaylight.setItemMeta(daylightMeta);

        NamespacedKey daylightKey = new NamespacedKey(this, "control_daylight");
        ShapedRecipe daylightRecipe = new ShapedRecipe(daylightKey, controlDaylight);
        daylightRecipe.shape("123", "456", "789");
        daylightRecipe.setIngredient('2', Material.DAYLIGHT_DETECTOR);
        daylightRecipe.setIngredient('5', Material.DAYLIGHT_DETECTOR);
        daylightRecipe.setIngredient('8', Material.DAYLIGHT_DETECTOR);
        Bukkit.addRecipe(daylightRecipe);

        ItemStack controlNoteBlock = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta noteBlockMeta = controlNoteBlock.getItemMeta();
        noteBlockMeta.setDisplayName("§6Steuer-Notenblock");
        controlNoteBlock.setItemMeta(noteBlockMeta);

        NamespacedKey noteBlockKey = new NamespacedKey(this, "control_noteblock");
        ShapedRecipe noteBlockRecipe = new ShapedRecipe(noteBlockKey, controlNoteBlock);
        noteBlockRecipe.shape("123", "456", "789");
        noteBlockRecipe.setIngredient('2', Material.NOTE_BLOCK);
        noteBlockRecipe.setIngredient('5', Material.NOTE_BLOCK);
        noteBlockRecipe.setIngredient('8', Material.NOTE_BLOCK);
        Bukkit.addRecipe(noteBlockRecipe);

        ItemStack controlMotion = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta motionMeta = controlMotion.getItemMeta();
        motionMeta.setDisplayName("§6Steuer-Bewegungsmelder");
        controlMotion.setItemMeta(motionMeta);

        NamespacedKey motionKey = new NamespacedKey(this, "control_motion");
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

            String[] parts = controllerLoc.split(",");
            if (parts.length != 4) continue;

            World world = getServer().getWorld(parts[0]);
            if (world == null) continue;

            Location loc = new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));

            Block block = loc.getBlock();
            if (block.getType() != Material.DAYLIGHT_DETECTOR) continue;

            long time = loc.getWorld().getTime();
            boolean isDay = time >= 0 && time < 13000;

            List<String> connectedBlocks = dataManager.getConnectedBlocks(buttonId);
            if (connectedBlocks == null) continue;

            for (String targetLocStr : connectedBlocks) {
                String[] targetParts = targetLocStr.split(",");
                if (targetParts.length != 4) continue;

                World targetWorld = getServer().getWorld(targetParts[0]);
                if (targetWorld == null) continue;

                Location targetLoc = new Location(targetWorld,
                        Integer.parseInt(targetParts[1]),
                        Integer.parseInt(targetParts[2]),
                        Integer.parseInt(targetParts[3]));

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
            String[] parts = controllerLoc.split(",");
            if (parts.length != 4) continue;

            World world = getServer().getWorld(parts[0]);
            if (world == null) continue;

            Location loc = new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));

            Block block = loc.getBlock();
            if (block.getType() != Material.TRIPWIRE_HOOK) continue;

            String buttonId = dataManager.getButtonIdForPlacedController(controllerLoc);
            if (buttonId == null) continue;

            // Individuelle Einstellungen für diesen Bewegungsmelder
            double radius = dataManager.getMotionSensorRadius(controllerLoc);
            if (radius == -1) radius = configManager.getConfig().getDouble("motion-detection-radius", 5.0);
            long delay = dataManager.getMotionSensorDelay(controllerLoc);
            if (delay == -1) delay = configManager.getConfig().getLong("motion-close-delay-ms", 5000L);

            boolean detected = !world.getNearbyEntities(loc, radius, radius, radius, e -> e instanceof Player).isEmpty();

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
            String[] targetParts = targetLocStr.split(",");
            if (targetParts.length != 4) continue;

            World targetWorld = getServer().getWorld(targetParts[0]);
            if (targetWorld == null) continue;

            Location targetLoc = new Location(targetWorld,
                    Integer.parseInt(targetParts[1]),
                    Integer.parseInt(targetParts[2]),
                    Integer.parseInt(targetParts[3]));

            Block targetBlock = targetLoc.getBlock();

            if (targetBlock.getBlockData() instanceof org.bukkit.block.data.Openable) {
                org.bukkit.block.data.Openable openable = (org.bukkit.block.data.Openable) targetBlock.getBlockData();
                openable.setOpen(open);
                targetBlock.setBlockData(openable);
            }
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
        if (command.getName().equalsIgnoreCase("bc")) {
            if (args.length == 0) {
                sender.sendMessage("§6[ButtonControl] §7Verwende: /bc <info|reload|note>");
                return true;
            }

            if (args[0].equalsIgnoreCase("info")) {
                sender.sendMessage("§6[ButtonControl] §7Informationen zum Plugin:");
                sender.sendMessage("§eVersion: §f" + getDescription().getVersion());
                sender.sendMessage("§eErsteller: §fM_Viper");
                sender.sendMessage("§ePlugin: §fButtonControl");
                sender.sendMessage("§eGetestet für Minecraft: §f1.21.5 - 1.21.8");
                sender.sendMessage("§eWeitere Infos: §fTüren, Lampen & Notenblöcke mit Buttons oder Tageslichtsensoren steuern");
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("buttoncontrol.reload")) {
                    sender.sendMessage(configManager.getMessage("keine-berechtigung"));
                    return true;
                }
                configManager.reloadConfig();
                updateConfigWithDefaults();
                dataManager.reloadData();
                sender.sendMessage(configManager.getMessage("konfiguration-reloaded"));
                return true;
            }

            if (args[0].equalsIgnoreCase("note") && sender instanceof Player) {
                Player player = (Player) sender;
                if (!player.hasPermission("buttoncontrol.note")) {
                    player.sendMessage(configManager.getMessage("keine-berechtigung"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§6[ButtonControl] §7Verwende: /bc note <Instrument>");
                    sender.sendMessage("§7Verfügbare Instrumente: PIANO, BASS_DRUM, SNARE, STICKS, BASS_GUITAR, FLUTE, BELL, GUITAR, CHIME, XYLOPHONE, etc.");
                    return true;
                }

                String instrument = args[1].toUpperCase();
                try {
                    org.bukkit.Instrument.valueOf(instrument);
                    dataManager.setPlayerInstrument(player.getUniqueId(), instrument);
                    sender.sendMessage(String.format(configManager.getMessage("instrument-gesetzt"), instrument));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(configManager.getMessage("ungueltiges-instrument"));
                }
                return true;
            }
        }
        return false;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}