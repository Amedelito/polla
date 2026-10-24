package com.ytcustom;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/** Claves de PersistentDataContainer usadas por el plugin. */
public final class Keys {
    public static NamespacedKey ID;
    public static NamespacedKey SIG;
    public static NamespacedKey CHARGES;
    public static NamespacedKey TEMP;
    public static NamespacedKey BOSS;
    public static NamespacedKey MINION;

    private Keys() {}

    public static void init(JavaPlugin plugin) {
        ID = new NamespacedKey(plugin, "item_id");
        SIG = new NamespacedKey(plugin, "item_sig");
        CHARGES = new NamespacedKey(plugin, "charges");
        TEMP = new NamespacedKey(plugin, "temp_entity");
        BOSS = new NamespacedKey(plugin, "hollow_king");
        MINION = new NamespacedKey(plugin, "wraith");
    }
}
