package com.ytcustom;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** THE HOLLOW KING: jefe de tres fases con modelo 3D propio. */
public final class HollowKing {

    private enum Stage { INTRO, FIGHT, TRANSITION }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] BODY_CMD = {0, 3001, 3002, 3003};
    private static final int[] HALO_CMD = {0, 3011, 3012, 3013};
    private static final String[] PHASE_NAME = {"", "Despertar", "El Rey Desatado", "Apocalipsis"};
    private static final String[] ROMAN = {"", "I", "II", "III"};

    private final YTCustom plugin;
    private final BossManager manager;
    private final WitherSkeleton entity;
    private final Rig rig;
    private final BossBar bar;
    private final Set<Player> viewers = new HashSet<>();
    private final Map<UUID, Double> damageDealt = new HashMap<>();
    private final List<UUID> minions = new ArrayList<>();
    private final Random rnd = new Random();
    private final double maxHp;
    private final float fullModelScale;
    private final BukkitTask task;

    private Stage stage = Stage.INTRO;
    private int stageTicks = 0;
    private int nextPhase = 1;
    private int phase = 1;
    private long ticks = 0;
    private int cooldown = 100;
    private boolean dead = false;
    private String lastAbility = "";

    HollowKing(YTCustom plugin, BossManager manager, Location loc) {
        this.plugin = plugin;
        this.manager = manager;
        var cfg = plugin.getConfig();
        this.maxHp = Math.max(20.0, cfg.getDouble("boss.health", 700.0));
        this.fullModelScale = (float) cfg.getDouble("boss.model-scale", 2.05);
        float hitboxScale = (float) cfg.getDouble("boss.hitbox-scale", 1.7);

        this.entity = loc.getWorld().spawn(loc, WitherSkeleton.class, w -> {
            w.setInvisible(true);
            w.setSilent(true);
            w.setPersistent(false);
            w.setRemoveWhenFarAway(false);
            w.setCanPickupItems(false);
            w.customName(MM.deserialize("<gradient:#8a2be2:#00e5ff>THE HOLLOW KING</gradient>"));
            w.setCustomNameVisible(false);
            w.getPersistentDataContainer().set(Keys.BOSS, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            w.getPersistentDataContainer().set(Keys.TEMP, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            BossManager.setAttr(w, Attribute.GENERIC_MAX_HEALTH, maxHp);
            w.setHealth(maxHp);
            BossManager.setAttr(w, Attribute.GENERIC_SCALE, hitboxScale);
            BossManager.setAttr(w, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
            BossManager.setAttr(w, Attribute.GENERIC_FOLLOW_RANGE, 48.0);
            BossManager.setAttr(w, Attribute.GENERIC_ARMOR, 6.0);
            w.setAI(false);
            w.setInvulnerable(true);
        });
        BossManager.clearEquipment(entity);

        float yawOff = (float) cfg.getDouble("boss.model-yaw-offset", 0.0);
        this.rig = new Rig(plugin, entity, BODY_CMD[1], fullModelScale, 0.05f,
                HALO_CMD[1], 2.4f, 3.7, yawOff);
        this.rig.setScale(fullModelScale, cfg.getInt("boss.intro-ticks", 60));

        this.bar = BossBar.bossBar(title(), 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_20);
        applyPhaseStats();
        announceSpawn(loc);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public UUID getEntityId() { return entity.getUniqueId(); }

    public boolean isInvulnerableNow() { return stage != Stage.FIGHT; }

    private boolean alive() { return !dead && entity.isValid(); }

    private double cfg(String path, double def) { return plugin.getConfig().getDouble(path, def); }

    private double phaseCfg(String key, double def) { return cfg("boss.phase-" + phase + "." + key, def); }

    /* ======================= Anuncios ======================= */

    private Component title() {
        double hp = Math.max(0, entity == null ? maxHp : entity.getHealth());
        return MM.deserialize("<gradient:#8a2be2:#00e5ff><bold>☠ THE HOLLOW KING ☠</bold></gradient> <dark_gray>· <gray>Fase "
                + ROMAN[phase] + ": " + PHASE_NAME[phase] + " <dark_gray>· <red>❤ " + (int) Math.ceil(hp) + "<dark_gray>/<red>" + (int) maxHp);
    }

    private void broadcastSound(Sound s, float vol, float pitch) {
        Location l = entity.getLocation();
        double range = cfg("boss.bar-range", 64.0);
        for (Player p : l.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(l) <= range * range * 2.0) {
                p.playSound(p.getLocation(), s, SoundCategory.HOSTILE, vol, pitch);
            }
        }
    }

    private void showTitleNear(String main, String sub) {
        Title t = Title.title(MM.deserialize(main), MM.deserialize(sub),
                Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2600), Duration.ofMillis(800)));
        Bukkit.getServer().showTitle(t);
    }

    private void announceSpawn(Location loc) {
        Bukkit.getServer().sendMessage(MM.deserialize(
                "<dark_gray><strikethrough>                                                </strikethrough>\n"
                        + "  <gradient:#8a2be2:#00e5ff><bold>☠ THE HOLLOW KING HA DESPERTADO ☠</bold></gradient>\n"
                        + "  <gray>Una sombra antigua se alza en <white>" + loc.getWorld().getName() + "</white> <dark_gray>(" + loc.getBlockX() + ", "
                        + loc.getBlockY() + ", " + loc.getBlockZ() + ")\n"
                        + "<dark_gray><strikethrough>                                                </strikethrough>"));
        showTitleNear("<gradient:#8a2be2:#00e5ff><bold>THE HOLLOW KING</bold></gradient>", "<gray>La corona vacía despierta...");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 0.9f, 0.6f);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, SoundCategory.MASTER, 0.9f, 0.7f);
        }
    }

    /* ======================= Bucle principal ======================= */

    private void tick() {
        if (!alive()) {
            if (!dead) cleanupSilently();
            return;
        }
        ticks++;
        rig.tick();
        BossManager.clearEquipmentIfArmed(entity);
        if (ticks % 5 == 0) updateBar();
        ambient();

        switch (stage) {
            case INTRO -> tickIntro();
            case TRANSITION -> tickTransition();
            case FIGHT -> tickFight();
        }
    }

    private void tickIntro() {
        stageTicks++;
        Location l = entity.getLocation();
        World w = l.getWorld();
        w.spawnParticle(Particle.REVERSE_PORTAL, l.clone().add(0, 0.2, 0), 25, 1.5, 0.2, 1.5, 0.3);
        if (stageTicks % 8 == 0) ring(l, 2.0 + stageTicks * 0.06, Color.fromRGB(138, 43, 226), 1.4f);
        int introTicks = plugin.getConfig().getInt("boss.intro-ticks", 60);
        if (stageTicks >= introTicks) {
            stage = Stage.FIGHT;
            stageTicks = 0;
            entity.setInvulnerable(false);
            entity.setAI(true);
            cooldown = 50;
            w.spawnParticle(Particle.EXPLOSION, l.clone().add(0, 1, 0), 6, 1.5, 0.5, 1.5, 0);
            broadcastSound(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.7f);
            rig.pulse();
        }
    }

    private void tickFight() {
        checkPhase();
        if (stage != Stage.FIGHT) return;

        if (ticks % 40 == 0 && phase == 3) {
            for (Player p : entity.getWorld().getPlayers()) {
                if (playable(p) && p.getLocation().distanceSquared(entity.getLocation()) < 14 * 14) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false, false));
                }
            }
        }
        if (ticks % 100 == 0) broadcastSound(Sound.ENTITY_WITHER_AMBIENT, 0.7f, 0.5f);

        Player target = nearestPlayer(40);
        if (target != null && ticks % 25 == 0 && target.getLocation().distanceSquared(entity.getLocation()) < 20) {
            rig.lunge();
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (target == null) return;
        castAbility(target);
    }

    private void tickTransition() {
        stageTicks++;
        Location l = entity.getLocation();
        World w = l.getWorld();
        double h = 2.0 + stageTicks * 0.05;
        double ang = stageTicks * 0.5;
        for (int i = 0; i < 3; i++) {
            double a = ang + i * (Math.PI * 2 / 3);
            Location p = l.clone().add(Math.cos(a) * 2.2, h % 4.5, Math.sin(a) * 2.2);
            w.spawnParticle(nextPhase == 3 ? Particle.FLAME : Particle.SOUL_FIRE_FLAME, p, 3, 0.05, 0.05, 0.05, 0.01);
            w.spawnParticle(Particle.REVERSE_PORTAL, p, 4, 0.2, 0.2, 0.2, 0.1);
        }
        if (stageTicks == 25) {
            rig.setBodyModel(BODY_CMD[nextPhase]);
            rig.setHaloModel(HALO_CMD[nextPhase]);
            w.spawnParticle(Particle.FLASH, l.clone().add(0, 2, 0), 1);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, l.clone().add(0, 2, 0), 2, 1, 1, 1, 0);
            broadcastSound(Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.6f);
            rig.pulse();
        }
        if (stageTicks == 40) ring(l, 3.0, Color.fromRGB(255, 255, 255), 1.6f);
        if (stageTicks >= 60) finishTransition();
    }

    private void checkPhase() {
        double ratio = entity.getHealth() / maxHp;
        if (phase == 1 && ratio <= cfg("boss.phase2-threshold", 0.66)) beginTransition(2);
        else if (phase == 2 && ratio <= cfg("boss.phase3-threshold", 0.33)) beginTransition(3);
    }

    private void beginTransition(int next) {
        stage = Stage.TRANSITION;
        stageTicks = 0;
        nextPhase = next;
        entity.setAI(false);
        entity.setInvulnerable(true);
        rig.pulse();
        pushPlayers(10.0, 1.8, 0.6);
        if (next == 2) {
            Bukkit.getServer().sendMessage(MM.deserialize("\n  <gradient:#8a2be2:#c77dff><bold>⚔ FASE II · EL REY DESATADO ⚔</bold></gradient>\n  <gray>La armadura se resquebraja. El Rey ya no se contiene.\n"));
            showTitleNear("<gradient:#8a2be2:#c77dff><bold>EL REY DESATADO</bold></gradient>", "<gray>Fase II");
            broadcastSound(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.55f);
            broadcastSound(Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.8f);
        } else {
            Bukkit.getServer().sendMessage(MM.deserialize("\n  <gradient:#c1121f:#ff7a00><bold>☠ FASE III · APOCALIPSIS ☠</bold></gradient>\n  <gray>La corona arde. Nada quedará en pie.\n"));
            showTitleNear("<gradient:#c1121f:#ff7a00><bold>APOCALIPSIS</bold></gradient>", "<red>Fase III");
            broadcastSound(Sound.ENTITY_WITHER_SPAWN, 1.3f, 0.5f);
            broadcastSound(Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.6f);
        }
    }

    private void finishTransition() {
        phase = nextPhase;
        stage = Stage.FIGHT;
        stageTicks = 0;
        applyPhaseStats();
        entity.setInvulnerable(false);
        entity.setAI(true);
        cooldown = 45;
        bar.color(phase == 2 ? BossBar.Color.BLUE : BossBar.Color.RED);
        updateBar();
    }

    private void applyPhaseStats() {
        BossManager.setAttr(entity, Attribute.GENERIC_ATTACK_DAMAGE, phaseCfg("melee-damage", 8.0));
        BossManager.setAttr(entity, Attribute.GENERIC_MOVEMENT_SPEED, phaseCfg("speed", 0.26));
    }

    private void ambient() {
        if (ticks % 3 != 0) return;
        Location l = entity.getLocation().add(0, 1.8, 0);
        World w = l.getWorld();
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, l, 3, 0.9, 1.6, 0.9, 0.01);
        w.spawnParticle(Particle.SMOKE, l, 3, 1.0, 1.8, 1.0, 0.01);
        if (phase >= 2) w.spawnParticle(Particle.WITCH, l, 4, 1.0, 1.7, 1.0, 0.02);
        if (phase == 3) {
            w.spawnParticle(Particle.FLAME, l, 3, 1.0, 1.8, 1.0, 0.02);
            w.spawnParticle(Particle.ELECTRIC_SPARK, l, 2, 1.2, 1.8, 1.2, 0.2);
        }
    }

    /* ======================= Bossbar ======================= */

    private void updateBar() {
        if (dead) return;
        double hp = Math.max(0.0, entity.getHealth());
        bar.progress((float) Math.max(0.0, Math.min(1.0, hp / maxHp)));
        bar.name(title());
        double range = cfg("boss.bar-range", 64.0);
        double r2 = range * range;
        Location bl = entity.getLocation();
        Set<Player> now = new HashSet<>();
        for (Player p : bl.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(bl) <= r2) now.add(p);
        }
        for (Player p : now) if (!viewers.contains(p)) p.showBossBar(bar);
        for (Player p : new ArrayList<>(viewers)) if (!now.contains(p)) p.hideBossBar(bar);
        viewers.clear();
        viewers.addAll(now);
    }

    public void forgetViewer(Player p) {
        if (viewers.remove(p)) p.hideBossBar(bar);
    }

    private void hideBar() {
        for (Player p : new ArrayList<>(viewers)) p.hideBossBar(bar);
        viewers.clear();
    }

    public void addDamage(Player p, double amount) {
        damageDealt.merge(p.getUniqueId(), amount, Double::sum);
    }

    /* ======================= Habilidades ======================= */

    private boolean playable(Player p) {
        return p.isOnline() && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    private Player nearestPlayer(double range) {
        Player best = null;
        double bd = range * range;
        Location l = entity.getLocation();
        for (Player p : l.getWorld().getPlayers()) {
            if (!playable(p)) continue;
            double d = p.getLocation().distanceSquared(l);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private void later(int delay, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (alive()) r.run();
        }, Math.max(0, delay));
    }

    private void lockAI(int ticksLocked) {
        entity.setAI(false);
        later(ticksLocked, () -> {
            if (stage == Stage.FIGHT) entity.setAI(true);
        });
    }

    private void face(Player t) {
        Vector d = t.getLocation().toVector().subtract(entity.getLocation().toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        entity.setRotation(yaw, 0f);
    }

    private double abilityDamage() { return phaseCfg("ability-damage", 7.0); }

    private void hurt(Player p, double dmg) {
        if (!playable(p)) return;
        p.damage(dmg, entity);
    }

    private void castAbility(Player target) {
        List<String> pool = new ArrayList<>();
        switch (phase) {
            case 1 -> pool.addAll(List.of("bolt", "slam"));
            case 2 -> pool.addAll(List.of("bolt", "slam", "wave", "pulse", "summon"));
            default -> pool.addAll(List.of("bolt", "slam", "wave", "meteors", "combo", "summon"));
        }
        pool.remove(lastAbility);
        String pick = pool.get(rnd.nextInt(pool.size()));
        if (pick.equals("summon") && manager.aliveWraiths(minions) >= (int) cfg("boss.wraiths.max-alive", 4)) pick = "bolt";
        lastAbility = pick;
        cooldown = (int) phaseCfg("ability-cooldown", 100);
        face(target);
        switch (pick) {
            case "bolt" -> bolt(target);
            case "slam" -> slam();
            case "wave" -> wave();
            case "pulse" -> pulse();
            case "summon" -> summon();
            case "meteors" -> { meteors(); cooldown += 40; }
            case "combo" -> { combo(target); cooldown += 90; }
            default -> { }
        }
    }

    /** Rayo de sombras: línea telegrafiada 0.9 s antes; se esquiva moviéndose lateralmente. */
    private void bolt(Player target) {
        Vector dir = target.getEyeLocation().toVector()
                .subtract(entity.getLocation().add(0, 3.0, 0).toVector()).normalize();
        lockAI(24);
        rig.lunge();
        broadcastSound(Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.6f);
        for (int i = 0; i < 18; i += 2) {
            later(i, () -> {
                Location o = entity.getLocation().add(0, 3.0, 0);
                for (double d = 1; d < 26; d += 1.0) {
                    Location p = o.clone().add(dir.clone().multiply(d));
                    p.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(120, 0, 200), 0.9f));
                }
            });
        }
        later(18, () -> fireBolt(entity.getLocation().add(0, 3.0, 0), dir, abilityDamage() * 1.1));
    }

    private void fireBolt(Location origin, Vector dir, double dmg) {
        World w = origin.getWorld();
        Set<UUID> hit = new HashSet<>();
        broadcastSound(Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.7f);
        Location cur = origin.clone();
        Vector step = dir.clone().multiply(0.5);
        for (double d = 0; d < 28; d += 0.5) {
            cur.add(step);
            if (!cur.getBlock().isPassable()) break;
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, cur, 2, 0.05, 0.05, 0.05, 0);
            w.spawnParticle(Particle.DUST, cur, 3, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(Color.fromRGB(80, 0, 140), 1.6f));
            for (Player p : w.getPlayers()) {
                if (hit.contains(p.getUniqueId()) || !playable(p)) continue;
                if (p.getBoundingBox().expand(0.5).contains(cur.toVector())) {
                    hit.add(p.getUniqueId());
                    hurt(p, dmg);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                }
            }
        }
    }

    /** Golpe contra el suelo: círculo telegrafiado 1.3 s antes. */
    private void slam() {
        final double radius = 5.0 + (phase - 1) * 0.9;
        final Location c = entity.getLocation().clone();
        lockAI(32);
        broadcastSound(Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.6f);
        for (int i = 0; i < 26; i += 3) {
            later(i, () -> ring(c, radius, Color.fromRGB(200, 30, 60), 1.3f));
        }
        later(8, () -> rig.pulse());
        later(26, () -> {
            rig.slam();
            World w = c.getWorld();
            w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.5, 0), 12, radius * 0.4, 0.3, radius * 0.4, 0);
            w.spawnParticle(Particle.LARGE_SMOKE, c.clone().add(0, 0.3, 0), 40, radius * 0.5, 0.2, radius * 0.5, 0.05);
            broadcastSound(Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
            for (Player p : w.getPlayers()) {
                if (!playable(p)) continue;
                Location pl = p.getLocation();
                double dx = pl.getX() - c.getX(), dz = pl.getZ() - c.getZ();
                if (Math.sqrt(dx * dx + dz * dz) <= radius && Math.abs(pl.getY() - c.getY()) < 3.0) {
                    hurt(p, abilityDamage());
                    Vector kb = new Vector(dx, 0, dz);
                    if (kb.lengthSquared() > 1.0E-4) kb.normalize().multiply(0.9);
                    p.setVelocity(kb.setY(0.55));
                }
            }
        });
    }

    /** Onda de energía: anillo expansivo que se esquiva saltando. */
    private void wave() {
        final Location c = entity.getLocation().clone();
        final double dmg = abilityDamage();
        final Set<UUID> hit = new HashSet<>();
        lockAI(34);
        rig.slam();
        broadcastSound(Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.2f, 0.7f);
        for (int i = 0; i < 12; i += 4) later(i, () -> ring(c, 2.5, Color.fromRGB(0, 229, 255), 1.5f));
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!alive()) { cancel(); return; }
                t++;
                double r = 1.5 + t * 0.7;
                ring(c, r, Color.fromRGB(0, 229, 255), 1.3f);
                c.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, c.clone().add(r, 0.2, 0), 1, 0, 0, 0, 0);
                for (Player p : c.getWorld().getPlayers()) {
                    if (!playable(p) || hit.contains(p.getUniqueId())) continue;
                    Location pl = p.getLocation();
                    double dx = pl.getX() - c.getX(), dz = pl.getZ() - c.getZ();
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (Math.abs(d - r) < 1.1 && pl.getY() < c.getY() + 0.9) {
                        hit.add(p.getUniqueId());
                        hurt(p, dmg);
                        Vector kb = new Vector(dx, 0, dz);
                        if (kb.lengthSquared() > 1.0E-4) kb.normalize().multiply(0.8);
                        p.setVelocity(kb.setY(0.4));
                    }
                }
                if (r > 19) cancel();
            }
        }.runTaskTimer(plugin, 10L, 1L);
    }

    /** Pulso de empuje: anillo que se contrae y después lanza a todos hacia fuera. */
    private void pulse() {
        final Location c = entity.getLocation().clone();
        lockAI(30);
        broadcastSound(Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.5f);
        for (int i = 0; i < 22; i += 2) {
            final double r = 9.0 - i * 0.28;
            later(i, () -> ring(c, r, Color.fromRGB(199, 125, 255), 1.4f));
        }
        later(24, () -> {
            rig.pulse();
            c.getWorld().spawnParticle(Particle.EXPLOSION, c.clone().add(0, 1, 0), 10, 2.5, 0.8, 2.5, 0);
            broadcastSound(Sound.ENTITY_WARDEN_SONIC_BOOM, 1.1f, 0.8f);
            for (Player p : c.getWorld().getPlayers()) {
                if (!playable(p) || p.getLocation().distanceSquared(c) > 81) continue;
                hurt(p, abilityDamage() * 0.6);
                Vector kb = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                if (kb.lengthSquared() > 1.0E-4) kb.normalize().multiply(1.7);
                p.setVelocity(kb.setY(0.6));
            }
        });
    }

    private void summon() {
        int count = phase == 3 ? 3 : 2;
        lockAI(30);
        rig.pulse();
        broadcastSound(Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 0.6f);
        later(18, () -> {
            for (int i = 0; i < count; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                Location l = entity.getLocation().add(Math.cos(a) * 3.5, 1.5, Math.sin(a) * 3.5);
                l.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, l, 25, 0.4, 0.6, 0.4, 0.05);
                minions.add(manager.spawnWraith(l));
            }
        });
    }

    /** Meteoros de energía oscura sobre los jugadores; cada impacto se marca con 1.5 s de antelación. */
    private void meteors() {
        List<Player> targets = new ArrayList<>();
        for (Player p : entity.getWorld().getPlayers()) {
            if (playable(p) && p.getLocation().distanceSquared(entity.getLocation()) < 35 * 35) targets.add(p);
        }
        java.util.Collections.shuffle(targets, rnd);
        if (targets.size() > 4) targets = new ArrayList<>(targets.subList(0, 4));
        final List<Player> chosen = targets;
        lockAI(40);
        rig.pulse();
        broadcastSound(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
        for (int wave = 0; wave < 3; wave++) {
            later(wave * 25, () -> {
                for (Player p : chosen) {
                    if (!playable(p)) continue;
                    final Location t = ground(p.getLocation());
                    for (int i = 0; i < 28; i += 4) later(i, () -> ring(t, 3.2, Color.fromRGB(255, 80, 0), 1.2f));
                    later(30, () -> meteorImpact(t));
                }
            });
        }
    }

    private void meteorImpact(Location t) {
        World w = t.getWorld();
        w.spawnParticle(Particle.EXPLOSION_EMITTER, t.clone().add(0, 1, 0), 1);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, t.clone().add(0, 1, 0), 60, 1.4, 1.2, 1.4, 0.1);
        w.spawnParticle(Particle.REVERSE_PORTAL, t.clone().add(0, 1, 0), 60, 1.2, 1.2, 1.2, 0.3);
        w.playSound(t, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 0.7f);
        for (Player p : w.getPlayers()) {
            if (!playable(p) || p.getLocation().distanceSquared(t) > 3.6 * 3.6) continue;
            hurt(p, abilityDamage() * 1.1);
            Vector kb = p.getLocation().toVector().subtract(t.toVector()).setY(0);
            if (kb.lengthSquared() > 1.0E-4) kb.normalize().multiply(0.7);
            p.setVelocity(kb.setY(0.5));
        }
    }

    /** Combo del Apocalipsis: golpe al suelo, onda y rayo encadenados. */
    private void combo(Player target) {
        slam();
        later(40, () -> wave());
        later(82, () -> {
            Player t = nearestPlayer(40);
            if (t != null) { face(t); bolt(t); }
        });
    }

    private Location ground(Location l) {
        RayTraceResult r = l.getWorld().rayTraceBlocks(l.clone().add(0, 1, 0), new Vector(0, -1, 0), 8.0);
        if (r != null && r.getHitPosition() != null) {
            return new Location(l.getWorld(), l.getX(), r.getHitPosition().getY() + 0.1, l.getZ());
        }
        return l.clone();
    }

    private void ring(Location c, double radius, Color color, float size) {
        World w = c.getWorld();
        int points = Math.max(16, (int) (radius * 7));
        Particle.DustOptions opt = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * radius, c.getY() + 0.2, c.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0, opt);
        }
    }

    private void pushPlayers(double radius, double power, double up) {
        Location c = entity.getLocation();
        for (Player p : c.getWorld().getPlayers()) {
            if (!playable(p) || p.getLocation().distanceSquared(c) > radius * radius) continue;
            Vector kb = p.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (kb.lengthSquared() > 1.0E-4) kb.normalize().multiply(power);
            p.setVelocity(kb.setY(up));
        }
    }

    /* ======================= Muerte y recompensas ======================= */

    /** Se llama una única vez desde BossManager al morir el jefe. */
    public void onDeath() {
        if (dead) return;
        dead = true;
        task.cancel();
        hideBar();
        manager.killWraiths(minions);
        minions.clear();

        Location loc = entity.getLocation().clone();
        World w = loc.getWorld();

        // Anuncio de victoria con los mayores daños
        List<Map.Entry<UUID, Double>> top = new ArrayList<>(damageDealt.entrySet());
        top.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()));
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<UUID, Double> en : top) {
            if (n++ >= 3) break;
            Player p = Bukkit.getPlayer(en.getKey());
            String name = p != null ? p.getName() : String.valueOf(Bukkit.getOfflinePlayer(en.getKey()).getName());
            sb.append("\n  <gray>").append(n).append(". <white>").append(name).append(" <dark_gray>— <red>")
                    .append((int) Math.round(en.getValue())).append(" de daño");
        }
        Bukkit.getServer().sendMessage(MM.deserialize(
                "<dark_gray><strikethrough>                                                </strikethrough>\n"
                        + "  <gradient:#ffd166:#c77dff><bold>✦ THE HOLLOW KING HA CAÍDO ✦</bold></gradient>\n"
                        + "  <gray>La corona vuelve a quedar vacía." + sb + "\n"
                        + "<dark_gray><strikethrough>                                                </strikethrough>"));
        showTitleNear("<gradient:#ffd166:#c77dff><bold>VICTORIA</bold></gradient>", "<gray>THE HOLLOW KING ha sido derrotado");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, SoundCategory.MASTER, 0.8f, 0.8f);
        }

        // Explosión de partículas
        w.spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 2, 0), 4, 1.5, 1.2, 1.5, 0);
        w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 150, 1.8, 2.0, 1.8, 0.3);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 2, 0), 120, 1.5, 2.0, 1.5, 0.15);

        dropRewards(loc);

        // Animación de muerte del modelo
        manager.trackDying(rig);
        rig.playDeath(() -> manager.untrackDying(rig));
    }

    private void dropRewards(Location loc) {
        int min = Math.max(0, plugin.getConfig().getInt("boss.rewards.coins-min", 8));
        int max = Math.max(min, plugin.getConfig().getInt("boss.rewards.coins-max", 16));
        int coins = min + rnd.nextInt(max - min + 1);
        for (int i = 0; i < coins; i++) {
            dropItem(loc, plugin.items().create(ItemManager.COIN, 1));
        }
        dropItem(loc, plugin.items().create(ItemManager.PLUMA, 1)); // objeto especial garantizado
        double chance = Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble("boss.rewards.relic-chance", 0.15)));
        if (rnd.nextDouble() < chance) {
            dropItem(loc, plugin.items().create(ItemManager.HEART, 1));
            Bukkit.getServer().sendMessage(MM.deserialize("<dark_gray>» <red>¡Un <bold>Corazón Hueco</bold> ha caído del Rey!"));
        }
        int xp = plugin.getConfig().getInt("boss.rewards.xp", 1500);
        if (xp > 0) loc.getWorld().spawn(loc.clone().add(0, 1, 0), ExperienceOrb.class, o -> o.setExperience(xp));
    }

    private void dropItem(Location loc, ItemStack stack) {
        if (stack == null) return;
        Item it = loc.getWorld().dropItem(loc.clone().add(0, 1.5, 0), stack);
        it.setVelocity(new Vector((rnd.nextDouble() - 0.5) * 0.5, 0.35 + rnd.nextDouble() * 0.3, (rnd.nextDouble() - 0.5) * 0.5));
        it.setGlowing(true);
        it.setInvulnerable(true);
        it.setPickupDelay(30);
    }

    /* ======================= Limpieza ======================= */

    /** La entidad base desapareció sin morir (descarga de chunk, /kill, etc.). */
    private void cleanupSilently() {
        dead = true;
        task.cancel();
        hideBar();
        rig.remove();
        manager.killWraiths(minions);
        minions.clear();
    }

    /** Cierre del plugin o /ytboss remove. */
    public void shutdown() {
        dead = true;
        task.cancel();
        hideBar();
        rig.remove();
        manager.killWraiths(minions);
        minions.clear();
        if (entity.isValid()) entity.remove();
    }
}
