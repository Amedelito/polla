package com.ytcustom;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /ytboss, /ytitems, /ytcoins y /ytcustom. */
public final class Commands implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<dark_gray>[<gradient:#8a2be2:#00e5ff>YTCustom</gradient><dark_gray>] <gray>";

    private final YTCustom plugin;
    private final Map<String, String> aliases = new HashMap<>();

    public Commands(YTCustom plugin) {
        this.plugin = plugin;
        aliases.put("coin", ItemManager.COIN);
        aliases.put("moneda", ItemManager.COIN);
        aliases.put("pluma", ItemManager.PLUMA);
        aliases.put("heart", ItemManager.HEART);
        aliases.put("corazon", ItemManager.HEART);
        aliases.put("blade", ItemManager.BLADE);
        aliases.put("filo", ItemManager.BLADE);
        aliases.put("pickaxe", ItemManager.PICKAXE);
        aliases.put("pico", ItemManager.PICKAXE);
        aliases.put("ingot", ItemManager.INGOT);
        aliases.put("lingote", ItemManager.INGOT);
        aliases.put("ore", ItemManager.ORE);
        aliases.put("mena", ItemManager.ORE);
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage(MM.deserialize(PREFIX + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "ytboss": return boss(sender, args);
            case "ytitems": return items(sender, args);
            case "ytcoins": return coins(sender, args);
            case "ytcustom": return admin(sender, args);
            default: return false;
        }
    }

    private boolean boss(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ytcustom.boss")) { msg(sender, "<red>No tienes permiso."); return true; }
        if (args.length >= 1 && args[0].equalsIgnoreCase("remove")) {
            int n = plugin.bosses().removeAllKings();
            msg(sender, "Jefes eliminados: <white>" + n);
            return true;
        }
        if (!(sender instanceof Player p)) { msg(sender, "<red>Solo un jugador puede invocar al jefe (usa la posición del jugador)."); return true; }
        HollowKing k = plugin.bosses().spawnKing(p.getLocation());
        if (k == null) {
            msg(sender, "<red>No se pudo invocar al jefe (límite de jefes activos alcanzado o error: revisa la consola).");
        }
        return true;
    }

    private boolean items(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ytcustom.items")) { msg(sender, "<red>No tienes permiso."); return true; }
        if (!(sender instanceof Player p)) { msg(sender, "<red>Solo para jugadores."); return true; }
        if (args.length == 0) {
            msg(sender, "Uso: <white>/ytitems <coin|pluma|heart|blade|pickaxe|ingot|ore|all> [cantidad]");
            return true;
        }
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                msg(sender, "<red>Cantidad inválida.");
                return true;
            }
        }
        List<String> ids = new ArrayList<>();
        String a = args[0].toLowerCase(Locale.ROOT);
        if (a.equals("all")) {
            ids.addAll(ItemManager.ALL_IDS);
        } else {
            String id = aliases.get(a);
            if (id == null && ItemManager.ALL_IDS.contains(a)) id = a;
            if (id == null) { msg(sender, "<red>Objeto desconocido: " + a); return true; }
            ids.add(id);
        }
        for (String id : ids) {
            ItemStack it = plugin.items().create(id, a.equals("all") ? 1 : amount);
            if (it == null) continue;
            var left = p.getInventory().addItem(it);
            for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        msg(sender, "Objetos entregados.");
        return true;
    }

    private boolean coins(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ytcustom.coins")) { msg(sender, "<red>No tienes permiso."); return true; }
        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("ytcustom.coins.others")) { msg(sender, "<red>No puedes consultar a otros jugadores."); return true; }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { msg(sender, "<red>Jugador no conectado."); return true; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            msg(sender, "Uso: /ytcoins <jugador>");
            return true;
        }
        int n = plugin.items().countCoins(target);
        msg(sender, (target == sender ? "Llevas" : target.getName() + " lleva") + " <gold>" + n
                + " <gray>Monedas Huecas (inventario + cofre de Ender).");
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ytcustom.admin")) { msg(sender, "<red>No tienes permiso."); return true; }
        if (args.length == 0) {
            msg(sender, "Uso: <white>/ytcustom <reload|pack>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                msg(sender, "Configuración recargada.");
            }
            case "pack" -> {
                if (!(sender instanceof Player p)) { msg(sender, "<red>Solo para jugadores."); return true; }
                if (!plugin.packListener().isConfigured()) {
                    msg(sender, "<red>resource-pack.url no está configurada en config.yml.");
                } else if (plugin.packListener().send(p)) {
                    msg(sender, "Resource pack enviado.");
                } else {
                    msg(sender, "<red>No se pudo enviar (revisa la consola).");
                }
            }
            default -> msg(sender, "Uso: <white>/ytcustom <reload|pack>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> opts = switch (name) {
                case "ytboss" -> List.of("remove");
                case "ytitems" -> {
                    List<String> l = new ArrayList<>(List.of("all"));
                    l.addAll(ItemManager.ALL_IDS);
                    yield l;
                }
                case "ytcustom" -> List.of("reload", "pack");
                case "ytcoins" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
            for (String o : opts) if (o.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(o);
        } else if (args.length == 2 && name.equals("ytitems")) {
            out.addAll(List.of("1", "8", "16", "64"));
        }
        return out;
    }
}
