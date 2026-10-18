package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateJoinUnderProjectRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateJoinUnderProjectRight.json"));

    private static LocalRelation table(List<Integer> values) {
        var key = Support.attribute("k", false);
        var rows = new ArrayList<InternalRow>();
        for (var value : values) {
            rows.add(new GenericInternalRow(new Object[] {value}));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(key)), Catalyst.<InternalRow>seq(rows), false,
                Match.noStream());
    }

    private static LogicalPlan uniqueKeyed() {
        var base = table(List.of(0, 1, 2, 3));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)), Catalyst.<NamedExpression>seq(List.of(key)), base,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static Expression keyEquals(LogicalPlan left, LogicalPlan right) {
        return new EqualTo(Catalyst.<Attribute>list(left.output()).get(0),
                Catalyst.<Attribute>list(right.output()).get(0));
    }

    @Test
    void dropsSelfJoinOnUniqueKeyAndKeepsRightColumn() {
        var left = uniqueKeyed();
        var right = uniqueKeyed();
        var rightKey = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Catalyst.<Expression>some(keyEquals(left, right)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(rightKey)), join);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        assertTrue(!(((Project) rewritten).child() instanceof Join));
        assertEquals(List.of(rightKey.exprId()),
                Catalyst.<Attribute>list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsLeftOuterJoinEvenOnUniqueKey() {
        var left = uniqueKeyed();
        var right = uniqueKeyed();
        var rightKey = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(keyEquals(left, right)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(rightKey)), join);

        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsSelfJoinOnNonUniqueKey() {
        var left = table(List.of(1, 1, 2));
        var right = table(List.of(1, 1, 2));
        var rightKey = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Catalyst.<Expression>some(keyEquals(left, right)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(rightKey)), join);

        assertEquals(plan, rule.apply(plan));
    }
}
