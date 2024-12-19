/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kinnarastudio.kecakplugins.processadministration.userview;

import com.kinnarastudio.kecakplugins.processadministration.form.ProcessOptionsBinder;
import com.kinnarastudio.kecakplugins.processadministration.model.ProcessMonitor;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.directory.model.User;
import org.joget.directory.model.service.ExtDirectoryManager;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Yonathan Edited on 4 Aug 2016: Fix missing reference Field when goes
 * to sub flow
 */
public class ProcessReassignmentUserviewMenu extends UserviewMenu implements PluginWebSupport {

    @Override
    public String getCategory() {
        return AppPluginUtil.getMessage("processAdministration.processAdmin", getClassName(), "/messages/ProcessAdministration");
    }

    @Override
    public String getIcon() {
        return "/plugin/org.joget.apps.userview.lib.RunProcess/images/grid_icon.gif";
    }

    @Override
    public String getRenderPage() {
        final String tableName = getPropertyString("tableName");
        final String field = getPropertyString("fieldId");
        final String masterUsername = getPropertyString("masterUser");
        final String masterHash = getPropertyString("masterHash");
        final String histFormDefId = getPropertyString("histFormDefId");
        final String refFieldHist = getPropertyString("refField");

        final Map<String, Object> dataModel = new HashMap<>();
        final String templatePath = "ProcessReassignmentUserview.ftl";

        final ApplicationContext appContext = AppUtil.getApplicationContext();
        final AppService appService = (AppService) appContext.getBean("appService");
        final PluginManager pluginManager = (PluginManager) appContext.getBean("pluginManager");
        final ExtDirectoryManager directoryManager = (ExtDirectoryManager) appContext.getBean("directoryManager");
        final DataSource ds = (DataSource) appContext.getBean("setupDataSource");
        final AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        String historyTableName = null;
        if (!histFormDefId.equals("")) {
            // Store to history
            historyTableName = appService.getFormTableName(appDef, histFormDefId);
        }


        String select = "";
        String join = "";

        if (!tableName.equals("") && !field.equals("")) {
            select = ", c_" + field + " ";
            join = "LEFT JOIN app_fd_" + tableName + " t ON t.id = p.id ";
        }

        //Populate running process for chosen process
        String sql
                = "SELECT p.Id AS processId, p.ResourceRequesterId as requester, p.Name AS processName, "
                + " a.id AS activityId, a.Name AS activityName, at.ResourceId AS assignee "
                + select
                + "FROM SHKProcesses p "
                + "INNER JOIN SHKActivities a ON a.ProcessId = p.id "
                + "INNER JOIN SHKAssignmentsTable at ON at.ActivityId=a.Id "
                + join
                + "WHERE p.State = '1000000' and a.State='1000003' ";

        try(Connection con = ds.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
            List<ProcessMonitor> listPM = new ArrayList<>();
            while (rs.next()) {
                ProcessMonitor pm = new ProcessMonitor();
                pm.setProcessId(rs.getString("processId"));
                pm.setProcessName(rs.getString("processName"));
                User user = directoryManager.getUserByUsername(rs.getString("requester"));
                pm.setRequester(user.getFirstName() + " " + user.getLastName() + " (" + user.getUsername() + ")");
                pm.setActivityId(rs.getString("activityId"));
                pm.setActivityName(rs.getString("activityName"));
                user = directoryManager.getUserByUsername(rs.getString("assignee"));
                pm.setAssignee(user.getFirstName() + " " + user.getLastName() + " (" + user.getUsername() + ")");
                pm.setAssigneeUsername(user.getUsername());
                if (rs.getString("c_" + field) != null) {
                    pm.setReferenceField(rs.getString("c_" + field));
                } else {
                    // search from parent table
                    String parentProcessId = appService.getOriginProcessId(rs.getString("processId"));

                    String sqlParent
                            = "SELECT c_" + field + " "
                            + "FROM app_fd_" + tableName + " "
                            + "WHERE id = ? ";
                    try(PreparedStatement psRef = con.prepareStatement(sqlParent)) {

                        psRef.setString(1, parentProcessId);

                        try(ResultSet rsRef = psRef.executeQuery()) {
                            String refField = null;
                            while (rsRef.next()) {
                                refField = rsRef.getString(1);
                            }
                            if (refField != null) {
                                pm.setReferenceField(refField);
                            } else {
                                pm.setReferenceField("");
                            }
                        }
                    } catch (SQLException ignored) {}
                }
                listPM.add(pm);
            }
            // Data tables Process monitor
            final List<String> header = new ArrayList<>();
            header.add("Process ID");
            header.add("Process Name");
            header.add("Requester");
            header.add("Activity Name");
            header.add("Assignee");
            header.add("Reference Field");

            dataModel.put("headers", header);
            dataModel.put("datas", listPM);
            if (historyTableName != null) {
                dataModel.put("historyTableName", historyTableName);
                dataModel.put("refFieldHist", refFieldHist);
            } else {
                dataModel.put("historyTableName", "");
                dataModel.put("refFieldHist", "");
            }
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, ex.getMessage());
        }

