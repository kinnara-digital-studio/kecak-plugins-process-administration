package com.kinnarastudio.kecakplugins.processadministration.userview;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import org.joget.apps.app.model.PackageActivityForm;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListQueryParam;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.userview.lib.InboxMenu;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.StringUtil;
import org.joget.commons.util.TimeZoneUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * View inbox of all users. Also can complete assignments
 */
public class SuperInboxUserviewMenu extends InboxMenu {
    public final static String NAME = "Super Inbox Menu";
    public final static String LABEL = "Super Inbox";
    private DataList cacheDataList = null;

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
        String filterByProcess = getProcess();
        int count = workflowManager.getRunningProcessSize(null, filterByProcess, null, null);
        return count;
    }

    @Override
    protected DataListCollection getRows(DataList dataList) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        DataListQueryParam param = dataList.getQueryParam(null, null);

        String filterByProcess = getProcess();

        Collection<WorkflowProcess> runningProcesses = workflowManager.getRunningProcessList(null, filterByProcess, null, null, param.getSort(), param.getDesc(), param.getStart(), param.getSize());

        return Optional.ofNullable(runningProcesses)
                .stream()
                .flatMap(Collection::stream)
                .map(process -> firstOpenActivity(process)
                        .map(a -> new HashMap<String, Object>() {{
                            final WorkflowActivity trackWflowActivity = workflowManager.getRunningActivityInfo(a.getId());
                            final String format = AppUtil.getAppDateFormat();

                            put("processId", a.getProcessId());
                            put("processRequesterId", "");
                            put("activityId", a.getId());
                            put("name", process.getName());
                            put("activityName", a.getName());
                            put("processVersion", process.getVersion());
                            put("createdTime", TimeZoneUtil.convertToTimeZone(process.getStartedTime(), null, format));
                            put("acceptedStatus", trackWflowActivity.getNameOfAcceptedUser() != null);
                            put("dueDate", trackWflowActivity.getDue() != null ? TimeZoneUtil.convertToTimeZone(trackWflowActivity.getDue(), null, format) : "-");
                            put("pendingUsername", String.join(";", trackWflowActivity.getAssignmentUsers()));

                            double serviceLevelMonitor = workflowManager.getServiceLevelMonitorForRunningActivity(a.getId());
                            put("serviceLevelMonitor", WorkflowUtil.getServiceLevelIndicator(serviceLevelMonitor));
                        }})
                )
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(DataListCollection::new));

//        return Optional.ofNullable(runningProcesses)
//                .stream()
//                .flatMap(Collection::stream)
//                .map(this::firstOpenActivity)
//                .filter(Optional::isPresent)
//                .map(Optional::get)
//                .map(a -> new HashMap<String, Object>() {{
//                    final WorkflowActivity trackWflowActivity = workflowManager.getRunningActivityInfo(a.getId());
//                    final String format = AppUtil.getAppDateFormat();
//
//                    put("processId", a.getProcessId());
//                    put("processRequesterId", "");
//                    put("activityId", a.getId());
//                    put("name", a.getProcessName());
//                    put("activityName", a.getName());
//                    put("processVersion", a.getProcessVersion());
//                    put("createdTime", TimeZoneUtil.convertToTimeZone(a.getCreatedTime(), null, format));
//                    put("acceptedStatus", trackWflowActivity.getNameOfAcceptedUser() != null);
//                    put("dueDate", trackWflowActivity.getDue() != null ? TimeZoneUtil.convertToTimeZone(trackWflowActivity.getDue(), null, format) : "-");
//
//                    double serviceLevelMonitor = workflowManager.getServiceLevelMonitorForRunningActivity(a.getId());
//                    put("serviceLevelMonitor", WorkflowUtil.getServiceLevelIndicator(serviceLevelMonitor));
//
//                }})
//                .collect(Collectors.toCollection(DataListCollection::new));
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

    @Override
    protected Form submitAssignmentForm(FormData formData, WorkflowAssignment assignment, PackageActivityForm activityForm) {
        formData.setDoValidation(!ignoreValidation());
        return super.submitAssignmentForm(formData, assignment, activityForm);
    }

    @Override
    protected DataList getDataList() {
        if (cacheDataList == null) {
            // get datalist
            ApplicationContext ac = AppUtil.getApplicationContext();
            AppService appService = (AppService) ac.getBean("appService");
            DataListService dataListService = (DataListService) ac.getBean("dataListService");
            String target = "_self";
            if ("true".equalsIgnoreCase(getPropertyString("showPopup"))) {
                target = "popup";
            }
            String json = AppUtil.readPluginResource(getClass().getName(), "/templates/userview/SuperInboxMenuListJson.json", new String[]{target}, true, "message/userview/inboxMenu");
            cacheDataList = dataListService.fromJson(json);
        }
        return cacheDataList;
    }

    protected Optional<WorkflowActivity> firstOpenActivity(WorkflowProcess process) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");

        return Optional.ofNullable(workflowManager.getActivityList(process.getInstanceId(), 0, -1, "id", false))
                .stream()
                .flatMap(Collection::stream)
                .filter(a -> a.getState() != null && a.getState().startsWith("open"))
                .findFirst();
    }

    protected String getProcess() {
        return getPropertyString("processId");
    }

    protected boolean ignoreValidation() {
        return "true".equalsIgnoreCase(getPropertyString("ignoreValidation"));
    }

    @Override
    public String getPropertyOptions() {
        try {
            Stream<JSONObject> inboxJson = JSONStream.of(new JSONArray(super.getPropertyOptions()), Try.onBiFunction(JSONArray::getJSONObject));
            Stream<JSONObject> superInboxJson = JSONStream.of(new JSONArray(AppUtil.readPluginResource(getClassName(), "/properties/userview/SuperInboxUserviewMenu.json")), Try.onBiFunction(JSONArray::getJSONObject));
            return Stream.concat(inboxJson, superInboxJson)
                    .collect(JSONCollectors.toJSONArray()).toString();
        } catch (JSONException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return super.getPropertyOptions();
        }
    }
}
