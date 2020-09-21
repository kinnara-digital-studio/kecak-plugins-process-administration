package com.kinnara.kecakplugins.processadministration;

import com.kinnara.kecakplugins.processadministration.exception.RestApiException;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewPermission;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryManager;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
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
//public class ProcessAdministrationApi extends ExtDefaultPlugin implements PluginWebSupport, PropertyEditable {
public class ProcessAdministrationApi extends DefaultApplicationPlugin implements PluginWebSupport {

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

            UserviewPermission permission = generatePermission();
            boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN);
            if((permission == null && !isAdmin) || (permission != null &&permission.isAuthorize())) {
                throw new RestApiException(HttpServletResponse.SC_UNAUTHORIZED, "Current user ["+ WorkflowUtil.getCurrentUsername() +"] is not authorized");
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
                    final BufferedReader br = request.getReader();
                    final JSONObject jsonBody = new JSONObject(br.lines().collect(Collectors.joining()));
                    jsonBody.keys().forEachRemaining(key -> variableStreamBuilder.add(key.toString()));

//                    final JSONArray responseBody = new JSONArray();
                    final JSONArray responseBody = Stream.of(processIds)
                            .flatMap(pid -> processLinkDao.getLinks(pid).stream())
                            .map(WorkflowProcessLink::getProcessId)
                            .map(workflowManager::getAssignmentByProcess)
                            .filter(Objects::nonNull)
                            .map(a -> {
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

//                                    responseBody.put(jsonObject);
                                    return jsonObject;
                                } catch (JSONException e) {
                                    LogUtil.error(getClassName(), e, e.getMessage());
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(JSONArray::new, JSONArray::put, (receiver, supplier) -> {
                                for(int i = 0, size = supplier.length(); i < size; i++) {
                                    try { receiver.put(supplier.get(i)); } catch (JSONException ignored) { }
                                }
                            });

                    response.setContentType("application/json");
                    if(responseBody.length() > 0) {
                        response.setStatus(HttpServletResponse.SC_OK);
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
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
    public String getLabel() {
        return "Process Admin - JSON API";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/ProcessAdministrationApi.json", null, false, "/messages/ProcessAdministration");
    }

    @Override
    public Object execute(Map props) {
        return null;
    }

    private UserviewPermission generatePermission() {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        DirectoryManager directoryManager = (DirectoryManager) applicationContext.getBean("directoryManager");

        Map<String, Object> permission = (Map<String, Object>)getProperty("permission");
        if(permission == null)
            return null;

        String className = (String) permission.get("className");
        Map<String, Object> properties = (Map<String, Object>)permission.get("properties");

        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        Plugin plugin = pluginManager.getPlugin(className);
        if(properties != null)
            ((PropertyEditable)plugin).setProperties(properties);

        User user = directoryManager.getUserByUsername(WorkflowUtil.getCurrentUsername());
        if(user != null)
            ((UserviewPermission)plugin).setCurrentUser(user);

        return (UserviewPermission) plugin;
    }
}