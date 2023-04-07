package com.example.bankingsystem.services;

import jakarta.servlet.http.HttpSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.logging.Logger;

public class NetworkManager {
    private static NetworkManager instance = null;
    private final String BOOTSTRAPPING_NODE_USERNAME = "bootstrappingNode";
    private final String BOOTSTRAPPING_NODE_TOKEN = "@321bootstrappingNode123@";
    private Logger logger = Logger.getLogger(NetworkManager.class.getName());
    private RestTemplate restTemplate = new RestTemplate();
    private String databaseIP = "35.184.131.198";
    private HashMap<String, Integer> tokenToWorkerPort = new HashMap<>();

    private NetworkManager() {
        logger.info("NetworkManager created and connected to " + databaseIP);
    }

    public static NetworkManager getInstance() {
        if (instance == null) {
            instance = new NetworkManager();
        }
        return instance;
    }

    public String getWorkerName(int port) {
        return "worker" + (port - 8080);
    }


    public void setDatabaseIP(String databaseIP) {
        this.databaseIP = databaseIP;
    }

    public String registerANewUser(String username) {
        String url = "http://" + databaseIP + ":8080/api/register/" + username;
        return restTemplate.getForObject(url, String.class);
    }

    public void addWorkerPortToToken(String token, Integer workerPort) {
        tokenToWorkerPort.put(token, workerPort);
    }

    public int getWorkerPort(String token) {
        return tokenToWorkerPort.get(token);
    }

    public boolean isAuthorizedUser(String username, String token) {
        String url = "http://" + databaseIP + ":" + getWorkerPort(token) + "/api/isUser/" + username + "/" + token;
        return restTemplate.getForObject(url, Boolean.class);
    }

    public boolean isAuthorizedAdmin(String username, String token) {
        String url = "http://" + databaseIP + ":" + 8081 + "/api/isAdmin/" + username + "/" + token;
        return restTemplate.getForObject(url, Boolean.class);
    }

    public void buildDatabaseSchema() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Username", BOOTSTRAPPING_NODE_USERNAME);
        headers.set("X-Token", BOOTSTRAPPING_NODE_TOKEN);
        headers.set("Content-Type", "application/json");

