package me.illusion.skyblockcore.spigot.island;

import me.illusion.skyblockcore.shared.data.IslandData;
import me.illusion.skyblockcore.shared.data.PlayerData;
import me.illusion.skyblockcore.shared.storage.SerializedFile;
import me.illusion.skyblockcore.shared.utilities.ExceptionLogger;
import me.illusion.skyblockcore.shared.utilities.FileUtils;
import me.illusion.skyblockcore.shared.utilities.Reference;
import me.illusion.skyblockcore.spigot.SkyblockPlugin;
import me.illusion.skyblockcore.spigot.event.IslandUnloadEvent;
import me.illusion.skyblockcore.spigot.file.SetupData;
import me.illusion.skyblockcore.spigot.island.impl.LoadedIsland;
import me.illusion.skyblockcore.spigot.island.impl.RemoteIsland;
import me.illusion.skyblockcore.spigot.utilities.WorldUtils;
import me.illusion.skyblockcore.spigot.utilities.schedulerutil.builders.ScheduleBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/*
    Island loading is hella weird, so I made spaghetti code

    Sync -> Requests island loading ->

    Async -> Checks if the island is already pasted,
    creates files, if there isn't any pasting required,
    return the already pasted island, otherwise ->

    Sync -> Loads the world

    Async -> Pastes the island (Sync -> Unloads the world, Async -> Replaces files)

    Sync -> Makes sure the world is loaded, returning the island



    Caching: Each island has a UUID, and a respective
    temporary folder, the files get pasted into the cache/<island uuid>/ folder
    and then the files are respectively used on their providers (FAWE pastes directly, .mca unloads the world)

    Island cache folders are deleted upon island deletion.
 */
public class IslandManager {

    private final Map<UUID, Island> islands = new HashMap<>();
    private final Map<UUID, CompletableFuture<LoadedIsland>> loadingIslands = new HashMap<>();
    private final Set<LoadedIsland> unloadingIslands = new HashSet<>();

    private final SkyblockPlugin main;

    public IslandManager(SkyblockPlugin main) {
        this.main = main;

        File cache = new File(main.getDataFolder(), "cache");

        FileUtils.delete(cache);
        cache.mkdirs();
    }


    public void register(Island island) {
        islands.put(island.getData().getId(), island);
    }

    void unregister(Island island) {
        islands.remove(island.getData().getId());
    }

    public Optional<Island> getIslandFromId(UUID islandId) {
        return Optional.ofNullable(islands.get(islandId));
    }

    public CompletableFuture<IslandData> getIslandData(UUID playerId) {
        return getPlayerIslandId(playerId).thenApply(islandId -> {
            if (islandId == null)
                return null;

            try {
                return main.getStorageHandler().get(islandId, "ISLAND").thenApply(islandData -> {
                    if (islandData == null)
                        return null;

                    return (IslandData) islandData;
                }).get();
            } catch (InterruptedException | ExecutionException e) {
                ExceptionLogger.log(e);
            }

            return null;
        });
    }

    public CompletableFuture<UUID> getPlayerIslandId(UUID playerId) {
        for (Island island : islands.values())
            if (island.getData().getUsers().contains(playerId))
                return CompletableFuture.completedFuture(island.getData().getId());

        return main.getStorageHandler().get(playerId, "PLAYER").thenApply((playerData) -> {
            if (playerData == null)
                return null;

            PlayerData data = (PlayerData) playerData;

            return data.getIslandId();
        }).exceptionally((ex) -> {
            ExceptionLogger.log(ex);
            return null;
        });
    }

    public Optional<LoadedIsland> getLoadedIslandFromId(UUID islandId) {
        Optional<Island> island = getIslandFromId(islandId);

        if (!island.isPresent())
            return Optional.empty();

        Island island1 = island.get();

        if (!(island1 instanceof LoadedIsland))
            return Optional.empty();

        return Optional.of((LoadedIsland) island1);
    }

    public Collection<UUID> getLoadedIslandIds() {
        return islands.keySet();
    }

    public byte getIslandCount() {
        byte count = 0;

        for (Island island : islands.values()) {
            if (island instanceof LoadedIsland)
                count++;
        }

        return count;
    }

    public byte getMaxCapacity() {
        return main.getSetupData().getServerType() == SetupData.ServerType.ISLAND ? (byte) main.getFiles().getIslandConfig().getWorldCount() : getIslandCount();
    }

    public boolean isMaxCapacity() {
        return islands.size() <= main.getFiles().getIslandConfig().getWorldCount();
    }

    /**
     * Gets the island passed a belonging location
     *
     * @param location - The location to match
     * @return NULL if no match is found, Island object otherwise
     */
    public Island getIslandAt(Location location) {
        for (Island island : islands.values())
            if (island.locationBelongs(location))
                return island;

        return null;
    }

    public Island getIsland(UUID islandId) {
        return islands.get(islandId);
    }

    public Island getPlayerIsland(UUID playerId) {
        for (Island island : islands.values())
            if (island.getData().getUsers().contains(playerId))
                return island;

        return null;
    }

