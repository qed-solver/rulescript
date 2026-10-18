package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldIsNullProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldIsNullProject.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Project project(LogicalPlan child, NamedExpression... items) {
        return new Project(Catalyst.seq(List.of(items)), child);
    }

    @Test
    void foldsIsNullOfNotNullColumn() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var c = Catalyst.<Attribute>list(base.output()).get(0);
        var d = Catalyst.<Attribute>list(base.output()).get(1);
        var plan = project(base, alias(new IsNull(c), "isnull_c"), d);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var outer = (Project) rewritten;
        var outerItems = Catalyst.<NamedExpression>list(outer.projectList());
        assertEquals(2, outerItems.size());
        assertEquals(d.exprId(), outerItems.get(1).exprId());
        assertTrue(outer.child() instanceof Project);
        var inner = (Project) outer.child();
        var innerItems = Catalyst.<NamedExpression>list(inner.projectList());
        assertEquals(2, innerItems.size());
        var folded = innerItems.get(0) instanceof org.apache.spark.sql.catalyst.expressions.Alias a ? a.child() : innerItems.get(0);
        assertEquals(Literal$.MODULE$.apply(false), folded);
        assertEquals(d.exprId(), innerItems.get(1).exprId());
        assertEquals(base, inner.child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void foldsIsNullOfNotNullColumnWithAnotherSeed() {
        var base = Support.relation("t", 2, List.of(0), new Random(7));
        var c = Catalyst.<Attribute>list(base.output()).get(0);
        var d = Catalyst.<Attribute>list(base.output()).get(1);
        var plan = project(base, alias(new IsNull(c), "isnull_c"), d);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesIsNullOfNullableColumnAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var c = Catalyst.<Attribute>list(base.output()).get(0);
        var d = Catalyst.<Attribute>list(base.output()).get(1);
        var plan = project(base, alias(new IsNull(c), "isnull_c"), d);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNotNullOfNotNullColumnAlone() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var c = Catalyst.<Attribute>list(base.output()).get(0);
        var d = Catalyst.<Attribute>list(base.output()).get(1);
        var plan = project(base, alias(new IsNotNull(c), "isnotnull_c"), d);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesWiderRelationAlone() {
        var base = Support.relation("t", 3, List.of(0), new Random(1));
        var c = Catalyst.<Attribute>list(base.output()).get(0);
        var d = Catalyst.<Attribute>list(base.output()).get(1);
        var plan = project(base, alias(new IsNull(c), "isnull_c"), d);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonColumnPassthroughAlone() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var c = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = project(base, alias(new IsNull(c), "isnull_c"), alias(Literal$.MODULE$.apply(2), "two"));
        assertEquals(plan, rule.apply(plan));
    }
}
