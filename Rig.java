package com.ytcustom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Cuerpo visual de una entidad: ItemDisplays con modelos 3D del resource pack que siguen
 * a una entidad base invisible (la que aporta hitbox, IA y vida).
 */
public final class Rig {

    private final YTCustom plugin;
    private final LivingEntity base;
    private final ItemDisplay body;
    private final ItemDisplay halo;       // puede ser null
    private final float fullScale;
    private final float haloFullScale;
    private final double haloY;
    private final float yawOffset;

    private float scale;
    private int tick = 0;
    private int busyUntil = 0;
    private boolean breathe = false;
    private float haloAngle = 0f;
    private boolean detached = false;
    private BukkitTask deathTask;

    public Rig(YTCustom plugin, LivingEntity base, int bodyCmd, float fullScale, float startScale,
               int haloCmd, float haloScale, double haloY, float yawOffset) {
        this.plugin = plugin;
        this.base = base;
        this.fullScale = fullScale;
        this.scale = startScale;
        this.haloFullScale = haloScale;
        this.haloY = haloY;
        this.yawOffset = yawOffset;

        Location l = base.getLocation();
        this.body = spawnDisplay(l, bodyCmd, 12);
        body.setTransformation(bodyTransform(scale, null));
        if (haloCmd > 0) {
            this.halo = spawnDisplay(l, haloCmd, 15);
            halo.setTransformation(haloTransform());
        } else {
            this.halo = null;
        }
    }

    private ItemDisplay spawnDisplay(Location l, int cmd, int light) {
        return l.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(ItemManager.modelItem(cmd));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(light, light));
            d.setViewRange(2.0f);
            d.setPersistent(false);
            d.setGravity(false);
            d.getPersistentDataContainer().set(Keys.TEMP, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** El modelo mide 1 bloque centrado en su origen; se eleva para que los pies queden en la entidad. */
    private Transformation bodyTransform(float s, Quaternionf rot) {
        Quaternionf q = rot == null ? new Quaternionf() : new Quaternionf(rot);
        Vector3f t = new Vector3f(0f, 0.5f * s, 0f);
        q.transform(t); // rotación alrededor de los pies
        return new Transformation(t, q, new Vector3f(s, s, s), new Quaternionf());
    }

    private Transformation haloTransform() {
        float ratio = fullScale <= 0 ? 1f : scale / fullScale;
        float s = haloFullScale * ratio;
        return new Transformation(new Vector3f(0f, (float) (haloY * ratio), 0f),
                new Quaternionf().rotateY(haloAngle), new Vector3f(s, s, s), new Quaternionf());
    }

    public void animate(Quaternionf rot, float mul, int duration) {
        if (body == null || !body.isValid()) return;
        body.setInterpolationDelay(0);
        body.setInterpolationDuration(duration);
        body.setTransformation(bodyTransform(scale * mul, rot));
    }

    public void setScale(float newScale, int duration) {
        this.scale = newScale;
        animate(null, 1f, duration);
        if (halo != null && halo.isValid()) {
            halo.setInterpolationDelay(0);
            halo.setInterpolationDuration(duration);
            halo.setTransformation(haloTransform());
        }
    }

    public void setBodyModel(int cmd) {
        if (body.isValid()) body.setItemStack(ItemManager.modelItem(cmd));
    }

    public void setHaloModel(int cmd) {
        if (halo != null && halo.isValid()) halo.setItemStack(ItemManager.modelItem(cmd));
    }

    /** Debe llamarse cada tick. */
    public void tick() {
        if (detached || !base.isValid() || !body.isValid()) return;
        tick++;
        Location l = base.getLocation();
        l.setYaw(l.getYaw() + yawOffset);
        l.setPitch(0f);
        body.teleport(l);
        if (halo != null && halo.isValid()) {
            halo.teleport(l);
            if (tick % 10 == 0) {
                haloAngle += (float) (Math.PI / 2.0);
                halo.setInterpolationDelay(0);
                halo.setInterpolationDuration(10);
                halo.setTransformation(haloTransform());
            }
        }
        if (tick % 24 == 0 && tick >= busyUntil) {
            breathe = !breathe;
            animate(null, breathe ? 1.03f : 1.0f, 24);
        }
    }

    private void resetLater(int delay) {
        busyUntil = tick + delay + 6;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!detached && body.isValid()) animate(null, 1f, 6);
        }, delay);
    }

    /** Inclinación hacia delante (golpe). */
    public void lunge() {
        animate(new Quaternionf().rotateX(0.32f), 1.05f, 3);
        resetLater(5);
    }

    /** Pulso de escala (rugido/transformación). */
    public void pulse() {
        animate(null, 1.25f, 6);
        resetLater(8);
    }

    /** Golpe contra el suelo. */
    public void slam() {
        animate(new Quaternionf().rotateX(0.55f), 0.97f, 4);
        resetLater(6);
    }

    /** Animación de muerte: tiembla, gira, se encoge y desaparece. Llama a done al terminar. */
    public void playDeath(Runnable done) {
        detached = true;
        final Location l = body.getLocation().clone();
        final World w = l.getWorld();
        deathTask = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                if (!body.isValid()) {
                    finish();
                    return;
                }
                if (t == 1) animate(new Quaternionf().rotateZ(0.12f), 1.08f, 4);
                if (t == 6) animate(new Quaternionf().rotateZ(-0.12f), 1.08f, 4);
                if (t == 12) animate(new Quaternionf().rotateY(0.7f * (float) Math.PI), 1.15f, 10);
                if (t == 25) animate(new Quaternionf().rotateY(1.4f * (float) Math.PI), 0.7f, 15);
                if (t == 42) animate(new Quaternionf().rotateY(2.1f * (float) Math.PI), 0.02f, 18);
                if (t % 3 == 0) {
                    double h = scale * 1.9;
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.clone().add(0, h * 0.5, 0), 14, 0.9, h * 0.4, 0.9, 0.04);
                    w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, h * 0.5, 0), 6, 0.8, h * 0.4, 0.8, 0.02);
                }
                if (halo != null && halo.isValid() && t == 25) {
                    halo.setInterpolationDelay(0);
                    halo.setInterpolationDuration(30);
                    haloAngle += (float) Math.PI * 0.9f;
                    halo.setTransformation(new Transformation(new Vector3f(0, 0.2f, 0),
                            new Quaternionf().rotateY(haloAngle), new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf()));
                }
                if (t >= 62) finish();
            }

            private void finish() {
                w.spawnParticle(Particle.EXPLOSION_EMITTER, l.clone().add(0, 2, 0), 3, 1.2, 1.0, 1.2, 0);
                w.spawnParticle(Particle.END_ROD, l.clone().add(0, 2, 0), 120, 1.5, 1.8, 1.5, 0.25);
                w.spawnParticle(Particle.SOUL, l.clone().add(0, 2, 0), 60, 1.2, 1.5, 1.2, 0.1);
                remove();
                cancel();
                if (done != null) done.run();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void remove() {
        detached = true;
        if (deathTask != null) {
            deathTask.cancel();
            deathTask = null;
        }
        if (body != null && body.isValid()) body.remove();
        if (halo != null && halo.isValid()) halo.remove();
    }
}
