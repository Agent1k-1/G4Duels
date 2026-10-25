package com.agent1k.g4duels.duel;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public record PlayerSnapshot(@NotNull Location location, @NotNull ItemStack[] inventory, @NotNull GameMode gameMode,
        double health, int food, float saturation, float exhaustion, int level, float experience,
        int totalExperience, boolean allowFlight, boolean flying, int fireTicks, int selectedSlot, double absorption, int remainingAir, @NotNull List<PotionEffect> effects) {
    public static @NotNull PlayerSnapshot capture(@NotNull Player player) {
        ItemStack[] contents = Arrays.stream(player.getInventory().getContents()).map(i -> i == null ? new ItemStack(Material.AIR) : i.clone()).toArray(ItemStack[]::new);
        return new PlayerSnapshot(player.getLocation(), contents, player.getGameMode(), player.getHealth(), player.getFoodLevel(),
            player.getSaturation(), player.getExhaustion(), player.getLevel(), player.getExp(), player.getTotalExperience(),
            player.getAllowFlight(), player.isFlying(), player.getFireTicks(), player.getInventory().getHeldItemSlot(), player.getAbsorptionAmount(), player.getRemainingAir(), List.copyOf(player.getActivePotionEffects()));
    }
    public void restore(@NotNull Player player) {
        player.closeInventory();
        player.getInventory().setContents(inventory);
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight); player.setFlying(flying && allowFlight);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.addPotionEffects(effects);
        player.setHealth(Math.max(0.5, Math.min(health, player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue())));
        player.setFoodLevel(food); player.setSaturation(saturation); player.setExhaustion(exhaustion);
        player.setTotalExperience(totalExperience); player.setLevel(level); player.setExp(experience); player.setFireTicks(fireTicks);
        player.getInventory().setHeldItemSlot(selectedSlot); player.setAbsorptionAmount(absorption); player.setRemainingAir(remainingAir);
        player.setFallDistance(0); player.setVelocity(new org.bukkit.util.Vector());
    }
    public void write(@NotNull ConfigurationSection section) {
        section.set("location", location); section.set("inventory", Arrays.asList(inventory)); section.set("gamemode", gameMode.name());
        section.set("health", health); section.set("food", food); section.set("saturation", saturation); section.set("exhaustion", exhaustion);
        section.set("level", level); section.set("experience", experience); section.set("total-experience", totalExperience);
        section.set("allow-flight", allowFlight); section.set("flying", flying); section.set("fire", fireTicks); section.set("effects", effects); section.set("selected-slot", selectedSlot); section.set("absorption", absorption); section.set("remaining-air", remainingAir);
    }
    public static @NotNull PlayerSnapshot read(@NotNull ConfigurationSection section) {
        Location location = Objects.requireNonNull(section.getLocation("location"), "Отсутствует точка возврата");
        Objects.requireNonNull(location.getWorld(), "Мир точки возврата недоступен");
        var items = section.getList("inventory", List.of());
        if (items.size() != 41 || items.stream().anyMatch(i -> !(i instanceof ItemStack))) throw new IllegalArgumentException("Некорректный снимок инвентаря");
        return new PlayerSnapshot(location, items.stream().map(ItemStack.class::cast).map(ItemStack::clone).toArray(ItemStack[]::new),
            GameMode.valueOf(section.getString("gamemode", "SURVIVAL")), section.getDouble("health", 20), section.getInt("food", 20),
            (float) section.getDouble("saturation", 5), (float) section.getDouble("exhaustion"), section.getInt("level"),
            (float) section.getDouble("experience"), section.getInt("total-experience"), section.getBoolean("allow-flight"),
            section.getBoolean("flying"), section.getInt("fire"), section.getInt("selected-slot"), section.getDouble("absorption"), section.getInt("remaining-air", 300), section.getList("effects", List.of()).stream().filter(PotionEffect.class::isInstance).map(PotionEffect.class::cast).toList());
    }
}
