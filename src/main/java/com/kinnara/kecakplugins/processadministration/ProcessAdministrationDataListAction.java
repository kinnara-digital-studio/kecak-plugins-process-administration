package com.kinnara.kecakplugins.processadministration;

import org.enhydra.shark.api.common.SharkConstants;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.dao.WorkflowProcessLinkDao;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Stream;

public class ProcessAdministrationDataListAction extends DataListActionDefault {
    @Override
    public String getLinkLabel() {
        String label = getPropertyString("label");
        if (label == null || label.isEmpty()) {
            label = getName();
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
        DataListActionResult result = new DataListActionResult();
        result.setType(DataListActionResult.TYPE_REDIRECT);
        result.setUrl("REFERER");

        ApplicationContext appContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        WorkflowProcessLinkDao processLinkDao = (WorkflowProcessLinkDao) appContext.getBean("workflowProcessLinkDao");
        AppDefinitionDao appDefinitionDao = (AppDefinitionDao) appContext.getBean("appDefinitionDao");
        AppDefinition currentAppDefinition = AppUtil.getCurrentAppDefinition();

        if("reevaluate".equalsIgnoreCase(getPropertyString("action"))) {
            Arrays.stream(rowKeys)
                    .flatMap(id -> processLinkDao.getLinks(id).stream())
                    .filter(Objects::nonNull)
                    .filter(link -> {
                        WorkflowProcess wfProcess = workflowManager.getRunningProcessById(link.getProcessId());
                        return !SharkConstants.STATE_CLOSED_ABORTED.equals(wfProcess.getState()) && !SharkConstants.STATE_CLOSED_TERMINATED.equals(wfProcess.getState());
                    })
                    .map(l -> workflowManager.getAssignmentByProcess(l.getProcessId()))
                    .filter(Objects::nonNull)
                    .peek(a -> LogUtil.info(getClassName(), "Re-evaluating process [" + a.getActivityId() + "]"))
                    .forEach(a -> workflowManager.reevaluateAssignmentsForActivity(a.getActivityId()));
        } else if("abort".equalsIgnoreCase(getPropertyString("action"))) {
            Arrays.stream(rowKeys)
                    .flatMap(id -> processLinkDao.getLinks(id).stream())
                    .filter(Objects::nonNull)
                    .map(WorkflowProcessLink::getProcessId)
                    .map(workflowManager::getRunningProcessById)
                    .filter(p -> (p != null && p.getState().startsWith("open")))
                    .map(WorkflowProcess::getInstanceId)
                    .peek(pid -> LogUtil.info(getClassName(), "Aborting process [" + pid + "]"))
                    .forEach(workflowManager::processAbort);
        } else if("migrate".equalsIgnoreCase(getPropertyString("action"))) {
            AppDefinition publishedAppDefinition = appDefinitionDao.loadVersion(currentAppDefinition.getAppId(), appDefinitionDao.getPublishedVersion(currentAppDefinition.getAppId()));

            Arrays.stream(rowKeys)
                    .flatMap(id -> processLinkDao.getLinks(id).stream())
                    .filter(Objects::nonNull)
                    .map(WorkflowProcessLink::getProcessId)
                    .map(workflowManager::getRunningProcessById)
                    .filter(p -> (p != null && p.getState().startsWith("open")))
                    .forEach(p -> {
                        String publishedProcessDefId = p.getId().replaceAll("#[0-9]+#", "#" + publishedAppDefinition.getPackageDefinition().getVersion() + "#");
                        LogUtil.info(getClassName(), "Migrating process instance ["+p.getInstanceId()+"] to process def [" + publishedProcessDefId + "]");
                        WorkflowProcessResult workflowProcessResult = workflowManager.processCopyFromInstanceId(p.getInstanceId(), publishedProcessDefId, true);

                        // reevaluate after migration
                        Stream.of(workflowManager.getAssignmentByProcess(workflowProcessResult.getProcess().getInstanceId()))
                                .filter(Objects::nonNull)
                                .peek(a -> LogUtil.info(getClassName(), "Re-evaluating process [" + a.getActivityId() + "]"))
                                .forEach(a -> workflowManager.reevaluateAssignmentsForActivity(a.getActivityId()));
                    });

        } else if("prev".equalsIgnoreCase(getPropertyString("action"))) {

        }
        return result;
    }

    @Override
    public String getName() {
        return AppPluginUtil.getMessage("processAdministration.processAdministrationDataListAction", getClassName(), "/messages/ProcessAdministration");
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
        return AppUtil.readPluginResource(getClassName(), "/properties/ProcessAdministraionDataListAction.json",null, false, "/messages/ProcessAdministration");
    }
}
