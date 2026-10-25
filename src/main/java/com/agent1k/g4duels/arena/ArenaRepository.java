package com.agent1k.g4duels.arena;

import com.agent1k.g4duels.storage.AsyncFiles;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.util.*;

public final class ArenaRepository {
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Set<String> reserved = new HashSet<>();
    private final Path path;
    private final AsyncFiles files;
    public ArenaRepository(@NotNull Path path, @NotNull AsyncFiles files) {
        this.path = path; this.files = files;
        var yaml = com.agent1k.g4duels.config.YamlFiles.load(path.toFile());
        for (String id : yaml.getKeys(false)) {
            Arena arena = new Arena(id);
            Optional.ofNullable(yaml.getLocation(id + ".first")).filter(p -> p.getWorld() != null).ifPresent(p -> arena.position(1, p));
            Optional.ofNullable(yaml.getLocation(id + ".second")).filter(p -> p.getWorld() != null).ifPresent(p -> arena.position(2, p));
            arenas.put(id, arena);
        }
    }
    public @NotNull Collection<Arena> all() { return List.copyOf(arenas.values()); }
    public @NotNull Optional<Arena> find(@NotNull String id) { return Optional.ofNullable(arenas.get(id)); }
    public boolean create(@NotNull String id) {
        if (!id.matches("[a-z0-9_-]{1,32}") || arenas.containsKey(id)) return false;
        arenas.put(id, new Arena(id)); save(); return true;
    }
    public boolean delete(@NotNull String id) {
        if (reserved.contains(id) || arenas.remove(id) == null) return false;
        save(); return true;
    }
    public boolean position(@NotNull String id, int position, @NotNull Location location) {
        if (reserved.contains(id)) return false;
        return find(id).map(a -> { a.position(position, location); save(); return true; }).orElse(false);
    }
    public @NotNull Optional<Arena> reserve(@NotNull Optional<String> requested) {
        return arenas.values().stream().filter(Arena::ready).filter(a -> !reserved.contains(a.id()))
            .filter(a -> requested.map(id -> id.equals(a.id())).orElse(true)).findFirst().map(a -> { reserved.add(a.id()); return a; });
    }
    public void release(@NotNull Arena arena) { reserved.remove(arena.id()); }
    private void save() {
        var yaml = new YamlConfiguration();
        arenas.forEach((id, arena) -> { yaml.set(id + ".enabled", true); arena.first().ifPresent(p -> yaml.set(id + ".first", p)); arena.second().ifPresent(p -> yaml.set(id + ".second", p)); });
        files.save(path, yaml.saveToString());
    }
}
