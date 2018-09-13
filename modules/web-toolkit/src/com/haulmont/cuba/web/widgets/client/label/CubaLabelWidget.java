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

package com.haulmont.cuba.web.widgets.client.label;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.vaadin.client.ui.VLabel;

/**
 * @author glebfox
 */
public class CubaLabelWidget extends VLabel {

    protected ContextHelpClickHandler contextHelpClickHandler;
    protected Element contextHelpIcon;

    @Override
    public void onBrowserEvent(Event event) {
        super.onBrowserEvent(event);

        if (DOM.eventGetType(event) == Event.ONCLICK
                && contextHelpClickHandler != null
                && DOM.eventGetTarget(event) == contextHelpIcon) {
            contextHelpClickHandler.onClick(event);
        }
    }

    interface ContextHelpClickHandler {
        void onClick(Event event);
    }
}
