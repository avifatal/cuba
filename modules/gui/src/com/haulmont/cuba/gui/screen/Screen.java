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
import com.haulmont.bali.events.Subscription;
import com.haulmont.bali.events.TriggerOnce;
import com.haulmont.cuba.client.ClientConfig;
import com.haulmont.cuba.core.global.BeanLocator;
import com.haulmont.cuba.core.global.Configuration;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.gui.ComponentsHelper;
import com.haulmont.cuba.gui.Dialogs.MessageType;
import com.haulmont.cuba.gui.Notifications;
import com.haulmont.cuba.gui.Notifications.NotificationType;
import com.haulmont.cuba.gui.Screens;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.actions.BaseAction;
import com.haulmont.cuba.gui.components.sys.WindowImplementation;
import com.haulmont.cuba.gui.icons.CubaIcon;
import com.haulmont.cuba.gui.icons.Icons;
import com.haulmont.cuba.gui.model.ScreenData;
import com.haulmont.cuba.gui.presentations.Presentations;
import com.haulmont.cuba.gui.settings.Settings;
import com.haulmont.cuba.gui.util.OperationResult;
import com.haulmont.cuba.gui.util.UnknownOperationResult;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;

import javax.inject.Inject;
import java.util.Collection;
import java.util.EventObject;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.haulmont.bali.util.Preconditions.checkNotNullArgument;
import static com.haulmont.cuba.gui.ComponentsHelper.walkComponents;

/**
 * Base class for all screen controllers.
 *
 * @see Window
 */
public abstract class Screen implements FrameOwner {

    private String id;

    private ScreenContext screenContext;

    private ScreenData screenData;

    private Window window;

    private Settings settings;

    private EventHub eventHub = new EventHub();

    private BeanLocator beanLocator;

    // Global event listeners
    private List<ApplicationListener> uiEventListeners;

    protected BeanLocator getBeanLocator() {
        return beanLocator;
    }

    @Inject
    protected void setBeanLocator(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    protected EventHub getEventHub() {
        return eventHub;
    }

    public String getId() {
        return id;
    }

    /**
     * JavaDoc
     *
     * @param id
     */
    protected void setId(String id) {
        this.id = id;
    }

    @Override
    public ScreenContext getScreenContext() {
        return screenContext;
    }

    protected void setScreenContext(ScreenContext screenContext) {
        this.screenContext = screenContext;
    }

    protected ScreenData getScreenData() {
        return screenData;
    }

    protected void setScreenData(ScreenData data) {
        this.screenData = data;
    }

    protected <E> void fireEvent(Class<E> eventType, E event) {
        eventHub.publish(eventType, event);
    }

    public Window getWindow() {
        return window;
    }

    protected void setWindow(Window window) {
        checkNotNullArgument(window);

        if (this.window != null) {
            throw new IllegalStateException("Screen already has Window");
        }
        this.window = window;
    }

    protected List<ApplicationListener> getUiEventListeners() {
        return uiEventListeners;
    }

    protected void setUiEventListeners(List<ApplicationListener> listeners) {
        this.uiEventListeners = listeners;

        if (listeners != null && !listeners.isEmpty()) {
            ((WindowImplementation) this.window).initUiEventListeners();
        }
    }

    /**
     * JavaDoc
     *
     * @param listener
     * @return
     */
    protected Subscription addInitListener(Consumer<InitEvent> listener) {
        return eventHub.subscribe(InitEvent.class, listener);
    }

    /**
     * JavaDoc
     *
     * @param listener
     * @return
     */
    protected Subscription addAfterInitListener(Consumer<AfterInitEvent> listener) {
        return eventHub.subscribe(AfterInitEvent.class, listener);
    }

    /**
     * JavaDoc
     *
     * @param listener
     * @return
     */
    protected Subscription addBeforeCloseListener(Consumer<BeforeCloseEvent> listener) {
        return eventHub.subscribe(BeforeCloseEvent.class, listener);
    }

    /**
     * JavaDoc
     *
     * @param listener
     * @return
     */
    protected Subscription addBeforeShowListener(Consumer<BeforeShowEvent> listener) {
        return eventHub.subscribe(BeforeShowEvent.class, listener);
    }

    /**
     * JavaDoc
     *
     * @param listener
     * @return
     */
    protected Subscription addAfterShowListener(Consumer<AfterShowEvent> listener) {
        return eventHub.subscribe(AfterShowEvent.class, listener);
    }

    /**
     * JavaDoc
     *
     * @param listener
     * @return
     */
    protected Subscription addCloseTriggeredEvent(Consumer<CloseTriggeredEvent> listener) {
        return eventHub.subscribe(CloseTriggeredEvent.class, listener);
    }

    /**
     * JavaDoc
     *
     * @param listener listener
     * @return
     */
    public Subscription addAfterCloseListener(Consumer<AfterCloseEvent> listener) {
        return eventHub.subscribe(AfterCloseEvent.class, listener);
    }

    protected OperationResult showUnsavedChangesDialog(CloseAction closeAction) {
        UnknownOperationResult result = new UnknownOperationResult();
        Messages messages = beanLocator.get(Messages.NAME);

        screenContext.getDialogs().createOptionDialog()
                .setCaption(messages.getMainMessage("closeUnsaved.caption"))
                .setMessage(messages.getMainMessage("closeUnsaved"))
                .setType(MessageType.WARNING)
                .setActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> {
                                    closeWithDiscard()
                                            .then(result::success)
                                            .otherwise(result::fail);
                                }),
                        new DialogAction(DialogAction.Type.NO, Action.Status.PRIMARY)
                                .withHandler(e -> {
                                    ComponentsHelper.focusChildComponent(getWindow());

                                    result.fail();
                                })
                )
                .show();

