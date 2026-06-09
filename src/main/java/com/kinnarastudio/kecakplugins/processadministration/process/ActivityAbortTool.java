package com.kinnarastudio.kecakplugins.processadministration.process;

import org.joget.apps.app.service.AppUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import java.util.*;
import java.util.stream.Collectors;

public class ActivityAbortTool extends DefaultApplicationPlugin {
    public final static String LABEL = "Activity Abort Tool";

    @Override
    public String getName() {
        return LABEL;
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
    public Object execute(Map map) {
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        WorkflowAssignment assignment = (WorkflowAssignment) map.get("workflowAssignment");
        Collection<String> activityDefIds = getActivityDefIds();
        for (String activityDefId : activityDefIds) {
            workflowManager.activityAbort(assignment.getProcessId(), activityDefId);
        }

        return null;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/process/ActivityAbortTool.json", null, true, "");
    }

    protected Collection<String> getActivityDefIds() {
        return Optional.of("activityDefIds")
                .map(this::getPropertyString)
                .map(s -> s.split("[;,]"))
                .stream()
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());
    }
}
