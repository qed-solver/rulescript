package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Sum;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoveRepetitionFromGroupExpressionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RemoveRepetitionFromGroupExpressions.json"));

    private static NamedExpression sumOf(Expression value, String name) {
        var function = new Sum(value);
        var aggregate = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return Alias$.MODULE$.apply(aggregate, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void removesTheRepeatedGroupKey() {
        var base = Support.relation("s", 3, List.of(), new Random(3));
        var cols = Catalyst.<Attribute>list(base.output());
        var a = cols.get(0);
        var b = cols.get(1);
        var y = cols.get(2);
        var inner = new Project(Catalyst.seq(List.<NamedExpression>of(a, b, a, y)), base);
        var innerCols = Catalyst.<Attribute>list(inner.output());
        var aggregate = new Aggregate(Catalyst.seq(List.<Expression>of(innerCols.get(0), innerCols.get(1), innerCols.get(2))),
                Catalyst.seq(List.<NamedExpression>of(innerCols.get(0), innerCols.get(1), innerCols.get(2),
                        sumOf(innerCols.get(3), "total"))),
                inner, Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
        var aggCols = Catalyst.<Attribute>list(aggregate.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(aggCols.get(0), aggCols.get(1), aggCols.get(3))), aggregate);

        LogicalPlan rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var result = (Aggregate) rewritten;
        assertEquals(2, Catalyst.list(result.groupingExpressions()).size());
        assertEquals(3, Catalyst.list(result.aggregateExpressions()).size());
        assertTrue(result.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAGroupKeyThatIsNotTheRepeatedOne() {
        var base = Support.relation("s", 3, List.of(), new Random(3));
        var cols = Catalyst.<Attribute>list(base.output());
        var a = cols.get(0);
        var b = cols.get(1);
        var y = cols.get(2);
        var inner = new Project(Catalyst.seq(List.<NamedExpression>of(a, b, a, y)), base);
        var innerCols = Catalyst.<Attribute>list(inner.output());
        var aggregate = new Aggregate(Catalyst.seq(List.<Expression>of(innerCols.get(0), innerCols.get(1), innerCols.get(3))),
                Catalyst.seq(List.<NamedExpression>of(innerCols.get(0), innerCols.get(1), innerCols.get(3),
                        sumOf(innerCols.get(2), "total"))),
                inner, Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
        var aggCols = Catalyst.<Attribute>list(aggregate.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(aggCols.get(0), aggCols.get(1), aggCols.get(3))), aggregate);
        assertEquals(plan, rule.apply(plan));
    }
}
