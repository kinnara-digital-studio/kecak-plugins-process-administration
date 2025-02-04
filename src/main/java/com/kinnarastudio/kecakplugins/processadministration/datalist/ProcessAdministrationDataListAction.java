package com.kinnarastudio.kecakplugins.processadministration.datalist;

import com.kinnarastudio.commons.Declutter;
import org.enhydra.shark.api.common.SharkConstants;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.model.PackageActivityForm;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.workflow.lib.AssignmentCompleteButton;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.*;
import org.joget.workflow.model.dao.WorkflowProcessLinkDao;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProcessAdministrationDataListAction extends DataListActionDefault implements Declutter {

    private final static Map<String, Form> formCache = new WeakHashMap<>();

    @Override
    public String getLinkLabel() {
        String label = getPropertyString("label");
        if (label == null || label.isEmpty()) {
            label = getLabel();
        }
        return label;
    }

    @Override
    public String getHref() {
        return getPropertyString("href");
    }

    @Override
    public String getTarget() {
        return "post";
    }

    @Override
    public String getHrefParam() {
        return getPropertyString("hrefParam");
    }

    @Override
    public String getHrefColumn() {
        return getPropertyString("hrefColumn");
    }

    @Override
    public String getConfirmation() {
        String confirm = getPropertyString("confirmation");
        if (confirm == null || confirm.isEmpty()) {
            confirm = "Please Confirm";
        }
        return confirm;
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        // only allow POST
        if (!isPostMethod()) {
            return null;
        }

        final ApplicationContext appContext = AppUtil.getApplicationContext();
        final WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        final AppDefinitionDao appDefinitionDao = (AppDefinitionDao) appContext.getBean("appDefinitionDao");
        final AppDefinition currentAppDefinition = AppUtil.getCurrentAppDefinition();
        final AppService appService = (AppService) appContext.getBean("appService");
        final WorkflowUserManager workflowUserManager = (WorkflowUserManager) appContext.getBean("workflowUserManager");

        workflowUserManager.setCurrentThreadUser(WorkflowUtil.getCurrentUsername());

        final DataListActionResult result = new DataListActionResult();
        result.setType(DataListActionResult.TYPE_REDIRECT);
//        result.setUrl("REFERER");
        result.setUrl(this.getRedirectUrl());
        // complete assignment
        if ("complete".equalsIgnoreCase(getPropertyString("action"))) {
            final Map<String, String> worklfowVariables = Optional.ofNullable((Object[]) getProperty("workflowVariables"))
                    .stream()
                    .flatMap(Arrays::stream)
                    .map(o -> (Map<String, String>) o)
                    .collect(Collectors.toMap(m -> m.get("variable"), m -> AppUtil.processHashVariable(m.get("value"), null, null, null)));

            if (isForced()) {
                final String currentUsername = WorkflowUtil.getCurrentUsername();

                getOpenActivities(rowKeys)
                        .stream()
                        .filter(a -> a.getState().startsWith("open.not_running"))
                        .forEach(a -> {
                            final String activityId = a.getId();
                            // set workflow variables
                            workflowManager.activityVariables(activityId, worklfowVariables);

                            // complete assignment
                            workflowManager.assignmentForceComplete(a.getProcessDefId(), a.getProcessId(), activityId, currentUsername);
                        });
            } else {
                getAssignments(rowKeys).forEach(a -> {
                    if (!a.isAccepted()) {
                        workflowManager.assignmentAccept(a.getActivityId());
                    }

                    workflowManager.assignmentComplete(a.getActivityId(), worklfowVariables);
                });
            }
        } else if ("submit".equalsIgnoreCase(getPropertyString("action"))) {
            final Object[] formFields = (Object[]) getProperty("formFields");

            getAssignments(rowKeys).forEach(a -> {
                final FormData formData = new FormData();

                AppDefinition appDefinition = appService.getAppDefinitionForWorkflowProcess(a.getProcessId());
                PackageActivityForm activityForm = appService.viewAssignmentForm(appDefinition, a, formData, "", "");
                final Form form = activityForm.getForm();

                formData.setDoValidation(true);
                formData.addRequestParameterValues(FormUtil.getElementParameterName(form) + "_SUBMITTED", new String[]{""});
                formData.addRequestParameterValues(AssignmentCompleteButton.DEFAULT_ID, new String[]{"true"});

                if (formFields != null) {
                    Arrays.stream(formFields)
                            .map(o -> (Map<String, String>) o)
                            .map(m -> {
                                Map<String, String> field = new HashMap<>();
                                field.put(m.get("field"), AppUtil.processHashVariable(m.get("value"), a, null, null));
                                return field;
                            })
                            .map(Map::entrySet)
                            .flatMap(Collection::stream)
                            .forEach(e -> {
                                Element element = FormUtil.findElement(e.getKey(), form, formData, true);
                                if (element != null) {
                                    String parameterName = FormUtil.getElementParameterName(element);
                                    formData.addRequestParameterValues(parameterName, new String[]{e.getValue()});
                                }
                            });
                }

                appService
                        .completeAssignmentForm(form, a, formData, new HashMap<>())
                        .getFormErrors().forEach((field, message) -> {
                            LogUtil.error(getClassName(), null, "[" + getPropertyString("action").toUpperCase() + "] Error form [" + form.getPropertyString(FormUtil.PROPERTY_ID) + "] field [" + field + "] message [" + message + "]");
                        });
            });
        } else if ("reevaluate".equalsIgnoreCase(getPropertyString("action"))) {
            getAssignments(rowKeys)
                    .stream()
                    .map(WorkflowAssignment::getActivityId)
                    .forEach(workflowManager::reevaluateAssignmentsForActivity);

        } else if ("abort".equalsIgnoreCase(getPropertyString("action"))) {
            getRunningProcess(rowKeys).stream()
                    .map(WorkflowProcess::getInstanceId)
                    .peek(pid -> LogUtil.info(getClassName(), "[" + getPropertyString("action").toUpperCase() + "] process [" + pid + "]"))
                    .forEach(workflowManager::processAbort);
        } else if ("migrate".equalsIgnoreCase(getPropertyString("action"))) {
            final AppDefinition publishedAppDefinition = appDefinitionDao.loadVersion(currentAppDefinition.getAppId(), appDefinitionDao.getPublishedVersion(currentAppDefinition.getAppId()));

            getRunningProcess(rowKeys).stream()
                    .map(this::getGreatGreatGrandParent)
                    .map(WorkflowProcess::getInstanceId)
                    .distinct()
                    .map(workflowManager::getRunningProcessById)
                    .map(p -> {
                        String currentProcessDefId = p.getId();
                        String publishedProcessDefId = currentProcessDefId.replaceAll("#[0-9]+#", "#" + publishedAppDefinition.getPackageDefinition().getVersion() + "#");

                        // check if target process is the same as current process, UNLESS BEING FORCED
                        if (currentProcessDefId.equals(publishedProcessDefId) && !isForced()) {
                            // no need to migrate current process
                            return null;
                        }

                        LogUtil.info(getClassName(), "[" + getPropertyString("action").toUpperCase() + "] Migrating process [" + p.getInstanceId() + "] from [" + currentProcessDefId + "] to [" + publishedProcessDefId + "]");
                        return workflowManager.processCopyFromInstanceId(p.getInstanceId(), publishedProcessDefId, true);
                    })
                    .filter(Objects::nonNull)

                    // get process from process result
                    .map(WorkflowProcessResult::getProcess)
                    .filter(Objects::nonNull)

                    .map(WorkflowProcess::getInstanceId)
                    .filter(Objects::nonNull)

                    .peek(pid -> LogUtil.info(getClassName(), "[" + getPropertyString("action").toUpperCase() + "] New process [" + pid + "]"))

                    // get the latest activity, assume only handle the latest one
                    .map(pid -> workflowManager.getActivityList(pid, 0, 1, "dateCreated", true))
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)

                    // check status = open
                    .filter(activity -> checkIfMigrationVisible(activity, publishedAppDefinition))

                    // reevaluate process
                    .map(WorkflowActivity::getId)
                    .forEach(workflowManager::reevaluateAssignmentsForActivity);

        } else if ("viewGraph".equalsIgnoreCase(getPropertyString("action"))) {
            getRunningProcess(rowKeys).stream().map(WorkflowProcess::getInstanceId).forEach((p) -> {
                result.setUrl("/web/console/monitor/process/graph/" + p);
            });
        } else {
            LogUtil.warn(getClassName(), "Action [" + getPropertyString("action") + "] is not supported yet");
        }
        return result;
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
        return "Process Admin";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/ProcessAdministrationDataListAction.json", null, false, "/messages/ProcessAdministration");
    }

    private Form generateForm(AppDefinition appDef, String formDefId) {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        FormService formService = (FormService) appContext.getBean("formService");
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) appContext.getBean("formDefinitionDao");

        // check in cache
        if (formCache.containsKey(formDefId))
            return formCache.get(formDefId);

        // proceed without cache
        if (appDef != null && formDefId != null && !formDefId.isEmpty()) {
            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
            if (formDef != null) {
                String json = formDef.getJson();
                Form form = (Form) formService.createElementFromJson(json);

                if (form != null)
                    formCache.put(formDefId, form);

                return form;
            }
        }
        return null;
    }

    /**
     * get running assignments
     *
     * @param rowKeys
     * @return
     */
    protected List<WorkflowAssignment> getAssignments(String[] rowKeys) {
        final ApplicationContext appContext = AppUtil.getApplicationContext();
        final WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");

        return getRunningProcesses(rowKeys)
                .stream()
                .map(WorkflowProcess::getInstanceId)
                .map(workflowManager::getAssignmentByProcess)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    protected Collection<WorkflowProcess> getRunningProcesses(String[] rowKeys) {
        final ApplicationContext appContext = AppUtil.getApplicationContext();
        final WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        final WorkflowProcessLinkDao processLinkDao = (WorkflowProcessLinkDao) appContext.getBean("workflowProcessLinkDao");

        return Arrays.stream(rowKeys)
                .map(processLinkDao::getLinks)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(WorkflowProcessLink::getProcessId)
                .distinct()
                .map(workflowManager::getRunningProcessById)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }


    protected Collection<WorkflowActivity> getOpenActivities(String[] rowKeys) {
        final ApplicationContext appContext = AppUtil.getApplicationContext();
        final WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");

        return getRunningProcesses(rowKeys)
                .stream()
                .map(p -> workflowManager.getActivityList(p.getInstanceId(), 0, Integer.MAX_VALUE, null, null))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(a -> a.getState().startsWith("open"))
                .collect(Collectors.toSet());
    }

    protected boolean isForced() {
        return "true".equalsIgnoreCase(getPropertyString("forceAction"));
    }

    /**
     * get running process
     *
     * @param rowKeys
     * @return
     */
    protected Collection<WorkflowProcess> getRunningProcess(String[] rowKeys) {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        WorkflowProcessLinkDao processLinkDao = (WorkflowProcessLinkDao) appContext.getBean("workflowProcessLinkDao");

        return Arrays.stream(rowKeys)
                .map(processLinkDao::getLinks)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)

                .map(WorkflowProcessLink::getProcessId)
                .filter(Objects::nonNull)
                .distinct()

                .map(workflowManager::getRunningProcessById)
                .filter(Objects::nonNull)
                .filter(p -> p.getState() != null && p.getState().startsWith("open"))

                .collect(Collectors.toSet());
    }

    protected WorkflowProcess getGreatGreatGrandParent(@Nonnull WorkflowProcess process) {
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        return Optional.of(process)
                .map(WorkflowProcess::getInstanceId)
                .map(workflowManager::getWorkflowProcessLink)
                .map(WorkflowProcessLink::getParentProcessId)
                .map(workflowManager::getRunningProcessById)
                .filter(p -> Optional.of(p).map(WorkflowProcess::getState).map(s -> s.startsWith("open")).orElse(false))

                // recursively looking for the further parent
                .map(this::getGreatGreatGrandParent)

                .orElse(process);
    }

    /**
     * Check activity
     *
     * @return
     */
    protected boolean checkIfMigrationVisible(WorkflowActivity activity, AppDefinition targetAppDefinition) {
        return activity.getState().startsWith(SharkConstants.STATEPREFIX_OPEN);
    }

    protected String getRedirectUrl() {
        final HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
        return Optional.ofNullable(request)
                .map(r -> r.getHeader("Referer"))
                .map(s -> s.replaceAll("d-[0-9]+-ac=rowAction_[0-9]+(?=&)", ""))
                .orElse("REFERER");
    }

    protected boolean isPostMethod() {
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();

        return Optional.ofNullable(request)
                .map(HttpServletRequest::getMethod)
                .map("POST"::equalsIgnoreCase)
                .orElse(false);
    }
}
