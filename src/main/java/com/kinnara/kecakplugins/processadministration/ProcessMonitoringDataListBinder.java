package com.kinnara.kecakplugins.processadministration;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.commons.util.TimeZoneUtil;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProcessMonitoringDataListBinder extends DataListBinderDefault {
    @Override
    public DataListColumn[] getColumns() {
        return new DataListColumn[] {
                new DataListColumn("id", AppPluginUtil.getMessage("console.app.process.common.label.id", getClassName(), "/messages/console"), true),
                new DataListColumn("name", AppPluginUtil.getMessage("console.app.process.common.label.name", getClassName(), "/messages/console"), true),
                new DataListColumn("state", AppPluginUtil.getMessage("console.app.process.common.label.state", getClassName(), "/messages/console"), true),
                new DataListColumn("version", AppPluginUtil.getMessage("console.app.process.common.label.version", getClassName(), "/messages/console"), true),
                new DataListColumn("startedTime", AppPluginUtil.getMessage("console.app.process.common.label.startedTime", getClassName(), "/messages/console"), true),
                new DataListColumn("requesterId", AppPluginUtil.getMessage("console.app.process.common.label.requester", getClassName(), "/messages/console"), true),
                new DataListColumn("due", AppPluginUtil.getMessage("console.app.process.common.label.dueDate", getClassName(), "/messages/console"), true),
                new DataListColumn("serviceLevelMonitor", AppPluginUtil.getMessage("console.app.process.common.label.serviceLevelMonitor", getClassName(), "/messages/console"), true),
                new DataListColumn("activityId", AppPluginUtil.getMessage("console.app.activity.common.label.id", getClassName(), "/messages/console"), true),
                new DataListColumn("activityName", AppPluginUtil.getMessage("console.app.activity.common.label.name", getClassName(), "/messages/console"), true),
                new DataListColumn("assignmentUsers", AppPluginUtil.getMessage("console.app.activity.common.label.listOfPending", getClassName(), "/messages/console"), true)
        };
    }

    @Override
    public String getPrimaryKeyColumnName() {
        return "id";
    }

    @Override
    public DataListCollection getData(DataList dataList, Map properties, DataListFilterQueryObject[] dataListFilterQueryObjects, String s, Boolean aBoolean, Integer integer, Integer integer1) {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        String processDefId = properties.get("processDefId") == null ? null : properties.get("processDefId").toString();

        return workflowManager.getRunningProcessList(appDefinition.getAppId(), processDefId, null, null, null, null, null, null)
                .stream()
                .map(workflowProcess -> {
                    final Map<String, Object> data = new HashMap<>();

                    double serviceLevelMonitor = workflowManager.getServiceLevelMonitorForRunningProcess(workflowProcess.getInstanceId());
                    data.put("id", workflowProcess.getInstanceId());
                    data.put("name", workflowProcess.getName());
                    data.put("state", workflowProcess.getState());
                    data.put("version", workflowProcess.getVersion());
                    data.put("startedTime", TimeZoneUtil.convertToTimeZone(workflowProcess.getStartedTime(), null, "yyyy-MM-dd hh:mm"));
                    data.put("requesterId", workflowProcess.getRequesterId());
                    data.put("due", workflowProcess.getDue() != null ? TimeZoneUtil.convertToTimeZone(workflowProcess.getDue(), null, AppUtil.getAppDateFormat()) : "-");
                    data.put("serviceLevelMonitor", WorkflowUtil.getServiceLevelIndicator(serviceLevelMonitor));

                    Optional.ofNullable(workflowManager.getActivityList(workflowProcess.getInstanceId(), 0, 1, "dateCreated", true))
                            .map(Collection::stream)
                            .orElse(Stream.empty())
                            .peek(a -> a.setAssignmentUsers(workflowManager.getRunningActivityInfo(a.getId()).getAssignmentUsers()))
                            .findFirst()
                            .ifPresent(a -> {
                                data.put("activityId", a.getId());
                                data.put("activityName", a.getName());
                                data.put("assignmentUsers", String.join(";", a.getAssignmentUsers()));
                            });


                    return data;
                })
                .collect(Collectors.toCollection(DataListCollection::new));

    }

    @Override
    public int getDataTotalRowCount(DataList dataList, Map properties, DataListFilterQueryObject[] dataListFilterQueryObjects) {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");

        String processDefId = properties.get("processDefId") == null ? null : properties.get("processDefId").toString();
        return workflowManager.getRunningProcessSize(appDefinition.getAppId(), processDefId, null, null);
    }

    @Override
    public String getName() {
        return "Process Admin - Process Monitoring DataList Binder";
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
        return AppUtil.readPluginResource(getClassName(), "/properties/ProcessMonitoringDataListBinder.json", null, false, "/messages/ProcessAdministration");
    }
}
