package com.agent1k.g4duels.listener;

import com.agent1k.g4duels.G4Duels;
import com.agent1k.g4duels.duel.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;
import java.util.Optional;

public final class DuelListener implements Listener {
    private final G4Duels plugin;
    private final DuelService duels;
    private final java.util.Set<org.bukkit.Material> usableItems;
    public DuelListener(@NotNull G4Duels plugin, @NotNull DuelService duels) { this.plugin = plugin; this.duels = duels;
        usableItems = plugin.getConfig().getStringList("duel.allowed-use-items").stream().map(org.bukkit.Material::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    private boolean locked(Player player) { return duels.busy(player); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Optional<Player> attacker = event instanceof EntityDamageByEntityEvent hit ? source(hit.getDamager()) : Optional.empty();
        var duel = duels.find(victim);
        if (duels.recovering(victim)) { event.setCancelled(true); return; }
        if (duel.isEmpty()) { if (attacker.filter(this::locked).isPresent()) event.setCancelled(true); return; }
        if (duel.get().state() != DuelState.FIGHTING || attacker.filter(p -> !duel.get().opponent(victim).equals(p)).isPresent()) event.setCancelled(true);
    }
    private Optional<Player> source(Entity entity) {
        if (entity instanceof Player player) return Optional.of(player);
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return Optional.of(player);
        if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player player) return Optional.of(player);
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) return Optional.of(player);
        if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) return Optional.of(player);
        return Optional.empty();
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void outgoing(EntityDamageByEntityEvent event) {
        source(event.getDamager()).filter(this::locked).ifPresent(player -> {
            boolean valid = event.getEntity() instanceof Player target && duels.find(player)
                .filter(d -> d.state() == DuelState.FIGHTING && d.opponent(player).equals(target)).isPresent();
            if (!valid) event.setCancelled(true);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        if (!locked(event.getEntity())) return;
        event.getDrops().clear(); event.setDroppedExp(0); event.setKeepInventory(true); event.setKeepLevel(true);
        duels.find(event.getEntity()).ifPresent(d -> duels.finish(d, Optional.of(d.opponent(event.getEntity())), "finished"));
    }
    @EventHandler public void respawn(PlayerRespawnEvent event) { Bukkit.getScheduler().runTask(plugin, () -> duels.recover(event.getPlayer())); }
    @EventHandler public void join(PlayerJoinEvent event) { Bukkit.getScheduler().runTask(plugin, () -> duels.recover(event.getPlayer())); }
    @EventHandler public void quit(PlayerQuitEvent event) { duels.disconnect(event.getPlayer()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        if (duels.recovering(event.getPlayer()) || duels.find(event.getPlayer()).filter(d -> d.state() != DuelState.FIGHTING).isPresent()) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getY() != event.getTo().getY() || event.getFrom().getZ() != event.getTo().getZ()) event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) { if (locked(event.getPlayer()) && !duels.internalTeleport(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) { if (locked(event.getPlayer()) && !duels.allowedCommand(event.getMessage())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) { if (locked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player p && locked(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) { if (locked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) { if (locked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bucket(PlayerBucketEmptyEvent event) { if (locked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fill(PlayerBucketFillEvent event) { if (locked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (!locked(event.getPlayer())) return;
        boolean combat = duels.find(event.getPlayer()).filter(d -> d.state() == DuelState.FIGHTING).isPresent();
        if (!combat || event.getAction() == org.bukkit.event.block.Action.PHYSICAL) { event.setCancelled(true); return; }
        if (event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getItem() != null && !usableItems.contains(event.getItem().getType()) && !event.getItem().getType().isEdible()) event.setUseItemInHand(Event.Result.DENY);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interactEntity(PlayerInteractEntityEvent event) { if (locked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) { if (event.getPlayer() instanceof Player p && locked(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player p && locked(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player p && locked(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void armorStand(PlayerArmorStandManipulateEvent event) { if (locked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void egg(PlayerEggThrowEvent event) { if (locked(event.getPlayer())) event.setHatching(false); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityPlace(EntityPlaceEvent event) { Optional.ofNullable(event.getPlayer()).filter(this::locked).ifPresent(p -> event.setCancelled(true)); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hanging(org.bukkit.event.hanging.HangingPlaceEvent event) { Optional.ofNullable(event.getPlayer()).filter(this::locked).ifPresent(p -> event.setCancelled(true)); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fish(PlayerFishEvent event) { if (locked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void food(FoodLevelChangeEvent event) { if (event.getEntity() instanceof Player p && duels.recovering(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) { if (duels.recovering(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void experience(PlayerExpChangeEvent event) { if (locked(event.getPlayer())) event.setAmount(0); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void splash(PotionSplashEvent event) {
        Optional<Player> shooter = source(event.getPotion());
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (!(entity instanceof Player target)) continue;
            boolean isolated = locked(target) || shooter.filter(this::locked).isPresent();
            boolean allowed = shooter.flatMap(duels::find).filter(d -> d.state() == DuelState.FIGHTING && (d.players().contains(target))).isPresent();
            if (isolated && !allowed) event.setIntensity(target, 0);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lingering(LingeringPotionSplashEvent event) { if (source(event.getEntity()).filter(this::locked).isPresent()) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void cloud(AreaEffectCloudApplyEvent event) { event.getAffectedEntities().removeIf(e -> e instanceof Player p && locked(p)); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void projectile(ProjectileLaunchEvent event) {
        source(event.getEntity()).filter(this::locked).ifPresent(p -> {
            var duel = duels.find(p).filter(d -> d.state() == DuelState.FIGHTING);
            if (event.getEntity() instanceof ThrownExpBottle || duel.isEmpty()) { event.setCancelled(true); return; }
            if (event.getEntity() instanceof AbstractArrow arrow) arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            duel.get().track(event.getEntity());
        });
    }
}
