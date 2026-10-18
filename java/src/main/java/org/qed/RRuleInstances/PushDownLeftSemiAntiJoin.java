package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the LeftSemi/over-Project case of the rule (Spark also fires on LeftAnti and on Aggregate/Window/Union/other unary operators); left projection is an arbitrary multi-expression project and the join condition an uninterpreted predicate over the projected row
public record PushDownLeftSemiAntiJoin() implements RRule {
    // Two-column raw sides: X feeds the left projection, Y is the semi-join's
    // right input.
    static final RelRN x = RelRN.scanMany("X", Seq.of(
            new RelType.VarType("X0_Type", true),
            new RelType.VarType("X1_Type", true)));
    static final RelRN y = RelRN.scanMany("Y", Seq.of(
            new RelType.VarType("Y0_Type", true),
            new RelType.VarType("Y1_Type", true)));

    // Shared uninterpreted symbols, reused in both patterns so QED treats the
    // occurrences as the same symbol: two projection expressions P0 and P1,
    // each a function of X's two columns, and the join condition C.
    static final SqlOperator p0Op =
            RuleBuilder.create().genericProjectionOp("P0", new RelType.VarType("P0_Type", true));
    static final SqlOperator p1Op =
            RuleBuilder.create().genericProjectionOp("P1", new RelType.VarType("P1_Type", true));
    static final SqlOperator cOp = RuleBuilder.create().genericPredicateOp("C", true);

    // Before: SemiJoin(Xp, Y) where Xp = Project([P0, P1], X); the condition
    // is C over the whole join row (projected X row, then Y's columns).
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

    // After: Project([P0, P1], SemiJoin(X, Y, C(P0(x0, x1), P1(x0, x1), y0, y1))).
    // Raw join row over X then Y: 0,1 = X's columns; 2,3 = Y's columns.
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
