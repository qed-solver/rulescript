package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — single-column join inputs, one pushed expression per side, one residual predicate, inner join
public record JoinPushExpressions() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    static final SqlOperator flOp =
            RuleBuilder.create().genericProjectionOp("FL", new RelType.VarType("FL_Type", true));
    static final SqlOperator frOp =
            RuleBuilder.create().genericProjectionOp("FR", new RelType.VarType("FR_Type", true));
    static final SqlOperator eqOp = RuleBuilder.create().genericPredicateOp("EQ", true);
    static final SqlOperator resOp = RuleBuilder.create().genericPredicateOp("RES", true);

    static final RexRN lRef = left.joinField(0, right);
    static final RexRN rRef = left.joinField(1, right);
    static final RexRN condBefore = RexRN.and(
            new RexRN.Pred(eqOp, Seq.of(new RexRN.Proj(flOp, Seq.of(lRef)),
                                       new RexRN.Proj(frOp, Seq.of(rRef)))),
            new RexRN.Pred(resOp, Seq.of(lRef, rRef)));

    static final RelRN leftProject = left.project(Seq.of(
            left.field(0),
            new RexRN.Proj(flOp, Seq.of(left.field(0)))));
    static final RelRN rightProject = right.project(Seq.of(
            right.field(0),
            new RexRN.Proj(frOp, Seq.of(right.field(0)))));

    static final RexRN condAfter = RexRN.and(
            new RexRN.Pred(eqOp, Seq.of(leftProject.joinField(1, rightProject),
                                       leftProject.joinField(3, rightProject))),
            new RexRN.Pred(resOp, Seq.of(leftProject.joinField(0, rightProject),
                                         leftProject.joinField(2, rightProject))));

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, condBefore, right);
    }

    @Override
    public RelRN after() {
        return leftProject.join(JoinRelType.INNER, condAfter, rightProject)
                .project(Seq.of(
                        leftProject.joinField(0, rightProject),
                        leftProject.joinField(2, rightProject)));
    }
}
