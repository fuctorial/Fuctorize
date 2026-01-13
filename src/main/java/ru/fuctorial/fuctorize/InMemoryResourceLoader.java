// Файл: ru/fuctorial/fuctorize/InMemoryResourceLoader.java
package ru.fuctorial.fuctorize;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

public class InMemoryResourceLoader extends URLClassLoader {

    private final Map<String, byte[]> resourceMap;

    public InMemoryResourceLoader(Map<String, byte[]> resourceMap, ClassLoader parent) {
        super(new URL[0], parent);
        this.resourceMap = resourceMap;
        // === ЛОГ 1: Конструктор вызван ===
        System.out.println(">>> FUCTORIZE-DEBUG: InMemoryResourceLoader CONSTRUCTOR CALLED. Parent: " + parent.toString());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        // === ЛОГ 2: Метод вызван ===
        System.out.println(">>> FUCTORIZE-DEBUG: InMemoryResourceLoader trying to get resource: " + name);
        if (resourceMap.containsKey(name)) {
            // === ЛОГ 3: Ресурс найден в карте ===
            System.out.println(">>> FUCTORIZE-DEBUG: Resource '" + name + "' FOUND in memory map!");
            return new ByteArrayInputStream(resourceMap.get(name));
        }

        // === ЛОГ 4: Ресурс не найден, передаем родителю ===
        System.out.println(">>> FUCTORIZE-DEBUG: Resource '" + name + "' NOT FOUND. Delegating to parent.");
        return super.getResourceAsStream(name);
    }

    @Override
    public URL findResource(String name) {
        // Этот метод реализовать сложнее, т.к. нужно создавать URL для данных в памяти.
        // Для большинства случаев достаточно переопределить getResourceAsStream.
        // Если фреймворку нужен именно URL, придется реализовывать кастомный URLStreamHandler.
        // Но сначала попробуем без этого.

        // Передаем запрос родителю
        return super.findResource(name);
    }
}