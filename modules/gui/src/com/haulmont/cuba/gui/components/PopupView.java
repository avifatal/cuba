/*
 * Copyright (c) 2008-2016 Haulmont.
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

package com.haulmont.cuba.gui.components;

import com.haulmont.bali.events.Subscription;
import com.haulmont.cuba.gui.components.sys.EventHubOwner;

import java.util.EventObject;
import java.util.function.Consumer;

public interface PopupView extends Component.HasCaption, Component.BelongToFrame,
        Component.HasIcon, HasContextHelp {
    String NAME = "popupView";

    /**
     * Set visibility for the popup window.
     *
     * @param popupVisible popup visibility.
     */
    void setPopupVisible(boolean popupVisible);
    /**
     * @return true if popup is visible.
     */
    boolean isPopupVisible();

    /**
     * Set value for the label of component. Value of the label can contain HTML.
     *
     * @param minimizedValue label text.
     */
    void setMinimizedValue(String minimizedValue);
    /**
     * @return value of the label of component.
     */
    String getMinimizedValue();

    /**
     * Set inner content for the popup window.
     *
     * @param popupContent popup component.
     */
    void setPopupContent(Component popupContent);
    /**
     * @return popup content component.
     */
    Component getPopupContent();

    /**
     * Set possibility to close popup window on cursor out.
     *
     * @param hideOnMouseOut popup hide option.
     */
    void setHideOnMouseOut(boolean hideOnMouseOut);
    /**
     * @return true if popup window closes on cursor out.
     */
    boolean isHideOnMouseOut();

    /**
     * Set caption rendering as HTML.
     *
     * @param captionAsHtml true if we want to show caption as HTML.
     */
    void setCaptionAsHtml(boolean captionAsHtml);
    /**
     * @return true if caption is shown as HTML.
     */
    boolean isCaptionAsHtml();

    default Subscription addPopupVisibilityListener(Consumer<PopupVisibilityEvent> listener) {
        return ((EventHubOwner) this).getEventHub().subscribe(PopupVisibilityEvent.class, listener);
    }

    /**
     * @param listener a listener to remove
     * @deprecated Use {@link Subscription} instead
     */
    @Deprecated
    default void removePopupVisibilityListener(Consumer<PopupVisibilityEvent> listener) {
        ((EventHubOwner) this).getEventHub().unsubscribe(PopupVisibilityEvent.class, listener);
    }

    /**
     * This event is received by the PopupVisibilityListeners when the visibility of the popup changes.
     */
    class PopupVisibilityEvent extends EventObject {
        public PopupVisibilityEvent(PopupView popupView) {
            super(popupView);
        }

        @Override
        public PopupView getSource() {
            return (PopupView) super.getSource();
        }

        /**
         * @return popup view
         * @deprecated Use {@link #getSource()}
         */
        @Deprecated
        public PopupView getPopupView() {
            return getSource();
        }

        public boolean isPopupVisible() {
            return getSource().isPopupVisible();
        }
    }
}