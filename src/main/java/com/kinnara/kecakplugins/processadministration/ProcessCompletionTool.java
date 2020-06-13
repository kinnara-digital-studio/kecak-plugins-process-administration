package com.kinnara.kecakplugins.processadministration;

import com.kinnara.kecakplugins.processadministration.exception.ProcessException;
import com.kinnara.kecakplugins.processadministration.lib.ProcessUtils;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageActivityForm;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.*;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 *
 * Complete other pending assignment
 *
 */
public class ProcessCompletionTool extends DefaultApplicationPlugin implements ProcessUtils, PluginWebSupport {
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
    public Object execute(Map props) {
        try {
            String currentUser = getAsUser(props);
            getAssignmentByProcess(getAssignmentProcessId(props), getActivities(props), isForce(props) ? null : currentUser)
                    .forEach(throwableConsumer(assignment -> assignmentComplete(props, assignment, getWorkflowVariables(props)), (ProcessException e) -> LogUtil.error(getClass().getName(), e, e.getMessage())));
        } catch (ProcessException e) {
            LogUtil.error(getClass().getName(), e, e.getMessage());
        }
        return null;
    }

    private void assignmentComplete(@Nonnull Map properties, @Nonnull WorkflowAssignment assignment, Map<String, String> variableMap) throws ProcessException {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) applicationContext.getBean("workflowUserManager");
        AppDefinition appDefinition = getApplicationDefinition(assignment);
        AppService appService = (AppService) applicationContext.getBean("appService");

        String username = getAsUser(properties);
        if(isForce(properties)) {
            workflowUserManager.setCurrentThreadUser(assignment.getAssigneeName());
            workflowManager.assignmentReassign(assignment.getProcessDefId(), assignment.getProcessId(), assignment.getActivityId(), username, assignment.getAssigneeName());
        }

        LogUtil.info(getClass().getName(), "Completing assignment [" + assignment.getActivityId() + "]");

        workflowUserManager.setCurrentThreadUser(username);

        final FormData formData = new FormData();
        Form form = Optional.ofNullable(appService.viewAssignmentForm(appDefinition, assignment, formData, ""))
                .map(PackageActivityForm::getForm)
                .orElse(null);

        if(form == null) {
            if (!assignment.isAccepted()) {
                workflowManager.assignmentAccept(assignment.getActivityId());
            }
            workflowManager.assignmentComplete(assignment.getActivityId(), variableMap);
        } else {
            elementStream(form, formData)
                    .filter(e -> variableMap.containsKey(e.getPropertyString("workflowVariable")))
                    .forEach(element -> {
                        String variableName = element.getPropertyString("workflowVariable");
                        String parameterName = FormUtil.getElementParameterName(element);
                        String parameterValue = variableMap.get(variableName);

                        formData.addRequestParameterValues(parameterName, new String[]{parameterValue});
                    });

            FormData resultFormData = appService.completeAssignmentForm(form, assignment, formData, variableMap);
            String errorMessage = Optional.ofNullable(resultFormData)
                    .map(FormData::getFileErrors)
                    .map(Map::entrySet)
                    .map(Collection::stream)
                    .orElseGet(Stream::empty)
                    .map(e -> String.format("Field [%s] message [%s]", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(", "));

            if (!errorMessage.isEmpty()) {
                throw new ProcessException(errorMessage);
            }
        }
    }

    @Override
    public String getLabel() {
        return "Process Admin - Process Completion Tool";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/ProcessCompletionTool.json", new Object[]{ getClassName(), getClassName() }, false, "/messages/ProcessAdministration");
    }


    /**
     * Get proeprty "processId"
     *
     * @param prop
     * @return
     */
    @Nonnull
    private String getProcessId(Map prop) {
        return String.valueOf(prop.get("processId"));
    }

    /**
     * Get property "activityDefId"
     *
     * @param props
     * @return
     */
    @Nonnull
    private Set<String> getActivities(Map props) {
        return Optional.ofNullable(props)
                .map(m -> m.get("activityDefIds"))
                .map(String::valueOf)
                .map(s -> s.split(";"))
                .map(Arrays::stream)
                .orElse(Stream.empty())
                .collect(Collectors.toSet());
    }

    /**
     * Get property "force"
     *
     * @param props
     * @return
     */
    private boolean isForce(Map props) {
        return "true".equalsIgnoreCase(String.valueOf(props.get("force")));
    }

    /**
     * Get property "asUser"
     *
     * @param props
     * @return
     */
    @Nonnull
    private String getAsUser(Map props) throws ProcessException {
        String username = String.valueOf(props.get("asUser"));
        return getUser(username).getUsername();
    }


    /**
     * Get property "workflowVariables"
     *
     * @return
     */
    private Map<String, String> getWorkflowVariables(Map props) {
        return Optional.ofNullable(props.get("workflowVariables"))
                .map(o -> (Object[])o)
                .map(Arrays::stream)
                .orElse(Stream.empty())
                .map(o -> (Map<String, Object>)o)
                .collect(HashMap::new, (stringString, stringObject) -> {
                    String variable = String.valueOf(stringObject.get("variable"));
                    String value = String.valueOf(stringObject.get("value"));
                    stringString.put(variable, value);
                }, Map::putAll);
    }

    /**
     * Get property "processInstanceId"
     *
     * @param props
     * @return
     */
    @Nonnull
    private String getProcessInstanceId(Map props) {
        return String.valueOf(props.get("processInstanceId"));
    }

