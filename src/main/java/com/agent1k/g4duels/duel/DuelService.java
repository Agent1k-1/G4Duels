package com.agent1k.g4duels.duel;

import com.agent1k.g4duels.G4Duels;
import com.agent1k.g4duels.arena.ArenaRepository;
import com.agent1k.g4duels.config.Messages;
import com.agent1k.g4duels.kit.*;
import com.agent1k.g4duels.storage.SnapshotRepository;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public final class DuelService {
    private final G4Duels plugin;
    private final ArenaRepository arenas;
    private final KitRepository kits;
    private final SnapshotRepository snapshots;
    private final Messages messages;
    private final Map<UUID, Duel> active = new HashMap<>();
    private final Map<UUID, Invitation> invitations = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> restoring = new HashSet<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final int countdown;
    private final int duration;
    private final long inviteLifetime;
    private final long inviteCooldown;
    private final int maximumInvitations;
    private final Set<String> allowedCommands;
    public DuelService(@NotNull G4Duels plugin, @NotNull ArenaRepository arenas, @NotNull KitRepository kits,
                       @NotNull SnapshotRepository snapshots, @NotNull Messages messages) {
        this.plugin = plugin; this.arenas = arenas; this.kits = kits; this.snapshots = snapshots; this.messages = messages;
        countdown = Math.max(1, plugin.getConfig().getInt("duel.countdown-seconds", 5));
        duration = Math.max(10, plugin.getConfig().getInt("duel.max-duration-seconds", 300));
        inviteLifetime = Math.max(5, plugin.getConfig().getInt("invitations.expire-seconds", 30)) * 1000L;
        inviteCooldown = Math.max(1, plugin.getConfig().getInt("invitations.cooldown-seconds", 3)) * 1000L;
        maximumInvitations = Math.max(1, plugin.getConfig().getInt("invitations.max-pending", 1000));
        allowedCommands = new HashSet<>(plugin.getConfig().getStringList("duel.allowed-commands"));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis(); invitations.values().removeIf(i -> i.expiresAt() <= now);
            cooldowns.values().removeIf(time -> time <= now);
        }, 200, 200);
    }
    public @NotNull Optional<Duel> find(@NotNull Player player) { return Optional.ofNullable(active.get(player.getUniqueId())); }
    public boolean busy(@NotNull Player player) { return active.containsKey(player.getUniqueId()) || snapshots.contains(player.getUniqueId()) || restoring.contains(player.getUniqueId()); }
    public boolean internalTeleport(@NotNull Player player) { return internalTeleports.contains(player.getUniqueId()); }
    public boolean allowedCommand(@NotNull String command) {
        String normalized = command.substring(1).strip().toLowerCase(Locale.ROOT);
        return allowedCommands.contains(normalized);
    }
    public @NotNull Optional<Loadout> loadout(@NotNull String id) {
        return Arrays.stream(BuiltinLoadout.values()).filter(mode -> mode.id().equals(id)).map(Loadout.class::cast).findFirst()
            .or(() -> kits.contains(id) ? Optional.of(new ServerLoadout(id, kits)) : Optional.empty());
    }
    public void invite(@NotNull Player sender, @NotNull Player receiver, @NotNull String kit, @NotNull Optional<String> arena) {
        if (sender.equals(receiver) || !available(sender) || !available(receiver) || busy(sender) || busy(receiver)) { messages.send(sender, "unavailable"); return; }
        if (loadout(kit).isEmpty() || arena.filter(id -> arenas.find(id).filter(a -> a.ready()).isEmpty()).isPresent()) { messages.send(sender, "invalid-selection"); return; }
        long now = System.currentTimeMillis();
        invitations.values().removeIf(i -> i.expiresAt() <= now);
        if (cooldowns.getOrDefault(sender.getUniqueId(), 0L) > now || invitations.size() >= maximumInvitations) { messages.send(sender, "cooldown"); return; }
        invitations.put(sender.getUniqueId(), new Invitation(sender.getUniqueId(), receiver.getUniqueId(), kit, arena, now + inviteLifetime));
        cooldowns.put(sender.getUniqueId(), now + inviteCooldown);
        messages.send(sender, "invite-sent", Map.of("player", receiver.getName()));
        messages.send(receiver, "invite-received", Map.of("player", sender.getName(), "kit", kit));
    }
    public void accept(@NotNull Player receiver, @NotNull String senderName) {
        Player sender = Bukkit.getPlayerExact(senderName);
        if (sender == null) { messages.send(receiver, "player-not-found"); return; }
        Invitation invitation = invitations.get(sender.getUniqueId());
        if (invitation == null || !invitation.receiver().equals(receiver.getUniqueId()) || invitation.expiresAt() <= System.currentTimeMillis()) { messages.send(receiver, "invite-missing"); return; }
        if (!available(sender) || !available(receiver) || busy(sender) || busy(receiver)) { messages.send(receiver, "unavailable"); return; }
        var selected = loadout(invitation.loadout());
        if (selected.isEmpty()) { messages.send(receiver, "invalid-selection"); return; }
        var arena = arenas.reserve(invitation.arena());
        if (arena.isEmpty()) { messages.send(receiver, "no-arena"); return; }
        invitations.values().removeIf(i -> Set.of(sender.getUniqueId(), receiver.getUniqueId()).contains(i.sender()) || Set.of(sender.getUniqueId(), receiver.getUniqueId()).contains(i.receiver()));
        Duel duel = new Duel(sender, receiver, arena.get(), selected.get());
        duel.players().forEach(p -> { p.closeInventory(); active.put(p.getUniqueId(), duel); });
        var saved = snapshots.put(Map.of(sender.getUniqueId(), PlayerSnapshot.capture(sender), receiver.getUniqueId(), PlayerSnapshot.capture(receiver)));
        saved.whenComplete((ignored, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (duel.state() == DuelState.FINISHED) return;
                if (error != null || duel.players().stream().anyMatch(p -> !p.isOnline() || p.isDead())) { finish(duel, Optional.empty(), "cancelled"); return; }
                try { prepare(duel); }
                catch (RuntimeException exception) { plugin.getLogger().severe("Ошибка запуска дуэли: " + exception.getMessage()); finish(duel, Optional.empty(), "cancelled"); }
            });
        });
    }
    private boolean available(Player player) { return player.isOnline() && !player.isDead() && !player.isInsideVehicle() && player.getGameMode() == GameMode.SURVIVAL; }
    private void prepare(Duel duel) {
        for (int index = 0; index < duel.players().size(); index++) {
            Player player = duel.players().get(index);
            player.setAllowFlight(false); player.setFlying(false);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFoodLevel(20); player.setSaturation(5); player.setExhaustion(0); player.setFireTicks(0); player.setFallDistance(0);
            player.setVelocity(new org.bukkit.util.Vector()); player.setAbsorptionAmount(0); player.setRemainingAir(player.getMaximumAir());
            duel.loadout().apply(player);
            Location destination = (index == 0 ? duel.arena().first() : duel.arena().second()).orElseThrow();
            if (!teleport(player, destination)) { finish(duel, Optional.empty(), "cancelled"); return; }
        }
        duel.state(DuelState.COUNTDOWN);
        duel.task(new BukkitRunnable() {
            private int remaining = countdown;
            @Override public void run() {
                if (remaining == 0) {
                    duel.cancelTask(); duel.state(DuelState.FIGHTING);
                    duel.players().forEach(p -> messages.send(p, "started"));
                    duel.task(Bukkit.getScheduler().runTaskLater(plugin, () -> finish(duel, Optional.empty(), "draw"), duration * 20L));
                    return;
                }
                duel.players().forEach(p -> messages.send(p, "countdown", Map.of("seconds", Integer.toString(remaining))));
                remaining--;
            }
        }.runTaskTimer(plugin, 0, 20));
    }
    public void deny(@NotNull Player receiver, @NotNull String senderName) {
        var invitation = invitations.values().stream().filter(i -> i.receiver().equals(receiver.getUniqueId()))
            .filter(i -> Optional.ofNullable(Bukkit.getPlayer(i.sender())).map(p -> p.getName().equalsIgnoreCase(senderName)).orElse(false)).findFirst();
        if (invitation.isEmpty()) { messages.send(receiver, "invite-missing"); return; }
        invitations.remove(invitation.get().sender()); messages.send(receiver, "invite-denied");
        Optional.ofNullable(Bukkit.getPlayer(invitation.get().sender())).ifPresent(p -> messages.send(p, "invite-denied"));
    }
    public void leave(@NotNull Player player) {
        find(player).ifPresentOrElse(duel -> finish(duel, Optional.of(duel.opponent(player)), "finished"), () -> messages.send(player, "not-in-duel"));
    }
    public void disconnect(@NotNull Player player) {
        invitations.remove(player.getUniqueId()); invitations.values().removeIf(i -> i.receiver().equals(player.getUniqueId()));
        find(player).ifPresent(duel -> finish(duel, Optional.of(duel.opponent(player)), "finished"));
    }
    public void finish(@NotNull Duel duel, @NotNull Optional<Player> winner, @NotNull String reason) {
        if (duel.state() == DuelState.FINISHED) return;
        duel.state(DuelState.FINISHED); duel.cancelTask(); duel.clearProjectiles();
        for (Player player : duel.players()) {
            active.remove(player.getUniqueId());
            if (player.isOnline()) messages.send(player, reason, Map.of("winner", winner.map(Player::getName).orElse("—")));
            if (player.isOnline() && !player.isDead() && player.getHealth() > 0) recover(player);
        }
        arenas.release(duel.arena());
    }
    public boolean recover(@NotNull Player player) {
        var snapshot = snapshots.find(player.getUniqueId());
        if (active.containsKey(player.getUniqueId()) || snapshot.isEmpty() || player.isDead() || player.getHealth() <= 0 || !player.isOnline() || restoring.contains(player.getUniqueId())) return false;
        if (!teleport(player, snapshot.get().location())) { messages.send(player, "restore-failed"); return false; }
        snapshot.get().restore(player);
        player.saveData();
        UUID id = player.getUniqueId(); restoring.add(id);
        snapshots.remove(id).whenComplete((ignored, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                restoring.remove(id);
                if (error != null) {
                    snapshots.put(Map.of(id, snapshot.get()));
                    if (player.isOnline()) messages.send(player, "restore-failed");
                }
            });
        });
        return true;
    }
    private boolean teleport(Player player, Location location) {
        internalTeleports.add(player.getUniqueId());
        try { return player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN); }
        finally { internalTeleports.remove(player.getUniqueId()); }
    }
    public boolean recovering(@NotNull Player player) { return (snapshots.contains(player.getUniqueId()) || restoring.contains(player.getUniqueId())) && !active.containsKey(player.getUniqueId()); }
    public void shutdown() {
        new HashSet<>(active.values()).forEach(duel -> finish(duel, Optional.empty(), "cancelled"));
        invitations.clear(); cooldowns.clear();
    }
}
