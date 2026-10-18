package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.types.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineProjectInProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InlineProjectInProject.json"));

    private static Expression plus(Expression a, Expression b) {
        return Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
    }

    private static Expression times(Expression a, Expression b) {
        return Multiply$.MODULE$.apply(a, b, Multiply$.MODULE$.apply$default$3());
    }

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.<Metadata>empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void inlinesInnerProjectIntoOuterProject() {
        var base = Support.relation("s", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Project(Catalyst.seq(List.<NamedExpression>of(named(plus(x, Literal$.MODULE$.apply(1)), "x2"))), base);
        var x2 = Catalyst.<Attribute>list(inner.output()).get(0);
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(named(times(x2, Literal$.MODULE$.apply(2)), "t"), x2)), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var outputs = Catalyst.<NamedExpression>list(((Project) rewritten).projectList());
        assertEquals(2, outputs.size());
        assertEquals("t", outputs.get(0).name());
        assertEquals(times(plus(x, Literal$.MODULE$.apply(1)), Literal$.MODULE$.apply(2)), ((Alias) outputs.get(0)).child());
        assertEquals(plus(x, Literal$.MODULE$.apply(1)), ((Alias) outputs.get(1)).child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOuterProjectWithoutPassthroughAlone() {
        var base = Support.relation("s", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Project(Catalyst.seq(List.<NamedExpression>of(named(plus(x, Literal$.MODULE$.apply(1)), "x2"))), base);
        var x2 = Catalyst.<Attribute>list(inner.output()).get(0);
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(named(times(x2, Literal$.MODULE$.apply(2)), "t"))), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOuterProjectOverBaseRelationAlone() {
        var base = Support.relation("s", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(named(times(x, Literal$.MODULE$.apply(2)), "t"), x)), base);
        assertEquals(plan, rule.apply(plan));
    }
}
