package com.ytcustom;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Random;

/** Genera pequeñas vetas de Mena de Hollowite dentro de la pizarra (solo en chunks nuevos del Overworld). */
public final class OreGenerator implements Listener {

    private final YTCustom plugin;

    public OreGenerator(YTCustom plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.isNewChunk()) return;
        if (!plugin.getConfig().getBoolean("ore.generate", true)) return;
        World w = e.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) return;

        Chunk chunk = e.getChunk();
        Random r = new Random(w.getSeed() ^ (chunk.getX() * 341873128712L) ^ (chunk.getZ() * 132897987541L) ^ 0x48B0L);
        int veins = plugin.getConfig().getInt("ore.veins-per-chunk", 3);
        int size = Math.max(1, plugin.getConfig().getInt("ore.vein-size", 5));
        int minY = Math.max(w.getMinHeight() + 1, plugin.getConfig().getInt("ore.min-y", -58));
        int maxY = Math.max(minY + 1, plugin.getConfig().getInt("ore.max-y", -4));

        for (int v = 0; v < veins; v++) {
            int x = 2 + r.nextInt(12);
            int z = 2 + r.nextInt(12);
            int y = minY + r.nextInt(maxY - minY);
            for (int i = 0; i < size; i++) {
                int bx = Math.min(15, Math.max(0, x + r.nextInt(3) - 1));
                int bz = Math.min(15, Math.max(0, z + r.nextInt(3) - 1));
                int by = y + r.nextInt(3) - 1;
                Block b = chunk.getBlock(bx, by, bz);
                if (b.getType() == Material.DEEPSLATE) {
                    b.setBlockData(CustomBlockListener.oreData(), false);
                }
                x = bx;
                z = bz;
                y = by;
            }
        }
    }
}
