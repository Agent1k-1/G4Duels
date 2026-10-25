package com.agent1k.g4duels.action;

import com.agent1k.g4duels.menu.MenuContext;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface Action {
    void execute(@NotNull Player player, @NotNull MenuContext context, @NotNull String value);
}
