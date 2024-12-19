package com.kinnarastudio.kecakplugins.processadministration.datalist;

import com.kinnarastudio.kecakplugins.processadministration.exception.ProcessException;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ResourceBundle;

/**
 * Aegis of Immortal Process
 */
public class ProcessReviveDataListAction extends DataListActionDefault {
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
        final ApplicationContext applicationContext = AppUtil.getApplicationContext();
        final DataSource ds = (DataSource) applicationContext.getBean("setupDataSource");

        for (String primaryKey : rowKeys) {
            try {
                final String processId = getProcessId(primaryKey);

                final String activityId = getActivityId(processId);

                reviveActivity(activityId);

                reviveProcess(processId);
            } catch (ProcessException e) {
                LogUtil.error(getClassName(), e, e.getMessage());
            }
        }

        return null;
    }

    @Override
    public String getName() {
        return "Process Revive";
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        return resourceBundle.getString("buildNumber");
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle() + ", Aegis of Immortal Process";
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
        return AppUtil.readPluginResource(getClass().getName(), "/properties/ProcessReviveDataListAction.json");
    }

    protected String getProcessId(String primaryKey) {
        // TODO
        return "";
    }

    protected String getActivityId(String processId) {
        // TODO
        return "";
    }

    /**
     * UPDATE SHKActivities  SET state='1000003' WHERE Id=[activityId]
     *
     * @param activityId
     */
    protected void reviveActivity(String activityId) throws ProcessException {
        final ApplicationContext applicationContext = AppUtil.getApplicationContext();
        final DataSource ds = (DataSource) applicationContext.getBean("setupDataSource");

        final String sql = "UPDATE SHKActivities  SET state='1000003' WHERE Id= ?";

        try(final Connection con = ds.getConnection();
            final PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, activityId);
            if(ps.execute()) {
                LogUtil.info(getClassName(), "Activity [" + activityId + "] revived");
            } else throw new ProcessException("Error executing query [" + sql + "]");
        } catch (SQLException e) {
            throw new ProcessException("Error reviving activity [" + activityId + "]", e);
        }
    }

    /**
     * UPDATE SHKProcesses SET state='1000000' WHERE Id = [processId]
     *
     * @param processId
     */
    protected void reviveProcess(String processId) throws ProcessException {
        final ApplicationContext applicationContext = AppUtil.getApplicationContext();
        final DataSource ds = (DataSource) applicationContext.getBean("setupDataSource");

        final String sql = "UPDATE SHKProcesses SET state='1000000' WHERE Id = ?";

        try(final Connection con = ds.getConnection();
            final PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, processId);
            if(ps.execute()) {
                LogUtil.info(getClassName(), "Process [" + processId + "] revived");
            } else throw new ProcessException("Error executing query [" + sql + "]");
        } catch (SQLException e) {
            throw new ProcessException("Error reviving process [" + processId + "]", e);
        }
    }
}
