package com.example.bootstrap;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LoadBalancer {
    private static LoadBalancer instance = new LoadBalancer();
    private List<String> workers = new ArrayList<>();
    private int nextWorkerIndex = 0;

    private LoadBalancer() {
        workers.add("worker1");
        workers.add("worker2");
        workers.add("worker3");
    }

    public static LoadBalancer getInstance() {
        return instance;
    }

    public int getWorkerPort(String workerName) {
        if (workerName.equals("worker1"))
            return 8081;
        else if (workerName.equals("worker2"))
            return 8082;
        else
            return 8083;
    }

    public void passToNextWorker() {
        nextWorkerIndex = (nextWorkerIndex + 1) % workers.size();
    }

    public String getNextWorkerName() {
        String workerName = workers.get(nextWorkerIndex);
        return workerName;
    }

    public int getNextWorkerIndex() {
        return nextWorkerIndex;
    }

    public void setNextWorkerIndex(int nextWorkerIndex) {
        this.nextWorkerIndex = nextWorkerIndex;
    }
}
