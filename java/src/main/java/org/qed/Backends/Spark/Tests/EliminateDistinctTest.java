package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateDistinctTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateDistinct.json"));

    private static LocalRelation build(String name, int[][] values) {
        var attrs = new ArrayList<Attribute>();
        for (int i = 0; i < values[0].length; i++) {
            attrs.add(Support.attribute(name + "_" + i, false));
        }
        var rows = new ArrayList<InternalRow>();
        for (int[] row : values) {
            var cells = new Object[row.length];
            for (int i = 0; i < row.length; i++) {
                cells[i] = row[i];
            }
            rows.add(new GenericInternalRow(cells));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.seq(rows), false, Match.noStream());
    }

    private static Aggregate groupBy(Attribute key, org.apache.spark.sql.catalyst.plans.logical.LogicalPlan child) {
        return new Aggregate(Catalyst.seq(List.of(key)), Catalyst.seq(List.of(key)), child,
                scala.Option.<AggregateHint>empty());
    }

    @Test
    void dropsDistinctOverUniqueColumn() {
        var base = build("t", new int[][] {{0, 1}, {1, 2}, {2, 3}});
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var unique = groupBy(key, base);
        var plan = groupBy(key, unique);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        assertEquals(new Project(Catalyst.<NamedExpression>seq(List.of(key)), unique), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsDistinctOnNonKeyColumn() {
        var base = build("t", new int[][] {{0, 1}, {1, 2}, {2, 3}});
        var other = Catalyst.<Attribute>list(base.output()).get(1);
        var plan = groupBy(other, base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsDistinctWhenKeyIsNotUniqueInInput() {
        var base = build("t", new int[][] {{0, 1}, {1, 2}, {2, 3}});
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var other = Catalyst.<Attribute>list(base.output()).get(1);
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(other, key)), base);
        var plan = groupBy(other, projected);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsDistinctWhenInputIsFiltered() {
        var base = build("t", new int[][] {{0, 1}, {1, 2}, {2, 3}});
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var filtered = new Filter(new GreaterThan(key, Literal$.MODULE$.apply(0)), base);
        var plan = groupBy(key, filtered);
        assertEquals(plan, rule.apply(plan));
    }
}
