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
package com.haulmont.cuba.gui.components;

import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.core.entity.Entity;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.Collection;
import java.util.function.Function;

public interface ListComponent<E extends Entity> extends Component, Component.BelongToFrame, ActionsHolder {

    boolean isMultiSelect();

    @Nullable
    E getSingleSelected();

    Set<E> getSelected();

    void setSelected(@Nullable E item);
    void setSelected(Collection<E> items);

    /**
     * @return metaclass that corresponds to data binding setup
     */
    @Nullable
    MetaClass getBindingMetaClass();

    CollectionDatasource getDatasource();

    /**
     * Allows to set icons for particular rows in the table.
     *
     * @param <E> entity class
     * @deprecated Use {@link Function} instead
     */
    @Deprecated
    interface IconProvider<E extends Entity> extends Function<E, String> {
        @Override
        default String apply(E entity) {
            return getItemIcon(entity);
        }

        /**
         * Called by {@link Table} to get an icon to be shown for a row.
         *
         * @param entity an entity instance represented by the current row
         * @return icon name or null to show no icon
         */
        @Nullable
        String getItemIcon(E entity);
    }

    interface ListComponentAction {
        void setListComponent(ListComponent listComponent);
    }
}