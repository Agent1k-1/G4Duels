package com.agent1k.g4duels.menu;

import com.agent1k.g4duels.G4Duels;
import com.agent1k.g4duels.action.ActionRegistry;
import com.agent1k.g4duels.arena.ArenaRepository;
import com.agent1k.g4duels.duel.DuelService;
import com.agent1k.g4duels.kit.KitRepository;
import com.agent1k.g4duels.text.Text;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.*;

public final class MenuService implements Listener {
    private final G4Duels plugin;
    private final DuelService duels;
    private final KitRepository kits;
    private final ArenaRepository arenas;
    private final Map<String, YamlConfiguration> menus = new HashMap<>();
    private final ActionRegistry actions;
    public MenuService(@NotNull G4Duels plugin, @NotNull DuelService duels, @NotNull KitRepository kits, @NotNull ArenaRepository arenas) {
        this.plugin = plugin; this.duels = duels; this.kits = kits; this.arenas = arenas;
        actions = new ActionRegistry(plugin, this, duels);
        for (String name : List.of("duel", "kits", "arenas")) {
            var yaml = com.agent1k.g4duels.config.YamlFiles.load(new File(plugin.getDataFolder(), "menus/" + name + ".yml"));
            int size = yaml.getInt("size");
            if (size < 9 || size > 54 || size % 9 != 0) throw new IllegalArgumentException("Неверный размер меню: " + name);
            var items = yaml.getConfigurationSection("items");
            if (items != null) for (String key : items.getKeys(false)) {
                var section = Objects.requireNonNull(items.getConfigurationSection(key));
                validate(section);
                int slot = section.getInt("slot", -1);
                if (slot < 0 || slot >= size) throw new IllegalArgumentException("Неверный слот: " + name + "." + key);
            }
            if (yaml.isConfigurationSection("dynamic")) {
                var dynamic = Objects.requireNonNull(yaml.getConfigurationSection("dynamic")); validate(dynamic);
                var slots = dynamic.getIntegerList("slots");
                if (slots.isEmpty() || new HashSet<>(slots).size() != slots.size() || slots.stream().anyMatch(slot -> slot < 0 || slot >= size)) throw new IllegalArgumentException("Неверные динамические слоты: " + name);
            }
            menus.put(name, yaml);
        }
    }
    private void validate(ConfigurationSection section) {
        if (Material.matchMaterial(section.getString("material", "STONE")) == null) throw new IllegalArgumentException("Неизвестный материал меню");
        for (String key : List.of("left_click_action", "right_click_action")) for (String line : section.getStringList(key))
            if (!actions.valid(line)) throw new IllegalArgumentException("Неизвестное действие: " + line);
    }
    public void open(@NotNull Player player, @NotNull String name, @NotNull MenuContext context) {
        if (duels.busy(player)) { plugin.messages().send(player, "unavailable"); return; }
        YamlConfiguration yaml = menus.get(name);
        if (yaml == null) return;
        ConfigurationSection dynamic = yaml.getConfigurationSection("dynamic");
        List<String> entries = name.equals("kits") ? kits.names().stream().sorted().toList() : arenas.all().stream().filter(a -> a.ready()).map(a -> a.id()).sorted().toList();
        if (dynamic != null) context.page(Math.min(context.page(), Math.max(0, (entries.size() - 1) / dynamic.getIntegerList("slots").size())));
        Map<String, String> values = new HashMap<>();
        values.put("player", player.getName()); values.put("target", Optional.ofNullable(Bukkit.getPlayer(context.target())).map(Player::getName).orElse("Не в сети"));
        values.put("kit", context.kit()); values.put("arena", context.arena().orElse("Автоматически")); values.put("page", Integer.toString(context.page() + 1));
        MenuHolder holder = new MenuHolder(context);
        Inventory inventory = Bukkit.createInventory(holder, yaml.getInt("size"), Text.color(Text.replace(yaml.getString("title", "Дуэли"), values)));
        holder.inventory(inventory);
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) {
            ConfigurationSection section = Objects.requireNonNull(items.getConfigurationSection(key));
            put(holder, section.getInt("slot"), section, values);
        }
        if (dynamic != null) {
            List<Integer> slots = dynamic.getIntegerList("slots");
            for (int index = 0; index < slots.size(); index++) {
                int entry = context.page() * slots.size() + index;
                if (entry >= entries.size()) break;
                var specific = new HashMap<>(values); specific.put("entry", entries.get(entry));
                put(holder, slots.get(index), dynamic, specific);
            }
        }
        player.openInventory(inventory);
    }
    private void put(MenuHolder holder, int slot, ConfigurationSection section, Map<String, String> values) {
        Material material = Objects.requireNonNull(Material.matchMaterial(section.getString("material", "STONE")));
        ItemStack item = new ItemStack(material); var meta = item.getItemMeta();
        meta.displayName(Text.color(Text.replace(section.getString("name", ""), values)));
        meta.lore(section.getStringList("lore").stream().map(line -> Text.color(Text.replace(line, values))).toList());
        item.setItemMeta(meta); holder.getInventory().setItem(slot, item);
        holder.button(slot, new MenuHolder.Button(section.getStringList("left_click_action"), section.getStringList("right_click_action"), Map.copyOf(values)));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || duels.busy(player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        holder.button(event.getRawSlot()).ifPresent(button -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || duels.busy(player) || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            actions.execute(player, holder.context(), event.getClick() == ClickType.LEFT ? button.left() : button.right(), button.values());
        }));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true); }
    public void closeAll() { Bukkit.getOnlinePlayers().stream().filter(p -> Optional.ofNullable(p.getOpenInventory().getTopInventory()).map(i -> i.getHolder() instanceof MenuHolder).orElse(false)).forEach(Player::closeInventory); }
}
