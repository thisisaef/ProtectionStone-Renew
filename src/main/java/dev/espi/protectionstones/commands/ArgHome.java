/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.espi.protectionstones.commands;

import dev.espi.protectionstones.*;
import dev.espi.protectionstones.utils.ChatUtil;
import dev.espi.protectionstones.utils.MiscUtil;
import dev.espi.protectionstones.utils.TextGUI;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

public class ArgHome implements PSCommandArg {

    private static HashMap<UUID, List<String>> tabCache = new HashMap<>();

    @Override
    public List<String> getNames() {
        return Collections.singletonList("home");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Arrays.asList("protectionstones.home");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        HashMap<String, Boolean> h = new HashMap<>();
        h.put("-p", true);
        return h;
    }

    // tab completion
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player p)) return null;
        PSPlayer psp = PSPlayer.fromPlayer(p);

        if (args.length == 2) {

            // add to cache if not already
            if (tabCache.get(p.getUniqueId()) == null) {
                List<PSRegion> regions = psp.getHomes(p.getWorld());
                List<String> regionNames = new ArrayList<>();
                for (PSRegion r : regions) {
                    if (r.getName() != null) {
                        regionNames.add(r.getName());
                    } else {
                        regionNames.add(r.getId());
                    }
                }
                // cache home regions
                tabCache.put(p.getUniqueId(), regionNames);

                Bukkit.getScheduler().runTaskLater(ProtectionStones.getInstance(), () -> {
                    tabCache.remove(p.getUniqueId());
                }, 200); // remove cache after 10 seconds
            }

            return StringUtil.copyPartialMatches(args[1], tabCache.get(p.getUniqueId()), new ArrayList<>());
        }
        return null;
    }

    private static final int GUI_SIZE = 17;

    private void openHomeGUI(PSPlayer psp, List<PSRegion> homes, int page) {
        List<TextComponent> entries = new ArrayList<>();
        for (PSRegion r : homes) {
            String msg;
            if (r.getName() == null) {
                msg = ChatColor.GRAY + "> " + ChatColor.AQUA + r.getId();
            } else {
                msg = ChatColor.GRAY + "> " + ChatColor.AQUA + r.getName() + " (" + r.getId() + ")";
            }
            TextComponent tc = new TextComponent(msg);
            tc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(PSL.HOME_CLICK_TO_TP.msg()).create()));
            if (r.getName() == null) {
                tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ProtectionStones.getInstance().getConfigOptions().base_command + " home " + r.getId()));
            } else {
                tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ProtectionStones.getInstance().getConfigOptions().base_command + " home " + r.getName()));
            }
            entries.add(tc);
        }

        TextGUI.displayGUI(psp.getPlayer(), PSL.HOME_HEADER.msg(), "/" + ProtectionStones.getInstance().getConfigOptions().base_command + " home -p %page%", page, GUI_SIZE, entries, true);

        if (page * GUI_SIZE + GUI_SIZE < entries.size())
            PSL.msg(psp, PSL.HOME_NEXT.msg().replace("%page%", page + 2 + ""));
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        Player p = (Player) s;

        // prelim checks
        if (!p.hasPermission("protectionstones.home"))
            return PSL.msg(p, PSL.NO_PERMISSION_HOME.msg());

        if (args.length != 2 && args.length != 1)
            return PSL.msg(p, PSL.HOME_HELP.msg());

        Bukkit.getScheduler().runTaskAsynchronously(ProtectionStones.getInstance(), () -> {
            PSPlayer psp = PSPlayer.fromPlayer(p);
            if (args.length == 1) {
                // just "/ps home"
                List<PSRegion> regions = psp.getHomes(p.getWorld());
                if (regions.size() == 1) { // teleport to home if there is only one home
                    ArgTp.teleportPlayer(p, regions.get(0));
                } else { // otherwise, open the GUI
                    openHomeGUI(psp, regions, (flags.get("-p") == null || !MiscUtil.isValidInteger(flags.get("-p")) ? 0 : Integer.parseInt(flags.get("-p")) - 1));
                }
            } else {// /ps home [id]
                // get regions from the query
                String query = args[1];
                List<PSRegion> regions = psp.getHomes(p.getWorld())
                        .stream()
                        .filter(region -> region.getId().equals(query)
                                || (region.getName() != null && region.getName().equals(query)))
                        .collect(Collectors.toList());

                if (regions.isEmpty()) {
                    PSL.msg(s, PSL.REGION_DOES_NOT_EXIST.msg());
                    return;
                }

                // if there is more than one name in the query
                if (regions.size() > 1) {
                    ChatUtil.displayDuplicateRegionAliases(p, regions);
                    return;
                }

                ArgTp.teleportPlayer(p, regions.get(0));
            }
        });

        return true;
    }
}
