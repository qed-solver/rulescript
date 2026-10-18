package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Subtract$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneProjectColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneProjectCols.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void prunesTheUnusedThirdColumnOfTheInnerProject() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var used = alias(Multiply$.MODULE$.apply(x, Literal$.MODULE$.apply(2), Multiply$.MODULE$.apply$default$3()), "Used");
        var unused = alias(Subtract$.MODULE$.apply(x, Literal$.MODULE$.apply(1), Subtract$.MODULE$.apply$default$3()), "Unused");
        var inner = new Project(Catalyst.seq(List.<NamedExpression>of(x, used, unused)), base);
        var innerOut = Catalyst.<Attribute>list(inner.output());
        var top = alias(Add$.MODULE$.apply(innerOut.get(0), innerOut.get(1), Add$.MODULE$.apply$default$3()), "Top");
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(top)), inner);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() == base);
        var items = Catalyst.<NamedExpression>list(((Project) rewritten).projectList());
        assertEquals(1, items.size());
        assertEquals("Top", items.get(0).name());
        assertTrue(items.get(0) instanceof Alias a && a.child() instanceof Add);
        assertEquals(1, Catalyst.list(rewritten.output()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOuterProjectThatReadsTheUnusedColumnAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var used = alias(Multiply$.MODULE$.apply(x, Literal$.MODULE$.apply(2), Multiply$.MODULE$.apply$default$3()), "Used");
        var unused = alias(Subtract$.MODULE$.apply(x, Literal$.MODULE$.apply(1), Subtract$.MODULE$.apply$default$3()), "Unused");
        var inner = new Project(Catalyst.seq(List.<NamedExpression>of(x, used, unused)), base);
        var innerOut = Catalyst.<Attribute>list(inner.output());
        var top = alias(Add$.MODULE$.apply(innerOut.get(0), innerOut.get(2), Add$.MODULE$.apply$default$3()), "Top");
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(top)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnInnerProjectWithTwoColumnsAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var used = alias(Multiply$.MODULE$.apply(x, Literal$.MODULE$.apply(2), Multiply$.MODULE$.apply$default$3()), "Used");
        var inner = new Project(Catalyst.seq(List.<NamedExpression>of(x, used)), base);
        var innerOut = Catalyst.<Attribute>list(inner.output());
        var top = alias(Add$.MODULE$.apply(innerOut.get(0), innerOut.get(1), Add$.MODULE$.apply$default$3()), "Top");
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(top)), inner);
        assertEquals(plan, rule.apply(plan));
    }
}
