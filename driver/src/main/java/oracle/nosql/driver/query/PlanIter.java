/*-
 * Copyright (c) 2011, 2025 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 *  https://oss.oracle.com/licenses/upl/
 */

package oracle.nosql.driver.query;

import java.io.IOException;

import oracle.nosql.driver.query.QueryException.Location;
import oracle.nosql.driver.values.FieldValue;
import oracle.nosql.driver.util.ByteInputStream;
import oracle.nosql.driver.util.SerializationUtil;

/**
 * Base class for all query-plan iterators that may appear at the driver.
 *
 * The query compiler produces the "query execution plan" as a tree of plan
 * iterators. Roughly speaking, each kind of iterator evaluates a different
 * kind of expression that may appear in a query.
 *
 * Each plan iterator has an open()-next()-close() interface. To execute a
 * query, the "user" must first call open() on the root iterator and then
 * call next() a number of times to retrieve the results. Finally, after the
 * application has retrieved all the results, or when it is not interested in
 * retrieving any more results, it must call close() to release any resources
 * held by the iterators.
 *
 * In general, these calls are propagated top-down within the execution plan,
 * and results flow bottom-up. More specifically, each next() call on the root
 * iterator produces one item (a FieldValue) in the result set of the query.
 * If there are no more results to be produced, the next() call will return
 * false. Actually, the same is true for all iterators: each next() call on a
 * plan iterator produces one item in the result set of that iterator or returns
 * false if there are no more results. So, in the most general case, the result
 * set of each iterator is a sequence of zero or more items.
 *
 * The root iterator will always produce MapValues, but other iterators may
 * produces different kinds of values (instances of FieldValue).
 *
 * Iterator state and registers:
 * -----------------------------
 *
 * As mentioned already, each next() call on an iterator produces at most one
 * result item. However, the result items are not returned by the next() call
 * directly. Instead, each iterator places its current result (the item produced
 * by the current invocation of the next() call) in its "result register", where
 * a consumer iterator can pick it up from. A "register" is just an entry in an
 * array of FieldValue instances. This array is created during the creation of
 * the RuntimeControlBlock (RCB) and is stored in the RCB. Each iterator knows
 * the position of its result register within the array, and will provide this
 * info to its parent (consuming) iterator. All iterators have a result reg.
 * Notice, however, that some iterators may share the same result reg. For
 * example, if an iterator A is just filtering the result of another iterator B,
 * A can use B's result reg as its own result reg as well.
 *
 * Each iterator has some state that it needs to maintain across open()-next()-
 * close() invocations. For each kind of iterator, its state is represented by
 * an instance of PlanIterState or subclass of. Like the result registers, the
 * states of each iterator in the plan is stored in a PlanIterState array that
 * is created during the creation of the RuntimeControlBlock (RCB) and is stored
 * in the RCB. Each iterator knows the position of its state within this array.
 *
 * Storing the dynamic state of each iterator outside the iterator itself is
 * important, because it makes the query plan immutable, and as a result, it
 * allows multiple threads to concurrently execute the same query using a
 * single, shared instance of the query plan, but with a different state per
 * thread.
 *
 * The state of each iterator is created and initialized during the open() call,
 * it is updated during subsequent next() calls, and is released during close().
 * Each iterator also has a reset() method, which is similar to open(): it
 * re-initializes the iterator state so that the iterator will produce a new
 * result set from the beginning during subsequent next() calls.
 *
 * Data members:
 * -------------
 *
 * theResultReg:
 * The position within the array of registers, of the register where this
 * iterator will store each item generated by a next() call.
 *
 * theStatePos:
 * The position, within the state array, where the state of this iterator is
 * stored.
 *
 * theLocation:
 * The location, within the query text, of the expression implemented by this
 * iterator. It is used only in error messages, to indicate the location within
 * the query text of the expression that encountered the error.
 */
public abstract class PlanIter {

    /**
     * Enumeration of the different kinds of iterators (there is one PlanIter
     * subclass for each kind).
     *
     * NOTE: The kvcode stored with each value in this enum matches the ordinal
     * of the corresponding PlanIterKind in kvstore.
     */
    public static enum PlanIterKind {

        RECV(17),
        SFW(14),
        SORT(47),

        CONST(0),
        VAR_REF(1),
        EXTERNAL_VAR_REF(2),

        FIELD_STEP(11),

        ARITH_OP(8),

        FN_SIZE(15),
        FN_SUM(39),
        FN_MIN_MAX(41),

        GROUP(65),
        SORT2(66),
        FN_COLLECT(78);

        private static final PlanIterKind[] VALUES = values();

        int kvcode;