        return result;
    }

    protected OperationResult showSaveConfirmationDialog(CloseAction closeAction) {
        UnknownOperationResult result = new UnknownOperationResult();
        Messages messages = beanLocator.get(Messages.NAME);

        Icons icons = beanLocator.get(Icons.NAME);

        screenContext.getDialogs().createOptionDialog()
                .setCaption(messages.getMainMessage("closeUnsaved.caption"))
                .setMessage(messages.getMainMessage("saveUnsaved"))
                .setActions(
                        new DialogAction(DialogAction.Type.OK, Action.Status.PRIMARY)
                                .withCaption(messages.getMainMessage("closeUnsaved.save"))
                                .withHandler(e -> {
                                    closeWithCommit()
                                            .then(result::success)
                                            .otherwise(result::fail);
                                }),
                        new BaseAction("discard")
                                .withIcon(icons.get(CubaIcon.DIALOG_CANCEL))
                                .withCaption(messages.getMainMessage("closeUnsaved.discard"))
                                .withHandler(e -> {
                                    closeWithDiscard()
                                            .then(result::success)
                                            .otherwise(result::fail);
                                }),
                        new DialogAction(DialogAction.Type.CANCEL)
                                .withIcon(null)
                                .withHandler(e -> {
                                    ComponentsHelper.focusChildComponent(getWindow());

                                    result.fail();
                                })
                )
                .show();

        return result;
    }

    /**
     * Convenient method to show the screen.
     *
     * @see Screens#show(Screen)
     */
    public void show() {
        getScreenContext().getScreens().show(this);
    }

    /**
     * JavaDoc
     *
     * @param action close action
     * @return result of operation
     */
    public OperationResult close(CloseAction action) {
        CloseTriggeredEvent closeTriggeredEvent = new CloseTriggeredEvent(this, action);
        fireEvent(CloseTriggeredEvent.class, closeTriggeredEvent);
        if (closeTriggeredEvent.isClosePrevented()) {
            return OperationResult.fail();
        }

        if (action.isCheckForUnsavedChanges() && hasUnsavedChanges()) {
            // todo extract to Dialogs bean
            Configuration configuration = beanLocator.get(Configuration.NAME);
            ClientConfig clientConfig = configuration.getConfig(ClientConfig.class);

            if (clientConfig.getUseSaveConfirmation()) {
                return showSaveConfirmationDialog(action);
            } else {
                return showUnsavedChangesDialog(action);
            }
        }

        BeforeCloseEvent beforeCloseEvent = new BeforeCloseEvent(this, action);
        fireEvent(BeforeCloseEvent.class, beforeCloseEvent);
        if (beforeCloseEvent.isClosePrevented()) {
            return OperationResult.fail();
        }

        // save settings right before removing
        if (isSaveSettingsOnClose(action)) {
            saveSettings();
        }

        screenContext.getScreens().remove(this);

        AfterCloseEvent afterCloseEvent = new AfterCloseEvent(this, action);
        fireEvent(AfterCloseEvent.class, afterCloseEvent);

        return OperationResult.success();
    }

    protected boolean isSaveSettingsOnClose(@SuppressWarnings("unused") CloseAction action) {
        Configuration configuration = beanLocator.get(Configuration.NAME);
        ClientConfig clientConfig = configuration.getConfig(ClientConfig.class);
        return !clientConfig.getManualScreenSettingsSaving();
    }

    /**
     * @return if the screen has unsaved changes
     */
    public boolean hasUnsavedChanges() {
        return false;
    }

    /**
     * JavaDoc
     *
     * @return
     */
    public OperationResult closeWithCommit() {
        return commitChanges()
                .compose(() -> close(WINDOW_COMMIT_AND_CLOSE_ACTION));
    }

    /**
     * JavaDoc
     */
    protected OperationResult commitChanges() {
        return OperationResult.success();
    }

    /**
     * JavaDoc
     */
    public OperationResult closeWithDiscard() {
        return close(WINDOW_DISCARD_AND_CLOSE_ACTION);
    }

    /**
     * JavaDoc
     */
    protected Settings getSettings() {
        return settings;
    }

    /**
     * JavaDoc
     */
    protected void saveSettings() {
        if (settings != null) {
            walkComponents(
                    window,
                    (component, name) -> {
                        if (component.getId() != null
                                && component instanceof HasSettings) {
                            LoggerFactory.getLogger(Screen.class)
                                    .trace("Saving settings for {} : {}", name, component);

                            Element e = settings.get(name);
                            boolean modified = ((HasSettings) component).saveSettings(e);

                            if (component instanceof HasPresentations
                                    && ((HasPresentations) component).isUsePresentations()) {
                                Object def = ((HasPresentations) component).getDefaultPresentationId();
                                e.addAttribute("presentation", def != null ? def.toString() : "");
                                Presentations presentations = ((HasPresentations) component).getPresentations();
                                if (presentations != null) {
                                    presentations.commit();
                                }
                            }
                            settings.setModified(modified);
                        }
                    }
            );
            settings.commit();
        }
    }

    /**
     * JavaDoc
     *
     * @param settings
     */
    protected void applySettings(Settings settings) {
        this.settings = settings;

        walkComponents(
                window,
                (component, name) -> {
                    if (component.getId() != null
                            && component instanceof HasSettings) {
                        LoggerFactory.getLogger(Screen.class)
                                .trace("Applying settings for {} : {} ", name, component);

                        Element e = this.settings.get(name);
                        ((HasSettings) component).applySettings(e);

                        if (component instanceof HasPresentations
                                && e.attributeValue("presentation") != null) {
                            String def = e.attributeValue("presentation");
                            if (!StringUtils.isEmpty(def)) {
                                UUID defaultId = UUID.fromString(def);
                                ((HasPresentations) component).applyPresentationAsDefault(defaultId);
                            }
                        }
                    }
                }
        );
    }

    /**
     * JavaDoc
     */
    protected void deleteSettings() {
        settings.delete();
    }

    /**
     * JavaDoc
     *
     * @return validation errors
     */
    protected ValidationErrors getValidationErrors() {
        return getUiValidationErrors();
    }

    protected ValidationErrors getUiValidationErrors() {
        ValidationErrors errors = new ValidationErrors();

        Collection<Component> components = ComponentsHelper.getComponents(getWindow());
        for (Component component : components) {
            if (component instanceof Validatable) {
                Validatable validatable = (Validatable) component;
                if (validatable.isValidateOnCommit()) {
                    try {
                        validatable.validate();
                    } catch (ValidationException e) {
                        Logger log = LoggerFactory.getLogger(Screen.class);

                        if (log.isTraceEnabled()) {
                            log.trace("Validation failed", e);
                        } else if (log.isDebugEnabled()) {
                            log.debug("Validation failed: " + e);
                        }

                        ComponentsHelper.fillErrorMessages(validatable, e, errors);
                    }
                }
            }
        }
        return errors;
    }

    /**
     * Show validation errors alert. Can be overridden in subclasses.
     *
     * @param errors the list of validation errors. Caller fills it by errors found during the default validation.
     */
    protected void showValidationErrors(ValidationErrors errors) {
        StringBuilder buffer = new StringBuilder();
        for (ValidationErrors.Item error : errors.getAll()) {
            buffer.append(error.description).append("\n");
        }

        Configuration configuration = getBeanLocator().get(Configuration.NAME);
        ClientConfig clientConfig = configuration.getConfig(ClientConfig.class);

        String validationNotificationType = clientConfig.getValidationNotificationType();
        if (validationNotificationType.endsWith("_HTML")) {
            // HTML validation notification types are not supported
            validationNotificationType = validationNotificationType.replace("_HTML", "");
        }

        Messages messages = getBeanLocator().get(Messages.NAME);
        Notifications notifications = getScreenContext().getNotifications();

        notifications.create()
                .setType(NotificationType.valueOf(validationNotificationType))
                .setCaption(messages.getMainMessage("validationFail.caption"))
                .setDescription(buffer.toString())
                .show();
    }

    protected void focusProblemComponent(ValidationErrors errors) {
        com.haulmont.cuba.gui.components.Component component = null;
        if (!errors.getAll().isEmpty()) {
            component = errors.getAll().get(0).component;
        }
        if (component != null) {
            ComponentsHelper.focusComponent(component);
        }
    }

    /**
     * JavaDoc
     */
    @TriggerOnce
    public static class InitEvent extends EventObject {
        protected final ScreenOptions options;

        public InitEvent(Screen source, ScreenOptions options) {
            super(source);
            this.options = options;
        }

        @Override
        public Screen getSource() {
            return (Screen) super.getSource();
        }

        public ScreenOptions getOptions() {
            return options;
        }
    }

    /**
     * JavaDoc
     *
     * Used by UI components to perform actions after UiController initialized
     */
    @TriggerOnce
    public static class AfterInitEvent extends EventObject {
        protected final ScreenOptions options;

        public AfterInitEvent(Screen source, ScreenOptions options) {
            super(source);
            this.options = options;
        }

        @Override
        public Screen getSource() {
            return (Screen) super.getSource();
        }

        public ScreenOptions getOptions() {
            return options;
        }
    }

    /**
     * JavaDoc
     */
    public static class CloseTriggeredEvent extends EventObject {

        protected final CloseAction closeAction;
        protected boolean closePrevented = false;

        public CloseTriggeredEvent(Screen screen, CloseAction closeAction) {
            super(screen);
            this.closeAction = closeAction;
        }

        @Override
        public Screen getSource() {
            return (Screen) super.getSource();
        }

        public Screen getScreen() {
            return (Screen) super.getSource();
        }

        public CloseAction getCloseAction() {
            return closeAction;
        }

        public void preventWindowClose() {
            this.closePrevented = true;
        }

        public boolean isClosePrevented() {
            return closePrevented;
        }
    }

    /**
     * JavaDoc
     */
    @TriggerOnce
    public static class BeforeShowEvent extends EventObject {
        public BeforeShowEvent(Screen source) {
            super(source);
        }

        @Override
        public Screen getSource() {
            return (Screen) super.getSource();
        }
    }

    /**
     * JavaDoc
     */
    @TriggerOnce
    public static class AfterShowEvent extends EventObject {
        public AfterShowEvent(Screen source) {
            super(source);
        }

        @Override
        public Screen getSource() {
            return (Screen) super.getSource();
        }
    }

    /**
     * JavaDoc
     */
    public static class BeforeCloseEvent extends EventObject {

        protected final CloseAction closeAction;
        protected boolean closePrevented = false;

        public BeforeCloseEvent(Screen source, CloseAction closeAction) {
            super(source);
            this.closeAction = closeAction;
        }

        @Override
        public Screen getSource() {
            return (Screen) super.getSource();
        }

        public Screen getScreen() {
            return (Screen) super.getSource();
        }

        public CloseAction getCloseAction() {
            return closeAction;
        }

        public void preventWindowClose() {
            this.closePrevented = true;
        }

        public boolean isClosePrevented() {
            return closePrevented;
        }
    }

    /**
     * JavaDoc
     */
    public static class AfterCloseEvent extends EventObject {

        protected final CloseAction closeAction;

        public AfterCloseEvent(Screen source, CloseAction closeAction) {
            super(source);
            this.closeAction = closeAction;
        }

        @Override
        public Screen getSource() {
            return (Screen) super.getSource();
        }

        public Screen getScreen() {
            return (Screen) super.getSource();
        }

        public CloseAction getCloseAction() {
            return closeAction;
        }
    }
}