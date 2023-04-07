package com.example.worker.indexing;

import java.util.Objects;

public class PropertyIndex {
    private String dbName, collectionName, propertyName, propertyValue;

    public PropertyIndex(String dbName, String collectionName, String propertyName, String propertyValue) {
        this.dbName = dbName;
        this.collectionName = collectionName;
        this.propertyName = propertyName;
        this.propertyValue = propertyValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyIndex that = (PropertyIndex) o;
        return Objects.equals(dbName, that.dbName) && Objects.equals(collectionName, that.collectionName) && Objects.equals(propertyName, that.propertyName) && Objects.equals(propertyValue, that.propertyValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbName, collectionName, propertyName, propertyValue);
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyValue() {
        return propertyValue;
    }

    public void setPropertyValue(String propertyValue) {
        this.propertyValue = propertyValue;
    }
}
