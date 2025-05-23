/*-
 * Copyright (c) 2011, 2025 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 *  https://oss.oracle.com/licenses/upl/
 */

package oracle.nosql.driver;

import oracle.nosql.driver.ops.AddReplicaRequest;
import oracle.nosql.driver.ops.DeleteRequest;
import oracle.nosql.driver.ops.DeleteResult;
import oracle.nosql.driver.ops.DropReplicaRequest;
import oracle.nosql.driver.ops.GetIndexesRequest;
import oracle.nosql.driver.ops.GetIndexesResult;
import oracle.nosql.driver.ops.GetRequest;
import oracle.nosql.driver.ops.GetResult;
import oracle.nosql.driver.ops.GetTableRequest;
import oracle.nosql.driver.ops.ListTablesRequest;
import oracle.nosql.driver.ops.ListTablesResult;
import oracle.nosql.driver.ops.MultiDeleteRequest;
import oracle.nosql.driver.ops.MultiDeleteResult;
import oracle.nosql.driver.ops.PrepareRequest;
import oracle.nosql.driver.ops.PrepareResult;
import oracle.nosql.driver.ops.PutRequest;
import oracle.nosql.driver.ops.PutRequest.Option;
import oracle.nosql.driver.ops.PutResult;
import oracle.nosql.driver.ops.QueryIterableResult;
import oracle.nosql.driver.ops.QueryRequest;
import oracle.nosql.driver.ops.QueryResult;
import oracle.nosql.driver.ops.ReplicaStatsRequest;
import oracle.nosql.driver.ops.ReplicaStatsResult;
import oracle.nosql.driver.ops.Request;
import oracle.nosql.driver.ops.Result;
import oracle.nosql.driver.ops.SystemRequest;
import oracle.nosql.driver.ops.SystemResult;
import oracle.nosql.driver.ops.SystemStatusRequest;
import oracle.nosql.driver.ops.TableRequest;
import oracle.nosql.driver.ops.TableResult;
import oracle.nosql.driver.ops.TableUsageRequest;
import oracle.nosql.driver.ops.TableUsageResult;
import oracle.nosql.driver.ops.WriteMultipleRequest;
import oracle.nosql.driver.ops.WriteMultipleResult;

/**
 * NoSQLHandle is a handle that can be used to access Oracle NoSQL tables. To
 * create a connection represented by NoSQLHandle, request an instance using
 * {@link NoSQLHandleFactory#createNoSQLHandle} and {@link NoSQLHandleConfig},
 * which allows an application to specify default values and other configuration
 * information to be used by the handle.
 * <p>
 * The same interface is available to both users of the Oracle NoSQL Database
 * Cloud Service and the on-premises Oracle NoSQL Database; however, some methods
 * and/or parameters are specific to each environment. The documentation has notes
 * about whether a class, method, or parameter is environment-specific. Unless
 * otherwise noted they are applicable to both environments.
 * <p>
 * A handle has memory and network resources associated with it.
 * Consequently, the {@link NoSQLHandle#close} method must be invoked to free up
 * the resources when the application is done using the handle.  <p> To
 * minimize network activity as well as resource allocation and deallocation
 * overheads, it's best to avoid repeated creation and closing of handles. For
 * example, creating and closing a handle around each operation, would incur
 * large resource allocation overheads resulting in poor application
 * performance.
 * </p>
 * <p>
 * A handle permits concurrent operations, so a single handle is sufficient to
 * access tables in a multi-threaded application. The creation of multiple
 * handles incurs additional resource overheads without providing any
 * performance benefit.
 * </p>
 * <p>
 * With the exception of {@link #close} the operations on this interface follow
 * a similar pattern. They accept a {@link Request} object containing
 * parameters, both required and optional. They return a {@link Result} object
 * containing results. Operation failures throw exceptions. Unique subclasses
 * of {@link Request} and {@link Result} exist for most operations, containing
 * information specific to the operation. All of these operations result in
 * remote calls across a network.
 * </p>
 * <p>
 * All {@link Request} instances support specification of parameters for the
 * operation as well as the ability to override default parameters which may
 * have been specified in {@link NoSQLHandleConfig}, such as request timeouts,
 * {@link Consistency}, etc.
 * </p>
 * <p>
 * Objects returned by methods of this interface can only be used safely by
 * one thread at a time unless synchronized externally. {@link Request} objects
 * are not copied and must not be modified by the application while a method
 * on this interface is using them.
 * </p>
 * <h2>Error and Exception Handling</h2>
 * <p>
 * On success all methods in this interface return {@link Result} objects.
 * Errors are thrown as exceptions.  Some Java exceptions, such as {@link
 * IllegalArgumentException} and {@link NullPointerException} are thrown
 * directly. All other exceptions are instances of {@link NoSQLException},
 * which serves as a base class for NoSQL Database exceptions.
 * </p>
 * <p>
 * {@link NoSQLException} instances are split into 2 broad categories:
 * <ol>
 * <li> Exceptions that may be retried with the expectation that they
 * may succeed on retry. These are instances of {@link RetryableException}</li>
 * <li> Exceptions that may not be retried and if retried, will fail again</li>
 * </ol>
 * <p>
 * Exceptions that may be retried return true for
 * {@link NoSQLException#okToRetry} while those that may not will return false.
 * Examples of retryable exceptions are those which indicate resource
 * consumption violations such as {@link ThrottlingException}.
 * Examples of exceptions that should not be
 * retried are {@link IllegalArgumentException},
 * {@link TableNotFoundException}, and any other exception indicating a
 * syntactic or semantic error.
 * </p>
 * <p>
 * Instances of NoSQLHandle are thread-safe and expected to be shared among
 * threads.
 * </p>
 */
