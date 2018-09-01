package me.islandscout.hawk.checks.movement;

import me.islandscout.hawk.HawkPlayer;
import me.islandscout.hawk.checks.AsyncMovementCheck;
import me.islandscout.hawk.events.PositionEvent;
import me.islandscout.hawk.utils.*;
import me.islandscout.hawk.utils.blocks.BlockNMS;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.material.Openable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * The Phase check tests collision with blocks between players'
 * moves. Theoretically, this will effectively detect and block
 * any sort of phase, including v-clip. The bounding box of the
 * player is shrunk to reduce false positives, of course.
 */
public class Phase extends AsyncMovementCheck {
    
    //TODO: False positive due to block updating inside player bounding box or if someone teleports into a block. Probably can only fix TP issue.
    //TO DO: If a player sends a move with a large distance and passes through ANY solid block, flag it!
    //TO DO: Logging in while on ground may cause you to get stuck on ground. Make safe locations to fix this.

    //decrease bounding box size if there are false positives.
    //(these values shrink the bounding box)
    private static final double TOP_EPSILON = 0.1;
    private static final double BOTTOM_EPSILON = 0.4;
    private static final double SIDE_EPSILON = 0.1;

    //Maximum distance per move for ignoring whitelisted blocks
    //too big, and you may have a gap for bypasses
    //too small, and you may have false positives
    //optimal threshold < (block depth + 0.6) - (2 * SIDE_EPSILON)
    private static final double HORIZONTAL_DISTANCE_THRESHOLD = Math.pow(0.6, 2);
    private static final double VERTICAL_DISTANCE_THRESHOLD = Math.pow(1, 2);

    private final Map<UUID, Location> legitLoc;

    public Phase() {
        super("phase", true, 0, 10, 0.995, 2000, "&7%player% failed phase. Moved through %block%. VL: %vl%", null);
        legitLoc = new HashMap<>();
    }

    @Override
    protected void check(PositionEvent event) {
        Location locTo = event.getTo();
        Location locFrom = event.getFrom();
        Player p = event.getPlayer();
        HawkPlayer pp = event.getHawkPlayer();
        if(!legitLoc.containsKey(p.getUniqueId()))
            legitLoc.put(p.getUniqueId(), p.getLocation().clone());
        Location setback = legitLoc.get(p.getUniqueId());
        double distanceSquared = locFrom.distanceSquared(locTo);

        //this stops an NPE
        if (distanceSquared == 0)
            return;

        double horizDistanceSquared = Math.pow(locTo.getX() - locFrom.getX(), 2) + Math.pow(locTo.getZ() - locFrom.getZ(), 2);

        Vector moveDirection = new Vector(locTo.getX() - locFrom.getX(), locTo.getY() - locFrom.getY(), locTo.getZ() - locFrom.getZ());

        AABB playerFrom = new AABB(new Vector(locFrom.getX() - (0.3 - SIDE_EPSILON), locFrom.getY() + BOTTOM_EPSILON, locFrom.getZ() - (0.3 - SIDE_EPSILON)), new Vector(locFrom.getX() + (0.3 - SIDE_EPSILON), locFrom.getY() + 1.8 - TOP_EPSILON, locFrom.getZ() + (0.3 - SIDE_EPSILON)));
        AABB playerTo = playerFrom.clone();
        playerTo.translate(moveDirection);

        Vector minBigBox = new Vector(Math.min(playerFrom.getMin().getX(), playerTo.getMin().getX()), Math.min(playerFrom.getMin().getY(), playerTo.getMin().getY()), Math.min(playerFrom.getMin().getZ(), playerTo.getMin().getZ()));
        Vector maxBigBox = new Vector(Math.max(playerFrom.getMax().getX(), playerTo.getMax().getX()), Math.max(playerFrom.getMax().getY(), playerTo.getMax().getY()), Math.max(playerFrom.getMax().getZ(), playerTo.getMax().getZ()));
        AABB bigBox = new AABB(minBigBox, maxBigBox);

        for (int x = bigBox.getMin().getBlockX(); x <= bigBox.getMax().getBlockX(); x++) {
            for (int y = bigBox.getMin().getBlockY(); y <= bigBox.getMax().getBlockY(); y++) {
                for (int z = bigBox.getMin().getBlockZ(); z <= bigBox.getMax().getBlockZ(); z++) {

                    Block bukkitBlock = ServerUtils.getBlockAsync(new Location(locTo.getWorld(), x, y, z));

                    if(bukkitBlock == null)
                        continue;

                    if(!bukkitBlock.getType().isSolid())
                        continue;

                    if(bukkitBlock.getState().getData() instanceof Openable && horizDistanceSquared <= HORIZONTAL_DISTANCE_THRESHOLD && distanceSquared <= VERTICAL_DISTANCE_THRESHOLD) {
                        continue;
                    }

                    BlockNMS block = BlockNMS.getBlockNMS(bukkitBlock);
                    for(AABB test : block.getCollisionBoxes()) {
                        //check if "test" box is even in "bigBox"
                        if (!test.isColliding(bigBox))
                            continue;

                        boolean xCollide = collides2d(test.getMin().getZ(), test.getMax().getZ(), test.getMin().getY(), test.getMax().getY(), playerFrom.getMin().getZ(), playerFrom.getMax().getZ(), playerFrom.getMin().getY(), playerFrom.getMax().getY(), moveDirection.getZ(), moveDirection.getY());
                        boolean yCollide = collides2d(test.getMin().getX(), test.getMax().getX(), test.getMin().getZ(), test.getMax().getZ(), playerFrom.getMin().getX(), playerFrom.getMax().getX(), playerFrom.getMin().getZ(), playerFrom.getMax().getZ(), moveDirection.getX(), moveDirection.getZ());
                        boolean zCollide = collides2d(test.getMin().getX(), test.getMax().getX(), test.getMin().getY(), test.getMax().getY(), playerFrom.getMin().getX(), playerFrom.getMax().getX(), playerFrom.getMin().getY(), playerFrom.getMax().getY(), moveDirection.getX(), moveDirection.getY());
                        if (xCollide && yCollide && zCollide) {
                            punish(pp, new Placeholder("block", bukkitBlock.getType()));
                            tryRubberband(event, setback);
                            return;
                        }
                    }
                }
            }
        }

        if (!AdjacentBlocks.blockAdjacentIsSolid(p.getLocation()) && !AdjacentBlocks.blockAdjacentIsSolid(p.getLocation().clone().add(0, 1, 0))) {
            legitLoc.put(p.getUniqueId(), p.getLocation().clone());
        }
        reward(pp);
    }

