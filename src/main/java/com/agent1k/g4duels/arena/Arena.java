package com.agent1k.g4duels.arena;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import java.util.Optional;

public final class Arena {
    private final String id;
    private Optional<Location> first = Optional.empty();
    private Optional<Location> second = Optional.empty();
    public Arena(@NotNull String id) { this.id = id; }
    public @NotNull String id() { return id; }
    public @NotNull Optional<Location> first() { return first.map(Location::clone); }
    public @NotNull Optional<Location> second() { return second.map(Location::clone); }
    public void position(int position, @NotNull Location location) {
        if (position == 1) first = Optional.of(location.clone());
        else second = Optional.of(location.clone());
    }
    public boolean ready() { return first.isPresent() && second.isPresent() && first.get().getWorld().equals(second.get().getWorld()); }
}
