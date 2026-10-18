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
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkEliminateDistinctTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Path.of(System.getProperty("rulescript.generated", "Generated"))
            .resolve("SparkEliminateDistinct.json"));

    private static LocalRelation table(boolean keyNullable, int[][] rows) {
        var key = Support.attribute("u", keyNullable);
        var value = Support.attribute("g", true);
        var data = new ArrayList<InternalRow>();
        for (var row : rows) {
            data.add(new GenericInternalRow(new Object[] {row[0], row[1]}));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(key, value)), Catalyst.<InternalRow>seq(data), false,
                Match.noStream());
    }

    private static Alias aliased(Expression expression, String name) {
        return Alias$.MODULE$.apply(expression, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Aggregate keysOnly(LogicalPlan child, Attribute key) {
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)), Catalyst.<NamedExpression>seq(List.of(key)), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static Aggregate groupMax(LogicalPlan child, Attribute key, Attribute value) {
        var max = AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false, Option.<Expression>empty(),
                NamedExpression$.MODULE$.newExprId());
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)),
                Catalyst.<NamedExpression>seq(List.of(key, aliased((Expression) max, "m"))), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static Aggregate countBy(LogicalPlan child, Attribute group, Attribute arg, boolean distinct) {
        var count = new Count(Catalyst.<Expression>seq(List.of(arg)));
        var expression = AggregateExpression$.MODULE$.apply(count, Complete$.MODULE$, distinct, Option.<Expression>empty(),
                NamedExpression$.MODULE$.newExprId());
        return new Aggregate(Catalyst.<Expression>seq(List.of(group)),
                Catalyst.<NamedExpression>seq(List.of(group, aliased((Expression) expression, "c"))), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void distinctCountOverUniqueKeyBecomesPlainCount() {
        var base = table(false, new int[][] {{0, 0}, {1, 0}, {2, 1}, {3, 1}});
        var child = keysOnly(base, Catalyst.<Attribute>list(base.output()).get(0));
        var key = Catalyst.<Attribute>list(child.output()).get(0);
        var plan = countBy(child, key, key, true);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var aggregate = (Aggregate) rewritten;
        var items = Catalyst.<NamedExpression>list(aggregate.aggregateExpressions());
        assertEquals(2, items.size());
        assertTrue(items.get(1) instanceof Alias alias && alias.child() instanceof AggregateExpression ae
                && !ae.isDistinct() && ae.aggregateFunction() instanceof Count count
                && Catalyst.<Expression>list(count.children()).equals(List.of(key)));
        assertEquals(List.of(key), Catalyst.<Expression>list(aggregate.groupingExpressions()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void nullableKeyIsLeftAlone() {
        var base = table(true, new int[][] {{0, 0}, {1, 0}, {2, 1}, {3, 1}});
        var child = keysOnly(base, Catalyst.<Attribute>list(base.output()).get(0));
        var key = Catalyst.<Attribute>list(child.output()).get(0);
        var plan = countBy(child, key, key, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void duplicatedKeyIsLeftAlone() {
        var base = table(false, new int[][] {{0, 0}, {0, 0}, {2, 1}, {3, 1}});
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = countBy(base, key, key, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void distinctOnNonKeyColumnIsLeftAlone() {
        var base = table(false, new int[][] {{0, 0}, {1, 0}, {2, 1}, {3, 1}});
        var child = groupMax(base, Catalyst.<Attribute>list(base.output()).get(0), Catalyst.<Attribute>list(base.output()).get(1));
        var key = Catalyst.<Attribute>list(child.output()).get(0);
        var value = Catalyst.<Attribute>list(child.output()).get(1);
        var plan = countBy(child, key, value, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void plainCountOverUniqueKeyIsLeftAlone() {
        var base = table(false, new int[][] {{0, 0}, {1, 0}, {2, 1}, {3, 1}});
        var child = keysOnly(base, Catalyst.<Attribute>list(base.output()).get(0));
        var key = Catalyst.<Attribute>list(child.output()).get(0);
        var plan = countBy(child, key, key, false);
        assertEquals(plan, rule.apply(plan));
    }
}
