package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.apache.calcite.rel.core.JoinRelType;
import kala.collection.Seq;

// ORIGIN: Spark
// SCOPE: PARTIAL — fixed LEFT join kind, exactly one uninterpreted condition shared by the Correlate and the Join (no separate non-correlation condition), and the subquery side is an unchanged scan (no DomainJoin rewriting).
public record RewriteLateralSubquery() implements RRule {
    static final RelRN left = RelRN.scanMany("L", Seq.of(RexRN.varType("l0", true), RexRN.varType("l1", true)));
    static final RelRN right = RelRN.scanMany("S", Seq.of(RexRN.varType("s0", true), RexRN.varType("s1", true)));
    static final RexRN.Pred cond = left.joinPred("lateral_cond", right);

    @Override
    public RelRN before() {
        return left.correlate(JoinRelType.LEFT, cond.operator(), right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.LEFT, cond, right);
    }
}