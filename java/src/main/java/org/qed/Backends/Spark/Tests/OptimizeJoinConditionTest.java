package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
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

class OptimizeJoinConditionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("OptimizeJoinCondition.json"));

    private static LogicalPlan join(LogicalPlan left, LogicalPlan right, JoinType type, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void rewritesNullSafeEqualityOnSingleColumns() {
        for (int seed = 1; seed <= 20; seed++) {
            var left = Support.relation("L", 1, List.of(), new Random(seed));
            var right = Support.relation("R", 1, List.of(), new Random(seed + 100));
            var x = Catalyst.<Attribute>list(left.output()).get(0);
            var y = Catalyst.<Attribute>list(right.output()).get(0);
            var plan = join(left, right, Inner$.MODULE$,
                    new Or(new EqualTo(x, y), new And(new IsNull(x), new IsNull(y))));
            var expected = join(left, right, Inner$.MODULE$, new EqualNullSafe(x, y));
            var rewritten = rule.apply(plan);
            assertNotEquals(plan, rewritten);
            assertEquals(expected, rewritten);
            Assumptions.assumeTrue(Support.canExecute());
            assertEquals(Support.run(plan), Support.run(rewritten));
        }
    }

    @Test
    void leavesEqualityWithoutNullBranchAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(left, right, Inner$.MODULE$, new EqualTo(x, y));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(left, right, LeftOuter$.MODULE$,
                new Or(new EqualTo(x, y), new And(new IsNull(x), new IsNull(y))));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoColumnLeftInputAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(left, right, Inner$.MODULE$,
                new Or(new EqualTo(x, y), new And(new IsNull(x), new IsNull(y))));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesWrongNullBranchAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(left, right, Inner$.MODULE$,
                new Or(new EqualTo(x, y), new And(new IsNull(x), new IsNotNull(y))));
        assertEquals(plan, rule.apply(plan));
    }
}
