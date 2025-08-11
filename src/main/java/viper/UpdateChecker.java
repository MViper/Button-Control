package viper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.function.Consumer;

import org.json.JSONObject; // -> Dependency in pom.xml nötig

public class UpdateChecker {

    private final JavaPlugin plugin;
    private final int resourceId;

    public UpdateChecker(JavaPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public void getVersion(final Consumer<String> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection)
                        new URL("https://api.spiget.org/v2/resources/" + resourceId + "/versions/latest").openConnection();
                connection.setRequestMethod("GET");
                connection.addRequestProperty("User-Agent", "Mozilla/5.0");
                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String response = scanner.useDelimiter("\\A").next();
                    JSONObject json = new JSONObject(response);
                    String version = json.optString("name", "").trim();
                    consumer.accept(version);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Konnte nicht nach Updates suchen: " + e.getMessage());
                consumer.accept("");
            }
        });
    }
}
