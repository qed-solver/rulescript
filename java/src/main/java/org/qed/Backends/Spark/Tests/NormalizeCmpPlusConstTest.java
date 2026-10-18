package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Subtract$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NormalizeCmpPlusConstTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("NormalizeCmpPlusConst.json"));

    @Test
    void rewritesSumBelowConstantToDifference() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var a = cols.get(0);
        var c1 = cols.get(1);
        var c2 = cols.get(2);
        var sum = Add$.MODULE$.apply(a, c1, Add$.MODULE$.apply$default$3());
        var plan = new Filter(new LessThan(sum, c2), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var filter = assertInstanceOf(Filter.class, rewritten);
        assertEquals(base, filter.child());
        var difference = Subtract$.MODULE$.apply(c2, c1, Subtract$.MODULE$.apply$default$3());
        assertEquals(new LessThan(a, difference), filter.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesGreaterThanOverSumAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var sum = Add$.MODULE$.apply(cols.get(0), cols.get(1), Add$.MODULE$.apply$default$3());
        var plan = new Filter(new GreaterThan(sum, cols.get(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDifferenceBelowConstantAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var difference = Subtract$.MODULE$.apply(cols.get(0), cols.get(1), Subtract$.MODULE$.apply$default$3());
        var plan = new Filter(new LessThan(difference, cols.get(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoColumnInputAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var sum = Add$.MODULE$.apply(cols.get(0), cols.get(1), Add$.MODULE$.apply$default$3());
        var plan = new Filter(new LessThan(sum, cols.get(1)), base);
        assertEquals(plan, rule.apply(plan));
    }
}
