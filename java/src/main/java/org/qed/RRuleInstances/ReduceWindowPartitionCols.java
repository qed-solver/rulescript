package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — the window is modeled as one uninterpreted per-partition aggregate function joined back to its input, and the functional dependency is the syntactic one where the redundant partition column is an uninterpreted function f of the retained partition column k
public record ReduceWindowPartitionCols() implements RRule {
    static final RelRN base = RelRN.scanMany("B", Seq.of(
            RexRN.varType("K_Type", true),
            RexRN.varType("V_Type", true)));

    // Window input rows (k, f(k), v): f(k) is the redundant partition column.
    static final RelRN win = base.project(Seq.of(
            base.field(0),
            new RexRN.Proj(RuleBuilder.create().genericProjectionOp("f", RexRN.varType("F_Type", true)), Seq.of(base.field(0))),
            base.field(1)));

    static final RelRN partFull = new RelRN.Aggregate(
            win,
            Seq.of(win.field(0), win.field(1)),
            Seq.of(win.field(2).aggCall("w")));

    static final RelRN partReducedRaw = new RelRN.Aggregate(
            win,
            Seq.of(win.field(0)),
            Seq.of(win.field(2).aggCall("w")));

    // Re-derive f(k) over the reduced grouping so the window columns line up with the full partitioning.
    static final RelRN partReduced = partReducedRaw.project(Seq.of(
            partReducedRaw.field(0),
            new RexRN.Proj(RuleBuilder.create().genericProjectionOp("f", RexRN.varType("F_Type", true)), Seq.of(partReducedRaw.field(0))),
            partReducedRaw.field(1)));

    // Window partitioned by (k, f(k)): join back on both partition columns.
    static final RexRN condFull = RexRN.and(
            new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(win.joinField(0, partFull), win.joinField(3, partFull))),
            new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(win.joinField(1, partFull), win.joinField(4, partFull))));
    static final RelRN windowFull = win.join(JoinRelType.INNER, condFull, partFull);

    // Window partitioned by k only (f(k) re-derived and joined back on both columns).
    static final RexRN condReduced = RexRN.and(
            new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(win.joinField(0, partReduced), win.joinField(3, partReduced))),
            new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(win.joinField(1, partReduced), win.joinField(4, partReduced))));
    static final RelRN windowReduced = win.join(JoinRelType.INNER, condReduced, partReduced);

    static final SqlOperator top = RuleBuilder.create()
            .genericProjectionOp("Top", RexRN.varType("Top_Type", true));

    @Override
    public RelRN before() {
        return windowFull.project(new RexRN.Proj(top, Seq.of(
                windowFull.field(0), windowFull.field(1), windowFull.field(2), windowFull.field(5))));
    }

    @Override
    public RelRN after() {
        return windowReduced.project(new RexRN.Proj(top, Seq.of(
                windowReduced.field(0), windowReduced.field(1), windowReduced.field(2), windowReduced.field(5))));
    }
}
