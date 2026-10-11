package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — uncorrelated INNER correlate modeled as a true-condition inner (cross) join, projections restricted to bare field references (fixed 2-column-per-side shape with one column pruned per side)
public record ProjectCorrelateTranspose() implements RRule {
    static final RelRN left = RelRN.scanMany("L", Seq.of(
            RexRN.varType("L0_Type", true),
            RexRN.varType("L1_Type", true)));
    static final RelRN right = RelRN.scanMany("R", Seq.of(
            RexRN.varType("R0_Type", true),
            RexRN.varType("R1_Type", true)));

    @Override
    public RelRN before() {
        RelRN base = left.join(JoinRelType.INNER, RexRN.trueLiteral(), right);
        return base.project(Seq.of(base.field(0), base.field(2)));
    }

    @Override
    public RelRN after() {
        RelRN leftProj = left.project(Seq.of(left.field(0)));
        RelRN rightProj = right.project(Seq.of(right.field(0)));
        RelRN newCorrelate = leftProj.join(JoinRelType.INNER, RexRN.trueLiteral(), rightProj);
        return newCorrelate.project(Seq.of(newCorrelate.field(0), newCorrelate.field(1)));
    }
}
