package com.agent1k.g4duels;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import java.nio.file.Files;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

final class DuelIntegrationTest {
    private ServerMock server;
    private G4Duels plugin;
    private PlayerMock first;
    private PlayerMock second;
    private WorldMock world;
    private static final class TestServer extends ServerMock {
        @Override public org.mockbukkit.mockbukkit.inventory.InventoryMock createInventory(org.bukkit.inventory.InventoryHolder holder, int size, net.kyori.adventure.text.Component title) {
            return super.createInventory(holder, size, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(title));
        }
    }
    private static final class TestPlayer extends PlayerMock {
        private float exhaustion;
        private ItemStack[] persisted = new ItemStack[0];
        TestPlayer(ServerMock server, String name) { super(server, name); }
        @Override public float getExhaustion() { return exhaustion; }
        @Override public void setExhaustion(float value) { exhaustion = value; }
        @Override public void saveData() { persisted = getInventory().getContents(); }
    }
    @BeforeEach void setup() {
        server = MockBukkit.mock(new TestServer());
        world = server.addSimpleWorld("world");
        org.bukkit.configuration.serialization.ConfigurationSerialization.registerClass(org.mockbukkit.mockbukkit.inventory.ItemStackMock.class);
        plugin = MockBukkit.load(G4Duels.class);
        first = new TestPlayer(server, "Первый"); second = new TestPlayer(server, "Второй");
        server.addPlayer(first); server.addPlayer(second);
        first.setOp(true); second.setOp(true);
        first.teleport(new Location(world, 0, 70, 0)); second.teleport(new Location(world, 10, 70, 0));
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    private void command(PlayerMock player, String command) { assertTrue(server.dispatchCommand(player, "duel " + command)); }
    private void arena() {
        command(first, "arena create test"); command(first, "arena pos1 test"); command(second, "arena pos2 test");
    }
    private void drainUntil(java.util.function.BooleanSupplier condition) {
        assertTimeout(Duration.ofSeconds(10), () -> {
            long deadline = System.nanoTime() + Duration.ofSeconds(9).toNanos();
            while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(5); server.getScheduler().performOneTick(); }
            assertTrue(condition.getAsBoolean(), "Ожидаемое состояние не достигнуто");
        });
    }
    @Test void enableCreatesConfiguredMenus() {
        assertTrue(plugin.isEnabled());
        command(first, second.getName());
        assertEquals(27, first.getOpenInventory().getTopInventory().getSize());
        assertTrue(Files.exists(plugin.getDataFolder().toPath().resolve("menus/kits.yml")));
    }
    @Test void emptyLoadoutRestoresBothInventoriesOnSurrender() {
        arena(); first.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
        second.getInventory().setItem(0, new ItemStack(Material.EMERALD, 4));
        command(first, second.getName() + " none test"); command(second, "accept " + first.getName());
        drainUntil(() -> first.getInventory().isEmpty());
        assertTrue(second.getInventory().isEmpty());
        server.getScheduler().performTicks(110);
        command(first, "leave");
        drainUntil(() -> first.getInventory().contains(Material.DIAMOND));
        assertEquals(7, first.getInventory().getItem(0).getAmount());
        assertEquals(4, second.getInventory().getItem(0).getAmount());
        assertEquals(7, ((TestPlayer) first).persisted[0].getAmount());
        drainUntil(() -> Files.exists(plugin.getDataFolder().toPath().resolve("recovery.yml")) && !readRecovery().contains(first.getUniqueId().toString()) && !readRecovery().contains(second.getUniqueId().toString()));
    }
    private String readRecovery() {
        try { return Files.readString(plugin.getDataFolder().toPath().resolve("recovery.yml")); }
        catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
    @Test void thirdPlayerCannotDamageDuelParticipants() {
        arena(); command(first, second.getName() + " own test"); command(second, "accept " + first.getName());
        drainUntil(() -> readRecoveryIfPresent().contains(first.getUniqueId().toString()));
        server.getScheduler().performTicks(120);
        PlayerMock outsider = server.addPlayer("Посторонний");
        var event = new EntityDamageByEntityEvent(outsider, first, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4);
        server.getPluginManager().callEvent(event); assertTrue(event.isCancelled());
        var allowed = new EntityDamageByEntityEvent(second, first, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4);
        server.getPluginManager().callEvent(allowed); assertFalse(allowed.isCancelled());
    }
    private String readRecoveryIfPresent() { return Files.exists(plugin.getDataFolder().toPath().resolve("recovery.yml")) ? readRecovery() : ""; }
    @Test void invitationCannotBeAcceptedByAnotherPlayer() {
        arena(); first.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        command(first, second.getName() + " none test");
        PlayerMock outsider = server.addPlayer("Посторонний"); command(outsider, "accept " + first.getName());
        server.getScheduler().performTicks(120); assertTrue(first.getInventory().contains(Material.DIAMOND));
        assertFalse(Files.exists(plugin.getDataFolder().toPath().resolve("recovery.yml")));
    }

    @Test void serverKitIsAppliedAndOriginalItemsReturn() {
        arena(); first.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        command(first, "kit save classic");
        first.getInventory().clear(); first.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
        second.getInventory().setItem(0, new ItemStack(Material.EMERALD, 2));
        command(first, second.getName() + " classic test"); command(second, "accept " + first.getName());
        drainUntil(() -> first.getInventory().contains(Material.IRON_SWORD));
        assertTrue(second.getInventory().contains(Material.IRON_SWORD));
        command(second, "leave");
        assertEquals(3, first.getInventory().getItem(0).getAmount());
        assertEquals(Material.DIAMOND, first.getInventory().getItem(0).getType());
        assertEquals(Material.EMERALD, second.getInventory().getItem(0).getType());
    }
    @Test void damageIsBlockedDuringCountdown() {
        arena(); first.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        command(first, second.getName() + " none test"); command(second, "accept " + first.getName());
        drainUntil(() -> first.getInventory().isEmpty());
        var hit = new EntityDamageByEntityEvent(second, first, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4);
        server.getPluginManager().callEvent(hit); assertTrue(hit.isCancelled());
    }
    @Test void durationLimitRestoresInventories() {
        arena(); first.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        command(first, second.getName() + " none test"); command(second, "accept " + first.getName());
        drainUntil(() -> first.getInventory().isEmpty());
        server.getScheduler().performTicks(6200);
        assertEquals(5, first.getInventory().getItem(0).getAmount());
    }
    @Test void unavailableArenaDoesNotTouchInventories() {
        first.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        command(first, second.getName() + " none"); command(second, "accept " + first.getName());
        server.getScheduler().performTicks(5);
        assertEquals(5, first.getInventory().getItem(0).getAmount());
        assertFalse(Files.exists(plugin.getDataFolder().toPath().resolve("recovery.yml")));
    }
    @Test void regularPlayerCannotManageArenasOrKits() {
        first.setOp(false); command(first, "arena create forbidden"); command(first, "kit save forbidden");
        assertTrue(com.agent1k.g4duels.config.YamlFiles.load(plugin.getDataFolder().toPath().resolve("arenas.yml").toFile()).getKeys(false).isEmpty());
        assertTrue(com.agent1k.g4duels.config.YamlFiles.load(plugin.getDataFolder().toPath().resolve("kits.yml").toFile()).getKeys(false).isEmpty());
    }
    @Test void ownLoadoutKeepsItemsAndRestoresConsumedAmounts() {
        arena(); first.getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE, 6));
        command(first, second.getName() + " own test"); command(second, "accept " + first.getName());
        drainUntil(() -> readRecoveryIfPresent().contains(first.getUniqueId().toString()));
        server.getScheduler().performTicks(5);
        assertEquals(6, first.getInventory().getItem(0).getAmount());
        first.getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE, 2));
        command(first, "leave"); assertEquals(6, first.getInventory().getItem(0).getAmount());
    }
    @Test void deathPreservesInventoryUntilRespawnRecovery() {
        arena(); first.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 8));
        command(first, second.getName() + " none test"); command(second, "accept " + first.getName());
        drainUntil(() -> first.getInventory().isEmpty());
        server.getScheduler().performTicks(110);
        first.setHealth(0); first.respawn();
        drainUntil(() -> first.getInventory().contains(Material.DIAMOND));
        assertEquals(8, first.getInventory().getItem(0).getAmount());
    }
    @Test void tabCompletionIncludesSavedKitAndArena() {
        arena(); command(first, "kit save classic");
        var command = java.util.Objects.requireNonNull(plugin.getCommand("duel"));
        assertTrue(command.tabComplete(first, "duel", new String[] { second.getName(), "cl" }).contains("classic"));
        assertTrue(command.tabComplete(first, "duel", new String[] { second.getName(), "own", "te" }).contains("test"));
    }
    @Test void arenaCannotBeDeletedWhileReserved() {
        arena(); first.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        command(first, second.getName() + " none test"); command(second, "accept " + first.getName());
        drainUntil(() -> first.getInventory().isEmpty());
        PlayerMock admin = server.addPlayer("Администратор"); admin.setOp(true); command(admin, "arena delete test");
        assertTrue(com.agent1k.g4duels.config.YamlFiles.load(plugin.getDataFolder().toPath().resolve("arenas.yml").toFile()).contains("test"));
    }

    @Test void clickingNextOnLastPageKeepsCorrectTitle() {
        command(first, second.getName());
        clickMenu(11);
        String title = first.getOpenInventory().getTitle();
        assertEquals(54, first.getOpenInventory().getTopInventory().getSize());
        clickMenu(50);
        assertEquals(title, first.getOpenInventory().getTitle());
    }
    private void clickMenu(int slot) {
        var event = new org.bukkit.event.inventory.InventoryClickEvent(first.getOpenInventory(),
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, slot,
            org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        server.getScheduler().performOneTick();
    }
}
