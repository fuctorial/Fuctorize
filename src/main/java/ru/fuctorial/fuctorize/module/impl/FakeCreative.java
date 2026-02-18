 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
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
            this.toggle();  
            return;
        }

         
        this.originalGameType = ReflectionUtils.getCurrentGameType(mc.playerController);

         
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
         
         
        if (this.isEnabled()) {
            this.needsReapply = true;
        }
    }

    @Override
    public void onDisconnect() {
         
        this.originalGameType = null;
        this.playerWasDead = false;
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.playerController == null) {
            playerWasDead = true;
            return;
        }

         
        boolean isPlayerDeadNow = mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0;
        if (playerWasDead && !isPlayerDeadNow) {
            client.scheduleTask(this::setCreativeMode);
        }
        playerWasDead = isPlayerDeadNow;

         
        if (needsReapply) {
             
            this.originalGameType = ReflectionUtils.getCurrentGameType(mc.playerController);
            setCreativeMode();
            this.needsReapply = false;
        }

         
         
        mc.thePlayer.capabilities.allowFlying = true;
        mc.thePlayer.capabilities.disableDamage = true;
        mc.thePlayer.capabilities.isCreativeMode = true;  
    }

     
    private void setCreativeMode() {
        if (mc.thePlayer == null || mc.playerController == null) {
            return;
        }

         
        mc.playerController.setGameType(WorldSettings.GameType.CREATIVE);

         
        mc.thePlayer.capabilities.isCreativeMode = true;
        mc.thePlayer.capabilities.allowFlying = true;
        mc.thePlayer.capabilities.disableDamage = true;

         
        mc.thePlayer.sendPlayerAbilities();
    }

     
    private void restoreOriginalGameType() {
        if (mc.thePlayer == null || mc.playerController == null || this.originalGameType == null) {
            this.originalGameType = null;
            return;
        }

         
        mc.playerController.setGameType(this.originalGameType);

         
         
        this.originalGameType.configurePlayerCapabilities(mc.thePlayer.capabilities);

         
        if (!mc.thePlayer.capabilities.allowFlying) {
            mc.thePlayer.capabilities.isFlying = false;
        }

         
        mc.thePlayer.sendPlayerAbilities();

         
        this.originalGameType = null;
    }
}