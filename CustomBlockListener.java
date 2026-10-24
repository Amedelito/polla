package com.ytcustom;

import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

/**
 * Bloque personalizado "Mena de Hollowite" implementado con NOTE_BLOCK
 * (instrument BIT, nota 7). El resource pack sustituye su blockstate.
 */
public final class CustomBlockListener implements Listener {

    public static final Instrument ORE_INSTRUMENT = Instrument.BIT;
    public static final int ORE_NOTE = 7;

    private final YTCustom plugin;
    private final Random random = new Random();

    public CustomBlockListener(YTCustom plugin) {
        this.plugin = plugin;
    }

    public static boolean isOre(Block b) {
        if (b == null || b.getType() != Material.NOTE_BLOCK) return false;
        if (!(b.getBlockData() instanceof NoteBlock nb)) return false;
        return nb.getInstrument() == ORE_INSTRUMENT && nb.getNote().getId() == ORE_NOTE;
    }

    public static NoteBlock oreData() {
        NoteBlock nb = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        nb.setInstrument(ORE_INSTRUMENT);
        nb.setNote(new Note(ORE_NOTE));
        nb.setPowered(false);
        return nb;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.items().is(e.getItemInHand(), ItemManager.ORE)) return;
        e.getBlockPlaced().setBlockData(oreData(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isOre(b)) return;
        Player p = e.getPlayer();
        e.setDropItems(false);
        e.setExpToDrop(0);
        if (p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            b.getWorld().dropItemNaturally(loc, plugin.items().create(ItemManager.ORE, 1));
        } else {
            int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
            int amount = 1 + random.nextInt(2) + (fortune > 0 ? random.nextInt(fortune + 1) : 0);
            b.getWorld().dropItemNaturally(loc, plugin.items().create(ItemManager.INGOT, amount));
        }
        b.getWorld().spawn(loc, org.bukkit.entity.ExperienceOrb.class, o -> o.setExperience(3 + random.nextInt(4)));
    }

    /** Impide que cambie su estado (instrumento) por actualizaciones de bloques vecinos. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.NOTE_BLOCK && isOre(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onNote(NotePlayEvent e) {
        if (isOre(e.getBlock())) e.setCancelled(true);
    }

    /** Impide afinar / interactuar con la mena como si fuese un bloque de notas. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (isOre(e.getClickedBlock())) e.setUseInteractedBlock(Event.Result.DENY);
    }

    /* Las explosiones y pistones no pueden convertir la mena en un bloque de notas normal. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(CustomBlockListener::isOre);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(CustomBlockListener::isOre);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (anyOre(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (anyOre(e.getBlocks())) e.setCancelled(true);
    }

    private boolean anyOre(List<Block> blocks) {
        for (Block b : blocks) if (isOre(b)) return true;
        return false;
    }
}
