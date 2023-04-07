package com.example.worker.DAO;

import com.example.worker.caching.LRUCache;
import com.example.worker.indexing.PropertyIndexManager;
import com.example.worker.model.Schema;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static com.example.worker.services.FileServices.readFileAsString;

public class DAO {
    private final Object lock = new Object(); // lock object for synchronization
    // The name of the collection - all documents inside it.
    private LRUCache<String, JSONArray> cache = new LRUCache<>(1000);
    private PropertyIndexManager propertyIndexManager = PropertyIndexManager.getInstance();

    // done
    public synchronized void clearCollectionCaching(File collectionFile) {
        cache.remove(collectionFile.getName());
    }

    // done
    public void clearDBCache(File dbDirectory) {
        File[] files = dbDirectory.listFiles();
        for (File collectionFile : files) {
            if (collectionFile.getName().equals("schemas"))
                continue;
            clearCollectionCaching(collectionFile);
        }
    }

    // done
    public String allCollections(File dbDirectory) {
        File[] files = dbDirectory.listFiles();
        StringBuilder collections = new StringBuilder();

        for (int i = 0; i < files.length; i++) {
            String current = files[i].getName();
            if (current.equals("schemas"))
                continue;
            // Getting the name without the extension
            String[] name = current.split("\\.");
            collections.append(name[0]);
            if (i != files.length - 1 && files.length > 2)
                collections.append(", ");
        }
        return collections.toString();
    }

    // done
    public synchronized void createCollection(File collectionFile, File schemaFile, Schema schema) {
        try {
            collectionFile.createNewFile();
            FileWriter writer = new FileWriter(collectionFile, true);
            writer.write("[]");
            writer.close();

            schemaFile.createNewFile();
            schema.setAdditionalProperties(false);
            writer = new FileWriter(schemaFile);
            writer.write(new Gson().toJson(schema));
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    // done
    public JSONObject getDocument(String dbName, String collectionName, String id) {

        // trying to get it from the indexing first.
        JSONArray jsonArray = propertyIndexManager.getMatchingDocs(dbName, collectionName, "_id", id);
        if (jsonArray.length() != 0) {
            JSONObject ret = (JSONObject) jsonArray.get(0);
            return ret;
        }

        File collectionFile = new File("databases/" + dbName + "/" + collectionName + ".json");
        JSONArray data = getFilteredData(collectionFile, "_id", id);

        if (data.length() == 0)
            return new JSONObject("{}");

        return (JSONObject) data.get(0); // for sure there is only one element matching the id.
    }


    // done
    public boolean updateDoc(File collectionFile, String docId, JSONObject newObj) {

        JSONArray jsonArray = new JSONArray(readFileAsString(collectionFile));
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            if (obj.getString("_id").equals(docId)) {
                jsonArray.put(i, newObj);
                try {
                    synchronized (lock) {
                        FileWriter writer = new FileWriter(collectionFile, false);
                        writer.write(jsonArray.toString());
                        writer.close();
                        cache.put(collectionFile.getName(), jsonArray);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return true;
            }
        }
        return false;
    }

    // done
    public synchronized void addDocument(File collectionFile, String jsonOBJ) {
        JSONArray jsonArray = new JSONArray(readFileAsString(collectionFile));
        JSONObject jsonObject = new JSONObject(jsonOBJ);
        jsonArray.put(jsonObject);

        try {
            FileWriter writer = new FileWriter(collectionFile, false);
            writer.write(jsonArray.toString());
            writer.close();
            cache.put(collectionFile.getName(), jsonArray);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // done
    public String getAllDocs(File collectionFile) {
        JSONArray jsonArray = cache.get(collectionFile.getName());
        if (jsonArray != null)
            return jsonArray.toString();

        try {
            return FileUtils.readFileToString(collectionFile, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // done
    public JSONArray getFilteredData(File collectionFile, String attributeName, String attributeValue) {
        // accessing the cache first.
        JSONArray collectionData = cache.get(collectionFile.getName());

        if (collectionData == null) {
            collectionData = new JSONArray(readFileAsString(collectionFile));
        }

        JSONArray filteredArray = new JSONArray();
        for (int i = 0; i < collectionData.length(); i++) {
            JSONObject obj = collectionData.getJSONObject(i);
            if (obj.has(attributeName) && obj.get(attributeName).toString().equals(attributeValue)) {
                filteredArray.put(obj);
            }
        }
        // triggering the caching for the current collection.
        synchronized (lock) {
            cache.put(collectionFile.getName(), collectionData);
        }
        return filteredArray;
    }


    // done
    public boolean deleteDoc(String docContent, File collectionFile, String docId) {

        JSONArray jsonArray = cache.get(collectionFile.getName());
        if (jsonArray == null) {
            jsonArray = new JSONArray(readFileAsString(collectionFile));
        }

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            if (obj.getString("_id").equals(docId)) {
                jsonArray.remove(i);
                try {
                    synchronized (lock) {
                        FileWriter writer = new FileWriter(collectionFile, false);
                        writer.write(jsonArray.toString());
                        writer.close();
                        cache.put(collectionFile.getName(), jsonArray);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return true;
            }
        }
        return false;
    }

    // done
    public String listDbs(File dbDirectory) {
        String[] databases = dbDirectory.list();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < databases.length; i++) {
            sb.append(databases[i]);
            if (i != databases.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}
