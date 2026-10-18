package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectFilterTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectFilterTranspose.json"));

    private static Alias alias(Expression value, String name) {
        return new Alias(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void hoistsProjectionsBelowFilter() {
        var base = Support.relation("s", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var e = Add$.MODULE$.apply(x, y, Add$.MODULE$.apply$default$3());
        var f = new Multiply(x, Literal$.MODULE$.apply(2));
        var filter = new Filter(new GreaterThan(e, y), base);
        var plan = new Project(Catalyst.seq(List.of(alias(e, "e"), alias(f, "f"))), filter);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        assertEquals(2, top.projectList().size());
        assertTrue(top.child() instanceof Filter);
        var kept = (Filter) top.child();
        assertTrue(kept.condition() instanceof GreaterThan);
        assertTrue(kept.child() instanceof Project);
        var lowered = (Project) kept.child();
        assertEquals(4, lowered.projectList().size());
        assertTrue(lowered.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOnOtherColumnAlone() {
        var base = Support.relation("s", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var e = Add$.MODULE$.apply(x, y, Add$.MODULE$.apply$default$3());
        var f = new Multiply(x, Literal$.MODULE$.apply(2));
        var filter = new Filter(new GreaterThan(e, x), base);
        var plan = new Project(Catalyst.seq(List.of(alias(e, "e"), alias(f, "f"))), filter);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterOverThreeColumnsAlone() {
        var base = Support.relation("s", 3, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var e = Add$.MODULE$.apply(x, y, Add$.MODULE$.apply$default$3());
        var f = new Multiply(x, Literal$.MODULE$.apply(2));
        var filter = new Filter(new GreaterThan(e, y), base);
        var plan = new Project(Catalyst.seq(List.of(alias(e, "e"), alias(f, "f"))), filter);
        assertEquals(plan, rule.apply(plan));
    }
}