public interface NoSQLHandle extends AutoCloseable {

    /**
     * Deletes a row from a table. The row is identified using a primary key
     * value supplied in {@link DeleteRequest#setKey}
     * <p>
     * By default a delete operation is unconditional and will succeed if the
     * specified row exists. Delete operations can be made conditional based
     * on whether the {@link Version} of an existing row matches that supplied
     * by {@link DeleteRequest#setMatchVersion}.
     * <p>
     * It is also possible to return information about the existing
     * row. The row, including it's {@link Version} and modification time
     * can be optionally returned.
     * The existing row information will only be returned if
     * {@link DeleteRequest#setReturnRow} is true and one of the following
     * occurs:
     * <ul>
     * <li> The {@link DeleteRequest#setMatchVersion} is used and the operation
     * fails because the row exists and its version does not match.
     * </li>
     * <li> The {@link DeleteRequest#setMatchVersion} is not used and the
     * operation succeeds provided that the server supports providing the
     * existing row.
     * </li>
     * </ul>
     * Use of {@link DeleteRequest#setReturnRow} may result in additional
     * consumed read capacity.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    DeleteResult delete(DeleteRequest request);

    /**
     * Gets the row associated with a primary key. On success the value of the
     * row is available using the {@link GetResult#getValue} operation. If there
     * are no matching rows that method will return null.
     * <p>
     * The default {@link Consistency} used for the operation is
     * {@link Consistency#EVENTUAL} unless an explicit value is has been set
     * using {@link NoSQLHandleConfig#setConsistency} or
     * {@link GetRequest#setConsistency}. Use of {@link Consistency#ABSOLUTE}
     * may affect latency of the operation and may result in additional cost
     * for the operation.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    GetResult get(GetRequest request);

    /**
     * Puts a row into a table. This method creates a new row or overwrites
     * an existing row entirely. The value used for the put is in the
     * {@link PutRequest} object and must contain a complete primary key and all
     * required fields.
     * <p>
     * It is not possible to put part of a row.
     * Any fields that are not provided will be defaulted, overwriting any
     * existing value. Fields that are not nullable or defaulted must be
     * provided or an exception will be thrown.
     * <p>
     * By default a put operation is unconditional, but put operations can be
     * conditional based on existence, or not, of a
     * previous value as well as conditional on the {@link Version} of
     * the existing value.
     * <ul>
     * <li>Use {@link Option#IfAbsent} to do a put only if there is no existing
     * row that matches the primary key</li>
     * <li>Use {@link Option#IfPresent} to do a put only if there is an
     * existing row that matches the primary key</li>
     * <li>Use {@link Option#IfVersion} to do a put only if there is an
     * existing row that matches the primary key <em>and</em> its
     * {@link Version} matches that provided</li>
     * </ul>
     * <p>
     * It is also possible to return information about the existing
     * row. The existing row, including it's {@link Version} and modification
     * time can be optionally returned.
     * The existing row information will only be returned if
     * {@link PutRequest#setReturnRow} is true and one of the following occurs:
     * <ul>
     * <li>The {@link Option#IfAbsent} is used and the operation fails because
     * the row already exists.</li>
     * <li>The {@link Option#IfVersion} is used and the operation fails because
     * the row exists and its version does not match.
     * </li>
     * <li>The {@link Option#IfPresent} is used and the operation succeeds
     * provided that the server supports providing the existing row.
     * </li>
     * <li>The {@link Option} is not used and put operation replaces the
     * existing row provided that the server supports providing the existing
     * row.
     * </li>
     * </ul>
     * Use of {@link PutRequest#setReturnRow} may result in additional
     * consumed read capacity.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    PutResult put(PutRequest request);

    /**
     * Executes a sequence of operations associated with a table that share the
     * same <em>shard key</em> portion of their primary keys, all the specified
     * operations are executed within the scope of a single transaction.
     * {@link WriteMultipleRequest}.
     * <p>
     * There are some size-based limitations on this operation:
     * <ul>
     * <li>The max number of individual operations (put, delete) in a single
     * WriteMultiple request is 50.</li>
     * <li>The total request size is limited to 25MB.</li>
     * </ul>
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws RowSizeLimitException if data size in an operation exceeds the
     * limit.
     *
     * @throws BatchOperationNumberLimitException if the number of operations
     * exceeds this limit.
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    WriteMultipleResult writeMultiple(WriteMultipleRequest request);

    /**
     * Deletes multiple rows from a table in an atomic operation.  The
     * key used may be partial but must contain all of the fields that are
     * in the shard key. A range may be specified to delete a range of keys.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    MultiDeleteResult multiDelete(MultiDeleteRequest request);

    /**
     * Queries a table based on the query statement specified in the
     * {@link QueryRequest}.
     * <p>
     * Queries that include a full shard key will execute much more efficiently
     * than more distributed queries that must go to multiple shards.
     * <p>
     * Table- and system-style queries such as "CREATE TABLE ..." or "DROP TABLE .."
     * are not supported by this interface. Those operations must be performed using
     * {@link #tableRequest} or {@link #systemRequest} as appropriate.
     * <p>
     * The amount of data read by a single query request is limited by a system
     * default and can be further limited using
     * {@link QueryRequest#setMaxReadKB}. This limits the amount of data
     * <em>read</em> and not the amount of data <em>returned</em>, which means that
     * a query can return zero results but still have more data to read. This
     * situation is detected by checking if the {@link QueryResult} has a
     * continuation key, using {@link QueryResult#getContinuationKey}. For this
     * reason queries should always operate in a loop, acquiring more results,
     * until the continuation key is null, indicating that the query is done.
     * Inside the loop the continuation key is applied to the
     * {@link QueryRequest} using {@link QueryRequest#setContinuationKey}.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    QueryResult query(QueryRequest request);

    /**
     * Queries a table based on the query statement specified in the
     * {@link QueryRequest} while returning an iterable result.
     * <p>
     * Queries that include a full shard key will execute much more efficiently
     * than more distributed queries that must go to multiple shards.
     * <p>
     * Remote calls, including preparation of a query statement, will not
     * occur until the iteration starts. This means that errors such as an
     * invalid statement or network issue will be thrown from the iterator
     * and not this method.
     * <p>
     * Table- and system-style queries such as "CREATE TABLE ..." or "DROP TABLE .."
     * are not supported by this interface. Those operations must be performed using
     * {@link #tableRequest} or {@link #systemRequest} as appropriate.
     * <p>
     * The results are returned through an iterator, if connected to the
     * cloud, the SDK uses the read/write rate limits set in the
     * NoSQLHandleConfig or they can be overwritten using the
     * {@link Request#setReadRateLimiter(RateLimiter)} and
     * {@link Request#setWriteRateLimiter(RateLimiter)}.
     * <p>
     * Note: Since iterators might use resources until they reach the end, it
     * is necessary to close the QueryIterableResult or use the
     * try-with-resources statement:
     * <pre>
     *    try (
     *        QueryRequest qreq = new QueryRequest()
     *            .setStatement("select * from MyTable");
     *        QueryIterableResult qir = handle.queryIterable(qreq)) {
     *        for( MapValue row : qir) {
     *            // do something with row
     *        }
     *    }
     * </pre>
     *
     * @param request the input parameters for the operation
     *
     * @return the iterable result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    QueryIterableResult queryIterable(QueryRequest request);

    /**
     * Prepares a query for execution and reuse. See {@link #query} for general
     * information and restrictions. It is recommended that prepared queries
     * are used when the same query will run multiple times as execution is
     * much more efficient than starting with a query string every time. The
     * query language and API support query variables to assist with re-use.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    PrepareResult prepare(PrepareRequest request);

    /**
     * Performs an operation on a table. This method is used for creating and
     * dropping tables and indexes as well as altering tables. Only one
     * operation is allowed on a table at any one time.
     * <p>
     * This operation is implicitly asynchronous. The caller must poll using
     * methods on {@link TableResult} to determine when it has completed.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for
     * any other reason
     */
    TableResult tableRequest(TableRequest request);

