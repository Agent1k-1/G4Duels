package com.agent1k.g4duels.kit;

import com.agent1k.g4duels.storage.AsyncFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.util.*;

public final class KitRepository {
    private final Map<String, ItemStack[]> kits = new LinkedHashMap<>();
    private final Path path;
    private final AsyncFiles files;
    public KitRepository(@NotNull Path path, @NotNull AsyncFiles files) {
        this.path = path; this.files = files;
        var yaml = com.agent1k.g4duels.config.YamlFiles.load(path.toFile());
        for (String id : yaml.getKeys(false)) {
            var items = yaml.getList(id, List.of());
            if (items.size() != 41 || items.stream().anyMatch(i -> !(i instanceof ItemStack))) throw new IllegalArgumentException("Некорректный кит: " + id);
            kits.put(id, items.stream().map(ItemStack.class::cast).map(ItemStack::clone).toArray(ItemStack[]::new));
        }
    }
    public @NotNull Set<String> names() { return Set.copyOf(kits.keySet()); }
    public boolean contains(@NotNull String id) { return kits.containsKey(id); }
    public boolean save(@NotNull String id, @NotNull Player player) {
        if (!id.matches("[a-z0-9_-]{1,32}") || Set.of("own", "none").contains(id)) return false;
        kits.put(id, cloneItems(player.getInventory().getContents())); persist(); return true;
    }
    public boolean delete(@NotNull String id) { if (kits.remove(id) == null) return false; persist(); return true; }
    public void apply(@NotNull String id, @NotNull Player player) { player.getInventory().setContents(cloneItems(Objects.requireNonNull(kits.get(id), "Кит не найден"))); }
    private static ItemStack[] cloneItems(ItemStack[] items) { return Arrays.stream(items).map(i -> i == null ? new ItemStack(org.bukkit.Material.AIR) : i.clone()).toArray(ItemStack[]::new); }
    private void persist() {
        var yaml = new YamlConfiguration(); kits.forEach((id, items) -> yaml.set(id, Arrays.asList(items))); files.save(path, yaml.saveToString());
    }
}
