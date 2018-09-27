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

package com.haulmont.cuba.gui;

import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.gui.components.Window;
import com.haulmont.cuba.gui.config.WindowConfig;
import com.haulmont.cuba.gui.config.WindowInfo;
import com.haulmont.cuba.gui.model.CollectionContainer;
import com.haulmont.cuba.gui.model.DataContext;
import com.haulmont.cuba.gui.screen.*;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.haulmont.bali.util.Preconditions.checkNotNullArgument;

@Component("cuba_EditorScreens")
public class EditorScreens {
    @Inject
    protected Metadata metadata;
    @Inject
    protected WindowConfig windowConfig;

    public <E extends Entity> EditorBuilder<E> builder(Class<E> entityClass, FrameOwner origin) {
        checkNotNullArgument(entityClass);
        checkNotNullArgument(origin);

        return new EditorBuilder<>(origin, entityClass, this::buildEditor);
    }

    @SuppressWarnings("unchecked")
    protected <E extends Entity, S extends Screen> S buildEditor(EditorBuilder<E> builder) {
        FrameOwner origin = builder.getOrigin();
        Screens screens = origin.getScreenContext().getScreens();

        if (builder.getMode() == Mode.EDIT && builder.getEntity() == null) {
            throw new IllegalStateException(String.format("Editor of %s cannot be open with mode EDIT, entity is not set",
                    builder.getEntityClass()));
        }

        Screen screen;

        if (builder instanceof EditorClassBuilder) {
            Class screenClass = ((EditorClassBuilder) builder).getScreenClass();
            screen = screens.create(screenClass, builder.getLaunchMode(), builder.getOptions());
        } else {
            MetaClass metaClass = metadata.getClass(builder.getEntityClass());
            String editorScreenId = windowConfig.getEditorScreenId(metaClass);
            WindowInfo windowInfo = windowConfig.getWindowInfo(editorScreenId);

            screen = screens.create(windowInfo, builder.getLaunchMode(), builder.getOptions());
        }

        initEditor(builder, screen);

        return (S) screen;
    }

    @SuppressWarnings("unchecked")
    protected <E extends Entity> void initEditor(EditorBuilder<E> builder, Screen screen) {
        if (!(screen instanceof EditorScreen)) {
            throw new IllegalArgumentException(String.format("Screen %s does not implement EditorScreen: %s",
                    screen.getId(), screen.getClass()));
        }

        EditorScreen<E> editorScreen = (EditorScreen<E>) screen;

        E entity = builder.getEntity();

        if (builder.getMode() == Mode.CREATE) {
            if (entity == null) {
                entity = metadata.create(builder.getEntityClass());
            }
            if (builder.getInitializer() != null) {
                builder.getInitializer().accept(entity);
            }
        }

        editorScreen.setEntityToEdit(entity);

        DataContext parentDataContext = builder.getParentDataContext();
        if (parentDataContext != null) {
            UiControllerUtils.getScreenData(screen).getDataContext().setParent(parentDataContext);
        }

        CollectionContainer<E> container = builder.getContainer();
        if (container != null) {
            screen.addAfterCloseListener(afterCloseEvent -> {
                CloseAction closeAction = afterCloseEvent.getCloseAction();
                if (isCommitCloseAction(closeAction)) {
                    if (builder.getMode() == Mode.CREATE) {
                        container.getMutableItems().add(0, editorScreen.getEditedEntity());
                    } else {
                        container.replaceItem(editorScreen.getEditedEntity());
                    }
                }
            });
        }
    }

    protected boolean isCommitCloseAction(CloseAction closeAction) {
        return (closeAction instanceof StandardCloseAction)
                && ((StandardCloseAction) closeAction).getActionId().equals(Window.COMMIT_ACTION_ID);
    }

    public enum Mode {
        CREATE,
        EDIT
    }

    public static class EditorBuilder<E extends Entity> {

        // todo add screenId parameter to builder

        // todo add ListComponent to builder, focus after close

        protected final FrameOwner origin;
        protected final Class<E> entityClass;
        protected final Function<EditorBuilder<E>, Screen> handler;

        protected E entity;
        protected CollectionContainer<E> container;
        protected Consumer<E> initializer;
        protected Screens.LaunchMode launchMode = OpenMode.THIS_TAB;
        protected ScreenOptions options = FrameOwner.NO_OPTIONS;

