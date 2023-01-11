package com.kinnara.kecakplugins.processadministration;

import org.joget.apps.app.service.AppUtil;
import org.joget.plugin.base.PluginManager;
import org.kecak.apps.app.model.DefaultSchedulerPlugin;
import org.quartz.JobExecutionContext;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.ResourceBundle;

public class ProcessCompletionScheduler extends DefaultSchedulerPlugin {
    @Override
    public String getName() {
        return "Process Completion Scheduler";
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
    public void jobRun(@Nonnull JobExecutionContext jobExecutionContext, @Nonnull Map<String, Object> map) {

    }

    @Override
    public String getLabel() {
        return "Process Completion Scheduler";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/ProcessCompletionScheduler.json", new Object[]{ getClassName(), getClassName(), getClassName() }, false, "/messages/ProcessAdministration");

    }
}
