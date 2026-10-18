package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Distinct;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PruneUnionAllColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneUnionAllCols.json"));

    @Test
    void prunesTheUnusedMiddleColumnOfAUnionAll() {
        var left = Support.relation("L", 3, List.of(), new Random(1));
        var right = Support.relation("R", 3, List.of(), new Random(2));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(left, right)), false, false);
        var cols = Catalyst.<Attribute>list(union.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(2))))), union);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Project.class, rewritten);
        var top = (Project) rewritten;
        assertEquals(1, Catalyst.list(top.output()).size());
        assertInstanceOf(Union.class, top.child());
        var pruned = (Union) top.child();
        var kids = Catalyst.<LogicalPlan>list(pruned.children());
        assertEquals(2, kids.size());
        assertInstanceOf(Project.class, kids.get(0));
        assertInstanceOf(Project.class, kids.get(1));
        assertInstanceOf(LocalRelation.class, ((Project) kids.get(0)).child());
        assertInstanceOf(LocalRelation.class, ((Project) kids.get(1)).child());
        assertEquals(List.of("L_0", "L_2"), names(kids.get(0)));
        assertEquals(List.of("R_0", "R_2"), names(kids.get(1)));

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAProjectionOfTheMiddleColumnAlone() {
        var left = Support.relation("L", 3, List.of(), new Random(1));
        var right = Support.relation("R", 3, List.of(), new Random(2));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(left, right)), false, false);
        var cols = Catalyst.<Attribute>list(union.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(1))))), union);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesADistinctUnionAlone() {
        var left = Support.relation("L", 3, List.of(), new Random(1));
        var right = Support.relation("R", 3, List.of(), new Random(2));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(left, right)), false, false);
        var distinct = new Distinct(union);
        var cols = Catalyst.<Attribute>list(distinct.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(2))))), distinct);
        assertEquals(plan, rule.apply(plan));
    }

    private static Expression sum(Attribute a, Attribute b) {
        return Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
    }

    private static NamedExpression alias(Expression value) {
        return Alias$.MODULE$.apply(value, "g", NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static List<String> names(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output()).stream().map(a -> a.name()).toList();
    }
}
