/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.jdbc.store;

import static java.time.Duration.ofSeconds;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.tracing.TracingHelper;

import com.google.common.collect.ImmutableMap;

import io.enmasse.iot.jdbc.store.Statement.ExpandedStatement;
import io.enmasse.iot.utils.MoreFutures;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.UpdateResult;

public class AbstractStore implements HealthCheckProvider, AutoCloseable {

    private static final String DEFAULT_CHECK_SQL = "SELECT 1";

    private final SQLClient client;
    private final Tracer tracer;

    private final ExpandedStatement checkSql;

    public AbstractStore(final SQLClient client, final Tracer tracer, final Optional<Statement> checkSql) {
        this.client = client;
        this.tracer = tracer;
        this.checkSql = checkSql.orElseGet(() -> Statement.statement(DEFAULT_CHECK_SQL)).expand();
    }

    @Override
    public void close() throws Exception {
        this.client.close();
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {}

    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        readinessHandler.register("sql", ofSeconds(10).toMillis(), p -> {
            this.checkSql
                    .query(this.client)
                    .map(Status.OK())
                    .setHandler(p);
        });
    }

    /**
     * Check of an optimistic lock outcome.
     * <p>
     * This method will take the result of a simple update and, in case of a versioned update, try to
     * figure out of the optimistic lock held.
     * <p>
     * The implementation will do a simple, post update query to the entity, in case no rows got
     * updated, and then check for the existence of the entity. So there still is a race condition
     * possible, of an object not being updated because it doesn't exist, and a new, matching entity
     * being created at the same time. Or a failed update, and someone deleting the object at the same
     * time. The state in the database would always be consistent.
     * <p>
     * So it might be that the method reports a "nothing updated" condition, although when the update
     * statement was updated, it was a broken optimistic lock condition. However, the final result still
     * is that the update failed, and the object is now deleted.
     *
     * @param <K> The key type to use for looking up the entity.
     * @param result The original result, from updating the entity.
     * @param reader The reader to use for fetching the entity, by key only.
     * @param key The key of the entity.
     * @param resourceVersion The optional resource version.
     * @return If the resource version is not set, it will return the result unaltered. Otherwise it
     *         will read the entity by key only. If it finds the entity, then this is considered a
     *         broken optimistic lock, and a failed future will be returned. Otherwise it is considered
     *         an "object not found" condition.
     */
    protected Future<UpdateResult> checkOptimisticLock(final Future<UpdateResult> result, final Span span,
            final Optional<String> resourceVersion,
            final Function<Span, Future<ResultSet>> reader) {

        // if we don't have a resource version ...
        if (resourceVersion.isEmpty()) {
            /// ... then there is no need to check
            return result;
        }

        return result
                .<UpdateResult>flatMap(r -> {

                    span.log(ImmutableMap.<String, Object>builder()
                            .put("event", "check update result")
                            .put("update_count", r.getUpdated())
                            .build());

                    // if we updated something ...
                    if (r.getUpdated() != 0) {
                        // ... then we optimistic lock held
                        return Future.succeededFuture(r);
                    }

                    final Span readSpan = TracingHelper.buildChildSpan(this.tracer, span.context(), "check optimistic lock")
                            .withTag("resource_version", resourceVersion.get())
                            .start();

                    // we did not update anything, we need to check why ...
                    var f = reader.apply(readSpan)
                            .<UpdateResult>flatMap(readResult -> {

                                span.log(Map.of(
                                        "event", "check read result",
                                        "read_count", readResult.getNumRows()));

                                // ... having read the current state, without the version ...
                                if (readResult.getNumRows() <= 0) {
                                    // ... we know that the entry simply doesn't exist
                                    return Future.succeededFuture(r);
                                } else {
                                    // ... we know that the entry does exists, just had the wrong version, and the lock broke
                                    return Future.failedFuture(new OptimisticLockingException());
                                }
                            });

                    return MoreFutures
                            .whenComplete(f, readSpan::finish);
                });

    }


}
