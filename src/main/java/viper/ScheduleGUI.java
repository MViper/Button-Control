package viper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * GUI zur Konfiguration der zeitgesteuerten Automatisierung eines Controllers.
 *
 * Layout (9×3 = 27 Slots):
 *  Slot 4  – Abschuss-Verzögerung Werfer/Spender (REPEATER)  ← Links/Rechts: ±1 Tick | Shift: ±5
 *  Slot 6  – Schuss-Modus (COMPARATOR)  ← Klick: gleichzeitig / nacheinander
 *  Slot 10 – Öffnungszeit  (LIME_DYE  / Sonne)  ← Links/Rechts: ±1h  |  Shift: ±15min
 *  Slot 13 – Aktivierung an/aus (LEVER)
 *  Slot 16 – Schließzeit   (RED_DYE   / Mond)   ← Links/Rechts: ±1h  |  Shift: ±15min
 *  Slot 22 – Speichern & Schließen (EMERALD)
 *
 * Zeit wird als Ingame-Ticks gespeichert (0–23999).
 * ticksToTime() / timeToTicks() in ButtonControl konvertieren nach "HH:MM".
 */
public class ScheduleGUI implements Listener {
    private final ButtonControl plugin;
    private final DataManager dataManager;
    private final Player player;
    private final String buttonId;
    private final Inventory inv;

    // Aktuelle Werte während die GUI offen ist
    private long openTime;
    private long closeTime;
    private int shotDelayTicks;
    private String triggerMode;
    private boolean enabled;

    public ScheduleGUI(ButtonControl plugin, Player player, String buttonId) {
        this.plugin      = plugin;
        this.dataManager = plugin.getDataManager();
        this.player      = player;
        this.buttonId    = buttonId;
        this.inv         = Bukkit.createInventory(null, 27, "§6Zeitplan-Einstellungen");

        // Gespeicherte Werte laden (oder Standardwerte)
        long savedOpen  = dataManager.getScheduleOpenTime(buttonId);
        long savedClose = dataManager.getScheduleCloseTime(buttonId);
        int savedShotDelay = dataManager.getScheduleShotDelayTicks(buttonId);
        String savedTriggerMode = dataManager.getScheduleTriggerMode(buttonId);
        this.openTime   = savedOpen  >= 0 ? savedOpen  : plugin.timeToTicks(7, 0);  // 07:00
        this.closeTime  = savedClose >= 0 ? savedClose : plugin.timeToTicks(19, 0); // 19:00
        this.shotDelayTicks = savedShotDelay >= 0
            ? savedShotDelay
            : Math.max(1, plugin.getConfigManager().getConfig().getInt("timed-container-shot-delay-ticks", 2));
        this.triggerMode = normalizeTriggerMode(savedTriggerMode != null
            ? savedTriggerMode
            : plugin.getConfigManager().getConfig().getString("timed-container-trigger-mode", "simultaneous"));
        this.enabled    = savedOpen  >= 0;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open() {
        renderItems();
        player.openInventory(inv);
    }

    // -----------------------------------------------------------------------
    //  GUI aufbauen
    // -----------------------------------------------------------------------

    private void renderItems() {
        // Füllung
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, ChatColor.RESET + "");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Slot 4 – Abschuss-Verzögerung
        inv.setItem(4, makeDelayItem());

        // Slot 6 – Modus
        inv.setItem(6, makeModeItem());

        // Slot 10 – Öffnungszeit
        inv.setItem(10, makeTimeItem(
            Material.LIME_DYE,
            "§a§lÖffnungszeit",
            openTime,
            "§7Linksklick: §f+1 Stunde",
            "§7Rechtsklick: §f−1 Stunde",
            "§7Shift+Links: §f+15 Minuten",
            "§7Shift+Rechts: §f−15 Minuten"
        ));

        // Slot 13 – Aktivierung an/aus
        Material leverMat  = enabled ? Material.LEVER : Material.DEAD_BUSH;
        String leverName   = enabled ? "§a§lZeitplan aktiv" : "§c§lZeitplan deaktiviert";
        String leverDesc   = enabled ? "§7Klick zum §cDeaktivieren" : "§7Klick zum §aAktivieren";
        inv.setItem(13, makeItem(leverMat, leverName, leverDesc, "§8(Zeitplan wird gespeichert)"));

        // Slot 16 – Schließzeit
        inv.setItem(16, makeTimeItem(
            Material.RED_DYE,
            "§c§lSchließzeit",
            closeTime,
            "§7Linksklick: §f+1 Stunde",
            "§7Rechtsklick: §f−1 Stunde",
            "§7Shift+Links: §f+15 Minuten",
            "§7Shift+Rechts: §f−15 Minuten"
        ));

        // Slot 22 – Speichern
        inv.setItem(22, makeItem(Material.EMERALD,
            "§a§lSpeichern & Schließen",
            "§7Speichert den aktuellen Zeitplan."));
    }

