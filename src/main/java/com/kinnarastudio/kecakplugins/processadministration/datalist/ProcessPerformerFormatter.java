package com.kinnarastudio.kecakplugins.processadministration.datalist;

import com.kinnarastudio.kecakplugins.processadministration.lib.ProcessUtils;
import com.kinnarastudio.kecakplugins.processadministration.process.ProcessCompletionTool;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListColumnFormatDefault;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProcessPerformerFormatter extends DataListColumnFormatDefault implements ProcessUtils {
    @Override
    public String format(DataList dataList, DataListColumn column, Object row, Object value) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager)applicationContext.getBean("workflowManager");
        String primaryKey = Optional.of((Map<String, Object>)row).map(m -> m.get(dataList.getBinder().getPrimaryKeyColumnName())).map(String::valueOf).orElse("");
        return getCompletedWorkflowActivities(primaryKey, getPropertyActivityDefIds())
                .stream()
                .map(WorkflowActivity::getId)
                .map(workflowManager::getRunningActivityInfo)
                .filter(Objects::nonNull)
                .map(WorkflowActivity::getNameOfAcceptedUser)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(";"));
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
        return "Process Performer Formatter";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/ProcessPerformerFormatter.json", new String[] {ProcessCompletionTool.class.getName(), ProcessCompletionTool.class.getName()}, false, "/messages/ProcessAdministration");
    }

    /**
     * Get property "activityDefIds"
     *
     * @return
     */
    private Set<String> getPropertyActivityDefIds() {
        return Optional.of("activityDefIds")
                .map(this::getPropertyString)
                .map(s -> s.split(";"))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .collect(Collectors.toSet());
    }
}
