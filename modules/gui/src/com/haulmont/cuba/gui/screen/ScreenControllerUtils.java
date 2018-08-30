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
import com.haulmont.cuba.gui.components.Frame;
import com.haulmont.cuba.gui.components.Window;
import com.haulmont.cuba.gui.model.ScreenData;
import com.haulmont.cuba.gui.settings.Settings;
import com.haulmont.cuba.gui.util.OperationResult;

/**
 * Internal methods used in WindowManager implementations.
 */
public final class ScreenControllerUtils {

    public static void setWindowId(Screen screen, String id) {
        screen.setId(id);
    }

    public static void setWindow(Screen screen, Window window) {
        screen.setWindow(window);
    }

    public static <E> void fireEvent(Screen screen, Class<E> eventType, E event) {
        screen.fireEvent(eventType, event);
    }

    public static EventHub getEventHub(FrameOwner frameOwner) {
        if (frameOwner instanceof Screen) {
            return ((Screen) frameOwner).getEventHub();
        }
        return ((ScreenFragment) frameOwner).getEventHub();
    }

    public static void setScreenContext(Screen screen, ScreenContext screenContext) {
        screen.setScreenContext(screenContext);
    }

    public static ScreenContext getScreenContext(FrameOwner frameOwner) {
        if (frameOwner instanceof Screen) {
            return ((Screen) frameOwner).getScreenContext();
        }
        return ((ScreenFragment) frameOwner).getScreenContext();
    }

    public static void setScreenData(Screen screen, ScreenData screenData) {
        screen.setScreenData(screenData);
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
}