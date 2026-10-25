package com.agent1k.g4duels;

import com.agent1k.g4duels.config.YamlFiles;
import com.agent1k.g4duels.storage.AsyncFiles;
import com.agent1k.g4duels.text.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

final class StorageAndTextTest {
    @TempDir Path directory;
    @Test void queuedWritesAreAtomicAndOrdered() throws Exception {
        Path file = directory.resolve("данные.yml");
        try (var files = new AsyncFiles(Logger.getLogger("Проверка"))) {
            files.save(file, "значение: первое"); files.save(file, "значение: второе").join();
        }
        assertEquals("значение: второе", Files.readString(file));
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }
    @Test void invalidYamlIsRejected() throws Exception {
        Path file = directory.resolve("данные.yml"); Files.writeString(file, "ключ: [");
        assertThrows(IllegalStateException.class, () -> YamlFiles.load(file.toFile()));
    }
    @Test void rgbAndLegacyColorsDoNotLeakFormattingIntoText() {
        var component = Text.color("&#12ABEFТекст &aцвет #FFAA00RGB");
        assertEquals("Текст цвет RGB", PlainTextComponentSerializer.plainText().serialize(component));
    }
    @Test void placeholdersAreSubstitutedWithoutRegexInterpretation() {
        assertEquals("Кит: меч$1", Text.replace("Кит: %kit%", Map.of("kit", "меч$1")));
    }
}
