package com.kinnarastudio.kecakplugins.processadministration.form;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();

        Pattern valuePattern = getValuePattern();
        Pattern labelPattern = getLabelPattern();

        String appId = isAllApps() ? null : appDefinition.getAppId();
        String appVersion = isAllApps() ? null : appDefinition.getVersion().toString();
        boolean showValueInLabel = showValueInLabel();
        FormRowSet result = Optional.ofNullable(workflowManager.getProcessList(appId, appVersion))
                .stream()
                .flatMap(Collection::stream)
                .map(p -> {
                    String value = p.getIdWithoutVersion();
                    String label = AppUtil.processHashVariable(p.getName(), null, null, null);
                    Matcher mValue = valuePattern.matcher(value);
                    Matcher mLabel = labelPattern.matcher(label + (showValueInLabel ? " (" + value + ")" : ""));

                    if (mValue.find() && mLabel.find()) {
                        return new FormRow() {{
                            put(FormUtil.PROPERTY_VALUE, value);
                            put(FormUtil.PROPERTY_LABEL, label);
                            put(FormUtil.PROPERTY_GROUPING, p.getPackageId());
                        }};
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(FormRowSet::new));

        String emptyOptionLabel = getEmptyOptionLabel();
        if (!emptyOptionLabel.isEmpty()) {
            result.add(0, new FormRow() {{
                put(FormUtil.PROPERTY_VALUE, "");
                put(FormUtil.PROPERTY_LABEL, emptyOptionLabel);
            }});
        }
        result.setMultiRow(true);

        return result;
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
        return AppUtil.readPluginResource(ProcessOptionsBinder.class.getName(), "/properties/form/ProcessOptionsBinder.json");
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.getParameterMap().forEach((key, value) -> setProperty(String.valueOf(key), String.valueOf(value)));

        JSONArray result = loadAjaxOptions(null)
                .stream()
                .filter(Objects::nonNull)
                .map(Try.onFunction(r -> {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(FormUtil.PROPERTY_VALUE, r.getProperty(FormUtil.PROPERTY_VALUE));
                    jsonObject.put(FormUtil.PROPERTY_LABEL, r.getProperty(FormUtil.PROPERTY_LABEL));
                    jsonObject.put(FormUtil.PROPERTY_GROUPING, r.getProperty(FormUtil.PROPERTY_GROUPING));
                    return jsonObject;
                }))
                .collect(JSONCollectors.toJSONArray());

        response.getWriter().write(result.toString());
    }

    protected boolean isAllApps() {
        return "true".equalsIgnoreCase(getPropertyString("allApps"));
    }

    protected Pattern getValuePattern() {
        String pattern = Optional.ofNullable(getPropertyString("valuePattern"))
                .filter(Predicate.not(String::isEmpty))
                .orElse(".*");
        return Pattern.compile(pattern);
    }

    protected Pattern getLabelPattern() {
        String pattern = Optional.ofNullable(getPropertyString("labelPattern"))
                .filter(Predicate.not(String::isEmpty))
                .orElse(".*");
        return Pattern.compile(pattern);
    }

    protected String getEmptyOptionLabel() {
        return getPropertyString("emptyOptionLabel");
    }

    protected boolean showValueInLabel() {
        return "true".equalsIgnoreCase(getPropertyString("showValueInLabel"));
    }
}