    /**
     * A convenience method that performs a TableRequest and waits for
     * completion of the operation. This is the same as calling
     * {@link #tableRequest} then
     * calling {@link TableResult#waitForCompletion}. If the operation fails
     * an exception is thrown. All parameters are required.
     *
     * @param request the {@link TableRequest} to perform.
     *
     * @param timeoutMs the amount of time to wait for completion, in
     * milliseconds.
     *
     * @param pollIntervalMs the polling interval for the wait operation.
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws RequestTimeoutException if the operation times out.
     *
     * @throws NoSQLException if the operation cannot be performed for
     * any other reason
     */
    TableResult doTableRequest(TableRequest request,
                               int timeoutMs,
                               int pollIntervalMs);

    /**
     * On-premises only.
     * <p>
     * Performs a system operation on the system, such as
     * administrative operations that don't affect a specific table. For
     * table-specific operations use {@link #tableRequest} or
     * {@link #doTableRequest}.
     * <p>
     * Examples of statements in the {@link SystemRequest} passed to this
     * method include:
     * <ul>
     * <li>CREATE NAMESPACE mynamespace</li>
     * <li>CREATE USER some_user IDENTIFIED BY password</li>
     * <li>CREATE ROLE some_role</li>
     * <li>GRANT ROLE some_role TO USER some_user</li>
     * </ul>
     * <p>
     * This operation is implicitly asynchronous. The caller must poll using
     * methods on {@link SystemResult} to determine when it has completed.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for
     * any other reason
     */
    SystemResult systemRequest(SystemRequest request);

