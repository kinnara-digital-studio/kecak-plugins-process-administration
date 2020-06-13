package com.kinnara.kecakplugins.processadministration.lib;

import com.kinnara.kecakplugins.processadministration.exception.ProcessException;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.directory.dao.UserDao;
import org.joget.directory.model.User;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.dao.WorkflowProcessLinkDao;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.shark.model.dao.WorkflowAssignmentDao;
import org.springframework.context.ApplicationContext;
import sun.rmi.runtime.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface ProcessUtils {

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
        UserDao userDao = (UserDao) applicationContext.getBean("userDao");
        return Optional.ofNullable(username)
                .map(userDao::getUser)
                .orElseThrow(() -> new ProcessException("User [" + username + "] is not available"));
    }

    @Nonnull
    default Collection<WorkflowAssignment> getAssignmentByProcess(@Nonnull String processId, @Nonnull Set<String> activityDefIds, @Nullable String username) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowAssignmentDao workflowAssignmentDao = (WorkflowAssignmentDao) applicationContext.getBean("workflowAssignmentDao");

        return Optional.ofNullable(workflowAssignmentDao.getAssignmentsByProcessIds(Collections.singleton(processId), username, null, null, null, null, null))
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .filter(a -> activityDefIds.isEmpty() || activityDefIds.contains(a.getActivityDefId()))
                .collect(Collectors.toList());
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

    default <T, R, E extends Exception> Function<T, R> throwable(ThrowableFunction<T, R, E> throwableFunction, Function<E, R> failover) {
        return throwableFunction.onException(failover);
    }

    default <T, E extends Exception> ThrowableConsumer<T, E> throwable(ThrowableConsumer<T, E> throwableConsumer) {
        return throwableConsumer;
    }

    default <T, U, E extends Exception> BiConsumer<T, U> throwable(ThrowableBiConsumer<T, U, E> throwableBiConsumer) {
        return throwableBiConsumer;
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
                onException((E) e);
            }
        }

        default void onException(E e) {
            LogUtil.error(ThrowableFunction.class.getName(), e, e.getMessage());
        }

        void acceptThrowable(T t, U u) throws E;
    }

}
