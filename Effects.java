package com.unstablesmp.orbitalwarden;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.util.Vector;

public final class Effects {
    private Effects() {}

    public static void ring(World world, Location center, double radius, int points, double soulOffset) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SCULK_SOUL, point, 1, 0, 0, 0, soulOffset * 0.01);
        }
    }

    public static void orbitalColumn(World world, Location center, double radius, double progress) {
        int points = 18;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points + progress * 5;
            double y = 1 + Math.sin(progress * Math.PI) * 10;
            Location p = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            world.spawnParticle(Particle.SCULK_SOUL, p, 1, 0, 0, 0, 0.01);
        }
    }

    public static void sonicTrail(World world, Location start, Location end, double step) {
        Vector direction = end.toVector().subtract(start.toVector());
        double length = direction.length();
        if (length <= 0.1) return;
        direction.normalize();
        for (double d = 0; d <= length; d += step) {
            Location point = start.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
            if (((int) d) % 3 == 0) world.spawnParticle(Particle.SCULK_SOUL, point, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    public static void impact(World world, Location center, double radius) {
        ring(world, center.clone().add(0, 0.2, 0), radius, 72, 1.0);
        world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 1, 0), 5, radius / 3, 0.4, radius / 3, 0);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 7.0f, 0.9f);
    }

    public static void finalRing(World world, Location center, double radius) {
        for (int layer = 0; layer < 3; layer++) {
            ring(world, center.clone().add(0, layer * 0.7, 0), radius - layer * 1.2, 96, 1.0);
        }
    }
}
