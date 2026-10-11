package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

public record SemiJoinProjectTranspose() implements RRule {
    static final RelRN x = RelRN.scanMany("X", Seq.of(
            new RelType.VarType("X0_Type", true),
            new RelType.VarType("X1_Type", true)));
    static final RelRN y = RelRN.scanMany("Y", Seq.of(
            new RelType.VarType("Y0_Type", true),
            new RelType.VarType("Y1_Type", true)));

    static final SqlOperator p0Op =
            RuleBuilder.create().genericProjectionOp("P0", new RelType.VarType("P0_Type", true));
    static final SqlOperator p1Op =
            RuleBuilder.create().genericProjectionOp("P1", new RelType.VarType("P1_Type", true));
    static final SqlOperator cOp = RuleBuilder.create().genericPredicateOp("C", true);

    static final RexRN x0 = x.field(0);
    static final RexRN x1 = x.field(1);
    static final RelRN xProject = x.project(Seq.of(
            new RexRN.Proj(p0Op, Seq.of(x0, x1)),
            new RexRN.Proj(p1Op, Seq.of(x0, x1))));
    static final RexRN condBefore = new RexRN.Pred(cOp, Seq.of(
            xProject.joinField(0, y),
            xProject.joinField(1, y),
            xProject.joinField(2, y),
            xProject.joinField(3, y)));

    static final RexRN xl0 = x.joinField(0, y);
    static final RexRN xl1 = x.joinField(1, y);
    static final RexRN yr0 = x.joinField(2, y);
    static final RexRN yr1 = x.joinField(3, y);
    static final RexRN p0After = new RexRN.Proj(p0Op, Seq.of(xl0, xl1));
    static final RexRN p1After = new RexRN.Proj(p1Op, Seq.of(xl0, xl1));
    static final RexRN condAfter = new RexRN.Pred(cOp, Seq.of(p0After, p1After, yr0, yr1));

    @Override
    public RelRN before() {
        return xProject.join(JoinRelType.SEMI, condBefore, y);
    }

    @Override
    public RelRN after() {
        return x.join(JoinRelType.SEMI, condAfter, y)
                .project(Seq.of(p0After, p1After));
    }
}
