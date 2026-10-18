package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BooleanSimplificationTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("BooleanSimplification.json"));

    @Test
    void factorsTheSharedDisjunctOutOfTwoConjunctedDisjunctions() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var a = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var b = new LessThan(x, Literal$.MODULE$.apply(3));
        var c = new EqualTo(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new And(new Or(a, b), new Or(a, c)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(new Filter(new Or(a, new And(b, c)), base), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesDisjunctionsWithoutASharedFactorAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var a = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var b = new LessThan(x, Literal$.MODULE$.apply(3));
        var c = new EqualTo(x, Literal$.MODULE$.apply(1));
        var d = new EqualTo(x, Literal$.MODULE$.apply(0));
        var plan = new Filter(new And(new Or(a, b), new Or(c, d)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAConjunctThatIsNotADisjunctionAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var a = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var b = new LessThan(x, Literal$.MODULE$.apply(3));
        var c = new EqualTo(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new And(new Or(a, b), new And(a, c)), base);
        assertEquals(plan, rule.apply(plan));
    }
}
