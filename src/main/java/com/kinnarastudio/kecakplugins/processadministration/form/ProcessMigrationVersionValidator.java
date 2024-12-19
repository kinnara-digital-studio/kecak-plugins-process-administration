package com.kinnarastudio.kecakplugins.processadministration.form;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormValidator;
import org.joget.apps.form.service.FormUtil;
import org.joget.plugin.base.PluginManager;

import java.util.ResourceBundle;

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
        return getLabel();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        return resourceBundle.getString("buildNumber");
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "Process Admin - Process Migration Version Validator";
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
