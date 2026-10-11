package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

public record UnionPullUpConstants() implements RRule {
    static final RexRN lit = RexRN.trueLiteral();

    static final RelRN a = RelRN.scanMany("A", Seq.of(RexRN.varType("A_Type", true), RexRN.varType("C_Type", true)));
    static final RelRN b = RelRN.scanMany("B", Seq.of(RexRN.varType("B_Type", true), RexRN.varType("C_Type", true)));

    static final RelRN aProj = a.project(Seq.of(lit, a.field(1)));
    static final RelRN bProj = b.project(Seq.of(lit, b.field(1)));

    static final RelRN aInner = a.project(Seq.of(a.field(1)));
    static final RelRN bInner = b.project(Seq.of(b.field(1)));

    @Override
    public RelRN before() {
        return aProj.union(true, bProj);
    }

    @Override
    public RelRN after() {
        RelRN u = aInner.union(true, bInner);
        return u.project(Seq.of(lit, u.field(0)));
    }
}
