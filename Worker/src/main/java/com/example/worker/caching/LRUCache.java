package com.example.worker.caching;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LRUCache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> cache;
    private Node<K, V> head;

    private Node<K, V> tail;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new ConcurrentHashMap<>();
    }

    public V get(K key) {
        Node<K, V> node = cache.get(key);
        if (node == null) {
            return null;
        }
        moveToHead(node);
        return node.value;
    }

    public void clearDbCaching(String dbName) {
    }

    public synchronized void put(K key, V value) {
        Node<K, V> node = cache.get(key);
        if (node == null) {
            node = new Node<>(key, value);
            if (cache.size() == capacity) {
                cache.remove(tail.key);
                removeTail();
            }
            addToHead(node);
            cache.put(key, node);
        } else {
            node.value = value;
            moveToHead(node);
        }
    }

    private synchronized void addToHead(Node<K, V> node) {
        node.prev = null;
        node.next = head;
        if (head != null) {
            head.prev = node;
        }
        head = node;
        if (tail == null) {
            tail = node;
        }
    }

    private synchronized void removeNode(Node<K, V> node) {
        if (node.prev != null) {
            node.prev.next = node.next;
        } else {
            head = node.next;
        }
        if (node.next != null) {
            node.next.prev = node.prev;
        } else {
            tail = node.prev;
        }
    }

    private synchronized void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }

    private synchronized void removeTail() {
        if (tail != null) {
            tail = tail.prev;
            if (tail != null) {
                tail.next = null;
            } else {
                head = null;
            }
        }
    }

    public synchronized void remove(K key) {
        if (cache.containsKey(key)) {
            Node<K, V> currentNode = cache.get(key);
            cache.remove(key);
            removeNode(currentNode);
        }
    }

    private static class Node<K, V> {
        private final K key;
        private V value;
        private Node<K, V> prev;
        private Node<K, V> next;

        public Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}

