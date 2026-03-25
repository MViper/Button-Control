package viper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ButtonListener implements Listener {
    private final ButtonControl plugin;
    private final ConfigManager configManager;
    private final DataManager dataManager;

    public ButtonListener(ButtonControl plugin, ConfigManager configManager, DataManager dataManager) {
        this.plugin        = plugin;
        this.configManager = configManager;
        this.dataManager   = dataManager;
    }

    // -----------------------------------------------------------------------
    //  Interact – Benutzung + Verbinden
    // -----------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Doppelt-Feuern bei Items verhindern (Haupt- und Nebenhand)
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player    = event.getPlayer();
        UUID playerUUID  = player.getUniqueId();
        ItemStack item   = event.getItem();
        Block block      = event.getClickedBlock();

        // ── 1. Bereits platzierter Controller ──────────────────────────────
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null) {
            String blockLocation = plugin.toLoc(block);
            String buttonId      = dataManager.getButtonIdForLocation(blockLocation);

            if (buttonId != null) {
                // Admin-Bypass
                if (!dataManager.canAccess(buttonId, playerUUID)
                        && !player.hasPermission("buttoncontrol.admin")) {
                    player.sendMessage(configManager.getMessage("keine-berechtigung-controller"));
                    event.setCancelled(true);
                    return;
                }

                // Tripwire & Teppich → MotionSensorGUI öffnen
                if (block.getType() == Material.TRIPWIRE_HOOK
                        || block.getType().name().endsWith("_CARPET")) {
                    event.setCancelled(true);
                    new MotionSensorGUI(plugin, player, blockLocation, buttonId).open();
                    return;
                }

                // Schild → nur mit Shift+Klick Blöcke toggeln,
                //          normaler Klick öffnet den Schildeditor (Vanilla)
                if (block.getType().name().endsWith("_SIGN")
                        || block.getType().name().endsWith("_BUTTON")
                        || block.getType() == Material.DAYLIGHT_DETECTOR) {

                    if (player.isSneaking()) {
                        // Shift+Klick → Vanilla-Verhalten zulassen (Schildeditor etc.)
                        return;
                    }

                    event.setCancelled(true);
                    List<String> connectedBlocks = dataManager.getConnectedBlocks(buttonId);
                    boolean hasConnectedBlocks = connectedBlocks != null && !connectedBlocks.isEmpty();
                    boolean secretTriggered = plugin.triggerSecretWall(buttonId);

                    if (hasConnectedBlocks) {
                        toggleConnectedBlocks(player, playerUUID, connectedBlocks);
                    }

                    if (!hasConnectedBlocks && !secretTriggered) {
                        player.sendMessage(configManager.getMessage("keine-bloecke-verbunden"));
                    }
                }
                return;
            }
        }

        // ── 2. Verbinden mit Controller-Item in der Hand ───────────────────
        if (item == null || !item.hasItemMeta()) return;
        String displayName = item.getItemMeta().getDisplayName();
        if (!displayName.contains("§6Steuer-")) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) return;

        if (isInteractableTarget(block.getType())) {
            event.setCancelled(true);

            // Doppeltür: immer untersten Block speichern
            Block targetBlock = getBottomDoorBlock(block);

            ItemMeta meta     = item.getItemMeta();
            String buttonId   = extractButtonId(meta);
            if (buttonId == null) {
                buttonId = UUID.randomUUID().toString();
                updateButtonLore(item, buttonId);
            }

            List<String> connectedBlocks = dataManager.getConnectedBlocks(buttonId);
            if (connectedBlocks == null) connectedBlocks = new ArrayList<>();

            String targetLocStr = plugin.toLoc(targetBlock);
            if (connectedBlocks.contains(targetLocStr)) {
                player.sendMessage(configManager.getMessage("block-bereits-verbunden"));
                return;
            }

            if (checkLimits(player, targetBlock.getType(), connectedBlocks)) {
                connectedBlocks.add(targetLocStr);
                dataManager.setConnectedBlocks(playerUUID.toString(), buttonId, connectedBlocks);
                player.sendMessage(configManager.getMessage("block-verbunden"));
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Block-Break: Controller abbauen
    // -----------------------------------------------------------------------

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block        = event.getBlock();
        String blockLocation = plugin.toLoc(block);
        String buttonId    = dataManager.getButtonIdForLocation(blockLocation);

        if (buttonId != null) {
            if (!dataManager.isOwner(buttonId, event.getPlayer().getUniqueId())
                    && !event.getPlayer().hasPermission("buttoncontrol.admin")) {
                event.getPlayer().sendMessage(configManager.getMessage("nur-besitzer-abbauen"));
                event.setCancelled(true);
                return;
            }
            dataManager.removeController(blockLocation);
            event.getPlayer().sendMessage(configManager.getMessage("controller-entfernt"));
        }
    }

    // -----------------------------------------------------------------------
    //  Block-Break: Verbundener Block abgebaut → Eintrag bereinigen (NEU)
    // -----------------------------------------------------------------------

    @EventHandler
    public void onConnectedBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isInteractableTarget(block.getType())) return;

        // Bei Türen normalisieren auf Unterblock
        Block bottomBlock = getBottomDoorBlock(block);
        String locStr     = plugin.toLoc(bottomBlock);

        if (dataManager.removeFromAllConnectedBlocks(locStr)) {
            event.getPlayer().sendMessage(configManager.getMessage("block-verbindung-entfernt"));
        }
    }

    // -----------------------------------------------------------------------
    //  Controller platzieren
    // -----------------------------------------------------------------------

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getDisplayName().contains("§6Steuer-")) return;

        Block block   = event.getBlockPlaced();
        String buttonId = extractButtonId(item.getItemMeta());
        if (buttonId == null) {
            buttonId = UUID.randomUUID().toString();
            updateButtonLore(item, buttonId);
        }

        dataManager.registerController(
            plugin.toLoc(block), event.getPlayer().getUniqueId(), buttonId);
        event.getPlayer().sendMessage(configManager.getMessage("controller-platziert"));
    }

    // -----------------------------------------------------------------------
    //  Toggle-Logik
    // -----------------------------------------------------------------------

    private void toggleConnectedBlocks(Player player, UUID playerUUID, List<String> connectedBlocks) {
        boolean anyDoorOpened = false, anyDoorClosed = false;
        boolean anyGateOpened = false, anyGateClosed = false;
        boolean anyTrapOpened = false, anyTrapClosed = false;
        boolean anyIronDoorOpened = false, anyIronDoorClosed = false;
        boolean anyIronTrapOpened = false, anyIronTrapClosed = false;
        boolean anyLampOn = false, anyLampOff = false;
        boolean anyGrateOpened = false, anyGrateClosed = false;
        boolean anyCreakingHeartOn = false, anyCreakingHeartOff = false;
        boolean anyNoteBlockPlayed = false;
        boolean anyBellPlayed = false;
        boolean anyDispenserTriggered = false;
        boolean anyDropperTriggered = false;

        boolean soundsEnabled = configManager.getConfig().getBoolean("sounds.enabled", true);

        for (String locStr : connectedBlocks) {
            Location location = parseLocation(locStr);
            if (location == null) continue;
            Block targetBlock = location.getBlock();
            Material mat = targetBlock.getType();

            // ── Eisentür (NEU) ──────────────────────────────────────────────
            // Eisentüren implementieren Openable in der Bukkit-API.
            // Wir setzen den Zustand direkt – kein Redstone-Signal nötig.
            if (mat == Material.IRON_DOOR) {
                if (targetBlock.getBlockData() instanceof Openable) {
                    Openable op = (Openable) targetBlock.getBlockData();
                    boolean wasOpen = op.isOpen();
                    op.setOpen(!wasOpen);
                    targetBlock.setBlockData(op);
                    if (soundsEnabled) {
                        String soundKey = wasOpen ? "sounds.iron-door-close" : "sounds.iron-door-open";
                        playConfigSound(location, soundKey,
                            wasOpen ? "BLOCK_IRON_DOOR_CLOSE" : "BLOCK_IRON_DOOR_OPEN");
                    }
                    if (!wasOpen) anyIronDoorOpened = true; else anyIronDoorClosed = true;
                }
                continue;
            }

            // ── Eisenfalltür (NEU) ─────────────────────────────────────────
            if (mat == Material.IRON_TRAPDOOR) {
                if (targetBlock.getBlockData() instanceof Openable) {
                    Openable op = (Openable) targetBlock.getBlockData();
                    boolean wasOpen = op.isOpen();
                    op.setOpen(!wasOpen);
                    targetBlock.setBlockData(op);
                    if (soundsEnabled) {
                        String soundKey = wasOpen ? "sounds.iron-door-close" : "sounds.iron-door-open";
                        playConfigSound(location, soundKey,
                            wasOpen ? "BLOCK_IRON_TRAPDOOR_CLOSE" : "BLOCK_IRON_TRAPDOOR_OPEN");
                    }
                    if (!wasOpen) anyIronTrapOpened = true; else anyIronTrapClosed = true;
                }
                continue;
            }

            // ── Holztür / Zauntore / Falltüren ────────────────────────────
            if (isDoor(mat) || isGate(mat) || isTrapdoor(mat)) {
                if (targetBlock.getBlockData() instanceof Openable) {
                    Openable op = (Openable) targetBlock.getBlockData();
                    boolean wasOpen = op.isOpen();
                    op.setOpen(!wasOpen);
                    targetBlock.setBlockData(op);

                    if (soundsEnabled) {
                        String soundKey = wasOpen ? "sounds.door-close" : "sounds.door-open";
                        String fallback = wasOpen ? "BLOCK_WOODEN_DOOR_CLOSE" : "BLOCK_WOODEN_DOOR_OPEN";
                        playConfigSound(location, soundKey, fallback);
                    }

                    if (isDoor(mat))      { if (!wasOpen) anyDoorOpened = true; else anyDoorClosed = true; }
                    else if (isGate(mat)) { if (!wasOpen) anyGateOpened = true; else anyGateClosed = true; }
                    else                  { if (!wasOpen) anyTrapOpened = true; else anyTrapClosed = true; }
                }
            }
            // ── Lampen (Redstone + Kupferlampen) ─────────────────────────
            else if (isLamp(mat)) {
                if (targetBlock.getBlockData() instanceof Lightable) {
                    Lightable lamp = (Lightable) targetBlock.getBlockData();
                    boolean wasLit = lamp.isLit();
                    lamp.setLit(!wasLit);
                    targetBlock.setBlockData(lamp);
                    if (soundsEnabled) {
                        playConfigSound(location,
                            wasLit ? "sounds.lamp-off" : "sounds.lamp-on",
                            "BLOCK_LEVER_CLICK");
                    }
                    if (!wasLit) anyLampOn = true; else anyLampOff = true;
                }
            }
            // ── Gitter (alle *_GRATE + Eisenstangen) ─────────────────────
            else if (plugin.isGrate(mat) || (mat == Material.AIR && plugin.isManagedOpenGrateLocation(locStr))) {
                Boolean nowOpen = plugin.toggleGrate(targetBlock);
                if (nowOpen != null) {
                    if (nowOpen) anyGrateOpened = true;
                    else anyGrateClosed = true;
                }
            }
            // ── Notenblock ────────────────────────────────────────────────
            else if (mat == Material.NOTE_BLOCK) {
                String instrument = dataManager.getPlayerInstrument(playerUUID);
                if (instrument == null)
                    instrument = configManager.getConfig().getString("default-note", "PIANO");
                plugin.playDoorbellSound(location, instrument);
                anyNoteBlockPlayed = true;
            }
            // ── Glocke ───────────────────────────────────────────────────
            else if (mat == Material.BELL) {
                targetBlock.getWorld().playSound(location, Sound.BLOCK_BELL_USE, 3.0f, 1.0f);
                anyBellPlayed = true;
            }
            // ── Spender / Werfer ──────────────────────────────────────────
            else if (mat == Material.DISPENSER) {
                if (triggerContainer(targetBlock, "dispense")) {
                    targetBlock.getWorld().playSound(location, Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                    anyDispenserTriggered = true;
                }
            }
            else if (mat == Material.DROPPER) {
                if (triggerContainer(targetBlock, "drop")) {
                    targetBlock.getWorld().playSound(location, Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                    anyDropperTriggered = true;
                }
            }
            // ── Creaking Heart ────────────────────────────────────────────
            else if (isCreakingHeart(mat)) {
                Boolean nowActive = plugin.togglePersistentCreakingHeart(targetBlock);
                if (nowActive != null) {
                    if (nowActive) anyCreakingHeartOn = true;
                    else anyCreakingHeartOff = true;
                }
            }
        }

        // Feedback-Nachrichten
        if (anyDoorOpened)      player.sendMessage(configManager.getMessage("tueren-geoeffnet"));
        if (anyDoorClosed)      player.sendMessage(configManager.getMessage("tueren-geschlossen"));
        if (anyIronDoorOpened)  player.sendMessage(configManager.getMessage("eisentueren-geoeffnet"));
        if (anyIronDoorClosed)  player.sendMessage(configManager.getMessage("eisentueren-geschlossen"));
        if (anyIronTrapOpened)  player.sendMessage(configManager.getMessage("eisenfallturen-geoeffnet"));
        if (anyIronTrapClosed)  player.sendMessage(configManager.getMessage("eisenfallturen-geschlossen"));
        if (anyGateOpened)      player.sendMessage(configManager.getMessage("gates-geoeffnet"));
        if (anyGateClosed)      player.sendMessage(configManager.getMessage("gates-geschlossen"));
        if (anyTrapOpened)      player.sendMessage(configManager.getMessage("fallturen-geoeffnet"));
        if (anyTrapClosed)      player.sendMessage(configManager.getMessage("fallturen-geschlossen"));
        if (anyLampOn)          player.sendMessage(configManager.getMessage("lampen-eingeschaltet"));
        if (anyLampOff)         player.sendMessage(configManager.getMessage("lampen-ausgeschaltet"));
        if (anyGrateOpened)     player.sendMessage(configManager.getMessage("gitter-geoeffnet"));
        if (anyGrateClosed)     player.sendMessage(configManager.getMessage("gitter-geschlossen"));
        if (anyCreakingHeartOn) player.sendMessage(configManager.getMessage("creaking-heart-aktiviert"));
        if (anyCreakingHeartOff) player.sendMessage(configManager.getMessage("creaking-heart-deaktiviert"));
        if (anyNoteBlockPlayed) player.sendMessage(configManager.getMessage("notenblock-ausgeloest"));
        if (anyBellPlayed)      player.sendMessage(configManager.getMessage("glocke-gelaeutet"));
        if (anyDispenserTriggered) player.sendMessage(configManager.getMessage("spender-ausgeloest"));
        if (anyDropperTriggered)   player.sendMessage(configManager.getMessage("werfer-ausgeloest"));
    }

    /**
     * Spielt einen Sound ab dessen Name aus der config.yml gelesen wird.
     * Ist der Key nicht gesetzt oder der Sound-Name ungültig, wird der Fallback verwendet.
     */
    private void playConfigSound(Location loc, String configKey, String fallback) {
        String soundName = configManager.getConfig().getString(configKey, fallback);
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            loc.getWorld().playSound(loc, sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            try {
                Sound sound = Sound.valueOf(fallback.toUpperCase());
                loc.getWorld().playSound(loc, sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) { }
        }
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
    //  Limits
    // -----------------------------------------------------------------------

    private boolean checkLimits(Player player, Material type, List<String> connected) {
        if (type == Material.IRON_DOOR) {
            if (connected.stream().filter(l -> getMaterialAt(l) == Material.IRON_DOOR).count()
                    >= configManager.getMaxDoors()) {
                player.sendMessage(configManager.getMessage("max-tueren-erreicht")); return false;
            }
        } else if (type == Material.IRON_TRAPDOOR) {
            if (connected.stream().filter(l -> getMaterialAt(l) == Material.IRON_TRAPDOOR).count()
                    >= configManager.getMaxTrapdoors()) {
                player.sendMessage(configManager.getMessage("max-fallturen-erreicht")); return false;
            }
        } else if (isDoor(type)) {
            if (connected.stream().filter(l -> isDoor(getMaterialAt(l))).count()
                    >= configManager.getMaxDoors()) {
                player.sendMessage(configManager.getMessage("max-tueren-erreicht")); return false;
            }
        } else if (isGate(type)) {
            if (connected.stream().filter(l -> isGate(getMaterialAt(l))).count()
                    >= configManager.getMaxGates()) {
                player.sendMessage(configManager.getMessage("max-gates-erreicht")); return false;
            }
        } else if (isTrapdoor(type)) {
            if (connected.stream().filter(l -> isTrapdoor(getMaterialAt(l))).count()
                    >= configManager.getMaxTrapdoors()) {
                player.sendMessage(configManager.getMessage("max-fallturen-erreicht")); return false;
            }
        } else if (isLamp(type)) {
            if (connected.stream().filter(l -> isLamp(getMaterialAt(l))).count()
                    >= configManager.getMaxLamps()) {
                player.sendMessage(configManager.getMessage("max-lampen-erreicht")); return false;
            }
        } else if (type == Material.NOTE_BLOCK) {
            if (connected.stream().filter(l -> getMaterialAt(l) == Material.NOTE_BLOCK).count()
                    >= configManager.getMaxNoteBlocks()) {
                player.sendMessage(configManager.getMessage("max-notenbloecke-erreicht")); return false;
            }
        } else if (type == Material.BELL) {
            if (connected.stream().filter(l -> getMaterialAt(l) == Material.BELL).count()
                    >= configManager.getMaxBells()) {
                player.sendMessage(configManager.getMessage("max-glocken-erreicht")); return false;
            }
        } else if (type == Material.DISPENSER) {
            if (connected.stream().filter(l -> getMaterialAt(l) == Material.DISPENSER).count()
                    >= configManager.getMaxDispensers()) {
                player.sendMessage(configManager.getMessage("max-spender-erreicht")); return false;
            }
        } else if (type == Material.DROPPER) {
            if (connected.stream().filter(l -> getMaterialAt(l) == Material.DROPPER).count()
                    >= configManager.getMaxDroppers()) {
                player.sendMessage(configManager.getMessage("max-werfer-erreicht")); return false;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    //  Hilfsmethoden
    // -----------------------------------------------------------------------

    /** Gibt bei zweiteiligen Türen immer den untersten Block zurück. */
    private Block getBottomDoorBlock(Block block) {
        Material mat = block.getType();
        if (!isDoor(mat) && mat != Material.IRON_DOOR) return block;
        if (block.getBlockData() instanceof Bisected) {
            Bisected b = (Bisected) block.getBlockData();
            if (b.getHalf() == Bisected.Half.TOP) return block.getRelative(0, -1, 0);
        }
        return block;
    }

    private String extractButtonId(ItemMeta meta) {
        if (meta == null || !meta.hasLore() || meta.getLore().isEmpty()) return null;
        String first = meta.getLore().get(0);
        return first.startsWith("§8ID: ") ? first.replace("§8ID: ", "") : null;
    }

    private void updateButtonLore(ItemStack item, String buttonId) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§8ID: " + buttonId);
            lore.add("§7Ein universeller Controller für");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private boolean isButton(Material m)    { return m.name().endsWith("_BUTTON"); }
    private boolean isDoor(Material m)      { return m.name().endsWith("_DOOR") && m != Material.IRON_DOOR; }
    private boolean isGate(Material m)      { return m.name().endsWith("_FENCE_GATE"); }
    private boolean isTrapdoor(Material m)  { return m.name().endsWith("_TRAPDOOR") && m != Material.IRON_TRAPDOOR; }
    private boolean isLamp(Material m)      {
        return m == Material.REDSTONE_LAMP
            || "COPPER_BULB".equals(m.name())
            || m.name().endsWith("_COPPER_BULB");
    }
    private boolean isCreakingHeart(Material m) { return "CREAKING_HEART".equals(m.name()); }

    private boolean isInteractableTarget(Material m) {
        return isDoor(m) || isGate(m) || isTrapdoor(m)
            || m == Material.IRON_DOOR || m == Material.IRON_TRAPDOOR
            || isLamp(m) || plugin.isGrate(m)
            || m == Material.NOTE_BLOCK || m == Material.BELL
            || m == Material.DISPENSER || m == Material.DROPPER
            || isCreakingHeart(m);
    }

    private Material getMaterialAt(String locString) {
        Location l = parseLocation(locString);
        return l != null ? l.getBlock().getType() : Material.AIR;
    }

    private Location parseLocation(String s) {
        String[] p = s.split(",");
        if (p.length != 4) return null;
        try {
            return new Location(Bukkit.getWorld(p[0]),
                Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (Exception e) { return null; }
    }
}