    public boolean shouldRemoveIsland(Island island) {
        List<UUID> users = island.getData().getUsers();
        return shouldRemoveIsland(users);
    }

    private boolean shouldRemoveIsland(List<UUID> users) {
        int onlinePlayers = 0;

        for (UUID user : users) {
            if (Bukkit.getPlayer(user) != null)
                onlinePlayers++;
        }

        return onlinePlayers <= 1;
    }

    public RemoteIsland loadRemoteIsland(IslandData islandData) {
        RemoteIsland island = new RemoteIsland(main, islandData);
        islands.put(islandData.getId(), island);

        return island;
    }

    public CompletableFuture<LoadedIsland> pasteIsland(UUID islandId, UUID ownerId) {
        return load(islandId)
                .thenApply(data -> {
                    System.out.println("Pasting island " + islandId);
                    if (data == null)
                        data = new IslandData(islandId, ownerId);

                    IslandData islandData = (IslandData) data;

                    try {
                        return loadIsland(islandData).get();
                    } catch (InterruptedException | ExecutionException e) {
                        ExceptionLogger.log(e);
                    }

                    return null;
                }).exceptionally(throwable -> {
                    ExceptionLogger.log(throwable);
                    return null;
                });
    }

    private LoadedIsland getLoadedIsland(Island island) {
        if (island instanceof LoadedIsland)
            return (LoadedIsland) island;

        return null;
    }

