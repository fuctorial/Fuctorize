package ru.fuctorial.fuctorize.utils;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ScreenContext {

    private static final Minecraft mc = FMLClientHandler.instance().getClient();
    private static final Map<Class<?>, ScreenContextProvider> providers = new ConcurrentHashMap<>();
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private static final Pattern TRANSLATION_KEY_PATTERN = Pattern.compile("^[a-z0-9_.:-]{3,}$");
    private static final int MAX_COLLECTION_SCAN = 4;
    private static final int MAX_HINT_PARTS = 2;
    private static final String[] STRING_GETTER_HINTS = new String[]{
            "getTitle", "getScreenTitle", "getDialogTitle", "getCaption",
            "getNpcName", "getTraderName", "getDisplayName", "getName",
            "getOwnerName", "getPlayerName", "getHeaderText", "getWindowTitle",
            "getMissionName", "getQuestName", "getObjectiveName", "getLabel"
    };
    private static final String[] STRONG_FIELD_KEYWORDS = new String[]{
            "npc", "dialog", "quest", "mission", "title", "name", "screen",
            "window", "trade", "shop", "vendor", "chapter", "objective", "faction"
    };
    private static final String[] WEAK_FIELD_KEYWORDS = new String[]{
            "text", "message", "info", "description", "status", "state", "label", "hint"
    };

    static {
        registerProvider(GuiContainerCreative.class, screen -> {
            try {
                Field tabIndexField = ReflectionHelper.findField(GuiContainerCreative.class, "field_147058_w", "selectedTabIndex");
                int index = (int) tabIndexField.get(null);
                CreativeTabs tab = CreativeTabs.creativeTabArray[index];
                String name = StatCollector.translateToLocal(tab.getTranslatedTabLabel());
                return buildResult("(" + sanitize(name, 30) + ")", "creative_tab:" + index + ":" + name);
            } catch (Exception e) {
                return buildResult("(Creative)", "creative_tab:fallback");
            }
        });

        ScreenContextProvider emptyProvider = screen -> null;
        registerProvider(GuiIngameMenu.class, emptyProvider);

        try {
            Class<?> stalkerGameMenuClass = Class.forName("org.rhino.stalker.core.side.client.gui.menu.GuiGameMenu");
            registerProvider(stalkerGameMenuClass, emptyProvider);
        } catch (ClassNotFoundException e) {
             
        }

        registerProvider(GuiGameOver.class, screen -> buildGameOverContext());
    }

    public static void registerProvider(Class<?> screenClass, ScreenContextProvider provider) {
        providers.put(screenClass, provider);
    }

    public static ScreenContextResult getScreenContext(GuiScreen screen) {
        if (screen == null) {
            return null;
        }

        ScreenContextProvider provider = findProvider(screen.getClass());
        if (provider != null) {
            try {
                ScreenContextResult result = provider.getContext(screen);
                if (hasHumanText(result)) {
                    return result;
                }
            } catch (Throwable ignored) {}
        }

        ScreenContextResult inventory = inventoryQuickContext(screen);
        if (hasHumanText(inventory)) {
            return inventory;
        }

        return reflectionContext(screen);
    }

    private static ScreenContextProvider findProvider(Class<?> clazz) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            ScreenContextProvider p = providers.get(c);
            if (p != null) return p;
        }
        return null;
    }

    private static ScreenContextResult inventoryQuickContext(GuiScreen screen) {
        if (!(screen instanceof GuiContainer)) return null;
        try {
            Container container = ((GuiContainer) screen).inventorySlots;
            if (container != null && !container.inventorySlots.isEmpty()) {
                Slot s0 = (Slot) container.inventorySlots.get(0);
                if (s0 != null && s0.inventory != null && s0.inventory != mc.thePlayer.inventory) {
                    String name = s0.inventory.getInventoryName();
                    String label = "(" + sanitize(name, 30) + ")";
                    return buildResult(label, screen.getClass().getName() + ":" + name);
                }
            }
        } catch (Throwable t) {   }
        return null;
    }



    private static ScreenContextResult reflectionContext(GuiScreen screen) {
        if (screen == null) return null;
        List<Hint> hints = new ArrayList<>();
        collectMethodHints(screen, hints);
        collectFieldHints(screen, hints);
        if (hints.isEmpty()) return null;

        hints.sort((a, b) -> Integer.compare(b.score, a.score));
        List<String> best = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Hint hint : hints) {
            if (best.size() >= MAX_HINT_PARTS) break;
            if (seen.add(hint.value)) {
                best.add(hint.value);
            }
        }
        if (best.isEmpty()) return null;
        String human = "(" + String.join(" | ", best) + ")";
        String hashSeed = screen.getClass().getName() + ":" + String.join("|", best);
        return buildResult(human, hashSeed);
    }

    private static void collectMethodHints(GuiScreen screen, List<Hint> hints) {
        for (String methodName : STRING_GETTER_HINTS) {
            String value = invokeStringGetter(screen, methodName);
            if (value != null) {
                addHint(hints, cleanCandidate(value, 48), 12);
            }
        }
    }

    private static void collectFieldHints(GuiScreen screen, List<Hint> hints) {
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields;
            try {
                fields = c.getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                } catch (Throwable ignored) {
                    continue;
                }
                Object value;
                try {
                    value = field.get(screen);
                } catch (Throwable ignored) {
                    continue;
                }
                if (value == null) continue;
                collectReadableValues(value, 0, field.getName(), hints);
            }
        }
    }

    private static void collectReadableValues(Object value, int depth, String sourceName, List<Hint> hints) {
        if (value == null || depth > 2) return;

        if (value instanceof Iterable<?>) {
            int inspected = 0;
            for (Object element : (Iterable<?>) value) {
                collectReadableValues(element, depth + 1, sourceName, hints);
                if (++inspected >= MAX_COLLECTION_SCAN) break;
            }
            return;
        }

        if (value instanceof Map<?, ?>) {
            int inspected = 0;
            for (Object element : ((Map<?, ?>) value).values()) {
                collectReadableValues(element, depth + 1, sourceName, hints);
                if (++inspected >= MAX_COLLECTION_SCAN) break;
            }
            return;
        }

        if (value.getClass().isArray()) {
            int length = Math.min(Array.getLength(value), MAX_COLLECTION_SCAN);
            for (int i = 0; i < length; i++) {
                collectReadableValues(Array.get(value, i), depth + 1, sourceName, hints);
            }
            return;
        }

        String extracted = extractDirectValue(value);
        if (extracted == null) return;
        int score = baseScoreForValue(value) + scoreFieldName(sourceName) - (depth * 2);
        addHint(hints, extracted, score);
    }

    private static String extractDirectValue(Object value) {
        if (value == null) return null;
        if (value instanceof String) return cleanCandidate((String) value, 48);
        if (value instanceof IChatComponent) return cleanCandidate(((IChatComponent) value).getUnformattedText(), 48);
        if (value instanceof GuiTextField) return cleanCandidate(((GuiTextField) value).getText(), 48);
        if (value instanceof ItemStack) return cleanCandidate(((ItemStack) value).getDisplayName(), 48);
        if (value instanceof EntityLivingBase) return cleanCandidate(((EntityLivingBase) value).getCommandSenderName(), 32);
        if (value instanceof Entity) return cleanCandidate(((Entity) value).getCommandSenderName(), 32);
        if (value instanceof ResourceLocation) return cleanCandidate(((ResourceLocation) value).getResourcePath(), 48);
        if (value instanceof Enum<?>) return cleanCandidate(((Enum<?>) value).name(), 32);

        for (String methodName : STRING_GETTER_HINTS) {
            String invoked = invokeStringGetter(value, methodName);
            if (invoked != null) {
                return cleanCandidate(invoked, 48);
            }
        }

        if (value instanceof IChatComponent[]) {
            for (IChatComponent component : (IChatComponent[]) value) {
                String text = cleanCandidate(component.getUnformattedText(), 48);
                if (text != null) return text;
            }
        }

        String fallback = value.toString();
        if (fallback != null && fallback.contains(" ")) {
            return cleanCandidate(fallback, 48);
        }
        return null;
    }

    private static String invokeStringGetter(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            if (!String.class.isAssignableFrom(method.getReturnType())) return null;
            if (method.getParameterTypes().length != 0) return null;
            method.setAccessible(true);
            Object result = method.invoke(target);
            return result instanceof String ? (String) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void addHint(List<Hint> hints, String value, int score) {
        if (value == null || value.isEmpty() || score <= 0) return;
        if (value.length() <= 2) return;
        hints.add(new Hint(value, score));
    }

    private static int baseScoreForValue(Object value) {
        if (value instanceof IChatComponent) return 8;
        if (value instanceof String) return 7;
        if (value instanceof ItemStack) return 6;
        if (value instanceof Entity) return 6;
        if (value instanceof GuiTextField) return 5;
        if (value instanceof Enum<?>) return 4;
        if (value instanceof Iterable<?> || value instanceof Map<?, ?> || value.getClass().isArray()) return 3;
        return 2;
    }

    private static int scoreFieldName(String name) {
        if (name == null) return 0;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String keyword : STRONG_FIELD_KEYWORDS) {
            if (lower.contains(keyword)) return 6;
        }
        for (String keyword : WEAK_FIELD_KEYWORDS) {
            if (lower.contains(keyword)) return 3;
        }
        return 0;
    }

    private static boolean hasHumanText(ScreenContextResult result) {
        return result != null && result.human != null && !result.human.trim().isEmpty();
    }

    private static ScreenContextResult buildGameOverContext() {
        StringBuilder builder = new StringBuilder();
        String death = fetchDeathMessage();
        if (!death.isEmpty()) {
            builder.append(death);
        }
        String position = fetchPlayerPosition();
        if (!position.isEmpty()) {
            if (builder.length() > 0) builder.append(" | ");
            builder.append(position);
        }
        if (builder.length() == 0) {
            builder.append("Death");
        }
        String human = "(" + builder + ")";
        return buildResult(human, "gui_game_over:" + builder);
    }

    private static String fetchDeathMessage() {
        if (mc.thePlayer == null) return "";
        Object tracker = tryInvokeNoArgs(mc.thePlayer, "getCombatTracker");
        if (tracker == null) {
            tracker = tryInvokeNoArgs(mc.thePlayer, "func_110142_aN");
        }
        if (tracker == null) return "";

        Object component = tryInvokeNoArgs(tracker, "getDeathMessage");
        if (component == null) {
            component = tryInvokeNoArgs(tracker, "func_151521_b");
        }
        if (component instanceof IChatComponent) {
            return cleanCandidate(((IChatComponent) component).getUnformattedText(), 48);
        }
        if (component != null) {
            return cleanCandidate(component.toString(), 48);
        }
        return "";
    }

    private static String fetchPlayerPosition() {
        if (mc.thePlayer == null) return "";
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int y = MathHelper.floor_double(mc.thePlayer.posY);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        return "XYZ " + x + "/" + y + "/" + z;
    }

    private static Object tryInvokeNoArgs(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isEmpty()) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterTypes().length != 0) return null;
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String cleanCandidate(String value, int limit) {
        if (value == null) return null;
        String cleaned = COLOR_CODE_PATTERN.matcher(value).replaceAll("");
        cleaned = cleaned.replaceAll("[\r\n\t]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return null;
        if (TRANSLATION_KEY_PATTERN.matcher(cleaned).matches()) return null;
        if (cleaned.contains("Gui") && !cleaned.contains(" ")) return null;
        String sanitized = sanitize(cleaned, limit);
        return sanitized.isEmpty() ? null : sanitized;
    }

    private static ScreenContextResult buildResult(String human, String hashSeed) {
        if (human == null || human.trim().isEmpty()) return null;
        String hashSource = (hashSeed == null || hashSeed.isEmpty()) ? human : hashSeed;
        return new ScreenContextResult(human, sha256Hex(hashSource));
    }

    private static String sanitize(String s, int len) {
        return s == null ? "" : StringUtils.abbreviate(s.replaceAll("§.", ""), len);
    }

    public static String sha256Hex(String s) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] b = d.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte byt : b) sb.append(String.format("%02x", byt));
            return sb.toString();
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }

    private static final class Hint {
        final String value;
        final int score;

        Hint(String value, int score) {
            this.value = value;
            this.score = score;
        }
    }
}
