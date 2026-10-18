package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Subtract$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneMutationReturnColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneMutationReturnCols.json"));

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression add(Expression l, Expression r) {
        return Add$.MODULE$.apply(l, r, Add$.MODULE$.apply$default$3());
    }

    private static Expression sub(Expression l, Expression r) {
        return Subtract$.MODULE$.apply(l, r, Subtract$.MODULE$.apply$default$3());
    }

    private static Expression mul(Expression l, Expression r) {
        return Multiply$.MODULE$.apply(l, r, Multiply$.MODULE$.apply$default$3());
    }

    private static List<String> names(LogicalPlan plan) {
        return Catalyst.list(plan.output()).stream().map(a -> ((Attribute) a).name()).toList();
    }

    @Test
    void prunesUnusedMutationColumnOverNullableInput() {
        var base = Support.relation("t", 4, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output());
        var inner = new Project(Catalyst.seq(List.<NamedExpression>of(
                named(add(add(a.get(0), a.get(1)), add(a.get(2), a.get(3))), "M0"),
                named(sub(add(a.get(0), a.get(1)), add(a.get(2), a.get(3))), "M1"),
                named(mul(add(a.get(0), a.get(1)), add(a.get(2), a.get(3))), "M2"),
                named(add(sub(a.get(0), a.get(1)), sub(a.get(2), a.get(3))), "M3"))), base);
        var m = Catalyst.<Attribute>list(inner.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(
                named(add(m.get(0), Literal$.MODULE$.apply(1)), "P"), m.get(3))), inner);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.child() instanceof Project mid
                && names(mid).equals(List.of("M0", "M2", "M3"))
                && Catalyst.list(top.projectList()).size() == 2
                && Catalyst.list(top.projectList()).get(1).name().equals("M3")
                && mid.child() == base, "unexpected shape:\n" + rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesThreeColumnInputAlone() {
        var base = Support.relation("t", 3, List.of(0, 1, 2), new Random(2));
        var a = Catalyst.<Attribute>list(base.output());
        var inner = new Project(Catalyst.seq(List.<NamedExpression>of(
                named(add(add(a.get(0), a.get(1)), a.get(2)), "M0"),
                named(sub(a.get(0), a.get(1)), "M1"),
                named(mul(a.get(1), a.get(2)), "M2"),
                named(add(sub(a.get(0), a.get(1)), a.get(2)), "M3"))), base);
        var m = Catalyst.<Attribute>list(inner.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(
                named(add(m.get(0), Literal$.MODULE$.apply(1)), "P"), m.get(3))), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterBetweenProjectsAlone() {
        var base = Support.relation("t", 4, List.of(0, 1, 2, 3), new Random(3));
        var a = Catalyst.<Attribute>list(base.output());
        var inner = new Project(Catalyst.seq(List.<NamedExpression>of(
                named(add(add(a.get(0), a.get(1)), add(a.get(2), a.get(3))), "M0"),
                named(sub(add(a.get(0), a.get(1)), add(a.get(2), a.get(3))), "M1"),
                named(mul(add(a.get(0), a.get(1)), add(a.get(2), a.get(3))), "M2"),
                named(add(sub(a.get(0), a.get(1)), sub(a.get(2), a.get(3))), "M3"))), base);
        var m = Catalyst.<Attribute>list(inner.output());
        var filter = new Filter(new GreaterThan(m.get(0), Literal$.MODULE$.apply(1)), inner);
        var f = Catalyst.<Attribute>list(filter.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(
                named(add(f.get(0), Literal$.MODULE$.apply(1)), "P"), f.get(3))), filter);
        assertEquals(plan, rule.apply(plan));
    }
}
