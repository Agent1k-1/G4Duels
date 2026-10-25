package com.agent1k.g4duels.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.jetbrains.annotations.NotNull;
import java.io.*;

public final class YamlFiles {
    private YamlFiles() {}
    public static @NotNull YamlConfiguration load(@NotNull File file) {
        var yaml = new YamlConfiguration();
        if (!file.exists()) return yaml;
        try { yaml.load(file); return yaml; }
        catch (IOException | InvalidConfigurationException exception) { throw new IllegalStateException("Не удалось прочитать " + file.getName(), exception); }
    }
}
