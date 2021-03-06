/*
 * Copyright (c) 2008-2018 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cuba.gui.screen;

import com.haulmont.bali.events.EventHub;
import com.haulmont.cuba.gui.components.Fragment;
import com.haulmont.cuba.gui.components.Frame;
import com.haulmont.cuba.gui.components.Window;
import com.haulmont.cuba.gui.model.ScreenData;
import com.haulmont.cuba.gui.settings.Settings;
import com.haulmont.cuba.gui.util.OperationResult;
import org.springframework.context.ApplicationListener;

import java.util.Collections;
import java.util.List;

/**
 * Internal methods used in Screens and Fragments implementations.
 */
public final class UiControllerUtils {

    public static void setWindowId(FrameOwner screen, String id) {
        if (screen instanceof Screen) {
            ((Screen) screen).setId(id);
        } else if (screen instanceof ScreenFragment) {
            ((ScreenFragment) screen).setId(id);
        }
    }

    public static void setFrame(FrameOwner screen, Frame window) {
        if (screen instanceof Screen) {
            ((Screen) screen).setWindow((Window) window);
        } else if (screen instanceof ScreenFragment) {
            ((ScreenFragment) screen).setFragment((Fragment) window);
        }
    }

    public static <E> void fireEvent(FrameOwner screen, Class<E> eventType, E event) {
        if (screen instanceof Screen) {
            ((Screen) screen).fireEvent(eventType, event);
        } else if (screen instanceof ScreenFragment) {
            ((ScreenFragment) screen).fireEvent(eventType, event);
        }
    }

    public static EventHub getEventHub(FrameOwner frameOwner) {
        if (frameOwner instanceof Screen) {
            return ((Screen) frameOwner).getEventHub();
        }
        return ((ScreenFragment) frameOwner).getEventHub();
    }

    public static void setScreenContext(FrameOwner screen, ScreenContext screenContext) {
        if (screen instanceof Screen) {
            ((Screen) screen).setScreenContext(screenContext);
        } else if (screen instanceof ScreenFragment) {
            ((ScreenFragment) screen).setScreenContext(screenContext);
        }
    }

    public static ScreenContext getScreenContext(FrameOwner frameOwner) {
        if (frameOwner instanceof Screen) {
            return ((Screen) frameOwner).getScreenContext();
        }
        return ((ScreenFragment) frameOwner).getScreenContext();
    }

    public static void setScreenData(FrameOwner screen, ScreenData screenData) {
        if (screen instanceof Screen) {
            ((Screen) screen).setScreenData(screenData);
        } else if (screen instanceof ScreenFragment) {
            ((ScreenFragment) screen).setScreenData(screenData);
        }
    }

    public static void applySettings(Screen screen, Settings settings) {
        screen.applySettings(settings);
    }

    public static void saveSettings(Screen screen) {
        screen.saveSettings();
    }

    public static void deleteSettings(Screen screen) {
        screen.deleteSettings();
    }

    public static Settings getSettings(Screen screen) {
        return screen.getSettings();
    }

    public static ScreenData getScreenData(FrameOwner frameOwner) {
        if (frameOwner instanceof Screen) {
            return ((Screen) frameOwner).getScreenData();
        }
        return ((ScreenFragment) frameOwner).getScreenData();
    }

    public static Frame getFrame(FrameOwner frameOwner) {
        if (frameOwner instanceof Screen) {
            return ((Screen) frameOwner).getWindow();
        }
        return ((ScreenFragment) frameOwner).getFragment();
    }

    public static OperationResult commitChanges(Screen screen) {
        return screen.commitChanges();
    }

    public static void setUiEventListeners(FrameOwner frameOwner, List<ApplicationListener> listeners) {
        if (frameOwner instanceof Screen) {
            ((Screen) frameOwner).setUiEventListeners(listeners);
        } else if (frameOwner instanceof ScreenFragment) {
            ((ScreenFragment) frameOwner).setUiEventListeners(listeners);
        }
    }

    public static List<ApplicationListener> getUiEventListeners(FrameOwner frameOwner) {
        if (frameOwner instanceof Screen) {
            return ((Screen) frameOwner).getUiEventListeners();
        } else if (frameOwner instanceof ScreenFragment) {
            return ((ScreenFragment) frameOwner).getUiEventListeners();
        }
        return Collections.emptyList();
    }
}