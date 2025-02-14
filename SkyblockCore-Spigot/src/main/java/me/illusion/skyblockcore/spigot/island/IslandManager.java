package me.illusion.skyblockcore.spigot.island;

import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import me.illusion.cosmos.session.CosmosSession;
import me.illusion.cosmos.template.TemplatedArea;
import me.illusion.cosmos.utilities.time.Time;
import me.illusion.skyblockcore.common.data.IslandData;
import me.illusion.skyblockcore.common.database.fetching.SkyblockFetchingDatabase;
import me.illusion.skyblockcore.common.profile.SkyblockProfileCache;
import me.illusion.skyblockcore.spigot.SkyblockSpigotPlugin;
import me.illusion.skyblockcore.spigot.cosmos.SkyblockCosmosSetup;
import me.illusion.skyblockcore.spigot.event.island.SkyblockIslandLoadEvent;
import me.illusion.skyblockcore.spigot.event.island.SkyblockIslandUnloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Manages islands. The lifecycle of an island is tied to a CosmosSession, which means that if the session is destroyed, the island is destroyed.
 */
public class IslandManager {

    private final Map<UUID, Island> loadedIslands = new ConcurrentHashMap<>();
    private final Set<UUID> unloadingIslands = Sets.newConcurrentHashSet();

    private final Set<CompletableFuture<?>> pending = Sets.newConcurrentHashSet();

    private final SkyblockCosmosSetup cosmosSetup;
    private final SkyblockFetchingDatabase database;
    private final SkyblockProfileCache profileCache;

    public IslandManager(SkyblockSpigotPlugin plugin) {
        this.cosmosSetup = plugin.getCosmosSetup();
        this.database = plugin.getDatabaseRegistry().getChosenDatabase();
        this.profileCache = plugin.getProfileCache();

        new IllegalIslandUnloadCatcher(plugin).register(); // This is a listener that catches when an island is unloaded illegally, through Cosmos directly.
    }

    /**
     * Loads an island from IslandData
     *
     * @param data The data of the island
     * @return The loaded island
     */
    public CompletableFuture<Island> loadIsland(IslandData data) {
        UUID islandId = data.getIslandId();

        Island cached = getLoadedIsland(islandId);

        if (cached != null) { // Idiots
            return CompletableFuture.completedFuture(cached);
        }

        String id = islandId.toString();

        TemplatedArea cachedArea = cosmosSetup.getTemplateCache().get(id);

        if (cachedArea != null) {
            return register(loadFromTemplate(islandId, data, cachedArea));
        }

        return register(cosmosSetup.getIslandContainer().fetchTemplate(id).thenCompose(template -> {
            if (template == null) {
                throw new IllegalStateException("Template not found, database is corrupted!");
            }

            return loadFromTemplate(islandId, data, template);
        }));
    }

    /**
     * Loads an island from a template
     *
     * @return The loaded island
     */
    public CompletableFuture<Island> loadPlayerIsland(UUID profileId, String fallback) {
        Island cached = getProfileIsland(profileId);

        if (cached != null) { // Idiots
            return CompletableFuture.completedFuture(cached);
        }

        return register(database.fetchPlayerIsland(profileId).thenCompose(id -> {
            if (id == null) {
                return createIsland(fallback, profileId);
            }

            return loadIsland(id);
        }));
    }

    /**
     * Loads an island from a template
     *
     * @param template The template
     * @return The loaded island
     */
    public CompletableFuture<Island> createIsland(String template, UUID profileId) {
        TemplatedArea cachedArea = cosmosSetup.getTemplateCache().get(template);

        if (cachedArea == null) {
            throw new IllegalStateException("Template not found, improper setup!");
        }

        UUID islandId = UUID.randomUUID();
        IslandData data = new IslandData(islandId, profileId);

        return register(database.saveIslandData(data).thenCompose(irrelevant -> loadFromTemplate(islandId, data, cachedArea)));
    }

    /**
     * Fetches the island data of a player
     *
     * @param playerId The player's id
     * @return A future
     */
    public CompletableFuture<IslandData> getIslandData(UUID profileId) {
        Island cached = getProfileIsland(profileId);

        if (cached != null) {
            return CompletableFuture.completedFuture(cached.getData());
        }

        return register(database.fetchPlayerIsland(profileId));
    }

