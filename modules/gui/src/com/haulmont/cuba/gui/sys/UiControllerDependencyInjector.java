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

package com.haulmont.cuba.gui.sys;

import com.google.common.base.Strings;
import com.haulmont.cuba.client.ClientConfiguration;
import com.haulmont.cuba.core.config.Config;
import com.haulmont.cuba.core.global.BeanLocator;
import com.haulmont.cuba.core.global.Configuration;
import com.haulmont.cuba.core.global.DevelopmentException;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.gui.*;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.sys.EventTarget;
import com.haulmont.cuba.gui.components.sys.ValuePathHelper;
import com.haulmont.cuba.gui.data.DataSupplier;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.DsContext;
import com.haulmont.cuba.gui.events.sys.UiEventListenerMethodAdapter;
import com.haulmont.cuba.gui.export.ExportDisplay;
import com.haulmont.cuba.gui.model.DataContext;
import com.haulmont.cuba.gui.model.DataLoader;
import com.haulmont.cuba.gui.model.InstanceContainer;
import com.haulmont.cuba.gui.model.ScreenData;
import com.haulmont.cuba.gui.screen.*;
import com.haulmont.cuba.gui.screen.compatibility.LegacyFrame;
import com.haulmont.cuba.gui.sys.UiControllerReflectionInspector.AnnotatedMethod;
import com.haulmont.cuba.gui.sys.UiControllerReflectionInspector.InjectElement;
import com.haulmont.cuba.gui.theme.ThemeConstants;
import com.haulmont.cuba.gui.theme.ThemeConstantsManager;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import java.lang.reflect.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.reflect.Proxy.newProxyInstance;
import static org.apache.commons.lang3.reflect.MethodUtils.getAccessibleMethod;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

/**
 * Wires {@link Inject}, {@link Named}, {@link WindowParam} fields/setters and {@link EventListener} methods.
 */
