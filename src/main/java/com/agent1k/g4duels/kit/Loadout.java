package com.agent1k.g4duels.kit;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface Loadout {
    @NotNull String id();
    void apply(@NotNull Player player);
}
