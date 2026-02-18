 
package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.client.renderer.entity.RenderManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.module.settings.TextSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import net.minecraft.entity.player.EntityPlayer;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import ru.fuctorial.fuctorize.utils.RotationUtils;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Stalker extends Module {

    private TextSetting targetNickname;
    private BooleanSetting followClosestPlayer;
    private SliderSetting minFollowDistance;
    private SliderSetting maxFollowDistance;
    private SliderSetting moveSpeed;
    private BooleanSetting renderPath;
    private BooleanSetting mimicJumpsAndSwimming;

    private EntityPlayer target;
    private final Queue<PathPoint> path = new ConcurrentLinkedQueue<>();
    private PathPoint currentMoveTarget = null;
    private static final int MAX_PATH_POINTS = 2000;
    private static final long PATH_BUFFER_MS = 100;
    private Module jesusModule;
    private double lastTargetMotionY = 0;
    private boolean wasTargetOnGround = false;

    public Stalker(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("stalker", Lang.get("module.stalker.name"), Category.FUN);

        followClosestPlayer = new BooleanSetting(Lang.get("module.stalker.setting.follow_closest"), false);
        targetNickname = new TextSetting(Lang.get("module.stalker.setting.target_nickname"), "");
        minFollowDistance = new SliderSetting(Lang.get("module.stalker.setting.min_distance"), 2.0, 0.5, 10.0, 0.5);
        maxFollowDistance = new SliderSetting(Lang.get("module.stalker.setting.max_distance"), 20.0, 5.0, 100.0, 1.0);
        moveSpeed = new SliderSetting(Lang.get("module.stalker.setting.move_speed"), 0.3, 0.1, 2.0, 0.05);
        renderPath = new BooleanSetting(Lang.get("module.stalker.setting.render_path"), true);
        mimicJumpsAndSwimming = new BooleanSetting(Lang.get("module.stalker.setting.mimic_actions"), true);

        addSetting(followClosestPlayer);
        addSetting(targetNickname);
        addSetting(minFollowDistance);
        addSetting(maxFollowDistance);
        addSetting(moveSpeed);
        addSetting(renderPath);
        addSetting(mimicJumpsAndSwimming);
        addSetting(new BindSetting(Lang.get("module.stalker.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.stalker.desc");
    }

    @Override
    public void onEnable() {
        resetState();
        if (this.jesusModule == null) {
            this.jesusModule = client.moduleManager.getModuleByKey("jesus");
        }
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            resetState();
            return;
        }

        if (mc.thePlayer.isDead) {
            resetState();
            return;
        }

        updateTarget();

        if (this.target != null) {
            recordPathPoint();
            processPathQueue();
            handleSmoothMovement();
        } else {
            stopMovement();
        }
    }

    private void resetState() {
        this.target = null;
        this.path.clear();
        this.currentMoveTarget = null;
        this.lastTargetMotionY = 0;
        this.wasTargetOnGround = false;
        if (mc.thePlayer != null) {
            stopMovement();
        }
    }

    private void updateTarget() {
        EntityPlayer newTarget;
        if (followClosestPlayer.enabled) {
            newTarget = findClosestPlayer();
        } else {
            newTarget = targetNickname.text.isEmpty() ? null : mc.theWorld.getPlayerEntityByName(targetNickname.text);
        }

        if (this.target != newTarget) {
            this.path.clear();
            this.currentMoveTarget = null;
            this.lastTargetMotionY = 0;
            this.wasTargetOnGround = newTarget != null && newTarget.onGround;
        }

        this.target = newTarget;

        if (this.target != null && mc.thePlayer.getDistanceToEntity(this.target) > maxFollowDistance.value) {
            this.target = null;
            this.path.clear();
            this.currentMoveTarget = null;
        }
    }

    private EntityPlayer findClosestPlayer() {
        if (mc.theWorld == null || mc.theWorld.playerEntities == null) {
            return null;
        }
        return (EntityPlayer) mc.theWorld.playerEntities.stream()
                .filter(p -> p instanceof EntityPlayer && p != mc.thePlayer && !((EntityPlayer)p).isDead && ((EntityPlayer)p).getHealth() > 0)
                .min(Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity((EntityPlayer)p)))
                .orElse(null);
    }

    private void recordPathPoint() {
        if (this.target == null) return;

        boolean didJump = false;
        if (wasTargetOnGround && !target.onGround && target.motionY > 0.3) {
            didJump = true;
        }

        path.add(new PathPoint(target.posX, target.posY, target.posZ, didJump, target.isInWater()));

        this.lastTargetMotionY = target.motionY;
        this.wasTargetOnGround = target.onGround;

        if (path.size() > MAX_PATH_POINTS) {
            path.poll();
        }
    }

    private void processPathQueue() {
        PathPoint pointToSetAsTarget = null;
        Iterator<PathPoint> iterator = path.iterator();

        while (iterator.hasNext()) {
            PathPoint p = iterator.next();
            if (System.currentTimeMillis() >= p.timestamp + PATH_BUFFER_MS) {
                pointToSetAsTarget = p;
                iterator.remove();
            } else {
                break;
            }
        }
        if (pointToSetAsTarget != null) {
            this.currentMoveTarget = pointToSetAsTarget;
        }
    }

    private void handleSmoothMovement() {
        if (currentMoveTarget == null || target == null) {
            stopMovement();
            return;
        }

        if (mc.thePlayer.getDistanceToEntity(this.target) <= minFollowDistance.value) {
            stopMovement();
            RotationUtils.faceEntity(this.target, 180f);
            return;
        }

        double targetX = currentMoveTarget.x;
        double targetY = currentMoveTarget.y;
        double targetZ = currentMoveTarget.z;

        double distanceToPoint = mc.thePlayer.getDistance(targetX, targetY, targetZ);
        if (distanceToPoint < 0.5) {
            currentMoveTarget = null;
            stopMovement();
            return;
        }

        double dx = targetX - mc.thePlayer.posX;
        double dy = targetY - mc.thePlayer.posY;
        double dz = targetZ - mc.thePlayer.posZ;

        double horizontalMagnitude = Math.sqrt(dx * dx + dz * dz);
        double speed = moveSpeed.value;

        if (horizontalMagnitude > 0.1) {
            mc.thePlayer.motionX = (dx / horizontalMagnitude) * speed;
            mc.thePlayer.motionZ = (dz / horizontalMagnitude) * speed;
        } else {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }

        if (mimicJumpsAndSwimming.enabled) {
            boolean isJesusActive = jesusModule != null && jesusModule.isEnabled();

            if (currentMoveTarget.isInWater && !isJesusActive) {
                mc.thePlayer.motionY = (dy / Math.sqrt(dx*dx + dy*dy + dz*dz)) * speed;
            } else if (currentMoveTarget.didJump && mc.thePlayer.onGround) {
                mc.thePlayer.jump();
                currentMoveTarget.didJump = false;
            }
        }

        RotationUtils.faceEntity(this.target, 180f);
    }

    private void stopMovement() {
        if (mc.thePlayer != null) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }
    }

    @Override
    public void onRender3D(RenderWorldLastEvent event) {
        if (!renderPath.enabled || path.isEmpty() || mc.thePlayer == null) {
            return;
        }

        RenderUtils.begin3DRendering();
        GL11.glLineWidth(2.0f);
        GL11.glBegin(GL11.GL_LINE_STRIP);

        for (PathPoint p : path) {
            if (p.didJump) {
                RenderUtils.setColor(0xFFFF55, 0.9f);
            } else {
                RenderUtils.setColor(0xFFFFFF, 0.7f);
            }
            double renderX = p.x - RenderManager.renderPosX;
            double renderY = p.y - RenderManager.renderPosY;
            double renderZ = p.z - RenderManager.renderPosZ;
            GL11.glVertex3d(renderX, renderY, renderZ);
        }
        GL11.glEnd();

        RenderUtils.end3DRendering();
    }

    private static class PathPoint {
        final double x, y, z;
        final long timestamp;
        boolean didJump;
        final boolean isInWater;

        PathPoint(double x, double y, double z, boolean didJump, boolean isInWater) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = System.currentTimeMillis();
            this.didJump = didJump;
            this.isInWater = isInWater;
        }
    }
}
