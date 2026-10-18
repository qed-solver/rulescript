package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateJoinRemoveTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateJoinRemove.json"));

    private static Aggregate groupBy(Attribute key, LogicalPlan child) {
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)), Catalyst.<NamedExpression>seq(List.of(key)),
                child, Option.empty());
    }

    private static Join join(org.apache.spark.sql.catalyst.plans.JoinType type, LocalRelation left,
            LocalRelation right) {
        var leftKey = Catalyst.<Attribute>list(left.output()).get(0);
        var rightKey = Catalyst.<Attribute>list(right.output()).get(0);
        return new Join(left, right, type, Catalyst.<Expression>some(new EqualTo(leftKey, rightKey)),
                JoinHint$.MODULE$.NONE());
    }

    @Test
    void removesLeftJoinWhenGroupingOnLeftKey() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var key = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = groupBy(key, join(LeftOuter$.MODULE$, left, right));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate a && a.child() == left);
        var grouped = (Aggregate) rewritten;
        assertEquals(List.of(key), Catalyst.list(grouped.groupingExpressions()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesInnerJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var key = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = groupBy(key, join(Inner$.MODULE$, left, right));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesGroupingOnRightColumnAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var joined = join(LeftOuter$.MODULE$, left, right);
        var rightKey = Catalyst.<Attribute>list(joined.output()).get(1);
        var plan = groupBy(rightKey, joined);
        assertEquals(plan, rule.apply(plan));
    }
}
