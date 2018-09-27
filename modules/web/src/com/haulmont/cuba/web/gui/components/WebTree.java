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

package com.haulmont.cuba.web.gui.components;

import com.haulmont.bali.events.Subscription;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaProperty;
import com.haulmont.chile.core.model.MetaPropertyPath;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.MetadataTools;
import com.haulmont.cuba.core.global.Security;
import com.haulmont.cuba.gui.ComponentsHelper;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.LookupComponent.LookupSelectionChangeNotifier;
import com.haulmont.cuba.gui.components.actions.BaseAction;
import com.haulmont.cuba.gui.components.data.BindingState;
import com.haulmont.cuba.gui.components.data.EntityTreeSource;
import com.haulmont.cuba.gui.components.data.TreeSource;
import com.haulmont.cuba.gui.components.data.tree.HierarchicalDatasourceTreeAdapter;
import com.haulmont.cuba.gui.components.security.ActionsPermissions;
import com.haulmont.cuba.gui.components.sys.ShortcutsDelegate;
import com.haulmont.cuba.gui.components.sys.ShowInfoAction;
import com.haulmont.cuba.gui.theme.ThemeConstants;
import com.haulmont.cuba.gui.theme.ThemeConstantsManager;
import com.haulmont.cuba.web.gui.components.tree.TreeDataProvider;
import com.haulmont.cuba.web.gui.components.tree.TreeSourceEventsDelegate;
import com.haulmont.cuba.web.gui.components.util.ShortcutListenerDelegate;
import com.haulmont.cuba.web.gui.icons.IconResolver;
import com.haulmont.cuba.web.widgets.CubaCssActionsLayout;
import com.haulmont.cuba.web.widgets.CubaTree;
import com.haulmont.cuba.web.widgets.CubaUI;
import com.haulmont.cuba.web.widgets.addons.contextmenu.MenuItem;
import com.haulmont.cuba.web.widgets.grid.CubaGridContextMenu;
import com.haulmont.cuba.web.widgets.grid.CubaMultiSelectionModel;
import com.haulmont.cuba.web.widgets.grid.CubaSingleSelectionModel;
import com.haulmont.cuba.web.widgets.tree.EnhancedTreeDataProvider;
import com.vaadin.data.TreeData;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.event.ShortcutListener;
import com.vaadin.event.selection.SelectionEvent;
import com.vaadin.server.Resource;
import com.vaadin.server.Sizeable;
import com.vaadin.shared.Registration;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.Grid;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.StyleGenerator;
import com.vaadin.ui.components.grid.MultiSelectionModel;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.haulmont.bali.util.Preconditions.checkNotNullArgument;
import static com.haulmont.cuba.gui.ComponentsHelper.findActionById;

