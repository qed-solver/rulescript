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
public record ProjectJoinTranspose() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    static final SqlOperator tlOp =
            RuleBuilder.create().genericProjectionOp("TL", new RelType.VarType("P1_Type", true));
    static final SqlOperator trOp =
            RuleBuilder.create().genericProjectionOp("TR", new RelType.VarType("P2_Type", true));
    static final SqlOperator cOp =
            RuleBuilder.create().genericPredicateOp("C", true);
    static final SqlOperator gOp =
            RuleBuilder.create().genericProjectionOp("G", new RelType.VarType("G_Type", true));

    static final RexRN lRef = left.joinField(0, right);
    static final RexRN rRef = left.joinField(1, right);
    static final RexRN tlBefore = new RexRN.Proj(tlOp, Seq.of(lRef));
    static final RexRN trBefore = new RexRN.Proj(trOp, Seq.of(rRef));
    static final RexRN condBefore = new RexRN.Pred(cOp, Seq.of(tlBefore, trBefore));
    static final RexRN topBefore = new RexRN.Proj(gOp, Seq.of(tlBefore, trBefore));

    static final RelRN leftProject =
            left.project(new RexRN.Proj(tlOp, Seq.of(left.field(0))));
    static final RelRN rightProject =
            right.project(new RexRN.Proj(trOp, Seq.of(right.field(0))));

    static final RexRN p1 = leftProject.joinField(0, rightProject);
    static final RexRN p2 = leftProject.joinField(1, rightProject);
    static final RexRN condAfter = new RexRN.Pred(cOp, Seq.of(p1, p2));
    static final RexRN topAfter = new RexRN.Proj(gOp, Seq.of(p1, p2));

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, condBefore, right).project(topBefore);
    }

    @Override
    public RelRN after() {
        return leftProject.join(JoinRelType.INNER, condAfter, rightProject).project(topAfter);
    }
}
