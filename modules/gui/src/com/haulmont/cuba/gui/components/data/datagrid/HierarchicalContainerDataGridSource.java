/*
 * Copyright (c) 2008-2018 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cuba.gui.components.data.datagrid;

import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.gui.components.data.TreeDataGridSource;
import com.haulmont.cuba.gui.model.CollectionContainer;

import java.util.stream.Stream;

public class HierarchicalContainerDataGridSource<E extends Entity>
            extends CollectionContainerDataGridSource<E>
            implements TreeDataGridSource<E> {

    private final String hierarchyProperty;

    public HierarchicalContainerDataGridSource(CollectionContainer<E> container, String hierarchyProperty) {
        super(container);
        this.hierarchyProperty = hierarchyProperty;
    }

    @Override
    public int getChildCount(E parent) {
        return Math.toIntExact(getChildren(parent).count());
    }

    @Override
    public Stream<E> getChildren(E item) {
        if (item == null) {
            // root items
            return container.getItems().stream()
                    .filter(it -> {
                        E parentItem = it.getValue(hierarchyProperty);
                        return parentItem == null || (container.getItemOrNull(parentItem.getId()) == null);
                    });
        } else {
            return container.getItems().stream()
                    .filter(it -> {
                        E parentItem = it.getValue(hierarchyProperty);
                        return parentItem != null && parentItem.equals(item);
                    });
        }
    }

    @Override
    public boolean hasChildren(E item) {
        return container.getItems().stream().anyMatch(it -> {
            E parentItem = it.getValue(hierarchyProperty);
            return parentItem != null && parentItem.equals(item);
        });
    }
}
