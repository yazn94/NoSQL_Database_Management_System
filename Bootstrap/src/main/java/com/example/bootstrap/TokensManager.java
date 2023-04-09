package com.example.bootstrap;


import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

@Service
public class TokensManager {
    private static final String tokensFilePath = "src/main/resources/static/tokens.json";
    private static TokensManager instance;

    // three hours
    private final Long expirationTime = 1000L * 60 * 60 * 3;
    // 30 minutes
    private final Long checkForExpiredTokensInterval = 1000L * 60 * 30;
    private File tokensFile;
    private Logger logger = Logger.getLogger(TokensManager.class.getName());

    private TokensManager() {
        tokensFile = new File(tokensFilePath);
    }

    public static TokensManager getInstance() {
        if (instance == null)
            instance = new TokensManager();
        return instance;
    }

    private static String readFileAsString(File file) {
        try {
            return new String(Files.readAllBytes(Paths.get(file.getPath())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addNewToken(String userName, String token, String workerName) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userName", userName);
        jsonObject.put("token", token);
        jsonObject.put("workerName", workerName);
        jsonObject.put("expirationTime", System.currentTimeMillis() + expirationTime);

        String content = readFileAsString(tokensFile);
        JSONArray jsonArray = new JSONArray(content);
        jsonArray.put(jsonObject);

        try {
            Files.write(Paths.get(tokensFilePath), jsonArray.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Scheduled(fixedRate = checkForExpiredTokensInterval)
    public void removeExpiredTokens() {
        logger.info("Checking for expired tokens - new cycle -");

        String content = readFileAsString(tokensFile);
        JSONArray jsonArray = new JSONArray(content);

        for (int i = jsonArray.length() - 1; i >= 0; i--) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Long expirationTime = jsonObject.getLong("expirationTime");

            if (System.currentTimeMillis() > expirationTime) {
                String userName = jsonObject.getString("userName");
                String token = jsonObject.getString("token");
                String workerName = jsonObject.getString("workerName");

                // Sending a request to the worker to remove the registered user.
                NetworkManager.getInstance().removeExpiredToken(userName, token, workerName);

                // Removing the User from the tokens.json file
                jsonArray.remove(i);
            }
        }
        try {
            Files.write(Paths.get(tokensFilePath), jsonArray.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String allUsers() {
        return readFileAsString(tokensFile);
    }

    public void removeUser(String token) {
        String content = readFileAsString(tokensFile);
        JSONArray jsonArray = new JSONArray(content);

        for (int i = jsonArray.length() - 1; i >= 0; i--) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            String currentToken = jsonObject.getString("token");
            if (currentToken.equals(token)) {
                String userName = jsonObject.getString("userName");
                String workerName = jsonObject.getString("workerName");
                // Sending a request to the worker to remove the registered user.
                NetworkManager.getInstance().removeExpiredToken(userName, token, workerName);
                jsonArray.remove(i);
                break;
            }
        }

        // writing the new content to the tokens.json file
        try {
            Files.write(Paths.get(tokensFilePath), jsonArray.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
