package com.example.worker.services.authentication;

import com.example.worker.caching.LRUCache;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static com.example.worker.services.FileServices.readFileAsString;

@Component
public class AuthenticationService {

    private final String authDirectory = "src/main/java/com/example/worker/services/authentication/";
    private final Object lock = new Object();
    private LRUCache<String, String> userCache, adminCache;

    public AuthenticationService() {
        userCache = new LRUCache<>(1000);
        adminCache = new LRUCache<>(1000);
    }

    public void addUser(String username, String token) {
        if (username == null || token == null) {
            throw new RuntimeException("username or token is null");
        }

        // adding to the cache.
        userCache.put(token, username);

        // adding to the file.
        File usersCollection = new File(authDirectory + "users.json");
        String content = readFileAsString(usersCollection);
        if (content.isEmpty())
            content = "[]";
        JSONArray jsonArray = new JSONArray(content);
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("username", username);
        jsonObject.put("token", token);
        jsonArray.put(jsonObject);
        
        try {

            FileWriter fileWriter = new FileWriter(usersCollection);
            synchronized (lock) {
                fileWriter.write(jsonArray.toString());
            }
            fileWriter.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addNewAdmin(String username, String token) {
        if (username == null || token == null) {
            throw new RuntimeException("username or token is null");
        }

        // adding the admin as a user first.
        addUser(username, token);

        // adding to the cache.
        adminCache.put(token, username);

        // adding to the file.
        File adminsCollection = new File(authDirectory + "admins.json");
        String content = readFileAsString(adminsCollection);

        if (content.isEmpty())
            content = "[]";

        JSONArray jsonArray = new JSONArray(content);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username", username);
        jsonObject.put("token", token);
        jsonArray.put(jsonObject);

        try {
            synchronized (lock) {
                FileWriter fileWriter = new FileWriter(adminsCollection);
                fileWriter.write(jsonArray.toString());
                fileWriter.flush();
                fileWriter.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void removeUser(String username, String token) {
        if (username == null || token == null) {
            throw new RuntimeException("username or token is null");
        }

        // removing from the cache.
        userCache.remove(token);

        // removing from the file.
        File usersCollection = new File(authDirectory + "users.json");
        String content = readFileAsString(usersCollection);
        JSONArray jsonArray = new JSONArray(content);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            if (jsonObject.get("token").equals(token) && jsonObject.get("username").equals(username)) {
                jsonArray.remove(i);
                break;
            }
        }
        try {
            FileWriter fileWriter = new FileWriter(usersCollection);
            fileWriter.write(jsonArray.toString());
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isAuthenticatedUser(String username, String token) {
        if (username == null || token == null) {
            throw new RuntimeException("username or token is null");
        }

        // checking the cache.
        Object get = userCache.get(token);
        if (get != null && get.equals(username)) {
            return true;
        }

        // checking the file.
        File collection = new File(authDirectory + "users.json");
        String content = readFileAsString(collection);
        JSONArray jsonArray = new JSONArray(content);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            if (jsonObject.get("token").equals(token) && jsonObject.get("username").equals(username)) {
                userCache.put(username, token);
                return true;
            }
        }
        return false;
    }

    public boolean isAdmin(String username, String token) {
        if (username == null || token == null) {
            throw new RuntimeException("username or token is null");
        }

        // checking the cache.
        Object get = adminCache.get(token);
        if (get != null && get.equals(username)) {
            return true;
        }


        // checking the file.
        File collection = new File(authDirectory + "admins.json");
        String content = readFileAsString(collection);
        JSONArray jsonArray = new JSONArray(content);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            if (jsonObject.get("token").equals(token) && jsonObject.get("username").equals(username)) {
                adminCache.put(username, token);
                return true;
            }
        }
        return false;
    }
}
