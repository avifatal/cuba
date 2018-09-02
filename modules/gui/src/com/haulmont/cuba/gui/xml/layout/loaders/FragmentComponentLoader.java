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
 *
 */
package com.haulmont.cuba.gui.xml.layout.loaders;

import com.haulmont.cuba.core.global.DevelopmentException;
import com.haulmont.cuba.gui.Dialogs;
import com.haulmont.cuba.gui.GuiDevelopmentException;
import com.haulmont.cuba.gui.Notifications;
import com.haulmont.cuba.gui.Screens;
import com.haulmont.cuba.gui.components.AbstractFrame;
import com.haulmont.cuba.gui.components.Fragment;
import com.haulmont.cuba.gui.components.Frame;
import com.haulmont.cuba.gui.components.sys.FragmentImplementation;
import com.haulmont.cuba.gui.config.WindowAttributesProvider;
import com.haulmont.cuba.gui.config.WindowConfig;
import com.haulmont.cuba.gui.config.WindowInfo;
import com.haulmont.cuba.gui.logging.UIPerformanceLogger.LifeCycle;
import com.haulmont.cuba.gui.model.ScreenData;
import com.haulmont.cuba.gui.screen.FrameOwner;
import com.haulmont.cuba.gui.screen.ScreenFragment;
import com.haulmont.cuba.gui.screen.UiControllerUtils;
import com.haulmont.cuba.gui.sys.ScreenContextImpl;
import com.haulmont.cuba.gui.xml.layout.ComponentLoader;
import com.haulmont.cuba.gui.xml.layout.LayoutLoader;
import com.haulmont.cuba.gui.xml.layout.ScreenXmlLoader;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.perf4j.StopWatch;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;

import static com.haulmont.cuba.gui.screen.FrameOwner.NO_OPTIONS;

public class FragmentComponentLoader extends ContainerLoader<Frame> {

    protected String fragmentId;
    protected ComponentLoader fragmentLoader;
    protected ComponentLoaderContext innerContext;

    protected WindowConfig getWindowConfig() {
        return beanLocator.get(WindowConfig.NAME);
    }

    @Override
    public void createComponent() {
        String src = element.attributeValue("src");
        String screenId = element.attributeValue("screen");

        if (element.attributeValue("id") != null) {
            fragmentId = element.attributeValue("id");
        }

        if (src == null
                && screenId == null) {
            throw new GuiDevelopmentException("Either 'src' or 'screen' must be specified for 'frame'",
                    context.getFullFrameId(), "fragment", fragmentId);
        }

        WindowInfo windowInfo;
        if (src == null) {
            windowInfo = getWindowConfig().getWindowInfo(screenId);
            if (windowInfo.getTemplate() == null) {
                throw new GuiDevelopmentException(
                        String.format("Screen %s doesn't have template path configured", screenId),
                        context.getFullFrameId()
                );
            }
        } else {
            windowInfo = createFakeWindowInfo(src, fragmentId);
        }

        ScreenXmlLoader screenXmlLoader = beanLocator.get(ScreenXmlLoader.NAME);

        Element windowElement = screenXmlLoader.load(windowInfo.getTemplate(), windowInfo.getId(),
                Collections.emptyMap()); // todo pass params

        Fragment fragment = factory.createComponent(Fragment.NAME);
        ScreenFragment controller = createController(windowInfo, fragment, windowInfo.asFragment());

        // setup screen and controller

        UiControllerUtils.setWindowId(controller, windowInfo.getId());
        UiControllerUtils.setFrame(controller, fragment);
        UiControllerUtils.setScreenContext(controller,
                new ScreenContextImpl(windowInfo, NO_OPTIONS,
                        beanLocator.get(Screens.NAME),
                        beanLocator.get(Dialogs.NAME),
                        beanLocator.get(Notifications.NAME))
        );
        UiControllerUtils.setScreenData(controller, beanLocator.get(ScreenData.NAME));

        FragmentImplementation fragmentImpl = (FragmentImplementation) fragment;
        fragmentImpl.setFrameOwner(controller);
        fragmentImpl.setId(controller.getId());

        // load from XML

        ComponentLoaderContext parentContext = (ComponentLoaderContext) getContext();

        String frameId = fragmentId;
        if (parentContext.getFullFrameId() != null) {
            frameId = parentContext.getFullFrameId() + "." + frameId;
        }

        innerContext = new ComponentLoaderContext(context.getParams());
        innerContext.setCurrentFrameId(fragmentId);
        innerContext.setFullFrameId(frameId);
        innerContext.setFrame(fragment);
        innerContext.setParent(parentContext);

        LayoutLoader layoutLoader = beanLocator.getPrototype(LayoutLoader.NAME, innerContext);
        layoutLoader.setLocale(getLocale());
        layoutLoader.setMessagesPack(getMessagesPack()); // todo set by template or controller

        this.fragmentLoader = layoutLoader.createFragmentContent(fragment, windowElement, fragmentId);

        this.resultComponent = fragment;
    }

