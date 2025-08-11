package viper;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;

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

        // Fehlende Defaults aus der Jar-Version einfügen
        mergeDefaults(config, "config.yml", configFile);

        // bestehende setConfigDefaults (nur für alte Backwards-Kompatibilität)
        setConfigDefaults();
    }

    private void loadLang() {
        langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        lang = YamlConfiguration.loadConfiguration(langFile);

        // Fehlende Defaults aus der Jar-Version einfügen
        mergeDefaults(lang, "lang.yml", langFile);

        // bestehende setLangDefaults
        setLangDefaults();
    }

    /**
     * Ergänzt fehlende Keys aus der im Jar enthaltenen Datei.
     */
    private void mergeDefaults(FileConfiguration file, String resourceName, File targetFile) {
        try (InputStream is = plugin.getResource(resourceName);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            if (is == null) {
                plugin.getLogger().warning(resourceName + " nicht im Plugin-Jar gefunden, Merge übersprungen.");
                return;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!file.contains(key)) {
                    file.set(key, defaults.get(key));
                    changed = true;
                    plugin.getLogger().info("[ConfigManager] Neuer Key in " + resourceName + " hinzugefügt: " + key);
                }
            }
            if (changed) {
                file.save(targetFile);
                plugin.getLogger().info(resourceName + " wurde mit neuen Standardwerten ergänzt.");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Fehler beim Mergen von " + resourceName + ": " + e.getMessage());
        }
    }

    private void setConfigDefaults() {
        if (!config.contains("max-doors")) config.set("max-doors", 20);
        if (!config.contains("max-lamps")) config.set("max-lamps", 50);
        if (!config.contains("max-noteblocks")) config.set("max-noteblocks", 10);
        if (!config.contains("max-gates")) config.set("max-gates", 20);
        if (!config.contains("max-trapdoors")) config.set("max-trapdoors", 20);
        if (!config.contains("max-bells")) config.set("max-bells", 5);
        if (!config.contains("default-note")) config.set("default-note", "PIANO");
        if (!config.contains("double-note-enabled")) config.set("double-note-enabled", true);
        if (!config.contains("double-note-delay-ms")) config.set("double-note-delay-ms", 1000);
        saveConfig();
    }

    private void setLangDefaults() {
        // Beispiel: nur wenn Key fehlt, setzen – alle Keys wie in deiner Version bleiben hier
        if (!lang.contains("tueren-geoeffnet")) lang.set("tueren-geoeffnet", "§aTüren wurden geöffnet.");
        if (!lang.contains("tueren-geschlossen")) lang.set("tueren-geschlossen", "§cTüren wurden geschlossen.");
        if (!lang.contains("max-tueren-erreicht")) lang.set("max-tueren-erreicht", "§cMaximale Anzahl an Türen erreicht.");

        if (!lang.contains("lampen-eingeschaltet")) lang.set("lampen-eingeschaltet", "§aLampen wurden eingeschaltet.");
        if (!lang.contains("lampen-ausgeschaltet")) lang.set("lampen-ausgeschaltet", "§cLampen wurden ausgeschaltet.");
        if (!lang.contains("max-lampen-erreicht")) lang.set("max-lampen-erreicht", "§cMaximale Anzahl an Lampen erreicht.");

        if (!lang.contains("notenblock-ausgeloest")) lang.set("notenblock-ausgeloest", "§aNotenblock-Klingel wurde ausgelöst.");
        if (!lang.contains("max-notenbloecke-erreicht")) lang.set("max-notenbloecke-erreicht", "§cMaximale Anzahl an Notenblöcken erreicht.");

        if (!lang.contains("gates-geoeffnet")) lang.set("gates-geoeffnet", "§aZauntore wurden geöffnet.");
        if (!lang.contains("gates-geschlossen")) lang.set("gates-geschlossen", "§cZauntore wurden geschlossen.");
        if (!lang.contains("max-gates-erreicht")) lang.set("max-gates-erreicht", "§cMaximale Anzahl an Zauntoren erreicht.");

        if (!lang.contains("fallturen-geoeffnet")) lang.set("fallturen-geoeffnet", "§aFalltüren wurden geöffnet.");
        if (!lang.contains("fallturen-geschlossen")) lang.set("fallturen-geschlossen", "§cFalltüren wurden geschlossen.");
        if (!lang.contains("max-fallturen-erreicht")) lang.set("max-fallturen-erreicht", "§cMaximale Anzahl an Falltüren erreicht.");

        if (!lang.contains("glocke-gelaeutet")) lang.set("glocke-gelaeutet", "§eGlocke wurde geläutet.");
        if (!lang.contains("max-glocken-erreicht")) lang.set("max-glocken-erreicht", "§cMaximale Anzahl an Glocken erreicht.");

        if (!lang.contains("bloecke-umgeschaltet")) lang.set("bloecke-umgeschaltet", "§eBlöcke wurden umgeschaltet.");
        if (!lang.contains("keine-bloecke-verbunden")) lang.set("keine-bloecke-verbunden", "§cKeine Blöcke sind verbunden.");
        if (!lang.contains("block-verbunden")) lang.set("block-verbunden", "§aBlock verbunden.");
        if (!lang.contains("block-bereits-verbunden")) lang.set("block-bereits-verbunden", "§cBlock ist bereits verbunden.");
        if (!lang.contains("controller-platziert")) lang.set("controller-platziert", "§aController platziert.");
        if (!lang.contains("controller-entfernt")) lang.set("controller-entfernt", "§cController entfernt.");
        if (!lang.contains("instrument-gesetzt")) lang.set("instrument-gesetzt", "§aDein Notenblock-Instrument wurde auf %s gesetzt.");
        if (!lang.contains("ungueltiges-instrument")) lang.set("ungueltiges-instrument", "§cUngültiges Instrument! Verwende: /bc note <Instrument>");
        if (!lang.contains("konfiguration-reloaded")) lang.set("konfiguration-reloaded", "§aKonfiguration und Daten erfolgreich neu geladen!");
        if (!lang.contains("keine-berechtigung")) lang.set("keine-berechtigung", "§cDu hast keine Berechtigung für diesen Befehl!");
        if (!lang.contains("kolben-ausgefahren")) lang.set("kolben-ausgefahren", "§6[ButtonControl] §7Kolben wurden ausgefahren.");
        if (!lang.contains("kolben-eingefahren")) lang.set("kolben-eingefahren", "§6[ButtonControl] §7Kolben wurden eingezogen.");
        if (!lang.contains("max-kolben-erreicht")) lang.set("max-kolben-erreicht", "§6[ButtonControl] §7Maximale Anzahl an Kolben erreicht.");

        saveLang();
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        lang = YamlConfiguration.loadConfiguration(langFile);
        mergeDefaults(config, "config.yml", configFile);
        mergeDefaults(lang, "lang.yml", langFile);
        setConfigDefaults();
        setLangDefaults();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public int getMaxDoors() { return config.getInt("max-doors", 20); }
    public int getMaxLamps() { return config.getInt("max-lamps", 50); }
    public int getMaxNoteBlocks() { return config.getInt("max-noteblocks", 10); }
    public int getMaxGates() { return config.getInt("max-gates", getMaxDoors()); }
    public int getMaxTrapdoors() { return config.getInt("max-trapdoors", getMaxDoors()); }
    public int getMaxBells() { return config.getInt("max-bells", 5); }

    public String getMessage(String key) {
        return lang.getString(key, "§cNachricht nicht gefunden: " + key);
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
