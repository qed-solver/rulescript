package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import kala.collection.Seq;

// SCOPE: PARTIAL — LEFT outer join with INNER inner join over single-column L/IL/IR and one 2-arg computed projection f
public record TryDecorrelateProjectInnerJoin() implements RRule {
    static final SqlOperator on = RuleBuilder.create().genericPredicateOp("on", true);
    static final SqlOperator innerOn = RuleBuilder.create().genericPredicateOp("innerOn", true);
    static final SqlOperator f = RuleBuilder.create().genericProjectionOp("f", RexRN.varType("INTEGER", true));

    static final RelRN L = RelRN.scan("L", "INTEGER");
    static final RelRN IL = RelRN.scan("IL", "INTEGER");
    static final RelRN IR = RelRN.scan("IR", "INTEGER");

    @Override
    public RelRN before() {
        RelRN innerBefore = IL.join(JoinRelType.INNER, new RexRN.Pred(innerOn, IL.joinFields(IR)), IR);
        RelRN rightBefore = innerBefore.project(Seq.of(
            innerBefore.field(0),
            new RexRN.Proj(f, Seq.of(innerBefore.field(0), innerBefore.field(1)))));
        RexRN onBefore = new RexRN.Pred(on, Seq.of(
            L.joinField(0, rightBefore),
            L.joinField(1, rightBefore),
            L.joinField(2, rightBefore)));
        return L.join(JoinRelType.LEFT, onBefore, rightBefore);
    }

    @Override
    public RelRN after() {
        RelRN innerAfter = IL.join(JoinRelType.INNER, RexRN.trueLiteral(), IR);
        RelRN rightAfter = innerAfter.project(Seq.of(
            innerAfter.field(0),
            innerAfter.field(1),
            new RexRN.Proj(f, Seq.of(innerAfter.field(0), innerAfter.field(1)))));
        RexRN onAfter = RexRN.and(
            new RexRN.Pred(on, Seq.of(
                L.joinField(0, rightAfter),
                L.joinField(1, rightAfter),
                L.joinField(3, rightAfter))),
            new RexRN.Pred(innerOn, Seq.of(
                L.joinField(1, rightAfter),
                L.joinField(2, rightAfter))));
        RelRN j = L.join(JoinRelType.LEFT, onAfter, rightAfter);
        return j.project(Seq.of(j.field(0), j.field(1), j.field(3)));
    }
}
