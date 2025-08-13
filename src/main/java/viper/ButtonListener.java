package viper;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Lightable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        String playerUUID = event.getPlayer().getUniqueId().toString();
        ItemStack item = event.getItem();
        Block block = event.getClickedBlock();

        // Block wird gesteuert (Button, Tageslichtsensor oder Bewegungsmelder)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null) {
            String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
            String buttonId = dataManager.getButtonIdForPlacedController(playerUUID, blockLocation);

            if (buttonId != null) {
                event.setCancelled(true);
                // Bewegungsmelder: GUI öffnen
                if (block.getType() == Material.TRIPWIRE_HOOK) {
                    new MotionSensorGUI(plugin, event.getPlayer(), blockLocation, buttonId).open();
                    return;
                }
                // Button oder Tageslichtsensor: Normale Steuerung
                if (block.getType() == Material.STONE_BUTTON || block.getType() == Material.DAYLIGHT_DETECTOR) {
                    List<String> connectedBlocks = dataManager.getConnectedBlocks(playerUUID, buttonId);
                    if (connectedBlocks != null && !connectedBlocks.isEmpty()) {
                        boolean anyDoorOpened = false;
                        boolean anyDoorClosed = false;
                        boolean anyGateOpened = false;
                        boolean anyGateClosed = false;
                        boolean anyTrapOpened = false;
                        boolean anyTrapClosed = false;
                        boolean anyLampOn = false;
                        boolean anyLampOff = false;
                        boolean anyNoteBlockPlayed = false;
                        boolean anyBellPlayed = false;

                        for (String loc : connectedBlocks) {
                            String[] parts = loc.split(",");
                            Location location = new Location(plugin.getServer().getWorld(parts[0]),
                                    Integer.parseInt(parts[1]),
                                    Integer.parseInt(parts[2]),
                                    Integer.parseInt(parts[3]));
                            Block targetBlock = location.getBlock();

                            if (isDoor(targetBlock.getType()) || isGate(targetBlock.getType()) || isTrapdoor(targetBlock.getType())) {
                                if (targetBlock.getBlockData() instanceof Openable) {
                                    Openable openable = (Openable) targetBlock.getBlockData();
                                    boolean wasOpen = openable.isOpen();
                                    openable.setOpen(!wasOpen);
                                    targetBlock.setBlockData(openable);

                                    if (isDoor(targetBlock.getType())) {
                                        if (!wasOpen) anyDoorOpened = true; else anyDoorClosed = true;
                                    } else if (isGate(targetBlock.getType())) {
                                        if (!wasOpen) anyGateOpened = true; else anyGateClosed = true;
                                    } else if (isTrapdoor(targetBlock.getType())) {
                                        if (!wasOpen) anyTrapOpened = true; else anyTrapClosed = true;
                                    }
                                }
                            } else if (targetBlock.getType() == Material.REDSTONE_LAMP) {
                                Lightable lamp = (Lightable) targetBlock.getBlockData();
                                boolean wasLit = lamp.isLit();
                                lamp.setLit(!wasLit);
                                targetBlock.setBlockData(lamp);
                                if (!wasLit) anyLampOn = true; else anyLampOff = true;
                            } else if (targetBlock.getType() == Material.NOTE_BLOCK) {
                                String instrument = dataManager.getPlayerInstrument(event.getPlayer().getUniqueId());
                                if (instrument == null) {
                                    instrument = configManager.getConfig().getString("default-note", "PIANO");
                                }
                                plugin.playDoorbellSound(location, instrument);
                                anyNoteBlockPlayed = true;
                            } else if (targetBlock.getType() == Material.BELL) {
                                targetBlock.getWorld().playSound(location, org.bukkit.Sound.BLOCK_BELL_USE, 3.0f, 1.0f);
                                anyBellPlayed = true;
                            }
                        }

                        if (anyDoorOpened) event.getPlayer().sendMessage(configManager.getMessage("tueren-geoeffnet"));
                        if (anyDoorClosed) event.getPlayer().sendMessage(configManager.getMessage("tueren-geschlossen"));
                        if (anyGateOpened) event.getPlayer().sendMessage(configManager.getMessage("gates-geoeffnet"));
                        if (anyGateClosed) event.getPlayer().sendMessage(configManager.getMessage("gates-geschlossen"));
                        if (anyTrapOpened) event.getPlayer().sendMessage(configManager.getMessage("fallturen-geoeffnet"));
                        if (anyTrapClosed) event.getPlayer().sendMessage(configManager.getMessage("fallturen-geschlossen"));
                        if (anyLampOn) event.getPlayer().sendMessage(configManager.getMessage("lampen-eingeschaltet"));
                        if (anyLampOff) event.getPlayer().sendMessage(configManager.getMessage("lampen-ausgeschaltet"));
                        if (anyNoteBlockPlayed) event.getPlayer().sendMessage(configManager.getMessage("notenblock-ausgeloest"));
                        if (anyBellPlayed) event.getPlayer().sendMessage(configManager.getMessage("glocke-gelaeutet"));
                    } else {
                        event.getPlayer().sendMessage(configManager.getMessage("keine-bloecke-verbunden"));
                    }
                }
                return;
            }
        }

        // Verbindung herstellen
        if (item == null || (!item.getType().equals(Material.STONE_BUTTON) &&
                             !item.getType().equals(Material.DAYLIGHT_DETECTOR) &&
                             !item.getType().equals(Material.NOTE_BLOCK) &&
                             !item.getType().equals(Material.TRIPWIRE_HOOK))) {
            return;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().getDisplayName().contains("§6Steuer-")) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }

        if (isDoor(block.getType()) || isGate(block.getType()) || isTrapdoor(block.getType()) ||
            block.getType() == Material.REDSTONE_LAMP ||
            block.getType() == Material.NOTE_BLOCK ||
            block.getType() == Material.BELL) {

            event.setCancelled(true);

            String buttonId = item.getItemMeta().hasLore() ? item.getItemMeta().getLore().get(0) : UUID.randomUUID().toString();
            List<String> connectedBlocks = dataManager.getConnectedBlocks(playerUUID, buttonId);
            if (connectedBlocks == null) {
                connectedBlocks = new ArrayList<>();
            }

            int maxDoors = configManager.getMaxDoors();
            int maxGates = configManager.getMaxDoors();
            int maxTraps = configManager.getMaxDoors();
            int maxLamps = configManager.getMaxLamps();
            int maxNoteBlocks = configManager.getMaxNoteBlocks();
            int maxBells = configManager.getMaxBells();

            int doorCount = (int) connectedBlocks.stream().filter(loc -> isDoor(getMaterialFromLocation(loc))).count();
            int gateCount = (int) connectedBlocks.stream().filter(loc -> isGate(getMaterialFromLocation(loc))).count();
            int trapCount = (int) connectedBlocks.stream().filter(loc -> isTrapdoor(getMaterialFromLocation(loc))).count();
            int lampCount = (int) connectedBlocks.stream().filter(loc -> getMaterialFromLocation(loc) == Material.REDSTONE_LAMP).count();
            int noteBlockCount = (int) connectedBlocks.stream().filter(loc -> getMaterialFromLocation(loc) == Material.NOTE_BLOCK).count();
            int bellCount = (int) connectedBlocks.stream().filter(loc -> getMaterialFromLocation(loc) == Material.BELL).count();

            if (isDoor(block.getType()) && doorCount >= maxDoors) {
                event.getPlayer().sendMessage(configManager.getMessage("max-tueren-erreicht"));
                return;
            }
            if (isGate(block.getType()) && gateCount >= maxGates) {
                event.getPlayer().sendMessage(configManager.getMessage("max-gates-erreicht"));
                return;
            }
            if (isTrapdoor(block.getType()) && trapCount >= maxTraps) {
                event.getPlayer().sendMessage(configManager.getMessage("max-fallturen-erreicht"));
                return;
            }
            if (block.getType() == Material.REDSTONE_LAMP && lampCount >= maxLamps) {
                event.getPlayer().sendMessage(configManager.getMessage("max-lampen-erreicht"));
                return;
            }
            if (block.getType() == Material.NOTE_BLOCK && noteBlockCount >= maxNoteBlocks) {
                event.getPlayer().sendMessage(configManager.getMessage("max-notenbloecke-erreicht"));
                return;
            }
            if (block.getType() == Material.BELL && bellCount >= maxBells) {
                event.getPlayer().sendMessage(configManager.getMessage("max-glocken-erreicht"));
                return;
            }

            String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
            if (!connectedBlocks.contains(blockLocation)) {
                connectedBlocks.add(blockLocation);
                dataManager.setConnectedBlocks(playerUUID, buttonId, connectedBlocks);
                updateButtonLore(item, buttonId);
                event.getPlayer().sendMessage(configManager.getMessage("block-verbunden"));
            } else {
                event.getPlayer().sendMessage(configManager.getMessage("block-bereits-verbunden"));
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        String playerUUID = event.getPlayer().getUniqueId().toString();
        ItemStack item = event.getItemInHand();
        Block block = event.getBlockPlaced();

        if (item == null || (!item.getType().equals(Material.STONE_BUTTON) &&
                             !item.getType().equals(Material.DAYLIGHT_DETECTOR) &&
                             !item.getType().equals(Material.NOTE_BLOCK) &&
                             !item.getType().equals(Material.TRIPWIRE_HOOK))) {
            return;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().getDisplayName().contains("§6Steuer-")) {
            return;
        }

        String buttonId = item.getItemMeta().hasLore() ? item.getItemMeta().getLore().get(0) : UUID.randomUUID().toString();
        String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        dataManager.addPlacedController(playerUUID, blockLocation, buttonId);
        event.getPlayer().sendMessage(configManager.getMessage("controller-platziert"));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        String playerUUID = event.getPlayer().getUniqueId().toString();
        Block block = event.getBlock();
        String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        String buttonId = dataManager.getButtonIdForPlacedController(playerUUID, blockLocation);

        if (buttonId != null) {
            dataManager.removePlacedController(playerUUID, blockLocation);
            dataManager.setConnectedBlocks(playerUUID, buttonId, null);
            dataManager.removeMotionSensorSettings(blockLocation); // Entferne Bewegungsmelder-Einstellungen
            event.getPlayer().sendMessage(configManager.getMessage("controller-entfernt"));
        }
    }

    private boolean isDoor(Material material) {
        return material.toString().endsWith("_DOOR");
    }

    private boolean isGate(Material material) {
        return material.toString().endsWith("_FENCE_GATE");
    }

    private boolean isTrapdoor(Material material) {
        return material.toString().endsWith("_TRAPDOOR");
    }

    private Material getMaterialFromLocation(String locString) {
        String[] parts = locString.split(",");
        if (parts.length != 4) return null;
        Location loc = new Location(plugin.getServer().getWorld(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
        return loc.getBlock().getType();
    }

    private void updateButtonLore(ItemStack item, String buttonId) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            if (!lore.contains(buttonId)) {
                lore.add(buttonId);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
    }
}