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

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import dev.espi.protectionstones.*;
import dev.espi.protectionstones.utils.ParticlesUtil;
import dev.espi.protectionstones.utils.RegionTraverse;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ArgView implements PSCommandArg {

    private static List<UUID> cooldown = new ArrayList<>();

    @Override
    public List<String> getNames() {
        return Collections.singletonList("view");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Arrays.asList("protectionstones.view");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        return null;
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        Player p = (Player) s;

        PSRegion r = PSRegion.fromLocationGroup(p.getLocation());

        if (!p.hasPermission("protectionstones.view")) {
            PSL.msg(p, PSL.NO_PERMISSION_VIEW.msg());
            return true;
        }
        if (r == null) {
            PSL.msg(p, PSL.NOT_IN_REGION.msg());
            return true;
        }
        if (!p.hasPermission("protectionstones.view.others") && WGUtils.hasNoAccess(r.getWGRegion(), p, WorldGuardPlugin.inst().wrapPlayer(p), true)) {
            PSL.msg(p, PSL.NO_ACCESS.msg());
            return true;
        }
        if (cooldown.contains(p.getUniqueId())) {
            PSL.msg(p, PSL.VIEW_COOLDOWN.msg());
            return true;
        }

        PSL.msg(p, PSL.VIEW_GENERATING.msg());

        // add player to cooldown
        cooldown.add(p.getUniqueId());
        Bukkit.getScheduler().runTaskLaterAsynchronously(ProtectionStones.getInstance(), () -> cooldown.remove(p.getUniqueId()), 20 * ProtectionStones.getInstance().getConfigOptions().psViewCooldown);

        int playerY = p.getLocation().getBlockY(), minY = r.getWGRegion().getMinimumPoint().getBlockY(), maxY = r.getWGRegion().getMaximumPoint().getBlockY();

        // send particles to client

        Bukkit.getScheduler().runTaskAsynchronously(ProtectionStones.getInstance(), () -> {

            AtomicInteger modU = new AtomicInteger(0);

            if (r instanceof PSGroupRegion) {
                PSGroupRegion pr = (PSGroupRegion) r;
                for (PSMergedRegion psmr : pr.getMergedRegions()) {
                    handlePurpleParticle(p, new Location(p.getWorld(), 0.5 + psmr.getProtectBlock().getX(), 1.5 + psmr.getProtectBlock().getY(), 0.5 + psmr.getProtectBlock().getZ()));
                    for (int y = minY; y <= maxY; y += 10) {
                        handlePurpleParticle(p, new Location(p.getWorld(), 0.5 + psmr.getProtectBlock().getX(), 0.5 + y, 0.5 + psmr.getProtectBlock().getZ()));
                    }
                }
            } else {
                handlePurpleParticle(p, new Location(p.getWorld(), 0.5 + r.getProtectBlock().getX(), 1.5 + r.getProtectBlock().getY(), 0.5 + r.getProtectBlock().getZ()));
                for (int y = minY; y <= maxY; y += 10) {
                    handlePurpleParticle(p, new Location(p.getWorld(), 0.5 + r.getProtectBlock().getX(), 0.5 + y, 0.5 + r.getProtectBlock().getZ()));
                }
            }

            RegionTraverse.traverseRegionEdge(new HashSet<>(r.getWGRegion().getPoints()), Collections.singletonList(r.getWGRegion()), tr -> {
                if (tr.isVertex) {
                    handleBlueParticle(p, new Location(p.getWorld(), 0.5+tr.point.getX(), 0.5+playerY, 0.5+tr.point.getZ()));
                    for (int y = minY; y <= maxY; y += 5) {
                        handleBlueParticle(p, new Location(p.getWorld(), 0.5+tr.point.getX(), 0.5+y, 0.5+tr.point.getZ()));
                    }
                } else {
                    if (modU.get() % 2 == 0) {
                        handlePinkParticle(p, new Location(p.getWorld(), 0.5+tr.point.getX(), 0.5+playerY, 0.5+tr.point.getZ()));
                        handlePinkParticle(p, new Location(p.getWorld(), 0.5+tr.point.getX(), 0.5+minY, 0.5+tr.point.getZ()));
                        handlePinkParticle(p, new Location(p.getWorld(), 0.5+tr.point.getX(), 0.5+maxY, 0.5+tr.point.getZ()));
                    }
                    modU.set((modU.get() + 1) % 2);
                }
            });
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return null;
    }

    private static int PARTICLE_VIEW_DISTANCE_LIMIT = 150;

    private static boolean handlePinkParticle(Player p, Location l) {
        if (p.getLocation().distance(l) > PARTICLE_VIEW_DISTANCE_LIMIT || Math.abs(l.getY()-p.getLocation().getY()) > 30) return false;
        ParticlesUtil.persistRedstoneParticle(p, l, new Particle.DustOptions(Color.fromRGB(233, 30, 99), 2), 30);
        return true;
    }

    private static boolean handleBlueParticle(Player p, Location l) {
        if (p.getLocation().distance(l) > PARTICLE_VIEW_DISTANCE_LIMIT || Math.abs(l.getY()-p.getLocation().getY()) > 30) return false;
        ParticlesUtil.persistRedstoneParticle(p, l, new Particle.DustOptions(Color.fromRGB(0, 255, 255), 2), 30);
        return true;
    }

    private static boolean handlePurpleParticle(Player p, Location l) {
        if (p.getLocation().distance(l) > PARTICLE_VIEW_DISTANCE_LIMIT || Math.abs(l.getY()-p.getLocation().getY()) > 30) return false;
        ParticlesUtil.persistRedstoneParticle(p, l, new Particle.DustOptions(Color.fromRGB(255, 0, 255), 10), 30);
        return true;
    }
}
