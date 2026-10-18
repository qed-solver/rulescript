package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.IntegerType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldFunctionWithNullArgTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldFunctionWithNullArg.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void foldsEqualsAgainstNullToBooleanNull() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var nullInt = Literal$.MODULE$.create(null, IntegerType$.MODULE$);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(new EqualTo(x, nullInt), "c"))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var outer = (Project) rewritten;
        assertEquals(Catalyst.list(plan.output()).get(0).exprId(), Catalyst.list(rewritten.output()).get(0).exprId());
        assertTrue(outer.child() instanceof Project);
        var inner = (Project) outer.child();
        assertSame(base, inner.child());
        var items = Catalyst.<NamedExpression>list(inner.projectList());
        assertEquals(1, items.size());
        var value = items.get(0);
        var folded = value instanceof Alias a ? a.child() : value;
        assertTrue(folded instanceof Literal lit && lit.value() == null && lit.dataType() == BooleanType$.MODULE$);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesEqualsAgainstNullOnTheLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var nullInt = Literal$.MODULE$.create(null, IntegerType$.MODULE$);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(new EqualTo(nullInt, x), "c"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOtherOperatorsAgainstNullAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var nullInt = Literal$.MODULE$.create(null, IntegerType$.MODULE$);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(new GreaterThan(x, nullInt), "c"))), base);
        assertEquals(plan, rule.apply(plan));
    }
}
