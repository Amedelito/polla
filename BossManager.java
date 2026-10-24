package com.ytcustom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Gestiona jefes y esbirros activos y escucha los eventos que los afectan. */
public final class BossManager implements Listener {

    public static final int CMD_WRAITH = 3020;

    private record Wraith(Vex vex, Rig rig) {}

    private final YTCustom plugin;
    private final Map<UUID, HollowKing> kings = new HashMap<>();
    private final Map<UUID, Wraith> wraiths = new HashMap<>();
    private final Set<Rig> dying = new HashSet<>();
    private final BukkitTask ticker;

    public BossManager(YTCustom plugin) {
        this.plugin = plugin;
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickWraiths, 1L, 1L);
    }

    /* ---------------- API ---------------- */

    public HollowKing spawnKing(Location loc) {
        int max = plugin.getConfig().getInt("boss.max-concurrent", 1);
        if (max >= 0 && kings.size() >= max) return null;
        try {
            HollowKing k = new HollowKing(plugin, this, loc);
            kings.put(k.getEntityId(), k);
            return k;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error al invocar a THE HOLLOW KING", e);
            return null;
        }
    }

    public int removeAllKings() {
        int n = kings.size();
        for (HollowKing k : new ArrayList<>(kings.values())) k.shutdown();
        kings.clear();
        return n;
    }

    public void trackDying(Rig rig) {
        dying.add(rig);
    }

    public void untrackDying(Rig rig) {
        dying.remove(rig);
    }

    public UUID spawnWraith(Location loc) {
        double hp = plugin.getConfig().getDouble("boss.wraiths.health", 16.0);
        double dmg = plugin.getConfig().getDouble("boss.wraiths.damage", 5.0);
        Vex v = loc.getWorld().spawn(loc, Vex.class, x -> {
            x.setInvisible(true);
            x.setPersistent(false);
            x.setRemoveWhenFarAway(true);
            x.setSilent(true);
            x.setCanPickupItems(false);
            x.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.BYTE, (byte) 1);
            x.getPersistentDataContainer().set(Keys.TEMP, PersistentDataType.BYTE, (byte) 1);
            setAttr(x, Attribute.GENERIC_MAX_HEALTH, hp);
            x.setHealth(hp);
            setAttr(x, Attribute.GENERIC_ATTACK_DAMAGE, dmg);
        });
        clearEquipment(v);
        Rig rig = new Rig(plugin, v, CMD_WRAITH, 1.2f, 1.2f, 0, 0f, 0.0, 0f);
        wraiths.put(v.getUniqueId(), new Wraith(v, rig));
        return v.getUniqueId();
    }

    public int aliveWraiths(List<UUID> ids) {
        int n = 0;
        for (UUID id : ids) {
            Wraith w = wraiths.get(id);
            if (w != null && w.vex().isValid()) n++;
        }
        return n;
    }

    public void killWraiths(List<UUID> ids) {
        for (UUID id : ids) {
            Wraith w = wraiths.remove(id);
            if (w == null) continue;
            Location l = w.vex().getLocation().add(0, 0.5, 0);
            l.getWorld().spawnParticle(Particle.SOUL, l, 10, 0.3, 0.3, 0.3, 0.03);
            w.rig().remove();
            w.vex().remove();
        }
    }

    public void shutdown() {
        ticker.cancel();
        removeAllKings();
        for (Wraith w : wraiths.values()) {
            w.rig().remove();
            w.vex().remove();
        }
        wraiths.clear();
        for (Rig r : dying) r.remove();
        dying.clear();
    }

    /* ---------------- Utilidades ---------------- */

    public static void setAttr(LivingEntity e, Attribute a, double v) {
        AttributeInstance ai = e.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    public static void clearEquipment(LivingEntity e) {
        var eq = e.getEquipment();
        if (eq == null) return;
        eq.setItemInMainHand(null);
        eq.setItemInOffHand(null);
        eq.setHelmet(null);
        eq.setChestplate(null);
        eq.setLeggings(null);
        eq.setBoots(null);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    public static void clearEquipmentIfArmed(LivingEntity e) {
        var eq = e.getEquipment();
        if (eq != null && eq.getItemInMainHand().getType() != org.bukkit.Material.AIR) clearEquipment(e);
    }

    private void tickWraiths() {
        Iterator<Map.Entry<UUID, Wraith>> it = wraiths.entrySet().iterator();
        while (it.hasNext()) {
            Wraith w = it.next().getValue();
            if (!w.vex().isValid()) {
                w.rig().remove();
                it.remove();
                continue;
            }
            w.rig().tick();
            var eq = w.vex().getEquipment();
            if (eq != null && eq.getItemInMainHand().getType() != org.bukkit.Material.AIR) clearEquipment(w.vex());
            if (w.vex().getTarget() == null && w.vex().getTicksLived() % 40 == 0) {
                Player nearest = null;
                double best = 30 * 30;
                for (Player p : w.vex().getWorld().getPlayers()) {
                    if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL && p.getGameMode() != org.bukkit.GameMode.ADVENTURE) continue;
                    double d = p.getLocation().distanceSquared(w.vex().getLocation());
                    if (d < best) { best = d; nearest = p; }
                }
                if (nearest != null) w.vex().setTarget(nearest);
            }
            if (w.vex().getTicksLived() % 6 == 0) {
                w.vex().getWorld().spawnParticle(Particle.SOUL, w.vex().getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.0);
            }
        }
    }

    /* ---------------- Eventos ---------------- */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        HollowKing k = kings.get(e.getEntity().getUniqueId());
        if (k == null) return;
        Player attacker = null;
        Entity d = e.getDamager();
        if (d instanceof Player p) {
            attacker = p;
        } else if (d instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) attacker = p;
        }
        if (attacker != null) k.addDamage(attacker, e.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        HollowKing k = kings.get(e.getEntity().getUniqueId());
        if (k == null) return;
        switch (e.getCause()) {
            case DROWNING, SUFFOCATION, FALL, FIRE, FIRE_TICK, LAVA, HOT_FLOOR, STARVATION, CRAMMING, FLY_INTO_WALL ->
                    e.setCancelled(true);
            default -> { }
        }
        if (!e.isCancelled() && k.isInvulnerableNow()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (kings.containsKey(e.getEntity().getUniqueId()) || wraiths.containsKey(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        HollowKing k = kings.remove(id);
        if (k != null) {
            e.getDrops().clear();      // sin drops vanilla
            e.setDroppedExp(0);
            k.onDeath();               // protegido contra doble ejecución
            return;
        }
        Wraith w = wraiths.remove(id);
        if (w != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            Location l = w.vex().getLocation().add(0, 0.5, 0);
            l.getWorld().spawnParticle(Particle.SOUL, l, 16, 0.3, 0.3, 0.3, 0.05);
            w.rig().remove();
        }
    }

    /** Un esbirro nunca debe atacar a otro esbirro ni al jefe. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() != null && (kings.containsKey(e.getTarget().getUniqueId())
                || wraiths.containsKey(e.getTarget().getUniqueId()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        for (HollowKing k : kings.values()) k.forgetViewer(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        for (HollowKing k : kings.values()) k.forgetViewer(e.getPlayer());
    }

    /** Limpia entidades temporales (modelos/esbirros) que hubiesen quedado guardadas tras un cierre brusco. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en.getPersistentDataContainer().has(Keys.TEMP, PersistentDataType.BYTE)
                    && !wraiths.containsKey(en.getUniqueId())) {
                en.remove();
            }
        }
    }
}
