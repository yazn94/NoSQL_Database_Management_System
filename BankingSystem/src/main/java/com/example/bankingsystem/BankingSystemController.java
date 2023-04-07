package com.example.bankingsystem;


import com.example.bankingsystem.services.NetworkManager;
import jakarta.servlet.http.HttpSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.logging.Logger;

@Controller
public class BankingSystemController {
    private NetworkManager networkManager = NetworkManager.getInstance();
    private Logger logger = Logger.getLogger(BankingSystemController.class.getName());

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("newUser")
    public String newUser() {
        logger.info("Received request to register new user");
        return "new-user";
    }

    @GetMapping("userRegistration")
    public String userRegistration() {
        logger.info("Received request to register new user");
        return "user-registration";
    }

    @GetMapping("adminRegistration")
    public String adminRegistration() {
        logger.info("Received request to register new admin");
        return "admin-registration";
    }

    @PostMapping("registerNewUser")
    public String registerNewUser(@RequestParam String username, Model model) {
        logger.info("Received request to register new user " + username);

        JSONObject jsonObject = new JSONObject(networkManager.registerANewUser(username));
        logger.info("Received user credentials from the database: " + jsonObject.toString());
        String token = jsonObject.getString("token");
        int workerPort = jsonObject.getInt("workerPort");

        model.addAttribute("username", username);
        model.addAttribute("token", token);
        model.addAttribute("workerName", networkManager.getWorkerName(workerPort));

        networkManager.addWorkerPortToToken(token, workerPort);
        return "show-user-credentials";
    }

    @PostMapping("check-user-credentials")
    public String checkUserCredentials(@RequestParam String username, @RequestParam String token,
                                       HttpSession httpSession, Model model) {

        logger.info("Received request to check user credentials");
        model.addAttribute("username", username);
        model.addAttribute("token", token);

        if (networkManager.isAuthorizedUser(username, token)) {
            logger.info("User credentials are correct for " + username);
            int workerPort = networkManager.getWorkerPort(token);

            model.addAttribute("username", username);
            model.addAttribute("token", token);
            model.addAttribute("workerPort", workerPort);

            httpSession.setAttribute("username", username);
            httpSession.setAttribute("token", token);
            httpSession.setAttribute("workerPort", workerPort);

            return "bank-system";
        } else {
            return "registration-failed";
        }
    }

    @PostMapping("check-admin-credentials")
    public String checkAdminCredentials(@RequestParam String username, @RequestParam String token,
                                        HttpSession httpSession, Model model) {

        logger.info("Received request to check admin credentials");
        model.addAttribute("username", username);
        model.addAttribute("token", token);

        if (networkManager.isAuthorizedAdmin(username, token)) {
            logger.info("Admin credentials are correct for " + username);

            model.addAttribute("username", username);
            model.addAttribute("token", token);

            httpSession.setAttribute("username", username);
            httpSession.setAttribute("token", token);

            return "admin-system";
        } else {
            return "registration-failed";
        }
    }


    @GetMapping("CreateNewAccount")
    public String createNewAccount(HttpSession httpSession, Model model) {
        return "create-account-form";
    }

    @PostMapping("storeNewAccountData")
    public String storeNewAccountData(@RequestParam String customerName, @RequestParam String customerPhone,
                                      @RequestParam String customerAddress, @RequestParam int accountBalance,
                                      Model model, HttpSession httpSession) {

        logger.info("Received request to store new account data");
        logger.info("Customer name: " + customerName);
        logger.info("Phone: " + customerPhone);
        logger.info("Address: " + customerAddress);
        logger.info("Balance: " + accountBalance);

        JSONObject customer = new JSONObject();
        customer.put("name", customerName);
        customer.put("phone", customerPhone);
        customer.put("address", customerAddress);
        customer.put("accountBalance", accountBalance);

        logger.info("Customer data JSON: " + customer.toString());

        networkManager.addNewCustomer(customer, httpSession);
        return "bank-system";
    }

    @GetMapping("bankingSystem")
    public String bankingSystem(HttpSession httpSession, Model model) {
        return "bank-system";
    }

    @GetMapping("showCustomersAccounts")
    public String showCustomersAccounts(HttpSession httpSession, Model model) {
        logger.info("Received request to show all customers accounts");
        String customers = networkManager.allCustomers(httpSession);

        JSONObject response = new JSONObject(customers);
        JSONArray customersArray = new JSONArray(response.get("message").toString());
        logger.info("Customers: " + customersArray.toString());

        model.addAttribute("customers", customersArray);
        return "show-customers-accounts";
    }

    @GetMapping("addNewTransaction")
    public String addNewTransaction(HttpSession httpSession, Model model) {
        return "add-new-transaction";
    }

    @PostMapping("storeNewTransaction")
    public String storeNewTransaction(@RequestParam String customerAccountID, @RequestParam String transactionAmount,
                                      Model model, HttpSession httpSession) {

        logger.info("Received request to store new transaction");
        logger.info("Receiver name: " + customerAccountID);
        logger.info("Amount: " + transactionAmount);

        networkManager.makeTransaction(customerAccountID, transactionAmount, httpSession);
        return "bank-system";
    }

    @GetMapping("getCustomer")
    public String getCustomer(HttpSession httpSession, Model model) {
        return "get-customer-id";
    }

    @PostMapping("showCustomer")
    public String showCustomer(@RequestParam String customerAccountID, Model model, HttpSession httpSession) {

        logger.info("Received request to show customer with ID: " + customerAccountID);

        JSONObject customer = networkManager.getCustomerByID(customerAccountID, httpSession);
        logger.info("Customer: " + customer.toString());

        model.addAttribute("customer", customer);
        return "show-customer";
    }

    @GetMapping("getCustomer-transactions")
    public String showCustomerTransactions(Model model, HttpSession httpSession) {
        return "get-customer-id-transactions-form";
    }

    @PostMapping("showTransactions")
    public String showTransactions(@RequestParam String customerAccountID, Model model, HttpSession httpSession) {
        logger.info("Received request to show transactions for customer with ID: " + customerAccountID);

        JSONArray transactions = networkManager.getCustomerTransactions(customerAccountID, httpSession);
        logger.info("Transactions: " + transactions.toString());

        model.addAttribute("transactions", transactions);
        return "show-transactions";
    }

    @GetMapping("remove-user")
    public String removeUser(Model model, HttpSession httpSession) {
        return "remove-user-form";
    }

    @PostMapping("removeUser")
    public String removeUser(@RequestParam String token, Model model, HttpSession httpSession) {
        logger.info("Received request to remove user with token: " + token);
        networkManager.removeUser(token, httpSession);
        return "admin-system";
    }

    @GetMapping("all-users")
    public String allUsers(Model model, HttpSession httpSession) {
        logger.info("Received request to show all users");
        JSONArray users = networkManager.allActiveUsers(httpSession);
        logger.info("Users: " + users.toString());
        model.addAttribute("users", users);
        return "show-all-users";
    }


    @GetMapping("removeCustomer")
    public String removeCustomer(Model model, HttpSession httpSession) {
        return "remove-customer-form";
    }

    @PostMapping("removeCustomer")
    public String removeCustomer(@RequestParam String token, Model model, HttpSession httpSession) {
        logger.info("Received request to remove customer with token: " + token);
        networkManager.removeCustomer(token, httpSession);
        return "bank-system";
    }
}