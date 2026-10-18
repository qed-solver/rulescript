package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — 2-column input; the lower Project consists of exactly one uninterpreted expression E0(x, y) plus a pass-through of source column x, and the upper Project consists of exactly two uninterpreted expressions P0, P1 each referencing both lower outputs, all of which are inlined (no never/maybe-inline producers kept below)
public record CollapseProject() implements RRule {
    // Source table with two columns x, y.
    static final RelRN source = RelRN.scanMany("S", Seq.of(
            RexRN.varType("X_Type", true),
            RexRN.varType("Y_Type", true)));
    static final RexRN x = source.field(0);
    static final RexRN y = source.field(1);

    // Lower Project list: a new uninterpreted expression E0(x, y) (an Alias in
    // Spark) and a pass-through of the source's first column x.
    static final SqlOperator e0Op =
            RuleBuilder.create().genericProjectionOp("E0", new RelType.VarType("E0_Type", true));
    static final RexRN e0 = new RexRN.Proj(e0Op, Seq.of(x, y));
    static final RelRN lower = source.project(Seq.of(e0, x));

    // Fields of the lower Project, referenced by the upper Project in before().
    static final RexRN c0 = lower.field(0);
    static final RexRN c1 = lower.field(1);

    // Upper Project list: two uninterpreted expressions P0, P1 over both lower outputs.
    static final SqlOperator p0Op =
            RuleBuilder.create().genericProjectionOp("P0", new RelType.VarType("P0_Type", true));
    static final SqlOperator p1Op =
            RuleBuilder.create().genericProjectionOp("P1", new RelType.VarType("P1_Type", true));
    static final RexRN p0Before = new RexRN.Proj(p0Op, Seq.of(c0, c1));
    static final RexRN p1Before = new RexRN.Proj(p1Op, Seq.of(c0, c1));

    @Override
    public RelRN before() {
        return lower.project(Seq.of(p0Before, p1Before));
    }

    // Merged list, per CollapseProject.buildCleanedProjectList: each upper
    // expression has its references to lower aliases replaced by the lower
    // expressions (so c0 becomes E0(x, y)), while references to the
    // pass-through attribute stay as direct references to x on the source.
    static final RexRN p0Merged = new RexRN.Proj(p0Op, Seq.of(e0, x));
    static final RexRN p1Merged = new RexRN.Proj(p1Op, Seq.of(e0, x));

    @Override
    public RelRN after() {
        return source.project(Seq.of(p0Merged, p1Merged));
    }
}