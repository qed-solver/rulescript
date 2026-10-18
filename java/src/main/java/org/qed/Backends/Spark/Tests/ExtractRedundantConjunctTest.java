package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ExtractRedundantConjunctTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ExtractRedundantConjunct.json"));

    @Test
    void extractsSharedConjunctOutOfDisjunction() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = sharedConjunctDisjunction(base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var expected = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(2)),
                new Or(new LessThan(x, Literal$.MODULE$.apply(15)), new IsNotNull(x))), base);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        for (long seed = 1; seed <= 5; seed++) {
            var data = Support.relation("t", 1, List.of(), new Random(seed));
            var input = sharedConjunctDisjunction(data);
            assertEquals(Support.run(input), Support.run(rule.apply(input)));
        }
    }

    @Test
    void leavesDisjunctionWithoutSharedConjunctAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var a = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var b = new LessThan(x, Literal$.MODULE$.apply(15));
        var d = new EqualTo(x, Literal$.MODULE$.apply(3));
        var c = new IsNotNull(x);
        var plan = new Filter(new Or(new And(a, b), new And(d, c)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesThreeBranchDisjunctionAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var a = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var b = new LessThan(x, Literal$.MODULE$.apply(15));
        var c = new IsNotNull(x);
        var d = new EqualTo(x, Literal$.MODULE$.apply(3));
        var plan = new Filter(new Or(new And(a, b), new Or(new And(a, c), new And(a, d))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionWithNonConjunctBranchAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var a = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var b = new LessThan(x, Literal$.MODULE$.apply(15));
        var plan = new Filter(new Or(new And(a, b), a), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesThreeWayConjunctionBranchAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var a = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var b = new LessThan(x, Literal$.MODULE$.apply(15));
        var c = new IsNotNull(x);
        var plan = new Filter(new Or(new And(new And(a, b), c), new And(a, c)), base);
        assertEquals(plan, rule.apply(plan));
    }

    private static LogicalPlan sharedConjunctDisjunction(LogicalPlan base) {
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var a = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var b = new LessThan(x, Literal$.MODULE$.apply(15));
        var c = new IsNotNull(x);
        return new Filter(new Or(new And(a, b), new And(a, c)), base);
    }
}
