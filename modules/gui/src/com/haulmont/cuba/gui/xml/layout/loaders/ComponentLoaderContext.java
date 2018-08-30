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

import com.haulmont.cuba.gui.components.Frame;
import com.haulmont.cuba.gui.data.DsContext;
import com.haulmont.cuba.gui.xml.layout.ComponentLoader;
import com.haulmont.cuba.gui.xml.layout.ComponentLoader.InitTask;
import com.haulmont.cuba.gui.xml.layout.ComponentLoader.InjectTask;
import com.haulmont.cuba.gui.xml.layout.ComponentLoader.PostInitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComponentLoaderContext implements ComponentLoader.Context {

    protected DsContext dsContext;
    protected Frame frame;
    protected String fullFrameId;
    protected String currentFrameId;

    protected List<PostInitTask> postInitTasks = new ArrayList<>();
    protected List<InjectTask> injectTasks = new ArrayList<>();
    protected List<InitTask> initTasks = new ArrayList<>();

    protected Map<String, Object> parameters;
    protected Map<String, String> aliasesMap = new HashMap<>();

    protected ComponentLoader.Context parent;

    public ComponentLoaderContext(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    @Override
    public Map<String, Object> getParams() {
        return parameters;
    }

    @Override
    public DsContext getDsContext() {
        return dsContext;
    }

    public void setDsContext(DsContext dsContext) {
        this.dsContext = dsContext;
    }

    @Override
    public Frame getFrame() {
        return frame;
    }

    @Override
    public void setFrame(Frame frame) {
        this.frame = frame;
    }

    @Override
    public String getFullFrameId() {
        return fullFrameId;
    }

    @Override
    public void setFullFrameId(String frameId) {
        this.fullFrameId = frameId;
    }

    @Override
    public String getCurrentFrameId() {
        return currentFrameId;
    }

    @Override
    public void setCurrentFrameId(String currentFrameId) {
        this.currentFrameId = currentFrameId;
    }

    @Override
    public void addPostInitTask(PostInitTask task) {
        postInitTasks.add(task);
    }

    @Override
    public ComponentLoader.Context getParent() {
        return parent;
    }

    @Override
    public void setParent(ComponentLoader.Context parent) {
        this.parent = parent;
    }

    @Override
    public void executePostInitTasks() {
        if (!getPostInitTasks().isEmpty()) {
            new TaskExecutor(getPostInitTasks().get(0)).run();
        }
    }

    @Override
    public void addInjectTask(InjectTask task) {
        injectTasks.add(task);
    }

    @Override
    public void executeInjectTasks() {
        if (!getInjectTasks().isEmpty()) {
            new InjectTaskExecutor(getInjectTasks().get(0)).run();
        }
    }

    @Override
    public void addInitTask(InitTask task) {
        initTasks.add(task);
    }

    @Override
    public void executeInitTasks() {
        if (!getInitTasks().isEmpty()) {
            new InitTaskExecutor(getInitTasks().get(0)).run();
        }
    }

    public List<InjectTask> getInjectTasks() {
        return injectTasks;
    }

    public List<PostInitTask> getPostInitTasks() {
        return postInitTasks;
    }

    public List<InitTask> getInitTasks() {
        return initTasks;
    }

    protected void removeTask(PostInitTask task, ComponentLoaderContext context) {
        if (context.getPostInitTasks().remove(task) && context.getParent() != null) {
            removeTask(task, (ComponentLoaderContext) context.getParent());
        }
    }

    protected void removeTask(InjectTask task, ComponentLoaderContext context) {
        if (context.getInjectTasks().remove(task) && context.getParent() != null) {
            removeTask(task, (ComponentLoaderContext) context.getParent());
        }
    }

    protected void removeTask(InitTask task, ComponentLoaderContext context) {
        if (context.getInitTasks().remove(task) && context.getParent() != null) {
            removeTask(task, (ComponentLoaderContext) context.getParent());
        }
    }

    @Override
    public Map<String, String> getAliasesMap() {
        return aliasesMap;
    }

    protected class TaskExecutor implements Runnable {

        private final PostInitTask task;

        public TaskExecutor(PostInitTask task) {
            this.task = task;
        }

        @Override
        public void run() {
            removeTask(task, ComponentLoaderContext.this);
            task.execute(ComponentLoaderContext.this, frame);
            if (!getPostInitTasks().isEmpty()) {
                new TaskExecutor(getPostInitTasks().get(0)).run();
            }
        }
    }

    protected class InjectTaskExecutor implements Runnable {
        private final InjectTask task;

        public InjectTaskExecutor(InjectTask task) {
            this.task = task;
        }

        @Override
        public void run() {
            removeTask(task, ComponentLoaderContext.this);
            task.execute(ComponentLoaderContext.this, frame);
            if (!getInjectTasks().isEmpty()) {
                new InjectTaskExecutor(getInjectTasks().get(0)).run();
            }
        }
    }

    protected class InitTaskExecutor implements Runnable {
        private final InitTask task;

        public InitTaskExecutor(InitTask task) {
            this.task = task;
        }

        @Override
        public void run() {
            removeTask(task, ComponentLoaderContext.this);
            task.execute(ComponentLoaderContext.this, frame);
            if (!getInitTasks().isEmpty()) {
                new InitTaskExecutor(getInitTasks().get(0)).run();
            }
        }
    }
}