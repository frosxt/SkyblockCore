package me.illusion.skyblockcore.common.database.fetching;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import me.illusion.skyblockcore.common.data.IslandData;
import me.illusion.skyblockcore.common.database.SkyblockDatabase;

/**
 * This interface represents a template for all fetching databases. A fetching database is responsible for fetching the actual data from the database.
 */
public interface SkyblockFetchingDatabase extends SkyblockDatabase {

    /**
     * Fetches the island id of a player
     *
     * @param profileId The profile's id
     * @return The island id
     */
    CompletableFuture<UUID> fetchIslandId(UUID profileId);

    /**
     * Fetches the island data of an island
     *
     * @param islandId The island's id
     * @return The island data
     */
    CompletableFuture<IslandData> fetchIslandData(UUID islandId);

    /**
     * Saves the island data
     *
     * @param data The island data
     * @return A future
     */
    CompletableFuture<Void> saveIslandData(IslandData data);

    /**
     * Deletes the island data
     *
     * @param islandId The island's id
     * @return A future
     */
    CompletableFuture<Void> deleteIslandData(UUID islandId);

    /**
     * Fetches the profile id of a player
     *
     * @param playerId The player's id
     * @return The profile id
     */
    CompletableFuture<UUID> getProfileId(UUID playerId);

    /**
     * Sets the profile id of a player
     *
     * @param playerId  The player's id
     * @param profileId The profile's id
     * @return A future
     */
    CompletableFuture<Void> setProfileId(UUID playerId, UUID profileId);

    /**
     * Deletes the island data of a player
     *
     * @param profileId The player's id
     * @return A future
     */
    default CompletableFuture<Void> deletePlayerIsland(UUID profileId) {
        return compose(fetchIslandId(profileId), this::deleteIslandData);
    }

    /**
     * Fetches the island data of a player
     *
     * @param profileId The player's id
     * @return The island data
     */
    default CompletableFuture<IslandData> fetchPlayerIsland(UUID profileId) {
        return compose(fetchIslandId(profileId), this::fetchIslandData);
    }

    /**
     * Composes a future, returning null if the value is null
     *
     * @param future   The future
     * @param function The function
     * @param <T>      The type of the first future
     * @param <U>      The return type of the second future
     * @return The composed future
     */
    private <T, U> CompletableFuture<U> compose(CompletableFuture<T> future, Function<T, CompletableFuture<U>> function) {
        return future.thenCompose(value -> {
            if (value == null) {
                return CompletableFuture.completedFuture(null);
            }

            return function.apply(value);
        });
    }

    /**
     * Checks if the database is file based, meaning it is not a remote database and is not supported by complex networks
     *
     * @return If the database is file based
     */
    default boolean isFileBased() {
        return false;
    }

}
