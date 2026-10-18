package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyTrueAndTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Path.of(
            System.getProperty("rulescript.generated", "Generated")).resolve("SimplifyTrueAnd.json"));

    @Test
    void dropsLeadingTrueConjunct() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var pred = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new And(Literal$.MODULE$.TrueLiteral(), pred), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(new Filter(pred, base), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void dropsLeadingTrueConjunctOnNonNullColumn() {
        var base = Support.relation("t", 1, List.of(0), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var pred = new LessThan(attrs.get(0), Literal$.MODULE$.apply(3));
        var plan = new Filter(new And(Literal$.MODULE$.TrueLiteral(), pred), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(new Filter(pred, base), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTrueOnTheRightAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.TrueLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionWithTrueAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Or(Literal$.MODULE$.TrueLiteral(), new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFalseLeadingConjunctAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new And(Literal$.MODULE$.FalseLiteral(), new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }
}
