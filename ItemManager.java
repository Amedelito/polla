package com.ytcustom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Level;

/**
 * Crea e identifica objetos personalizados.
 * Cada objeto lleva en su PersistentDataContainer un id y una firma HMAC-SHA256
 * calculada con un secreto único del servidor (plugins/YTCustom/secret.key).
 * Un objeto vanilla con el mismo nombre/lore NO pasa la verificación.
 */
public final class ItemManager {

    public static final String COIN = "hollow_coin";
    public static final String PLUMA = "pluma_impulso";
    public static final String HEART = "hollow_heart";
    public static final String BLADE = "hollow_blade";
    public static final String PICKAXE = "hollow_pickaxe";
    public static final String INGOT = "hollowite_ingot";
    public static final String ORE = "hollowite_ore";

    public static final List<String> ALL_IDS = List.of(COIN, PLUMA, HEART, BLADE, PICKAXE, INGOT, ORE);

    // CustomModelData
    public static final int CMD_COIN = 2001, CMD_PLUMA = 2002, CMD_HEART = 2003, CMD_BLADE = 2004,
            CMD_PICKAXE = 2005, CMD_INGOT = 2006, CMD_ORE = 2007;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final YTCustom plugin;
    private byte[] secret;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    public ItemManager(YTCustom plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        File f = new File(plugin.getDataFolder(), "secret.key");
        try {
            plugin.getDataFolder().mkdirs();
            if (!f.exists()) {
                byte[] raw = new byte[32];
                new SecureRandom().nextBytes(raw);
                Files.writeString(f.toPath(), HexFormat.of().formatHex(raw));
                plugin.getLogger().info("Generado secret.key (firma anti-falsificación). No lo compartas.");
            }
            secret = HexFormat.of().parseHex(Files.readString(f.toPath()).trim());
            return secret.length >= 16;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo leer/crear secret.key", e);
            return false;
        }
    }

    /* ---------------- Firma / identificación ---------------- */

    private String sign(String id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(id.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC no disponible", e);
        }
    }

    /** Devuelve el id del objeto personalizado si la firma es válida; si no, null. */
    public String idOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(Keys.ID, PersistentDataType.STRING);
        String sig = pdc.get(Keys.SIG, PersistentDataType.STRING);
        if (id == null || sig == null) return null;
        boolean ok = MessageDigest.isEqual(sign(id).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
        return ok ? id : null;
    }

    public boolean is(ItemStack item, String id) {
        return id.equals(idOf(item));
    }

    /* ---------------- Creación ---------------- */

    private static Component c(String mini) {
        return MM.deserialize("<!italic>" + mini);
    }

    private ItemStack build(String id, Material mat, int cmd, String name, List<String> lore, boolean glint) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(c(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(c(s));
        meta.lore(l);
        meta.setCustomModelData(cmd);
        if (glint) meta.setEnchantmentGlintOverride(true);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ID, PersistentDataType.STRING, id);
        pdc.set(Keys.SIG, PersistentDataType.STRING, sign(id));
        item.setItemMeta(meta);
        return item;
    }

