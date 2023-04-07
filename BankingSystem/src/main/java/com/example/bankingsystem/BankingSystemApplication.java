package com.example.bankingsystem;

import com.example.bankingsystem.services.NetworkManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Scanner;

@SpringBootApplication
public class BankingSystemApplication {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
//        String dbIP = scanner.nextLine();
//        NetworkManager.getInstance().setDatabaseIP(dbIP);
//

        SpringApplication.run(BankingSystemApplication.class, args);
        NetworkManager.getInstance().buildDatabaseSchema();
    }
}
