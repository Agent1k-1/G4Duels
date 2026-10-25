package com.agent1k.g4duels.action;

import org.jetbrains.annotations.NotNull;

public abstract class AbstractAction implements Action {
    private final String name;
    protected AbstractAction(@NotNull String name) { this.name = name; }
    public final @NotNull String name() { return name; }
}
