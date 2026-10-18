package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UnionToValuesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("UnionToValues.json"));

    private static LocalRelation table(String prefix, int... cells) {
        var attrs = List.of(Support.attribute(prefix + "0", false), Support.attribute(prefix + "1", false));
        var rows = new ArrayList<InternalRow>();
        for (int i = 0; i < cells.length; i += 2) {
            rows.add(new GenericInternalRow(new Object[]{cells[i], cells[i + 1]}));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(attrs), Catalyst.<InternalRow>seq(rows), false, Match.noStream());
    }

    @Test
    void unionOfThreeConstantTablesBecomesOneTable() {
        var v1 = table("a", 1, 2, 3, 4);
        var v2 = table("b", 3, 4, 5, 6);
        var v3 = table("c", 7, 8);
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(v1, v2, v3)), false, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof LocalRelation lr && lr.data().size() == 5);
        assertEquals(Catalyst.list(plan.output()).size(), Catalyst.list(rewritten.output()).size());
        for (int i = 0; i < Catalyst.list(plan.output()).size(); i++) {
            assertEquals(Catalyst.list(plan.output()).get(i).exprId(), Catalyst.list(rewritten.output()).get(i).exprId());
        }
        assumeTrue(Support.canExecute());
        assertEquals(List.of("[1,2]", "[3,4]", "[3,4]", "[5,6]", "[7,8]"), Support.run(rewritten));
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void unionWithDifferentConstantIsLeftAlone() {
        var v1 = table("a", 1, 2, 3, 4);
        var v2 = table("b", 3, 4, 5, 6);
        var v3 = table("c", 7, 9);
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(v1, v2, v3)), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void unionOfTwoInputsIsLeftAlone() {
        var v1 = table("a", 1, 2, 3, 4);
        var v2 = table("b", 3, 4, 5, 6);
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(v1, v2)), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void distinctUnionIsLeftAlone() {
        var v1 = table("a", 1, 2, 3, 4);
        var v2 = table("b", 3, 4, 5, 6);
        var v3 = table("c", 7, 8);
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(v1, v2, v3)), false, false);
        var attrs = Catalyst.<Attribute>list(union.output());
        var plan = new Aggregate(Catalyst.<Expression>seq(attrs), Catalyst.<NamedExpression>seq(attrs), union,
                Option.<AggregateHint>empty());
        assertFalse(rule.matches(plan));
    }
}
