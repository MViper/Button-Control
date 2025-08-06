package viper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ButtonControl extends JavaPlugin {
    private ConfigManager configManager;
    private DataManager dataManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        dataManager = new DataManager(this);

        getServer().getPluginManager().registerEvents(new ButtonListener(this, configManager, dataManager), this);

        registerRecipes();

        // Scheduler zum Prüfen der Tageslichtsensoren alle 10 Sekunden (20 Ticks = 1 Sekunde)
        getServer().getScheduler().runTaskTimer(this, this::checkDaylightSensors, 0L, 20L * 10);
    }

    private void registerRecipes() {
        ItemStack controlButton = new ItemStack(Material.STONE_BUTTON);
        ItemMeta buttonMeta = controlButton.getItemMeta();
        buttonMeta.setDisplayName("§6Steuer-Button");
        controlButton.setItemMeta(buttonMeta);

        NamespacedKey buttonKey = new NamespacedKey(this, "control_button");
        ShapedRecipe buttonRecipe = new ShapedRecipe(buttonKey, controlButton);
        buttonRecipe.shape("123", "456", "789");
        buttonRecipe.setIngredient('2', Material.STONE_BUTTON);
        buttonRecipe.setIngredient('5', Material.STONE_BUTTON);
        buttonRecipe.setIngredient('8', Material.STONE_BUTTON);
        Bukkit.addRecipe(buttonRecipe);

        ItemStack controlDaylight = new ItemStack(Material.DAYLIGHT_DETECTOR);
        ItemMeta daylightMeta = controlDaylight.getItemMeta();
        daylightMeta.setDisplayName("§6Steuer-Tageslichtsensor");
        controlDaylight.setItemMeta(daylightMeta);

        NamespacedKey daylightKey = new NamespacedKey(this, "control_daylight");
        ShapedRecipe daylightRecipe = new ShapedRecipe(daylightKey, controlDaylight);
        daylightRecipe.shape("123", "456", "789");
        daylightRecipe.setIngredient('2', Material.DAYLIGHT_DETECTOR);
        daylightRecipe.setIngredient('5', Material.DAYLIGHT_DETECTOR);
        daylightRecipe.setIngredient('8', Material.DAYLIGHT_DETECTOR);
        Bukkit.addRecipe(daylightRecipe);
    }

    // Prüft alle platzierten Tageslichtsensoren und schaltet Lampen bei Tag aus und bei Nacht an
    public void checkDaylightSensors() {
        List<String> allControllers = dataManager.getAllPlacedControllers();
        for (String controllerLoc : allControllers) {
            String buttonId = dataManager.getButtonIdForPlacedController(controllerLoc);
            if (buttonId == null) continue;

            String[] parts = controllerLoc.split(",");
            if (parts.length != 4) continue;

            World world = getServer().getWorld(parts[0]);
            if (world == null) continue;

            Location loc = new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));

            Block block = loc.getBlock();
            if (block.getType() != Material.DAYLIGHT_DETECTOR) continue;

            long time = loc.getWorld().getTime();
            boolean isDay = time >= 0 && time < 13000;

            List<String> connectedBlocks = dataManager.getConnectedBlocks(buttonId);
            if (connectedBlocks == null) continue;

            for (String targetLocStr : connectedBlocks) {
                String[] targetParts = targetLocStr.split(",");
                if (targetParts.length != 4) continue;

                World targetWorld = getServer().getWorld(targetParts[0]);
                if (targetWorld == null) continue;

                Location targetLoc = new Location(targetWorld,
                        Integer.parseInt(targetParts[1]),
                        Integer.parseInt(targetParts[2]),
                        Integer.parseInt(targetParts[3]));

                Block targetBlock = targetLoc.getBlock();

                if (targetBlock.getType() == Material.REDSTONE_LAMP) {
                    Lightable lamp = (Lightable) targetBlock.getBlockData();
                    lamp.setLit(!isDay);
                    targetBlock.setBlockData(lamp);
                }
            }
        }
    }

    // Befehlsverarbeitung
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bc") && args.length > 0 && args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("§6[ButtonControl] §7Informationen zum Plugin:");
            sender.sendMessage("§eVersion: §f" + getDescription().getVersion());
            sender.sendMessage("§eErsteller: §fM_Viper");
            sender.sendMessage("§ePlugin: §fButtonControl");
            sender.sendMessage("§eGetestet für Minecraft: §f1.21.5 - 1.21.8");
            sender.sendMessage("§eWeitere Infos: §fTüren & Lampen mit Buttons oder Tageslichtsensoren steuern");
            return true;
        }
        return false;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
