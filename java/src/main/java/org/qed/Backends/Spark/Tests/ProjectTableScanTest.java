package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ProjectTableScanTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectTableScan.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression sum(Attribute a, Attribute b) {
        return Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
    }

    private static List<Object> ids(org.apache.spark.sql.catalyst.plans.logical.LogicalPlan plan) {
        return Catalyst.list(plan.output()).stream().map(a -> (Object) a.exprId()).toList();
    }

    @Test
    void pushesTheUsedColumnsBelowTheFunctionProjection() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(2)), "f"), cols.get(2))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Project.class, rewritten);
        var outer = (Project) rewritten;
        assertInstanceOf(Project.class, outer.child());
        var inner = (Project) outer.child();
        assertEquals(base, inner.child());
        assertEquals(List.of(cols.get(0), cols.get(2)), Catalyst.list(inner.projectList()));
        assertEquals(ids(plan), ids(rewritten));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAProjectionThatUsesAnotherColumnAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(1)), "f"), cols.get(2))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectionWhoseSecondOutputIsAnotherColumnAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(2)), "f"), cols.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectionOverATwoColumnScanAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(1)), "f"), cols.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void pushesTheUsedColumnsBelowTheFunctionProjectionOverAFilter() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var filtered = new Filter(new GreaterThan(cols.get(0), Literal$.MODULE$.apply(1)), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(2)), "f"), cols.get(2))), filtered);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(ids(plan), ids(rewritten));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }
}
