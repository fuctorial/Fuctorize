package ru.fuctorial.fuctorize.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import ru.fuctorial.fuctorize.utils.LaunchArgumentParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

public class AccountManager {
    private final File accountsFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private List<Account> accounts = new ArrayList<>();
    private Account currentAccount;
    private static Field sessionField;
    private final boolean launchedWithTokens;

    static {
        try {
            for (Field field : Minecraft.class.getDeclaredFields()) {
                if (field.getType().isAssignableFrom(Session.class)) {
                    sessionField = field;
                    sessionField.setAccessible(true);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Fuctorize/AccountManager: Critical error reflecting Session field!");
            e.printStackTrace();
        }
    }

    public AccountManager() {
        File configDir = new File(new File(System.getenv("APPDATA")), "Fuctorize");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        this.accountsFile = new File(configDir, "accounts.json");
        this.launchedWithTokens = LaunchArgumentParser.isLaunchedWithTokens();
        loadAccounts();
        addCurrentSessionIfNotExists();
        this.currentAccount = findAccountByUsername(Minecraft.getMinecraft().getSession().getUsername());
    }

    public boolean isLaunchedWithTokens() {
        return this.launchedWithTokens;
    }

    private void addCurrentSessionIfNotExists() {
        Map<String, String> tokens = LaunchArgumentParser.getDiscoveredTokens();
        String currentUsername = tokens.getOrDefault("username", Minecraft.getMinecraft().getSession().getUsername());

        if (findAccountByUsername(currentUsername) == null) {
            System.out.println("Fuctorize: Adding current session account '" + currentUsername + "' to the list.");
            Account initialAccount = new Account(currentUsername, tokens);
            accounts.add(initialAccount);
            saveAccounts();
        }
    }

    public void loadAccounts() {
        if (!accountsFile.exists()) return;
        try (FileReader reader = new FileReader(accountsFile)) {
            Type type = new TypeToken<List<Account>>() {}.getType();
            accounts = gson.fromJson(reader, type);
            if (accounts == null) accounts = new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveAccounts() {
        try (FileWriter writer = new FileWriter(accountsFile)) {
            gson.toJson(accounts, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void addOrUpdateAccount(Account account) {
        accounts.removeIf(acc -> acc.getUsername().equalsIgnoreCase(account.getUsername()));
        accounts.add(account);
        saveAccounts();
    }

    public void removeAccount(Account account) {
        accounts.remove(account);
        saveAccounts();
    }

    public Account getCurrentAccount() {
        return findAccountByUsername(Minecraft.getMinecraft().getSession().getUsername());
    }

    public Account findAccountByUsername(String username) {
        if (username == null) return null;
        for (Account acc : accounts) {
            if (acc.getUsername().equalsIgnoreCase(username)) {
                return acc;
            }
        }
        return null;
    }

    public boolean login(Account account) {
        if (account == null || account.getUsername() == null || account.getUsername().trim().isEmpty()) {
            System.err.println("Fuctorize/AccountManager: Attempted to log in with an invalid/empty account. Aborting.");
            return false;
        }
        if (sessionField == null) {
            System.err.println("Fuctorize/AccountManager: Session field is null, cannot log in.");
            return false;
        }
        try {
            String username = account.getUsername();
            Map<String, String> tokens = account.getTokens();
            String uuidString = tokens.get("uuid");
            if (uuidString == null || uuidString.isEmpty()) {
                uuidString = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
            }
            String accessToken = tokens.getOrDefault("accessToken", "0");
            Session newSession = new Session(username, uuidString.replace("-", ""), accessToken, "legacy");
            sessionField.set(Minecraft.getMinecraft(), newSession);
            this.currentAccount = account;
            System.out.println("Fuctorize/AccountManager: Session updated to: " + username);
            return true;
        } catch (Exception e) {
            System.err.println("Fuctorize/AccountManager: Failed to set new session for " + account.getUsername());
            e.printStackTrace();
            return false;
        }
    }
}