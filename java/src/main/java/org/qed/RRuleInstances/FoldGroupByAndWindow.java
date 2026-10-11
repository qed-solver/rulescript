package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

// SCOPE: PARTIAL — one window function whose output is a partition-determined aggregate, and every GroupBy aggregate references only Window-input cols (case 5a); the 5b ConstAgg/FirstAgg pass-through of window outputs needs per-aggregate algebra QED lacks.
public record FoldGroupByAndWindow() implements RRule {
    static final RelType.VarType kT = RexRN.varType("k", false);
    static final RelType.VarType vT = RexRN.varType("v", false);

    // Window's input: rows (k, v), where k is the partition column.
    static final RelRN input = RelRN.scanMany("Input", Seq.of(kT, vT));

    // Window operator, satisfying condition 2: its function output w is
    // functionally determined by the partition column k, i.e. it yields one
    // value per partition — modeled as an uninterpreted aggregate grouped by k.
    static final RelRN window = new RelRN.Aggregate(
            input,
            Seq.of(input.field(0)),
            Seq.of(input.field(1).aggCall("w")));

    // The Window operator's output shape (input cols + window cols) is an
    // INNER equi-join of the input with the per-partition values on k: each
    // input row (k, v) matches exactly one window row, preserving (k, v).
    // Both JoinFields are built on the join's actual left (input): ordinal 0
    // = input.k, ordinal 2 = window.k in the concatenated field space.
    static final RexRN cond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(input.joinField(0, window), input.joinField(2, window)));
    static final RelRN joined = input.join(JoinRelType.INNER, cond, window);

    // Before: GroupBy over the Window's output (conditions 1 and 3: grouping
    // cols = partition cols), with every aggregate referencing only the
    // window's input columns (case 5a).
    static final RelRN beforeRel = new RelRN.Aggregate(
            joined,
            Seq.of(joined.field(0)),
            Seq.of(joined.field(1).aggCall("a")));

    // After: the window is eliminated and the same group-by is taken directly
    // over the window's input — 5a aggregates are left alone.
    static final RelRN afterRel = new RelRN.Aggregate(
            input,
            Seq.of(input.field(0)),
            Seq.of(input.field(1).aggCall("a")));

    @Override
    public RelRN before() {
        return beforeRel;
    }

    @Override
    public RelRN after() {
        return afterRel;
    }
}
