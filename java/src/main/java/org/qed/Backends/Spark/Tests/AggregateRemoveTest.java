package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AggregateRemoveTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateRemove.json"));

    private static LocalRelation table(List<Attribute> columns, Object[]... rows) {
        var data = new ArrayList<InternalRow>();
        for (var row : rows) {
            data.add(new GenericInternalRow(row));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(columns), Catalyst.<InternalRow>seq(data), false, Match.noStream());
    }

    private static Aggregate group(List<Attribute> keys, org.apache.spark.sql.catalyst.plans.logical.LogicalPlan child) {
        return new Aggregate(Catalyst.<Expression>seq(keys), Catalyst.<NamedExpression>seq(keys), child, scala.Option.empty());
    }

    @Test
    void removesGroupingOnUniqueNonNullKey() {
        var k = Support.attribute("k", false);
        var base = table(List.of(k), new Object[] {0}, new Object[] {1}, new Object[] {3});
        var keyed = group(List.of(k), base);
        var plan = group(List.of(k), keyed);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(LocalRelation.class, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesGroupingOnPartOfCompositeKeyAlone() {
        var k = Support.attribute("k", false);
        var v = Support.attribute("v", false);
        var base = table(List.of(k, v), new Object[] {0, 5}, new Object[] {0, 6}, new Object[] {1, 5});
        var keyed = group(List.of(k, v), base);
        var plan = group(List.of(k), keyed);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesGroupingOnTwoColumnInputAlone() {
        var k = Support.attribute("k", false);
        var v = Support.attribute("v", false);
        var base = table(List.of(k, v), new Object[] {0, 5}, new Object[] {1, 5});
        var plan = group(List.of(k), base);
        assertEquals(plan, rule.apply(plan));
    }
}
