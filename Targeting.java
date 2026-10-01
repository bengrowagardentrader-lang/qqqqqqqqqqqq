package com.unstablesmp.orbitalwarden;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class Targeting {
    private Targeting() {}

    public static Location find(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        RayTraceResult entities = player.getWorld().rayTraceEntities(eye, direction, range, 0.75,
                entity -> entity instanceof Player && !entity.equals(player));
        if (entities != null && entities.getHitEntity() != null) {
            return entities.getHitEntity().getLocation().add(0, 0.1, 0);
        }

        RayTraceResult blocks = player.getWorld().rayTraceBlocks(eye, direction, range, org.bukkit.FluidCollisionMode.NEVER, true);
        if (blocks != null && blocks.getHitPosition() != null) {
            return blocks.getHitPosition().toLocation(player.getWorld());
        }

        // If the player is aiming into the sky, choose the ground below a point
        // along the aim direction instead of randomly using the player's location.
        Location fallback = eye.clone().add(direction.multiply(Math.min(range, 40)));
        int highest = player.getWorld().getHighestBlockYAt(fallback);
        return new Location(player.getWorld(), fallback.getX(), highest + 1.0, fallback.getZ());
    }
}
