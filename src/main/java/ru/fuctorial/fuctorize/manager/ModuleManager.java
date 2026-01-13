// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\manager\ModuleManager.java
package ru.fuctorial.fuctorize.manager;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.impl.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private final FuctorizeClient client;
    public final List<Module> modules = new ArrayList<>();

    public ModuleManager(FuctorizeClient client) {
        this.client = client;
        initModules();
    }

    private void initModules() {
        // Combat
        modules.add(new KillAura(client));


        // Movement
        modules.add(new Sprint(client));
        modules.add(new AntiKnockback(client));
        modules.add(new NoFall(client));
        modules.add(new TimerModule(client));
        modules.add(new LookTP(client));
        modules.add(new Jesus(client));
        modules.add(new NoWeb(client));
        modules.add(new BHop(client));
        modules.add(new TeleportSpeed(client));
        modules.add(new Fly(client));
        modules.add(new PathNavigator(client));
        modules.add(new AutoSpace(client));

        modules.add(new MoneyRevealer(client));

        // Render
        modules.add(new Colors(client)); // Added the central Colors module
        modules.add(new Fullbright(client));
        modules.add(new Tracers(client));
        modules.add(new ESP(client));
        modules.add(new DamagePopups(client));
        modules.add(new ArtifactESP(client));
        modules.add(new AnomalyESP(client));
        modules.add(new BarrierESP(client));
        modules.add(new NoRender(client));
        modules.add(new EntityCounter(client));
        // Player
        modules.add(new AntiAFK(client));
        modules.add(new FreeCam(client));
        modules.add(new ArtifactStealer(client));
        modules.add(new FakeCreative(client));
        modules.add(new Suicide(client));
        modules.add(new MassTrade(client));
        modules.add(new Dropper(client));

        // Exploit
        modules.add(new CoordTP(client));
        modules.add(new NBTEditor(client));
        modules.add(new Blink(client));
        modules.add(new ExcaliburBypass(client));
        modules.add(new Phase(client));
        modules.add(new ScriptManager(client));
        modules.add(new SmartSpammer(client));
        modules.add(new PacketSpammer(client));
        modules.add(new RaceTest(client));
        modules.add(new PacketBlocker(client));
        modules.add(new PacketSender(client));
        modules.add(new SoundLuxCrash(client));
        // Removed TheGunsDupe, AntiSpamKick, StalkerImmunity

        // Misc
        modules.add(new AdvancedTooltip(client));
        modules.add(new CheckVanish(client));
        modules.add(new Panic(client));
        modules.add(new PacketSniffer(client));
        modules.add(new ScreenBypass(client));
        modules.add(new SmartMovingConfigModule(client));
        modules.add(new WorldSelector(client));
        modules.add(new ScreenHistory(client));
        modules.add(new FavoriteScreen(client));
        modules.add(new GuiInspector(client));
        modules.add(new NbtViewer(client));

        // Fun
        modules.add(new Spinner(client));
        modules.add(new Stalker(client));
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModulesInCategory(Category category) {
        return modules.stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * Находит модуль по его нелокализованному ключу.
     * @param key Ключ модуля (например, "killaura").
     * @return Найденный модуль или null.
     */
    public Module getModuleByKey(String key) {
        for (Module m : modules) {
            if (m.getKey().equalsIgnoreCase(key)) {
                return m;
            }
        }
        return null;
    }
}
