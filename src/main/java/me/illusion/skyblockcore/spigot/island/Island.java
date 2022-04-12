package me.illusion.skyblockcore.spigot.island;

import me.illusion.skyblockcore.shared.data.IslandData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public interface Island {

    CompletableFuture<Void> save();

    CompletableFuture<Void> saveData();

    void teleport(Player player);

    String getWorldName();

    IslandData getData();

    boolean locationBelongs(Location location);

}
