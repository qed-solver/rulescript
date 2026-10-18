package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RRule;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — INNER join only; one column per side; both-side conjunct Or(And(p(l),q(r)),And(p(l),s(r)))
public record PushExtraPredicateThroughJoin() implements RRule {
    static final RelRN L = RelRN.scan("L", "L_Type");
    static final RelRN R = RelRN.scan("R", "R_Type");

    static final SqlOperator p = RuleBuilder.create().genericPredicateOp("p", true);
    static final SqlOperator q = RuleBuilder.create().genericPredicateOp("q", true);
    static final SqlOperator s = RuleBuilder.create().genericPredicateOp("s", true);

    static final RexRN pl = new RexRN.Pred(p, Seq.of(L.field(0)));
    static final RexRN pr = new RexRN.Pred(q, Seq.of(R.field(0)));
    static final RexRN sr = new RexRN.Pred(s, Seq.of(R.field(0)));

    static final RexRN plj = new RexRN.Pred(p, Seq.of(L.joinField(0, R)));
    static final RexRN prj = new RexRN.Pred(q, Seq.of(L.joinField(1, R)));
    static final RexRN srj = new RexRN.Pred(s, Seq.of(L.joinField(1, R)));

    static final RexRN conj = new RexRN.Or(Seq.of(
            new RexRN.And(Seq.of(plj, prj)),
            new RexRN.And(Seq.of(plj, srj))));

    @Override
    public RelRN before() {
        return L.join(JoinRelType.INNER, conj, R);
    }

    @Override
    public RelRN after() {
        return L.filter(new RexRN.Or(Seq.of(pl, pl)))
                .join(JoinRelType.INNER, conj, R.filter(new RexRN.Or(Seq.of(pr, sr))));
    }
}
