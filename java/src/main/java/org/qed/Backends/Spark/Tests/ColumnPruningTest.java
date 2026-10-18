package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ColumnPruningTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ColumnPruning.json"));

    private static NamedExpression named(Expression e, String name) {
        return (NamedExpression) Alias$.MODULE$.apply(e, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void prunesInnerProjectToTheReferencedColumns() {
        var base = Support.relation("r", 3, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output());
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(
                named(x.get(0), "p2_c0"),
                named(new IsNull(x.get(1)), "p2_c1"),
                named(x.get(2), "p2_c2"),
                named(Literal$.MODULE$.apply(7), "p2_c3"))), base);
        var cols = Catalyst.<Attribute>list(inner.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), cols.get(2))), inner);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Project.class, rewritten);
        var outer = (Project) rewritten;
        assertEquals(2, Catalyst.list(outer.projectList()).size());
        assertInstanceOf(Project.class, outer.child());
        var pruned = (Project) outer.child();
        assertEquals(List.of("p2_c0", "p2_c2"),
                Catalyst.<Attribute>list(pruned.output()).stream().map(a -> a.name()).toList());
        assertEquals(base, pruned.child());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAProjectAloneWhenTheOuterReferencesOtherColumns() {
        var base = Support.relation("r", 3, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output());
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(
                named(x.get(0), "p2_c0"),
                named(new IsNull(x.get(1)), "p2_c1"),
                named(x.get(2), "p2_c2"),
                named(Literal$.MODULE$.apply(7), "p2_c3"))), base);
        var cols = Catalyst.<Attribute>list(inner.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(1), cols.get(3))), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectAloneOverAFourColumnRelation() {
        var base = Support.relation("r", 4, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output());
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(x.get(0), x.get(1), x.get(2), x.get(3))), base);
        var cols = Catalyst.<Attribute>list(inner.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), cols.get(2))), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectAloneWhenAFilterSitsBetween() {
        var base = Support.relation("r", 3, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output());
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(
                named(x.get(0), "p2_c0"),
                named(new IsNull(x.get(1)), "p2_c1"),
                named(x.get(2), "p2_c2"),
                named(Literal$.MODULE$.apply(7), "p2_c3"))), base);
        var cols = Catalyst.<Attribute>list(inner.output());
        var filtered = new Filter(new GreaterThan(cols.get(0), Literal$.MODULE$.apply(2)), inner);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), cols.get(2))), filtered);
        assertEquals(plan, rule.apply(plan));
    }
}
