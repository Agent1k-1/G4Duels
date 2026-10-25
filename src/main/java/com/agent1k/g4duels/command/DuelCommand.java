package com.agent1k.g4duels.command;

import com.agent1k.g4duels.G4Duels;
import com.agent1k.g4duels.arena.ArenaRepository;
import com.agent1k.g4duels.duel.DuelService;
import com.agent1k.g4duels.kit.KitRepository;
import com.agent1k.g4duels.menu.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public final class DuelCommand implements CommandExecutor, TabCompleter {
    private final G4Duels plugin;
    private final DuelService duels;
    private final KitRepository kits;
    private final ArenaRepository arenas;
    private final MenuService menus;
    private final Map<String, BiConsumer<Player, String[]>> commands = new LinkedHashMap<>();
    private final Map<String, BiConsumer<Player, String[]>> arenaCommands = new LinkedHashMap<>();
    private final Map<String, BiConsumer<Player, String[]>> kitCommands = new LinkedHashMap<>();
    public DuelCommand(@NotNull G4Duels plugin, @NotNull DuelService duels, @NotNull KitRepository kits,
                       @NotNull ArenaRepository arenas, @NotNull MenuService menus) {
        this.plugin = plugin; this.duels = duels; this.kits = kits; this.arenas = arenas; this.menus = menus;
        commands.put("accept", (p, a) -> { if (a.length == 2) duels.accept(p, a[1]); else help(p); });
        commands.put("deny", (p, a) -> { if (a.length == 2) duels.deny(p, a[1]); else help(p); });
        commands.put("leave", (p, a) -> duels.leave(p));
        commands.put("restore", (p, a) -> { if (!duels.busy(p) || duels.recovering(p)) duels.recover(p); });
        commands.put("help", (p, a) -> help(p));
        commands.put("arena", (p, a) -> admin(p, a, arenaCommands));
        commands.put("kit", (p, a) -> admin(p, a, kitCommands));
        arenaCommands.put("create", (p, a) -> result(p, a.length == 3 && arenas.create(a[2])));
        arenaCommands.put("delete", (p, a) -> result(p, a.length == 3 && arenas.delete(a[2])));
        arenaCommands.put("pos1", (p, a) -> result(p, a.length == 3 && arenas.position(a[2], 1, p.getLocation())));
        arenaCommands.put("pos2", (p, a) -> result(p, a.length == 3 && arenas.position(a[2], 2, p.getLocation())));
        arenaCommands.put("list", (p, a) -> plugin.messages().send(p, "list", Map.of("entries", String.join(", ", arenas.all().stream().map(arena -> arena.id() + (arena.ready() ? "" : " (не настроена)")).toList()))));
        kitCommands.put("save", (p, a) -> result(p, a.length == 3 && kits.save(a[2], p)));
        kitCommands.put("delete", (p, a) -> result(p, a.length == 3 && kits.delete(a[2])));
        kitCommands.put("list", (p, a) -> plugin.messages().send(p, "list", Map.of("entries", String.join(", ", kits.names()))));
    }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { plugin.messages().send(sender, "only-player"); return true; }
        if (!player.hasPermission("g4duels.use")) { plugin.messages().send(player, "no-permission"); return true; }
        if (args.length == 0) { help(player); return true; }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (duels.busy(player) && !Set.of("leave", "restore", "help").contains(subcommand)) { plugin.messages().send(player, "unavailable"); return true; }
        BiConsumer<Player, String[]> handler = commands.get(subcommand);
        if (handler != null) { handler.accept(player, args); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !player.canSee(target)) { plugin.messages().send(player, "player-not-found"); return true; }
        if (target.equals(player)) { plugin.messages().send(player, "unavailable"); return true; }
        if (args.length == 1) menus.open(player, "duel", new MenuContext(target.getUniqueId()));
        else if (args.length <= 3) duels.invite(player, target, args[1].toLowerCase(Locale.ROOT), args.length == 3 ? Optional.of(args[2].toLowerCase(Locale.ROOT)) : Optional.empty());
        else help(player);
        return true;
    }
    private void admin(Player player, String[] args, Map<String, BiConsumer<Player, String[]>> handlers) {
        if (!player.hasPermission("g4duels.admin")) { plugin.messages().send(player, "no-permission"); return; }
        if (duels.busy(player)) { plugin.messages().send(player, "unavailable"); return; }
        if (args.length < 2) { help(player); return; }
        Optional.ofNullable(handlers.get(args[1].toLowerCase(Locale.ROOT))).ifPresentOrElse(action -> action.accept(player, args), () -> help(player));
    }
    private void result(Player player, boolean success) { plugin.messages().send(player, success ? "saved" : "operation-failed"); }
    private void help(Player player) { plugin.messages().send(player, "help"); if (player.hasPermission("g4duels.admin")) plugin.messages().send(player, "admin-help"); }
    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("g4duels.use")) return List.of();
        if (args.length == 1) return filter(Stream.concat(commands.keySet().stream().filter(k -> !Set.of("arena", "kit").contains(k) || sender.hasPermission("g4duels.admin")),
            Bukkit.getOnlinePlayers().stream().filter(p -> !p.equals(player) && player.canSee(p)).map(Player::getName)), args[0]);
        boolean admin = sender.hasPermission("g4duels.admin");
        String root = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && admin && root.equals("arena")) return filter(arenaCommands.keySet().stream(), args[1]);
        if (args.length == 2 && admin && root.equals("kit")) return filter(kitCommands.keySet().stream(), args[1]);
        if (args.length == 3 && admin && root.equals("arena")) return filter(arenas.all().stream().map(a -> a.id()), args[2]);
        if (args.length == 3 && admin && root.equals("kit")) return filter(kits.names().stream(), args[2]);
        if (args.length == 2 && Set.of("accept", "deny").contains(root)) return filter(Bukkit.getOnlinePlayers().stream().filter(player::canSee).map(Player::getName), args[1]);
        if (args.length == 2 && !commands.containsKey(root)) return filter(Stream.concat(Stream.of("own", "none"), kits.names().stream()), args[1]);
        if (args.length == 3 && !commands.containsKey(root)) return filter(arenas.all().stream().filter(a -> a.ready()).map(a -> a.id()), args[2]);
        return List.of();
    }
    private List<String> filter(Stream<String> values, String prefix) { return values.filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).distinct().sorted().toList(); }
}
