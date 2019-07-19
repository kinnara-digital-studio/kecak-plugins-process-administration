package com.kinnara.kecakplugins.processadministration;

import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;

import java.util.Objects;

public class AppVersionOptionsBinder extends FormBinder implements FormLoadOptionsBinder, FormAjaxOptionsBinder {
    @Override
    public boolean useAjax() {
        return false;
    }

    @Override
    public FormRowSet loadAjaxOptions(String[] strings) {
        AppDefinitionDao appDefinitionDao = (AppDefinitionDao) AppUtil.getApplicationContext().getBean("appDefinitionDao");
        return appDefinitionDao.findByVersion(null, null, null, null, null, null, null, null)
                .stream()
                .filter(Objects::nonNull)
                .collect(FormRowSet::new, (rowSet, appDefinition) -> {
                    FormRow row = new FormRow();
                    row.put(FormUtil.PROPERTY_VALUE, appDefinition.getVersion());
                    row.put(FormUtil.PROPERTY_LABEL, "v" + appDefinition.getVersion() + (appDefinition.isPublished() ? " (Published)" : ""));
                    row.put(FormUtil.PROPERTY_GROUPING, appDefinition.getAppId().trim());
                    rowSet.add(row);
                }, FormRowSet::addAll);
    }

    @Override
    public FormRowSet load(Element element, String s, FormData formData) {
        return this.loadAjaxOptions(null);
    }

    @Override
    public String getName() {
        return "Process Admin - App Version Options Binder";
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
        return "";
    }
}
