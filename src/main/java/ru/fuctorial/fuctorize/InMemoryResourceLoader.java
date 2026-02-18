 
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
         
        System.out.println(">>> FUCTORIZE-DEBUG: InMemoryResourceLoader CONSTRUCTOR CALLED. Parent: " + parent.toString());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
         
        System.out.println(">>> FUCTORIZE-DEBUG: InMemoryResourceLoader trying to get resource: " + name);
        if (resourceMap.containsKey(name)) {
             
            System.out.println(">>> FUCTORIZE-DEBUG: Resource '" + name + "' FOUND in memory map!");
            return new ByteArrayInputStream(resourceMap.get(name));
        }

         
        System.out.println(">>> FUCTORIZE-DEBUG: Resource '" + name + "' NOT FOUND. Delegating to parent.");
        return super.getResourceAsStream(name);
    }

    @Override
    public URL findResource(String name) {
         
         
         
         

         
        return super.findResource(name);
    }
}