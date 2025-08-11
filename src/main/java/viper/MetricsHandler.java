package viper;

import org.bukkit.plugin.java.JavaPlugin;
// Import aus dem korrekten verschobenen Package:
import org.bstats.bukkit.Metrics;

public class MetricsHandler {

    private static final int BSTATS_PLUGIN_ID = 26862;

    public static void startMetrics(JavaPlugin plugin) {
        new Metrics(plugin, BSTATS_PLUGIN_ID);
    }
}
