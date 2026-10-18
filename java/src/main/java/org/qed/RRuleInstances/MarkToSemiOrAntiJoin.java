package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — semi case only (marker conjunct kept true): single-column L and R, one residual filter conjunct P, one projected column p; the LEFT_MARK join is modeled as UNION ALL of the semi branch (marker=true) and the anti branch (marker=false)
public record MarkToSemiOrAntiJoin() implements RRule {
    static final RelRN L = RelRN.scan("L", "L_T");
    static final RelRN R = RelRN.scan("R", "R_T");
    static final RexRN cond = L.joinPred("cond", R);
    static final RelRN semi = L.join(JoinRelType.SEMI, cond, R);
    static final RelRN anti = L.join(JoinRelType.ANTI, cond, R);

    // LEFT_MARK join result: each row of L tagged with whether it has a
    // matching row of R. That is exactly the bag-union of the semi branch
    // (marker = true, one row per matching left row) and the anti branch
    // (marker = false, one row per non-matching left row).
    static final RelRN mark = semi
            .project(Seq.of(semi.field(0), RexRN.trueLiteral()))
            .union(true,
                    anti.project(Seq.of(anti.field(0), RexRN.falseLiteral())));

    static final RuleBuilder builder = RuleBuilder.create();
    static final SqlOperator pOp = builder.genericPredicateOp("P", true);
    static final SqlOperator projOp = builder.genericProjectionOp("p", RexRN.varType("P_T", true));

    @Override
    public RelRN before() {
        RexRN marker = mark.field(1);
        RexRN P = new RexRN.Pred(pOp, Seq.of(mark.field(0)));
        return mark.filter(RexRN.and(marker, P))
                .project(new RexRN.Proj(projOp, Seq.of(mark.field(0))));
    }

    @Override
    public RelRN after() {
        RexRN P = new RexRN.Pred(pOp, Seq.of(semi.field(0)));
        return semi.filter(P)
                .project(new RexRN.Proj(projOp, Seq.of(semi.field(0))));
    }
}
