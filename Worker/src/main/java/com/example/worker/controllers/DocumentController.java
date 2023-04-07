package com.example.worker.controllers;

import com.example.worker.DAO.DAO;
import com.example.worker.indexing.PropertyIndexManager;
import com.example.worker.model.APIResponse;
import com.example.worker.services.affinity.AffinityManager;
import com.example.worker.services.authentication.AuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.example.worker.services.FileServices.*;
import static com.example.worker.services.affinity.AffinityManager.BOOTSTRAPPING_NODE_TOKEN;
import static com.example.worker.services.affinity.AffinityManager.BOOTSTRAPPING_NODE_USERNAME;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private DAO dao = new DAO();
    private PropertyIndexManager propertyIndexManager = PropertyIndexManager.getInstance();
    private AffinityManager affinityManager = AffinityManager.getInstance();
    private RestTemplate restTemplate = new RestTemplate();
    private AuthenticationService authenticationService = new AuthenticationService();
    private Logger logger = LoggerFactory.getLogger(DocumentController.class);

    public boolean validJSONObject(String json, File schemaFile) {
        try {
            String withoutId = removeIdFromDoc(json);
            // Read the schema file
            String schema = readFileAsString(schemaFile);

            // Parse the schema and create a validator
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance();
            JsonSchema validator = factory.getSchema(schema);

            // Parse the JSON string
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(withoutId);

            // Validate the JSON against the schema
            Set<ValidationMessage> errors = validator.validate(jsonNode);

            // If there are no errors, then the JSON is valid
            return errors.isEmpty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @PostMapping("/insertOne/{db_name}/{collection_name}")
    public APIResponse insertOne(
            @PathVariable("db_name") String dbName,
            @PathVariable("collection_name") String collectionName,
            @RequestBody String json,
            @RequestHeader(value = "X-Propagated-Request", defaultValue = "false") boolean propagatedRequest,
            @RequestHeader(value = "X-Username") String username,
            @RequestHeader(value = "X-Token") String token
    ) {
        logger.info("trying to add the document" + json);

        dbName = dbName.toLowerCase();
        collectionName = collectionName.toLowerCase();
        logger.info("Inserting document into database: {}, collection: {}" + (propagatedRequest ? " (propagated)" : ""), dbName, collectionName);

        if (!authenticationService.isAuthenticatedUser(username, token)) {
            logger.warn("User is not authorized: {}", username);
            return new APIResponse("User is not authorized.", 401);
        }

        File dbDirectory = new File(DATABASES_DIRECTORY + dbName);
        File collectionFile = new File(DATABASES_DIRECTORY + dbName + "/" + collectionName + ".json");
        File schemaFile = new File(DATABASES_DIRECTORY + dbName + SCHEMAS_DIRECTORY + collectionName + ".json");

        if (propagatedRequest) { // - Just take the new copy of the data. (no need to propagate it again)
            logger.info("Propagated request to add the document:" + json);
            dao.addDocument(collectionFile, json);
            propertyIndexManager.indexingNewObject(dbName, collectionName, new JSONObject(json));
            logger.info("Document inserted successfully");
            return new APIResponse("Document inserted successfully.", 200);
        }
        if (!isDatabaseExists(dbDirectory)) {
            logger.warn("Database does not exist: {}", dbName);
            return new APIResponse("Database does not exist.", 400);
        }
        if (!isCollectionExists(collectionFile)) {
            logger.warn("Collection does not exist: {}", collectionName);
            return new APIResponse("Collection does not exist.", 400);
        }
        if (!validJSONObject(json, schemaFile)) {
            logger.warn("Invalid JSON object: {}", json);
            return new APIResponse("Invalid JSON object.", 400);
        }


        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Username", BOOTSTRAPPING_NODE_USERNAME);
        headers.set("X-Token", BOOTSTRAPPING_NODE_TOKEN);


        // the current worker is the affinity worker
        if (affinityManager.isCurrentWorkerAffinity()) {
            json = addIdToDocument(json);
            // propagating the new document to all workers
            for (String worker : affinityManager.getWorkers()) {
                String url = "http://" + worker + ":8081/api/insertOne/" + dbName + "/" + collectionName;
                headers.set("X-Propagated-Request", "true");
                HttpEntity<String> requestEntity = new HttpEntity<>(json, headers);
                restTemplate.postForObject(url, requestEntity, APIResponse.class);
            }
            // Marking the current worker as affinity node for the new document
            JSONObject jsonObject = new JSONObject(json);
            String id = jsonObject.getString("_id");

            // propagating the affinity data to all workers
            affinityManager.propagateAddingAffinity(id, affinityManager.getCurrentWorkerName());

            // Passing the affinity worker to the next worker
            passTheAffinityToNextWorker();
        } else {
            logger.info("The current worker: " + affinityManager.getCurrentWorkerName() + " is not the affinity worker");
            // The current worker is not the affinity worker
            // search for the affinity worker.
            for (String worker : affinityManager.getWorkers()) {
                {
                    String url = "http://" + worker + ":8081/api/isAffinity";
                    HttpEntity<String> requestEntity = new HttpEntity<>("", headers);
                    ResponseEntity<Boolean> responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Boolean.class);
                    if (responseEntity.getBody()) {
                        logger.info("The current worker: " + affinityManager.getCurrentWorkerName() + " is not the " +
                                "affinity, The affinity worker is: {}", worker);
                        String affinityUrl = "http://" + worker + ":8081/api/insertOne/" + dbName + "/" + collectionName;
                        HttpEntity<String> affinityRequestEntity = new HttpEntity<>(json, headers);
                        restTemplate.postForObject(affinityUrl, affinityRequestEntity, APIResponse.class);
                        break;
                    }
                }
            }
        }
        logger.info("Document inserted successfully");
        return new APIResponse("Document inserted successfully.", 200);
    }

    private void passTheAffinityToNextWorker() {
        affinityManager.unsetCurrentWorkerAffinity();

        // first getting the name of the current worker
        String currentWorkerName = affinityManager.getCurrentWorkerName(), nextWorkerName;
        if (currentWorkerName.equals("worker1")) {
            nextWorkerName = "worker2";
        } else if (currentWorkerName.equals("worker2")) {
            nextWorkerName = "worker3";
        } else
            nextWorkerName = "worker1";
        logger.info("Passing the affinity from " + currentWorkerName + " to the next worker: " + nextWorkerName);

        String url = "http://" + nextWorkerName + ":8081/api/setAffinity";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Username", "bootstrappingNode");
        headers.set("X-Token", "@321bootstrappingNode123@");
        HttpEntity<String> requestEntity = new HttpEntity<>("", headers);
        restTemplate.exchange(url, HttpMethod.GET, requestEntity, Void.class);
    }

    @GetMapping("/getDoc/{db_name}/{collection_name}/{doc_id}")
    @ResponseBody
    public APIResponse getDoc(
            @PathVariable("db_name") String dbName,
            @PathVariable("collection_name") String collectionName,
            @PathVariable("doc_id") String docId,
            @RequestHeader(value = "X-Username") String username,
            @RequestHeader(value = "X-Token") String token) {

        logger.info("Getting document with ID " + docId + " from collection " + collectionName + " in database " + dbName + ".");

        dbName = dbName.toLowerCase();
        collectionName = collectionName.toLowerCase();

        if (!authenticationService.isAuthenticatedUser(username, token)) {
            logger.info("User is not authorized.");
            return new APIResponse("User is not authorized.", 401);
        }

        File dbDirectory = new File(DATABASES_DIRECTORY + dbName);
        File collectionFile = new File(DATABASES_DIRECTORY + dbName + "/" + collectionName + ".json");

        if (!isDatabaseExists(dbDirectory)) {
            logger.info("Database does not exist.");
            return new APIResponse("Database does not exist.", 400);
        }
        if (!isCollectionExists(collectionFile)) {
            logger.info("Collection does not exist.");
            return new APIResponse("Collection does not exist.", 400);
        }

        JSONObject obj = dao.getDocument(dbName, collectionName, docId);
        if (obj == null) {
            return new APIResponse("Document with ID " + docId + " not found in collection " + collectionName + ".", 404);
        } else {
            logger.info("Document with ID " + docId + " found in collection " + collectionName + ".");
            return new APIResponse(obj.toString(), 200);
        }
    }

    @GetMapping("/getAllDocs/{db_name}/{collection_name}")
    @ResponseBody
    public APIResponse getAll(
            @PathVariable("db_name") String db_name,
            @PathVariable("collection_name") String collection_name,
            @RequestHeader(value = "X-Username") String username,
            @RequestHeader(value = "X-Token") String token
    ) {
        logger.info("Getting all documents from collection " + collection_name + " in database " + db_name + ".");

        db_name = db_name.toLowerCase();
        collection_name = collection_name.toLowerCase();
        if (!authenticationService.isAuthenticatedUser(username, token)) {
            logger.info("User is not authorized.");
            return new APIResponse("User is not authorized.", 401);
        }

        APIResponse response = new APIResponse("", 200);
        File collectionFile = new File(DATABASES_DIRECTORY + db_name + "/" + collection_name + ".json");
        if (!collectionFile.exists()) {
            logger.info("Collection does not exist.");
            response.setMessage("Collection does not exist.");
            response.setStatusCode(400);
        } else {
            try {
                String data = dao.getAllDocs(collectionFile);
                response.setMessage(data);
                response.setStatusCode(200);
            } catch (Exception e) {
                response.setMessage("Error getting data.");
                response.setStatusCode(500);
            }
        }
        return response;
    }

    @DeleteMapping("/deleteDoc/{db_name}/{collection_name}/{doc_id}")
    @ResponseBody
    public APIResponse deleteDoc(
            @PathVariable("db_name") String dbName,
            @PathVariable("collection_name") String collectionName,
            @PathVariable("doc_id") String docId,
            @RequestHeader(value = "X-Propagated-Request", defaultValue = "false") boolean propagatedRequest,
            @RequestHeader(value = "X-Username") String username,
            @RequestHeader(value = "X-Token") String token) {
        logger.info("Deleting document with ID " + docId + " from collection " + collectionName +
                " in database " + dbName + (propagatedRequest ? " (propagated)" : "") + ".");
        dbName = dbName.toLowerCase();
        collectionName = collectionName.toLowerCase();

        if (!authenticationService.isAuthenticatedUser(username, token)) {
            logger.info("User is not authorized.");
            return new APIResponse("User is not authorized.", 401);
        }

        File dbDirectory = new File(DATABASES_DIRECTORY + dbName);
        File collectionFile = new File(DATABASES_DIRECTORY + dbName + "/" + collectionName + ".json");

        if (!isDatabaseExists(dbDirectory)) {
            logger.info("Database does not exist.");
            return new APIResponse("Database does not exist.", 400);
        }
        if (!isCollectionExists(collectionFile)) {
            logger.info("Collection does not exist.");
            return new APIResponse("Collection does not exist.", 400);
        }
        // Read the contents of the collection file
        String currentContent = readFileAsString(collectionFile);

        if (propagatedRequest) {
            if (dao.deleteDoc(currentContent, collectionFile, docId)) {
                propertyIndexManager.clearDocumentIndexing(dbName, collectionName, docId);
                affinityManager.removeAffinity(docId);
                return new APIResponse("Document deleted successfully.", 200);
            } else {
                return new APIResponse("Document with ID " + docId + " not found in collection " + collectionName + ".", 404);
            }
        }

        // getting the owner affinity port for the document
        String affinityName = affinityManager.getAffinityName(docId);
        if (affinityName == null) {
            return new APIResponse("Document with ID " + docId + " not found in collection " + collectionName + ".", 404);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Username", BOOTSTRAPPING_NODE_USERNAME);
        headers.set("X-Token", BOOTSTRAPPING_NODE_TOKEN);

        // the document is owned by the current worker
        if (affinityName.equals(affinityManager.getCurrentWorkerName())) {
            logger.info("propagating delete request to all workers");
            for (String worker : affinityManager.getWorkers()) {
                String url = "http://" + worker + ":8081/api/deleteDoc/" + dbName + "/" + collectionName + "/" + docId;
                headers.set("X-Propagated-Request", "true");
                HttpEntity<String> requestEntity = new HttpEntity<>("", headers);
                restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, String.class);
            }
        } else {
            String url = "http://" + affinityName + ":8081/api/deleteDoc/" + dbName + "/" + collectionName + "/" + docId;
            HttpEntity<String> requestEntity = new HttpEntity<>("", headers);
            restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, String.class);
        }
        logger.info("Document deleted successfully.");
        return new APIResponse("Document deleted successfully.", 200);
    }

    private String getDataType(File Schema, String property) {
        JSONObject schema = new JSONObject(readFileAsString(Schema));
        String dataType = schema.getJSONObject("properties").getJSONObject(property).getString("type");
        return dataType;
    }

    @PostMapping("/updateDoc/{db_name}/{collection_name}/{doc_id}/{property_name}/{new_value}")
    @ResponseBody
    public APIResponse updateDoc(
            @PathVariable("db_name") String dbName,
            @PathVariable("collection_name") String collectionName,
            @PathVariable("doc_id") String docId,
            @PathVariable("property_name") String propertyName,
            @PathVariable("new_value") Object newValue,
            @RequestHeader(value = "X-Propagated-Request", defaultValue = "false") boolean propagatedRequest,
            @RequestHeader(value = "X-Username") String username,
            @RequestHeader(value = "X-Token") String token,
            @RequestHeader(value = "X-Old-Value", required = false) String oldValue
    ) {
        logger.info("Updating document with ID " + docId + " in collection " + collectionName + " in database " + dbName
                + (propagatedRequest ? " (propagated)" : "") + ".");

        dbName = dbName.toLowerCase();
        collectionName = collectionName.toLowerCase();
        if (!authenticationService.isAuthenticatedUser(username, token)) {
            logger.info("User is not authorized.");
            return new APIResponse("User is not authorized.", 401);
        }

        File dbDirectory = new File(DATABASES_DIRECTORY + dbName);
        File collectionFile = new File(DATABASES_DIRECTORY + dbName + "/" + collectionName + ".json");

        if (!isDatabaseExists(dbDirectory)) {
            logger.warn("Database does not exist.");
            return new APIResponse("Database does not exist.", 400);
        }

        if (!isCollectionExists(collectionFile)) {
            logger.warn("Collection does not exist.");
            return new APIResponse("Collection does not exist.", 400);
        }

        JSONObject currentObject = dao.getDocument(dbName, collectionName, docId);
        if (currentObject == null) {
            logger.warn("Document with ID " + docId + " not found in collection " + collectionName + ".");
            return new APIResponse("Document with ID " + docId + " not found in collection " + collectionName + ".", 404);
        }

        // Getting the data type from the schema.
        File schemaFile = new File(DATABASES_DIRECTORY + dbName + SCHEMAS_DIRECTORY + collectionName + ".json");
        String dataType = getDataType(schemaFile, propertyName);

        // Casting the new value to the correct data type.
        switch (dataType) {
            case "string":
                newValue = newValue.toString();
                break;
            case "integer":
                newValue = (Integer.parseInt(newValue.toString()));
                break;
            case "number":
                newValue = (Double.parseDouble(newValue.toString()));
                break;
            case "boolean":
                newValue = (Boolean.parseBoolean(newValue.toString()));
                break;
            default:
                return new APIResponse("Unsupported data type.", 400);
        }

        // new json object
        JSONObject newObject = new JSONObject(currentObject.toString());
        newObject.put(propertyName, newValue);

        // check if the new json object is valid
        if (!validJSONObject(newObject.toString(), schemaFile)) {
            logger.warn("Invalid JSON object.");
            return new APIResponse("Invalid JSON object.", 400);
        }

        if (propagatedRequest) {
            if (dao.updateDoc(collectionFile, docId, newObject)) {
                propertyIndexManager.updateDocumentIndexing(dbName, collectionName, newObject);
                return new APIResponse("Document updated successfully.", 200);
            } else {
                return new APIResponse("Document with ID " + docId + " not found in collection " + collectionName + ".", 404);
            }
        }

        // getting the owner affinity port for the document
        String affinityName = affinityManager.getAffinityName(docId);
        if (affinityName == null) {
            return new APIResponse("Document with ID " + docId + " not found in collection " + collectionName + ".", 404);
        }

        // if the owner affinity port is the current port
        if (affinityName.equals(affinityManager.getCurrentWorkerName())) {
            // Check whether the old value matches the value inside the affinity node.
            // (Optimistic locking rules)
            if (oldValue != null) {
                String currentObjectValue = currentObject.get(propertyName).toString();
                if (!oldValue.equals(currentObjectValue)) {
                    return new APIResponse
                            ("your version of this document doesn't match the up-to-date document " +
                                    "(optimistic looking rules violation)", 400);
                }
            }

            logger.info("propagating the update request to all the nodes...");
            // Propagate the request to all the nodes.
            for (String worker : affinityManager.getWorkers()) {
                String url = "http://" + worker + ":8081/api/updateDoc/" + dbName + "/" +
                        collectionName + "/" + docId + "/" + propertyName + "/" + newValue;
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Propagated-Request", "true");
                headers.set("X-Username", BOOTSTRAPPING_NODE_USERNAME);
                headers.set("X-Token", BOOTSTRAPPING_NODE_TOKEN);
                HttpEntity<String> requestEntity = new HttpEntity<>("", headers);
                restTemplate.postForObject(url, requestEntity, String.class);
            }
        } else {
            String url = "http://" + affinityName + ":8081/api/updateDoc/" + dbName + "/" +
                    collectionName + "/" + docId + "/" + propertyName + "/" + newValue;
            // Sending the current version of data to the affinity.
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Old-Value", currentObject.get(propertyName).toString());
            headers.set("X-Username", BOOTSTRAPPING_NODE_USERNAME);
            headers.set("X-Token", BOOTSTRAPPING_NODE_TOKEN);
            HttpEntity<String> requestEntity = new HttpEntity<>("", headers);
            restTemplate.postForObject(url, requestEntity, String.class);
        }

        logger.info("Document updated successfully.");
        return new APIResponse("Document updated successfully.", 200);
    }
}