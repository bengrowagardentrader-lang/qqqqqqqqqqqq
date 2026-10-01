package com.unstablesmp.orbitalwarden;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OrbitalWardenCannon extends JavaPlugin implements Listener {
    private NamespacedKey weaponKey;
    private NamespacedKey typeKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> activeStrikes = new HashMap<>();

    @Override
    public void onEnable() {
        weaponKey = new NamespacedKey(this, "warden_weapon");
        typeKey = new NamespacedKey(this, "warden_weapon_type");
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Orbital Warden Cannon enabled for Paper 1.21.11");
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : activeStrikes.values()) task.cancel();
        activeStrikes.clear();
        cooldowns.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        WeaponType type = getWeaponType(item);
        if (type == null) return;

        event.setCancelled(true);
        if (player.getGameMode().isInvulnerable()) return;

        long now = System.currentTimeMillis();
        long cooldown = getConfig().getLong("cooldown-seconds", 15) * 1000L;
        long previous = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < cooldown) {
            long seconds = Math.max(1, (cooldown - (now - previous) + 999) / 1000);
            player.sendActionBar(ChatColor.RED + "Warden weapon cooldown: " + seconds + "s");
            return;
        }

        Location target = Targeting.find(player, getConfig().getDouble("max-range", 300));
        if (target == null) {
            player.sendActionBar(ChatColor.RED + "No target found.");
            return;
        }

        cooldowns.put(player.getUniqueId(), now);
        consumeWeapon(player, item);

        if (type == WeaponType.STRIKE) startStrike(player, target);
        else spawnWardenWave(player, target);
    }

    private void consumeWeapon(Player player, ItemStack item) {
        // The rod is deliberately marked as having only 2 durability states,
        // but this cannon is a single-use item: the rod disappears on activation.
        if (getConfig().getBoolean("consume-on-use", true)) {
            item.setAmount(item.getAmount() - 1);
            return;
        }

        if (item.getItemMeta() instanceof Damageable damageable) {
            int max = item.getType().getMaxDurability();
            int current = damageable.getDamage();
            int next = current + Math.max(1, max / 2);
            if (next >= max) item.setAmount(item.getAmount() - 1);
            else {
                damageable.setDamage(next);
                item.setItemMeta(damageable);
            }
        }
    }

    private void startStrike(Player shooter, Location target) {
        World world = target.getWorld();
        int chargeTicks = getConfig().getInt("charge-ticks", 36);
        Location center = target.clone().add(0, 0.15, 0);

        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 7.0f, 0.65f);
        world.spawnParticle(Particle.SCULK_SOUL, center, 25, 1.5, 0.4, 1.5, 0.02);

        BukkitTask task = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            int tick;
            @Override public void run() {
                if (tick >= chargeTicks) {
                    BukkitTask current = activeStrikes.remove(shooter.getUniqueId());
                    if (current != null) current.cancel();
                    fireStrike(shooter, center);
                    return;
                }

                double progress = tick / (double) chargeTicks;
                double radius = 1.5 + progress * getConfig().getDouble("charge-ring-radius", 10.0);
                double y = 0.4 + progress * 8.0;
                Effects.ring(world, center.clone().add(0, y, 0), radius, 42, 1.0);
                Effects.orbitalColumn(world, center, radius, progress);

                if (tick % 6 == 0) {
                    world.playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 2.5f, 0.85f + (float) progress * 0.45f);
                }
                tick++;
            }
        }, 0L, 1L);

        activeStrikes.put(shooter.getUniqueId(), task);
    }

    private void fireStrike(Player shooter, Location center) {
        World world = center.getWorld();
        int blasts = Math.max(8, getConfig().getInt("blast-count", 32));
        double height = getConfig().getDouble("blast-height", 24.0);
        double radius = getConfig().getDouble("blast-radius", 11.0);
        double damage = getConfig().getDouble("damage", 12.0);
        double impactRadius = getConfig().getDouble("impact-radius", 5.5);

        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 10.0f, 0.7f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 8.0f, 1.0f);
        world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 1.0, 0), 10, 1.5, 0.5, 1.5, 0);

        for (int i = 0; i < blasts; i++) {
            double angle = Math.PI * 2.0 * i / blasts;
            double ringRadius = radius * (0.55 + ((i % 7) / 6.0) * 0.45);
            Location start = center.clone().add(
                    Math.cos(angle) * ringRadius,
                    height + (i % 5) * 1.5,
                    Math.sin(angle) * ringRadius
            );
            long delay = (i % 8) * 2L;
            getServer().getScheduler().runTaskLater(this, () -> Effects.sonicTrail(world, start, center, 1.0), delay);
        }

        // Three controlled damage pulses. This is intentionally NOT a lethal
        // armor-bypassing hit. Normal armor, absorption and totems still work.
        for (int pulse = 0; pulse < 3; pulse++) {
            final int currentPulse = pulse;
            getServer().getScheduler().runTaskLater(this, () -> {
                Effects.impact(world, center, radius + currentPulse * 2.0);
                applyStrikeDamage(shooter, center, impactRadius, damage / 3.0);
            }, 10L + pulse * 5L);
        }

        getServer().getScheduler().runTaskLater(this, () -> {
            Effects.finalRing(world, center, radius + 4.0);
            world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 8.0f, 0.85f);
        }, 27L);
    }

    private void applyStrikeDamage(Player shooter, Location center, double radius, double damage) {
        World world = center.getWorld();
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(shooter) && getConfig().getBoolean("ignore-shooter", true)) continue;
            if (target.isDead()) continue;

            // Uses normal Bukkit damage. It does NOT bypass armor or totems.
            target.damage(damage, shooter);
            Vector push = target.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() > 0.001) {
                push.normalize().multiply(getConfig().getDouble("knockback", 0.35));
                push.setY(0.18);
                target.setVelocity(target.getVelocity().add(push));
            }
        }
    }

    private void spawnWardenWave(Player shooter, Location target) {
        World world = target.getWorld();
        int count = Math.max(1, getConfig().getInt("warden-spawn-count", 30));
        double radius = getConfig().getDouble("warden-spawn-radius", 7.0);
        double height = getConfig().getDouble("warden-spawn-height", 12.0);

        world.playSound(target, Sound.ENTITY_WARDEN_SONIC_CHARGE, 10.0f, 0.55f);
        Effects.ring(world, target.clone().add(0, 1, 0), radius, 64, 1.2);

        for (int i = 0; i < count; i++) {
            final int index = i;
            getServer().getScheduler().runTaskLater(this, () -> {
                double angle = Math.PI * 2.0 * index / count;
                double r = radius * (0.35 + ((index % 6) / 5.0) * 0.65);
                Location spawn = target.clone().add(Math.cos(angle) * r, height + (index % 5) * 1.5, Math.sin(angle) * r);

                Warden warden = (Warden) world.spawnEntity(spawn, org.bukkit.entity.EntityType.WARDEN);
                warden.setAware(true);
                warden.setRemoveWhenFarAway(false);
                warden.setTarget(findNearestPlayer(world, target, shooter));
                world.spawnParticle(Particle.SCULK_SOUL, spawn, 16, 0.5, 0.7, 0.5, 0.03);
                world.playSound(spawn, Sound.ENTITY_WARDEN_EMERGE, 1.8f, 0.8f + (index % 4) * 0.08f);
            }, index * 2L);
        }
    }

    private Player findNearestPlayer(World world, Location center, Player shooter) {
        Player nearest = null;
        double best = 64 * 64;
        for (Player player : world.getPlayers()) {
            if (player.equals(shooter) && getConfig().getBoolean("spawn-ignore-shooter", false)) continue;
            double distance = player.getLocation().distanceSquared(center);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private WeaponType getWeaponType(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) return null;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.BYTE);
        if (value == null) return null;
        if (value == (byte) 1) return WeaponType.STRIKE;
        if (value == (byte) 2) return WeaponType.SPAWN;
        return null;
    }

    private ItemStack createWeapon(WeaponType type) {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        String name = type == WeaponType.STRIKE ? ChatColor.DARK_AQUA + "Warden Strike" : ChatColor.DARK_AQUA + "Warden Spawn";
        meta.setDisplayName(name);
        meta.setLore(type == WeaponType.STRIKE
                ? java.util.List.of(ChatColor.GRAY + "Orbital Warden Sonic Boom cannon", ChatColor.DARK_GRAY + "Single use")
                : java.util.List.of(ChatColor.GRAY + "Spawns 30 Wardens at the target", ChatColor.DARK_GRAY + "Single use"));
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.BYTE, (byte) (type == WeaponType.STRIKE ? 1 : 2));

        // Display the rod as a 2-durability item. It is still consumed on use
        // by default so players cannot repeatedly fire it.
        if (meta instanceof Damageable damageable) {
            int max = rod.getType().getMaxDurability();
            damageable.setDamage(Math.max(0, max - 2));
            meta = damageable;
        }
        rod.setItemMeta(meta);
        return rod;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("owc")) return false;
        if (!sender.hasPermission("orbitalwarden.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can receive weapons.");
                return true;
            }
            WeaponType type = args[1].equalsIgnoreCase("spawn") ? WeaponType.SPAWN : WeaponType.STRIKE;
            player.getInventory().addItem(createWeapon(type));
            player.sendMessage(ChatColor.AQUA + "Received " + (type == WeaponType.STRIKE ? "Warden Strike" : "Warden Spawn") + ".");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Orbital Warden Cannon config reloaded.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "/owc give strike");
        sender.sendMessage(ChatColor.YELLOW + "/owc give spawn");
        sender.sendMessage(ChatColor.YELLOW + "/owc reload");
        return true;
    }

    private enum WeaponType { STRIKE, SPAWN }
}
