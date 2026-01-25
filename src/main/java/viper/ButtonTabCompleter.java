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

    private final List<String> commands = Arrays.asList("info", "reload", "note", "trust", "untrust", "public", "private");
    private final List<String> instruments = Arrays.asList(
            "PIANO", "BASS_DRUM", "SNARE_DRUM", "STICKS", "BASS_GUITAR", 
            "FLUTE", "BELL", "CHIME", "GUITAR", "XYLOPHONE", 
            "IRON_XYLOPHONE", "COW_BELL", "DIDGERIDOO", "BIT", "BANJO", "PLING"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        List<String> completions = new ArrayList<>();

        // Erste Ebene: /bc <Tab>
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], commands, completions);
        } 
        
        // Zweite Ebene: /bc note <Tab>
        else if (args.length == 2 && args[0].equalsIgnoreCase("note")) {
            StringUtil.copyPartialMatches(args[1], instruments, completions);
        }
        
        // Zweite Ebene: /bc trust/untrust <Tab> (Spielernamen vorschlagen)
        else if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            return null; // 'null' lässt Bukkit automatisch alle Online-Spieler vorschlagen
        }

        Collections.sort(completions);
        return completions;
    }
}