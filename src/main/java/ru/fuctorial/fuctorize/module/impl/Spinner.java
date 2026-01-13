// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\Spinner.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Spinner extends Module {

    private SliderSetting spinSpeed;
    private SliderSetting packetRate;
    private BooleanSetting smoothPackets;
    private BooleanSetting verticalSpin;
    private SliderSetting pitchAmplitude;
    private BindSetting bind;

    private double currentSpinAngle = 0.0;
    private double prevRenderAngle = 0.0;
    private long lastTimeMs = 0;
    private double lastSentYaw = 0;
    private double lastSentPitch = 0;
    private long lastPacketTimeMs = 0;
    private long lastForcedSendMs = 0;
    private double pitchPhaseDeg = 0.0;
    private double basePitch = 0.0;
    private boolean modelSaved = false;
    private ModelBiped savedModelRef = null;
    private float savedModelHeadRotateY = 0f;
    private float savedModelBodyRotateY = 0f;
    private float savedModelHeadRotateX = 0f;
    private float savedModelBodyRotateX = 0f;
    private float savedYaw, savedYawHead, savedPrevYaw, savedPrevYawHead;
    private boolean isRenderStateSaved = false;
    private static Field yawField;
    private static Field pitchField;

    public Spinner(FuctorizeClient client) {
        super(client);
        try {
            try {
                yawField = C03PacketPlayer.class.getDeclaredField("field_149476_e");
                yawField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {}
            try {
                pitchField = C03PacketPlayer.class.getDeclaredField("field_149473_f");
                pitchField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {}
            try {
                if (yawField == null) {
                    Field f = C03PacketPlayer.class.getDeclaredField("yaw");
                    f.setAccessible(true);
                    yawField = f;
                }
            } catch (NoSuchFieldException ignored) {}
            try {
                if (pitchField == null) {
                    Field f = C03PacketPlayer.class.getDeclaredField("pitch");
                    f.setAccessible(true);
                    pitchField = f;
                }
            } catch (NoSuchFieldException ignored) {}
            if (yawField == null || pitchField == null) {
                List<Field> floatFields = new ArrayList<>();
                for (Field f : C03PacketPlayer.class.getDeclaredFields()) {
                    if (f.getType() == float.class) {
                        f.setAccessible(true);
                        floatFields.add(f);
                    }
                }
                if (floatFields.size() >= 2) {
                    if (yawField == null) yawField = floatFields.get(0);
                    if (pitchField == null) pitchField = floatFields.get(1);
                }
            }
            if (yawField == null || pitchField == null) {
                List<Field> numeric = new ArrayList<>();
                for (Field f : C03PacketPlayer.class.getDeclaredFields()) {
                    Class<?> t = f.getType();
                    if (t == float.class || t == double.class) {
                        f.setAccessible(true);
                        numeric.add(f);
                    }
                }
                if (numeric.size() >= 2) {
                    Field a = numeric.get(numeric.size() - 2);
                    Field b = numeric.get(numeric.size() - 1);
                    if (yawField == null) yawField = a;
                    if (pitchField == null) pitchField = b;
                }
            }
        } catch (Throwable e) {
            System.err.println("Spinner: reflection init error");
            e.printStackTrace();
        }
    }

    @Override
    public void init() {
        setMetadata("spinner", Lang.get("module.spinner.name"), Category.FUN);

        spinSpeed = new SliderSetting(Lang.get("module.spinner.setting.yaw_speed"), 720.0, 30.0, 3600.0, 1.0);
        packetRate = new SliderSetting(Lang.get("module.spinner.setting.packet_rate"), 20.0, 1.0, 60.0, 1.0);
        smoothPackets = new BooleanSetting(Lang.get("module.spinner.setting.smooth_sending"), true);
        verticalSpin = new BooleanSetting(Lang.get("module.spinner.setting.vertical_spin"), false);
        pitchAmplitude = new SliderSetting(Lang.get("module.spinner.setting.pitch_amplitude"), 20.0, 1.0, 89.0, 1.0);
        bind = new BindSetting(Lang.get("module.spinner.setting.bind"), Keyboard.KEY_NONE);

        addSetting(spinSpeed);
        addSetting(packetRate);
        addSetting(smoothPackets);
        addSetting(verticalSpin);
        addSetting(pitchAmplitude);
        addSetting(bind);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.spinner.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null) { toggle(); return; }

        lastTimeMs = System.currentTimeMillis();
        lastPacketTimeMs = lastTimeMs;
        lastForcedSendMs = lastTimeMs;

        currentSpinAngle = mc.thePlayer.rotationYaw;
        prevRenderAngle = currentSpinAngle;
        lastSentYaw = currentSpinAngle;

        basePitch = mc.thePlayer.rotationPitch;
        lastSentPitch = basePitch;
        pitchPhaseDeg = 0.0;

        client.notificationManager.show(new Notification(Lang.get("notification.spinner.title"), Lang.get("notification.spinner.message.enabled"), Notification.NotificationType.INFO, 1500L));
    }

    @Override
    public void onDisable() {
        restoreRenderIfSaved();
        restoreModelIfSaved();
        client.notificationManager.show(new Notification(Lang.get("notification.spinner.title"), Lang.get("notification.spinner.message.disabled"), Notification.NotificationType.INFO, 1500L));
    }

    @Override
    public void onUpdate() {
        long now = System.currentTimeMillis();
        double deltaTime = (now - lastTimeMs) / 1000.0;
        lastTimeMs = now;

        prevRenderAngle = currentSpinAngle;
        currentSpinAngle = (currentSpinAngle + spinSpeed.value * deltaTime) % 360.0;

        if (smoothPackets.enabled) {
            double deltaSeconds = (now - lastPacketTimeMs) / 1000.0;
            lastPacketTimeMs = now;
            double step = spinSpeed.value * deltaSeconds;
            lastSentYaw = (lastSentYaw + step) % 360.0;
        } else {
            lastSentYaw = currentSpinAngle;
        }

        if (verticalSpin.enabled) {
            pitchPhaseDeg = (pitchPhaseDeg + spinSpeed.value * deltaTime) % 360.0;
            double amp = pitchAmplitude.value;
            double radians = Math.toRadians(pitchPhaseDeg);
            double osc = amp * Math.sin(radians);
            lastSentPitch = clampDeg(basePitch + osc, -89.0, 89.0);
        } else {
            lastSentPitch = basePitch;
        }

        double hz = packetRate != null ? packetRate.value : 20.0;
        long intervalMs = (long) Math.max(1, Math.round(1000.0 / hz));
        if (now - lastForcedSendMs >= intervalMs) {
            lastForcedSendMs = now;
            forceSendLookPacket((float) lastSentYaw, (float) lastSentPitch, mc.thePlayer.onGround);
        }
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {}

    private void forceSendLookPacket(float yaw, float pitch, boolean onGround) {
        try {
            C03PacketPlayer.C05PacketPlayerLook look = new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, onGround);
            if (mc.thePlayer != null && mc.thePlayer.sendQueue != null) {
                mc.thePlayer.sendQueue.addToSendQueue(look);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            S08PacketPlayerPosLook packet = (S08PacketPlayerPosLook) event.getPacket();
            float serverYaw = packet.func_148931_f();
            currentSpinAngle = (float) approachAngle(currentSpinAngle, serverYaw, 30.0);
            lastSentYaw = currentSpinAngle;
            prevRenderAngle = currentSpinAngle;

            try {
                float serverPitch = packet.func_148930_g();
                basePitch = serverPitch;
                lastSentPitch = basePitch;
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onRender3D(RenderWorldLastEvent event) {}

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!isEnabled()) return;
        if (event.entityPlayer == mc.thePlayer && mc.gameSettings.thirdPersonView != 0) {
            ModelBiped model = getModelFromRenderer(event.renderer);
            if (model != null) {
                if (!modelSaved) {
                    modelSaved = true;
                    savedModelRef = model;
                    savedModelHeadRotateY = model.bipedHead.rotateAngleY;
                    savedModelBodyRotateY = model.bipedBody.rotateAngleY;
                    savedModelHeadRotateX = model.bipedHead.rotateAngleX;
                    savedModelBodyRotateX = model.bipedBody.rotateAngleX;
                }

                float partial = 1.0f;
                try {
                    Field pf = event.getClass().getDeclaredField("partialRenderTick");
                    pf.setAccessible(true);
                    partial = pf.getFloat(event);
                } catch (Throwable ignored) {}

                double renderAngle = interpAngle(prevRenderAngle, currentSpinAngle, partial);
                float spinRadY = (float) Math.toRadians(renderAngle);
                float spinRadX = (float) Math.toRadians(lastSentPitch);

                model.bipedHead.rotateAngleY = spinRadY;
                model.bipedBody.rotateAngleY = spinRadY;
                model.bipedHead.rotateAngleX = spinRadX;
                model.bipedBody.rotateAngleX = spinRadX * 0.5f;
            } else {
                if (!isRenderStateSaved) {
                    isRenderStateSaved = true;
                    savedYaw = event.entityPlayer.rotationYaw;
                    savedYawHead = event.entityPlayer.rotationYawHead;
                    savedPrevYaw = event.entityPlayer.prevRotationYaw;
                    savedPrevYawHead = event.entityPlayer.prevRotationYawHead;
                }

                float fakeYaw = (float) currentSpinAngle;
                event.entityPlayer.rotationYaw = fakeYaw;
                event.entityPlayer.rotationYawHead = fakeYaw;
                event.entityPlayer.prevRotationYaw = fakeYaw;
                event.entityPlayer.prevRotationYawHead = fakeYaw;
            }
        }
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!isEnabled()) return;
        if (event.entityPlayer == mc.thePlayer) {
            if (modelSaved) {
                ModelBiped model = getModelFromRenderer(event.renderer);
                if (model != null) {
                    try {
                        model.bipedHead.rotateAngleY = savedModelHeadRotateY;
                        model.bipedBody.rotateAngleY = savedModelBodyRotateY;
                        model.bipedHead.rotateAngleX = savedModelHeadRotateX;
                        model.bipedBody.rotateAngleX = savedModelBodyRotateX;
                    } catch (Throwable ignored) {}
                } else if (savedModelRef != null) {
                    try {
                        savedModelRef.bipedHead.rotateAngleY = savedModelHeadRotateY;
                        savedModelRef.bipedBody.rotateAngleY = savedModelBodyRotateY;
                        savedModelRef.bipedHead.rotateAngleX = savedModelHeadRotateX;
                        savedModelRef.bipedBody.rotateAngleX = savedModelBodyRotateX;
                    } catch (Throwable ignored) {}
                }
                modelSaved = false;
                savedModelRef = null;
            }

            if (isRenderStateSaved) {
                restoreRenderIfSaved();
            }
        }
    }

    private ModelBiped getModelFromRenderer(RenderPlayer renderer) {
        try {
            try {
                java.lang.reflect.Method m = renderer.getClass().getMethod("getMainModel");
                Object main = m.invoke(renderer);
                if (main instanceof ModelBiped) return (ModelBiped) main;
            } catch (Throwable ignored) {}

            try {
                Field f = renderer.getClass().getField("mainModel");
                f.setAccessible(true);
                Object obj = f.get(renderer);
                if (obj instanceof ModelBiped) return (ModelBiped) obj;
            } catch (Throwable ignored) {}

            for (Field f : renderer.getClass().getDeclaredFields()) {
                if (ModelBiped.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object obj = f.get(renderer);
                    if (obj instanceof ModelBiped) return (ModelBiped) obj;
                }
            }

            Class<?> cls = renderer.getClass().getSuperclass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (ModelBiped.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object obj = f.get(renderer);
                        if (obj instanceof ModelBiped) return (ModelBiped) obj;
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private double interpAngle(double prev, double current, double partial) {
        double diff = ((current - prev + 540.0) % 360.0) - 180.0;
        return (prev + diff * partial + 360.0) % 360.0;
    }

    private double approachAngle(double current, double target, double maxDelta) {
        double diff = ((target - current + 540.0) % 360.0) - 180.0;
        if (diff > maxDelta) diff = maxDelta;
        if (diff < -maxDelta) diff = -maxDelta;
        return (current + diff + 360.0) % 360.0;
    }

    private double clampDeg(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private void restoreRenderIfSaved() {
        if (isRenderStateSaved && mc.thePlayer != null) {
            mc.thePlayer.rotationYaw = savedYaw;
            mc.thePlayer.rotationYawHead = savedYawHead;
            mc.thePlayer.prevRotationYaw = savedPrevYaw;
            mc.thePlayer.prevRotationYawHead = savedPrevYawHead;
            isRenderStateSaved = false;
        }
    }

    private void restoreModelIfSaved() {
        if (modelSaved) {
            try {
                if (savedModelRef != null) {
                    savedModelRef.bipedHead.rotateAngleY = savedModelHeadRotateY;
                    savedModelRef.bipedBody.rotateAngleY = savedModelBodyRotateY;
                    savedModelRef.bipedHead.rotateAngleX = savedModelHeadRotateX;
                    savedModelRef.bipedBody.rotateAngleX = savedModelBodyRotateX;
                }
            } catch (Throwable ignored) {}
            modelSaved = false;
            savedModelRef = null;
        }
    }
}
