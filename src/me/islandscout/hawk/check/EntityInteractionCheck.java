/*
 * This file is part of Hawk Anticheat.
 *
 * Hawk Anticheat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hawk Anticheat is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hawk Anticheat.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.islandscout.hawk.check;

import me.islandscout.hawk.event.InteractEntityEvent;

import java.util.List;

public abstract class EntityInteractionCheck extends Check<InteractEntityEvent> {

    protected EntityInteractionCheck(String name, boolean enabled, int cancelThreshold, int flagThreshold, double vlPassMultiplier, long flagCooldown, String flag, List<String> punishCommands) {
        super(name, enabled, cancelThreshold, flagThreshold, vlPassMultiplier, flagCooldown, flag, punishCommands);
        hawk.getCheckManager().getEntityInteractionChecks().add(this);
    }

    protected EntityInteractionCheck(String name, String flag) {
        this(name, true, 0, 5, 0.9, 5000, flag, null);
    }
}