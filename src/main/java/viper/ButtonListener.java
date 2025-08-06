package viper;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
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

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null &&
            (block.getType() == Material.STONE_BUTTON || block.getType() == Material.DAYLIGHT_DETECTOR)) {

            String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
            String buttonId = dataManager.getButtonIdForPlacedController(playerUUID, blockLocation);

            if (buttonId != null) {
                event.setCancelled(true);
                List<String> connectedBlocks = dataManager.getConnectedBlocks(playerUUID, buttonId);
                if (connectedBlocks != null && !connectedBlocks.isEmpty()) {

                    boolean anyDoorOpened = false;
                    boolean anyDoorClosed = false;
                    boolean anyLampOn = false;
                    boolean anyLampOff = false;

                    for (String loc : connectedBlocks) {
                        String[] parts = loc.split(",");
                        Location location = new Location(plugin.getServer().getWorld(parts[0]),
                                                        Integer.parseInt(parts[1]),
                                                        Integer.parseInt(parts[2]),
                                                        Integer.parseInt(parts[3]));
                        Block targetBlock = location.getBlock();
                        if (isDoor(targetBlock.getType())) {
                            Door door = (Door) targetBlock.getBlockData();
                            boolean wasOpen = door.isOpen();
                            door.setOpen(!wasOpen);
                            targetBlock.setBlockData(door);

                            if (!wasOpen) {
                                anyDoorOpened = true;
                            } else {
                                anyDoorClosed = true;
                            }
                        } else if (targetBlock.getType() == Material.REDSTONE_LAMP) {
                            Lightable lamp = (Lightable) targetBlock.getBlockData();
                            boolean wasLit = lamp.isLit();
                            lamp.setLit(!wasLit);
                            targetBlock.setBlockData(lamp);

                            if (!wasLit) {
                                anyLampOn = true;
                            } else {
                                anyLampOff = true;
                            }
                        }
                    }

                    if (anyDoorOpened) {
                        event.getPlayer().sendMessage(configManager.getMessage("doors-open"));
                    }
                    if (anyDoorClosed) {
                        event.getPlayer().sendMessage(configManager.getMessage("doors-closed"));
                    }
                    if (anyLampOn) {
                        event.getPlayer().sendMessage(configManager.getMessage("lamps-on"));
                    }
                    if (anyLampOff) {
                        event.getPlayer().sendMessage(configManager.getMessage("lamps-off"));
                    }

                } else {
                    event.getPlayer().sendMessage(configManager.getMessage("no-blocks-connected"));
                }
            }
            return;
        }

        if (item == null || (!item.getType().equals(Material.STONE_BUTTON) && !item.getType().equals(Material.DAYLIGHT_DETECTOR))) {
            return;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().getDisplayName().contains("§6Steuer-")) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }

        if (isDoor(block.getType()) || block.getType() == Material.REDSTONE_LAMP) {
            event.setCancelled(true);

            String buttonId = item.getItemMeta().hasLore() ? item.getItemMeta().getLore().get(0) : UUID.randomUUID().toString();
            List<String> connectedBlocks = dataManager.getConnectedBlocks(playerUUID, buttonId);
            if (connectedBlocks == null) {
                connectedBlocks = new ArrayList<>();
            }

            int maxDoors = configManager.getMaxDoors();
            int maxLamps = configManager.getMaxLamps();
            int doorCount = (int) connectedBlocks.stream().filter(loc -> isDoor(getMaterialFromLocation(loc))).count();
            int lampCount = (int) connectedBlocks.stream().filter(loc -> getMaterialFromLocation(loc) == Material.REDSTONE_LAMP).count();

            if (isDoor(block.getType()) && doorCount >= maxDoors) {
                event.getPlayer().sendMessage(configManager.getMessage("max-doors-reached"));
                return;
            }
            if (block.getType() == Material.REDSTONE_LAMP && lampCount >= maxLamps) {
                event.getPlayer().sendMessage(configManager.getMessage("max-lamps-reached"));
                return;
            }

            String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
            if (!connectedBlocks.contains(blockLocation)) {
                connectedBlocks.add(blockLocation);
                dataManager.setConnectedBlocks(playerUUID, buttonId, connectedBlocks);
                updateButtonLore(item, buttonId);
                event.getPlayer().sendMessage(configManager.getMessage("block-connected"));
            } else {
                event.getPlayer().sendMessage(configManager.getMessage("block-already-connected"));
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        String playerUUID = event.getPlayer().getUniqueId().toString();

        ItemStack item = event.getItemInHand();
        Block block = event.getBlockPlaced();

        if (item == null || (!item.getType().equals(Material.STONE_BUTTON) && !item.getType().equals(Material.DAYLIGHT_DETECTOR))) {
            return;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().getDisplayName().contains("§6Steuer-")) {
            return;
        }

        String buttonId = item.getItemMeta().hasLore() ? item.getItemMeta().getLore().get(0) : UUID.randomUUID().toString();
        String blockLocation = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        dataManager.addPlacedController(playerUUID, blockLocation, buttonId);
        event.getPlayer().sendMessage(configManager.getMessage("controller-placed"));
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
            event.getPlayer().sendMessage(configManager.getMessage("controller-removed"));
        }
    }

    private boolean isDoor(Material material) {
        return material.toString().endsWith("_DOOR");
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
