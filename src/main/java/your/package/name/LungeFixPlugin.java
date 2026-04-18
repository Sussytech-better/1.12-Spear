package me.armond.lungefix;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LungeFixPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private Enchantment lungeEnchant;

    private long cooldownMillis;
    private double basePower;
    private double powerPerLevel;
    private double upwardBoost;
    private boolean playSound;
    private Sound sound;
    private float volume;
    private float pitch;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        Bukkit.getPluginManager().registerEvents(this, this);

        lungeEnchant = Enchantment.getByKey(NamespacedKey.minecraft("lunge"));

        if (lungeEnchant == null) {
            getLogger().warning("Lunge enchant not found! Are you on 1.21.11?");
        }
    }

    private void loadConfigValues() {
        cooldownMillis = getConfig().getLong("cooldown-seconds") * 1000;

        basePower = getConfig().getDouble("base-power");
        powerPerLevel = getConfig().getDouble("power-per-level");
        upwardBoost = getConfig().getDouble("upward-boost");

        playSound = getConfig().getBoolean("play-sound");

        try {
            sound = Sound.valueOf(getConfig().getString("sound.name"));
        } catch (Exception e) {
            sound = Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK;
        }

        volume = (float) getConfig().getDouble("sound.volume");
        pitch = (float) getConfig().getDouble("sound.pitch");
    }

    private boolean isSpear(ItemStack item) {
        return item != null && item.getType().name().equals("SPEAR");
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isSpear(item)) return;
        if (lungeEnchant == null) return;

        int level = item.getEnchantmentLevel(lungeEnchant);
        if (level <= 0) return;

        long now = System.currentTimeMillis();

        if (cooldowns.containsKey(player.getUniqueId())) {
            long last = cooldowns.get(player.getUniqueId());
            if (now - last < cooldownMillis) return;
        }

        cooldowns.put(player.getUniqueId(), now);
        startCooldownBar(player);

        Vector direction = player.getLocation().getDirection().normalize();
        double power = basePower + (powerPerLevel * level);

        direction.setY(direction.getY() + upwardBoost);

        player.setVelocity(direction.multiply(power));
        player.setFallDistance(0f);

        if (playSound) {
            player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isSpear(item)) return;
        if (lungeEnchant == null) return;

        int level = item.getEnchantmentLevel(lungeEnchant);
        if (level <= 0) return;

        player.setVelocity(player.getVelocity().multiply(0.6));
    }

    private void startCooldownBar(Player player) {
        long start = System.currentTimeMillis();

        Bukkit.getScheduler().runTaskTimer(this, task -> {

            long now = System.currentTimeMillis();
            long remaining = cooldownMillis - (now - start);

            if (remaining <= 0) {
                player.sendActionBar(Component.text("§aLunge Ready!"));
                task.cancel();
                return;
            }

            double seconds = remaining / 1000.0;
            player.sendActionBar(Component.text("§cLunge cooldown: " + String.format("%.1f", seconds) + "s"));

        }, 0L, 2L);
    }
}