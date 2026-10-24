package com.ytcustom;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Efectos de armas y protección de objetos personalizados. */
public final class ItemListener implements Listener {

    private final YTCustom plugin;

    public ItemListener(YTCustom plugin) {
        this.plugin = plugin;
    }

    /** Filo Hueco: partículas oscuras y lentitud breve al golpear. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (!plugin.items().is(p.getInventory().getItemInMainHand(), ItemManager.BLADE)) return;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, true));
        target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.02);
        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.02);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.35f, 1.7f);
    }

    /** Evita que el yunque consuma las monedas/pluma como material de reparación. */
    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack second = e.getInventory().getItem(1);
        ItemStack first = e.getInventory().getItem(0);
        String idFirst = plugin.items().idOf(first);
        if (idFirst != null && (idFirst.equals(ItemManager.COIN) || idFirst.equals(ItemManager.PLUMA))) {
            e.setResult(null); // no se pueden renombrar/combinar: evita trucos de duplicación o mezcla
            return;
        }
        String idSecond = plugin.items().idOf(second);
        if (idSecond != null && (idSecond.equals(ItemManager.COIN) || idSecond.equals(ItemManager.PLUMA))) {
            e.setResult(null);
        }
    }
}
