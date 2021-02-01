package me.illusion.skyblockcore.shared.sql;

public class SQLOperation {

    public static final String CREATE_DATA_TABLE = "CREATE TABLE IF NOT EXISTS PLAYER (serialized_id VARCHAR(36) NOT NULL, object_name varchar(80) default NULL, serialized_object blob, PRIMARY KEY (serialized_id));";
    public static final String CREATE_ISLAND_DATA_TABLE = "CREATE TABLE IF NOT EXISTS ISLAND (serialized_id VARCHAR(36) NOT NULL, object_name varchar(80) default NULL, serialized_object mediumblob, PRIMARY KEY (serialized_id));";

    public static final String SQL_SERIALIZE_OBJECT = "INSERT INTO ?(serialized_id, object_name, serialized_object) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE serialized_id=VALUES(serialized_id), object_name=VALUES(object_name), serialized_object=VALUES(serialized_object)";
    public static final String SQL_DESERIALIZE_OBJECT = "SELECT serialized_object FROM ? WHERE serialized_id = ?";

}
