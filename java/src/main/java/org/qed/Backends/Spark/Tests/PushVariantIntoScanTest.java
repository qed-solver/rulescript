package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PushVariantIntoScanTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushVariantIntoScan.json"));

    private static Expression extractA(Attribute v) {
        return Add$.MODULE$.apply(v, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
    }

    private static Expression extractB(Attribute v) {
        return Multiply$.MODULE$.apply(v, Literal$.MODULE$.apply(2), Multiply$.MODULE$.apply$default$3());
    }

    private static Alias named(Expression e, String name) {
        return Alias$.MODULE$.apply(e, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void pushesExtractedFieldsIntoTheScan() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var c = attrs.get(0);
        var v = attrs.get(1);
        var ea = extractA(v);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(c, named(ea, "a"), named(extractB(v), "b"), v)),
                new Filter(new GreaterThan(ea, Literal$.MODULE$.apply(2)), base));

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var outer = assertInstanceOf(Project.class, rewritten);
        assertEquals(4, outer.projectList().size());
        var filter = assertInstanceOf(Filter.class, outer.child());
        assertInstanceOf(GreaterThan.class, filter.condition());
        var inner = assertInstanceOf(Project.class, filter.child());
        assertEquals(4, inner.projectList().size());
        assertEquals(base, inner.child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOnNonVariantColumnAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var c = attrs.get(0);
        var v = attrs.get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(c, named(extractA(v), "a"), named(extractB(v), "b"), v)),
                new Filter(new GreaterThan(c, Literal$.MODULE$.apply(2)), base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectWithoutVariantColumnAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var c = attrs.get(0);
        var v = attrs.get(1);
        var ea = extractA(v);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(c, named(ea, "a"), named(extractB(v), "b"))),
                new Filter(new GreaterThan(ea, Literal$.MODULE$.apply(2)), base));
        assertEquals(plan, rule.apply(plan));
    }
}
