package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
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

class RemoveEmptySingleTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RemoveEmptySingle.json"));

    private static LocalRelation emptyOf(List<Attribute> output) {
        return new LocalRelation(Catalyst.seq(output), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    private static NamedExpression maxOf(Attribute value) {
        var max = AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return Alias$.MODULE$.apply((Expression) max, "m", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void collapsesGroupedAggregateOverEmptyInput() {
        var key = Support.attribute("k", true);
        var plan = new Aggregate(Catalyst.seq(List.<Expression>of(key)),
                Catalyst.seq(List.<NamedExpression>of(key, maxOf(key))), emptyOf(List.of(key)),
                Option.<AggregateHint>empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation);
        var empty = (LocalRelation) rewritten;
        assertTrue(empty.data().isEmpty());
        assertEquals(plan.output(), empty.output());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesGrandTotalAggregateOverEmptyInputAlone() {
        var key = Support.attribute("k", true);
        var plan = new Aggregate(Catalyst.seq(List.<Expression>of()),
                Catalyst.seq(List.<NamedExpression>of(maxOf(key))), emptyOf(List.of(key)),
                Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesGroupedAggregateOverNonEmptyInputAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Aggregate(Catalyst.seq(List.<Expression>of(key)),
                Catalyst.seq(List.<NamedExpression>of(key, maxOf(key))), base, Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }
}
