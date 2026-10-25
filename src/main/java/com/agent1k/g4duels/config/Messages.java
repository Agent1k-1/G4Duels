package com.agent1k.g4duels.config;

import com.agent1k.g4duels.text.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.Map;

public final class Messages {
    private final YamlConfiguration yaml;
    public Messages(@NotNull File file) { yaml = com.agent1k.g4duels.config.YamlFiles.load(file); }
    public void send(@NotNull CommandSender sender, @NotNull String key) { send(sender, key, Map.of()); }
    public void send(@NotNull CommandSender sender, @NotNull String key, @NotNull Map<String, String> values) {
        sender.sendMessage(Text.color(Text.replace(yaml.getString("prefix", "") + yaml.getString(key, key), values)));
    }
}
