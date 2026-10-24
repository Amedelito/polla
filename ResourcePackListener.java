package com.ytcustom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

/** Envía el resource pack a los jugadores SOLO si url está configurada. */
public final class ResourcePackListener implements Listener {

    private final YTCustom plugin;
    private boolean warnedHash = false;

    public ResourcePackListener(YTCustom plugin) {
        this.plugin = plugin;
    }

    public boolean isConfigured() {
        var cfg = plugin.getConfig();
        String url = cfg.getString("resource-pack.url", "");
        return cfg.getBoolean("resource-pack.enabled", true) && url != null && !url.isBlank();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!isConfigured()) return;
        long delay = Math.max(1, plugin.getConfig().getLong("resource-pack.send-delay-ticks", 20L));
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) send(p);
        }, delay);
    }

    public boolean send(Player p) {
        var cfg = plugin.getConfig();
        String url = cfg.getString("resource-pack.url", "").trim();
        if (url.isEmpty()) return false;
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            plugin.getLogger().warning("resource-pack.url no es una URL http(s) válida: " + url);
            return false;
        }
        String sha1 = cfg.getString("resource-pack.sha1", "").trim().toLowerCase();
        byte[] hash = null;
        if (sha1.matches("[0-9a-f]{40}")) {
            hash = HexFormat.of().parseHex(sha1);
        } else if (!warnedHash) {
            warnedHash = true;
            plugin.getLogger().warning("resource-pack.sha1 vacío o inválido (debe tener 40 caracteres hex). "
                    + "El pack se enviará sin hash: los clientes lo redescargarán cada vez.");
        }
        boolean required = cfg.getBoolean("resource-pack.required", false);
        Component prompt = MiniMessage.miniMessage().deserialize(cfg.getString("resource-pack.prompt", "Resource pack de YTCustom"));
        UUID id = UUID.nameUUIDFromBytes((url + sha1).getBytes(StandardCharsets.UTF_8));
        try {
            p.setResourcePack(id, url, hash, prompt, required);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo enviar el resource pack a " + p.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        PlayerResourcePackStatusEvent.Status s = e.getStatus();
        switch (s) {
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DECLINED, DISCARDED ->
                    plugin.getLogger().info(e.getPlayer().getName() + " resource pack: " + s);
            default -> { }
        }
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
        if (required && (s == PlayerResourcePackStatusEvent.Status.DECLINED
                || s == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || s == PlayerResourcePackStatusEvent.Status.INVALID_URL)) {
            e.getPlayer().kick(MiniMessage.miniMessage().deserialize(
                    "<red>Este servidor requiere el resource pack para jugar."));
        }
    }
}