    private ItemStack makeDelayItem() {
        List<String> lore = new ArrayList<>();
        lore.add("§e§l" + shotDelayTicks + " Ticks §7(" + formatShotDelaySeconds() + "s§7)");
        if (isSequentialMode()) {
            lore.add("§7Aktuell: §f" + shotDelayTicks + " Ticks zwischen einzelnen Geräten");
        } else if (shotDelayTicks <= 1) {
            lore.add("§7Aktuell: §falle verbundenen Werfer schießen jeden Tick");
        } else {
            lore.add("§7Aktuell: §f" + shotDelayTicks + " Ticks zwischen gemeinsamen Schüssen");
        }
        lore.add("");
        lore.add("§7Linksklick: §f+1 Tick");
        lore.add("§7Rechtsklick: §f−1 Tick");
        lore.add("§7Shift+Links: §f+5 Ticks");
        lore.add("§7Shift+Rechts: §f−5 Ticks");
        lore.add("§8(1 Tick = schnellstmöglich)");

        ItemStack item = new ItemStack(Material.REPEATER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lAbschuss-Verzögerung");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeModeItem() {
        List<String> lore = new ArrayList<>();
        lore.add(isSequentialMode()
            ? "§e§lNacheinander"
            : "§e§lGleichzeitig");
        lore.add("");
        lore.add("§7Klick: §fModus wechseln");
        lore.add("§8Gleichzeitig = alle Werfer zusammen");
        lore.add("§8Nacheinander = Geräte rotieren der Reihe nach");

        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lSchuss-Modus");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatShotDelaySeconds() {
        return String.format(Locale.US, "%.2f", shotDelayTicks / 20.0);
    }

    private boolean isSequentialMode() {
        return "sequential".equals(triggerMode);
    }

    private String normalizeTriggerMode(String mode) {
        return "sequential".equalsIgnoreCase(mode) ? "sequential" : "simultaneous";
    }

    private ItemStack makeTimeItem(Material mat, String name, long ticks, String... loreLines) {
        String timeStr = plugin.ticksToTime(ticks);
        List<String> lore = new ArrayList<>();
        lore.add("§e§l" + timeStr + " Uhr §7(Ingame)");
        lore.add("");
        lore.addAll(Arrays.asList(loreLines));
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    // -----------------------------------------------------------------------
    //  Event-Handler
    // -----------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inv)) return;
        if (!event.getWhoClicked().equals(player)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();
        // Nur Klicks in unserer GUI (0–26) verarbeiten
        if (slot < 0 || slot > 26) return;

        // Schrittgröße: Shift = 15 Min (250 Ticks), sonst 1 Std (1000 Ticks)
        long step = event.isShiftClick() ? 250L : 1000L;

        if (slot == 4) {
            int delayStep = event.isShiftClick() ? 5 : 1;
            if (event.isLeftClick())  shotDelayTicks += delayStep;
            if (event.isRightClick()) shotDelayTicks -= delayStep;
            if (shotDelayTicks < 1) shotDelayTicks = 1;
            if (shotDelayTicks > 200) shotDelayTicks = 200;
            inv.setItem(4, makeDelayItem());

        } else if (slot == 6) {
            triggerMode = isSequentialMode() ? "simultaneous" : "sequential";
            inv.setItem(4, makeDelayItem());
            inv.setItem(6, makeModeItem());

        } else if (slot == 10) {
            // Öffnungszeit anpassen
            if (event.isLeftClick())  openTime  = (openTime  + step + 24000) % 24000;
            if (event.isRightClick()) openTime  = (openTime  - step + 24000) % 24000;
            inv.setItem(10, makeTimeItem(Material.LIME_DYE, "§a§lÖffnungszeit", openTime,
                "§7Linksklick: §f+1 Stunde", "§7Rechtsklick: §f−1 Stunde",
                "§7Shift+Links: §f+15 Minuten", "§7Shift+Rechts: §f−15 Minuten"));

        } else if (slot == 13) {
            // Aktivierung umschalten
            enabled = !enabled;
            renderItems();

        } else if (slot == 16) {
            // Schließzeit anpassen
            if (event.isLeftClick())  closeTime = (closeTime + step + 24000) % 24000;
            if (event.isRightClick()) closeTime = (closeTime - step + 24000) % 24000;
            inv.setItem(16, makeTimeItem(Material.RED_DYE, "§c§lSchließzeit", closeTime,
                "§7Linksklick: §f+1 Stunde", "§7Rechtsklick: §f−1 Stunde",
                "§7Shift+Links: §f+15 Minuten", "§7Shift+Rechts: §f−15 Minuten"));

        } else if (slot == 22) {
            // Speichern
            save();
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().equals(inv) && event.getWhoClicked().equals(player))
            event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player) || !event.getInventory().equals(inv)) return;
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryDragEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
    }

    // -----------------------------------------------------------------------
    //  Speichern
    // -----------------------------------------------------------------------

    private void save() {
        if (enabled) {
            dataManager.setScheduleOpenTime(buttonId, openTime);
            dataManager.setScheduleCloseTime(buttonId, closeTime);
            dataManager.setScheduleShotDelayTicks(buttonId, shotDelayTicks);
            dataManager.setScheduleTriggerMode(buttonId, triggerMode);
            player.sendMessage("§a[BC] §7Zeitplan gespeichert: §aÖffnet §7um §e"
                + plugin.ticksToTime(openTime)
                + " §7· §cSchließt §7um §e"
                + plugin.ticksToTime(closeTime)
                + " §7· §bDelay §e"
                + shotDelayTicks
                + "§7 Ticks §8("
                + formatShotDelaySeconds()
                + "s§8) §7· §dModus §e"
                + (isSequentialMode() ? "nacheinander" : "gleichzeitig"));
        } else {
            dataManager.clearSchedule(buttonId);
            player.sendMessage("§7[BC] Zeitplan deaktiviert.");
        }
    }

}