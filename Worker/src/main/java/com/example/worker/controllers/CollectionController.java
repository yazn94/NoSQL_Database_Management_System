package com.example.worker.controllers;

import com.example.worker.DAO.DAO;
import com.example.worker.indexing.PropertyIndexManager;
import com.example.worker.model.APIResponse;
import com.example.worker.model.Schema;
import com.example.worker.services.affinity.AffinityManager;
import com.example.worker.services.authentication.AuthenticationService;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;

import static com.example.worker.services.FileServices.*;
import static com.example.worker.services.affinity.AffinityManager.BOOTSTRAPPING_NODE_TOKEN;
import static com.example.worker.services.affinity.AffinityManager.BOOTSTRAPPING_NODE_USERNAME;

@RestController
@RequestMapping("/api")
public class CollectionController {
    private final RestTemplate restTemplate = new RestTemplate();
    private final Object lock = new Object(); // lock object for synchronization
    private DAO dao = new DAO();
    private PropertyIndexManager propertyIndexManager = PropertyIndexManager.getInstance();
    private AuthenticationService authenticationService = new AuthenticationService();
    private AffinityManager affinityManager = AffinityManager.getInstance();
    private Logger logger = LoggerFactory.getLogger(CollectionController.class);

    @PostMapping("createCol/{db_name}/{collection_name}")
    @ResponseBody
    public APIResponse addCollection(
            @PathVariable("db_name") String dbName,
            @PathVariable("collection_name") String collectionName,
            @RequestBody Schema schema,
            @RequestHeader(value = "X-Propagated-Request", defaultValue = "false") boolean PropagatedRequest,
            @RequestHeader(value = "X-Username") String username,
            @RequestHeader(value = "X-Token") String token
    ) {
        logger.info("Received request to create collection " + collectionName + " in database " + dbName
                + (PropagatedRequest ? " (propagated)" : "") + " from user " + username);

        dbName = dbName.toLowerCase();
        collectionName = collectionName.toLowerCase();


        if (!authenticationService.isAuthenticatedUser(username, token)) {
            logger.warn("User was not authorized.");
            return new APIResponse("User is not authorized.", 401);
        }

        File collectionFile = new File(DATABASES_DIRECTORY + dbName + "/" + collectionName + ".json");
        APIResponse response = new APIResponse("", 200);

        if (PropagatedRequest) {
            logger.info("the request was propagated.");
            if (collectionFile.exists()) {
                logger.warn("Collection already exists.");
                response.setMessage("Collection already exists.");
                response.setStatusCode(400);
            } else {
                File schemaFile = new File(DATABASES_DIRECTORY + dbName + SCHEMAS_DIRECTORY + collectionName + ".json");
                dao.createCollection(collectionFile, schemaFile, schema);
                response.setMessage("Collection created successfully.");
                response.setStatusCode(200);
            }
        } else {
            logger.info("started to propagate the request.");
            // Propagate the request to the other nodes
            for (String worker : affinityManager.getWorkers()) {
                String url = "http://" + worker + ":8081/api/createCol/" + dbName + "/" + collectionName;
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Propagated-Request", "true");
                headers.set("X-Username", BOOTSTRAPPING_NODE_USERNAME);
                headers.set("X-Token", BOOTSTRAPPING_NODE_TOKEN);
                HttpEntity<Schema> requestEntity = new HttpEntity<>(schema, headers);
                restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
                response.setMessage("Collection created successfully.");
                response.setStatusCode(200);
            }
        }

        logger.info("Finished creating collection " + collectionName + " in database " + dbName);
        return response;
    }

