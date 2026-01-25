package viper;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
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
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void reloadData() {
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public String getButtonIdForLocation(String location) {
        return getButtonIdForPlacedController(location);
    }

    public boolean canAccess(String buttonId, UUID playerUUID) {
        if (isPublic(buttonId)) return true;
        if (isOwner(buttonId, playerUUID)) return true;
        List<String> trusted = data.getStringList("trust." + buttonId);
        return trusted.contains(playerUUID.toString());
    }

    public boolean isOwner(String buttonId, UUID playerUUID) {
        // Wir prüfen direkt im globalen Pfad der Buttons
        return data.contains("players." + playerUUID.toString() + ".buttons." + buttonId);
    }

    public void registerController(String location, UUID ownerUUID, String buttonId) {
        addPlacedController(ownerUUID.toString(), location, buttonId);
    }

    public void removeController(String location) {
        if (data.getConfigurationSection("players") == null) return;
        for (String playerUUID : data.getConfigurationSection("players").getKeys(false)) {
            String path = "players." + playerUUID + ".placed-controllers." + location;
            if (data.contains(path)) {
                data.set(path, null);
            }
        }
        removeMotionSensorSettings(location);
        saveData();
    }

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

    // Speichert die Blöcke für eine ID unter einem spezifischen Spieler
    public void setConnectedBlocks(String playerUUID, String buttonId, List<String> blocks) {
        data.set("players." + playerUUID + ".buttons." + buttonId, blocks);
        saveData();
    }

    public void addPlacedController(String playerUUID, String location, String buttonId) {
        // Verhindert doppelte Punkte im Pfad, falls die Location Punkte enthält
        data.set("players." + playerUUID + ".placed-controllers." + location, buttonId);
        saveData();
    }

    public String getButtonIdForPlacedController(String location) {
        if (data.getConfigurationSection("players") == null) return null;
        for (String playerUUID : data.getConfigurationSection("players").getKeys(false)) {
            String buttonId = data.getString("players." + playerUUID + ".placed-controllers." + location);
            if (buttonId != null) return buttonId;
        }
        return null;
    }

    // VERBESSERT: Sucht gezielt nach den Blöcken für diese ID
    public List<String> getConnectedBlocks(String buttonId) {
        if (data.getConfigurationSection("players") == null) return new ArrayList<>();
        
        for (String playerUUID : data.getConfigurationSection("players").getKeys(false)) {
            String path = "players." + playerUUID + ".buttons." + buttonId;
            if (data.contains(path)) {
                return data.getStringList(path);
            }
        }
        return new ArrayList<>(); // Niemals null zurückgeben, um Fehler im Listener zu vermeiden
    }

    public List<String> getAllPlacedControllers() {
        List<String> allControllers = new ArrayList<>();
        if (data.getConfigurationSection("players") == null) return allControllers;
        for (String playerUUID : data.getConfigurationSection("players").getKeys(false)) {
            if (data.getConfigurationSection("players." + playerUUID + ".placed-controllers") != null) {
                allControllers.addAll(data.getConfigurationSection("players." + playerUUID + ".placed-controllers").getKeys(false));
            }
        }
        return allControllers;
    }

    public void setPlayerInstrument(UUID playerUUID, String instrument) {
        data.set("players." + playerUUID.toString() + ".instrument", instrument);
        saveData();
    }

    public String getPlayerInstrument(UUID playerUUID) {
        return data.getString("players." + playerUUID.toString() + ".instrument");
    }

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

    public void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte data.yml nicht speichern: " + e.getMessage());
        }
    }
}