        protected DataContext parentDataContext;
        protected Mode mode = Mode.CREATE;

        protected EditorBuilder(EditorBuilder<E> builder) {
            this.origin = builder.origin;
            this.entityClass = builder.entityClass;
            this.handler = builder.handler;

            // copy all properties

            this.mode = builder.mode;
            this.entity = builder.entity;
            this.container = builder.container;
            this.initializer = builder.initializer;
            this.options = builder.options;
            this.launchMode = builder.launchMode;
            this.parentDataContext = builder.parentDataContext;
        }

        public EditorBuilder(FrameOwner origin, Class<E> entityClass, Function<EditorBuilder<E>, Screen> handler) {
            this.origin = origin;
            this.entityClass = entityClass;
            this.handler = handler;
        }

        public EditorBuilder<E> newEntity() {
            this.mode = Mode.CREATE;
            return this;
        }

        public EditorBuilder<E> withEntity(E entity) {
            this.entity = entity;
            return this;
        }

        public EditorBuilder<E> editEntity(E entity) {
            this.entity = entity;
            this.mode = Mode.EDIT;
            return this;
        }

        public EditorBuilder<E> withContainer(CollectionContainer<E> container) {
            this.container = container;
            return this;
        }

        public EditorBuilder<E> withInitializer(Consumer<E> initializer) {
            this.initializer = initializer;

            return this;
        }

        public EditorBuilder<E> withLaunchMode(Screens.LaunchMode launchMode) {
            this.launchMode = launchMode;
            return this;
        }

        public EditorBuilder<E> withParentDataContext(DataContext parentDataContext) {
            this.parentDataContext = parentDataContext;
            return this;
        }

        public EditorBuilder<E> withOptions(ScreenOptions options) {
            this.options = options;
            return this;
        }

        public <S extends Screen & EditorScreen<E>> EditorClassBuilder<E, S> withScreen(Class<S> screenClass) {
            return new EditorClassBuilder<>(this, screenClass);
        }

        public DataContext getParentDataContext() {
            return parentDataContext;
        }

        public Class<E> getEntityClass() {
            return entityClass;
        }

        public E getEntity() {
            return entity;
        }

        public CollectionContainer<E> getContainer() {
            return container;
        }

        public Consumer<E> getInitializer() {
            return initializer;
        }

        public Screens.LaunchMode getLaunchMode() {
            return launchMode;
        }

        public FrameOwner getOrigin() {
            return origin;
        }

        public ScreenOptions getOptions() {
            return options;
        }

        public Mode getMode() {
            return mode;
        }

        public Screen create() {
            return handler.apply(this);
        }
    }

    public static class EditorClassBuilder<E extends Entity, S extends Screen & EditorScreen<E>>
            extends EditorBuilder<E> {

        protected Class<S> screenClass;

        public EditorClassBuilder(EditorBuilder<E> builder, Class<S> screenClass) {
            super(builder);

            this.screenClass = screenClass;
        }

        @Override
        public EditorClassBuilder<E, S> newEntity() {
            super.newEntity();
            return this;
        }

        @Override
        public EditorClassBuilder<E, S> editEntity(E entity) {
            super.editEntity(entity);
            return this;
        }

        @Override
        public EditorClassBuilder<E, S> withEntity(E entity) {
            super.withEntity(entity);
            return this;
        }

        @Override
        public EditorClassBuilder<E, S> withContainer(CollectionContainer<E> container) {
            super.withContainer(container);
            return this;
        }

        @Override
        public EditorClassBuilder<E, S> withInitializer(Consumer<E> initializer) {
            super.withInitializer(initializer);
            return this;
        }

        @Override
        public EditorClassBuilder<E, S> withLaunchMode(Screens.LaunchMode launchMode) {
            super.withLaunchMode(launchMode);
            return this;
        }

        @Override
        public EditorClassBuilder<E, S> withParentDataContext(DataContext parentDataContext) {
            super.withParentDataContext(parentDataContext);
            return this;
        }

        @Override
        public EditorClassBuilder<E, S> withOptions(ScreenOptions options) {
            super.withOptions(options);
            return this;
        }

        public Class<S> getScreenClass() {
            return screenClass;
        }

        @SuppressWarnings("unchecked")
        @Override
        public S create() {
            return (S) handler.apply(this);
        }
    }
}