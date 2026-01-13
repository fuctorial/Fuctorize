// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\FakeCreative.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import ru.fuctorial.fuctorize.utils.ReflectionUtils;
import net.minecraft.world.WorldSettings;
import org.lwjgl.input.Keyboard;

public class FakeCreative extends Module {

    private WorldSettings.GameType originalGameType;
    private boolean needsReapply = false;
    private boolean playerWasDead = false;

    public FakeCreative(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("fakecreative", Lang.get("module.fakecreative.name"), Category.PLAYER, ActivationType.TOGGLE);
        addSetting(new BindSetting(Lang.get("module.fakecreative.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.fakecreative.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.playerController == null) {
            this.toggle(); // Cannot enable if not in a world
            return;
        }

        // Store the original game type once when the module is enabled
        this.originalGameType = ReflectionUtils.getCurrentGameType(mc.playerController);

        // Apply creative mode settings
        setCreativeMode();
    }

    @Override
    public void onDisable() {
        restoreOriginalGameType();
        this.needsReapply = false;
        this.playerWasDead = false;
    }

    @Override
    public void onConnect() {
        // When connecting to a new server, the original game mode might be different.
        // We need to re-apply our fake creative mode if the module is enabled.
        if (this.isEnabled()) {
            this.needsReapply = true;
        }
    }

    @Override
    public void onDisconnect() {
        // The saved game type is no longer valid when disconnected from a world.
        this.originalGameType = null;
        this.playerWasDead = false;
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.playerController == null) {
            playerWasDead = true;
            return;
        }

        // Re-apply creative mode after respawning
        boolean isPlayerDeadNow = mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0;
        if (playerWasDead && !isPlayerDeadNow) {
            client.scheduleTask(this::setCreativeMode);
        }
        playerWasDead = isPlayerDeadNow;

        // Re-apply creative mode after connecting to a server
        if (needsReapply) {
            // The original game type for the new world must be saved again before switching.
            this.originalGameType = ReflectionUtils.getCurrentGameType(mc.playerController);
            setCreativeMode();
            this.needsReapply = false;
        }

        // THE MAIN FIX: Persistently enforce creative capabilities every tick.
        // This overrides any packets from the server that try to disable them.
        mc.thePlayer.capabilities.allowFlying = true;
        mc.thePlayer.capabilities.disableDamage = true;
        mc.thePlayer.capabilities.isCreativeMode = true; // Also enforce creative GUI
    }

    /**
     * Sets the client's game type to Creative and syncs capabilities with the server.
     * Does not store the original game type.
     */
    private void setCreativeMode() {
        if (mc.thePlayer == null || mc.playerController == null) {
            return;
        }

        // This call is responsible for the creative inventory GUI.
        mc.playerController.setGameType(WorldSettings.GameType.CREATIVE);

        // This method also sets capabilities, but we enforce them again for reliability.
        mc.thePlayer.capabilities.isCreativeMode = true;
        mc.thePlayer.capabilities.allowFlying = true;
        mc.thePlayer.capabilities.disableDamage = true;

        // Inform the server about our "new" capabilities.
        mc.thePlayer.sendPlayerAbilities();
    }

    /**
     * Restores the original game type that was saved when the module was enabled.
     */
    private void restoreOriginalGameType() {
        if (mc.thePlayer == null || mc.playerController == null || this.originalGameType == null) {
            this.originalGameType = null;
            return;
        }

        // Restore the original game type in the controller.
        mc.playerController.setGameType(this.originalGameType);

        // This is a crucial step to correctly reset all capability flags (like allowFlying, disableDamage etc.)
        // to match the original game mode.
        this.originalGameType.configurePlayerCapabilities(mc.thePlayer.capabilities);

        // If we were flying, and flying is no longer allowed, we must stop flying to prevent falling.
        if (!mc.thePlayer.capabilities.allowFlying) {
            mc.thePlayer.capabilities.isFlying = false;
        }

        // Sync the restored capabilities with the server.
        mc.thePlayer.sendPlayerAbilities();

        // Clear the saved state after restoring it.
        this.originalGameType = null;
    }
}