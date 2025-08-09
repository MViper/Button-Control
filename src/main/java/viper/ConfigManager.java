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
        // Setze Standardwerte, falls sie fehlen
        setConfigDefaults();
    }

    private void loadLang() {
        langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        lang = YamlConfiguration.loadConfiguration(langFile);
        // Setze Standardnachrichten, falls sie fehlen
        setLangDefaults();
    }

    private void setConfigDefaults() {
        if (!config.contains("max-doors")) {
            config.set("max-doors", 20);
        }
        if (!config.contains("max-lamps")) {
            config.set("max-lamps", 50);
        }
        if (!config.contains("max-noteblocks")) {
            config.set("max-noteblocks", 10);
        }
        if (!config.contains("default-note")) {
            config.set("default-note", "PIANO");
        }
        if (!config.contains("double-note-enabled")) {
            config.set("double-note-enabled", true);
        }
        if (!config.contains("double-note-delay-ms")) {
            config.set("double-note-delay-ms", 1000);
        }
        saveConfig();
    }

    private void setLangDefaults() {
        // Standardnachrichten hinzufügen, ohne bestehende zu überschreiben
        if (!lang.contains("tueren-geoeffnet")) {
            lang.set("tueren-geoeffnet", "§aTüren wurden geöffnet.");
        }
        if (!lang.contains("tueren-geschlossen")) {
            lang.set("tueren-geschlossen", "§cTüren wurden geschlossen.");
        }
        if (!lang.contains("lampen-eingeschaltet")) {
            lang.set("lampen-eingeschaltet", "§aLampen wurden eingeschaltet.");
        }
        if (!lang.contains("lampen-ausgeschaltet")) {
            lang.set("lampen-ausgeschaltet", "§cLampen wurden ausgeschaltet.");
        }
        if (!lang.contains("bloecke-umgeschaltet")) {
            lang.set("bloecke-umgeschaltet", "§eBlöcke wurden umgeschaltet.");
        }
        if (!lang.contains("keine-bloecke-verbunden")) {
            lang.set("keine-bloecke-verbunden", "§cKeine Blöcke sind verbunden.");
        }
        if (!lang.contains("max-tueren-erreicht")) {
            lang.set("max-tueren-erreicht", "§cMaximale Anzahl an Türen erreicht.");
        }
        if (!lang.contains("max-lampen-erreicht")) {
            lang.set("max-lampen-erreicht", "§cMaximale Anzahl an Lampen erreicht.");
        }
        if (!lang.contains("max-notenbloecke-erreicht")) {
            lang.set("max-notenbloecke-erreicht", "§cMaximale Anzahl an Notenblöcken erreicht.");
        }
        if (!lang.contains("block-verbunden")) {
            lang.set("block-verbunden", "§aBlock verbunden.");
        }
        if (!lang.contains("block-bereits-verbunden")) {
            lang.set("block-bereits-verbunden", "§cBlock ist bereits verbunden.");
        }
        if (!lang.contains("controller-platziert")) {
            lang.set("controller-platziert", "§aController platziert.");
        }
        if (!lang.contains("controller-entfernt")) {
            lang.set("controller-entfernt", "§cController entfernt.");
        }
        if (!lang.contains("notenblock-ausgeloest")) {
            lang.set("notenblock-ausgeloest", "§aNotenblock-Klingel wurde ausgelöst.");
        }
        if (!lang.contains("instrument-gesetzt")) {
            lang.set("instrument-gesetzt", "§aDein Notenblock-Instrument wurde auf %s gesetzt.");
        }
        if (!lang.contains("ungueltiges-instrument")) {
            lang.set("ungueltiges-instrument", "§cUngültiges Instrument! Verwende: /bc note <Instrument>");
        }
        if (!lang.contains("konfiguration-reloaded")) {
            lang.set("konfiguration-reloaded", "§aKonfiguration und Daten erfolgreich neu geladen!");
        }
        if (!lang.contains("keine-berechtigung")) {
            lang.set("keine-berechtigung", "§cDu hast keine Berechtigung für diesen Befehl!");
        }
        saveLang();
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        lang = YamlConfiguration.loadConfiguration(langFile);
        setConfigDefaults(); // Stelle sicher, dass neue Standardwerte hinzugefügt werden
        setLangDefaults(); // Stelle sicher, dass neue Nachrichten hinzugefügt werden
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public int getMaxDoors() {
        return config.getInt("max-doors", 20);
    }

    public int getMaxLamps() {
        return config.getInt("max-lamps", 50);
    }

    public int getMaxNoteBlocks() {
        return config.getInt("max-noteblocks", 10);
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