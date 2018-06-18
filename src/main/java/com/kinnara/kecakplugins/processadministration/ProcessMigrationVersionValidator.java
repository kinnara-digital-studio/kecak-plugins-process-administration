package com.kinnara.kecakplugins.processadministration;

import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormValidator;
import org.joget.apps.form.service.FormUtil;

public class ProcessMigrationVersionValidator extends FormValidator {
    @Override
    public boolean validate(Element element, FormData data, String[] values) {
        Form rootForm = FormUtil.findRootForm(element);

        long fromAppVersion = Long.valueOf(FormUtil.getElementPropertyValue(FormUtil.findElement("app_version_from", rootForm, data), data));
        long toAppVersion = Long.valueOf(FormUtil.getElementPropertyValue(FormUtil.findElement("app_version_to", rootForm, data), data));

        if(fromAppVersion == toAppVersion) {
            data.addFormError(FormUtil.getElementParameterName(element), "Error migrating to the same version");
            return false;
        }

        return true;
    }

    @Override
    public String getName() {
        return AppPluginUtil.getMessage("processAdministration.processMigrationVersionValidator", getClassName(), "/messages/ProcessAdministration");
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return null;
    }
}
