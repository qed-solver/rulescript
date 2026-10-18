package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only WriteDelta row-level operations whose delta query is a single direct Filter(cond, scan) over a non-nullable key column, with the runtime group filter derived from the same scan+cond (idealized as an exact semi-join on the key; grouping/projection omitted as row-preserving).
public record RowLevelOperationRuntimeGroupFiltering() implements RRule {
    // T: source table scanned by the row-level operation's delta query.
    // col-0 = key (partition/group column, non-nullable so join equality is crisp),
    // col-1 = data.
    static final RelRN T = RelRN.scanMany("T",
        Seq.of(RexRN.varType("KeyType", false), RexRN.varType("DataType", false)));

    // cond: the row-level operation's delta predicate (selects rows to write).
    static final RexRN cond = T.pred("cond");

    // groupFilter: the runtime group filter query, derived from the same delta
    // query; its key projection is idealized here as the filtered scan itself
    // (identical key set, hence equivalent for the semi-join).
    static final RelRN groupFilter = T.filter(cond);

    // Semi-join condition on the key column: T.key = groupFilter.key.
    static final RexRN keyEq = new RexRN.Pred(
        SqlStdOperatorTable.EQUALS,
        Seq.of(T.joinField(0, groupFilter), T.joinField(2, groupFilter)));

    // after-side: delta scan restricted by the runtime group filter.
    static final RelRN withGroupFilter = T.join(JoinRelType.SEMI, keyEq, groupFilter);

    @Override
    public RelRN before() {
        // Original delta-based write query: Filter(cond, T).
        return T.filter(cond);
    }

    @Override
    public RelRN after() {
        // Same delta query with the runtime group filter injected.
        return withGroupFilter.filter(cond);
    }
}