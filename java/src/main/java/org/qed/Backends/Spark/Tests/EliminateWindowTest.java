package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateWindowTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateWindow.json"));

    private static LogicalPlan joinBack(LocalRelation base, JoinType type, int condOrdinal) {
        var attrs = Catalyst.<Attribute>list(base.output());
        var key = attrs.get(0);
        var alias = Alias$.MODULE$.apply((Expression) key, "k", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        var distinctKeys = new Aggregate(Catalyst.<Expression>seq(List.of(key)),
                Catalyst.<NamedExpression>seq(List.of(alias)), base,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
        var partKey = Catalyst.<Attribute>list(distinctKeys.output()).get(0);
        var cond = new EqualTo(attrs.get(condOrdinal), partKey);
        var join = new Join(base, distinctKeys, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        return new Project(Catalyst.<NamedExpression>seq(List.of(attrs.get(0), attrs.get(1))), join);
    }

    @Test
    void eliminatesInnerJoinBackOnPartitionKey() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var plan = joinBack(base, Inner$.MODULE$, 0);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation);
        assertEquals(base, rewritten);
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void eliminatesInnerJoinBackOnPartitionKeyWithDifferentData() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(7));
        var plan = joinBack(base, Inner$.MODULE$, 0);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(base, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinBackAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var plan = joinBack(base, LeftOuter$.MODULE$, 0);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinBackWithNullablePartitionKeyAlone() {
        var base = Support.relation("t", 2, List.of(1), new Random(1));
        var plan = joinBack(base, Inner$.MODULE$, 0);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinBackOnNonKeyColumnAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var plan = joinBack(base, Inner$.MODULE$, 1);
        assertEquals(plan, rule.apply(plan));
    }
}
