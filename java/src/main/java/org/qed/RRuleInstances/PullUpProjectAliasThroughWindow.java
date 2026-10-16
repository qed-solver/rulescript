package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — exactly one window in the chain, exactly one deterministic alias whose inputs are all window-referenced (retained) pulled from the bottom project to the top pass-through project, windows modeled as partition-determined per-partition uninterpreted aggregates joined back on a non-nullable partition key (same modeling as FoldGroupByAndWindow/CollapseWindow); multi-window chains, extra candidates and expression-consumed outputs are excluded (QED has no ordering/frame semantics for Window).
public record PullUpProjectAliasThroughWindow() implements RRule {
    static final RelType.VarType kT = RexRN.varType("k", false);
    static final RelType.VarType vT = RexRN.varType("v", true);

    // The bottom project's input: rows (k, v). k = the (non-nullable)
    // partition column, v = a value column both consumed by the window
    // function and used as the alias's (retained) input.
    static final RelRN base = RelRN.scanMany("Base", Seq.of(kT, vT));

    // A Window is modeled (as in FoldGroupByAndWindow / CollapseWindow) as a
    // partition-determined per-partition uninterpreted aggregate grouped by k
    // and joined back to its input with an INNER equi-join on k, then
    // projected to (input cols ++ function output) to mirror Window's
    // pass-through-plus-append output shape.

    // Before: bottom project computes the alias x = f(v) (deterministic,
    // uninterpreted projection f) and passes k, v through: output (k, v, x).
    // The window references k (partition) and v (function input), so k and v
    // are the "retained" entries; x is the "candidate" alias being pulled up.
    static final RelRN lower = new RelRN.ProjectMany(
            Seq.of(base.field(0), base.field(1), base.field(1).proj("f", "f_type")), base);
    static final RelRN aggW = new RelRN.Aggregate(
            lower, Seq.of(lower.field(0)), Seq.of(lower.field(1).aggCall("w")));
    static final RexRN cond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(lower.joinField(0, aggW), lower.joinField(3, aggW)));
    static final RelRN joined = lower.join(JoinRelType.INNER, cond, aggW);
    // joined = (k, v, x, aggW.k, w); window output = (k, v, x, w).
    static final RelRN beforeWindow = new RelRN.ProjectMany(
            Seq.of(joined.field(0), joined.field(1), joined.field(2), joined.field(4)), joined);

    // Before: the top project passes the window output through as bare
    // attributes (k, v, x, w) — x is consumed solely as a bare pass-through.
    static final RelRN beforeRel = new RelRN.ProjectMany(
            Seq.of(beforeWindow.field(0), beforeWindow.field(1), beforeWindow.field(2), beforeWindow.field(3)),
            beforeWindow);

    // After: the bottom project no longer produces x: output (k, v).
    static final RelRN newLower = new RelRN.ProjectMany(
            Seq.of(base.field(0), base.field(1)), base);
    static final RelRN aggW2 = new RelRN.Aggregate(
            newLower, Seq.of(newLower.field(0)), Seq.of(newLower.field(1).aggCall("w")));
    static final RexRN cond2 = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(newLower.joinField(0, aggW2), newLower.joinField(2, aggW2)));
    static final RelRN joined2 = newLower.join(JoinRelType.INNER, cond2, aggW2);
    // joined2 = (k, v, aggW2.k, w); window output = (k, v, w).
    static final RelRN afterWindow = new RelRN.ProjectMany(
            Seq.of(joined2.field(0), joined2.field(1), joined2.field(3)), joined2);

    // After: the top project computes the alias child f(v) itself over the
    // window output (k, v, w), restoring output (k, v, f(v), w). The lookup
    // by expr id is identity-by-name here: same uninterpreted f applied to the
    // same retained input v, so the output schema and values are preserved.
    static final RelRN afterRel = new RelRN.ProjectMany(
            Seq.of(afterWindow.field(0), afterWindow.field(1),
                    afterWindow.field(1).proj("f", "f_type"), afterWindow.field(2)),
            afterWindow);

    @Override
    public RelRN before() {
        return beforeRel;
    }

    @Override
    public RelRN after() {
        return afterRel;
    }
}