    /**
     * On-premises only.
     * <p>
     * Checks the status of an operation previously performed using
     * {@link #systemRequest}.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for
     * any other reason
     */
    SystemResult systemStatus(SystemStatusRequest request);

    /**
     * Gets static information about the specified table including its
     * state, provisioned throughput and capacity and schema. Dynamic
     * information such as usage is obtained using {@link #getTableUsage}.
     * Throughput, capacity and usage information is only available when using
     * the Cloud Service and will be null or not defined on-premises.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws TableNotFoundException if the specified table does not exist
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    TableResult getTable(GetTableRequest request);

    /**
     * Cloud service only.
     * <p>
     * Gets dynamic information about the specified table such as the current
     * throughput usage. Usage information is collected in time slices and
     * returned in individual usage records. It is possible to specify a
     * time-based range of usage records using input parameters.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws TableNotFoundException if the specified table does not exist
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    TableUsageResult getTableUsage(TableUsageRequest request);

    /**
     * Lists tables, returning table names. If further information about a
     * specific table is desired the {@link #getTable} interface may be used.
     * If a given identity has access to a large number of tables the list may
     * be paged using input parameters.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    ListTablesResult listTables(ListTablesRequest request);

    /**
     * Returns information about and index, or indexes on a table.  If no index
     * name is specified in the {@link GetIndexesRequest}, then information on
     * all indexes is returned.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are
     * invalid or required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     */
    GetIndexesResult getIndexes(GetIndexesRequest request);

