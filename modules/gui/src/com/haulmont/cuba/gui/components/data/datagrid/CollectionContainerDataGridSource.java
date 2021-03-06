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

import com.haulmont.bali.events.EventHub;
import com.haulmont.bali.events.Subscription;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaPropertyPath;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.MetadataTools;
import com.haulmont.cuba.core.global.Sort;
import com.haulmont.cuba.gui.components.data.BindingState;
import com.haulmont.cuba.gui.components.data.DataGridSource;
import com.haulmont.cuba.gui.components.data.meta.ContainerDataSource;
import com.haulmont.cuba.gui.components.data.meta.EntityDataGridSource;
import com.haulmont.cuba.gui.model.CollectionContainer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class CollectionContainerDataGridSource<E extends Entity>
        implements EntityDataGridSource<E>, DataGridSource.Sortable<E>, ContainerDataSource<E> {

    protected CollectionContainer<E> container;

    protected EventHub events = new EventHub();

    public CollectionContainerDataGridSource(CollectionContainer<E> container) {
        this.container = container;
        this.container.addItemChangeListener(this::containerItemChanged);
        this.container.addCollectionChangeListener(this::containerCollectionChanged);
        this.container.addItemPropertyChangeListener(this::containerItemPropertyChanged);
    }

    @Override
    public CollectionContainer<E> getContainer() {
        return container;
    }

    protected void containerItemChanged(CollectionContainer.ItemChangeEvent<E> event) {
        events.publish(DataGridSource.SelectedItemChangeEvent.class, new DataGridSource.SelectedItemChangeEvent<>(this, event.getItem()));
    }

    protected void containerCollectionChanged(CollectionContainer.CollectionChangeEvent<E> e) {
        events.publish(DataGridSource.ItemSetChangeEvent.class, new DataGridSource.ItemSetChangeEvent<>(this));
    }

    @SuppressWarnings("unchecked")
    protected void containerItemPropertyChanged(CollectionContainer.ItemPropertyChangeEvent<E> e) {
        events.publish(DataGridSource.ValueChangeEvent.class, new DataGridSource.ValueChangeEvent(this,
                e.getItem(), e.getProperty(), e.getPrevValue(), e.getValue()));
    }

    @Override
    public MetaClass getEntityMetaClass() {
        return container.getEntityMetaClass();
    }

    @Override
    public BindingState getState() {
        return BindingState.ACTIVE;
    }

    @Override
    public Object getItemId(E item) {
        return item.getId();
    }

    @Override
    public E getItem(@Nullable Object itemId) {
        return itemId == null ? null : container.getItemOrNull(itemId);
    }

    @Override
    public int indexOfItem(E item) {
        return container.getItemIndex(item.getId());
    }

    @Nullable
    @Override
    public E getItemByIndex(int index) {
        return container.getItems().get(index);
    }

    @Override
    public Stream<E> getItems() {
        return container.getItems().stream();
    }

    @Override
    public List<E> getItems(int startIndex, int numberOfItems) {
        return container.getItems().subList(startIndex, startIndex + numberOfItems);
    }

    @Override
    public boolean containsItem(E item) {
        return container.getItemOrNull(item.getId()) != null;
    }

    @Override
    public int size() {
        return container.getItems().size();
    }

    @Nullable
    @Override
    public E getSelectedItem() {
        return container.getItemOrNull();
    }

    @Override
    public void setSelectedItem(@Nullable E item) {
        container.setItem(item);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Subscription addStateChangeListener(Consumer<StateChangeEvent<E>> listener) {
        return events.subscribe(StateChangeEvent.class, (Consumer) listener);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Subscription addValueChangeListener(Consumer<ValueChangeEvent<E>> listener) {
        return events.subscribe(ValueChangeEvent.class, (Consumer) listener);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Subscription addItemSetChangeListener(Consumer<ItemSetChangeEvent<E>> listener) {
        return events.subscribe(ItemSetChangeEvent.class, (Consumer) listener);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Subscription addSelectedItemChangeListener(Consumer<SelectedItemChangeEvent<E>> listener) {
        return events.subscribe(SelectedItemChangeEvent.class, (Consumer) listener);
    }

    @Override
    public void sort(Object[] propertyId, boolean[] ascending) {
        container.getSorter().sort(createSort(propertyId, ascending));
    }

    protected Sort createSort(Object[] propertyId, boolean[] ascending) {
        List<Sort.Order> orders = new ArrayList<>();
        for (int i = 0; i < propertyId.length; i++) {
            String property;
            if (propertyId[i] instanceof MetaPropertyPath) {
                property = ((MetaPropertyPath) propertyId[i]).toPathString();
            } else {
                property = (String) propertyId[i];
            }
            Sort.Order order = ascending[i] ? Sort.Order.asc(property) : Sort.Order.desc(property);
            orders.add(order);
        }
        return Sort.by(orders);
    }

    @Override
    public void resetSortOrder() {
        container.getSorter().sort(Sort.UNSORTED);
    }
}