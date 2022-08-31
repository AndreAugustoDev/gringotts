package org.gestern.gringotts.data;

import com.google.common.collect.ImmutableList;
import com.google.gson.*;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

public class UUIDFetcher implements Callable<Map<String, UUID>> {
    private static final double       PROFILES_PER_REQUEST = 100;
    private static final String       PROFILE_URL          = "https://api.mojang.com/profiles/minecraft";
    private static final Gson         GSON                 = new GsonBuilder().create();
    private final        List<String> names;
    private final        boolean      rateLimiting;

    public UUIDFetcher(List<String> names, boolean rateLimiting) {
        this.names        = ImmutableList.copyOf(names);
        this.rateLimiting = rateLimiting;
    }

    private static void writeBody(HttpURLConnection connection, String body) throws Exception {
        OutputStream stream = connection.getOutputStream();

        stream.write(body.getBytes());
        stream.flush();
        stream.close();
    }

    private static HttpURLConnection createConnection() throws Exception {
        URL url = new URL(PROFILE_URL);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(true);

        return connection;
    }

    private static UUID getUUID(String id) {
        return UUID.fromString(id.substring(0, 8) + "-" +
                id.substring(8, 12) + "-" +
                id.substring(12, 16) + "-" +
                id.substring(16, 20) + "-" +
                id.substring(20, 32));
    }

    public Map<String, UUID> call() throws Exception {
        Map<String, UUID> uuidMap = new HashMap<>();

        int requests = (int) Math.ceil(names.size() / PROFILES_PER_REQUEST);

        for (int i = 0; i < requests; i++) {
            HttpURLConnection connection = createConnection();
            String            body       = GSON.toJson(names.subList(i * 100, Math.min((i + 1) * 100, names.size())));
            writeBody(connection, body);
            JsonArray array = (JsonArray) JsonParser.parseReader(new InputStreamReader(connection.getInputStream()));

            for (Object profile : array) {
                JsonObject jsonProfile = (JsonObject) profile;
                String     id          = jsonProfile.get("id").getAsString();
                String     name        = jsonProfile.get("name").getAsString();
                UUID       uuid        = UUIDFetcher.getUUID(id);
                uuidMap.put(name, uuid);
            }

            if (rateLimiting && i != requests - 1) {
                Thread.sleep(100L);
            }
        }

        return uuidMap;
    }
}
