package com.agent1k.g4duels;

import com.agent1k.g4duels.arena.ArenaRepository;
import com.agent1k.g4duels.command.DuelCommand;
import com.agent1k.g4duels.config.Messages;
import com.agent1k.g4duels.duel.DuelService;
import com.agent1k.g4duels.kit.KitRepository;
import com.agent1k.g4duels.listener.DuelListener;
import com.agent1k.g4duels.menu.MenuService;
import com.agent1k.g4duels.storage.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.*;

public class G4Duels extends JavaPlugin {
    private Optional<AsyncFiles> files = Optional.empty();
    private Optional<Messages> messages = Optional.empty();
    private Optional<DuelService> duels = Optional.empty();
    private Optional<MenuService> menus = Optional.empty();
    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            for (String resource : List.of("messages.yml", "arenas.yml", "kits.yml", "menus/duel.yml", "menus/kits.yml", "menus/arenas.yml"))
                if (!new File(getDataFolder(), resource).exists()) saveResource(resource, false);
            AsyncFiles storage = new AsyncFiles(getLogger()); files = Optional.of(storage);
            Messages text = new Messages(new File(getDataFolder(), "messages.yml")); messages = Optional.of(text);
            var arenas = new ArenaRepository(getDataFolder().toPath().resolve("arenas.yml"), storage);
            var kits = new KitRepository(getDataFolder().toPath().resolve("kits.yml"), storage);
            var snapshots = new SnapshotRepository(getDataFolder().toPath().resolve("recovery.yml"), storage);
            var service = new DuelService(this, arenas, kits, snapshots, text); duels = Optional.of(service);
            var menuService = new MenuService(this, service, kits, arenas); menus = Optional.of(menuService);
            var command = new DuelCommand(this, service, kits, arenas, menuService);
            var registered = Objects.requireNonNull(getCommand("duel")); registered.setExecutor(command); registered.setTabCompleter(command);
            getServer().getPluginManager().registerEvents(menuService, this);
            getServer().getPluginManager().registerEvents(new DuelListener(this, service), this);
            Bukkit.getOnlinePlayers().forEach(service::recover);
            getLogger().info("G4Duels включён");
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Не удалось включить G4Duels", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    public @NotNull Messages messages() { return messages.orElseThrow(); }
    @Override public void onDisable() {
        try { menus.ifPresent(MenuService::closeAll); }
        finally { try { duels.ifPresent(DuelService::shutdown); } finally { files.ifPresent(AsyncFiles::close); } }
    }
}
