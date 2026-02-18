package ru.fuctorial.fuctorize.event;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;

 
@Cancelable
public class MouseEvent extends Event {
    public final int x;
    public final int y;
    public final int button;

    public MouseEvent(int x, int y, int button) {
        this.x = x;
        this.y = y;
        this.button = button;
    }

     
    public static class Click extends MouseEvent {
        public Click(int x, int y, int button) {
            super(x, y, button);
        }
    }

     
    public static class Release extends MouseEvent {
        public Release(int x, int y, int button) {
            super(x, y, button);
        }
    }
}