package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Sum;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.types.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateExtractProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateExtractProject.json"));

    @Test
    void extractsTheProjectionBelowTheAggregate() {
        var base = threeWayInnerJoin(new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = aggregate(base, cols.get(0), cols.get(1), false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var agg = (Aggregate) rewritten;
        assertEquals(cols.get(0), Catalyst.<Expression>list(agg.groupingExpressions()).get(0));
        assertTrue(agg.child() instanceof Project);
        var project = (Project) agg.child();
        var projected = Catalyst.<NamedExpression>list(project.projectList());
        assertEquals(2, projected.size());
        assertEquals(cols.get(0), projected.get(0));
        assertEquals(cols.get(1), projected.get(1));
        assertTrue(project.child() instanceof Join);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnAggregateOverALeftOuterJoinAlone() {
        var random = new Random(7);
        var a = Support.relation("a", 1, List.of(), random);
        var b = Support.relation("b", 1, List.of(), random);
        var c = Support.relation("c", 1, List.of(), random);
        var ab = new Join(a, b, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var base = new Join(ab, c, LeftOuter$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = aggregate(base, cols.get(0), cols.get(1), false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesADistinctAggregateAlone() {
        var base = threeWayInnerJoin(new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = aggregate(base, cols.get(0), cols.get(1), true);
        assertEquals(plan, rule.apply(plan));
    }

    private static LogicalPlan threeWayInnerJoin(Random random) {
        var a = Support.relation("a", 1, List.of(), random);
        var b = Support.relation("b", 1, List.of(), random);
        var c = Support.relation("c", 1, List.of(), random);
        var ab = new Join(a, b, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        return new Join(ab, c, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
    }

    private static Aggregate aggregate(LogicalPlan input, Attribute group, Attribute value, boolean distinct) {
        var sum = AggregateExpression$.MODULE$.apply(new Sum(value), Complete$.MODULE$, distinct,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var alias = Alias$.MODULE$.apply(sum, "f", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.<Metadata>empty(), Catalyst.<String>seq(List.of()));
        return new Aggregate(Catalyst.<Expression>seq(List.of(group)),
                Catalyst.<NamedExpression>seq(List.of(group, alias)), input, Option.<AggregateHint>empty());
    }
}
