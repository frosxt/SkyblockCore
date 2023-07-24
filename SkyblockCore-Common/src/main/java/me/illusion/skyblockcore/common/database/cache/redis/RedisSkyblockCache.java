package me.illusion.skyblockcore.common.database.cache.redis;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import me.illusion.skyblockcore.common.communication.redis.RedisController;
import me.illusion.skyblockcore.common.database.cache.SkyblockCacheDatabase;
import redis.clients.jedis.Jedis;

public class RedisSkyblockCache implements SkyblockCacheDatabase {

    private final Set<CompletableFuture<?>> futures = ConcurrentHashMap.newKeySet();
    private RedisController controller;

    @Override
    public String getName() {
        return "redis";
    }

    @Override
    public CompletableFuture<Boolean> enable(Map<String, ?> properties) {
        return CompletableFuture.supplyAsync(() -> {
            String host = getOrDefault(properties, "host", "localhost");
            int port = getOrDefault(properties, "port", 6379);
            String password = getOrDefault(properties, "password", null);
            boolean ssl = getOrDefault(properties, "ssl", false);

            controller = new RedisController(host, port, password, ssl);

            return controller.isValid();
        });
    }

    @Override
    public CompletableFuture<String> getIslandServer(UUID islandId) {
        return associate(jedis -> jedis.hget("island-servers", islandId.toString()));
    }

    @Override
    public CompletableFuture<Void> updateIslandServer(UUID islandId, String serverId) {
        return associateTask(jedis -> jedis.hset("island-servers", islandId.toString(), serverId));
    }

    @Override
    public CompletableFuture<Void> removeServer(String serverId) {
        return associateTask(jedis -> jedis.hdel("island-servers", serverId));
    }

    @Override
    public CompletableFuture<Void> removeIsland(UUID islandId) {
        return associateTask(jedis -> jedis.hdel("island-servers", islandId.toString()));
    }

    @Override
    public CompletableFuture<Void> flush() {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private <T> CompletableFuture<T> associate(Function<Jedis, T> function) {
        CompletableFuture<T> future = controller.supply(function);

        futures.add(future);

        future.thenRun(() -> futures.remove(future));
        future.exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });

        return future;
    }

    private CompletableFuture<Void> associateTask(Consumer<Jedis> consumer) {
        CompletableFuture<Void> future = controller.borrow(consumer);

        futures.add(future);

        future.thenRun(() -> futures.remove(future));
        future.exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });

        return future;
    }

    private <T> T getOrDefault(Map<String, ?> map, String key, T defaultValue) {
        Object value = map.get(key);

        if (value == null) {
            return defaultValue;
        }

        return (T) value;
    }

    private <T> T getOrDefault(Map<String, ?> map, String key) {
        return getOrDefault(map, key, null);
    }
}
