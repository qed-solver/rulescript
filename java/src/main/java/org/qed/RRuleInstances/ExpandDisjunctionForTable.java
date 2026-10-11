package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Calcite
public record ExpandDisjunctionForTable() implements RRule {
    static final RelRN t1 = RelRN.scan("t1", "T1_Type");
    static final RelRN t2 = RelRN.scan("t2", "T2_Type");

    static final RexRN a = t1.joinField(0, t2).pred("pL1");
    static final RexRN c = t1.joinField(0, t2).pred("pL2");
    static final RexRN b = t1.joinField(1, t2).pred("pR1");
    static final RexRN d = t1.joinField(1, t2).pred("pR2");
    static final RexRN e = t1.joinPred("pJ", t2);

    static final RexRN core = new RexRN.Or(Seq.of(RexRN.and(a, b), RexRN.and(c, d)));
    static final RexRN cond = RexRN.and(e, core);

    @Override
    public RelRN before() {
        return t1.join(JoinRelType.INNER, cond, t2);
    }

    @Override
    public RelRN after() {
        return t1.join(JoinRelType.INNER,
            RexRN.and(cond, new RexRN.Or(Seq.of(a, c)), new RexRN.Or(Seq.of(b, d))), t2);
    }
}
