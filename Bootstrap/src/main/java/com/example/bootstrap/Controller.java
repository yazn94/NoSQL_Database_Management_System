package com.example.bootstrap;

import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api")
public class Controller {
    private LoadBalancer loadBalancer = LoadBalancer.getInstance();
    private NetworkManager networkManager = NetworkManager.getInstance();
    private Logger logger = Logger.getLogger(Controller.class.getName());

    @GetMapping("/register/{username}")
    public String register(@PathVariable("username") String username) {
        logger.info("Received request to register user " + username);

        String token = UUID.randomUUID().toString();

        String workerName = loadBalancer.getNextWorkerName();
        loadBalancer.passToNextWorker();

        networkManager.sendUserInfo(username, token, workerName);

        JSONObject userCredentialsJSON = new JSONObject();
        int port = loadBalancer.getWorkerPort(workerName);
        userCredentialsJSON.put("username", username);
        userCredentialsJSON.put("token", token);
        userCredentialsJSON.put("workerPort", port);

        logger.info("The new user credentials have been sent to the TokenExpirationManager");
        TokensManager.getInstance().addNewToken(username, token, workerName);

        logger.info("User " + username + " registered with token " + token);
        return userCredentialsJSON.toString();
    }

    @GetMapping("getAllUsers")
    public String getAllUsers() {
        logger.info("Received request to get all users");
        return TokensManager.getInstance().allUsers();
    }

    @GetMapping("removeUser/{token}")
    public void removeUser(@PathVariable("token") String token) {
        logger.info("Received request to remove user with token " + token);
        TokensManager.getInstance().removeUser(token);
    }
}