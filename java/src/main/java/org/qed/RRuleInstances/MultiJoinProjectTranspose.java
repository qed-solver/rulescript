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
public record MultiJoinProjectTranspose() implements RRule {
    static final RelRN left = RelRN.scanMany("L", Seq.of(
            new RelType.VarType("L0_Type", true),
            new RelType.VarType("L1_Type", true)));
    static final RelRN right = RelRN.scanMany("R", Seq.of(
            new RelType.VarType("R0_Type", true),
            new RelType.VarType("R1_Type", true)));

    static final SqlOperator tlOp =
            RuleBuilder.create().genericProjectionOp("TL", new RelType.VarType("PL_Type", true));
    static final SqlOperator cOp = RuleBuilder.create().genericPredicateOp("C", true);

    static final RelRN leftProject =
            left.project(new RexRN.Proj(tlOp, Seq.of(left.field(0), left.field(1))));
    static final RexRN condBefore = new RexRN.Pred(cOp, Seq.of(
            leftProject.joinField(0, right),
            leftProject.joinField(1, right),
            leftProject.joinField(2, right)));

    static final RexRN l0 = left.joinField(0, right);
    static final RexRN l1 = left.joinField(1, right);
    static final RexRN r0 = left.joinField(2, right);
    static final RexRN r1 = left.joinField(3, right);
    static final RexRN tlAfter = new RexRN.Proj(tlOp, Seq.of(l0, l1));
    static final RexRN condAfter = new RexRN.Pred(cOp, Seq.of(tlAfter, r0, r1));

    @Override
    public RelRN before() {
        return leftProject.join(JoinRelType.INNER, condBefore, right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER, condAfter, right)
                .project(Seq.of(tlAfter, r0, r1));
    }
}