    public CompletableFuture<LoadedIsland> loadIsland(IslandData data) {
        if (loadingIslands.containsKey(data.getId()))
            return loadingIslands.get(data.getId());

        for (LoadedIsland island : unloadingIslands) {
            if (island.getData().getId().equals(data.getId())) {
                unloadingIslands.remove(island);
                return CompletableFuture.completedFuture(island);
            }
        }

        CompletableFuture<LoadedIsland> future = CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            UUID islandId = data.getId();

            // If any island member is online (island pasted), then we don't need to paste
            boolean paste = shouldRemoveIsland(data.getUsers()); // variable to store pasting

            if (!paste) {
                return getLoadedIsland(islands.get(islandId));
            }

            File folder = new File(main.getDataFolder() + File.separator + "cache" + File.separator + islandId); // Create cache folder

            final Reference<LoadedIsland> islandReference = new Reference<>();

            // Pastes island if required
            SerializedFile[] islandFiles = data.getIslandSchematic(); // Obtains original files

            if (islandFiles == null) {
                // Assigns default if not found
                System.out.println("No schematic found for island " + islandId);
                islandFiles = SerializedFile.loadArray(main.getIslandDependencies().getStartSchematic());
            }

            createFiles(folder, islandFiles) // Creates cache files
                    .thenAccept(schematicFiles -> {
                        data.setIslandSchematic(schematicFiles); // Updates schematic with cache files

                        String world = main.getWorldManager().assignWorld(islandId); // Assigns world

                        islandReference.set(loadIsland(data, world)); // Creates island
                    }).exceptionally(throwable -> {
                        ExceptionLogger.log(throwable);
                        return null;
                    }).join();

            long end = System.currentTimeMillis();

            LoadedIsland result = islandReference.get();

            if (result == null) {
                System.out.println("Island " + islandId + " failed to load");
                return null;
            }

            World islandWorld = Bukkit.getWorld(result.getWorldName());

            System.out.println("After action report");
            System.out.println("----------------------------------------");
            System.out.println("Time taken: " + (end - start) + "ms");
            System.out.println("Island world: " + result.getWorldName());
            System.out.println("Island world loaded: " + (islandWorld != null));
            System.out.println("----------------------------------------");

            // --- ENSURE WORLD IS PROPERLY LOADED ---
            if (islandWorld == null) {
                System.out.println("Loading world");
                islandWorld = WorldUtils.load(main, result.getWorldName()).join();
            }

            result.getCenter().setWorld(islandWorld);

            FileUtils.delete(folder); // Deletes folder
            // ----------------------------------------

            return result;

        }).exceptionally(throwable -> {
            ExceptionLogger.log(throwable);
            return null;
        });

        loadingIslands.put(data.getId(), future);
        return future;
    }

    /**
     * Obtains a serialized object
     *
     * @return deserialized object
     */
    private CompletableFuture<Object> load(UUID uuid) {
        return main.getStorageHandler().get(uuid, "ISLAND");
    }

    /**
     * Pastes an island
     *
     * @param data  - The island data, used in the island object
     * @param world - The world to paste the island on
     * @return island object
     */
    private LoadedIsland loadIslandLoadedWorld(IslandData data, World world) {
        try {
            Location center = world.getSpawnLocation();

            System.out.println(world.getName() + " spawn location: " + center);
            int offset = main.getFiles().getIslandConfig().getOverworldSettings().getMaxSize() >> 1;

            Location one = center.clone().add(-offset, -128, -offset);
            Location two = center.clone().add(offset, 128, offset);

            main.getIslandDependencies().getPastingHandler().paste(data.getIslandSchematic(), center).join();

            return new LoadedIsland(main, one, two, center, data, world.getName());
        } catch (Exception e) {
            ExceptionLogger.log(e);
            return null;
        }
    }

    private LoadedIsland loadIslandUnloadedWorld(IslandData data, String worldName) {
        try {
            Vector centerPoint = main.getFiles().getIslandConfig().getSpawnPoint();

            System.out.println(worldName + " spawn location: " + centerPoint);
            int offset = main.getFiles().getIslandConfig().getOverworldSettings().getMaxSize() >> 1;

            main
                    .getIslandDependencies()
                    .getPastingHandler()
                    .paste(data.getIslandSchematic(), worldName, centerPoint)
                    .thenRun(() ->
                            WorldUtils.load(main, worldName).join()
                    )
                    .join();

            World world = Bukkit.getWorld(worldName);
            Location center = centerPoint.toLocation(world);

            System.out.println(world.getName() + " spawn location: " + center);

            Location one = center.clone().add(-offset, -128, -offset);
            Location two = center.clone().add(offset, 128, offset);

            return new LoadedIsland(main, one, two, center, data, worldName);

        } catch (Exception e) {
            ExceptionLogger.log(e);
            return null;
        }
    }

    private LoadedIsland loadIsland(IslandData data, String worldName) {
        boolean requiresLoad = main.getIslandDependencies().getPastingHandler().requiresLoadedWorld();

        if (requiresLoad) {
            return loadIslandLoadedWorld(data, Bukkit.getWorld(worldName));
        }

        return loadIslandUnloadedWorld(data, worldName);
    }

    /**
     * Creates cache island files
     *
     * @param folder - The folder where to write the files do (default - cache)
     * @param files  - The files to put
     * @return The new renamed files
     */
    // I don't care that files are not created if they already exist, just making sure
    // I also can't get around the rawtypes warning without any other warning, I need an array of completablefutures, and they're generic type classes
    @SuppressWarnings({"ResultOfMethodCallIgnored", "rawtypes"})
    private CompletableFuture<SerializedFile[]> createFiles(File folder, SerializedFile... files) {
        return CompletableFuture.supplyAsync(() -> {
            SerializedFile[] copyArray = new SerializedFile[files.length];

            try {
                folder.getParentFile().mkdirs(); // Create parent folder if it doesn't exist
                folder.mkdir(); // Create folder if it doesn't exist


                CompletableFuture[] futures = new CompletableFuture[files.length]; // Used to manage all the files being written

                for (int index = 0; index < files.length; index++) { // Loops through all the serialized files
                    SerializedFile file = files[index].copy(); // Copies the file, so we don't modify the original

                    int finalI = index; // Java limitation REEEEEEEEEEE

                    futures[index] = file.getFile() // Obtain a future of the file
                            .whenComplete((realFile, throwable) -> { // Which is then used to change internal data
                                try {
                                    if (throwable != null)
                                        ExceptionLogger.log(throwable);


                                    file.setFile(new File(folder, realFile.getName())); // Change the file to the new location
                                    file.save(); // Save the file

                                    copyArray[finalI] = file; // Add the file to the copy array
                                } catch (Exception exception) {
                                    ExceptionLogger.log(exception);
                                }

                            });
                }

                CompletableFuture.allOf(futures).exceptionally((throwable) -> { // Waits for all the files to be written
                    if (throwable != null) // If there was an error
                        ExceptionLogger.log(throwable); // Prints the error

                    return null;
                }).join();


            } catch (Exception exception) {
                ExceptionLogger.log(exception);
            }

            return copyArray; // Returns the copy array
        }).exceptionally(throwable -> {
            ExceptionLogger.log(throwable);
            return null;
        });
    }

    public void deleteIsland(UUID islandId) {
        Island island = getIsland(islandId);

        if (!(island instanceof LoadedIsland))
            return;

        LoadedIsland loadedIsland = (LoadedIsland) island;
        String worldName = loadedIsland.getWorldName();

        Bukkit.getPluginManager().callEvent(new IslandUnloadEvent(island));

        File folder = new File(main.getDataFolder() + File.separator + "cache" + File.separator + islandId); // Create cache folder

        if (shouldRemoveIsland(island)) {
            WorldUtils
                    .unload(main, worldName)
                    .thenRun(() -> {
                        WorldUtils.deleteRegionFolder(main, worldName);
                        main.getIslandManager().unregister(island);

                        new ScheduleBuilder(main) // Intentional delay so we don't corrupt worlds by loading and unloading very fast
                                .in(main.getFiles().getSettings().getReleaseDelay()).ticks()
                                .run(() -> main.getWorldManager().unregister(worldName))
                                .sync()
                                .start();

                    });

        }

        System.out.println("Attempting to delete island files");

        FileUtils.delete(folder); // Delete the folder
    }

    public void unregisterRemoteIsland(UUID islandId) {
        Island island = getIsland(islandId);

        if (!(island instanceof RemoteIsland))
            return;

        islands.remove(islandId);
    }

    public Collection<Island> getAllIslands() {
        return islands.values();
    }


}
