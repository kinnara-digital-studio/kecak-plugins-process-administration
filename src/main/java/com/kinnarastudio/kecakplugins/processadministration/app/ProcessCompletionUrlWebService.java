package com.kinnarastudio.kecakplugins.processadministration.app;

import io.jsonwebtoken.Claims;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.kecak.apps.app.service.AuthTokenService;
import org.kecak.apps.exception.ApiException;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class ProcessCompletionUrlWebService extends DefaultApplicationPlugin implements PluginWebSupport {
    public final static String LABEL = "Process Completion URL Web Service";

    @Override
    public String getName() {
        return LABEL;
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
    public Object execute(Map properties) {
        return null;
    }

    @Override
    public void webService(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws ServletException, IOException {
        final ApplicationContext applicationContext = AppUtil.getApplicationContext();
        final AuthTokenService authTokenService = (AuthTokenService) applicationContext.getBean("authTokenService");
        try {
            final String method = servletRequest.getMethod();

            if (!"GET".equalsIgnoreCase(method)) {
                throw new ApiException(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method [" + method + "] is not supported");
            }

            final String action = getParameter(servletRequest, "_action");
            if ("assignmentComplete".equals(action)) {
                final String token = getParameter(servletRequest, "_token");
                final String processId = String.valueOf(authTokenService.getClaimDataFromToken(token, "processId"));
                final String username = String.valueOf(authTokenService.getClaimDataFromToken(token, "username"));

                servletResponse.getWriter().write(new JSONObject() {{
                    try {
                        put("processId", processId);
                        put("username", username);
                    } catch (JSONException e) {
                        LogUtil.error(getClassName(), e, e.getMessage());
                    }
                }}.toString());
                return;
            } else if ("getToken".equals(action)) {
                final String username = WorkflowUtil.getCurrentUsername();
                final String token = authTokenService.generateToken(username, new HashMap<>() {{
                    put("processId", "123");
                    put("username", username);
                }}, 1);

                servletResponse.getWriter().write(token);
                return;
            }

            throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Action [" + action + "] is not supported");
        } catch (ApiException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            servletResponse.sendError(e.getErrorCode(), e.getMessage());
        }
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
        return "";
    }

    protected String getParameter(HttpServletRequest request, String parameterName) throws ApiException {
        return optParameter(request, parameterName).orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Parameter [" + parameterName + "] is not supplied"));
    }

    protected Optional<String> optParameter(HttpServletRequest request, String parameterName) {
        return Optional.of(parameterName)
                .map(request::getParameter);
    }
}
