package com.kinnara.kecakplugins.processadministration;

import com.kinnara.kecakplugins.processadministration.exception.RestApiException;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.dao.WorkflowProcessLinkDao;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 *
 */
public class ProcessAdministrationApi extends ExtDefaultPlugin implements PluginWebSupport, PropertyEditable {

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        WorkflowProcessLinkDao processLinkDao = (WorkflowProcessLinkDao) appContext.getBean("workflowProcessLinkDao");
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) appContext.getBean("workflowUserManager");

        String method = request.getMethod();
        String action = request.getParameter("action");

        try {
            if(!method.equalsIgnoreCase("POST")) {
                throw new RestApiException(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Only accept POST method");
            }

            boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN);
            if (!isAdmin) {
                throw new RestApiException(HttpServletResponse.SC_UNAUTHORIZED, "Current user ["+ WorkflowUtil.getCurrentUsername() +"] is not admin");
            }

            String loginAs = request.getParameter("loginAs");
            if(loginAs != null && !loginAs.isEmpty()) {
                LogUtil.info(getClassName(), "Login As [" + loginAs + "]");
                workflowUserManager.setCurrentThreadUser(loginAs);
            }

            if("complete".equalsIgnoreCase(action)) {
                String[] processIds = request.getParameterValues("processId");
                if(processIds == null || processIds.length == 0) {
                    throw new RestApiException(HttpServletResponse.SC_BAD_REQUEST, "Requires parameter [processId]");
                }

                final Stream.Builder<String> variableStreamBuilder = Stream.builder();
                try {
                    BufferedReader br = request.getReader();
                    JSONObject jsonBody = new JSONObject(br.lines().collect(Collectors.joining()));
                    jsonBody.keys().forEachRemaining(key -> variableStreamBuilder.add(key.toString()));

                    final JSONArray responseBody = new JSONArray();
                    Stream.of(processIds)
                            .flatMap(pid -> processLinkDao.getLinks(pid).stream())
                            .map(WorkflowProcessLink::getProcessId)
                            .map(workflowManager::getAssignmentByProcess)
                            .filter(Objects::nonNull)
                            .forEach(a -> {
                                LogUtil.info(getClassName(), "Process ID ["+a.getProcessId()+"] assignment ID ["+a.getActivityId()+"]");
                                if (!a.isAccepted()) {
                                    workflowManager.assignmentAccept(a.getActivityId());
                                }

                                Map<String, String> worklfowVariables = variableStreamBuilder.build()
                                        .map(var -> {
                                            Map<String, String> variable = new HashMap<>();
                                            try {
                                                variable.put(var, jsonBody.getString(var));
                                            } catch (JSONException ignored) { }
                                            return variable;
                                        })
                                        .collect(HashMap::new, Map::putAll, Map::putAll);

                                workflowManager.assignmentComplete(a.getActivityId(), worklfowVariables);
                                LogUtil.info(getClass().getName(), "Assignment [" + a.getActivityId() + "] completed");

                                try {
                                    JSONObject jsonObject = new JSONObject();
                                    jsonObject.accumulate("status", "completed");
                                    jsonObject.accumulate("processId", a.getProcessId());
                                    jsonObject.accumulate("activityId", a.getActivityId());

                                    responseBody.put(jsonObject);
                                } catch (JSONException ignored) { }
                            });

                    response.setContentType("application/json");
                    if(responseBody.length() > 0) {
                        response.setStatus(HttpServletResponse.SC_OK);
                    } else {
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                    response.getWriter().write(responseBody.toString());

                } catch (JSONException e) {
                    throw new RestApiException(HttpServletResponse.SC_NOT_ACCEPTABLE, e.getMessage());
                }
            } else {
                throw new RestApiException(HttpServletResponse.SC_FORBIDDEN, "Action [" + action + "] not available");
            }
        } catch (RestApiException e) {
            response.sendError(e.getErrorCode(), e.getMessage());
            LogUtil.error(getClassName(), e, e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "Process Administration API";
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
        return null;
    }
}