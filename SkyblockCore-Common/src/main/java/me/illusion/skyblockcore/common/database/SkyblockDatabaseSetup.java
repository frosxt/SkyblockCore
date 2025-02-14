package me.illusion.skyblockcore.common.database;

import me.illusion.skyblockcore.common.config.ReadOnlyConfigurationSection;

/**
 * Represents a setup for a skyblock database. This class is used to load the database.
 *
 * @param <DataType> The sub-interface of the database
 */
public interface SkyblockDatabaseSetup<DataType extends SkyblockDatabase> {

    /**
     * Gets the properties for a database type, this is used for loading the database
     *
     * @param databaseType The database type
     * @return The properties
     */
    ReadOnlyConfigurationSection getProperties(String databaseType);

    /**
     * Gets the fallback database type
     *
     * @param databaseType The database type
     * @return The fallback database type
     */
    String getFallback(String databaseType);

    /**
     * Gets the preferred database type
     *
     * @return The preferred database type
     */
    String getPreferredDatabase();

    /**
     * Checks whether the setup supports the database, there are some databases that are not supported by some structures
     *
     * @param database The database
     * @return TRUE if the setup supports the database, FALSE otherwise
     */
    boolean isSupported(DataType database);

    /**
     * Obtains the sub-interface of the database
     *
     * @return The class
     */
    Class<DataType> getDatabaseClass();

}
