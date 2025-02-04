package com.kinnarastudio.kecakplugins.processadministration.userview;

import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.StringUtil;
import org.joget.commons.util.TimeZoneUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.plugin.enterprise.UniversalInboxMenu;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * View inbox of all users. Also can complete assignments
 */
public class SuperInboxUserviewMenu extends UniversalInboxMenu implements PluginWebSupport {
    public final static String NAME = "Super Inbox Menu";
    public final static String LABEL = "Super Inbox";

    @Override
    public String getCategory() {
        return AppPluginUtil.getMessage("processAdministration.processAdmin", getClassName(), "/messages/ProcessAdministration");
    }

    @Override
    public String getDecoratedMenu() {
        String menuItem = null;
        boolean showRowCount = Boolean.valueOf(getPropertyString("rowCount")).booleanValue();
        if (showRowCount) {
            int rowCount = getDataTotalRowCount();

            // sanitize label
            String label = getPropertyString("label");
            if (label != null) {
                label = StringUtil.stripHtmlRelaxed(label);
            }

            // generate menu link
            menuItem = "<a href=\"" + getUrl() + "\" class=\"menu-link default\"><span>" + label + "</span> <span class='pull-right badge rowCount'>" + rowCount + "</span></a>";
        }
        return menuItem;
    }

    @Override
    public String getName() {
        return NAME;
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
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public int getDataTotalRowCount() {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        int count = workflowManager.getRunningProcessSize(null, null, null, null);
        return count;
    }

    @Override
    protected DataListCollection getRows(DataList dataList) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        Collection<WorkflowProcess> runningProcesses = workflowManager.getRunningProcessList(null, null, null, null, null, null, null, null);

        return Optional.ofNullable(runningProcesses)
                .stream()
                .flatMap(Collection::stream)
                .map(p -> Optional.ofNullable(workflowManager.getActivityList(p.getInstanceId(), 0, -1, "id", false)))
                .flatMap(Optional::stream)
                .map(c -> c.stream().filter(a -> a.getState().startsWith("open")).findFirst())
                .flatMap(Optional::stream)
                .map(a -> new HashMap<String, Object>() {{
                    final WorkflowActivity trackWflowActivity = workflowManager.getRunningActivityInfo(a.getId());
                    final String format = AppUtil.getAppDateFormat();

                    put("processId", a.getProcessId());
                    put("processRequesterId", "");
                    put("activityId", a.getId());
                    put("processName", a.getProcessName());
                    put("activityName", a.getName());
                    put("processVersion", a.getProcessVersion());
                    put("dateCreated", TimeZoneUtil.convertToTimeZone(a.getCreatedTime(), null, format));
                    put("acceptedStatus", trackWflowActivity.getNameOfAcceptedUser() != null);
                    put("dueDate", trackWflowActivity.getDue() != null ? TimeZoneUtil.convertToTimeZone(trackWflowActivity.getDue(), null, format) : "-");

                    double serviceLevelMonitor = workflowManager.getServiceLevelMonitorForRunningActivity(a.getId());
                    put("serviceLevelMonitor", WorkflowUtil.getServiceLevelIndicator(serviceLevelMonitor));

                }})
                .collect(Collectors.toCollection(DataListCollection::new));
    }

    @Override
    protected void displayForm() {
        ApplicationContext ac = AppUtil.getApplicationContext();
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) ac.getBean("workflowUserManager");
        WorkflowManager workflowManager = (WorkflowManager) ac.getBean("workflowManager");

        String activityId = getRequestParameterString("activityId");
        Optional.ofNullable(activityId)
                .map(workflowManager::getRunningActivityInfo)
                .map(WorkflowActivity::getAssignmentUsers)
                .stream()
                .flatMap(Arrays::stream)
                .findFirst()
                .ifPresent(workflowUserManager::setCurrentThreadUser);

        super.displayForm();
    }

    @Override
    protected void submitForm() {
        ApplicationContext ac = AppUtil.getApplicationContext();
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) ac.getBean("workflowUserManager");
        WorkflowManager workflowManager = (WorkflowManager) ac.getBean("workflowManager");

        String activityId = getRequestParameterString("activityId");
        WorkflowActivity activity = workflowManager.getActivityById(activityId);
        if (activity == null) {
            this.setProperty("headerTitle", ResourceBundleUtil.getMessage("general.label.assignmentUnavailable"));
            this.setProperty("view", "assignmentFormUnavailable");
            this.setProperty("formHtml", ResourceBundleUtil.getMessage("general.label.assignmentUnavailable"));
            return;
        }

        String replaceWith = WorkflowUtil.getCurrentUsername();

        Optional.ofNullable(activityId)
                .map(workflowManager::getRunningActivityInfo)
                .map(WorkflowActivity::getAssignmentUsers)
                .stream()
                .flatMap(Arrays::stream)
                .findFirst()
                .ifPresent(replaceFrom -> workflowManager.assignmentReassign(activity.getProcessDefId(), activity.getProcessId(), activityId, replaceWith, replaceFrom));

        super.submitForm();
    }
}
