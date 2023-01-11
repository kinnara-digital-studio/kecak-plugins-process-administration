package com.kinnara.kecakplugins.processadministration;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.plugin.base.PluginManager;
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
import java.util.ResourceBundle;

public class ProcessOptionsBinder extends FormBinder implements FormLoadOptionsBinder, FormAjaxOptionsBinder, PluginWebSupport {
    @Override
    public boolean useAjax() {
        return false;
    }

    @Override
    @Nonnull
    public FormRowSet loadAjaxOptions(String[] dependencyValues) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        AppDefinitionDao appDefinitionDao = (AppDefinitionDao) applicationContext.getBean("appDefinitionDao");
        return appDefinitionDao.findByVersion(null, null, null, null, null, null, null, null)
                .stream()
                .filter(Objects::nonNull)
                .map(AppDefinition::getPackageDefinition)
                .filter(Objects::nonNull)
                .collect(FormRowSet::new, (rs, pd) -> workflowManager.getProcessList(pd.getAppId(), pd.getVersion().toString())
                        .forEach(p -> {
                            FormRow row = new FormRow();
                            row.put(FormUtil.PROPERTY_VALUE, p.getIdWithoutVersion());
                            row.put(FormUtil.PROPERTY_LABEL, p.getName() + " (" + p.getIdWithoutVersion() + ")");
                            row.put(FormUtil.PROPERTY_GROUPING, pd.getAppId());
                            rs.add(row);
                        }), FormRowSet::addAll);
    }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        setFormData(formData);
        return loadAjaxOptions(null);
    }

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;
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
        JSONArray result = loadAjaxOptions(null).stream()
                .map(Try.onFunction(r -> {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(FormUtil.PROPERTY_VALUE, r.getProperty(FormUtil.PROPERTY_VALUE));
                    jsonObject.put(FormUtil.PROPERTY_LABEL, r.getProperty(FormUtil.PROPERTY_LABEL));
                    jsonObject.put(FormUtil.PROPERTY_GROUPING, r.getProperty(FormUtil.PROPERTY_GROUPING));
                    return jsonObject;
                }))
                .filter(Objects::nonNull)
                .collect(JSONCollectors.toJSONArray());

        response.getWriter().write(result.toString());
    }
}
