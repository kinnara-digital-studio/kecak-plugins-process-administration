package com.kinnara.kecakplugins.processadministration;

import org.enhydra.shark.api.common.SharkConstants;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class ProcessAdministrationTool extends DefaultApplicationPlugin {
    @Override
    public String getName() {
        return "Process Admin - Process Administration Tool";
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
        WorkflowAssignment wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
        ApplicationContext appContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        AppDefinitionDao appDefinitionDao = (AppDefinitionDao) appContext.getBean("appDefinitionDao");
        AppDefinition currentAppDefinition = AppUtil.getCurrentAppDefinition();


        Stream<String> processStream = props.get("processId") == null || props.get("processId").toString().isEmpty() ? Stream.of(wfAssignment.getProcessId()) : Arrays.stream(props.get("processId").toString().split(";"));

        if("reevaluate".equalsIgnoreCase(getPropertyString("action"))) {
            processStream
                    .map(workflowManager::getRunningProcessById)

                    // filter by running process
                    .filter(process -> process.getState().startsWith(SharkConstants.STATEPREFIX_OPEN)/*!SharkConstants.STATE_CLOSED_ABORTED.equals(wfProcess.getState()) && !SharkConstants.STATE_CLOSED_TERMINATED.equals(wfProcess.getState())*/)
                    .map(WorkflowProcess::getInstanceId)

                    // get latest activity, assume only handle the latest one
                    .map(pid -> workflowManager.getActivityList(pid, 0, 1, "dateCreated", true))
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)

                    // check status = open
                    .filter(activity -> activity.getState().startsWith(SharkConstants.STATEPREFIX_OPEN))

                    // reevaluate process
                    .peek(a -> LogUtil.info(getClassName(), "["+getPropertyString("action").toUpperCase()+"] assignment [" + a.getId() + "]"))
                    .forEach(a -> workflowManager.reevaluateAssignmentsForActivity(a.getId()));
        } else if("abort".equalsIgnoreCase(getPropertyString("action"))) {
            processStream
                    .map(workflowManager::getRunningProcessById)
                    .filter(Objects::nonNull)
                    .filter(p -> p.getState() != null && p.getState().startsWith("open"))

                    .map(WorkflowProcess::getInstanceId)
                    .peek(pid -> LogUtil.info(getClassName(), "[" + getPropertyString("action").toUpperCase() + "] process [" + pid + "]"))
                    .forEach(workflowManager::processAbort);
        } else if("migrate".equalsIgnoreCase(getPropertyString("action"))) {
            AppDefinition publishedAppDefinition = appDefinitionDao.loadVersion(currentAppDefinition.getAppId(), appDefinitionDao.getPublishedVersion(currentAppDefinition.getAppId()));

            processStream
                    .map(workflowManager::getRunningProcessById)
                    .filter(p -> (p != null && p.getState() != null && p.getState().startsWith("open")))
                    .map(p -> {
                        String currentProcessDefId = p.getId();
                        String publishedProcessDefId = currentProcessDefId.replaceAll("#[0-9]+#", "#" + publishedAppDefinition.getPackageDefinition().getVersion() + "#");

                        // check if target process is the same as current process
                        if (currentProcessDefId.equals(publishedProcessDefId)) {
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

                    // get latest activity, assume only handle the latest one
                    .map(pid -> workflowManager.getActivityList(pid, 0, 1, "dateCreated", true))
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)

                    // check status = open
                    .filter(activity -> activity.getState().startsWith(SharkConstants.STATEPREFIX_OPEN))

                    // reevaluate process
                    .forEach(a -> workflowManager.reevaluateAssignmentsForActivity(a.getId()));

        }

        return null;
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
        return AppUtil.readPluginResource(getClassName(), "/properties/ProcessAdministrationTool.json",null, false, "/messages/ProcessAdministration");
    }
}
