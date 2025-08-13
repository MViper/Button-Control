package viper;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    public List<String> getConnectedBlocks(String playerUUID, String buttonId) {
        return data.getStringList("players." + playerUUID + ".buttons." + buttonId);
    }

    public void setConnectedBlocks(String playerUUID, String buttonId, List<String> blocks) {
        data.set("players." + playerUUID + ".buttons." + buttonId, blocks);
        saveData();
    }

    public void addPlacedController(String playerUUID, String location, String buttonId) {
        data.set("players." + playerUUID + ".placed-controllers." + location, buttonId);
        saveData();
    }

    public String getButtonIdForPlacedController(String playerUUID, String location) {
        return data.getString("players." + playerUUID + ".placed-controllers." + location);
    }

    public void removePlacedController(String playerUUID, String location) {
        data.set("players." + playerUUID + ".placed-controllers." + location, null);
        saveData();
    }

    public List<String> getAllPlacedControllers(String playerUUID) {
        if (data.getConfigurationSection("players." + playerUUID + ".placed-controllers") == null) {
            return new ArrayList<>();
        }
        Set<String> keys = data.getConfigurationSection("players." + playerUUID + ".placed-controllers").getKeys(false);
        return new ArrayList<>(keys);
    }

    public List<String> getAllPlacedControllers() {
        List<String> allControllers = new ArrayList<>();
        if (data.getConfigurationSection("players") == null) {
            return allControllers;
        }
        Set<String> players = data.getConfigurationSection("players").getKeys(false);
        for (String playerUUID : players) {
            allControllers.addAll(getAllPlacedControllers(playerUUID));
        }
        return allControllers;
    }

    public String getButtonIdForPlacedController(String location) {
        if (data.getConfigurationSection("players") == null) return null;
        Set<String> players = data.getConfigurationSection("players").getKeys(false);
        for (String playerUUID : players) {
            String buttonId = getButtonIdForPlacedController(playerUUID, location);
            if (buttonId != null) return buttonId;
        }
        return null;
    }

    public List<String> getConnectedBlocks(String buttonId) {
        if (data.getConfigurationSection("players") == null) return null;
        Set<String> players = data.getConfigurationSection("players").getKeys(false);
        for (String playerUUID : players) {
            List<String> connected = getConnectedBlocks(playerUUID, buttonId);
            if (connected != null && !connected.isEmpty()) {
                return connected;
            }
        }
        return null;
    }

    public void setPlayerInstrument(UUID playerUUID, String instrument) {
        data.set("players." + playerUUID.toString() + ".instrument", instrument);
        saveData();
    }

    public String getPlayerInstrument(UUID playerUUID) {
        return data.getString("players." + playerUUID.toString() + ".instrument");
    }

    // Bewegungsmelder-Einstellungen
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