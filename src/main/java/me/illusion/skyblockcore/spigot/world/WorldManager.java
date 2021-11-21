package me.illusion.skyblockcore.spigot.world;

import me.illusion.skyblockcore.spigot.SkyblockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WorldManager implements Listener {

    private final Map<String, Consumer<World>> saveEvents = new HashMap<>();
    private final Map<String, Consumer<World>> loadEvents = new ConcurrentHashMap<>();
    private final Map<String, Consumer<World>> unloadEvents = new ConcurrentHashMap<>();

    private final Map<String, UUID> loadedIslands = new HashMap<>();

    public WorldManager(SkyblockPlugin main) {
        for (int i = 1; i <= main.getIslandConfig().getWorldCount(); i++) {
            loadedIslands.put("SkyblockWorld" + i, null);

            if (new File(Bukkit.getWorldContainer() + File.separator + "SkyblockWorld" + i).exists())
                continue;

            main.setupWorld("SkyblockWorld" + i);
            Bukkit.unloadWorld("SkyblockWorld" + i, true);
        }

        Bukkit.getPluginManager().registerEvents(this, main);

    }

    public String assignWorld() {
        for (Map.Entry<String, UUID> entry : loadedIslands.entrySet())
            if (entry.getValue() == null)
                return entry.getKey();

        return null;
    }

    public void unregister(String world) {
        loadedIslands.put(world, null);
        //Bukkit.unloadWorld(world, false);
    }

    public void whenNextSave(Consumer<World> worldConsumer, String worldname) {
        saveEvents.put(worldname, saveEvents.getOrDefault(worldname, (world) -> {
        }).andThen(worldConsumer));
    }

    public void whenNextLoad(Consumer<World> worldConsumer, String worldname) {
        System.out.println("Queued up load for " + worldname);
        loadEvents.put(worldname.toLowerCase(Locale.ROOT), loadEvents.getOrDefault(worldname.toLowerCase(Locale.ROOT), (world) -> {
        }).andThen(worldConsumer));
    }

    public void whenNextUnload(Consumer<World> worldConsumer, String worldname) {
        unloadEvents.put(worldname.toLowerCase(Locale.ROOT), unloadEvents.getOrDefault(worldname.toLowerCase(Locale.ROOT), (world) -> {
        }).andThen(worldConsumer));
    }

    public boolean isSkyblockWorld(String name) {
        return loadedIslands.containsKey(name);
    }

    @EventHandler
    private void onSave(WorldSaveEvent event) {
        World world = event.getWorld();
        String name = world.getName();

        Consumer<World> action = saveEvents.remove(name);

        if (action != null)
            action.accept(world);
    }

    @EventHandler
    private void onLoad(WorldLoadEvent event) {
        System.out.println("AYO THE WORLD " + event.getWorld().getName() + " LOADED");
        World world = event.getWorld();
        String name = world.getName().toLowerCase(Locale.ROOT);

        Consumer<World> action = loadEvents.remove(name);

        System.out.println("Action - " + action);

        if (action != null)
            action.accept(world);
    }

    @EventHandler
    private void onUnload(WorldUnloadEvent event) {
        System.out.println("UNLOADED WORLD " + event.getWorld().getName());
        World world = event.getWorld();
        String name = world.getName().toLowerCase(Locale.ROOT);

        Consumer<World> action = unloadEvents.remove(name);

        if (action != null)
            action.accept(world);
    }
}
