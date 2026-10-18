package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EliminateJoinUnderGroupByRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("EliminateJoinUnderGroupByRight.json"));

    private static Aggregate groupBy(Attribute key, LogicalPlan child) {
        return new Aggregate(Catalyst.seq(List.<Expression>of(key)),
                Catalyst.seq(List.<NamedExpression>of(key)), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static Join selfJoin(LocalRelation left, LocalRelation right, JoinType type) {
        var leftKey = Catalyst.<Attribute>list(left.output()).get(0);
        var rightKey = Catalyst.<Attribute>list(right.output()).get(0);
        return new Join(left, right, type, Option.<Expression>apply(new EqualTo(leftKey, rightKey)),
                JoinHint$.MODULE$.NONE());
    }

    @Test
    void removesInnerSelfJoinUnderGroupByRightKey() {
        var base = Support.relation("s", 1, List.of(0), new Random(1));
        var other = (LocalRelation) base.newInstance();
        var rightKey = Catalyst.<Attribute>list(other.output()).get(0);
        var leftKey = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = groupBy(rightKey, selfJoin(base, other, Inner$.MODULE$));

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var restored = assertInstanceOf(Project.class, rewritten);
        var aggregate = assertInstanceOf(Aggregate.class, restored.child());
        assertInstanceOf(LocalRelation.class, aggregate.child());
        assertEquals(leftKey.exprId(), Catalyst.list(aggregate.child().output()).get(0).exprId());
        assertEquals(List.of(rightKey.exprId()),
                Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList());
        assertEquals(List.of(rightKey.exprId()),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsLeftOuterSelfJoinUnderGroupByRightKey() {
        var base = Support.relation("s", 1, List.of(0), new Random(2));
        var other = (LocalRelation) base.newInstance();
        var rightKey = Catalyst.<Attribute>list(other.output()).get(0);
        var plan = groupBy(rightKey, selfJoin(base, other, LeftOuter$.MODULE$));

        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsSelfJoinWhenKeyIsNullable() {
        var base = Support.relation("s", 1, List.of(), new Random(3));
        var other = (LocalRelation) base.newInstance();
        var rightKey = Catalyst.<Attribute>list(other.output()).get(0);
        var plan = groupBy(rightKey, selfJoin(base, other, Inner$.MODULE$));

        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsSelfJoinWhenGroupedOnLeftKey() {
        var base = Support.relation("s", 1, List.of(0), new Random(4));
        var other = (LocalRelation) base.newInstance();
        var leftKey = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = groupBy(leftKey, selfJoin(base, other, Inner$.MODULE$));

        assertEquals(plan, rule.apply(plan));
    }
}
