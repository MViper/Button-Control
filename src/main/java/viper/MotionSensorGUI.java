package viper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class MotionSensorGUI implements Listener {
    private final ButtonControl plugin;
    private final DataManager dataManager;
    private final ConfigManager configManager;
    private final String blockLocation;
    private final String buttonId;
    private final Player player;

    public MotionSensorGUI(ButtonControl plugin, Player player, String blockLocation, String buttonId) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.configManager = plugin.getConfigManager();
        this.blockLocation = blockLocation;
        this.buttonId = buttonId;
        this.player = player;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open() {
        Inventory inv = Bukkit.createInventory(player, 27, "Bewegungsmelder Einstellungen");

        // Aktuelle Werte aus DataManager holen oder Standardwerte aus Config
        double radius = dataManager.getMotionSensorRadius(blockLocation);
        if (radius == -1) radius = configManager.getConfig().getDouble("motion-detection-radius", 5.0);
        long delay = dataManager.getMotionSensorDelay(blockLocation);
        if (delay == -1) delay = configManager.getConfig().getLong("motion-close-delay-ms", 5000L);

        // Items für die GUI
        ItemStack radiusItem = new ItemStack(Material.COMPASS);
        ItemMeta radiusMeta = radiusItem.getItemMeta();
        radiusMeta.setDisplayName(ChatColor.GREEN + "Erkennungsradius: " + radius + " Blöcke");
        radiusMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Linksklick: +0.5 Blöcke",
                ChatColor.GRAY + "Rechtsklick: -0.5 Blöcke"
        ));
        radiusItem.setItemMeta(radiusMeta);

        ItemStack delayItem = new ItemStack(Material.CLOCK);
        ItemMeta delayMeta = delayItem.getItemMeta();
        delayMeta.setDisplayName(ChatColor.GREEN + "Schließverzögerung: " + (delay / 1000.0) + " Sekunden");
        delayMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Linksklick: +1 Sekunde",
                ChatColor.GRAY + "Rechtsklick: -1 Sekunde"
        ));
        delayItem.setItemMeta(delayMeta);

        ItemStack saveItem = new ItemStack(Material.EMERALD);
        ItemMeta saveMeta = saveItem.getItemMeta();
        saveMeta.setDisplayName(ChatColor.GREEN + "Speichern und Schließen");
        saveItem.setItemMeta(saveMeta);

        // Items in die GUI setzen
        inv.setItem(11, radiusItem);
        inv.setItem(15, delayItem);
        inv.setItem(22, saveItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().getHolder().equals(player)) return;
        if (event.getCurrentItem() == null) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        double radius = dataManager.getMotionSensorRadius(blockLocation);
        if (radius == -1) radius = configManager.getConfig().getDouble("motion-detection-radius", 5.0);
        long delay = dataManager.getMotionSensorDelay(blockLocation);
        if (delay == -1) delay = configManager.getConfig().getLong("motion-close-delay-ms", 5000L);

        if (clicked.getType() == Material.COMPASS) {
            if (event.isLeftClick()) {
                radius = Math.min(radius + 0.5, 20.0); // Max. Radius: 20 Blöcke
            } else if (event.isRightClick()) {
                radius = Math.max(radius - 0.5, 0.5); // Min. Radius: 0.5 Blöcke
            }
            dataManager.setMotionSensorRadius(blockLocation, radius);
            ItemMeta meta = clicked.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "Erkennungsradius: " + radius + " Blöcke");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Linksklick: +0.5 Blöcke",
                    ChatColor.GRAY + "Rechtsklick: -0.5 Blöcke"
            ));
            clicked.setItemMeta(meta);
        } else if (clicked.getType() == Material.CLOCK) {
            if (event.isLeftClick()) {
                delay = Math.min(delay + 1000, 30000); // Max. Verzögerung: 30 Sekunden
            } else if (event.isRightClick()) {
                delay = Math.max(delay - 1000, 1000); // Min. Verzögerung: 1 Sekunde
            }
            dataManager.setMotionSensorDelay(blockLocation, delay);
            ItemMeta meta = clicked.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "Schließverzögerung: " + (delay / 1000.0) + " Sekunden");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Linksklick: +1 Sekunde",
                    ChatColor.GRAY + "Rechtsklick: -1 Sekunde"
            ));
            clicked.setItemMeta(meta);
        } else if (clicked.getType() == Material.EMERALD) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(player)) {
            InventoryClickEvent.getHandlerList().unregister(this);
            InventoryCloseEvent.getHandlerList().unregister(this);
        }
    }
}