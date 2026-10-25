package com.agent1k.g4duels.storage;

import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public final class AsyncFiles implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("G4Duels-файлы").factory());
    private final Logger logger;
    public AsyncFiles(@NotNull Logger logger) { this.logger = logger; }
    public @NotNull CompletableFuture<Void> save(@NotNull Path path, @NotNull String content) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(path.getParent());
                Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
                Files.writeString(temporary, content);
                try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException exception) { throw new CompletionException(exception); }
        }, executor).whenComplete((ignored, error) -> { if (error != null) logger.severe("Не удалось сохранить " + path.getFileName() + ": " + error.getMessage()); });
    }
    @Override public void close() {
        executor.shutdown();
        try { if (!executor.awaitTermination(30, TimeUnit.SECONDS)) logger.severe("Сохранение файлов ещё не завершено"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }
}