    /** Crea un objeto personalizado por id. Devuelve null si el id no existe. */
    public ItemStack create(String id, int amount) {
        ItemStack item;
        switch (id) {
            case COIN -> item = build(COIN, Material.GOLD_NUGGET, CMD_COIN,
                    "<gradient:#ffd166:#c77dff><bold>Moneda Hueca</bold></gradient>",
                    List.of("<dark_gray>Acuñada con el eco de un rey olvidado.",
                            "<gray>Moneda del reino <dark_purple>sin sombra</dark_purple>.",
                            "", "<dark_purple>✦ Moneda de YTCustom"), true);
            case PLUMA -> {
                item = build(PLUMA, Material.FEATHER, CMD_PLUMA,
                        "<gradient:#00e5ff:#8a2be2><bold>Pluma de Impulso</bold></gradient>",
                        List.of(), true);
                ItemMeta m = item.getItemMeta();
                m.setMaxStackSize(1);
                item.setItemMeta(m);
                setCharges(item, plugin.getConfig().getInt("pluma.charges", 6));
            }
            case HEART -> item = build(HEART, Material.NETHER_STAR, CMD_HEART,
                    "<gradient:#c1121f:#8a2be2><bold>Corazón Hueco</bold></gradient>",
                    List.of("<gray>Todavía late, aunque no tenga dueño.",
                            "<dark_gray>Reliquia del Rey Hueco.", "", "<red>✦ Reliquia poco común"), true);
            case BLADE -> {
                item = build(BLADE, Material.NETHERITE_SWORD, CMD_BLADE,
                        "<gradient:#8a2be2:#00e5ff><bold>Filo Hueco</bold></gradient>",
                        List.of("<gray>Daño de ataque: <white>" + fmt(plugin.getConfig().getDouble("items.blade-damage", 9.0)),
                                "<gray>Velocidad: <white>" + fmt(plugin.getConfig().getDouble("items.blade-attack-speed", 1.6)),
                                "<dark_purple>Los golpes ralentizan al enemigo.",
                                "", "<dark_purple>✦ Arma de YTCustom"), true);
                ItemMeta m = item.getItemMeta();
                double dmg = plugin.getConfig().getDouble("items.blade-damage", 9.0);
                double spd = plugin.getConfig().getDouble("items.blade-attack-speed", 1.6);
                m.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE,
                        new AttributeModifier(new NamespacedKey(plugin, "blade_damage"), dmg - 1.0,
                                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                m.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
                        new AttributeModifier(new NamespacedKey(plugin, "blade_speed"), spd - 4.0,
                                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(m);
            }
            case PICKAXE -> {
                item = build(PICKAXE, Material.NETHERITE_PICKAXE, CMD_PICKAXE,
                        "<gradient:#8a2be2:#00e5ff><bold>Pico Hueco</bold></gradient>",
                        List.of("<gray>Forjado con Hollowite.", "<dark_gray>Eficiencia V · Irrompibilidad III",
                                "", "<dark_purple>✦ Herramienta de YTCustom"), true);
                ItemMeta m = item.getItemMeta();
                m.addEnchant(Enchantment.EFFICIENCY, 5, true);
                m.addEnchant(Enchantment.UNBREAKING, 3, true);
                item.setItemMeta(m);
            }
            case INGOT -> item = build(INGOT, Material.NETHERITE_SCRAP, CMD_INGOT,
                    "<gradient:#8a2be2:#00e5ff>Lingote de Hollowite</gradient>",
                    List.of("<gray>Metal oscuro que brilla desde dentro.", "", "<dark_purple>✦ Material de YTCustom"), false);
            case ORE -> item = build(ORE, Material.NOTE_BLOCK, CMD_ORE,
                    "<gradient:#8a2be2:#00e5ff>Mena de Hollowite</gradient>",
                    List.of("<gray>Aparece en lo profundo de la pizarra.", "", "<dark_purple>✦ Bloque de YTCustom"), false);
            default -> {
                return null;
            }
        }
        item.setAmount(Math.max(1, amount));
        return item;
    }

    private static String fmt(double d) {
        return (d == Math.floor(d)) ? String.valueOf((long) d) : String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    /** Objeto base de modelos visuales (ItemDisplay). */
    public static ItemStack modelItem(int cmd) {
        ItemStack it = new ItemStack(Material.ECHO_SHARD);
        ItemMeta m = it.getItemMeta();
        m.setCustomModelData(cmd);
        it.setItemMeta(m);
        return it;
    }

    /* ---------------- Cargas de la Pluma ---------------- */

    public int getCharges(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(Keys.CHARGES, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public void setCharges(ItemStack item, int charges) {
        ItemMeta m = item.getItemMeta();
        m.getPersistentDataContainer().set(Keys.CHARGES, PersistentDataType.INTEGER, charges);
        int cd = plugin.getConfig().getInt("pluma.cooldown-seconds", 18);
        List<Component> lore = new ArrayList<>();
        lore.add(c("<gray>Usa <white>clic derecho</white> para impulsarte hacia delante."));
        lore.add(c("<gray>Recarga: <white>" + cd + "s"));
        lore.add(c("<gray>Cargas: <aqua>" + charges));
        lore.add(Component.empty());
        lore.add(c("<dark_aqua>✦ Reliquia del Rey Hueco"));
        m.lore(lore);
        item.setItemMeta(m);
    }

    /* ---------------- Monedas ---------------- */

    public int countCoins(Player p) {
        int total = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && is(it, COIN)) total += it.getAmount();
        }
        for (ItemStack it : p.getEnderChest().getContents()) {
            if (it != null && is(it, COIN)) total += it.getAmount();
        }
        return total;
    }

    /* ---------------- Recetas ---------------- */

    public void registerRecipes() {
        if (!plugin.getConfig().getBoolean("items.recipes-enabled", true)) return;
        try {
            ItemStack ingot = create(INGOT, 1);
            RecipeChoice.ExactChoice ing = new RecipeChoice.ExactChoice(ingot);

            NamespacedKey bladeKey = new NamespacedKey(plugin, "hollow_blade");
            ShapedRecipe blade = new ShapedRecipe(bladeKey, create(BLADE, 1));
            blade.shape(" I ", " I ", " S ");
            blade.setIngredient('I', ing);
            blade.setIngredient('S', Material.STICK);
            Bukkit.addRecipe(blade);
            recipeKeys.add(bladeKey);

            NamespacedKey pickKey = new NamespacedKey(plugin, "hollow_pickaxe");
            ShapedRecipe pick = new ShapedRecipe(pickKey, create(PICKAXE, 1));
            pick.shape("III", " S ", " S ");
            pick.setIngredient('I', ing);
            pick.setIngredient('S', Material.STICK);
            Bukkit.addRecipe(pick);
            recipeKeys.add(pickKey);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron registrar las recetas", e);
        }
    }

    public void unregisterRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k);
        recipeKeys.clear();
    }
}
