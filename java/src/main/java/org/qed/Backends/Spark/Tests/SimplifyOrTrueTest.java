package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyOrTrueTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyOrTrue.json"));

    @Test
    void replacesDisjunctionWithTrueByTrue() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var trueLiteral = Literal$.MODULE$.apply(true);
        var plan = new Filter(new Or(new GreaterThan(x, Literal$.MODULE$.apply(2)), trueLiteral), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && f.condition() instanceof Literal l && Boolean.TRUE.equals(l.value()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void replacesDisjunctionWithTrueWhenPredicateIsNullable() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Or(new GreaterThan(x, Literal$.MODULE$.apply(1)), Literal$.MODULE$.apply(true)), base);
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && f.condition() instanceof Literal l && Boolean.TRUE.equals(l.value()));
    }

    @Test
    void leavesDisjunctionWithFalseAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Or(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.apply(false)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesPlainPredicateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }
}
