package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.InSubquery;
import org.apache.spark.sql.catalyst.expressions.ListQuery;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizeSelectAnyFilterTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("NormalizeSelectAnyFilter.json"));

    private static ListQuery subquery(org.apache.spark.sql.catalyst.plans.logical.LogicalPlan sub) {
        return new ListQuery(sub, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                sub.output().size(), Catalyst.<Expression>seq(List.of()), scala.Option.empty());
    }

    @Test
    void rewritesInFilterToCorrelatedExists() {
        var input = Support.relation("Input", 1, List.of(), new Random(1));
        var sub = Support.relation("Sub", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(input.output()).get(0);
        var plan = new Filter(new InSubquery(Catalyst.<Expression>seq(List.of(x)), subquery(sub)), input);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == input && f.condition() instanceof Exists);
        var exists = (Exists) ((Filter) rewritten).condition();
        assertTrue(exists.plan() instanceof Filter);
        assertEquals(sub, ((Filter) exists.plan()).child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void rewritesInFilterOverNonNullColumns() {
        var input = Support.relation("Input", 1, List.of(0), new Random(3));
        var sub = Support.relation("Sub", 1, List.of(0), new Random(4));
        var x = Catalyst.<Attribute>list(input.output()).get(0);
        var plan = new Filter(new InSubquery(Catalyst.<Expression>seq(List.of(x)), subquery(sub)), input);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesInConjunctionAlone() {
        var input = Support.relation("Input", 1, List.of(), new Random(1));
        var sub = Support.relation("Sub", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(input.output()).get(0);
        var in = new InSubquery(Catalyst.<Expression>seq(List.of(x)), subquery(sub));
        var plan = new Filter(new And(in, new GreaterThan(x, Literal$.MODULE$.apply(1))), input);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesMultiColumnSubqueryAlone() {
        var input = Support.relation("Input", 1, List.of(), new Random(1));
        var sub = Support.relation("Sub", 2, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(input.output()).get(0);
        var plan = new Filter(new InSubquery(Catalyst.<Expression>seq(List.of(x)), subquery(sub)), input);
        assertEquals(plan, rule.apply(plan));
    }
}