@org.springframework.stereotype.Component(UiControllerDependencyInjector.NAME)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UiControllerDependencyInjector {

    public static final String NAME = "cuba_UiControllerDependencyInjector";

    protected FrameOwner frameOwner;
    protected ScreenOptions options;

    protected BeanLocator beanLocator;
    protected UiControllerReflectionInspector reflectionInspector;

    public UiControllerDependencyInjector(FrameOwner frameOwner, ScreenOptions options) {
        this.frameOwner = frameOwner;
        this.options = options;
    }

    @Inject
    public void setBeanLocator(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Inject
    public void setReflectionInspector(UiControllerReflectionInspector reflectionInspector) {
        this.reflectionInspector = reflectionInspector;
    }

    public void inject() {
        injectValues(frameOwner);

        initSubscribeListeners(frameOwner);

        initProvideObjects(frameOwner);

        initUiEventListeners(frameOwner);
    }

    protected void initProvideObjects(FrameOwner frameOwner) {
        Class<? extends FrameOwner> clazz = frameOwner.getClass();

        List<AnnotatedMethod<Provide>> provideMethods = reflectionInspector.getAnnotatedProvideMethods(clazz);

        for (AnnotatedMethod<Provide> annotatedMethod : provideMethods) {
            Provide annotation = annotatedMethod.getAnnotation();

            String target = ScreenDescriptorUtils.getInferredProvideId(annotation);
            Frame frame = UiControllerUtils.getFrame(frameOwner);

            Object targetInstance;
            if (Strings.isNullOrEmpty(target)) {
                if (annotation.target() == Target.COMPONENT // if kept default value
                        || annotation.target() == Target.CONTROLLER) {
                    targetInstance = frameOwner;
                } else if (annotation.target() == Target.FRAME) {
                    targetInstance = frame;
                } else {
                    throw new UnsupportedOperationException();
                }
            } else {
                // todo column generators, field generators, etc
                targetInstance = frame.getComponent(target);

                if (targetInstance == null) {
                    throw new DevelopmentException(
                            String.format("Unable to find @Provide target %s in %s", target, frame.getId()));
                }
            }

            String targetMethodName;
            if (Strings.isNullOrEmpty(annotation.subject()) && annotation.type() == Object.class) {
                // todo default target method
                targetMethodName = null;
            } else if (annotation.type() != Object.class) {
                targetMethodName = "set" + annotation.type().getSimpleName();
            } else {
                targetMethodName = "set" + StringUtils.capitalize(annotation.subject());
            }

            Class<?> instanceClass = targetInstance.getClass();
            Method method = annotatedMethod.getMethod();

            Method targetMethod = getAccessibleMethod(instanceClass, targetMethodName);
            if (targetMethod == null) {
                throw new DevelopmentException(
                        String.format("Unable to find @Provide target method %s in %s", targetMethodName, frame.getId())
                );
            }

            if (targetMethod.getParameterCount() != 1) {
                throw new DevelopmentException("Target method of @Provide must have 1 parameter");
            }

            Class<?> targetObjectType = targetMethod.getParameterTypes()[0];
            if (!targetObjectType.isInterface()) {
                throw new DevelopmentException("@Provide target method parameter must be an interface");
            }

            // todo provide optimized version for Function
            ClassLoader classLoader = getClass().getClassLoader();
            Object proxy = newProxyInstance(classLoader, new Class[]{targetObjectType},
                    new ProvideInvocationHandler(targetInstance, method)
            );

            try {
                targetMethod.invoke(targetInstance, proxy);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Unable to set declarative @Provide handler for " + method, e);
            }
        }
    }

    protected void injectValues(FrameOwner frameOwner) {
        List<InjectElement> injectElements = reflectionInspector.getAnnotatedInjectElements(frameOwner.getClass());

        for (InjectElement entry : injectElements) {
            doInjection(entry.getElement(), entry.getAnnotationClass());
        }
    }

    @SuppressWarnings("unchecked")
    protected void initSubscribeListeners(FrameOwner frameOwner) {
        Class<? extends FrameOwner> clazz = frameOwner.getClass();

        List<Method> eventListenerMethods = reflectionInspector.getAnnotatedSubscribeMethods(clazz);

        for (Method method : eventListenerMethods) {
            Subscribe annotation = findMergedAnnotation(method, Subscribe.class);
            checkState(annotation != null);

            Consumer listener = new DeclarativeSubscribeExecutor(frameOwner, method);

            String target = ScreenDescriptorUtils.getInferredSubscribeId(annotation);

            Parameter parameter = method.getParameters()[0];
            Class<?> parameterType = parameter.getType();

            if (Strings.isNullOrEmpty(target)) {
                if (annotation.target() == Target.COMPONENT // if kept default value
                        || annotation.target() == Target.CONTROLLER) {
                    // controller event
                    UiControllerUtils.addListener(frameOwner, parameterType, listener);
                } else if (annotation.target() == Target.FRAME) {
                    // window or fragment event
                    Frame frame = UiControllerUtils.getFrame(frameOwner);
                    ((EventTarget) frame).addListener(parameterType, listener);
                }
            } else {
                // component event
                EventTarget component = findEventTarget(frameOwner, target);
                component.addListener(parameterType, listener);
            }
        }
    }

    protected EventTarget findEventTarget(FrameOwner frameOwner, String target) {
        Frame frame = UiControllerUtils.getFrame(frameOwner);

        String[] elements = ValuePathHelper.parse(target);
        if (elements.length > 1) {
            String id = elements[elements.length - 1];

            String[] subPath = ArrayUtils.subarray(elements, 0, elements.length - 1);
            Component component = frame.getComponent(ValuePathHelper.format(subPath));

            if (component != null) {
                if (component instanceof ActionsHolder) {
                    Action action = ((ActionsHolder) component).getAction(id);
                    if (action instanceof EventTarget) {
                        return (EventTarget) action;
                    }
                }

                if (component instanceof ComponentContainer) {
                    Component childComponent = ((ComponentContainer) component).getComponent(id);
                    if (childComponent instanceof EventTarget) {
                        return (EventTarget) childComponent;
                    }
                }
            }
        } else if (elements.length == 1) {
            Action action = frame.getAction(target);
            if (action instanceof EventTarget) {
                return (EventTarget) action;
            }

            Component component = frame.getComponent(target);
            if (component instanceof EventTarget) {
                return (EventTarget) component;
            }
        }

        throw new DevelopmentException(String.format("Unable to find @Subscribe target %s in %s", target, frame.getId()));
    }

    protected void initUiEventListeners(FrameOwner frameOwner) {
        Class<? extends FrameOwner> clazz = frameOwner.getClass();

        List<Method> eventListenerMethods = reflectionInspector.getAnnotatedListenerMethods(clazz);

        if (!eventListenerMethods.isEmpty()) {
            Events events = beanLocator.get(Events.NAME);

            List<ApplicationListener> listeners = eventListenerMethods.stream()
                    .map(m -> new UiEventListenerMethodAdapter(frameOwner, clazz, m, events))
                    .collect(Collectors.toList());

            UiControllerUtils.setUiEventListeners(frameOwner, listeners);
        }
    }

    protected void doInjection(AnnotatedElement element, Class annotationClass) {
        Class<?> type;
        String name = null;
        if (annotationClass == Named.class) {
            name = element.getAnnotation(Named.class).value();
        } else if (annotationClass == Resource.class) {
            name = element.getAnnotation(Resource.class).name();
        } else if (annotationClass == WindowParam.class) {
            name = element.getAnnotation(WindowParam.class).name();
        }

        boolean required = true;
        if (element.isAnnotationPresent(WindowParam.class)) {
            required = element.getAnnotation(WindowParam.class).required();
        }

        if (element instanceof Field) {
            type = ((Field) element).getType();
            if (StringUtils.isEmpty(name)) {
                name = ((Field) element).getName();
            }
        } else if (element instanceof Method) {
            Class<?>[] types = ((Method) element).getParameterTypes();
            if (types.length != 1) {
                throw new IllegalStateException("Can inject to methods with one parameter only");
            }

            type = types[0];
            if (StringUtils.isEmpty(name)) {
                if (((Method) element).getName().startsWith("set")) {
                    name = StringUtils.uncapitalize(((Method) element).getName().substring(3));
                } else {
                    name = ((Method) element).getName();
                }
            }
        } else {
            throw new IllegalStateException("Can inject to fields and setter methods only");
        }

        Object instance = getInjectedInstance(type, name, annotationClass, element);

        if (instance != null) {
            assignValue(element, instance);
        } else if (required) {
            Class<?> declaringClass = ((Member) element).getDeclaringClass();
            Class<? extends FrameOwner> frameClass = frameOwner.getClass();

            String msg;
            if (frameClass == declaringClass) {
                msg = String.format(
                        "CDI - Unable to find an instance of type '%s' named '%s' for instance of '%s'",
                        type, name, frameClass.getCanonicalName());
            } else {
                msg = String.format(
                        "CDI - Unable to find an instance of type '%s' named '%s' declared in '%s' for instance of '%s'",
                        type, name, declaringClass.getCanonicalName(), frameClass.getCanonicalName());
            }

            Logger log = LoggerFactory.getLogger(UiControllerDependencyInjector.class);
            log.warn(msg);
        }
    }

    protected Object getInjectedInstance(Class<?> type, String name, Class annotationClass, AnnotatedElement element) {
        Frame frame = UiControllerUtils.getFrame(frameOwner);

        if (annotationClass == WindowParam.class) {
            if (options instanceof MapScreenOptions) {
                return ((MapScreenOptions) options).getParams().get(name);
            }
            // Injecting a parameter
            return null;

        } else if (ScreenFragment.class.isAssignableFrom(type)) {
            // Injecting inner fragment controller
            Component fragment = frame.getComponent(name);
            if (fragment == null) {
                return null;
            }
            return ((Fragment) fragment).getFrameOwner();

        } else if (Component.class.isAssignableFrom(type)) {
            // Injecting a UI component
            return frame.getComponent(name);

        } else if (InstanceContainer.class.isAssignableFrom(type)) {
            // Injecting a container
            ScreenData data = UiControllerUtils.getScreenData(frameOwner);
            return data.getContainer(name);

        } else if (DataLoader.class.isAssignableFrom(type)) {
            // Injecting a loader
            ScreenData data = UiControllerUtils.getScreenData(frameOwner);
            return data.getLoader(name);

        } else if (DataContext.class.isAssignableFrom(type)) {
            // Injecting the data context
            ScreenData data = UiControllerUtils.getScreenData(frameOwner);
            return data.getDataContext();

        } else if (Datasource.class.isAssignableFrom(type)) {
            // Injecting a datasource
            return ((LegacyFrame) frame.getFrameOwner()).getDsContext().get(name);

        } else if (DsContext.class.isAssignableFrom(type)) {
            // Injecting the DsContext
            return ((LegacyFrame) frame.getFrameOwner()).getDsContext();

        } else if (DataSupplier.class.isAssignableFrom(type)) {
            // Injecting the DataSupplier
            return ((LegacyFrame) frame.getFrameOwner()).getDsContext().getDataSupplier();

        } else if (FrameContext.class.isAssignableFrom(type)) {
            // Injecting the FrameContext
            return frame.getContext();

        } else if (Action.class.isAssignableFrom(type)) {
            // Injecting an action
            return ComponentsHelper.findAction(name, frame);

        } else if (ExportDisplay.class.isAssignableFrom(type)) {
            // Injecting an ExportDisplay
            ExportDisplay exportDisplay = beanLocator.get(ExportDisplay.NAME);
            exportDisplay.setFrame(frame);
            return exportDisplay;

        } else if (Config.class.isAssignableFrom(type)) {
            ClientConfiguration configuration = beanLocator.get(Configuration.NAME);
            //noinspection unchecked
            return configuration.getConfigCached((Class<? extends Config>) type);

        } else if (Logger.class == type && element instanceof Field) {
            // injecting logger
            return LoggerFactory.getLogger(((Field) element).getDeclaringClass());

        } else if (Screens.class == type) {
            // injecting screens
            return UiControllerUtils.getScreenContext(frameOwner).getScreens();

        } else if (Dialogs.class == type) {
            // injecting screens
            return UiControllerUtils.getScreenContext(frameOwner).getDialogs();

        } else if (Notifications.class == type) {
            // injecting screens
            return UiControllerUtils.getScreenContext(frameOwner).getNotifications();

        } else if (Fragments.class == type) {
            // injecting fragments
            return UiControllerUtils.getScreenContext(frameOwner).getFragments();

        } else if (MessageBundle.class == type) {
            MessageBundle messageBundle = beanLocator.getPrototype(MessageBundle.NAME);

            String packageName;
            if (element instanceof Field) {
                packageName = ((Field) element).getDeclaringClass().getPackage().getName();
            } else if (element instanceof Method) {
                packageName = ((Method) element).getDeclaringClass().getPackage().getName();
            } else {
                throw new UnsupportedOperationException("Unsupported annotated element for MessageBundle");
            }

            messageBundle.setMessagesPack(packageName);

            if (frame instanceof Component.HasXmlDescriptor) {
                Element xmlDescriptor = ((Component.HasXmlDescriptor) frame).getXmlDescriptor();
                if (xmlDescriptor != null) {
                    String messagePack = xmlDescriptor.attributeValue("messagesPack");
                    if (messagePack != null) {
                        messageBundle.setMessagesPack(messagePack);
                    }
                }
            }

            return messageBundle;

        } else if (ThemeConstants.class == type) {
            // Injecting a Theme
            ThemeConstantsManager themeManager = beanLocator.get(ThemeConstantsManager.NAME);
            return themeManager.getConstants();

        } else {
            Object instance;
            // Try to find a Spring bean
            Map<String, ?> beans = beanLocator.getAll(type);
            if (!beans.isEmpty()) {
                instance = beans.get(name);
                // If a bean with required name found, return it. Otherwise return first found.
                if (instance != null) {
                    return instance;
                } else {
                    return beans.values().iterator().next();
                }
            }

            // There are no Spring beans of required type - the last option is Companion
            if (frameOwner instanceof LegacyFrame) {
                instance = ((LegacyFrame) frameOwner).getCompanion();
                if (instance != null && type.isAssignableFrom(instance.getClass())) {
                    return instance;
                }
            }
        }
        return null;
    }

    protected void assignValue(AnnotatedElement element, Object value) {
        // element is already marked as accessible in UiControllerReflectionInspector

        if (element instanceof Field) {
            Field field = (Field) element;

            try {
                field.set(frameOwner, value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("CDI - Unable to assign value to field " + field.getName(), e);
            }
        } else {
            Method method = (Method) element;

            Object[] params = new Object[1];
            params[0] = value;
            try {
                method.invoke(frameOwner, params);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("CDI - Unable to assign value through setter "
                        + method.getName(), e);
            }
        }
    }

    public static class DeclarativeSubscribeExecutor implements Consumer {
        protected final Object owner;
        protected final Method method;

        public DeclarativeSubscribeExecutor(Object owner, Method method) {
            this.method = method;
            this.owner = owner;
        }

        @Override
        public void accept(Object event) {
            try {
                method.invoke(owner, event);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Unhandled exception in UI controller", e);
            }
        }

        @Override
        public String toString() {
            return "DeclarativeSubscribeExecutor{" +
                    "owner=" + owner.getClass() +
                    ", method=" + method +
                    '}';
        }
    }

    public static class ProvideInvocationFunction implements Function {
        private final Object targetInstance;
        private final Method method;

        public ProvideInvocationFunction(Object targetInstance, Method method) {
            this.targetInstance = targetInstance;
            this.method = method;
        }

        @Override
        public Object apply(Object o) {
            return null;
        }
    }

    public static class ProvideInvocationHandler implements InvocationHandler {
        private final Object targetInstance;
        private final Method method;

        public ProvideInvocationHandler(Object targetInstance, Method method) {
            this.targetInstance = targetInstance;
            this.method = method;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return null;
        }
    }
}