        PlanIterKind(int kvcode) {
            this.kvcode = kvcode;
        }

        static PlanIterKind valueOf(int kvcode) {
            for (PlanIterKind kind : VALUES) {
                if (kind.kvcode == kvcode) {
                    return kind;
                }
            }
            //System.out.println("Unexpected iterator kind: " + kvcode);
            return null;
        }
    }

    /**
     * Some iterator classes may implement more than one SQL builtin function.
     * In such cases, each particular instance of the iterator will store a
     * FuncCode to specify what function is implemented by this instance.
     *
     * NOTE: The kvcode stored with each value in this enum matches the ordinal
     * of the corresponding FuncCode in kvstore.
     */
    static enum FuncCode {

        OP_ADD_SUB(14),
        OP_MULT_DIV(15),

        FN_COUNT_STAR(42),
        FN_COUNT(43),
        FN_COUNT_NUMBERS(44),
        FN_SUM(45),
        FN_MIN(47),
        FN_MAX(48),
        FN_ARRAY_COLLECT(91),
        FN_ARRAY_COLLECT_DISTINCT(92);

        private static final FuncCode[] VALUES = values();

        int kvcode;

        FuncCode(int kvcode) {
            this.kvcode = kvcode;
        }

        public static FuncCode valueOf(int kvcode) {
            for (FuncCode code : VALUES) {
                if (code.kvcode == kvcode) {
                    return code;
                }
            }

            throw new QueryStateException("Unknown function kind: " + kvcode);
        }

    }

    static boolean theTraceDeser = false;

    final int theResultReg;

    final int theStatePos;

    final Location theLocation;

    protected PlanIter(
        ByteInputStream in,
        short serialVersion) throws IOException {

        theResultReg = readPositiveInt(in, true);
        theStatePos = readPositiveInt(in);
        theLocation = new Location(readPositiveInt(in),
                                   readPositiveInt(in),
                                   readPositiveInt(in),
                                   readPositiveInt(in));
    }

    public final int getResultReg() {
        return theResultReg;
    }

    public Location getLocation() {
        return theLocation;
    }

    PlanIterState getState(RuntimeControlBlock rcb) {
        return rcb.getState(theStatePos);
    }

    public abstract PlanIterKind getKind();

    PlanIter getInputIter() {
        throw new QueryStateException(
            "Method not implemented for iterator " + getKind());
    }

    /*
     * Get the current value of an aggregate function. If the reset param is
     * true, the value is the final one and this method will also reset the
     * state of the associated aggregate-function iterator. In this case the
     * method is called when a group is completed. If reset is false, it is
     * actually the value of the aggr function on the 1st tuple of a new
     * group.
     */
    FieldValue getAggrValue(RuntimeControlBlock rcb, boolean reset)  {
        throw new QueryStateException(
            "Method not implemented for iterator " + getKind());
    }

    public abstract void open(RuntimeControlBlock rcb);

    public abstract boolean next(RuntimeControlBlock rcb);

    public abstract void reset(RuntimeControlBlock rcb);

    public abstract void close(RuntimeControlBlock rcb);

    /**
     * Returns whether the iterator is in the DONE or CLOSED state.  CLOSED is
     * included because, in the order of states, a CLOSED iterator is also
     * DONE.
     */
    public boolean isDone(RuntimeControlBlock rcb) {
        final PlanIterState state = rcb.getState(theStatePos);
        return state.isDone() || state.isClosed();
    }

    public final String display() {
        StringBuilder sb = new StringBuilder();
        display(sb, new QueryFormatter());
        return sb.toString();
    }

    /*
     * This method must be overriden by iterators that implement a family of
     * builtin functions (and as a result have a FuncCode data member).
     * Currently, this method is used only in the display method below.
     */
    FuncCode getFuncCode() {
        return null;
    }

    void display(StringBuilder sb, QueryFormatter formatter) {

        formatter.indent(sb);

        displayName(sb);
        displayRegs(sb);

        sb.append("\n");
        formatter.indent(sb);
        sb.append("[\n");
        formatter.incIndent();
        displayContent(sb, formatter);
        formatter.decIndent();
        sb.append("\n");
        formatter.indent(sb);
        sb.append("]");
    }

    void displayName(StringBuilder sb) {

        if (getFuncCode() != null) {
            sb.append(getFuncCode());
        } else {
            sb.append(getKind());
        }
    }

    final void displayRegs(StringBuilder sb) {

        sb.append("(");
        sb.append("[").append(theResultReg).append("]");
        sb.append(")");
    }

    protected abstract void displayContent(
        StringBuilder sb,
        QueryFormatter formatter);

