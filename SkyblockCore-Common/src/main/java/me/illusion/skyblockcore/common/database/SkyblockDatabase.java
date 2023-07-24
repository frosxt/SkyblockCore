package me.illusion.skyblockcore.common.database;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface SkyblockDatabase {

    /**
     * Obtains the name of this database
     *
     * @return The name
     */
    String getName();

    /**
     * Enables the database
     *
     * @param properties The properties, such as the host, port, username, password, etc.
     * @return A future
     */
    CompletableFuture<Boolean> enable(Map<String, ?> properties);

    /**
     * Flushes the database, this is called when the server is shutting down
     *
     * @return A future which completes when the database is flushed
     */
    CompletableFuture<Void> flush();


}
