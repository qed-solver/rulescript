package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThanOrEqual;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.LessThanOrEqual;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommuteVarInequalityTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CommuteVarInequality.json"));

    private static Join innerJoin(String type) {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        return new Join(left, right, Match.joinType(type), Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
    }

    private static void assertSameRows(LogicalPlan plan, LogicalPlan rewritten) {
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void rewritesLessThanOrEqualToSwappedGreaterThanOrEqual() {
        var join = innerJoin("inner");
        var columns = Catalyst.<Attribute>list(join.output());
        var x = columns.get(0);
        var y = columns.get(1);
        var plan = new Filter(new LessThanOrEqual(x, y), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter);
        var filter = (Filter) rewritten;
        assertEquals(new GreaterThanOrEqual(y, x), filter.condition());
        assertEquals(join, filter.child());
        assertSameRows(plan, rewritten);
    }

    @Test
    void leavesLessThanOrEqualOfTheSameColumnAlone() {
        var join = innerJoin("inner");
        var x = Catalyst.<Attribute>list(join.output()).get(0);
        var plan = new Filter(new LessThanOrEqual(x, x), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesStrictLessThanAlone() {
        var join = innerJoin("inner");
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new LessThan(columns.get(0), columns.get(1)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var join = innerJoin("left");
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new LessThanOrEqual(columns.get(0), columns.get(1)), join);
        assertEquals(plan, rule.apply(plan));
    }
}
