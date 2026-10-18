package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinishAnalysisTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FinishAnalysis.json"));

    private static AggregateExpression maxOf(Expression child, boolean distinct) {
        return AggregateExpression$.MODULE$.apply(new Max(child), Complete$.MODULE$, distinct,
                scala.Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
    }

    private static Alias named(Expression child, String name) {
        return Alias$.MODULE$.apply(child, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void pullsComplexGroupKeyBelowAggregate() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var key = Add$.MODULE$.apply(a, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
        var keyItem = named(key, "k");
        var plan = new Aggregate(Catalyst.seq(List.<Expression>of(key)),
                Catalyst.seq(List.<NamedExpression>of(keyItem, named(maxOf(b, false), "m"))),
                base, scala.Option.empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var top = rewritten instanceof Project p ? p.child() : rewritten;
        assertTrue(top instanceof Aggregate);
        var aggregate = (Aggregate) top;
        assertTrue(aggregate.child() instanceof Project);
        assertEquals(2, Catalyst.list(aggregate.child().output()).size());
        assertEquals(1, Catalyst.list(aggregate.groupingExpressions()).size());
        assertTrue(Catalyst.list(aggregate.groupingExpressions()).get(0) instanceof Attribute);
        assertEquals(2, Catalyst.list(aggregate.output()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void extractsAPlainColumnGroupKeyAsTheIdentityFunction() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Aggregate(Catalyst.seq(List.<Expression>of(attrs.get(0))),
                Catalyst.seq(List.<NamedExpression>of(attrs.get(0), named(maxOf(attrs.get(1), false), "m"))),
                base, scala.Option.empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsDistinctAggregate() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var key = Add$.MODULE$.apply(attrs.get(0), Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
        var plan = new Aggregate(Catalyst.seq(List.<Expression>of(key)),
                Catalyst.seq(List.<NamedExpression>of(named(key, "k"), named(maxOf(attrs.get(1), true), "m"))),
                base, scala.Option.empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsThreeColumnInput() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var key = Add$.MODULE$.apply(attrs.get(0), Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
        var plan = new Aggregate(Catalyst.seq(List.<Expression>of(key)),
                Catalyst.seq(List.<NamedExpression>of(named(key, "k"), named(maxOf(attrs.get(1), false), "m"))),
                base, scala.Option.empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsKeyUsingSecondColumn() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var key = Add$.MODULE$.apply(attrs.get(0), attrs.get(1), Add$.MODULE$.apply$default$3());
        var plan = new Aggregate(Catalyst.seq(List.<Expression>of(key)),
                Catalyst.seq(List.<NamedExpression>of(named(key, "k"), named(maxOf(attrs.get(1), false), "m"))),
                base, scala.Option.empty());
        assertEquals(plan, rule.apply(plan));
    }
}
