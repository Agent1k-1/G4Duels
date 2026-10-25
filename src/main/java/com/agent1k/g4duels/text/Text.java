package com.agent1k.g4duels.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import java.util.Map;

public final class Text {
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder().character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private Text() {}
    public static @NotNull Component color(@NotNull String value) {
        return SERIALIZER.deserialize(value.replaceAll("(?<!&)#([A-Fa-f0-9]{6})", "&#$1"));
    }
    public static @NotNull String replace(@NotNull String value, @NotNull Map<String, String> placeholders) {
        for (var entry : placeholders.entrySet()) value = value.replace("%" + entry.getKey() + "%", entry.getValue());
        return value;
    }
}
