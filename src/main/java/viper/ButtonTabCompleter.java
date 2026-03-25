package viper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ButtonTabCompleter implements TabCompleter {

    private final List<String> commands = Arrays.asList(
        "info", "reload", "note", "list", "rename", "schedule",
        "trust", "untrust", "public", "private", "undo", "secret"
    );

    private final List<String> instruments = Arrays.asList(
        "PIANO", "BASS_DRUM", "SNARE_DRUM", "STICKS", "BASS_GUITAR",
        "FLUTE", "BELL", "CHIME", "GUITAR", "XYLOPHONE",
        "IRON_XYLOPHONE", "COW_BELL", "DIDGERIDOO", "BIT", "BANJO", "PLING"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], commands, completions);

        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "note":
                    StringUtil.copyPartialMatches(args[1], instruments, completions);
                    break;
                case "trust":
                case "untrust":
                    return null; // Bukkit schlägt automatisch Online-Spieler vor
                case "rename":
                    completions.add("<Name>");
                    break;
                case "secret":
                    StringUtil.copyPartialMatches(args[1], Arrays.asList("select", "info", "add", "remove", "clear", "delay", "animation"), completions);
                    break;
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("secret") && args[1].equalsIgnoreCase("delay")) {
            completions.add("3");
            completions.add("5");
            completions.add("10");
            completions.add("30");
            completions.add("60");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("secret") && args[1].equalsIgnoreCase("animation")) {
            completions.add("instant");
            completions.add("wave");
            completions.add("reverse");
            completions.add("center");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("secret")) {
            if (completions.isEmpty()) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("select", "info", "add", "remove", "clear", "delay", "animation"), completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}