        HttpEntity<String> requestEntity = new HttpEntity<>("", headers);
        String url = "http://" + databaseIP + ":8081/api/createDB/bankingSystem";
        logger.info("Creating bankingSystem database");
        restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, requestEntity, String.class);

        logger.info("Creating customers collection");
        url = "http://" + databaseIP + ":8081/api/createCol/bankingSystem/customers";
        HttpEntity<String> requestEntity2 = new HttpEntity<>("{\n" +
                "    \"type\": \"object\",\n" +
                "    \"properties\": {\n" +
                "        \"name\": {\n" +
                "            \"type\": \"string\"\n" +
                "        },\n" +
                "        \"phone\": {\n" +
                "            \"type\": \"string\"\n" +
                "        },\n" +
                "        \"address\": {\n" +
                "            \"type\": \"string\"\n" +
                "        },\n" +
                "        \"accountBalance\": {\n" +
                "            \"type\": \"number\"\n" +
                "        }\n" +
                "    },\n" +
                "    \"required\": [\n" +
                "        \"name\",\n" +
                "        \"phone\",\n" +
                "        \"address\",\n" +
                "        \"accountBalance\"\n" +
                "    ],\n" +
                "    \"additionalProperties\": false\n" +
                "} ", headers);

        restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, requestEntity2, String.class);

        logger.info("Creating transactions collection");
        String url2 = "http://" + databaseIP + ":8081/api/createCol/bankingSystem/transactions";
        HttpEntity<String> requestEntity3 = new HttpEntity<>("{\n" +
                "    \"type\": \"object\",\n" +
                "    \"properties\": {\n" +
                "        \"customerID\": {\n" +
                "            \"type\": \"string\"\n" +
                "        },\n" +
                "        \"transactionAmount\": {\n" +
                "            \"type\": \"string\"\n" +
                "        }\n" +
                "    },\n" +
                "    \"required\": [\n" +
                "        \"customerID\",\n" +
                "        \"transactionAmount\"\n" +
                "    ],\n" +
                "    \"additionalProperties\": false\n" +
                "}\n", headers);
        restTemplate.exchange(url2, org.springframework.http.HttpMethod.POST, requestEntity3, String.class);
    }

    public void addNewCustomer(JSONObject customer,
                               HttpSession session) {

        logger.info("Adding new customer to database" + customer.toString());

        // fetching the data from the session
        String workerPort = session.getAttribute("workerPort").toString();
        String token = session.getAttribute("token").toString();
        String username = session.getAttribute("username").toString();

        logger.info("workerPort: " + workerPort);
        logger.info("token: " + token);
        logger.info("username: " + username);


        String url = "http://" + databaseIP + ":" + workerPort + "/api/insertOne/bankingSystem/customers";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Username", username);
        headers.set("X-Token", token);
        headers.set("Content-Type", "application/json");
        HttpEntity<String> requestEntity = new HttpEntity<>(customer.toString(), headers);
        restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, requestEntity, String.class);
    }

    public boolean makeTransaction(String customerID, String amount,
                                   HttpSession httpSession) {

        // checking if the amount is a valid number
        if (!amount.matches("^[+-][0-9]+")) {
            return false;
        }
        int amountInt = Integer.parseInt(amount);

        // fetching the data from the session
        String workerPort = httpSession.getAttribute("workerPort").toString();
        String token = httpSession.getAttribute("token").toString();
        String username = httpSession.getAttribute("username").toString();

        logger.info("workerPort: " + workerPort);
        logger.info("token: " + token);
        logger.info("username: " + username);

        String url = "http://" + databaseIP + ":" + workerPort + "/api/insertOne/bankingSystem/transactions";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Username", username);
        headers.set("X-Token", token);
        headers.set("Content-Type", "application/json");

        JSONObject transaction = new JSONObject();
        transaction.put("customerID", customerID);
        transaction.put("transactionAmount", amount);

        HttpEntity<String> requestEntity = new HttpEntity<>(transaction.toString(), headers);
        restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);


        // getting the old customer object
        JSONObject customer = getCustomerByID(customerID, httpSession);
        int oldBalance = customer.getInt("accountBalance");
        int newBalance = oldBalance + amountInt;

        url = "http://" + databaseIP + ":" + workerPort + "/api/updateDoc/bankingSystem/customers/" + customerID
                + "/accountBalance/" + newBalance;

        HttpEntity<String> requestEntity2 = new HttpEntity<>("", headers);
        restTemplate.exchange(url, HttpMethod.POST, requestEntity2, String.class);

        return true;
    }

    public JSONObject getCustomerByID(String id, HttpSession httpSession) {
        logger.info("Getting customer by id: " + id);

        String workerPort = httpSession.getAttribute("workerPort").toString();
        String token = httpSession.getAttribute("token").toString();
        String username = httpSession.getAttribute("username").toString();

        String url = "http://" + databaseIP + ":" + workerPort + "/api/getDoc/bankingSystem/customers/" + id;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Username", username);
        headers.set("X-Token", token);
        headers.set("Content-Type", "application/json");
        ResponseEntity<String> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, new HttpEntity<>("", headers), String.class);

        logger.info("response: " + response.getBody());
        JSONObject responseJSON = new JSONObject(response.getBody());
        JSONObject customer = new JSONObject(responseJSON.getString("message"));


        logger.info("customer: " + customer.toString());
        return customer;
    }

    public String allCustomers(HttpSession httpSession) {
        String workerPort = httpSession.getAttribute("workerPort").toString();
        String token = httpSession.getAttribute("token").toString();
        String username = httpSession.getAttribute("username").toString();


        logger.info("workerPort: " + workerPort);
        logger.info("token: " + token);
        logger.info("username: " + username);

        String url = "http://" + databaseIP + ":" + workerPort + "/api/getAllDocs/bankingSystem/customers";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Username", username);
        headers.set("X-Token", token);
        headers.set("Content-Type", "application/json");

        HttpEntity<String> requestEntity = new HttpEntity<>("", headers);
        return restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, requestEntity, String.class).getBody();
    }


    public JSONArray getCustomerTransactions(String customerId, HttpSession httpSession) {


        String workerPort = httpSession.getAttribute("workerPort").toString();
        String token = httpSession.getAttribute("token").toString();
        String username = httpSession.getAttribute("username").toString();

        logger.info("workerPort: " + workerPort);
        logger.info("token: " + token);
        logger.info("username: " + username);

        String url = "http://" + databaseIP + ":" + workerPort + "/api/filter/bankingSystem/transactions?" +
                "attributeName=customerID&attributeValue=" + customerId;


        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Username", username);
        headers.set("X-Token", token);
        headers.set("Content-Type", "application/json");


        String response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>("", headers), String.class).getBody();
        logger.info("response: " + response);

        JSONObject responseJSON = new JSONObject(response);
        JSONArray transactions = new JSONArray(responseJSON.getString("message"));
        logger.info("transactions: " + transactions.toString());

        return transactions;
    }

    public JSONArray allActiveUsers(HttpSession httpSession) {
        String username = httpSession.getAttribute("username").toString();
        String token = httpSession.getAttribute("token").toString();

        String url = "http://" + databaseIP + ":8080/api/getAllUsers";
        String users = restTemplate.getForObject(url, String.class);
        logger.info("users: " + users);

        JSONArray usersJSON = new JSONArray(users);
        logger.info("users: " + usersJSON.toString());
        return usersJSON;
    }

    public void removeUser(String token, HttpSession httpSession) {
        String url = "http://" + databaseIP + ":8080/api/removeUser/" + token;
        restTemplate.getForObject(url, String.class);
    }

    public void removeCustomer(String customerID, HttpSession httpSession) {
        String workerPort = httpSession.getAttribute("workerPort").toString();
        String token = httpSession.getAttribute("token").toString();
        String username = httpSession.getAttribute("username").toString();


        logger.info("workerPort: " + workerPort);
        logger.info("token: " + token);
        logger.info("username: " + username);

        String url = "http://" + databaseIP + ":" + workerPort + "/api/deleteDoc/bankingSystem/customers/" + customerID;
        HttpHeaders headers = new HttpHeaders();

        headers.set("X-Username", username);
        headers.set("X-Token", token);

        HttpEntity<String> requestEntity = new HttpEntity<>("", headers);
        restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, String.class);
    }
}
