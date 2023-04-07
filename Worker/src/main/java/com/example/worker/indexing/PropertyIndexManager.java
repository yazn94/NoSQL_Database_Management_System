package com.example.worker.indexing;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.worker.services.FileServices.DATABASES_DIRECTORY;
import static com.example.worker.services.FileServices.readFileAsString;

// Singleton class for managing property indexing.
public class PropertyIndexManager {
    // Singleton instance.
    private static PropertyIndexManager instance = new PropertyIndexManager();

    // Map for storing property index values and associated document arrays.
    private static Map<PropertyIndex, JSONArray> propertyIndexMap = new ConcurrentHashMap<>();

    private PropertyIndexManager() {
    }

    public static void buildInitialIndexing() {
        File dbsDir = new File(DATABASES_DIRECTORY);
        for (String dbName : dbsDir.list()) {
            File dbDir = new File(DATABASES_DIRECTORY + dbName);
            for (String collectionName : dbDir.list()) {
                if (collectionName.equals("schemas")) {
                    continue;
                }
                File collectionFile = new File(DATABASES_DIRECTORY + dbName + "/" + collectionName.toLowerCase());
                JSONArray collectionArray = new JSONArray(readFileAsString(collectionFile));
                for (int i = 0; i < collectionArray.length(); i++) {
                    JSONObject currentDoc = collectionArray.getJSONObject(i);
                    String collectionWithOutExtension = collectionName.toLowerCase().substring(0, collectionName.toLowerCase().length() - 5);
                    instance.indexingNewObject(dbName.toLowerCase(), collectionWithOutExtension, currentDoc);
                }
            }
        }
    }

    public static PropertyIndexManager getInstance() {
        return instance;
    }


    public synchronized void updateDocumentIndexing(String dbName, String collectionName, JSONObject updatedDoc) {
        if (updatedDoc == null) {
            return;
        }
        // getting the json object with the same id.
        JSONObject docToBeRemoved = getMatchingDocs(dbName.toLowerCase(), collectionName.toLowerCase(), "_id", updatedDoc.get("_id")).getJSONObject(0);

        // removing the old indexing.
        clearDocumentIndexing(dbName.toLowerCase(), collectionName.toLowerCase(), updatedDoc.get("_id").toString());

        // indexing the new object.
        indexingNewObject(dbName.toLowerCase(), collectionName.toLowerCase(), updatedDoc);
    }


    public synchronized void indexingNewObject(String dbName, String collectionName, JSONObject addedOBJ) {
        if (addedOBJ == null) {
            return;
        }

        for (String key : addedOBJ.keySet()) {
            Object valueObj = addedOBJ.get(key);
            String value;
            if (valueObj instanceof String) {
                value = (String) valueObj;
            } else {
                value = valueObj.toString();
            }

            PropertyIndex propertyIndex = new PropertyIndex(dbName.toLowerCase(), collectionName.toLowerCase(), key, value);
            JSONArray documentArray;

            if (propertyIndexMap.containsKey(propertyIndex)) {
                documentArray = propertyIndexMap.get(propertyIndex);
            } else {
                documentArray = new JSONArray();
            }

            documentArray.put(addedOBJ);
            propertyIndexMap.put(propertyIndex, documentArray);
        }
    }

    public JSONArray getMatchingDocs(String dbName, String collectionName, String key, Object value) {
        PropertyIndex propertyIndex = new PropertyIndex(dbName.toLowerCase(), collectionName.toLowerCase(), key, value.toString());
        return propertyIndexMap.get(propertyIndex);
    }

    public synchronized void clearDocumentIndexing(String dbName, String collectionName, String docId) {
        // getting the json object with the same id.
        JSONObject docToBeRemoved = getMatchingDocs(dbName.toLowerCase(), collectionName.toLowerCase(), "_id", docId).getJSONObject(0);

        for (String key : docToBeRemoved.keySet()) {
            Object valueObj = docToBeRemoved.get(key);
            String value;
            if (valueObj instanceof String) {
                value = (String) valueObj;
            } else {
                value = valueObj.toString();
            }

            PropertyIndex propertyIndex = new PropertyIndex(dbName.toLowerCase(), collectionName.toLowerCase(), key, value);
            JSONArray documentArray = propertyIndexMap.get(propertyIndex);

            for (int i = 0; i < documentArray.length(); i++) {
                JSONObject currentDoc = documentArray.getJSONObject(i);
                if (currentDoc.equals(docToBeRemoved)) {
                    documentArray.remove(i);
                    break;
                }
            }
        }
    }


    public synchronized void clearDBIndexing(String dbName) {
        List<PropertyIndex> toBeRemoved = new ArrayList<>();
        for (PropertyIndex propertyIndex : propertyIndexMap.keySet()) {
            if (propertyIndex.getDbName().equals(dbName.toLowerCase())) {
                toBeRemoved.add(propertyIndex);
            }
        }
        for (PropertyIndex propertyIndex : toBeRemoved) {
            propertyIndexMap.remove(propertyIndex);
        }
    }

    public synchronized void clearCollectionIndexing(String dbName, String collectionName) {
        List<PropertyIndex> toBeRemoved = new ArrayList<>();

        for (PropertyIndex propertyIndex : propertyIndexMap.keySet()) {
            if (propertyIndex.getDbName().equals(dbName.toLowerCase()) &&
                    propertyIndex.getCollectionName().equals(collectionName.toLowerCase())
            ) {
                toBeRemoved.add(propertyIndex);
            }
        }

        for (PropertyIndex propertyIndex : toBeRemoved) {
            propertyIndexMap.remove(propertyIndex);
        }
    }
}