    private String getAssignmentProcessId(Map props) throws ProcessException {
        String processInstanceId = getLatestProcessId(getProcessInstanceId(props));
        if(!processInstanceId.isEmpty()) {
            return processInstanceId;
        } else {
            WorkflowAssignment workflowAssignment = (WorkflowAssignment) props.get("workflowAssignment");
            return Optional.ofNullable(workflowAssignment)
                    .map(WorkflowAssignment::getProcessId)
                    .orElseThrow(() -> new ProcessException("Assignment for current process instance"));
        }
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        boolean isAdmin = WorkflowUtil.isCurrentUserInRole("ROLE_ADMIN");
        if (!isAdmin) {
            response.sendError(401);
            return;
        }

        String action = request.getParameter("action");
        String appId = request.getParameter("appId");
        String appVersion = request.getParameter("appVersion");
        ApplicationContext ac = AppUtil.getApplicationContext();
        AppService appService = (AppService) ac.getBean("appService");
        WorkflowManager workflowManager = (WorkflowManager) ac.getBean("workflowManager");
        AppDefinition appDef = appService.getAppDefinition(appId, appVersion);

        // get processes
        if ("getProcesses".equals(action)) {
            try {
                JSONArray jsonArray = new JSONArray();
                PackageDefinition packageDefinition = appDef.getPackageDefinition();
                Long packageVersion = packageDefinition != null ? packageDefinition.getVersion() : new Long(1);
                Collection<WorkflowProcess> processList = workflowManager.getProcessList(appId, packageVersion.toString());
                HashMap<String, String> empty = new HashMap<>();
                empty.put("value", "");
                empty.put("label", "");
                jsonArray.put(empty);
                for (WorkflowProcess p : processList) {
                    HashMap<String, String> option = new HashMap<String, String>();
                    option.put("value", p.getIdWithoutVersion());
                    option.put("label", p.getName() + " (" + p.getIdWithoutVersion() + ")");
                    jsonArray.put(option);
                }
                jsonArray.write(response.getWriter());
            } catch (Exception ex) {
                LogUtil.error(getClass().getName(), ex, "Get Process options Error!");
            }
        }

        // get activities
        else if ("getActivities".equals(action)) {
            try {
                JSONArray jsonArray = new JSONArray();
                HashMap<String, String> empty = new HashMap<String, String>();
                empty.put("value", "");
                empty.put("label", "");
                jsonArray.put(empty);
                String processId = request.getParameter("processId");
                if (!"null".equalsIgnoreCase(processId) && !processId.isEmpty()) {
                    String processDefId = "";
                    if (appDef != null) {
                        WorkflowProcess process = appService.getWorkflowProcessForApp(appDef.getId(), appDef.getVersion().toString(), processId);
                        processDefId = process.getId();
                    }
                    Collection<WorkflowActivity> activityList = workflowManager.getProcessActivityDefinitionList(processDefId);
                    for (WorkflowActivity a : activityList) {
                        if (a.getType().equals("route") || a.getType().equals("tool")) continue;
                        HashMap<String, String> option = new HashMap<String, String>();
                        option.put("value", a.getActivityDefId());
                        option.put("label", a.getName() + " (" + a.getActivityDefId() + ")");
                        jsonArray.put(option);
                    }
                }
                jsonArray.write(response.getWriter());
            } catch (Exception ex) {
                LogUtil.error(getClass().getName(), ex, "Get activity options Error!");
            }
        }

        // get participants
        else if ("getParticipants".equalsIgnoreCase(action)) {
            try {
                String processId = request.getParameter("processId");
                String packageId = appDef.getPackageDefinition().getId();
                long packageVersion = appDef.getPackageDefinition().getVersion();

                JSONArray jsonArray = new JSONArray(Optional.ofNullable(processId)
                        .filter(s -> !s.isEmpty())
                        .map(s -> String.join("#", packageId, String.valueOf(packageVersion), s))
                        .map(workflowManager::getProcessParticipantDefinitionList)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .map(throwableFunction(p -> {
                            JSONObject json = new JSONObject();
                            json.put("value", p.getId());
                            json.put("label", p.getName());
                            return json;
                        }))
                        .collect(Collectors.toList()));

                jsonArray.write(response.getWriter());
            } catch (Exception ex) {
                LogUtil.error(getClass().getName(), ex, "Get participants options Error!");
            }
        }

        // get variables
        else if ("getVariables".equalsIgnoreCase(action)) {
            try {
                String processId = request.getParameter("processId");
                String packageId = appDef.getPackageDefinition().getId();
                long packageVersion = appDef.getPackageDefinition().getVersion();
                JSONArray jsonArray = new JSONArray(Optional.ofNullable(processId)
                        .filter(s -> !s.isEmpty())
                        .map(s -> String.join("#", packageId, String.valueOf(packageVersion), s))
                        .map(workflowManager::getProcessVariableDefinitionList)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .map(WorkflowVariable::getId)
                        .map(throwableFunction(s -> {
                            JSONObject json = new JSONObject();
                            json.put("value", s);
                            json.put("label", s);
                            return json;
                        }))
                        .collect(Collectors.toList()));

                jsonArray.write(response.getWriter());
            } catch (Exception ex) {
                LogUtil.error(getClass().getName(), ex, "Get variables options Error!");
            }
        } else {
            response.setStatus(204);
        }
    }
}
