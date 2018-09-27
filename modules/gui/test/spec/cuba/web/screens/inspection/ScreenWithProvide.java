/*
 * Copyright (c) 2008-2018 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/commercial-software-license for details.
 */

package spec.cuba.web.screens.inspection;

import com.haulmont.cuba.gui.components.Button;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.screen.Install;
import com.haulmont.cuba.gui.screen.Screen;
import com.haulmont.cuba.security.entity.User;

import java.util.Date;

public class ScreenWithProvide extends Screen {

    @Install(subject = "formatter", to = "label1")
    public String format(Date date) {
        return "";
    }

    @Install(type = Table.StyleProvider.class, to = "usersTable")
    protected String getCellStyleName(User user, String property) {
        return "red";
    }

    @Install
    private String getData() {
        return "";
    }

    @Install
    protected void ignoredMethod() {
    }

    @Install(to = "button1")
    protected void consumeEvent(Button.ClickEvent event) {

    }

    // todo validator support
}