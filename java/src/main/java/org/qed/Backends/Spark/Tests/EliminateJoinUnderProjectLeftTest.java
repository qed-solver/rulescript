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
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateJoinUnderProjectLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateJoinUnderProjectLeft.json"));

    private static LogicalPlan keyed(LocalRelation base) {
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)), Catalyst.<NamedExpression>seq(List.of(key)), base,
                Option.empty());
    }

    private static Join selfJoin(LogicalPlan left, LogicalPlan right, org.apache.spark.sql.catalyst.plans.JoinType type) {
        var leftKey = Catalyst.<Attribute>list(left.output()).get(0);
        var rightKey = Catalyst.<Attribute>list(right.output()).get(0);
        return new Join(left, right, type, Option.<Expression>apply(new EqualTo(leftKey, rightKey)),
                JoinHint$.MODULE$.NONE());
    }

    private static void assertSameRows(LogicalPlan plan, LogicalPlan rewritten) {
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void removesLeftSelfJoinOnUniqueNonNullKey() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var left = keyed(base);
        var right = keyed((LocalRelation) base.newInstance());
        var join = selfJoin(left, right, LeftOuter$.MODULE$);
        var leftKey = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(leftKey)), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() == left);
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        assertSameRows(plan, rewritten);
    }

    @Test
    void keepsInnerSelfJoinOnSameKey() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var left = keyed(base);
        var right = keyed((LocalRelation) base.newInstance());
        var join = selfJoin(left, right, Inner$.MODULE$);
        var leftKey = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(leftKey)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsLeftJoinWhenKeyIsNullable() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var left = keyed(base);
        var right = keyed((LocalRelation) base.newInstance());
        var join = selfJoin(left, right, LeftOuter$.MODULE$);
        var leftKey = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(leftKey)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsLeftJoinWhenProjectUsesRightColumn() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var left = keyed(base);
        var right = keyed((LocalRelation) base.newInstance());
        var join = selfJoin(left, right, LeftOuter$.MODULE$);
        var rightKey = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(rightKey)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsLeftJoinWhenScanIsNotUnique() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var left = base;
        var right = (LocalRelation) base.newInstance();
        var join = selfJoin(left, right, LeftOuter$.MODULE$);
        var leftKey = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(leftKey)), join);
        assertEquals(plan, rule.apply(plan));
    }
}
