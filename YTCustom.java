package com.ytcustom;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class YTCustom extends JavaPlugin {

    private ItemManager items;
    private BossManager bosses;
    private ResourcePackListener packListener;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            Keys.init(this);

            items = new ItemManager(this);
            if (!items.init()) {
                getLogger().severe("No se pudo inicializar el sistema de objetos. Desactivando plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            items.registerRecipes();

            bosses = new BossManager(this);
            packListener = new ResourcePackListener(this);

            getServer().getPluginManager().registerEvents(bosses, this);
            getServer().getPluginManager().registerEvents(new PlumaListener(this), this);
            getServer().getPluginManager().registerEvents(new CustomBlockListener(this), this);
            getServer().getPluginManager().registerEvents(new ItemListener(this), this);
            getServer().getPluginManager().registerEvents(packListener, this);
            getServer().getPluginManager().registerEvents(new OreGenerator(this), this);

            Commands commands = new Commands(this);
            for (String name : new String[]{"ytboss", "ytitems", "ytcoins", "ytcustom"}) {
                PluginCommand cmd = getCommand(name);
                if (cmd == null) {
                    getLogger().severe("El comando /" + name + " no está declarado en plugin.yml");
                    continue;
                }
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }

            String url = getConfig().getString("resource-pack.url", "");
            if (getConfig().getBoolean("resource-pack.enabled", true) && (url == null || url.isBlank())) {
                getLogger().warning("resource-pack.url está vacío: el resource pack NO se enviará a los jugadores. "
                        + "Aloja el ZIP por HTTPS y rellena url y sha1 en config.yml (ver README).");
            }
            getLogger().info("YTCustom " + getPluginMeta().getVersion() + " cargado correctamente.");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Error fatal al cargar YTCustom", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bosses != null) bosses.shutdown();
        if (items != null) items.unregisterRecipes();
    }

    public void reload() {
        reloadConfig();
        items.unregisterRecipes();
        items.registerRecipes();
        getLogger().info("Configuración recargada.");
    }

    public ItemManager items() { return items; }
    public BossManager bosses() { return bosses; }
    public ResourcePackListener packListener() { return packListener; }
}
