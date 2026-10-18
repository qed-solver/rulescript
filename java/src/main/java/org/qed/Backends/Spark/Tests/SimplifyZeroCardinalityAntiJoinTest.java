package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyZeroCardinalityAntiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyZeroCardinalityAntiJoin.json"));

    private static LocalRelation oneRowZero(String name) {
        var attr = Support.attribute(name, false);
        var row = new GenericInternalRow(new Object[]{0});
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(attr)), Catalyst.<InternalRow>seq(List.of(row)), false, Match.noStream());
    }

    private static LocalRelation emptyRelation(String name) {
        var attr = Support.attribute(name, false);
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(attr)), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    private static Join join(LogicalPlan left, LogicalPlan right, org.apache.spark.sql.catalyst.plans.JoinType type, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void antiJoinOnTrueAgainstOneRowRelationBecomesEmpty() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = oneRowZero("R");
        var plan = join(left, right, LeftAnti$.MODULE$, Literal$.MODULE$.apply(true));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation lr && lr.data().isEmpty());
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesSemiJoinOnTrueAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = oneRowZero("R");
        var plan = join(left, right, LeftSemi$.MODULE$, Literal$.MODULE$.apply(true));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinOnTrueAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(4));
        var right = oneRowZero("R");
        var plan = join(left, right, Inner$.MODULE$, Literal$.MODULE$.apply(true));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAntiJoinWithConditionAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(5));
        var right = oneRowZero("R");
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = join(left, right, LeftAnti$.MODULE$, new GreaterThan(x, Literal$.MODULE$.apply(2)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAntiJoinAgainstPossiblyEmptyRightAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(6));
        var right = emptyRelation("R");
        var plan = join(left, right, LeftAnti$.MODULE$, Literal$.MODULE$.apply(true));
        assertEquals(plan, rule.apply(plan));
    }
}
