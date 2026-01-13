package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;
import ru.fuctorial.fuctorize.utils.TimerUtils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C16PacketClientStatus;
import org.lwjgl.input.Keyboard;

public class AntiAFK extends Module {

    private SliderSetting delay;
    private BooleanSetting autoRespawn;

    private final TimerUtils mainTimer = new TimerUtils();
    private final TimerUtils actionTimer = new TimerUtils();
    private net.minecraft.world.World lastWorld = null;

    private enum ActionState {
        IDLE, STARTING, MOVING_FORWARD, MOVING_BACKWARD
    }
    private ActionState currentState = ActionState.IDLE;
    private final int MOVE_DURATION_TICKS = 10;

    public AntiAFK(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("antiafk", Lang.get("module.antiafk.name"), Category.PLAYER);

        delay = new SliderSetting(Lang.get("module.antiafk.setting.delay"), 60.0, 10.0, 300.0, 5.0);
        autoRespawn = new BooleanSetting(Lang.get("module.antiafk.setting.auto_respawn"), true);

        addSetting(delay);
        addSetting(autoRespawn);
        addSetting(new BindSetting(Lang.get("module.antiafk.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.antiafk.desc");
    }

    @Override
    public void onEnable() {
        mainTimer.reset();
        currentState = ActionState.IDLE;
        lastWorld = mc.theWorld;
        resetMovementKeys();
    }

    @Override
    public void onDisable() {
        resetMovementKeys();
        currentState = ActionState.IDLE;
    }

    @Override
    public void onUpdate() {
        if ((mc.isGamePaused() && mc.isSingleplayer()) || mc.thePlayer == null || mc.theWorld == null) {
            if (currentState != ActionState.IDLE) {
                resetMovementKeys();
                currentState = ActionState.IDLE;
            }
            return;
        }

        if (mc.theWorld != lastWorld) {
            mainTimer.reset();
            lastWorld = mc.theWorld;
            if (currentState != ActionState.IDLE) {
                resetMovementKeys();
                currentState = ActionState.IDLE;
            }
        }

        if (autoRespawn.enabled && mc.thePlayer.getHealth() <= 0) {
            if (mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver) {
                NetUtils.sendPacket(new C16PacketClientStatus(C16PacketClientStatus.EnumState.PERFORM_RESPAWN));
            }
        }

        if (currentState == ActionState.IDLE) {
            if (mainTimer.hasReached((long) (delay.value * 1000))) {
                currentState = ActionState.STARTING;
                mainTimer.reset();
            }
        } else {
            updateSimplifiedMovement();
        }
    }

    private void updateSimplifiedMovement() {
        long moveMillis = MOVE_DURATION_TICKS * 50;

        switch (currentState) {
            case STARTING:
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.jump();
                }
                currentState = ActionState.MOVING_FORWARD;
                actionTimer.reset();
                break;

            case MOVING_FORWARD:
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
                if (actionTimer.hasReached(moveMillis)) {
                    resetMovementKeys();
                    currentState = ActionState.MOVING_BACKWARD;
                    actionTimer.reset();
                }
                break;

            case MOVING_BACKWARD:
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), true);
                if (actionTimer.hasReached(moveMillis)) {
                    resetMovementKeys();
                    currentState = ActionState.IDLE;
                }
                break;

            case IDLE:
            default:
                break;
        }
    }

    private void resetMovementKeys() {
        if (mc.gameSettings == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
    }
}