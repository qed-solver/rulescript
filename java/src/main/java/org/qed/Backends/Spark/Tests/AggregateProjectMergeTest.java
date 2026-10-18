package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
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
import static org.junit.jupiter.api.Assertions.assertSame;

class AggregateProjectMergeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateProjectMerge.json"));

    private static Aggregate maxBy(Attribute group, LogicalPlan child) {
        var max = AggregateExpression$.MODULE$.apply(new Max(group), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var alias = Alias$.MODULE$.apply(max, "m", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        return new Aggregate(Catalyst.<Expression>seq(List.<Expression>of(group)),
                Catalyst.<NamedExpression>seq(List.<NamedExpression>of(group, alias)), child,
                Option.<AggregateHint>empty());
    }

    @Test
    void aggregatesDirectlyOverTheJoinWhenColumnsAreSwapped() {
        var left = Support.relation("A", 1, List.of(), new Random(1));
        var right = Support.relation("B", 1, List.of(), new Random(2));
        var a = Catalyst.<Attribute>list(left.output()).get(0);
        var b = Catalyst.<Attribute>list(right.output()).get(0);
        var joined = new Join(left, right, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var swapped = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(b, a)), joined);
        var plan = maxBy(b, swapped);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var aggregate = assertInstanceOf(Aggregate.class, rewritten);
        assertEquals(List.of(b), Catalyst.list(aggregate.groupingExpressions()));
        assertEquals(2, aggregate.aggregateExpressions().size());
        assertEquals(b, Catalyst.list(aggregate.aggregateExpressions()).get(0));
        var child = assertInstanceOf(Join.class, aggregate.child());
        assertEquals(Catalyst.list(joined.output()), Catalyst.list(child.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("A", 1, List.of(), new Random(1));
        var right = Support.relation("B", 1, List.of(), new Random(2));
        var a = Catalyst.<Attribute>list(left.output()).get(0);
        var b = Catalyst.<Attribute>list(right.output()).get(0);
        var joined = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var swapped = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(b, a)), joined);
        var plan = maxBy(b, swapped);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnIdentityProjectionAlone() {
        var left = Support.relation("A", 1, List.of(), new Random(1));
        var right = Support.relation("B", 1, List.of(), new Random(2));
        var a = Catalyst.<Attribute>list(left.output()).get(0);
        var b = Catalyst.<Attribute>list(right.output()).get(0);
        var joined = new Join(left, right, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var identity = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(a, b)), joined);
        var plan = maxBy(a, identity);
        assertEquals(plan, rule.apply(plan));
    }
}
