package viper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
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
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        ItemStack item = event.getItem();
        Block block = event.getClickedBlock();

        // 1. Logik für bereits platzierte Controller (Benutzung)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null) {
            String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
            String buttonId = dataManager.getButtonIdForLocation(blockLocation);

            if (buttonId != null) {
                if (!dataManager.canAccess(buttonId, playerUUID)) {
                    player.sendMessage(configManager.getMessage("keine-berechtigung-controller"));
                    event.setCancelled(true);
                    return;
                }

                if (block.getType() == Material.TRIPWIRE_HOOK) {
                    event.setCancelled(true);
                    new MotionSensorGUI(plugin, player, blockLocation, buttonId).open();
                    return;
                }

                if (isButton(block.getType()) || block.getType() == Material.DAYLIGHT_DETECTOR) {
                    List<String> connectedBlocks = dataManager.getConnectedBlocks(buttonId);
                    
                    if (connectedBlocks != null && !connectedBlocks.isEmpty()) {
                        toggleConnectedBlocks(player, playerUUID, connectedBlocks);
                    } else {
                        player.sendMessage(configManager.getMessage("keine-bloecke-verbunden"));
                    }
                }
                return;
            }
        }

        // 2. Logik für das Verbinden von neuen Blöcken (Item in der Hand)
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getDisplayName().contains("§6Steuer-")) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }

        if (isInteractableTarget(block.getType())) {
            event.setCancelled(true);

            ItemMeta meta = item.getItemMeta();
            String buttonId = null;

            // ID aus der Lore extrahieren (wir suchen nach dem Präfix §8ID: )
            if (meta != null && meta.hasLore() && !meta.getLore().isEmpty()) {
                String firstLine = meta.getLore().get(0);
                if (firstLine.startsWith("§8ID: ")) {
                    buttonId = firstLine.replace("§8ID: ", "");
                }
            }

            // Falls keine ID existiert (neues Item), generieren und SOFORT speichern
            if (buttonId == null) {
                buttonId = UUID.randomUUID().toString();
                updateButtonLore(item, buttonId);
            }
            
            List<String> connectedBlocks = dataManager.getConnectedBlocks(buttonId);
            if (connectedBlocks == null) {
                connectedBlocks = new ArrayList<>();
            }

            String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
            if (connectedBlocks.contains(blockLocation)) {
                player.sendMessage(configManager.getMessage("block-bereits-verbunden"));
                return;
            }

            if (checkLimits(player, block.getType(), connectedBlocks)) {
                connectedBlocks.add(blockLocation);
                dataManager.setConnectedBlocks(playerUUID.toString(), buttonId, connectedBlocks);
                player.sendMessage(configManager.getMessage("block-verbunden"));
            }
        }
    }

    private void toggleConnectedBlocks(Player player, UUID playerUUID, List<String> connectedBlocks) {
        boolean anyDoorOpened = false, anyDoorClosed = false;
        boolean anyGateOpened = false, anyGateClosed = false;
        boolean anyTrapOpened = false, anyTrapClosed = false;
        boolean anyLampOn = false, anyLampOff = false;
        boolean anyNoteBlockPlayed = false;
        boolean anyBellPlayed = false;

        for (String locStr : connectedBlocks) {
            Location location = parseLocation(locStr);
            if (location == null) continue;
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
            } 
            else if (targetBlock.getType() == Material.REDSTONE_LAMP) {
                Lightable lamp = (Lightable) targetBlock.getBlockData();
                boolean wasLit = lamp.isLit();
                lamp.setLit(!wasLit);
                targetBlock.setBlockData(lamp);
                if (!wasLit) anyLampOn = true; else anyLampOff = true;
            } 
            else if (targetBlock.getType() == Material.NOTE_BLOCK) {
                String instrument = dataManager.getPlayerInstrument(playerUUID);
                if (instrument == null) {
                    instrument = configManager.getConfig().getString("default-note", "PIANO");
                }
                plugin.playDoorbellSound(location, instrument);
                anyNoteBlockPlayed = true;
            } 
            else if (targetBlock.getType() == Material.BELL) {
                targetBlock.getWorld().playSound(location, org.bukkit.Sound.BLOCK_BELL_USE, 3.0f, 1.0f);
                anyBellPlayed = true;
            }
        }

        if (anyDoorOpened) player.sendMessage(configManager.getMessage("tueren-geoeffnet"));
        if (anyDoorClosed) player.sendMessage(configManager.getMessage("tueren-geschlossen"));
        if (anyGateOpened) player.sendMessage(configManager.getMessage("gates-geoeffnet"));
        if (anyGateClosed) player.sendMessage(configManager.getMessage("gates-geschlossen"));
        if (anyTrapOpened) player.sendMessage(configManager.getMessage("fallturen-geoeffnet"));
        if (anyTrapClosed) player.sendMessage(configManager.getMessage("fallturen-geschlossen"));
        if (anyLampOn) player.sendMessage(configManager.getMessage("lampen-eingeschaltet"));
        if (anyLampOff) player.sendMessage(configManager.getMessage("lampen-ausgeschaltet"));
        if (anyNoteBlockPlayed) player.sendMessage(configManager.getMessage("notenblock-ausgeloest"));
        if (anyBellPlayed) player.sendMessage(configManager.getMessage("glocke-gelaeutet"));
    }

    private boolean checkLimits(Player player, Material type, List<String> connected) {
        if (isDoor(type)) {
            if (connected.stream().filter(l -> isDoor(getMaterialFromLocation(l))).count() >= configManager.getMaxDoors()) {
                player.sendMessage(configManager.getMessage("max-tueren-erreicht"));
                return false;
            }
        } else if (isGate(type)) {
            if (connected.stream().filter(l -> isGate(getMaterialFromLocation(l))).count() >= configManager.getMaxGates()) {
                player.sendMessage(configManager.getMessage("max-gates-erreicht"));
                return false;
            }
        } else if (isTrapdoor(type)) {
            if (connected.stream().filter(l -> isTrapdoor(getMaterialFromLocation(l))).count() >= configManager.getMaxTrapdoors()) {
                player.sendMessage(configManager.getMessage("max-fallturen-erreicht"));
                return false;
            }
        } else if (type == Material.REDSTONE_LAMP) {
            if (connected.stream().filter(l -> getMaterialFromLocation(l) == Material.REDSTONE_LAMP).count() >= configManager.getMaxLamps()) {
                player.sendMessage(configManager.getMessage("max-lampen-erreicht"));
                return false;
            }
        } else if (type == Material.NOTE_BLOCK) {
            if (connected.stream().filter(l -> getMaterialFromLocation(l) == Material.NOTE_BLOCK).count() >= configManager.getMaxNoteBlocks()) {
                player.sendMessage(configManager.getMessage("max-notenbloecke-erreicht"));
                return false;
            }
        } else if (type == Material.BELL) {
            if (connected.stream().filter(l -> getMaterialFromLocation(l) == Material.BELL).count() >= configManager.getMaxBells()) {
                player.sendMessage(configManager.getMessage("max-glocken-erreicht"));
                return false;
            }
        }
        return true;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getDisplayName().contains("§6Steuer-")) {
            return;
        }

        Block block = event.getBlockPlaced();
        ItemMeta meta = item.getItemMeta();
        String buttonId = null;

        if (meta != null && meta.hasLore() && !meta.getLore().isEmpty()) {
            String firstLine = meta.getLore().get(0);
            if (firstLine.startsWith("§8ID: ")) {
                buttonId = firstLine.replace("§8ID: ", "");
            }
        }

        if (buttonId == null) {
            buttonId = UUID.randomUUID().toString();
            updateButtonLore(item, buttonId);
        }
        
        String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        dataManager.registerController(blockLocation, event.getPlayer().getUniqueId(), buttonId);
        event.getPlayer().sendMessage(configManager.getMessage("controller-platziert"));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        String buttonId = dataManager.getButtonIdForLocation(blockLocation);

        if (buttonId != null) {
            if (!dataManager.isOwner(buttonId, event.getPlayer().getUniqueId())) {
                event.getPlayer().sendMessage(configManager.getMessage("nur-besitzer-abbauen"));
                event.setCancelled(true);
                return;
            }

            dataManager.removeController(blockLocation);
            event.getPlayer().sendMessage(configManager.getMessage("controller-entfernt"));
        }
    }

    private boolean isButton(Material m) { return m.name().endsWith("_BUTTON"); }
    private boolean isDoor(Material m) { return m.name().endsWith("_DOOR"); }
    private boolean isGate(Material m) { return m.name().endsWith("_FENCE_GATE"); }
    private boolean isTrapdoor(Material m) { return m.name().endsWith("_TRAPDOOR"); }
    
    private boolean isInteractableTarget(Material m) {
        return isDoor(m) || isGate(m) || isTrapdoor(m) || m == Material.REDSTONE_LAMP || m == Material.NOTE_BLOCK || m == Material.BELL;
    }

    private Material getMaterialFromLocation(String locString) {
        Location l = parseLocation(locString);
        return l != null ? l.getBlock().getType() : Material.AIR;
    }

    private Location parseLocation(String s) {
        String[] p = s.split(",");
        if (p.length != 4) return null;
        try {
            return new Location(Bukkit.getWorld(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (Exception e) { return null; }
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
}