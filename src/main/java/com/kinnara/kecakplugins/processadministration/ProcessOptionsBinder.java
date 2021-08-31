package com.kinnara.kecakplugins.processadministration;

import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

public class ProcessOptionsBinder extends FormBinder implements FormLoadOptionsBinder, FormAjaxOptionsBinder, PluginWebSupport {
    @Override
    public boolean useAjax() {
        return false;
    }

    @Override
    @Nonnull
    public FormRowSet loadAjaxOptions(String[] dependencyValues, FormData formData) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        AppDefinitionDao appDefinitionDao = (AppDefinitionDao) applicationContext.getBean("appDefinitionDao");
        return appDefinitionDao.findByVersion(null, null, null, null, null, null, null, null)
                .stream()
                .filter(Objects::nonNull)
                .map(AppDefinition::getPackageDefinition)
                .filter(Objects::nonNull)
                .collect(FormRowSet::new, (rs, pd) -> {
                    workflowManager.getProcessList(pd.getAppId(), pd.getVersion().toString())
                            .forEach(p -> {
                                FormRow row = new FormRow();
                                row.put(FormUtil.PROPERTY_VALUE, p.getIdWithoutVersion());
                                row.put(FormUtil.PROPERTY_LABEL, p.getName() + " (" + p.getIdWithoutVersion() + ")");
                                row.put(FormUtil.PROPERTY_GROUPING, pd.getAppId());
                                rs.add(row);
                            });
                }, FormRowSet::addAll);
    }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        return loadAjaxOptions(null, formData);
    }

    @Override
    public String getName() {
        return getClass().getName();
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
        return "Process Admin - Process Options Binder";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        JSONArray result = loadAjaxOptions(null, null).stream()
                .map(r -> {
                    try {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put(FormUtil.PROPERTY_VALUE, r.getProperty(FormUtil.PROPERTY_VALUE));
                        jsonObject.put(FormUtil.PROPERTY_LABEL, r.getProperty(FormUtil.PROPERTY_LABEL));
                        jsonObject.put(FormUtil.PROPERTY_GROUPING, r.getProperty(FormUtil.PROPERTY_GROUPING));
                        return jsonObject;
                    }catch (JSONException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(
                        JSONArray::new,
                        JSONArray::put,
                        (a1, a2) -> {
                            for(int i = 0, size = a2.length(); i < size; i++)
                                a1.put(a2.optJSONObject(i));
                        });

        response.getWriter().write(result.toString());
    }
}
