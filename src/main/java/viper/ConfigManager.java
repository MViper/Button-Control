package viper;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final ButtonControl plugin;
    private FileConfiguration config;
    private FileConfiguration lang;
    private File configFile;
    private File langFile;

    public ConfigManager(ButtonControl plugin) {
        this.plugin = plugin;
        loadConfig();
        loadLang();
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void loadLang() {
        langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        lang = YamlConfiguration.loadConfiguration(langFile);
    }

    public int getMaxDoors() {
        return config.getInt("max-doors", 20);
    }

    public int getMaxLamps() {
        return config.getInt("max-lamps", 50);
    }

    public String getMessage(String key) {
        return lang.getString(key, "Nachricht nicht gefunden: " + key);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte config.yml nicht speichern: " + e.getMessage());
        }
    }

    public void saveLang() {
        try {
            lang.save(langFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte lang.yml nicht speichern: " + e.getMessage());
        }
    }
}