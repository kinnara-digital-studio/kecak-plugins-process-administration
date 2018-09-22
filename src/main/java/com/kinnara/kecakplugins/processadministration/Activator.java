package com.kinnara.kecakplugins.processadministration;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(ProcessMigrationStoreBinder.class.getName(), new ProcessMigrationStoreBinder(), null));
        registrationList.add(context.registerService(AppOptionsBinder.class.getName(), new AppOptionsBinder(), null));
        registrationList.add(context.registerService(AppVersionOptionsBinder.class.getName(), new AppVersionOptionsBinder(), null));
        registrationList.add(context.registerService(ProcessMigrationVersionValidator.class.getName(), new ProcessMigrationVersionValidator(), null));
        registrationList.add(context.registerService(ProcessReassignmentUserviewMenu.class.getName(), new ProcessReassignmentUserviewMenu(), null));
        registrationList.add(context.registerService(ProcessOptionsBinder.class.getName(), new ProcessOptionsBinder(), null));
        registrationList.add(context.registerService(ProcessAdministrationDataListAction.class.getName(), new ProcessAdministrationDataListAction(), null));

    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}