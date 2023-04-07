package com.example.worker.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

public class FileServices {

    public static final String DATABASES_DIRECTORY = "./src/main/java/com/example/worker/databases/";
    public static final String SCHEMAS_DIRECTORY = "/schemas/";

    public static String readFileAsString(File file) {
        try {
            return new String(Files.readAllBytes(Paths.get(file.getPath())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String addIdToDocument(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            // Parse the JSON string to a JsonNode object
            JsonNode node = mapper.readTree(json);

            // Check if the _id field already exists
            if (!node.has("_id")) {
                // Generate a UUID and add it as the _id field
                UUID uuid = UUID.randomUUID();
                ((ObjectNode) node).put("_id", uuid.toString());
            }

            // Convert the JsonNode object back to a JSON string
            String newJson = mapper.writeValueAsString(node);
            return newJson;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isDatabaseExists(File file) {
        return file.exists() && file.isDirectory();
    }

    public static boolean isCollectionExists(File file) {
        return file.exists() && file.isFile();
    }

    public static String removeIdFromDoc(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            // Parse the JSON string to a JsonNode object
            JsonNode node = mapper.readTree(json);

            // Check if the _id field already exists
            if (node.has("_id")) {
                // Remove the _id field
                ((ObjectNode) node).remove("_id");
            }

            // Convert the JsonNode object back to a JSON string
            String newJson = mapper.writeValueAsString(node);
            return newJson;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