        // Multi Select
        StringBuilder sbUser = new StringBuilder("<option value='");
        Collection<User> userList = directoryManager.getUsers(null, null, null, null, null, null, null, "firstName", false, null, null);
        for (User u : userList) {
            sbUser.append(u.getUsername());
            sbUser.append("'>");
            sbUser.append(u.getUsername()).append(' ');
            sbUser.append("</option><option value='");
        }
        String replaceUser = sbUser.toString().replaceAll("<option value='$", "");
        dataModel.put("replaceUser", replaceUser);
        dataModel.put("masterHash", masterHash);
        try {
            dataModel.put("masterUser", URLEncoder.encode(masterUsername, "UTF-8"));
            dataModel.put("loginAs", URLEncoder.encode(WorkflowUtil.getCurrentUsername(), "UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            LogUtil.error(ProcessReassignmentUserviewMenu.class.getName(), ex, ex.getMessage());
        }

        dataModel.put("className", getClassName());

        String htmlContent = pluginManager.getPluginFreeMarkerTemplate(dataModel, getClassName(), "/templates/" + templatePath, "/messages/ProcessAdministration");
        return htmlContent;
    }

    @Override
    public boolean isHomePageSupported() {
        return true;
    }

    @Override
    public String getDecoratedMenu() {
        return null;
    }

    public String getName() {
        return getLabel();
    }

    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        return resourceBundle.getString("buildNumber");
    }

    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    public String getLabel() {
        return "Process Reassingment";
    }

    public String getClassName() {
        return this.getClass().getName();
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/ProcessReassignmentUserview.json", new String[] {ProcessOptionsBinder.class.getName()}, true, "/messages/ProcessAdministration");
    }

    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String historyTableName = request.getParameter("historyTableName");
        String refFieldHist = request.getParameter("refFieldHist");

        if (request.getMethod().equals("POST")) {
            ApplicationContext ac = AppUtil.getApplicationContext();
            DataSource ds = (DataSource) ac.getBean("setupDataSource");
            WorkflowUserManager workflowUserManager = (WorkflowUserManager) ac.getBean("workflowUserManager");
            String username = workflowUserManager.getCurrentUsername();

            UuidGenerator uuid = UuidGenerator.getInstance();
            String id = uuid.getUuid();

            String sql
                    = "INSERT INTO app_fd_" + historyTableName + " "
                    + "(id, createdBy, dateCreated, dateModified, c_" + refFieldHist + ") "
                    + "VALUES "
                    + "(?,?,?,?)";

            try(Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setString(1, id);
                ps.setString(2, username);
                ps.setDate(3, (java.sql.Date) new Date());
                ps.setDate(4, (java.sql.Date) new Date());
                ps.setString(5, username);
                ps.executeUpdate();
                
            } catch (SQLException ex) {
                Logger.getLogger(ProcessReassignmentUserviewMenu.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            }
        }

    }

}