    @DeleteMapping("/deleteCol/{db_name}/{collection_name}")
    @ResponseBody
    public APIResponse deleteCollection(
            @PathVariable("db_name") String dbName,
            @PathVariable("collection_name") String collectionName,
            @RequestHeader(value = "X-Propagated-Request", defaultValue = "false") boolean PropagatedRequest,
            @RequestHeader(value = "X-Username") String username,
            @RequestHeader(value = "X-Token") String token
    ) {
        logger.info("Received request to delete collection '{}.{}'"
                + (PropagatedRequest ? " (propagated)" : ""), dbName, collectionName);

        dbName = dbName.toLowerCase();
        collectionName = collectionName.toLowerCase();
        if (!authenticationService.isAuthenticatedUser(username, token)) {
            logger.warn("User is not authorized to delete collection '{}.{}'.", dbName, collectionName);
            return new APIResponse("User is not authorized.", 401);
        }

        File collectionFile = new File(DATABASES_DIRECTORY + dbName + "/" + collectionName + ".json");
        APIResponse response = new APIResponse("", 200);

        if (!collectionFile.exists()) {
            logger.warn("Collection '{}.{}' does not exist.", dbName, collectionName);
            response.setMessage("Collection does not exist.");
            response.setStatusCode(400);
            return response;
        }

        if (PropagatedRequest) {
            // clearing the cache and the indexing
            try {
                dao.clearCollectionCaching(collectionFile);
                propertyIndexManager.clearCollectionIndexing(dbName, collectionName);
                File schemaFile = new File(DATABASES_DIRECTORY + dbName + SCHEMAS_DIRECTORY + collectionName + ".json");

                synchronized (lock) {
                    collectionFile.delete();
                    schemaFile.delete();
                }

                logger.info("Collection '{}.{}' deleted successfully.", dbName, collectionName);
                response.setMessage("Collection deleted successfully.");
                response.setStatusCode(200);

            } catch (Exception e) {
                logger.error("Error deleting collection '{}.{}'.", dbName, collectionName, e);
                response.setMessage("Error deleting collection.");
                response.setStatusCode(500);
            }
        } else {
            // Propagate the request to the other nodes
            for (String worker : affinityManager.getWorkers()) {
                String url = "http://" + worker + ":/api/deleteCol/" + dbName.toLowerCase() + "/" + collectionName.toLowerCase();
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Propagated-Request", "true");
                headers.set("X-Username", BOOTSTRAPPING_NODE_USERNAME);
                headers.set("X-Token", BOOTSTRAPPING_NODE_TOKEN);
                HttpEntity<String> requestEntity = new HttpEntity<>(headers);
                restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, String.class);
            }
        }
        return response;
    }

    @GetMapping("/filter/{db_name}/{collectionName}")
    @ResponseBody
    public APIResponse filterCollection(
            @PathVariable("db_name") String dbName,
            @PathVariable("collectionName") String collectionName,
            @RequestParam("attributeName") String attributeName,
            @RequestParam("attributeValue") String attributeValue,
            @RequestHeader(value = "X-Username") String username,
            @RequestHeader(value = "X-Token") String token
    ) {
        logger.info("Received request to filter collection '{}.{}' by attribute '{}={}'", dbName, collectionName, attributeName, attributeValue);
        dbName = dbName.toLowerCase();
        collectionName = collectionName.toLowerCase();
        if (!authenticationService.isAuthenticatedUser(username, token)) {
            logger.warn("User is not authorized to filter collection '{}.{}'.", dbName, collectionName);
            return new APIResponse("User is not authorized.", 401);
        }

        File dbDirectory = new File(DATABASES_DIRECTORY + dbName);
        if (!dbDirectory.exists() && !dbDirectory.isDirectory()) {
            logger.error("Database does not exist. dbName={}", dbName);
            return new APIResponse("Database does not exist.", 400);
        }

        File collectionFile = new File(DATABASES_DIRECTORY + dbName + "/" + collectionName + ".json");
        APIResponse response = new APIResponse("", 200);

        if (!collectionFile.exists()) {
            logger.error("Collection does not exist. dbName={}, collectionName={}", dbName, collectionName);
            response.setMessage("Collection does not exist.");
            response.setStatusCode(400);
            return response;
        }

        // First, check if the index exists
        JSONArray collections = propertyIndexManager.getMatchingDocs(dbName, collectionName, attributeName, attributeValue);
        if (collections != null) {
            logger.info("Filtered data retrieved from property index. dbName={}, collectionName={}, attributeName={}, attributeValue={}", dbName, collectionName, attributeName, attributeValue);
            response.setMessage(collections.toString());
            response.setStatusCode(200);
            return response;
        }

        // else, read it from the file system
        JSONArray filteredData = dao.getFilteredData(collectionFile, attributeName, attributeValue);
        logger.info("Filtered data retrieved from file system. dbName={}, collectionName={}, attributeName={}, attributeValue={}", dbName, collectionName, attributeName, attributeValue);
        response.setMessage(filteredData.toString());
        response.setStatusCode(200);

        return response;
    }


    @GetMapping("getCollections/{db_name}")
    public APIResponse getCollections(@PathVariable("db_name") String dbName,
                                      @RequestHeader(value = "X-Username") String username,
                                      @RequestHeader(value = "X-Token") String token) {
        logger.info("Received request to get all collections in database '{}'", dbName);

        dbName = dbName.toLowerCase();
        if (!authenticationService.isAuthenticatedUser(username, token)) {
            return new APIResponse("User is not authorized.", 401);
        }

        File dbDirectory = new File(DATABASES_DIRECTORY + dbName);
        if (!isDatabaseExists(dbDirectory)) {
            logger.error("Database does not exist. dbName={}", dbName);
            return new APIResponse("Database does not exist.", 400);
        }

        logger.info("Finished getting all collections in database '{}'", dbName);
        return new APIResponse(dao.allCollections(dbDirectory), 200);
    }
}