    @SuppressWarnings("unchecked")
    protected WindowInfo createFakeWindowInfo(String src, String fragmentId) {
        Element screenElement = DocumentHelper.createElement("screen");
        screenElement.addAttribute("template", src);
        screenElement.addAttribute("id", fragmentId);

        ScreenXmlLoader screenXmlLoader = beanLocator.get(ScreenXmlLoader.NAME);

        Element windowElement = screenXmlLoader.load(src, fragmentId, Collections.emptyMap());
        Class<? extends ScreenFragment> fragmentClass;

        String className = windowElement.attributeValue("class");
        if (StringUtils.isNotEmpty(className)) {
            fragmentClass = (Class<? extends ScreenFragment>) getScripting().loadClassNN(className);
        } else {
            fragmentClass = AbstractFrame.class;
        }

        return new WindowInfo(fragmentId, new WindowAttributesProvider() {
            @Override
            public WindowInfo.Type getType(WindowInfo windowInfo1) {
                return WindowInfo.Type.FRAGMENT;
            }

            @Override
            public String getTemplate(WindowInfo windowInfo1) {
                return src;
            }

            @Override
            public boolean isMultiOpen(WindowInfo windowInfo1) {
                return false;
            }

            @Nonnull
            @Override
            public Class<? extends FrameOwner> getControllerClass(WindowInfo windowInfo1) {
                return fragmentClass;
            }
        }, screenElement);
    }

    protected <T extends ScreenFragment> T createController(WindowInfo windowInfo, Fragment fragment,
                                                            Class<T> screenClass) {
        Constructor<T> constructor;
        try {
            constructor = screenClass.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new DevelopmentException("No accessible constructor for screen class " + screenClass);
        }

        T controller;
        try {
            controller = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Unable to create instance of screen class " + screenClass);
        }

        return controller;
    }

    @Override
    public void loadComponent() {
        loadAliases();

        if (context.getFrame() != null) {
            resultComponent.setFrame(context.getFrame());
        }

        String src = element.attributeValue("src");
        String screenId = element.attributeValue("screen");
        String screenPath = StringUtils.isEmpty(screenId) ? src : screenId;
        if (element.attributeValue("id") != null) {
            screenPath = element.attributeValue("id");
        }
        if (context.getFrame() != null) {
            String parentId = context.getFullFrameId();
            if (StringUtils.isNotEmpty(parentId)) {
                screenPath = parentId + "." + screenPath;
            }
        }

        StopWatch stopWatch = LifeCycle.LOAD.createStopWatch(screenPath);

        fragmentLoader.loadComponent();

        stopWatch.stop();

        // load properties after inner context, they must override values defined inside of fragment

        assignXmlDescriptor(resultComponent, element);
        loadVisible(resultComponent, element);
        loadEnable(resultComponent, element);

        loadStyleName(resultComponent, element);
        loadResponsive(resultComponent, element);

        loadAlign(resultComponent, element);

        loadHeight(resultComponent, element);
        loadWidth(resultComponent, element);

        loadIcon(resultComponent, element);
        loadCaption(resultComponent, element);
        loadDescription(resultComponent, element);

        // propagate init phases

        ComponentLoaderContext parentContext = (ComponentLoaderContext) getContext();

        parentContext.getInjectTasks().addAll(innerContext.getInjectTasks());
        parentContext.getInitTasks().addAll(innerContext.getInitTasks());
        parentContext.getPostInitTasks().addAll(innerContext.getPostInitTasks());
    }

    protected void loadAliases() {
        if (fragmentLoader instanceof FragmentLoader) {
            ComponentLoaderContext frameLoaderInnerContext = (ComponentLoaderContext) fragmentLoader.getContext();
            for (Element aliasElement : element.elements("dsAlias")) {
                String aliasDatasourceId = aliasElement.attributeValue("alias");
                String originalDatasourceId = aliasElement.attributeValue("datasource");
                if (StringUtils.isNotBlank(aliasDatasourceId) && StringUtils.isNotBlank(originalDatasourceId)) {
                    frameLoaderInnerContext.getAliasesMap().put(aliasDatasourceId, originalDatasourceId);
                }
            }
        }
    }
}