/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kinnarastudio.kecakplugins.processadministration.model;

/**
 *
 * @author Yonathan
 */
public class ProcessMonitor {
    private String processId;
    private String requester;
    private String processName;
    private String activityId;
    private String activityName;
    private String assignee;
    private String assigneeUsername;
    private String referenceField;

    /**
     * @return the processId
     */
    public String getProcessId() {
        return processId;
    }

    /**
     * @param processId the processId to set
     */
    public void setProcessId(String processId) {
        this.processId = processId;
    }

    /**
     * @return the requester
     */
    public String getRequester() {
        return requester;
    }

    /**
     * @param requester the requester to set
     */
    public void setRequester(String requester) {
        this.requester = requester;
    }

    /**
     * @return the processName
     */
    public String getProcessName() {
        return processName;
    }

    /**
     * @param processName the processName to set
     */
    public void setProcessName(String processName) {
        this.processName = processName;
    }

    /**
     * @return the activityName
     */
    public String getActivityName() {
        return activityName;
    }

    /**
     * @param activityName the activityName to set
     */
    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    /**
     * @return the assignee
     */
    public String getAssignee() {
        return assignee;
    }

    /**
     * @param assignee the assignee to set
     */
    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    /**
     * @return the referenceField
     */
    public String getReferenceField() {
        return referenceField;
    }

    /**
     * @param referenceField the referenceField to set
     */
    public void setReferenceField(String referenceField) {
        this.referenceField = referenceField;
    }

    /**
     * @return the assigneeUsername
     */
    public String getAssigneeUsername() {
        return assigneeUsername;
    }

    /**
     * @param assigneeUsername the assigneeUsername to set
     */
    public void setAssigneeUsername(String assigneeUsername) {
        this.assigneeUsername = assigneeUsername;
    }

    /**
     * @return the activityId
     */
    public String getActivityId() {
        return activityId;
    }

    /**
     * @param activityId the activityId to set
     */
    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }
}
