package com.agent1k.g4duels.duel;

import org.jetbrains.annotations.NotNull;
import java.util.*;

public record Invitation(@NotNull UUID sender, @NotNull UUID receiver, @NotNull String loadout,
                         @NotNull Optional<String> arena, long expiresAt) {}
