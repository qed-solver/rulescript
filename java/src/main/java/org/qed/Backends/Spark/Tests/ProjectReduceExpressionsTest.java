package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectReduceExpressionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectReduceExpressions.json"));

    private static NamedExpression item(Expression value) {
        return Alias$.MODULE$.apply(value, "p0", NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void reducesFalseAndPredicateToFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var predicate = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(item(new And(Literal$.MODULE$.FalseLiteral(), predicate)))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        assertTrue(rewritten instanceof Project top && top.child() instanceof Project inner && inner.child() == base);
        var inner = (Project) ((Project) rewritten).child();
        var items = Catalyst.<NamedExpression>list(inner.projectList());
        assertEquals(1, items.size());
        assertTrue(items.get(0) instanceof Alias a && a.child().equals(Literal$.MODULE$.FalseLiteral()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOrOfFalseAndPredicateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var predicate = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(item(new Or(Literal$.MODULE$.FalseLiteral(), predicate)))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectWithTwoItemsAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var predicate = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(
                item(new And(Literal$.MODULE$.FalseLiteral(), predicate)), x)), base);
        assertEquals(plan, rule.apply(plan));
    }
}
