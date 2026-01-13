package ru.fuctorial.fuctorize.event;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;

// Add the @Cancelable annotation to allow the event to be cancelled.
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

    // A nested class for the "Button Pressed" event.
    public static class Click extends MouseEvent {
        public Click(int x, int y, int button) {
            super(x, y, button);
        }
    }

    // A nested class for the "Button Released" event.
    public static class Release extends MouseEvent {
        public Release(int x, int y, int button) {
            super(x, y, button);
        }
    }
}