package com.example.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BootstrapApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootstrapApplication.class, args);


        // Starting Up The Cluster
        NetworkManager.getInstance().setUpWorkersNames();
        NetworkManager.getInstance().setFirstWorkerAsAffinity();
    }
}
