package com.agent1k.g4duels.menu;

import org.bukkit.inventory.*;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public final class MenuHolder implements InventoryHolder {
    public record Button(@NotNull List<String> left, @NotNull List<String> right, @NotNull Map<String, String> values) {}
    private final MenuContext context;
    private final Map<Integer, Button> buttons = new HashMap<>();
    private Optional<Inventory> inventory = Optional.empty();
    public MenuHolder(@NotNull MenuContext context) { this.context = context; }
    public @NotNull MenuContext context() { return context; }
    public void inventory(@NotNull Inventory inventory) { this.inventory = Optional.of(inventory); }
    public void button(int slot, @NotNull Button button) { buttons.put(slot, button); }
    public @NotNull Optional<Button> button(int slot) { return Optional.ofNullable(buttons.get(slot)); }
    @Override public @NotNull Inventory getInventory() { return inventory.orElseThrow(); }
}
