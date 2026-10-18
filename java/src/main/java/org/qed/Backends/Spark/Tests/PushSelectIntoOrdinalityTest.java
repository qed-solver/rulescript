package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
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

class PushSelectIntoOrdinalityTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushSelectIntoOrdinality.json"));

    @Test
    void pushesFilterBelowOrdinality() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var c0 = attrs.get(0);
        var c1 = attrs.get(1);
        var o = Alias$.MODULE$.apply(Add$.MODULE$.apply(c0, c1, Add$.MODULE$.apply$default$3()), "o",
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
        var items = List.<NamedExpression>of(c0, c1, o);
        var ordinality = new Project(Catalyst.seq(items), base);
        var plan = new Filter(new GreaterThan(c0, Literal$.MODULE$.apply(2)), ordinality);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project, "expected the Project to end on top, got " + rewritten);
        var project = (Project) rewritten;
        assertEquals(items, Catalyst.list(project.projectList()));
        assertTrue(project.child() instanceof Filter, "expected a Filter under the Project, got " + project.child());
        var pushed = (Filter) project.child();
        assertEquals(new GreaterThan(c0, Literal$.MODULE$.apply(2)), pushed.condition());
        assertTrue(pushed.child() == base, "expected the Filter directly over the input");

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOnOrdinalityColumnAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var c0 = attrs.get(0);
        var c1 = attrs.get(1);
        var o = Alias$.MODULE$.apply(Add$.MODULE$.apply(c0, c1, Add$.MODULE$.apply$default$3()), "o",
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
        var items = List.<NamedExpression>of(c0, c1, o);
        var ordinality = new Project(Catalyst.seq(items), base);
        var plan = new Filter(new GreaterThan(o.toAttribute(), Literal$.MODULE$.apply(2)), ordinality);
        assertEquals(plan, rule.apply(plan));
    }
}
