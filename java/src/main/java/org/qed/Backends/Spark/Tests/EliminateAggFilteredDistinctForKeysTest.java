package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Count;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Distinct;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateAggFilteredDistinctForKeysTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateAggFilteredDistinctForKeys.json"));

    private static Aggregate aggregate(Attribute key, boolean distinct, LogicalPlan child) {
        var function = new Count(Catalyst.<Expression>seq(List.of(key)));
        var expression = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, distinct,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var alias = Alias$.MODULE$.apply(expression, "c", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        var groups = Catalyst.<Expression>seq(List.of(key));
        var items = Catalyst.<NamedExpression>seq(List.of(key, alias));
        return new Aggregate(groups, items, child, Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void removesDistinctOverUniqueKey() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregate(key, true, new Distinct(base));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var result = (Aggregate) rewritten;
        assertEquals(plan.child(), result.child());
        var alias = (Alias) Catalyst.<NamedExpression>list(result.aggregateExpressions()).get(1);
        assertTrue(alias.child() instanceof AggregateExpression ae && !ae.isDistinct());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesPlainAggregateOverUniqueKeyAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregate(key, false, new Distinct(base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullableKeyAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregate(key, true, new Distinct(base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoColumnInputAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(3));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregate(key, true, new Distinct(base));
        assertEquals(plan, rule.apply(plan));
    }
}
