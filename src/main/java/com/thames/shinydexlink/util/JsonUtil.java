package com.thames.shinydexlink.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public final class JsonUtil {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, typeOfSrc, context) ->
                    new JsonPrimitive(TimeUtil.format(src)))
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) -> {
                try {
                    return Instant.parse(json.getAsString());
                } catch (RuntimeException exception) {
                    throw new JsonParseException("Invalid instant: " + json, exception);
                }
            })
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private JsonUtil() {
    }

    public static <T> T read(Path path, Type type) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static void writeAtomic(Path path, Object value) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
            writer.write(System.lineSeparator());
        }
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static JsonObject toObject(Object value) {
        JsonElement element = GSON.toJsonTree(value);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Value did not serialize to a JSON object");
        }
        return element.getAsJsonObject();
    }

    public static String redactedJson(JsonObject object) {
        JsonObject copy = object.deepCopy();
        if (copy.has("serverToken")) {
            copy.addProperty("serverToken", "<redacted>");
        }
        return GSON.toJson(copy);
    }
}
