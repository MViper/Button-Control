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
        "trust", "untrust", "public", "private"
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
            }
        }

        Collections.sort(completions);
        return completions;
    }
}