    public static PlanIter deserializeIter(
        ByteInputStream in,
        short serialVersion) throws IOException {

        int ord = in.readByte();

        if (ord == -1) {
            return null;
        }

        PlanIterKind kind = PlanIterKind.valueOf(ord);

        if (theTraceDeser) {
            System.out.println("Deserializing " + kind + " iter");
        }

        PlanIter iter = null;

        switch (kind) {
        case SORT:
        case SORT2:
            iter = new SortIter(in, kind, serialVersion);
            break;
        case GROUP:
            iter = new GroupIter(in, serialVersion);
            break;
        case SFW:
            iter = new SFWIter(in, serialVersion);
            break;
        case RECV:
            iter = new ReceiveIter(in, serialVersion);
            break;
        case CONST:
            iter = new ConstIter(in, serialVersion);
            break;
        case VAR_REF:
            iter = new VarRefIter(in, serialVersion);
            break;
        case EXTERNAL_VAR_REF:
            iter = new ExternalVarRefIter(in, serialVersion);
            break;
        case FIELD_STEP:
            iter = new FieldStepIter(in, serialVersion);
            break;
        case ARITH_OP:
            iter = new ArithOpIter(in, serialVersion);
            break;
        case FN_SIZE:
            iter = new FuncSizeIter(in, serialVersion);
            break;
        case FN_SUM:
            iter = new FuncSumIter(in, serialVersion);
            break;
        case FN_MIN_MAX:
            iter = new FuncMinMaxIter(in, serialVersion);
            break;
        case FN_COLLECT:
            iter = new FuncCollectIter(in, serialVersion);
            break;
        default:
            throw new QueryStateException(
                "Unknown query iterator kind: " + kind);
        }

        if (theTraceDeser) {
            System.out.println("Done Deserializing " + kind + " iter");
        }

        return iter;
    }

    static PlanIter[] deserializeIters(
        ByteInputStream in,
        short serialVersion) throws IOException {

        final int numArgs = SerializationUtil.readSequenceLength(in);
        final PlanIter[] iters = new PlanIter[numArgs];
        for (int i = 0; i < numArgs; i++) {
            iters[i] = deserializeIter(in, serialVersion);
        }
        return iters;
    }

    /**
     * Read an int value and check if the value is {@literal >=} 0.
     */
    static int readPositiveInt(ByteInputStream in) throws IOException {
        return readPositiveInt(in, false);
    }

    /**
     * Read an int value and check if the value is {@literal >=} 0, the
     * {@code allowNegOne} indicates if -1 is valid value or not.
     */
    static int readPositiveInt(ByteInputStream in, boolean allowNegOne)
        throws IOException {

        int value = in.readInt();
        checkPositiveInt(value, allowNegOne);
        return value;
    }

    private static void checkPositiveInt(int value, boolean allowNegOne) {
        if (allowNegOne) {
            if (value < -1) {
                throw new IllegalArgumentException(value + " is invalid, " +
                    "it must be a positive value or -1");
            }
        } else {
            if (value < 0) {
                throw new IllegalArgumentException(value + " is invalid, " +
                    "it must be a positive value");
            }
        }
    }

    /**
     * Read an ordinal number value and validate the value in the range
     * 0 ~ (numValues - 1).
     */
    static short readOrdinal(ByteInputStream in, int numValues) throws IOException {

        short index = in.readShort();
        if (index < 0 || index >= numValues) {
            throw new IllegalArgumentException(index + " is invalid, it must " +
                "be in a range 0 ~ " + numValues);
        }
        return index;
    }

    static SortSpec[] readSortSpecs(ByteInputStream in) throws IOException {

        int len = SerializationUtil.readSequenceLength(in);

        if (len == -1) {
            return null;
        }

        SortSpec[] specs = new SortSpec[len];

        for (int i = 0; i < len; ++i) {
            specs[i] = new SortSpec(in);
        }

        return specs;
    }

    public static String printByteArray(byte[] bytes) {

        if (bytes == null) {
            return "null";
        }

        StringBuffer sb = new StringBuffer();

        sb.append("[");

        for (byte b : bytes) {
            sb.append(b).append(" ");
        }

        sb.append("]");

        return sb.toString();
    }

    public static String printIntArray(int[] ints) {

        if (ints == null) {
            return "null";
        }

        StringBuffer sb = new StringBuffer();

        sb.append("[ ");

        for (int i =0; i < ints.length; ++i) {
            int v = ints[i];
            sb.append(v);
            if (i < ints.length - 1) {
                sb.append(", ");
            }
        }

        sb.append(" ]");

        return sb.toString();
    }
}
