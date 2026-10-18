package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Count;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EliminateAggDistinctTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateAggDistinct.json"));

    private static LocalRelation column(Attribute attr, Object... values) {
        var rows = new ArrayList<InternalRow>();
        for (var value : values) {
            rows.add(new GenericInternalRow(new Object[] {value}));
        }
        return new LocalRelation(Catalyst.seq(List.of(attr)), Catalyst.seq(rows), false, Match.noStream());
    }

    private static LogicalPlan keyed(Attribute key, Object... values) {
        return new Aggregate(Catalyst.seq(List.<Expression>of(key)), Catalyst.<NamedExpression>seq(List.of(key)),
                column(key, values), Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static LogicalPlan countPlan(LogicalPlan base, Attribute key, boolean distinct) {
        var count = AggregateExpression$.MODULE$.apply(new Count(Catalyst.<Expression>seq(List.of(key))),
                Complete$.MODULE$, distinct, Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var item = Alias$.MODULE$.apply((Expression) count, "c", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        return new Aggregate(Catalyst.seq(List.<Expression>of(key)), Catalyst.<NamedExpression>seq(List.of(key, item)), base,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void dropsDistinctOverUniqueNonNullKey() {
        var key = Support.attribute("k", false);
        var base = keyed(key, 0, 1, 2, 3);
        var plan = countPlan(base, key, true);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var aggregate = (Aggregate) (rewritten instanceof Project p ? p.child() : rewritten);
        assertEquals(base, aggregate.child());
        assertEquals(plan.output().size(), rewritten.output().size());
        var item = (Alias) Catalyst.list(aggregate.aggregateExpressions()).get(1);
        var agg = (AggregateExpression) item.child();
        assertFalse(agg.isDistinct());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNullableKeyAlone() {
        var key = Support.attribute("k", true);
        var plan = countPlan(keyed(key, 0, 1, null), key, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonUniqueKeyAlone() {
        var key = Support.attribute("k", false);
        var plan = countPlan(column(key, 0, 0, 1), key, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAlreadyPlainAggregateAlone() {
        var key = Support.attribute("k", false);
        var plan = countPlan(keyed(key, 0, 1, 2), key, false);
        assertEquals(plan, rule.apply(plan));
    }
}
