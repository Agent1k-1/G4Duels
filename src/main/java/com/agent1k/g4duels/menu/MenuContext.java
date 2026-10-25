package com.agent1k.g4duels.menu;

import org.jetbrains.annotations.NotNull;
import java.util.*;

public final class MenuContext {
    private final UUID target;
    private String kit = "own";
    private Optional<String> arena = Optional.empty();
    private int page;
    public MenuContext(@NotNull UUID target) { this.target = target; }
    public @NotNull UUID target() { return target; }
    public @NotNull String kit() { return kit; }
    public void kit(@NotNull String kit) { this.kit = kit; }
    public @NotNull Optional<String> arena() { return arena; }
    public void arena(@NotNull String arena) { this.arena = arena.equals("auto") ? Optional.empty() : Optional.of(arena); }
    public int page() { return page; }
    public void page(int page) { this.page = Math.max(0, page); }
}