    /**
     * On-premises only.
     * <p>
     * Returns the namespaces in a store as an array of String.
     *
     * @return the namespaces or null if none are found
     */
    String[] listNamespaces();

    /**
     * On-premises only.
     * <p>
     * Returns the roles in a store as an array of String.
     *
     * @return the list of roles or null if none are found
     */
    String[] listRoles();

    /**
     * On-premises only.
     * <p>
     * Returns the users in a store as an array of {@link UserInfo}.
     *
     * @return the users or null if none are found
     */
    UserInfo[] listUsers();

    /**
     * On-premises only.
     * <p>
     * A convenience method that performs a SystemRequest and waits for
     * completion of the operation. This is the same as calling {@link
     * #systemRequest} then calling {@link SystemResult#waitForCompletion}. If
     * the operation fails an exception is thrown. All parameters are required.
     * <p>
     * System requests are those related to namespaces and security and are
     * generally independent of specific tables. Examples of statements include:
     * <ul>
     * <li>CREATE NAMESPACE mynamespace</li>
     * <li>CREATE USER some_user IDENTIFIED BY password</li>
     * <li>CREATE ROLE some_role</li>
     * <li>GRANT ROLE some_role TO USER some_user</li>
     * </ul>
     *
     * @param statement the system statement for the operation.
     *
     * @param timeoutMs the amount of time to wait for completion, in
     * milliseconds.
     *
     * @param pollIntervalMs the polling interval for the wait operation.
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws RequestTimeoutException if the operation times out.
     *
     * @throws NoSQLException if the operation cannot be performed for
     * any other reason
     */
    SystemResult doSystemRequest(String statement,
                                 int timeoutMs,
                                 int pollIntervalMs);

    /**
     * Cloud service only.
     * <p>
     * Add replica to a table.
     * <p>
     * This operation is implicitly asynchronous. The caller must poll using
     * methods on {@link TableResult} to determine when it has completed.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for
     * any other reason
     *
     * @since 5.4.13
     */
    TableResult addReplica(AddReplicaRequest request);

    /**
     * Cloud service only.
     * <p>
     * Drop replica from a table.
     * <p>
     * This operation is implicitly asynchronous. The caller must poll using
     * methods on {@link TableResult} to determine when it has completed.
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws NoSQLException if the operation cannot be performed for
     * any other reason
     *
     * @since 5.4.13
     */
    TableResult dropReplica(DropReplicaRequest request);

    /**
     * Cloud service only.
     * <p>
     * Gets replica statistics information
     *
     * @param request the input parameters for the operation
     *
     * @return the result of the operation
     *
     * @throws IllegalArgumentException if any of the parameters are invalid or
     * required parameters are missing
     *
     * @throws TableNotFoundException if the specified table does not exist
     *
     * @throws NoSQLException if the operation cannot be performed for any other
     * reason
     *
     * @since 5.4.13
     */
    ReplicaStatsResult getReplicaStats(ReplicaStatsRequest request);

    /**
     * Returns an object that allows control over how SDK statistics
     * are collected.
     *
     * @return the StatsControl object
     *
     * @since 5.2.30
     */
    StatsControl getStatsControl();

    /**
     * Closes the handle, releasing its memory and network resources. Once
     * this method is closed the handle is no longer usable. Any attempt to
     * use a closed handle will throw {@link IllegalArgumentException}.
     */
    void close();
}