public class WebTree<E extends Entity>
        extends WebAbstractComponent<CubaTree<E>>
        implements Tree<E>, LookupSelectionChangeNotifier<E>, SecuredActionsHolder,
        HasInnerComponents, InitializingBean, TreeSourceEventsDelegate<E> {

    private static final String HAS_TOP_PANEL_STYLENAME = "has-top-panel";

    // Style names used by tree itself
    protected List<String> internalStyles = new ArrayList<>(2);

    protected List<Function<? super E, String>> styleProviders; // lazily initialized List
    protected StyleGenerator<E> styleGenerator;    // lazily initialized field

    protected CubaGridContextMenu<E> contextMenu;
    protected final List<WebAbstractDataGrid.ActionMenuItemWrapper> contextMenuItems = new ArrayList<>();

    protected ButtonsPanel buttonsPanel;
    protected HorizontalLayout topPanel;
    protected CubaCssActionsLayout componentComposition;
    protected Action enterPressAction;
    protected Function<? super E, String> iconProvider;

    /* Beans */
    protected Metadata metadata;
    protected Security security;
    protected IconResolver iconResolver;
    protected MetadataTools metadataTools;

    protected SelectionMode selectionMode;

    protected CaptionMode captionMode = CaptionMode.ITEM;
    protected String captionProperty;

    protected Action doubleClickAction;
    protected Registration itemClickListener;

    /* SecuredActionsHolder */
    protected final List<Action> actionList = new ArrayList<>();
    protected final ShortcutsDelegate<ShortcutListener> shortcutsDelegate;
    protected final ActionsPermissions actionsPermissions = new ActionsPermissions(this);

    protected boolean showIconsForPopupMenuActions;

    protected String hierarchyProperty;
    protected TreeDataProvider<E> dataBinding;

    public WebTree() {
        component = createComponent();
        componentComposition = createComponentComposition();
        shortcutsDelegate = createShortcutsDelegate();
    }

    protected CubaTree<E> createComponent() {
        return new CubaTree<>();
    }

    protected ShortcutsDelegate<ShortcutListener> createShortcutsDelegate() {
        return new ShortcutsDelegate<ShortcutListener>() {
            @Override
            protected ShortcutListener attachShortcut(String actionId, KeyCombination keyCombination) {
                ShortcutListener shortcut =
                        new ShortcutListenerDelegate(actionId, keyCombination.getKey().getCode(),
                                KeyCombination.Modifier.codes(keyCombination.getModifiers())
                        ).withHandler((sender, target) -> {
                            if (sender == componentComposition) {
                                Action action = getAction(actionId);
                                if (action != null && action.isEnabled() && action.isVisible()) {
                                    action.actionPerform(WebTree.this);
                                }
                            }
                        });

                componentComposition.addShortcutListener(shortcut);
                return shortcut;
            }

            @Override
            protected void detachShortcut(Action action, ShortcutListener shortcutDescriptor) {
                componentComposition.removeShortcutListener(shortcutDescriptor);
            }

            @Override
            protected Collection<Action> getActions() {
                return WebTree.this.getActions();
            }
        };
    }

    @Override
    public void afterPropertiesSet() {
        initComponentComposition(componentComposition);
        initComponent(component);

        initContextMenu();
    }

    protected void initComponentComposition(CubaCssActionsLayout componentComposition) {
        componentComposition.addShortcutListener(createEnterShortcutListener());
    }

    protected void initComponent(CubaTree<E> component) {
        component.setSizeFull();
        component.setItemCaptionGenerator(this::generateItemCaption);

        setSelectionMode(SelectionMode.SINGLE);
    }

    protected CubaCssActionsLayout createComponentComposition() {
        CubaCssActionsLayout composition = new CubaCssActionsLayout();
        composition.setPrimaryStyleName("c-tree-composition");
        composition.setWidthUndefined();
        composition.addComponent(component);

        return composition;
    }

    protected ShortcutListenerDelegate createEnterShortcutListener() {
        return new ShortcutListenerDelegate("treeEnter", KeyCode.ENTER, null)
                .withHandler((sender, target) -> {
                    if (sender == componentComposition) {
                        CubaUI ui = (CubaUI) componentComposition.getUI();
                        if (!ui.isAccessibleForUser(componentComposition)) {
                            LoggerFactory.getLogger(WebTree.class)
                                    .debug("Ignore click attempt because Tree is inaccessible for user");
                            return;
                        }

                        if (enterPressAction != null) {
                            enterPressAction.actionPerform(this);
                        } else {
                            handleClickAction();
                        }
                    }
                });
    }

    protected String generateItemCaption(E item) {
        if (item != null) {
            switch (captionMode) {
                case ITEM:
                    return metadataTools.getInstanceName(item);
                case PROPERTY:
                    MetaClass metaClass = metadata.getClassNN(item.getClass());
                    MetaPropertyPath metaPropertyPath =
                            metadataTools.resolveMetaPropertyPathNN(metaClass, captionProperty);
                    MetaProperty property = metaPropertyPath.getMetaProperty();
                    Object propertyValue = item.getValueEx(metaPropertyPath.toPathString());

                    return metadataTools.format(propertyValue, property);

                default:
                    throw new UnsupportedOperationException("'" + captionMode + "' mode is not supported");
            }
        }

        return "";
    }

    protected void initContextMenu() {
        contextMenu = new CubaGridContextMenu<>(component.getCompositionRoot());

        contextMenu.addGridBodyContextMenuListener(event -> {
            if (!component.getSelectedItems().contains(event.getItem())) {
                // In the multi select model 'setSelected' adds item to selected set,
                // but, in case of context click, we want to have a single selected item,
                // if it isn't in a set of already selected items
                if (isMultiSelect()) {
                    component.deselectAll();
                }
                //noinspection unchecked
                setSelected(event.getItem());
            }
        });
    }

    @Inject
    public void setSecurity(Security security) {
        this.security = security;
    }

    @Inject
    public void setIconResolver(IconResolver iconResolver) {
        this.iconResolver = iconResolver;
    }

    @Inject
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    @Inject
    public void setMetadataTools(MetadataTools metadataTools) {
        this.metadataTools = metadataTools;
    }

    @Inject
    public void setThemeConstantsManager(ThemeConstantsManager themeConstantsManager) {
        ThemeConstants theme = themeConstantsManager.getConstants();
        this.showIconsForPopupMenuActions = theme.getBoolean("cuba.gui.showIconsForPopupMenuActions", false);
    }

    @Override
    public TreeSource<E> getTreeSource() {
        return this.dataBinding != null ? this.dataBinding.getTreeSource() : null;
    }

    @Override
    public void setTreeSource(TreeSource<E> treeSource) {
        if (this.dataBinding != null) {
            this.dataBinding.unbind();
            this.dataBinding = null;

            this.component.setDataProvider(createEmptyDataProvider());
        }

        if (treeSource != null) {
            this.dataBinding = createDataGridDataProvider(treeSource);
            this.hierarchyProperty = treeSource.getHierarchyPropertyName();

            this.component.setDataProvider(this.dataBinding);

            initShowInfoAction();
            refreshActionsState();
        }
    }

    protected DataProvider<E, ?> createEmptyDataProvider() {
        return new EmptyTreeDataSource<>();
    }

    @Override
    public String getHierarchyProperty() {
        return hierarchyProperty;
    }

    @Override
    public Action getItemClickAction() {
        return doubleClickAction;
    }

    @Override
    public void setItemClickAction(Action action) {
        if (doubleClickAction != null) {
            removeAction(doubleClickAction);
        }

        if (!getActions().contains(action)) {
            addAction(action);
        }

        if (this.doubleClickAction != action) {
            if (action != null) {
                if (itemClickListener == null) {
                    itemClickListener = component.addItemClickListener(this::onItemClick);
                }
            } else if (itemClickListener != null) {
                itemClickListener.remove();
                itemClickListener = null;
            }

            this.doubleClickAction = action;
        }
    }

    protected void onItemClick(com.vaadin.ui.Tree.ItemClick<E> event) {
        if (event.getMouseEventDetails().isDoubleClick()) {
            CubaUI ui = (CubaUI) component.getUI();
            if (!ui.isAccessibleForUser(component)) {
                LoggerFactory.getLogger(WebTree.class)
                        .debug("Ignore click attempt because Tree is inaccessible for user");
                return;
            }

            if (doubleClickAction != null) {
                doubleClickAction.actionPerform(WebTree.this);
            }
        }
    }

    @Override
    public CaptionMode getCaptionMode() {
        return captionMode;
    }

    @Override
    public void setCaptionMode(CaptionMode captionMode) {
        if (this.captionMode != captionMode) {
            switch (captionMode) {
                case ITEM:
                case PROPERTY:
                    this.captionMode = captionMode;
                    component.repaint();
                    break;
                default:
                    throw new UnsupportedOperationException("'" + captionMode + "' mode is not supported");
            }
        }
    }

    @Override
    public String getCaptionProperty() {
        return captionProperty;
    }

    @Override
    public void setCaptionProperty(String captionProperty) {
        if (StringUtils.isEmpty(captionProperty)) {
            setCaptionMode(CaptionMode.ITEM);
        } else {
            this.captionProperty = captionProperty;
            setCaptionMode(CaptionMode.PROPERTY);
        }
    }

    protected void initShowInfoAction() {
        if (security.isSpecificPermitted(ShowInfoAction.ACTION_PERMISSION)) {
            if (getAction(ShowInfoAction.ACTION_ID) == null) {
                addAction(new ShowInfoAction());
            }
        }
    }

    protected void refreshActionsState() {
        for (Action action : getActions()) {
            action.refreshState();
        }
    }

    protected TreeDataProvider<E> createDataGridDataProvider(TreeSource<E> treeSource) {
        return new TreeDataProvider<>(treeSource, this);
    }

    @Override
    public void addAction(Action action) {
        int index = findActionById(actionList, action.getId());
        if (index < 0) {
            index = actionList.size();
        }

        addAction(action, index);
    }

    @Override
    public void addAction(Action action, int index) {
        checkNotNullArgument(action, "Action must be non null");

        int oldIndex = findActionById(actionList, action.getId());
        if (oldIndex >= 0) {
            removeAction(actionList.get(oldIndex));
            if (index > oldIndex) {
                index--;
            }
        }

        if (StringUtils.isNotEmpty(action.getCaption())) {
            WebAbstractDataGrid.ActionMenuItemWrapper menuItemWrapper = createContextMenuItem(action);
            menuItemWrapper.setAction(action);
            contextMenuItems.add(menuItemWrapper);
        }

        actionList.add(index, action);
        shortcutsDelegate.addAction(null, action);
        attachAction(action);
        actionsPermissions.apply(action);
    }

    protected WebAbstractDataGrid.ActionMenuItemWrapper createContextMenuItem(Action action) {
        MenuItem menuItem = contextMenu.addItem(action.getCaption(), null);
        menuItem.setStyleName("c-cm-item");

        return new WebAbstractDataGrid.ActionMenuItemWrapper(menuItem, showIconsForPopupMenuActions) {
            @Override
            public void performAction(Action action) {
                action.actionPerform(WebTree.this);
            }
        };
    }

    protected void attachAction(Action action) {
        if (action instanceof Action.HasTarget) {
            ((Action.HasTarget) action).setTarget(this);
        }

        action.refreshState();
    }

    @Override
    public void removeAction(@Nullable Action action) {
        if (actionList.remove(action)) {
            WebAbstractDataGrid.ActionMenuItemWrapper menuItemWrapper = null;
            for (WebAbstractDataGrid.ActionMenuItemWrapper menuItem : contextMenuItems) {
                if (menuItem.getAction() == action) {
                    menuItemWrapper = menuItem;
                    break;
                }
            }

            if (menuItemWrapper != null) {
                menuItemWrapper.setAction(null);
                contextMenu.removeItem(menuItemWrapper.getMenuItem());
            }

            shortcutsDelegate.removeAction(action);
        }
    }

    @Override
    public void removeAction(@Nullable String id) {
        Action action = getAction(id);
        if (action != null) {
            removeAction(action);
        }
    }

    @Override
    public void removeAllActions() {
        for (Action action : actionList.toArray(new Action[0])) {
            removeAction(action);
        }
    }

    @Override
    public Collection<Action> getActions() {
        return Collections.unmodifiableCollection(actionList);
    }

    @Nullable
    @Override
    public Action getAction(String id) {
        for (Action action : getActions()) {
            if (Objects.equals(action.getId(), id)) {
                return action;
            }
        }
        return null;
    }

    @Override
    public ActionsPermissions getActionsPermissions() {
        return actionsPermissions;
    }

    @Override
    public void treeSourceItemSetChanged(TreeSource.ItemSetChangeEvent<E> event) {
        // #PL-2035, reload selection from ds
        Set<E> selectedItems = getSelected();
        Set<E> newSelection = new HashSet<>();
        TreeSource<E> source = event.getSource();
        for (E item : selectedItems) {
            //noinspection unchecked
            if (source.containsItem(item)) {
                newSelection.add(source.getItem(item.getId()));
            }
        }

        if (source.getState() == BindingState.ACTIVE
                && source instanceof EntityTreeSource
                && ((EntityTreeSource<E>) source).getSelectedItem() != null) {
            newSelection.add(((EntityTreeSource<E>) source).getSelectedItem());
        }

        if (newSelection.isEmpty()) {
            setSelected((E) null);
        } else {
            // Workaround for the MultiSelect model.
            // Set the selected items only if the previous selection is different
            // Otherwise, the tree rows will display the values before editing
            if (isMultiSelect() && !selectedItems.equals(newSelection)) {
                setSelectedInternal(newSelection);
            }
        }

        refreshActionsState();
    }

    @Override
    public void treeSourcePropertyValueChanged(TreeSource.ValueChangeEvent<E> event) {
        refreshActionsState();
    }

    @Override
    public void treeSourceStateChanged(TreeSource.StateChangeEvent<E> event) {
        refreshActionsState();
    }

    @Override
    public void treeSourceSelectedItemChanged(TreeSource.SelectedItemChangeEvent<E> event) {
        refreshActionsState();
    }

    @Override
    public void collapseTree() {
        component.collapseAll();
    }

    @Override
    public void expandTree() {
        component.expandAll();
    }

    @Override
    public void collapse(Object itemId) {
        collapse(getTreeSource().getItem(itemId));
    }

    @Override
    public void collapse(E item) {
        component.collapseItemWithChildren(item);
    }

    @Override
    public void expand(Object itemId) {
        expand(getTreeSource().getItem(itemId));
    }

    @Override
    public void expand(E item) {
        component.expandItemWithParents(item);
    }

    @Override
    public void expandUpTo(int level) {
        component.expandUpTo(level);
    }

    @Override
    public boolean isExpanded(Object itemId) {
        return component.isExpanded(getTreeSource().getItem(itemId));
    }

    @Override
    public String getCaption() {
        return getComposition().getCaption();
    }

    @Override
    public void setCaption(String caption) {
        getComposition().setCaption(caption);
    }

    @Override
    public String getDescription() {
        return getComposition().getDescription();
    }

    @Override
    public void setDescription(String description) {
        if (getComposition() instanceof AbstractComponent) {
            ((AbstractComponent) getComposition()).setDescription(description);
        }
    }

    @Override
    public Collection<com.haulmont.cuba.gui.components.Component> getInnerComponents() {
        if (buttonsPanel != null) {
            return Collections.singletonList(buttonsPanel);
        }
        return Collections.emptyList();
    }

    @Override
    public ButtonsPanel getButtonsPanel() {
        return buttonsPanel;
    }

    @Override
    public com.vaadin.ui.Component getComposition() {
        return componentComposition;
    }

    @Override
    public void setButtonsPanel(ButtonsPanel panel) {
        if (buttonsPanel != null && topPanel != null) {
            topPanel.removeComponent(buttonsPanel.unwrap(com.vaadin.ui.Component.class));
            buttonsPanel.setParent(null);
        }
        buttonsPanel = panel;
        if (panel != null) {
            if (panel.getParent() != null && panel.getParent() != this) {
                throw new IllegalStateException("Component already has parent");
            }

            if (topPanel == null) {
                topPanel = createTopPanel();
                topPanel.setWidth(100, Sizeable.Unit.PERCENTAGE);
                componentComposition.addComponentAsFirst(topPanel);
            }
            topPanel.addComponent(panel.unwrap(com.vaadin.ui.Component.class));
            if (panel instanceof VisibilityChangeNotifier) {
                ((VisibilityChangeNotifier) panel).addVisibilityChangeListener(event ->
                        updateCompositionStylesTopPanelVisible()
                );
            }
            panel.setParent(this);
        }

        updateCompositionStylesTopPanelVisible();
    }

    protected HorizontalLayout createTopPanel() {
        HorizontalLayout topPanel = new HorizontalLayout();
        topPanel.setSpacing(false);
        topPanel.setMargin(false);
        topPanel.setStyleName("c-tree-top");
        return topPanel;
    }

    // if buttons panel becomes hidden we need to set top panel height to 0
    protected void updateCompositionStylesTopPanelVisible() {
        if (topPanel != null) {
            boolean hasChildren = topPanel.getComponentCount() > 0;
            boolean anyChildVisible = false;
            for (com.vaadin.ui.Component childComponent : topPanel) {
                if (childComponent.isVisible()) {
                    anyChildVisible = true;
                    break;
                }
            }
            boolean topPanelVisible = hasChildren && anyChildVisible;

            if (!topPanelVisible) {
                componentComposition.removeStyleName(HAS_TOP_PANEL_STYLENAME);

                internalStyles.remove(HAS_TOP_PANEL_STYLENAME);
            } else {
                componentComposition.addStyleName(HAS_TOP_PANEL_STYLENAME);

                if (!internalStyles.contains(HAS_TOP_PANEL_STYLENAME)) {
                    internalStyles.add(HAS_TOP_PANEL_STYLENAME);
                }
            }
        }
    }

    protected void handleClickAction() {
        Action action = getItemClickAction();
        if (action == null) {
            action = getEnterPressAction();
            if (action == null) {
                action = getAction("edit");
                if (action == null) {
                    action = getAction("view");
                }
            }
        }

        if (action != null && action.isEnabled()) {
            Window window = ComponentsHelper.getWindowImplementation(WebTree.this);
            if (window instanceof Window.Wrapper) {
                window = ((Window.Wrapper) window).getWrappedWindow();
            }

            if (!(window instanceof Window.Lookup)) {
                action.actionPerform(WebTree.this);
            } else {
                Window.Lookup lookup = (Window.Lookup) window;

                com.haulmont.cuba.gui.components.Component lookupComponent = lookup.getLookupComponent();
                if (lookupComponent != this)
                    action.actionPerform(WebTree.this);
                else if (action.getId().equals(Window.Lookup.LOOKUP_ITEM_CLICK_ACTION_ID)) {
                    action.actionPerform(WebTree.this);
                }
            }
        }
    }

    @Override
    public void setLookupSelectHandler(Consumer<Collection<E>> selectHandler) {
        Consumer<Action.ActionPerformedEvent> actionHandler = event ->  {
            Set<E> selected = getSelected();
            selectHandler.accept(selected);
        };

        setItemClickAction(new BaseAction(Window.Lookup.LOOKUP_ITEM_CLICK_ACTION_ID)
                .withHandler(actionHandler)
        );
    }

    @Override
    public Collection getLookupSelectedItems() {
        return getSelected();
    }

    @Override
    public void refresh() {
        TreeSource<E> treeSource = getTreeSource();
        if (treeSource instanceof HierarchicalDatasourceTreeAdapter) {
            ((HierarchicalDatasourceTreeAdapter) treeSource).getDatasource().refresh();
        }
    }

    @Override
    public void setStyleName(String name) {
        super.setStyleName(name);

        for (String internalStyle : internalStyles) {
            componentComposition.addStyleName(internalStyle);
        }
    }

    @Override
    public String getStyleName() {
        String styleName = super.getStyleName();
        for (String internalStyle : internalStyles) {
            styleName = styleName.replace(internalStyle, "");
        }
        return StringUtils.normalizeSpace(styleName);
    }

    @Override
    public void setStyleProvider(@Nullable Function<? super E, String> styleProvider) {
        if (styleProvider != null) {
            if (this.styleProviders == null) {
                this.styleProviders = new LinkedList<>();
            } else {
                this.styleProviders.clear();
            }

            this.styleProviders.add(styleProvider);
        } else {
            this.styleProviders = null;
        }

        updateStyleGenerator();
    }

    @Override
    public void addStyleProvider(Function<? super E, String> styleProvider) {
        if (this.styleProviders == null) {
            this.styleProviders = new LinkedList<>();
        }

        if (!this.styleProviders.contains(styleProvider)) {
            this.styleProviders.add(styleProvider);

            updateStyleGenerator();
        }
    }

    @Override
    public void removeStyleProvider(Function<? super E, String> styleProvider) {
        if (this.styleProviders != null) {
            if (this.styleProviders.remove(styleProvider)) {
                component.markAsDirty();
            }
        }
    }

    protected void updateStyleGenerator() {
        if (this.styleGenerator == null) {
            this.styleGenerator = this::getGeneratedStyle;
            component.setStyleGenerator(this.styleGenerator);
        } else {
            component.markAsDirty();
        }
    }

    protected String getGeneratedStyle(E item) {
        if (styleProviders == null) {
            return null;
        }

        StringBuilder joinedStyle = null;
        for (Function<? super E, String> styleProvider : styleProviders) {
            String styleName = styleProvider.apply(item);
            if (styleName != null) {
                if (joinedStyle == null) {
                    joinedStyle = new StringBuilder(styleName);
                } else {
                    joinedStyle.append(" ").append(styleName);
                }
            }
        }

        return joinedStyle != null ? joinedStyle.toString() : null;
    }

    @Override
    public void repaint() {
        component.markAsDirty();
    }

    @Override
    public void setIconProvider(Function<? super E, String> iconProvider) {
        if (this.iconProvider != iconProvider) {
            this.iconProvider = iconProvider;

            if (iconProvider == null) {
                component.setItemIconGenerator(item -> null);
            } else {
                component.setItemIconGenerator(this::getItemIcon);
            }
        }
    }

    protected Resource getItemIcon(E item) {
        if (item == null) {
            return null;
        }

        String resourceUrl = this.iconProvider.apply(item);
        return iconResolver.getIconResource(resourceUrl);
    }

    @Override
    public void setEnterPressAction(Action action) {
        enterPressAction = action;
    }

    @Override
    public Action getEnterPressAction() {
        return enterPressAction;
    }

    @Override
    public int getTabIndex() {
        return component.getTabIndex();
    }

    @Override
    public void setTabIndex(int tabIndex) {
        component.setTabIndex(tabIndex);
    }

    @Override
    public SelectionMode getSelectionMode() {
        return selectionMode;
    }

    @Override
    public void setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = selectionMode;
        switch (selectionMode) {
            case SINGLE:
                component.setGridSelectionModel(new CubaSingleSelectionModel<>());
                break;
            case MULTI:
                component.setGridSelectionModel(new CubaMultiSelectionModel<>());
                break;
            case NONE:
                component.setSelectionMode(Grid.SelectionMode.NONE);
                return;
        }

        // Every time we change selection mode, the new selection model is set,
        // so we need to add selection listener again.
        component.addSelectionListener(this::onSelectionChange);
    }

    protected void onSelectionChange(SelectionEvent<E> event) {
        TreeSource<E> treeSource = getTreeSource();

        if (treeSource == null
                || treeSource.getState() == BindingState.INACTIVE) {
            return;
        }

        Set<E> selected = getSelected();
        if (treeSource instanceof EntityTreeSource) {
            if (selected.isEmpty()) {
                ((EntityTreeSource<E>) treeSource).setSelectedItem(null);
            } else {
                // reset selection and select new item
                if (isMultiSelect()) {
                    ((EntityTreeSource<E>) treeSource).setSelectedItem(null);
                }

                E newItem = selected.iterator().next();
                ((EntityTreeSource<E>) treeSource).setSelectedItem(newItem);
            }
        }

        LookupSelectionChangeEvent selectionChangeEvent = new LookupSelectionChangeEvent(this);
        publish(LookupSelectionChangeEvent.class, selectionChangeEvent);

        // todo implement selection change events
    }

    @Override
    public boolean isMultiSelect() {
        return SelectionMode.MULTI.equals(selectionMode);
    }

    @Override
    public void setMultiSelect(boolean multiselect) {
        setSelectionMode(multiselect
                ? SelectionMode.MULTI
                : SelectionMode.SINGLE);
    }

    @Nullable
    @Override
    public E getSingleSelected() {
        final Set<E> selectedItems = component.getSelectedItems();
        return CollectionUtils.isNotEmpty(selectedItems)
                ? selectedItems.iterator().next()
                : null;
    }

    @Override
    public Set<E> getSelected() {
        final Set<E> selectedItems = component.getSelectedItems();
        return selectedItems != null
                ? selectedItems
                : Collections.emptySet();
    }

    @Override
    public void setSelected(@Nullable E item) {
        if (SelectionMode.NONE.equals(getSelectionMode())) {
            return;
        }

        if (item == null) {
            component.deselectAll();
        } else {
            setSelected(Collections.singletonList(item));
        }
    }

    @Override
    public void setSelected(Collection<E> items) {
        TreeSource<E> treeSource = getTreeSource();

        boolean allMatch = items.stream()
                .allMatch(treeSource::containsItem);

        if (!allMatch) {
            throw new IllegalStateException("Datasource doesn't contain items");
        }

        setSelectedInternal(items);
    }

    @Nullable
    @Override
    public MetaClass getBindingMetaClass() {
        if (getTreeSource() instanceof EntityTreeSource) {
            return ((EntityTreeSource<E>) getTreeSource()).getEntityMetaClass();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected void setSelectedInternal(Collection<E> items) {
        switch (selectionMode) {
            case SINGLE:
                if (items.size() > 0) {
                    E item = items.iterator().next();
                    component.select(item);
                } else {
                    component.deselectAll();
                }
                break;
            case MULTI:
                component.deselectAll();
                ((MultiSelectionModel) component.getSelectionModel()).selectItems(items.toArray());
                break;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Subscription addLookupValueChangeListener(Consumer<LookupSelectionChangeEvent<E>> listener) {
        return getEventHub().subscribe(LookupSelectionChangeEvent.class, (Consumer) listener);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void removeLookupValueChangeListener(Consumer<LookupSelectionChangeEvent<E>> listener) {
        unsubscribe(LookupSelectionChangeEvent.class, (Consumer) listener);
    }

    @Override
    public void focus() {
        component.focus();
    }

    protected class EmptyTreeDataSource<T>
            extends com.vaadin.data.provider.TreeDataProvider<T>
            implements EnhancedTreeDataProvider<T> {

        public EmptyTreeDataSource() {
            super(new TreeData<>());
        }

        @Override
        public Stream<T> getItems() {
            return Stream.empty();
        }

        @Override
        public T getParent(T item) {
            return null;
        }
    }
}