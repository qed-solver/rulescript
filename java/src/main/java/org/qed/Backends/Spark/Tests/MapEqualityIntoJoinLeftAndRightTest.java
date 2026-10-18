package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapEqualityIntoJoinLeftAndRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("MapEqualityIntoJoinLeftAndRight.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, JoinType type, Expression condition) {
        return new Join(left, right, type, Catalyst.some(condition), JoinHint$.MODULE$.NONE());
    }

    @Test
    void remapsTheSecondEqualityOntoTheLeftColumn() {
        var a = Support.relation("A", 2, List.of(0, 1), new Random(1));
        var b = Support.relation("B", 1, List.of(0), new Random(2));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var ay = Catalyst.<Attribute>list(a.output()).get(1);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var plan = join(a, b, Inner$.MODULE$, new And(new EqualTo(ax, bx), new EqualTo(bx, ay)));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join);
        var join = (Join) rewritten;
        assertEquals(a, join.left());
        assertEquals(b, join.right());
        assertEquals(Inner$.MODULE$, join.joinType());
        assertEquals(new And(new EqualTo(bx, ay), new EqualTo(ax, ay)), join.condition().get());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOuterJoinAlone() {
        var a = Support.relation("A", 2, List.of(0, 1), new Random(1));
        var b = Support.relation("B", 1, List.of(0), new Random(2));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var ay = Catalyst.<Attribute>list(a.output()).get(1);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var plan = join(a, b, LeftOuter$.MODULE$, new And(new EqualTo(ax, bx), new EqualTo(bx, ay)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANullableRightColumnAlone() {
        var a = Support.relation("A", 2, List.of(0, 1), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var ay = Catalyst.<Attribute>list(a.output()).get(1);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var plan = join(a, b, Inner$.MODULE$, new And(new EqualTo(ax, bx), new EqualTo(bx, ay)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANonEqualityAlone() {
        var a = Support.relation("A", 2, List.of(0, 1), new Random(1));
        var b = Support.relation("B", 1, List.of(0), new Random(2));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var ay = Catalyst.<Attribute>list(a.output()).get(1);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var plan = join(a, b, Inner$.MODULE$, new And(new EqualTo(ax, bx), new GreaterThan(bx, ay)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAWiderRightInputAlone() {
        var a = Support.relation("A", 2, List.of(0, 1), new Random(1));
        var b = Support.relation("B", 2, List.of(0), new Random(2));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var ay = Catalyst.<Attribute>list(a.output()).get(1);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var plan = join(a, b, Inner$.MODULE$, new And(new EqualTo(ax, bx), new EqualTo(bx, ay)));
        assertEquals(plan, rule.apply(plan));
    }
}