    /**
     * Forces an island to unload
     *
     * @param islandId The island's id
     * @param save     Whether or not to save the island
     * @return A future
     */
    public CompletableFuture<Void> forceUnloadIsland(UUID islandId, boolean save) {
        unloadingIslands.add(islandId);
        return register(cosmosSetup.getSessionHolder().unloadSession(islandId, save, true).thenRun(() -> removeInternal(islandId)));
    }

    /**
     * Requests to unload an island after a certain delay. If the island is requested to be loaded before the delay is over, the request is cancelled.
     *
     * @param islandId    The island's id
     * @param save        Whether or not to save the island
     * @param unloadDelay The delay
     * @return A future
     */
    public CompletableFuture<Boolean> requestUnloadIsland(UUID islandId, boolean save, Time unloadDelay) {
        unloadingIslands.add(islandId);
        return register(cosmosSetup.getSessionHolder().unloadAutomaticallyIn(unloadDelay, islandId, save).thenApply(unloaded -> {
            if (unloaded) {
                removeInternal(islandId);
            }

            unloadingIslands.remove(islandId);
            return unloaded;
        }));
    }

    /**
     * Loads an island
     *
     * @param islandId The island's id
     * @return A future
     */
    public CompletableFuture<Island> loadIsland(UUID islandId) {
        return register(database.fetchIslandData(islandId).thenCompose(this::loadIsland));
    }

    /**
     * Disables the island manager, unloading all islands
     *
     * @param save  Whether or not to save the islands
     * @param async Set this to FALSE if you're disabling the plugin, otherwise set it to TRUE. You can't use the scheduler on shutdown
     * @return A future
     */
    public CompletableFuture<Void> disable(boolean save, boolean async) {
        // We could save islands here, but at the moment island data isn't modified at all, so it's not necessary.

        return register(cosmosSetup.getSessionHolder().unloadAll(save, async));
    }

    /**
     * Forces all islands to unload
     *
     * @param islandId The island's id
     * @param data     The island's data
     * @param area     The island's template
     * @return A future
     */
    private CompletableFuture<Island> loadFromTemplate(UUID islandId, IslandData data, TemplatedArea area) {
        return register(cosmosSetup.getSessionHolder().loadOrCreateSession(islandId, area).thenApply(session -> {
            Island island = new Island(data, session);
            loadedIslands.put(islandId, island);

            Bukkit.getPluginManager().callEvent(new SkyblockIslandLoadEvent(island));

            return island;
        }));
    }


    /**
     * Removes an island from the manager internally
     *
     * @param islandId The island's id
     */
    private void removeInternal(UUID islandId) {
        Island island = loadedIslands.remove(islandId);

        if (island != null) {
            Bukkit.getPluginManager().callEvent(new SkyblockIslandUnloadEvent(island));
        }

        unloadingIslands.remove(islandId);
    }

    /**
     * Performs sanity checks when a session is removed
     *
     * @param session The session
     */
    void registerRemoved(CosmosSession session) {
        UUID sessionId = session.getUuid();

        if (!unloadingIslands.contains(sessionId) && loadedIslands.containsKey(sessionId)) {
            throw new IllegalStateException("Session was removed without being unloaded! Possible API misuse!");
        }
    }

    /**
     * Registers a task to the pending list
     *
     * @param task The task
     * @param <T>  The task's type
     * @return The task
     */
    private <T> CompletableFuture<T> register(CompletableFuture<T> task) {
        pending.add(task);
        return task.whenComplete((result, error) -> pending.remove(task));
    }

    public CompletableFuture<Void> flush() {
        return CompletableFuture.allOf(pending.toArray(new CompletableFuture[0]));
    }

    // -------------- REGULAR METHODS -------------- //

    /**
     * Gets an island by its id
     *
     * @param islandId The island's id
     * @return The island
     */
    public Island getLoadedIsland(UUID islandId) {
        return loadedIslands.get(islandId);
    }

    /**
     * Gets an island by its owner's id
     *
     * @param profileId The owner's id
     * @return The island
     */
    public Island getProfileIsland(UUID profileId) {
        for (Island island : loadedIslands.values()) {
            if (island.getData().getOwnerId().equals(profileId)) {
                return island;
            }
        }

        return null;
    }

    /**
     * Gets an island by its owner
     *
     * @param player The owner
     * @return The island
     */
    public Island getPlayerIsland(Player player) {
        return getPlayerIsland(player.getUniqueId());
    }

    public Island getPlayerIsland(UUID playerId) {
        UUID cachedProfileId = profileCache.getCachedProfileId(playerId);

        if (cachedProfileId == null) {
            return null;
        }

        return getProfileIsland(cachedProfileId);
    }


}
