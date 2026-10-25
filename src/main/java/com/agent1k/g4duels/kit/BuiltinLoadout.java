package com.agent1k.g4duels.kit;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public enum BuiltinLoadout implements Loadout {
    OWN { @Override public void apply(@NotNull Player player) {} },
    NONE { @Override public void apply(@NotNull Player player) { player.getInventory().clear(); } };
    @Override public @NotNull String id() { return name().toLowerCase(java.util.Locale.ROOT); }
}
