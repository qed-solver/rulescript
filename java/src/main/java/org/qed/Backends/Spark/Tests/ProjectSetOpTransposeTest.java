package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSetOpTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectSetOpTranspose.json"));

    private static Expression plusOne(Attribute a) {
        return Add$.MODULE$.apply(a, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
    }

    private static NamedExpression alias(Expression value) {
        return Alias$.MODULE$.apply(value, "f", NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Project projectOver(LogicalPlan child) {
        var column = Catalyst.<Attribute>list(child.output()).get(0);
        return new Project(Catalyst.<NamedExpression>seq(List.of(alias(plusOne(column)))), child);
    }

    @Test
    void pushesProjectionBelowUnionAll() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(left, right)), false, false);
        var plan = projectOver(union);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var core = rewritten instanceof Project p && Catalyst.list(p.projectList()).stream().allMatch(e -> e instanceof Attribute)
                ? p.child() : rewritten;
        assertTrue(core instanceof Union);
        var branches = Catalyst.<LogicalPlan>list(((Union) core).children());
        assertEquals(2, branches.size());
        assertTrue(branches.get(0) instanceof Project l && l.child().equals(left));
        assertTrue(branches.get(1) instanceof Project r && r.child().equals(right));
    }

    @Test
    void resultMatchesOriginalRows() {
        Assumptions.assumeTrue(Support.canExecute());
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(4));
        var plan = projectOver(new Union(Catalyst.<LogicalPlan>seq(List.of(left, right)), false, false));
        var rewritten = rule.apply(plan);
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesDistinctUnionAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(5));
        var right = Support.relation("R", 1, List.of(), new Random(6));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(left, right)), false, false);
        var outputs = Catalyst.<Attribute>list(union.output());
        var distinct = new Aggregate(Catalyst.<Expression>seq(outputs), Catalyst.<NamedExpression>seq(outputs), union,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
        var plan = projectOver(distinct);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesWiderUnionAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(7));
        var right = Support.relation("R", 2, List.of(), new Random(8));
        var plan = projectOver(new Union(Catalyst.<LogicalPlan>seq(List.of(left, right)), false, false));
        assertEquals(plan, rule.apply(plan));
    }
}