    //2d collision test. check if hexagon collides with rectangle
    private boolean collides2d(double testMinX, double testMaxX, double testMinY, double testMaxY, double otherMinX, double otherMaxX, double otherMinY, double otherMaxY, double otherExtrudeX, double otherExtrudeY) {
        if(otherExtrudeX == 0)
            return true; //prevent division by 0
        double slope = otherExtrudeY / otherExtrudeX;
        double height;
        double height2;
        Coordinate2D lowerPoint;
        Coordinate2D upperPoint;
        if(otherExtrudeX > 0) { //extruding to the right
            height = -(slope * (otherExtrudeY > 0 ? otherMaxX:otherMinX)) + otherMinY;
            height2 = -(slope * (otherExtrudeY > 0 ? otherMinX:otherMaxX)) + otherMaxY;
            lowerPoint = new Coordinate2D((otherExtrudeY > 0 ? testMaxX:testMinX), testMinY);
            upperPoint = new Coordinate2D((otherExtrudeY > 0 ? testMinX:testMaxX), testMaxY);
        }
        else { //extruding to the left
            height = -(slope * (otherExtrudeY <= 0 ? otherMaxX:otherMinX)) + otherMinY;
            height2 = -(slope * (otherExtrudeY <= 0 ? otherMinX:otherMaxX)) + otherMaxY;
            lowerPoint = new Coordinate2D((otherExtrudeY <= 0 ? testMaxX:testMinX), testMinY);
            upperPoint = new Coordinate2D((otherExtrudeY <= 0 ? testMinX:testMaxX), testMaxY);
        }
        Line lowerLine = new Line(height, slope);
        Line upperLine = new Line(height2, slope);
        return (lowerPoint.getY() <= upperLine.getYatX(lowerPoint.getX()) && upperPoint.getY() >= lowerLine.getYatX(upperPoint.getX()));
    }

    @Override
    public void removeData(Player p) {
        legitLoc.remove(p.getUniqueId());
    }
}
