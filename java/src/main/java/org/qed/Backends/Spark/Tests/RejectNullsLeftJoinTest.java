package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.RightOuter$;
import org.apache.spark.sql.catalyst.plans.JoinType;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RejectNullsLeftJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RejectNullsLeftJoin.json"));

    private static LogicalPlan filteredJoin(JoinType type, boolean isNotNull, boolean onRightColumn, long seed) {
        var left = Support.relation("l", 1, List.of(), new Random(seed));
        var right = Support.relation("r", 1, List.of(), new Random(seed + 1));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var on = new EqualTo(l0, r0);
        var join = new Join(left, right, type, Option.<Expression>apply(on), JoinHint$.MODULE$.NONE());
        var target = onRightColumn ? r0 : l0;
        Expression condition = isNotNull ? new IsNotNull(target) : new IsNull(target);
        return new Filter(condition, join);
    }

    @Test
    void leftJoinUnderNotNullFilterBecomesInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var on = new EqualTo(l0, r0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(on), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new IsNotNull(r0), join);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter);
        var filter = (Filter) rewritten;
        assertEquals(new IsNotNull(r0), filter.condition());
        assertTrue(filter.child() instanceof Join);
        var inner = (Join) filter.child();
        assertEquals(Inner$.MODULE$, inner.joinType());
        assertSame(left, inner.left());
        assertSame(right, inner.right());
        assertEquals(Option.<Expression>apply(on), inner.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void innerJoinUnderNotNullFilterIsLeftAlone() {
        var plan = filteredJoin(Inner$.MODULE$, true, true, 3);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void notNullFilterOnLeftColumnIsLeftAlone() {
        var plan = filteredJoin(LeftOuter$.MODULE$, true, false, 5);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void isNullFilterOnRightColumnIsLeftAlone() {
        var plan = filteredJoin(LeftOuter$.MODULE$, false, true, 7);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void rightOuterJoinUnderNotNullFilterIsLeftAlone() {
        var plan = filteredJoin(RightOuter$.MODULE$, true, true, 9);
        assertEquals(plan, rule.apply(plan));
    }
}
