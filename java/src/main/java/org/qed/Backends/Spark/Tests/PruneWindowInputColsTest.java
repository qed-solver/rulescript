package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.ExprId;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.JoinType;
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

class PruneWindowInputColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneWindowInputCols.json"));

    private static NamedExpression named(Expression value, String name, ExprId id) {
        return Alias$.MODULE$.apply(value, name, id, Catalyst.<String>seq(List.of()), Option.empty(),
                Catalyst.<String>seq(List.of()));
    }

    private static Expression sumOf(Attribute a, Attribute b, Attribute c) {
        var ab = Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
        return Add$.MODULE$.apply(ab, c, Add$.MODULE$.apply$default$3());
    }

    private static Aggregate perPartitionMax(LocalRelation input) {
        var attrs = Catalyst.<Attribute>list(input.output());
        var pruned = new Project(Catalyst.<NamedExpression>seq(List.of(attrs.get(0), attrs.get(1))), input);
        var key = Catalyst.<Attribute>list(pruned.output()).get(0);
        var value = Catalyst.<Attribute>list(pruned.output()).get(1);
        var max = AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)),
                Catalyst.<NamedExpression>seq(List.of(key, named(max, "w", NamedExpression$.MODULE$.newExprId()))), pruned,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static LogicalPlan windowOver(LocalRelation input, JoinType type, boolean onPartition) {
        var perPartition = perPartitionMax((LocalRelation) input.newInstance());
        var inputCols = Catalyst.<Attribute>list(input.output());
        var partitionCols = Catalyst.<Attribute>list(perPartition.output());
        var joinColumn = onPartition ? inputCols.get(0) : inputCols.get(1);
        var join = new Join(input, perPartition, type,
                Option.<Expression>apply(new EqualTo(joinColumn, partitionCols.get(0))), JoinHint$.MODULE$.NONE());
        var joined = Catalyst.<Attribute>list(join.output());
        var top = named(sumOf(joined.get(0), joined.get(1), joined.get(4)), "top", NamedExpression$.MODULE$.newExprId());
        return new Project(Catalyst.<NamedExpression>seq(List.of(top)), join);
    }

    @Test
    void prunesPassthroughColumnFromWindowInput() {
        var input = Support.relation("Input", 3, List.of(0, 1, 2), new Random(1));
        var plan = windowOver(input, Inner$.MODULE$, true);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.child() instanceof Join join
                && join.left() instanceof Project pruned && pruned.child() == input);
        assertEquals(1, Catalyst.list(rewritten.output()).size());
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leftOuterWindowJoinIsNotRewritten() {
        var input = Support.relation("Input", 3, List.of(0, 1, 2), new Random(2));
        var plan = windowOver(input, LeftOuter$.MODULE$, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void joinOnNonPartitionColumnIsNotRewritten() {
        var input = Support.relation("Input", 3, List.of(0, 1, 2), new Random(3));
        var plan = windowOver(input, Inner$.MODULE$, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nullablePassthroughColumnIsNotRewritten() {
        var input = Support.relation("Input", 3, List.of(0, 1), new Random(4));
        var plan = windowOver(input, Inner$.MODULE$, true);
        assertEquals(plan, rule.apply(plan));
    }
}
