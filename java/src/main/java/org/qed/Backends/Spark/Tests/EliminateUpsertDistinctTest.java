package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateUpsertDistinctTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateUpsertDistinct.json"));

    private static LocalRelation uniqueRelation() {
        var x = Support.attribute("s_0", true);
        var rows = new ArrayList<InternalRow>();
        for (Object v : new Object[] {1, 2, 3, null}) {
            rows.add(new GenericInternalRow(new Object[] {v}));
        }
        return new LocalRelation(Catalyst.seq(List.of(x)), Catalyst.seq(rows), false, Match.noStream());
    }

    @Test
    void dropsDistinctOverUniqueNullableColumn() {
        var base = uniqueRelation();
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var distinct = new Aggregate(Catalyst.<Expression>seq(List.of(x)), Catalyst.<NamedExpression>seq(List.of(x)), base,
                scala.Option.empty());
        var y = Catalyst.<Attribute>list(distinct.output()).get(0);
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(y)), Catalyst.<NamedExpression>seq(List.of(y)), distinct,
                scala.Option.empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesDistinctOverTwoColumnsAlone() {
        var base = Support.relation("s", 2, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(x)), Catalyst.<NamedExpression>seq(List.of(x)), base,
                scala.Option.empty());
        assertEquals(plan, rule.apply(plan));
    }
}
