 
package ru.fuctorial.fuctorize.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Account {
    private final UUID id;
    private String username;
    private Map<String, String> tokens;

    public Account() {
        this.id = UUID.randomUUID();
        this.tokens = new HashMap<>();
    }

    public Account(String username, Map<String, String> initialTokens) {
        this();
        this.username = username;
         
        this.tokens = new HashMap<>(initialTokens);
        this.tokens.remove("username");
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Map<String, String> getTokens() {
        return tokens;
    }

    public String getToken(String key) {
        return tokens.get(key);
    }

    public void setToken(String key, String value) {
         
         
        if (key != null && value != null) {
            tokens.put(key, value);
        }
    }
}