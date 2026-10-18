package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LateralSubquery;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.OuterReference;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.HintInfo;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LateralJoin;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecorrelateJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("DecorrelateJoin.json"));

    private static LateralJoin lateral(LogicalPlan left, LogicalPlan right, Attribute outer, Expression cond,
                                       JoinType type) {
        var subquery = new LateralSubquery(new Filter(cond, right), Catalyst.<Expression>seq(List.of(outer)),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<Expression>seq(List.of()),
                Option.<HintInfo>empty());
        return new LateralJoin(left, subquery, type, Option.<Expression>empty());
    }

    @Test
    void rewritesInnerLateralJoinIntoJoinWithCondition() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var outer = Catalyst.<Attribute>list(left.output()).get(0);
        var local = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = lateral(left, right, outer, new GreaterThan(new OuterReference(outer), local), Inner$.MODULE$);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.left() == left && j.right() == right
                && j.joinType().equals(Inner$.MODULE$) && j.condition().isDefined()
                && j.condition().get().equals(new GreaterThan(outer, local)));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterLateralJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var outer = Catalyst.<Attribute>list(left.output()).get(0);
        var local = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = lateral(left, right, outer, new GreaterThan(new OuterReference(outer), local), LeftOuter$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLateralJoinWithTwoLeftColumnsAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var outer = Catalyst.<Attribute>list(left.output()).get(0);
        var local = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = lateral(left, right, outer, new GreaterThan(new OuterReference(outer), local), Inner$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesPlainInnerJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var outer = Catalyst.<Attribute>list(left.output()).get(0);
        var local = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new GreaterThan(outer, local)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
