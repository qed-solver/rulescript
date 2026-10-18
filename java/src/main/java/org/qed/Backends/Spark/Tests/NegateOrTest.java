package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NegateOrTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("NegateOr.json"));

    @Test
    void pushesNegationOfDisjunctionIntoConjunction() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var right = new LessThan(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new Not(new Or(left, right)), base);
        var expected = new Filter(new And(new Not(left), new Not(right)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNegationOfConjunctionAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var right = new LessThan(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new Not(new And(left, right)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionWithoutNegationAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var right = new LessThan(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new Or(left, right), base);
        assertEquals(plan, rule.apply(plan));
    }
}
