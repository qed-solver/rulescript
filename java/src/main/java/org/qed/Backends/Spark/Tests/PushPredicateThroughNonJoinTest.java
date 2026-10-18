package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushPredicateThroughNonJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushPredicateThroughNonJoin.json"));

    @Test
    void pushesTheFirstConjunctBelowTheProjection() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p2 = Alias$.MODULE$.apply(x, "p2", NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
        var projection = new Project(Catalyst.<NamedExpression>seq(List.of(x, p2)), base);
        var p2Attr = p2.toAttribute();
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(2)),
                new LessThan(p2Attr, Literal$.MODULE$.apply(3))), projection);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter top && top.condition().equals(new LessThan(p2Attr, Literal$.MODULE$.apply(3)))
                && top.child() instanceof Project pushed && pushed.child() instanceof Filter bottom
                && bottom.condition().equals(new GreaterThan(x, Literal$.MODULE$.apply(2)))
                && bottom.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOrConditionAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p2 = Alias$.MODULE$.apply(x, "p2", NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
        var projection = new Project(Catalyst.<NamedExpression>seq(List.of(x, p2)), base);
        var plan = new Filter(new Or(new GreaterThan(x, Literal$.MODULE$.apply(2)),
                new LessThan(p2.toAttribute(), Literal$.MODULE$.apply(3))), projection);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesASingleConjunctAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p2 = Alias$.MODULE$.apply(x, "p2", NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
        var projection = new Project(Catalyst.<NamedExpression>seq(List.of(x, p2)), base);
        var plan = new Filter(new LessThan(p2.toAttribute(), Literal$.MODULE$.apply(3)), projection);
        assertEquals(plan, rule.apply(plan));
    }
}
