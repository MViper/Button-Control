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
        if (!configFile.exists()) plugin.saveResource("config.yml", false);
        config = YamlConfiguration.loadConfiguration(configFile);
        mergeDefaults(config, "config.yml", configFile);
        setConfigDefaults();
    }

    private void loadLang() {
        langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) plugin.saveResource("lang.yml", false);
        lang = YamlConfiguration.loadConfiguration(langFile);
        mergeDefaults(lang, "lang.yml", langFile);
        setLangDefaults();
    }

    /**
     * FIX: Null-Check VOR dem try-Block – verhindert NPE wenn Ressource nicht im JAR.
     */
    private void mergeDefaults(FileConfiguration file, String resourceName, File targetFile) {
        InputStream is = plugin.getResource(resourceName);
        if (is == null) {
            plugin.getLogger().warning(resourceName + " nicht im JAR gefunden.");
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!file.contains(key)) {
                    file.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                file.save(targetFile);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Merge-Fehler " + resourceName + ": " + e.getMessage());
        }
    }

    private void setConfigDefaults() {
        def(config, "max-doors",                 20);
        def(config, "max-lamps",                 50);
        def(config, "max-noteblocks",            10);
        def(config, "max-gates",                 20);
        def(config, "max-trapdoors",             20);
        def(config, "max-bells",                  5);
        def(config, "default-note",         "PIANO");
        def(config, "double-note-enabled",     true);
        def(config, "double-note-delay-ms",    1000);
        def(config, "motion-detection-radius",  5.0);
        def(config, "motion-close-delay-ms",   5000);
        def(config, "motion-trigger-cooldown-ms", 2000);

        // Optionales MySQL-Backend
        def(config, "mysql.enabled", false);
        def(config, "mysql.host", "127.0.0.1");
        def(config, "mysql.port", 3306);
        def(config, "mysql.database", "buttoncontrol");
        def(config, "mysql.user", "root");
        def(config, "mysql.password", "");

        // Controller-Namensanzeige beim Anschauen
        def(config, "controller-name-display.enabled", true);
        def(config, "controller-name-display.max-look-distance", 8);
        def(config, "controller-name-display.format", "§6Controller: §f%s");

        // Sounds (NEU)
        def(config, "sounds.enabled",                        true);
        def(config, "sounds.door-open",   "BLOCK_WOODEN_DOOR_OPEN");
        def(config, "sounds.door-close",  "BLOCK_WOODEN_DOOR_CLOSE");
        def(config, "sounds.iron-door-open",  "BLOCK_IRON_DOOR_OPEN");
        def(config, "sounds.iron-door-close", "BLOCK_IRON_DOOR_CLOSE");
        def(config, "sounds.lamp-on",         "BLOCK_LEVER_CLICK");
        def(config, "sounds.lamp-off",        "BLOCK_LEVER_CLICK");

        saveConfig();
    }

    private void setLangDefaults() {
        // Türen (Holz)
        def(lang, "tueren-geoeffnet",       "§aTüren wurden geöffnet.");
        def(lang, "tueren-geschlossen",     "§cTüren wurden geschlossen.");
        def(lang, "max-tueren-erreicht",    "§cMaximale Anzahl an Türen erreicht.");
        // Eisentüren (NEU)
        def(lang, "eisentueren-geoeffnet",      "§aEisentüren wurden geöffnet.");
        def(lang, "eisentueren-geschlossen",    "§cEisentüren wurden geschlossen.");
        def(lang, "eisenfallturen-geoeffnet",   "§aEisen-Falltüren wurden geöffnet.");
        def(lang, "eisenfallturen-geschlossen", "§cEisen-Falltüren wurden geschlossen.");
        // Zauntore
        def(lang, "gates-geoeffnet",        "§aZauntore wurden geöffnet.");
        def(lang, "gates-geschlossen",      "§cZauntore wurden geschlossen.");
        def(lang, "max-gates-erreicht",     "§cMaximale Anzahl an Zauntoren erreicht.");
        // Falltüren
        def(lang, "fallturen-geoeffnet",    "§aFalltüren wurden geöffnet.");
        def(lang, "fallturen-geschlossen",  "§cFalltüren wurden geschlossen.");
        def(lang, "max-fallturen-erreicht", "§cMaximale Anzahl an Falltüren erreicht.");
        // Lampen
        def(lang, "lampen-eingeschaltet",   "§aLampen wurden eingeschaltet.");
        def(lang, "lampen-ausgeschaltet",   "§cLampen wurden ausgeschaltet.");
        def(lang, "max-lampen-erreicht",    "§cMaximale Anzahl an Lampen erreicht.");
        // Glocken
        def(lang, "glocke-gelaeutet",       "§aGlocke wurde geläutet.");
        def(lang, "max-glocken-erreicht",   "§cMaximale Anzahl an Glocken erreicht.");
        // Notenblöcke
        def(lang, "notenblock-ausgeloest",      "§aNotenblock-Klingel wurde ausgelöst.");
        def(lang, "instrument-gesetzt",         "§aDein Instrument wurde auf %s gesetzt.");
        def(lang, "ungueltiges-instrument",     "§cUngültiges Instrument! /bc note <Instrument>");
        def(lang, "max-notenbloecke-erreicht",  "§cMaximale Anzahl an Notenblöcken erreicht.");
        // Kolben (vorbereitet)
        def(lang, "kolben-ausgefahren",     "§6[ButtonControl] §7Kolben wurden ausgefahren.");
        def(lang, "kolben-eingefahren",     "§6[ButtonControl] §7Kolben wurden eingezogen.");
        def(lang, "max-kolben-erreicht",    "§6[ButtonControl] §7Maximale Anzahl an Kolben erreicht.");
        // Controller
        def(lang, "block-verbunden",            "§aBlock verbunden.");
        def(lang, "block-bereits-verbunden",    "§cBlock ist bereits verbunden.");
        def(lang, "block-verbindung-entfernt",  "§7Verbindung zu abgebautem Block automatisch entfernt.");
        def(lang, "keine-bloecke-verbunden",    "§cKeine Blöcke sind verbunden.");
        def(lang, "bloecke-umgeschaltet",       "§eBlöcke wurden umgeschaltet.");
        def(lang, "controller-platziert",       "§aController platziert.");
        def(lang, "controller-entfernt",        "§cController entfernt.");
        def(lang, "controller-umbenannt",       "§aController umbenannt zu: §f%s");  // NEU
        // Trust & Berechtigungen
        def(lang, "keine-berechtigung",             "§cDu hast keine Berechtigung!");
        def(lang, "keine-berechtigung-controller",  "§cDu darfst diesen Controller nicht benutzen!");
        def(lang, "nur-besitzer-abbauen",           "§cNur der Besitzer kann diesen Controller verwalten!");
        def(lang, "spieler-nicht-gefunden",         "§cSpieler nicht gefunden.");
        def(lang, "status-geandert",                "§6[BC] §7Controller ist nun %s§7.");
        def(lang, "trust-hinzugefuegt",             "§a%s darf diesen Controller nun benutzen.");
        def(lang, "trust-entfernt",                 "§c%s wurde das Vertrauen entzogen.");
        def(lang, "kein-controller-im-blick",       "§cBitte sieh einen Controller direkt an!");
        // System
        // FIX: Korrekte Key-Bezeichnung (war fälschlich "konfiguration-reloaded")
        def(lang, "konfiguration-neugeladen",   "§aKonfiguration erfolgreich neu geladen!");
        saveLang();
    }

    private void def(FileConfiguration cfg, String key, Object value) {
        if (!cfg.contains(key)) cfg.set(key, value);
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        lang   = YamlConfiguration.loadConfiguration(langFile);
        mergeDefaults(config, "config.yml", configFile);
        mergeDefaults(lang,   "lang.yml",   langFile);
        setConfigDefaults();
        setLangDefaults();
    }

    public FileConfiguration getConfig() { return config; }

    public int getMaxDoors()       { return config.getInt("max-doors",      20); }
    public int getMaxLamps()       { return config.getInt("max-lamps",      50); }
    public int getMaxNoteBlocks()  { return config.getInt("max-noteblocks", 10); }
    public int getMaxGates()       { return config.getInt("max-gates",      20); }
    public int getMaxTrapdoors()   { return config.getInt("max-trapdoors",  20); }
    public int getMaxBells()       { return config.getInt("max-bells",       5); }

    public String getMessage(String key) {
        return lang.getString(key, "§cNachricht fehlt: " + key);
    }

    public void saveConfig() {
        try { config.save(configFile); }
        catch (IOException e) { plugin.getLogger().severe("config.yml Fehler: " + e.getMessage()); }
    }

    public void saveLang() {
        try { lang.save(langFile); }
        catch (IOException e) { plugin.getLogger().severe("lang.yml Fehler: " + e.getMessage()); }
    }
}