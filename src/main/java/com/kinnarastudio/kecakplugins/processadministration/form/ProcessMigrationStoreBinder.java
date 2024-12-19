package com.kinnarastudio.kecakplugins.processadministration.form;

import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.service.WorkflowManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class ProcessMigrationStoreBinder extends WorkflowFormBinder {
    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        return "Process Admin - Process Migration Store Binder";
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
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        AppDefinitionDao appDefinitionDao = (AppDefinitionDao) AppUtil.getApplicationContext().getBean("appDefinitionDao");
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        if (rows != null && !rows.isEmpty()) {
            //Get the submitted data
            FormRow row = rows.get(0);

            String appId = row.getProperty("app_id");
            Long fromAppVersion = Long.valueOf(row.getProperty("app_version_from"));
            Long toAppVersion = Long.valueOf(row.getProperty("app_version_to"));

            LogUtil.info(getClassName(), "Updating running processes for app [" + appId + "] version [" + fromAppVersion + "] to [" + toAppVersion + "]");

            // get App Definition
            AppDefinition appDefinitionFrom = appDefinitionDao.loadVersion(appId, fromAppVersion);
            AppDefinition appDefinitionTo = appDefinitionDao.loadVersion(appId, toAppVersion);

            Collection<WorkflowProcess> runningProcessList = new ArrayList<>(workflowManager.getRunningProcessList(appId, null, null, appDefinitionFrom.getPackageDefinition().getVersion().toString(), null, null, null, null));

            // get all processses for current app
            Collection<WorkflowProcess> processes = workflowManager.getProcessList(appId, appDefinitionTo.getPackageDefinition().getVersion().toString());

            if (runningProcessList.isEmpty()) {
                LogUtil.warn(getClassName(), "No running processes to update for [" + appId + "] version [" + fromAppVersion + "]");
            } else if (processes == null || processes.isEmpty()) {
                LogUtil.warn(getClassName(), "Target app [" + appId + "] version [" + toAppVersion + "] not found");
            } else {
                Collection<String> newProcessDefIds = processes
                        .stream()
                        .map(WorkflowProcess::getId)
                        .collect(Collectors.toList());

                final Long fromProcessVersion = appDefinitionFrom.getPackageDefinition().getVersion();
                final Long toProcessVersion = appDefinitionTo.getPackageDefinition().getVersion();

                Long migrated = runningProcessList
                        .stream()
                        .filter(process -> {
                            String processInstanceId = process.getInstanceId();
                            try {
                                String processDefId = process.getId().replace("#" + fromProcessVersion + "#", "#" + toProcessVersion + "#");

                                if (newProcessDefIds.contains(processDefId)) {
                                    workflowManager.processCopyFromInstanceId(processInstanceId, processDefId, true);
                                    LogUtil.info(getClassName(), "Success migrating process [" + processInstanceId + "]");
                                } else {
                                    workflowManager.processAbort(processInstanceId);
                                    LogUtil.info(getClassName(), "Process Def ID [" + processDefId + "] does not exist");
                                }

                                return true;
                            } catch (Exception e) {
                                LogUtil.error(getClass().getName(), e, "Error updating process [" + processInstanceId + "]");
                                return false;
                            }
                        })
                        .count();

                row.setProperty("num_migrated", String.valueOf(migrated));
                LogUtil.info(getClassName(), "Completed updating ["+migrated+"] running processeses");
            }
        }

        return super.store(element, rows, formData);
    }
}
