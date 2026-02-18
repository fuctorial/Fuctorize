package ru.fuctorial.fuctorize.module.settings;

import ru.fuctorial.fuctorize.module.Module;


 




public class Setting {
    public String name;
    private Module parent;  

    public Module getParent() {  
        return parent;
    }

    public void setParent(Module parent) {  
        this.parent = parent;
    }
}