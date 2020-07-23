package com.kinnara.kecakplugins.processadministration;

import com.kinnara.kecakplugins.processadministration.exception.ProcessException;
import com.kinnara.kecakplugins.processadministration.exception.RestApiException;
import com.kinnara.kecakplugins.processadministration.lib.ProcessUtils;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageActivityForm;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.User;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowVariable;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author aristo
 *
 * Complete other pending assignments
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
            getAsUser(props)
                    .forEach(throwableConsumer(currentUser -> getAssignmentByProcess(getAssignmentProcessId(props), getActivities(props), currentUser)
                    .forEach(throwableConsumer(assignment -> assignmentComplete(assignment, getWorkflowVariables(props), currentUser)))));
        } catch (ProcessException e) {
            LogUtil.error(getClass().getName(), e, e.getMessage());
        }
        return null;
    }

    private void assignmentComplete(@Nonnull WorkflowAssignment assignment, Map<String, String> variableMap, String username) throws ProcessException {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) applicationContext.getBean("workflowUserManager");
        FormService formService = (FormService) applicationContext.getBean("formService");
        AppDefinition appDefinition = getApplicationDefinition(assignment);
        AppService appService = (AppService) applicationContext.getBean("appService");

        workflowUserManager.setCurrentThreadUser(username);
        LogUtil.info(getClass().getName(), "Completing assignment [" + assignment.getActivityId() + "] user [" + username + "]");

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


            FormData validationFormData = formService.validateFormData(form, formData);
            String errorMessage = Optional.ofNullable(validationFormData)
                    .map(FormData::getFormErrors)
                    .map(Map::entrySet)
                    .map(Collection::stream)
                    .orElseGet(Stream::empty)
                    .map(e -> String.format("Assignment [" + assignment.getActivityId() + "] field [%s] in form [" + form.getPropertyString("id") + "] error [%s]", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(", "));


            if (!errorMessage.isEmpty()) {
                throw new ProcessException(errorMessage);
            }

            appService.completeAssignmentForm(form, assignment, formData, variableMap);
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
     * Get property "asUser"
     *
     * @param props
     * @return
     */
    @Nonnull
    private Set<String> getAsUser(Map props) throws ProcessException {
        return Arrays.stream(String.valueOf(props.get("asUser"))
                .split("[;,]"))
                .map(throwableFunction(this::getUser))
                .map(User::getUsername)
                .collect(Collectors.toSet());
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

    private JSONObject getBodyPayload(HttpServletRequest request) {
        try(BufferedReader bufferedReader = new BufferedReader(request.getReader())) {
            String lines = bufferedReader.lines().collect(Collectors.joining());
            return new JSONObject(lines.isEmpty() ? "{}" : lines);
        } catch (IOException | JSONException e) {
            LogUtil.error(getClass().getName(), e, e.getMessage());
            return new JSONObject();
        }
    }

    private Map<String, String> getWorkflowVariables(@Nonnull JSONObject jsonObject) {
        return jsonKeyStream(jsonObject)
                .collect(Collectors.toMap(String::valueOf, jsonObject::optString));
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

    @Nonnull
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
        LogUtil.info(getClass().getName(), "Executing plugin Rest API [" + request.getRequestURI() + "] in method [" + request.getMethod() + "] as [" + WorkflowUtil.getCurrentUsername() + "]");
        try {
            boolean isAdmin = WorkflowUtil.isCurrentUserInRole("ROLE_ADMIN");
            if (!isAdmin) {
                throw new RestApiException(HttpServletResponse.SC_UNAUTHORIZED, "User is not an admin");
            }

            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            String action = getRequiredParameter(request, "action");
            String appId = Optional.ofNullable(appDef)
                    .map(AppDefinition::getAppId)
                    .orElseThrow(() -> new RestApiException(HttpServletResponse.SC_NOT_FOUND, "Application not found"));
            ApplicationContext ac = AppUtil.getApplicationContext();
            AppService appService = (AppService) ac.getBean("appService");
            WorkflowManager workflowManager = (WorkflowManager) ac.getBean("workflowManager");

            if("completeAssignment".equals(action)) {
                if(!"POST".equalsIgnoreCase(request.getMethod())) {
                    throw new RestApiException(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Cannot accept method ["+request.getMethod()+"]");
                }

                String processId = getRequiredParameter(request, "processId");
                Collection<String> activityDefIds = getRequiredParameterValues(request, "activityDefId");
                String loginAs = getOptionalParameter(request, "loginAs", WorkflowUtil.getCurrentUsername());

                JSONArray jsonAssignments = new JSONArray();
                try {
                    JSONObject bodyPayload = getBodyPayload(request);
                    getAssignmentByProcess(processId, activityDefIds, loginAs)
                            .forEach(throwableConsumer(a -> {
                                assignmentComplete(a, getWorkflowVariables(bodyPayload), loginAs);
                                jsonAssignments.put(a.getActivityId());
                            }));
                } catch (ProcessException e) {
                    throw new RestApiException(HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), e);
                }

                try {
                    JSONObject jsonResponse = new JSONObject();
                    int completedAssignments = jsonAssignments.length();
                    if(completedAssignments > 0) {
                        jsonResponse.put("assignments", jsonAssignments);
                        jsonResponse.put("message", "Successfully completing [" + completedAssignments + "] assignments");
                    } else {
                        jsonResponse.put("message", "No assignment has been completed");
                    }

                    response.getWriter().write(jsonResponse.toString());
                } catch (JSONException e) {
                    LogUtil.error(getClass().getName(), e, e.getMessage());
                }
            }

            // get processes
            else if ("getProcesses".equals(action)) {
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
                    throw new RestApiException(HttpServletResponse.SC_FORBIDDEN, "Get Process options Error!", ex);
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
                    String processId = getRequiredParameter(request, "processId");
                    WorkflowProcess process = appService.getWorkflowProcessForApp(appDef.getId(), appDef.getVersion().toString(), processId);
                    String processDefId = process.getId();
                    Collection<WorkflowActivity> activityList = workflowManager.getProcessActivityDefinitionList(processDefId);
                    for (WorkflowActivity a : activityList) {
                        if (a.getType().equals("route") || a.getType().equals("tool")) continue;
                        HashMap<String, String> option = new HashMap<String, String>();
                        option.put("value", a.getActivityDefId());
                        option.put("label", a.getName() + " (" + a.getActivityDefId() + ")");
                        jsonArray.put(option);
                    }

                    jsonArray.write(response.getWriter());
                } catch (JSONException ex) {
                    throw new RestApiException(HttpServletResponse.SC_FORBIDDEN, "Get activity options Error!", ex);
                }
            }

            // get participants
            else if ("getParticipants".equalsIgnoreCase(action)) {
                try {
                    String processId = getRequiredParameter(request, "processId");
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
                } catch (JSONException ex) {
                    throw new RestApiException(HttpServletResponse.SC_FORBIDDEN, "Get participants options Error!", ex);
                }
            }

            // get variables
            else if ("getVariables".equalsIgnoreCase(action)) {
                try {
                    String processId = getRequiredParameter(request, "processId");
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
                } catch (JSONException ex) {
                    throw new RestApiException(HttpServletResponse.SC_FORBIDDEN, "Get variables options Error!", ex);
                }
            }

            // other actions
            else {
                throw new RestApiException(HttpServletResponse.SC_BAD_REQUEST, "Action ["+action+"] is not supported");
            }
        } catch (RestApiException e) {
            LogUtil.error(getClass().getName(), e, e.getMessage());
            response.sendError(e.getErrorCode(), e.getMessage());
        }
    }

    private Collection<String> getRequiredParameterValues(HttpServletRequest request, String parameterName) throws RestApiException {
        return Optional.of(parameterName)
                .map(request::getParameterValues)
                .map(Arrays::stream)
                .orElseThrow(() -> new RestApiException(HttpServletResponse.SC_BAD_REQUEST, "Required parameter ["+parameterName+"] is not provided"))
                .filter(not(String::isEmpty))
                .map(it -> it.split("[;,]"))
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());
    }

    private String getRequiredParameter(HttpServletRequest request, String parameterName) throws RestApiException {
        return Optional.of(parameterName)
                .map(request::getParameter)
                .filter(not(String::isEmpty))
                .orElseThrow(() -> new RestApiException(HttpServletResponse.SC_BAD_REQUEST, "Required parameter ["+parameterName+"] is not provided"));
    }

    private String getOptionalParameter(HttpServletRequest request, String parameterName, String defaultValue) {
        return Optional.of(parameterName)
                .map(request::getParameter)
                .filter(not(String::isEmpty))
                .orElse(defaultValue);
    }
}
