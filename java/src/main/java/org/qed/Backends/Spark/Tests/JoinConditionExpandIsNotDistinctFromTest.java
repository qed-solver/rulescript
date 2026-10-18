package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinConditionExpandIsNotDistinctFromTest {
    private final RuleScriptRule rule =
            RuleScriptRule.load(Support.generated().resolve("JoinConditionExpandIsNotDistinctFrom.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, JoinType type, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void expandsNullSafeEqualityOnSingleColumnInnerJoin() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(left, right, Inner$.MODULE$, new EqualNullSafe(l, r));
        var expectedCond = new Or(
                new And(new IsNull(l), new IsNull(r)),
                new EqualNullSafe(new EqualTo(l, r), Literal$.MODULE$.apply(true)));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join);
        var join = (Join) rewritten;
        assertEquals(Inner$.MODULE$, join.joinType());
        assertEquals(Option.<Expression>apply(expectedCond), join.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesPlainEqualityAloneOnInnerJoin() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(left, right, Inner$.MODULE$, new EqualTo(l, r));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullSafeEqualityAloneOnLeftOuterJoin() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(left, right, LeftOuter$.MODULE$, new EqualNullSafe(l, r));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoColumnLeftSideAloneOnInnerJoin() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(left, right, Inner$.MODULE$, new EqualNullSafe(l, r));
        assertEquals(plan, rule.apply(plan));
    }
}
