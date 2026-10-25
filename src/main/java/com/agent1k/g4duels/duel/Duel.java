package com.agent1k.g4duels.duel;

import com.agent1k.g4duels.arena.Arena;
import com.agent1k.g4duels.kit.Loadout;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public final class Duel {
    private final List<Player> players;
    private final Arena arena;
    private final Loadout loadout;
    private final Set<org.bukkit.entity.Projectile> projectiles = new HashSet<>();
    private DuelState state = DuelState.PREPARING;
    private Optional<BukkitTask> task = Optional.empty();
    public Duel(@NotNull Player first, @NotNull Player second, @NotNull Arena arena, @NotNull Loadout loadout) {
        this.players = List.of(first, second); this.arena = arena; this.loadout = loadout;
    }
    public @NotNull List<Player> players() { return players; }
    public @NotNull Player opponent(@NotNull Player player) { return players.get(players.getFirst().equals(player) ? 1 : 0); }
    public @NotNull Arena arena() { return arena; }
    public @NotNull Loadout loadout() { return loadout; }
    public @NotNull DuelState state() { return state; }
    public void state(@NotNull DuelState state) { this.state = state; }
    public void task(@NotNull BukkitTask task) { this.task = Optional.of(task); }
    public void track(@NotNull org.bukkit.entity.Projectile projectile) { projectiles.add(projectile); }
    public void clearProjectiles() { projectiles.forEach(org.bukkit.entity.Entity::remove); projectiles.clear(); }
    public void cancelTask() { task.ifPresent(BukkitTask::cancel); task = Optional.empty(); }
}
