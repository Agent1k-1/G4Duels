package com.agent1k.g4duels.storage;

import com.agent1k.g4duels.duel.PlayerSnapshot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class SnapshotRepository {
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final Path path;
    private final AsyncFiles files;
    public SnapshotRepository(@NotNull Path path, @NotNull AsyncFiles files) {
        this.path = path; this.files = files;
        var yaml = com.agent1k.g4duels.config.YamlFiles.load(path.toFile());
        for (String key : yaml.getKeys(false)) snapshots.put(UUID.fromString(key), PlayerSnapshot.read(Objects.requireNonNull(yaml.getConfigurationSection(key))));
    }
    public boolean contains(@NotNull UUID player) { return snapshots.containsKey(player); }
    public @NotNull Optional<PlayerSnapshot> find(@NotNull UUID player) { return Optional.ofNullable(snapshots.get(player)); }
    public @NotNull CompletableFuture<Void> put(@NotNull Map<UUID, PlayerSnapshot> entries) { snapshots.putAll(entries); return save(); }
    public @NotNull CompletableFuture<Void> remove(@NotNull UUID player) { snapshots.remove(player); return save(); }
    private CompletableFuture<Void> save() {
        var yaml = new YamlConfiguration(); snapshots.forEach((id, snapshot) -> snapshot.write(yaml.createSection(id.toString())));
        return files.save(path, yaml.saveToString());
    }
}
