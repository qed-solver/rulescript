package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
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

class SimplifyJoinNotNullEqualityTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyJoinNotNullEquality.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, org.apache.spark.sql.catalyst.plans.JoinType type, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void dropsNotFalseOnNonNullInnerJoinEquality() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var equal = new EqualTo(l0, r0);
        var rest = new LessThan(l0, r0);
        var plan = join(left, right, Inner$.MODULE$, new And(new Not(new EqualNullSafe(equal, Literal$.MODULE$.apply(false))), rest));
        var expected = join(left, right, Inner$.MODULE$, new And(equal, rest));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var equal = new EqualTo(l0, r0);
        var plan = join(left, right, LeftOuter$.MODULE$, new Not(new EqualNullSafe(equal, Literal$.MODULE$.apply(false))));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullableColumnAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var equal = new EqualTo(l0, r0);
        var plan = join(left, right, Inner$.MODULE$, new Not(new EqualNullSafe(equal, Literal$.MODULE$.apply(false))));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsFalseAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var equal = new EqualTo(l0, r0);
        var plan = join(left, right, Inner$.MODULE$, new EqualNullSafe(equal, Literal$.MODULE$.apply(false)));
        assertEquals(plan, rule.apply(plan));
    }
}
