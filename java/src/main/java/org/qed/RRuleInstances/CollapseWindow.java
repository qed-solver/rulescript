package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — identical non-empty partition specs and identical order specs, one partition-determined window expression per window (modeled as uninterpreted per-partition aggregates), and the parent window's expression referencing only grandchild columns; the empty-order-spec merge directions are excluded (QED has no ordering semantics).
public record CollapseWindow() implements RRule {
    static final RelType.VarType kT = RexRN.varType("k", false);
    static final RelType.VarType vT = RexRN.varType("v", false);

    // grandChild: rows (k, v). k = the shared partition column (both windows
    // have the same partition spec), v = an other input column.
    static final RelRN base = RelRN.scanMany("Base", Seq.of(kT, vT));

    // A partition-determined window function is modeled (as in the precedent
    // FoldGroupByAndWindow) as an uninterpreted per-partition aggregate
    // grouped by k, joined back to the input with an INNER equi-join on k,
    // then projected to (input cols ++ function output) to mirror Window's
    // pass-through-plus-append output shape.

    // Child window w2 = Window(f1, grandChild).
    static final RelRN agg1 = new RelRN.Aggregate(
            base, Seq.of(base.field(0)), Seq.of(base.field(1).aggCall("f1")));
    static final RelRN inner = base.join(JoinRelType.INNER,
            new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                    Seq.of(base.joinField(0, agg1), base.joinField(2, agg1))),
            agg1); // 0=k, 1=v, 2=agg1.k, 3=f1
    static final RelRN childWin = new RelRN.ProjectMany(
            Seq.of(inner.field(0), inner.field(1), inner.field(3)), inner); // (k, v, f1)

    // Parent window w1 = Window(f2, w2). f2 references only grandChild.output
    // (the independence condition), so it is computed over `base`.
    static final RelRN agg2 = new RelRN.Aggregate(
            base, Seq.of(base.field(0)), Seq.of(base.field(1).aggCall("f2")));
    static final RelRN outer = childWin.join(JoinRelType.INNER,
            new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                    Seq.of(childWin.joinField(0, agg2), childWin.joinField(3, agg2))),
            agg2); // 0=k, 1=v, 2=f1, 3=agg2.k, 4=f2

    // Before: Window(f2, Window(f1, grandChild)) — two nested window passes,
    // output (k, v, f1, f2).
    static final RelRN beforeRel = new RelRN.ProjectMany(
            Seq.of(outer.field(0), outer.field(1), outer.field(2), outer.field(4)), outer);

    // After: the merged Window(f1, f2, grandChild) — one pass computing both
    // partition-determined functions, output (k, v, f1, f2).
    static final RelRN agg12 = new RelRN.Aggregate(
            base, Seq.of(base.field(0)),
            Seq.of(base.field(1).aggCall("f1"), base.field(1).aggCall("f2")));
    static final RelRN merged = base.join(JoinRelType.INNER,
            new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                    Seq.of(base.joinField(0, agg12), base.joinField(2, agg12))),
            agg12); // 0=k, 1=v, 2=agg12.k, 3=f1, 4=f2
    static final RelRN afterRel = new RelRN.ProjectMany(
            Seq.of(merged.field(0), merged.field(1), merged.field(3), merged.field(4)), merged);

    @Override
    public RelRN before() {
        return beforeRel;
    }

    @Override
    public RelRN after() {
        return afterRel;
    }
}
