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
    private MySQLStorage mySQLStorage;

    public DataManager(ButtonControl plugin) {
        this.plugin = plugin;
        loadData();
        initializeStorage();
    }

    private void initializeStorage() {
        mySQLStorage = new MySQLStorage(plugin);
        if (!mySQLStorage.initialize()) {
            mySQLStorage = null;
        }
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) plugin.saveResource("data.yml", false);
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void reloadData() {
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (mySQLStorage != null) {
            mySQLStorage.close();
        }
        initializeStorage();
    }

    public void shutdown() {
        if (mySQLStorage != null) {
            mySQLStorage.close();
        }
    }

    // -----------------------------------------------------------------------
    //  Zugriff & Berechtigungen
    // -----------------------------------------------------------------------

    public boolean canAccess(String buttonId, UUID playerUUID) {
        if (mySQLStorage != null) return mySQLStorage.canAccess(buttonId, playerUUID);
        if (isPublic(buttonId)) return true;
        if (isOwner(buttonId, playerUUID)) return true;
        return data.getStringList("trust." + buttonId).contains(playerUUID.toString());
    }

    public boolean isOwner(String buttonId, UUID playerUUID) {
        if (mySQLStorage != null) return mySQLStorage.isOwner(buttonId, playerUUID);
        return data.contains("players." + playerUUID + ".buttons." + buttonId)
            || data.contains("players." + playerUUID + ".placed-controllers");
        // Zweite Bedingung: prüft ob irgendein placed-controller dieser UUID die buttonId enthält
    }

    // -----------------------------------------------------------------------
    //  Controller-Verwaltung
    // -----------------------------------------------------------------------

    public String getButtonIdForLocation(String location) {
        if (mySQLStorage != null) return mySQLStorage.getButtonIdForLocation(location);
        return getButtonIdForPlacedController(location);
    }

    public void registerController(String location, UUID ownerUUID, String buttonId) {
        if (mySQLStorage != null) {
            mySQLStorage.registerController(location, ownerUUID, buttonId);
            return;
        }
        data.set("players." + ownerUUID + ".placed-controllers." + location, buttonId);
        // Leere buttons-Liste anlegen damit isOwner() sofort greift
        if (!data.contains("players." + ownerUUID + ".buttons." + buttonId)) {
            data.set("players." + ownerUUID + ".buttons." + buttonId, new ArrayList<>());
        }
        saveData();
    }

    public void removeController(String location) {
        if (mySQLStorage != null) {
            mySQLStorage.removeController(location);
            return;
        }
        // buttonId vor dem Löschen des Location-Eintrags ermitteln
        String buttonId = getButtonIdForPlacedController(location);
        if (data.getConfigurationSection("players") != null) {
            for (String uuid : data.getConfigurationSection("players").getKeys(false)) {
                String path = "players." + uuid + ".placed-controllers." + location;
                if (data.contains(path)) {
                    data.set(path, null);
                }
            }
        }
        // Alle zugehörigen Daten (Name, Status, Trust, Zeitplan, Verbindungen, Secret) bereinigen
        if (buttonId != null) {
            data.set("names." + buttonId, null);
            data.set("public-status." + buttonId, null);
            data.set("trust." + buttonId, null);
            data.set("schedules." + buttonId, null);

            // Secret-Wall-Daten ebenfalls entfernen
            data.set("secret-walls." + buttonId, null);

            // Sicherheitshalber bei ALLEN Spielern den Button-Eintrag löschen
            if (data.getConfigurationSection("players") != null) {
                for (String uuid : data.getConfigurationSection("players").getKeys(false)) {
                    data.set("players." + uuid + ".buttons." + buttonId, null);
                }
            }
        }
        removeMotionSensorSettings(location);
        saveData();
    }

    public String getButtonIdForPlacedController(String location) {
        if (mySQLStorage != null) return mySQLStorage.getButtonIdForPlacedController(location);
        if (data.getConfigurationSection("players") == null) return null;
        for (String uuid : data.getConfigurationSection("players").getKeys(false)) {
            String val = data.getString("players." + uuid + ".placed-controllers." + location);
            if (val != null) return val;
        }
        return null;
    }

    public List<String> getAllPlacedControllers() {
        if (mySQLStorage != null) return mySQLStorage.getAllPlacedControllers();
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
        if (mySQLStorage != null) {
            mySQLStorage.setConnectedBlocks(playerUUID, buttonId, blocks);
            return;
        }
        data.set("players." + playerUUID + ".buttons." + buttonId, blocks);
        saveData();
    }

    public List<String> getConnectedBlocks(String buttonId) {
        if (mySQLStorage != null) return mySQLStorage.getConnectedBlocks(buttonId);
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
        if (mySQLStorage != null) return mySQLStorage.removeFromAllConnectedBlocks(locStr);
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
        if (mySQLStorage != null) {
            mySQLStorage.setControllerName(buttonId, name);
            return;
        }
        data.set("names." + buttonId, name);
        saveData();
    }

    public String getControllerName(String buttonId) {
        if (mySQLStorage != null) return mySQLStorage.getControllerName(buttonId);
        return data.getString("names." + buttonId);
    }

    // -----------------------------------------------------------------------
    //  Zeitplan (NEU)
    // -----------------------------------------------------------------------

    public void setScheduleOpenTime(String buttonId, long ticks) {
        if (mySQLStorage != null) {
            mySQLStorage.setScheduleOpenTime(buttonId, ticks);
            return;
        }
        data.set("schedules." + buttonId + ".open-time", ticks);
        saveData();
    }

    public long getScheduleOpenTime(String buttonId) {
        if (mySQLStorage != null) return mySQLStorage.getScheduleOpenTime(buttonId);
        return data.getLong("schedules." + buttonId + ".open-time", -1);
    }

    public void setScheduleCloseTime(String buttonId, long ticks) {
        if (mySQLStorage != null) {
            mySQLStorage.setScheduleCloseTime(buttonId, ticks);
            return;
        }
        data.set("schedules." + buttonId + ".close-time", ticks);
        saveData();
    }

    public long getScheduleCloseTime(String buttonId) {
        if (mySQLStorage != null) return mySQLStorage.getScheduleCloseTime(buttonId);
        return data.getLong("schedules." + buttonId + ".close-time", -1);
    }

    public void setScheduleShotDelayTicks(String buttonId, int ticks) {
        if (mySQLStorage != null) {
            mySQLStorage.setScheduleShotDelayTicks(buttonId, ticks);
            return;
        }
        data.set("schedules." + buttonId + ".shot-delay-ticks", ticks);
        saveData();
    }

    public int getScheduleShotDelayTicks(String buttonId) {
        if (mySQLStorage != null) return mySQLStorage.getScheduleShotDelayTicks(buttonId);
        return data.getInt("schedules." + buttonId + ".shot-delay-ticks", -1);
    }

    public void setScheduleTriggerMode(String buttonId, String mode) {
        if (mySQLStorage != null) {
            mySQLStorage.setScheduleTriggerMode(buttonId, mode);
            return;
        }
        data.set("schedules." + buttonId + ".trigger-mode", mode);
        saveData();
    }

    public String getScheduleTriggerMode(String buttonId) {
        if (mySQLStorage != null) return mySQLStorage.getScheduleTriggerMode(buttonId);
        return data.getString("schedules." + buttonId + ".trigger-mode");
    }

    /** Entfernt den kompletten Zeitplan für einen Controller. */
    public void clearSchedule(String buttonId) {
        if (mySQLStorage != null) {
            mySQLStorage.clearSchedule(buttonId);
            return;
        }
        data.set("schedules." + buttonId, null);
        saveData();
    }

    // -----------------------------------------------------------------------
    //  Trust & Public/Private
    // -----------------------------------------------------------------------

    public void addTrustedPlayer(String buttonId, UUID targetUUID) {
        if (mySQLStorage != null) {
            mySQLStorage.addTrustedPlayer(buttonId, targetUUID);
            return;
        }
        List<String> trusted = data.getStringList("trust." + buttonId);
        if (!trusted.contains(targetUUID.toString())) {
            trusted.add(targetUUID.toString());
            data.set("trust." + buttonId, trusted);
            saveData();
        }
    }

    public void removeTrustedPlayer(String buttonId, UUID targetUUID) {
        if (mySQLStorage != null) {
            mySQLStorage.removeTrustedPlayer(buttonId, targetUUID);
            return;
        }
        List<String> trusted = data.getStringList("trust." + buttonId);
        trusted.remove(targetUUID.toString());
        data.set("trust." + buttonId, trusted);
        saveData();
    }

    public void setPublic(String buttonId, boolean isPublic) {
        if (mySQLStorage != null) {
            mySQLStorage.setPublic(buttonId, isPublic);
            return;
        }
        data.set("public-status." + buttonId, isPublic);
        saveData();
    }

    public boolean isPublic(String buttonId) {
        if (mySQLStorage != null) return mySQLStorage.isPublic(buttonId);
        return data.getBoolean("public-status." + buttonId, false);
    }

    // -----------------------------------------------------------------------
    //  Instrumente
    // -----------------------------------------------------------------------

    public void setPlayerInstrument(UUID playerUUID, String instrument) {
        if (mySQLStorage != null) {
            mySQLStorage.setPlayerInstrument(playerUUID, instrument);
            return;
        }
        data.set("players." + playerUUID + ".instrument", instrument);
        saveData();
    }

    public String getPlayerInstrument(UUID playerUUID) {
        if (mySQLStorage != null) return mySQLStorage.getPlayerInstrument(playerUUID);
        return data.getString("players." + playerUUID + ".instrument");
    }

    // -----------------------------------------------------------------------
    //  Motion-Sensor-Einstellungen
    // -----------------------------------------------------------------------

    public void setMotionSensorRadius(String location, double radius) {
        if (mySQLStorage != null) {
            mySQLStorage.setMotionSensorRadius(location, radius);
            return;
        }
        data.set("motion-sensors." + location + ".radius", radius);
        saveData();
    }

    public double getMotionSensorRadius(String location) {
        if (mySQLStorage != null) return mySQLStorage.getMotionSensorRadius(location);
        return data.getDouble("motion-sensors." + location + ".radius", -1);
    }

    public void setMotionSensorDelay(String location, long delay) {
        if (mySQLStorage != null) {
            mySQLStorage.setMotionSensorDelay(location, delay);
            return;
        }
        data.set("motion-sensors." + location + ".delay", delay);
        saveData();
    }

    public long getMotionSensorDelay(String location) {
        if (mySQLStorage != null) return mySQLStorage.getMotionSensorDelay(location);
        return data.getLong("motion-sensors." + location + ".delay", -1);
    }

    public void removeMotionSensorSettings(String location) {
        if (mySQLStorage != null) {
            mySQLStorage.removeMotionSensorSettings(location);
            return;
        }
        data.set("motion-sensors." + location, null);
        saveData();
    }

    // -----------------------------------------------------------------------
    //  Secret-Wall (Geheimwand)
    // -----------------------------------------------------------------------

    public void setSecretBlocks(String buttonId, List<String> blocks) {
        if (mySQLStorage != null) {
            mySQLStorage.setSecretBlocks(buttonId, blocks);
            return;
        }
        data.set("secret-walls." + buttonId + ".blocks", blocks);
        saveData();
    }

    public List<String> getSecretBlocks(String buttonId) {
        if (mySQLStorage != null) return mySQLStorage.getSecretBlocks(buttonId);
        return data.getStringList("secret-walls." + buttonId + ".blocks");
    }

    public void setSecretRestoreDelayMs(String buttonId, long delayMs) {
        if (mySQLStorage != null) {
            mySQLStorage.setSecretRestoreDelayMs(buttonId, delayMs);
            return;
        }
        data.set("secret-walls." + buttonId + ".delay-ms", delayMs);
        saveData();
    }

    public long getSecretRestoreDelayMs(String buttonId) {
        if (mySQLStorage != null) return mySQLStorage.getSecretRestoreDelayMs(buttonId);
        return data.getLong("secret-walls." + buttonId + ".delay-ms", 5000L);
    }

    public void setSecretAnimation(String buttonId, String animation) {
        if (mySQLStorage != null) {
            mySQLStorage.setSecretAnimation(buttonId, animation);
            return;
        }
        data.set("secret-walls." + buttonId + ".animation", animation);
        saveData();
    }

    public String getSecretAnimation(String buttonId) {
        if (mySQLStorage != null) return mySQLStorage.getSecretAnimation(buttonId);
        return data.getString("secret-walls." + buttonId + ".animation", "wave");
    }

    public void clearSecret(String buttonId) {
        if (mySQLStorage != null) {
            mySQLStorage.clearSecret(buttonId);
            return;
        }
        data.set("secret-walls." + buttonId, null);
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
        if (mySQLStorage != null) return;
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