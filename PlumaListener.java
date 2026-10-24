package com.ytcustom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lógica de la Pluma de Impulso. */
public final class PlumaListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final YTCustom plugin;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public PlumaListener(YTCustom plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        // Solo mano principal: evita el doble evento (mano principal + secundaria)
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!plugin.items().is(hand, ItemManager.PLUMA)) return;

        if (a == Action.RIGHT_CLICK_BLOCK && !p.isSneaking() && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable()) {
            return; // deja que se abra la puerta/cofre
        }
        if (e.useItemInHand() == Event.Result.DENY) return;
        e.setCancelled(true);

        if (!p.hasPermission("ytcustom.pluma")) {
            p.sendActionBar(MM.deserialize("<red>No tienes permiso para usar la Pluma de Impulso."));
            return;
        }

        long now = System.currentTimeMillis();
        Long until = cooldownUntil.get(p.getUniqueId());
        if (until != null && until > now) {
            double left = (until - now) / 1000.0;
            p.sendActionBar(MM.deserialize("<gray>Pluma recargándose: <aqua>"
                    + String.format(java.util.Locale.ROOT, "%.1f", left) + "s"));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.6f);
            return;
        }

        int charges = plugin.items().getCharges(hand);
        if (charges <= 0) {
            p.getInventory().setItemInMainHand(null);
            return;
        }

        // Fijar la recarga ANTES de cualquier otra cosa: impide gastar varias cargas por error
        long cd = (long) (plugin.getConfig().getDouble("pluma.cooldown-seconds", 18.0) * 1000.0);
        cooldownUntil.put(p.getUniqueId(), now + cd);

        // Consumir carga
        charges--;
        if (charges <= 0) {
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1f, 0.8f);
            p.sendActionBar(MM.deserialize("<red>La Pluma de Impulso se ha desvanecido."));
        } else {
            plugin.items().setCharges(hand, charges);
            p.getInventory().setItemInMainHand(hand);
            p.sendActionBar(MM.deserialize("<aqua>Impulso <gray>· cargas restantes: <white>" + charges));
        }

        boost(p);
        effects(p);
    }

    private void boost(Player p) {
        double hs = plugin.getConfig().getDouble("pluma.horizontal-speed", 1.7);
        double vs = plugin.getConfig().getDouble("pluma.vertical-speed", 0.45);
        Vector dir = p.getLocation().getDirection();
        Vector h = new Vector(dir.getX(), 0, dir.getZ());
        if (h.lengthSquared() > 1.0E-4) {
            h.normalize().multiply(hs);
        } else {
            h = new Vector(0, 0, 0);
        }
        p.setVelocity(new Vector(h.getX(), vs, h.getZ()));
    }

    private void effects(Player p) {
        World w = p.getWorld();
        Location start = p.getLocation().add(0, 1.0, 0);
        w.playSound(start, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1.0f, 1.6f);
        w.playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.6f, 1.4f);
        w.spawnParticle(Particle.CLOUD, start, 12, 0.3, 0.2, 0.3, 0.05);

        // Pluma visual que se desvanece detrás del jugador
        ItemDisplay feather = null;
        try {
            Vector back = p.getLocation().getDirection().multiply(-0.8);
            Location fl = start.clone().add(back);
            feather = w.spawn(fl, ItemDisplay.class, d -> {
                ItemStack icon = new ItemStack(Material.FEATHER);
                var m = icon.getItemMeta();
                m.setCustomModelData(ItemManager.CMD_PLUMA);
                icon.setItemMeta(m);
                d.setItemStack(icon);
                d.setPersistent(false);
                d.getPersistentDataContainer().set(Keys.TEMP, PersistentDataType.BYTE, (byte) 1);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.4f, 0.4f, 0.4f), new Quaternionf()));
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo crear la animación de la Pluma: " + ex.getMessage());
        }
        final ItemDisplay fd = feather;
        if (fd != null) {
            fd.setInterpolationDelay(0);
            fd.setInterpolationDuration(12);
            fd.setTransformation(new Transformation(new Vector3f(0, 0.3f, 0),
                    new Quaternionf().rotateZ(1.2f), new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
        }

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t++ >= 14) {
                    if (fd != null) fd.remove();
                    cancel();
                    return;
                }
                Location l = p.getLocation().add(0, 1.0, 0);
                p.getWorld().spawnParticle(Particle.END_ROD, l, 4, 0.2, 0.3, 0.2, 0.02);
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, l, 3, 0.2, 0.2, 0.2, 0.01);
                if (fd != null && fd.isValid()) fd.teleport(l.clone().add(p.getLocation().getDirection().multiply(-0.8)));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Se conserva el cooldown para impedir saltarlo reconectando; solo se limpia al expirar.
        Long until = cooldownUntil.get(e.getPlayer().getUniqueId());
        if (until != null && until < System.currentTimeMillis()) cooldownUntil.remove(e.getPlayer().getUniqueId());
    }
}
