package com.kinnara.kecakplugins.processadministration.lib;

import com.kinnara.kecakplugins.processadministration.exception.ProcessException;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.commons.util.LogUtil;
import org.joget.directory.dao.UserDao;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryManager;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.dao.WorkflowProcessLinkDao;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import sun.rmi.runtime.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface ProcessUtils {

    /**
     * Attempt to get app definition using activity ID or process ID
     *
     * @param assignment
     * @return
     */
    @Nonnull
    default AppDefinition getApplicationDefinition(@Nonnull WorkflowAssignment assignment) throws ProcessException {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        AppService appService = (AppService) applicationContext.getBean("appService");

        final String activityId = assignment.getActivityId();
        final String processId = assignment.getProcessId();

        AppDefinition appDefinition =  Optional.of(activityId)
                .map(appService::getAppDefinitionForWorkflowActivity)
                .orElseGet(() -> Optional.of(processId)
                        .map(appService::getAppDefinitionForWorkflowProcess)
                        .orElse(null));

        return Optional.ofNullable(appDefinition)
                .orElseThrow(() -> new ProcessException("Application definition for assignment [" + activityId + "] process [" + processId + "] not found"));
    }

    /**
     * Stream element children
     *
     * @param element
     * @return
     */
    @Nonnull
    default Stream<Element> elementStream(@Nonnull Element element, FormData formData) {
        if(!element.isAuthorize(formData)) {
            return Stream.empty();
        }

        Stream<Element> stream = Stream.of(element);
        for (Element child : element.getChildren()) {
            stream = Stream.concat(stream, elementStream(child, formData));
        }
        return stream;
    }

    default Stream<String> jsonKeyStream(@Nonnull JSONObject jsonObject) {
        Objects.requireNonNull(jsonObject);
        Iterator<String> iterator = jsonObject.keys();
        Spliterator<String> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED);
        return StreamSupport.stream(spliterator, true);
    }

    /**
     *
     *
     * @param processId
     * @param activityDefIds
     * @return
     */
    @Nonnull
    default Collection<WorkflowActivity> getCompletedWorkflowActivities(String processId, @Nonnull Set<String> activityDefIds) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();

        WorkflowManager wfManager = (WorkflowManager)applicationContext.getBean("workflowManager");
        WorkflowProcessLinkDao workflowProcessLinkDao = (WorkflowProcessLinkDao) applicationContext.getBean("workflowProcessLinkDao");

        return Optional.of(processId)
                .map(workflowProcessLinkDao::getLinks)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .map(WorkflowProcessLink::getProcessId)
                .map(s -> wfManager.getActivityList(s, null, Integer.MAX_VALUE, "id", null))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(a -> activityDefIds.isEmpty() || activityDefIds.contains(a.getActivityDefId()))
                .filter(a -> a.getState().contains("completed"))
                .collect(Collectors.toList());
    }

    /**
     * Get latest process ID
     *
     * @param processId
     * @return
     */
    @Nonnull
    default String getLatestProcessId(@Nonnull String processId) {
        WorkflowProcessLinkDao workflowProcessLinkDao = (WorkflowProcessLinkDao) AppUtil.getApplicationContext().getBean("workflowProcessLinkDao");
        return Optional.ofNullable(workflowProcessLinkDao.getLinks(processId))
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .reduce((l1, l2) -> l2)
                .map(WorkflowProcessLink::getProcessId)
                .orElse(processId);
    }

    /**
     * Get assignment object by process ID
     *
     * @param processId
     * @return
     * @throws ProcessException
     */
    @Nonnull
    default WorkflowAssignment getAssignmentByProcess(@Nonnull String processId) throws ProcessException {
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        return Optional.of(processId)
                .map(workflowManager::getAssignmentByProcess)
                .orElseThrow(() -> new ProcessException("Assignment for process [" + processId + "] not available"));
    }

    /**
     * Current package ID
     * @return
     */
    @Nullable
    default String getPackageId() {
        return Optional.ofNullable(AppUtil.getCurrentAppDefinition())
                .map(AppDefinition::getPackageDefinition)
                .map(PackageDefinition::getId)
                .orElse(null);
    }

    /**
     * Get user
     *
     * @param username
     * @return
     */
    default User getUser(String username) throws ProcessException {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        DirectoryManager directoryManager = (DirectoryManager) applicationContext.getBean("directoryManager");
        return Optional.ofNullable(username)
                .map(directoryManager::getUserByUsername)
                .orElseThrow(() -> new ProcessException("User [" + username + "] is not available"));
    }

    @Nonnull
    default Collection<WorkflowAssignment> getAssignmentByProcess(@Nonnull String processId, @Nonnull Collection<String> activityDefIds, @Nonnull String username) throws ProcessException {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) applicationContext.getBean("workflowUserManager");
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");

        workflowUserManager.setCurrentThreadUser(username);

        assert workflowManager != null;
        assert appDefinition != null;

        return Optional.of(processId)
                .map(it -> workflowManager.getAssignmentPendingAndAcceptedList(appDefinition.getAppId(), null, it, null, null, null, null))
                .filter(Objects::nonNull)
                .map(Collection::stream)
                .filter(Objects::nonNull)
                .orElseThrow(() -> new ProcessException("No assignment found in process ["+processId+"] by ["+username+"]"))
                .filter(a -> activityDefIds.isEmpty() || Optional.of(a)
                        .map(WorkflowAssignment::getActivityId)
                        .map(workflowManager::getActivityById)
                        .map(WorkflowActivity::getActivityDefId)
                        .map(activityDefIds::contains)
                        .orElse(false))
                .collect(Collectors.toSet());

    }


    default  <T> Predicate<T> not(Predicate<T> p) {
        return t -> !p.test(t);
    }

    /*
     *
     *   DARN, THIS IS NEAT !!!!!!!
     *
     *
     *       * * *   * * *
     *     *       *       *
     *      *             *
     *        *         *
     *          *     *
     *            * *
     *             *
     */

    default <T> UnaryOperator<T> peek(Consumer<T> consumer) {
        Objects.requireNonNull(consumer);
        return (T t) -> {
            consumer.accept(t);
            return t;
        };
    }

    default <T, R, E extends Exception> Function<T, R> throwableFunction(ThrowableFunction<T, R, E> throwableFunction) {
        return throwableFunction;
    }

    default <T, R, E extends Exception> Function<T, R> throwableFunction(ThrowableFunction<T, R, E> throwableFunction, Function<E, R> failover) {
        return throwableFunction.onException(failover);
    }

    default <T, E extends Exception> ThrowableConsumer<T, E> throwableConsumer(ThrowableConsumer<T, E> throwableConsumer) {
        return throwableConsumer;
    }

    default <T, E extends Exception> ThrowableConsumer<T, E> throwableConsumer(ThrowableConsumer<T, E> throwableConsumer, Consumer<E> failover) {
        return throwableConsumer.onException(failover);
    }

    default <T, U, E extends Exception> BiConsumer<T, U> throwableConsumer(ThrowableBiConsumer<T, U, E> throwableBiConsumer) {
        return throwableBiConsumer;
    }

    default <T, U, E extends Exception> BiConsumer<T, U> throwableConsumer(ThrowableBiConsumer<T, U, E> throwableBiConsumer, Consumer<E> failover) {
        return throwableBiConsumer.onException(failover);
    }

    default <T, E extends Exception> Predicate<T> throwableTest(ThrowablePredicate<T, E> throwablePredicate){
        return throwablePredicate;
    }

    /**
     * Throwable version of {@link Function}.
     * Returns null then exception is raised
     *
     * @param <T>
     * @param <R>
     * @param <E>
     */
    @FunctionalInterface
    interface ThrowableFunction<T, R, E extends Exception> extends Function<T, R> {

        @Override
        default R apply(T t) {
            try {
                return applyThrowable(t);
            } catch (Exception e) {
                LogUtil.error(ThrowableFunction.class.getName(), e, e.getMessage());
                return null;
            }
        }

        R applyThrowable(T t) throws E;


        /**
         *
         * @param f
         * @return
         */
        default Function<T, R> onException(Function<? super E, ? extends R> f) {
            return (T t) -> {
                try {
                    return (R) applyThrowable(t);
                } catch (Exception e) {
                    return f.apply((E) e);
                }
            };
        }
    }

    /**
     * Throwable version of {@link Consumer}
     *
     * @param <T>
     * @param <E>
     */
    @FunctionalInterface
    interface ThrowableConsumer<T, E extends Exception> extends Consumer<T> {
        @Override
        default void accept(T t) {
            try {
                acceptThrowable(t);
            } catch (Exception e) {
                LogUtil.error(ThrowableFunction.class.getName(), e, e.getMessage());
            }
        }

        default ThrowableConsumer<T, E> onException(final Consumer<E> c) {
            Objects.requireNonNull(c);
            return (T t) -> {
                try {
                    acceptThrowable(t);
                } catch (Exception e) {
                    c.accept((E) e);
                }
            };
        }

        void acceptThrowable(T t) throws E;
    }

    /**
     * Throwable version of {@link Predicate}
     *
     * @param <T>
     * @param <E>
     */
    @FunctionalInterface
    interface ThrowablePredicate<T, E extends Exception> extends Predicate<T> {
        @Override
        default boolean test(T t) {
            try {
                return testThrowable(t);
            } catch (Exception e) {
                return onException((E) e);
            }
        }

        default boolean onException(E e) {
            LogUtil.error(ThrowableFunction.class.getName(), e, e.getMessage());
            return false;
        }

        boolean testThrowable(T t) throws E;
    }

    /**
     * Throwable version of {@link BiConsumer}
     *
     * @param <T>
     * @param <U>
     * @param <E>
     */
    @FunctionalInterface
    interface ThrowableBiConsumer<T, U, E extends Exception> extends BiConsumer<T, U> {
        default void accept(T t, U u) {
            try {
                acceptThrowable(t, u);
            } catch (Exception e) {
                LogUtil.error(ThrowableFunction.class.getName(), e, e.getMessage());
            }
        }

        default ThrowableBiConsumer<T, U, E> onException(Consumer<E> onException) {
            return (T t, U u) -> {
                try {
                    acceptThrowable(t, u);
                } catch (Exception e) {
                    onException.accept((E) e);
                }
            };
        }

        void acceptThrowable(T t, U u) throws E;
    }

}
