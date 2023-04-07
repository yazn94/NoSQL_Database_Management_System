package com.example.worker;

import com.example.worker.indexing.PropertyIndexManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WorkerApplication {

    @Value("${server.port}")
    private String serverPort;

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);

        // Building the indexing for the old data.
        PropertyIndexManager.buildInitialIndexing();
    }
}
