package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

// SCOPE: PARTIAL — window functions modeled as partition-determined per-partition uninterpreted aggregates joined back to the input (no frame/ordering semantics, same modeling as FoldGroupByAndWindow); fixed shape with exactly two window functions, the one needed by the outer projection is kept and the other pruned.
public record PruneWindowOutputCols() implements RRule {
    static final RelType.VarType kT = RexRN.varType("k", false);
    static final RelType.VarType vT = RexRN.varType("v", false);

    // The Window's input: rows (k, v), k the partition column.
    static final RelRN input = RelRN.scanMany("Input", Seq.of(kT, vT));

    // Window with two functions: kept output w1 (needed by the outer
    // projection) and unused output w2 (to be pruned). Modeled as one
    // per-partition aggregate producing one row (k, w1, w2) per partition.
    static final RelRN window = new RelRN.Aggregate(
            input,
            Seq.of(input.field(0)),
            Seq.of(input.field(1).aggCall("w1"), input.field(1).aggCall("w2")));

    // Window output = input rows joined with their partition's window row on k.
    // Join layout: (input.k, input.v, window.k, window.w1, window.w2).
    static final RexRN cond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(input.joinField(0, window), input.joinField(2, window)));
    static final RelRN windowOut = input.join(JoinRelType.INNER, cond, window);

    // The outer projection needs input cols (k, v) plus window col w1 (at
    // index 3); w2 (index 4) is never referenced, so CanPruneWindows holds.
    static final RelRN beforeRel = windowOut.project(
            Seq.of(windowOut.field(0), windowOut.field(1), windowOut.field(3)));

    // After: the Window keeps only the needed function (w1); the join layout
    // becomes (input.k, input.v, window.k, window.w1), so w1 is at index 3
    // here too, and the same projection selects it.
    static final RelRN prunedWindow = new RelRN.Aggregate(
            input,
            Seq.of(input.field(0)),
            Seq.of(input.field(1).aggCall("w1")));
    static final RelRN prunedOut = input.join(JoinRelType.INNER, cond, prunedWindow);
    static final RelRN afterRel = prunedOut.project(
            Seq.of(prunedOut.field(0), prunedOut.field(1), prunedOut.field(3)));

    @Override
    public RelRN before() {
        return beforeRel;
    }

    @Override
    public RelRN after() {
        return afterRel;
    }
}
