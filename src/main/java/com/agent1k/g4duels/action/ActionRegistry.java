package com.agent1k.g4duels.action;

import com.agent1k.g4duels.G4Duels;
import com.agent1k.g4duels.duel.DuelService;
import com.agent1k.g4duels.menu.*;
import com.agent1k.g4duels.text.Text;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.regex.Pattern;

public final class ActionRegistry {
    private static final Pattern FORMAT = Pattern.compile("^\\[([a-z_]+)](?:\\s+(.*))?$");
    private final Map<String, Action> actions = new HashMap<>();
    private final G4Duels plugin;
    public ActionRegistry(@NotNull G4Duels plugin, @NotNull MenuService menus, @NotNull DuelService duels) {
        this.plugin = plugin;
        MessageAction message = new MessageAction(); actions.put(message.name(), message);
        actions.put("close", (p, c, v) -> p.closeInventory());
        actions.put("sound", (p, c, v) -> p.playSound(p.getLocation(), Sound.valueOf(v.toUpperCase(Locale.ROOT)), 1, 1));
        actions.put("actionbar", (p, c, v) -> p.sendActionBar(Text.color(v)));
        actions.put("menu", (p, c, v) -> { c.page(0); menus.open(p, v, c); });
        actions.put("kit", (p, c, v) -> { if (duels.loadout(v).isPresent()) { c.kit(v); c.page(0); menus.open(p, "duel", c); } });
        actions.put("arena", (p, c, v) -> { c.arena(v); c.page(0); menus.open(p, "duel", c); });
        actions.put("page", (p, c, v) -> { String[] parts = v.split(" ", 2); c.page(c.page() + Integer.parseInt(parts[1])); menus.open(p, parts[0], c); });
        actions.put("invite", (p, c, v) -> {
            p.closeInventory(); Optional.ofNullable(Bukkit.getPlayer(c.target())).ifPresentOrElse(target -> duels.invite(p, target, c.kit(), c.arena()), () -> plugin.messages().send(p, "player-not-found"));
        });
    }
    public boolean valid(@NotNull String line) { var match = FORMAT.matcher(line); return match.matches() && actions.containsKey(match.group(1)); }
    public void execute(@NotNull Player player, @NotNull MenuContext context, @NotNull List<String> lines, @NotNull Map<String, String> values) {
        for (String line : lines) {
            var match = FORMAT.matcher(line);
            if (!match.matches()) continue;
            Action action = actions.get(match.group(1));
            if (action == null) continue;
            try { action.execute(player, context, Text.replace(Optional.ofNullable(match.group(2)).orElse(""), values)); }
            catch (IllegalArgumentException exception) { plugin.getLogger().warning("Некорректное действие меню: " + line); }
        }
    }
}
