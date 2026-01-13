// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\Module.java
package ru.fuctorial.fuctorize.module;

import cpw.mods.fml.client.FMLClientHandler;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.render.IRenderable;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.event.PrePacketReceiveEvent;
import ru.fuctorial.fuctorize.module.settings.Setting;
import ru.fuctorial.fuctorize.utils.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import java.util.ArrayList;
import java.util.List;

public abstract class Module implements IRenderable {

    public enum ActivationType {TOGGLE, SINGLE}

    protected Minecraft mc = FMLClientHandler.instance().getClient();
    protected final FuctorizeClient client;

    private String key;
    private String name;
    private Category category;
    private ActivationType activationType = ActivationType.TOGGLE;
    private boolean isConfigOnly = false;

    private boolean enabled;
    private boolean wasEnabledOnStartup = false;
    private boolean showInHud = true;
    private final List<Setting> settings = new ArrayList<>();
    public boolean extended = false;
    public final AnimationUtils animation;
    public final AnimationUtils hudAnimation;

    public Module(FuctorizeClient client) {
        this.client = client;
        this.animation = new AnimationUtils(200);
        this.hudAnimation = new AnimationUtils(250, AnimationUtils.Easing.EASE_OUT_QUAD);
        init();
    }

    public abstract void init();
    public abstract String getDescription();

    protected void setMetadata(String key, String name, Category category) {
        this.key = key;
        this.name = name;
        this.category = category;
    }

    protected void setMetadata(String key, String name, Category category, ActivationType activationType) {
        this.key = key;
        this.name = name;
        this.category = category;
        this.activationType = activationType;
    }

    protected void setConfigOnly(boolean configOnly) {
        this.isConfigOnly = configOnly;
    }

    public void setState(boolean enabled) {
        if (this.isConfigOnly) return;
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        this.hudAnimation.setDirection(enabled);
        if (this.enabled) {
            onEnable();
            client.renderManager.register(this);
        } else {
            onDisable();
            client.renderManager.unregister(this);
        }
    }

    public void toggle() {
        if (this.isConfigOnly) return;
        setState(!this.enabled);
    }

    public void setEnabledFromConfig(boolean enabled) {
        if (this.isConfigOnly) {
            this.enabled = true;
            this.wasEnabledOnStartup = true;
            return;
        }
        this.enabled = enabled;
        this.wasEnabledOnStartup = enabled;
        this.hudAnimation.setDirection(enabled);
    }

    public boolean wasEnabledOnStartup() {
        return this.wasEnabledOnStartup;
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onUpdate() {}
    public void onConnect() {}
    public void onDisconnect() {}

    @Override
    public void onRender3D(RenderWorldLastEvent event) {}
    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {}

    // Optional: modules can render at Post/ALL stage
    public void onRender2DPost(RenderGameOverlayEvent.Post event) {}

    /**
     * Called BEFORE an event is posted. Allows a module to cancel the packet directly.
     * @param packet The packet being sent.
     * @return true to cancel the packet, false to allow it.
     */
    public boolean onPacketSendPre(Packet packet) { return false; }

    public void onPacketSend(PacketEvent.Send event) {}
    public void onPacketReceive(PacketEvent.Receive event) {}

    public String getKey() { return key; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public Category getCategory() { return category; }
    public ActivationType getActivationType() { return activationType; }
    public boolean isConfigOnly() { return isConfigOnly; }
    public List<Setting> getSettings() { return settings; }
    public boolean isShownInHud() { return this.showInHud; }
    public void onPrePacketReceive(PrePacketReceiveEvent event) {}

    protected void setShowInHud(boolean visible) {
        this.showInHud = visible;
    }

    public void addSetting(Setting setting) {
        setting.setParent(this);
        settings.add(setting);
    }
}
