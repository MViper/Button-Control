package viper;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataManager {
    private final ButtonControl plugin;
    private FileConfiguration data;
    private File dataFile;

    public DataManager(ButtonControl plugin) {
        this.plugin = plugin;
        loadData();
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) plugin.saveResource("data.yml", false);
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void reloadData() {
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    // -----------------------------------------------------------------------
    //  Zugriff & Berechtigungen
    // -----------------------------------------------------------------------

    public boolean canAccess(String buttonId, UUID playerUUID) {
        if (isPublic(buttonId)) return true;
        if (isOwner(buttonId, playerUUID)) return true;
        return data.getStringList("trust." + buttonId).contains(playerUUID.toString());
    }

    public boolean isOwner(String buttonId, UUID playerUUID) {
        return data.contains("players." + playerUUID + ".buttons." + buttonId)
            || data.contains("players." + playerUUID + ".placed-controllers");
        // Zweite Bedingung: prüft ob irgendein placed-controller dieser UUID die buttonId enthält
    }

    // -----------------------------------------------------------------------
    //  Controller-Verwaltung
    // -----------------------------------------------------------------------

    public String getButtonIdForLocation(String location) {
        return getButtonIdForPlacedController(location);
    }

    public void registerController(String location, UUID ownerUUID, String buttonId) {
        data.set("players." + ownerUUID + ".placed-controllers." + location, buttonId);
        // Leere buttons-Liste anlegen damit isOwner() sofort greift
        if (!data.contains("players." + ownerUUID + ".buttons." + buttonId)) {
            data.set("players." + ownerUUID + ".buttons." + buttonId, new ArrayList<>());
        }
        saveData();
    }

    public void removeController(String location) {
        if (data.getConfigurationSection("players") == null) return;
        for (String uuid : data.getConfigurationSection("players").getKeys(false)) {
            String path = "players." + uuid + ".placed-controllers." + location;
            if (data.contains(path)) data.set(path, null);
        }
        removeMotionSensorSettings(location);
        saveData();
    }

    public String getButtonIdForPlacedController(String location) {
        if (data.getConfigurationSection("players") == null) return null;
        for (String uuid : data.getConfigurationSection("players").getKeys(false)) {
            String val = data.getString("players." + uuid + ".placed-controllers." + location);
            if (val != null) return val;
        }
        return null;
    }

    public List<String> getAllPlacedControllers() {
        List<String> result = new ArrayList<>();
        if (data.getConfigurationSection("players") == null) return result;
        for (String uuid : data.getConfigurationSection("players").getKeys(false)) {
            var sec = data.getConfigurationSection("players." + uuid + ".placed-controllers");
            if (sec != null) result.addAll(sec.getKeys(false));
        }
        return result;
    }

    // -----------------------------------------------------------------------
    //  Verbundene Blöcke
    // -----------------------------------------------------------------------

    public void setConnectedBlocks(String playerUUID, String buttonId, List<String> blocks) {
        data.set("players." + playerUUID + ".buttons." + buttonId, blocks);
        saveData();
    }

    public List<String> getConnectedBlocks(String buttonId) {
        if (data.getConfigurationSection("players") == null) return new ArrayList<>();
        for (String uuid : data.getConfigurationSection("players").getKeys(false)) {
            String path = "players." + uuid + ".buttons." + buttonId;
            if (data.contains(path)) return data.getStringList(path);
        }
        return new ArrayList<>();
    }

    /**
     * Entfernt eine Block-Location aus ALLEN Verbindungslisten aller Controller.
     * Wird aufgerufen wenn ein verbundener Block abgebaut wird.
     */
    public boolean removeFromAllConnectedBlocks(String locStr) {
        if (data.getConfigurationSection("players") == null) return false;
        boolean changed = false;
        for (String uuid : data.getConfigurationSection("players").getKeys(false)) {
            var buttonsSection = data.getConfigurationSection("players." + uuid + ".buttons");
            if (buttonsSection == null) continue;
            for (String buttonId : buttonsSection.getKeys(false)) {
                String path  = "players." + uuid + ".buttons." + buttonId;
                List<String> blocks = data.getStringList(path);
                if (blocks.remove(locStr)) {
                    data.set(path, blocks);
                    changed = true;
                }
            }
        }
        if (changed) saveData();
        return changed;
    }

    // -----------------------------------------------------------------------
    //  Controller-Name (NEU)
    // -----------------------------------------------------------------------

    public void setControllerName(String buttonId, String name) {
        data.set("names." + buttonId, name);
        saveData();
    }

    public String getControllerName(String buttonId) {
        return data.getString("names." + buttonId);
    }

    // -----------------------------------------------------------------------
    //  Zeitplan (NEU)
    // -----------------------------------------------------------------------

    public void setScheduleOpenTime(String buttonId, long ticks) {
        data.set("schedules." + buttonId + ".open-time", ticks);
        saveData();
    }

    public long getScheduleOpenTime(String buttonId) {
        return data.getLong("schedules." + buttonId + ".open-time", -1);
    }

    public void setScheduleCloseTime(String buttonId, long ticks) {
        data.set("schedules." + buttonId + ".close-time", ticks);
        saveData();
    }

    public long getScheduleCloseTime(String buttonId) {
        return data.getLong("schedules." + buttonId + ".close-time", -1);
    }

    /** Entfernt den kompletten Zeitplan für einen Controller. */
    public void clearSchedule(String buttonId) {
        data.set("schedules." + buttonId, null);
        saveData();
    }

    // -----------------------------------------------------------------------
    //  Trust & Public/Private
    // -----------------------------------------------------------------------

    public void addTrustedPlayer(String buttonId, UUID targetUUID) {
        List<String> trusted = data.getStringList("trust." + buttonId);
        if (!trusted.contains(targetUUID.toString())) {
            trusted.add(targetUUID.toString());
            data.set("trust." + buttonId, trusted);
            saveData();
        }
    }

    public void removeTrustedPlayer(String buttonId, UUID targetUUID) {
        List<String> trusted = data.getStringList("trust." + buttonId);
        trusted.remove(targetUUID.toString());
        data.set("trust." + buttonId, trusted);
        saveData();
    }

    public void setPublic(String buttonId, boolean isPublic) {
        data.set("public-status." + buttonId, isPublic);
        saveData();
    }

    public boolean isPublic(String buttonId) {
        return data.getBoolean("public-status." + buttonId, false);
    }

    // -----------------------------------------------------------------------
    //  Instrumente
    // -----------------------------------------------------------------------

    public void setPlayerInstrument(UUID playerUUID, String instrument) {
        data.set("players." + playerUUID + ".instrument", instrument);
        saveData();
    }

    public String getPlayerInstrument(UUID playerUUID) {
        return data.getString("players." + playerUUID + ".instrument");
    }

    // -----------------------------------------------------------------------
    //  Motion-Sensor-Einstellungen
    // -----------------------------------------------------------------------

    public void setMotionSensorRadius(String location, double radius) {
        data.set("motion-sensors." + location + ".radius", radius);
        saveData();
    }

    public double getMotionSensorRadius(String location) {
        return data.getDouble("motion-sensors." + location + ".radius", -1);
    }

    public void setMotionSensorDelay(String location, long delay) {
        data.set("motion-sensors." + location + ".delay", delay);
        saveData();
    }

    public long getMotionSensorDelay(String location) {
        return data.getLong("motion-sensors." + location + ".delay", -1);
    }

    public void removeMotionSensorSettings(String location) {
        data.set("motion-sensors." + location, null);
        saveData();
    }

    // -----------------------------------------------------------------------
    //  Speichern – asynchron
    // -----------------------------------------------------------------------

    /**
     * Serialisiert die Daten synchron (thread-safe), schreibt dann asynchron auf Disk.
     * Verhindert I/O-Lags auf dem Main-Thread.
     */
    public void saveData() {
        final String serialized;
        try {
            serialized = data.saveToString();
        } catch (Exception e) {
            plugin.getLogger().severe("Serialisierungsfehler data.yml: " + e.getMessage());
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (FileWriter fw = new FileWriter(dataFile, false)) {
                fw.write(serialized);
            } catch (IOException e) {
                plugin.getLogger().severe("Konnte data.yml nicht speichern: " + e.getMessage());
            }
        });
    }
}