package com.agent1k.g4duels.kit;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public record ServerLoadout(@NotNull String id, @NotNull KitRepository repository) implements Loadout {
    @Override public void apply(@NotNull Player player) { repository.apply(id, player); }
}
