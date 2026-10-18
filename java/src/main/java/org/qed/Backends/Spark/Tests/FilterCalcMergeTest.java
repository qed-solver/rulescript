package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterCalcMergeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterCalcMerge.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void mergesFilterOverCalcIntoOneFilter() {
        Assumptions.assumeTrue(Support.canExecute());
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var y = Catalyst.<Attribute>list(base.output()).get(1);
        var inner = new Filter(new LessThan(x, y), base);
        var e0 = alias(new Add(x, y), "e0");
        var e1 = alias(new Add(y, Literal$.MODULE$.apply(1)), "e1");
        var calc = new Project(Catalyst.seq(List.of(e0, e1)), inner);
        var outs = Catalyst.<Attribute>list(calc.output());
        var plan = new Filter(new GreaterThan(outs.get(0), outs.get(1)), calc);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.child() instanceof Filter merged
                && merged.child() == base && merged.condition() instanceof And);
        var cond = (And) ((Filter) ((Project) rewritten).child()).condition();
        assertEquals(new GreaterThan(new Add(x, y), new Add(y, Literal$.MODULE$.apply(1))), cond.left());
        assertEquals(new LessThan(x, y), cond.right());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOverProjectWithoutInnerFilterAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var y = Catalyst.<Attribute>list(base.output()).get(1);
        var calc = new Project(Catalyst.seq(List.of(alias(new Add(x, y), "e0"), alias(y, "e1"))), base);
        var outs = Catalyst.<Attribute>list(calc.output());
        var plan = new Filter(new GreaterThan(outs.get(0), outs.get(1)), calc);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterWhenScanHasThreeColumns() {
        var base = Support.relation("t", 3, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var y = Catalyst.<Attribute>list(base.output()).get(1);
        var inner = new Filter(new LessThan(x, y), base);
        var calc = new Project(Catalyst.seq(List.of(alias(new Add(x, y), "e0"), alias(y, "e1"))), inner);
        var outs = Catalyst.<Attribute>list(calc.output());
        var plan = new Filter(new GreaterThan(outs.get(0), outs.get(1)), calc);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void mergesFilterWithConjunctionAboveCalc() {
        var base = Support.relation("t", 2, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var y = Catalyst.<Attribute>list(base.output()).get(1);
        var inner = new Filter(new LessThan(x, y), base);
        var calc = new Project(Catalyst.seq(List.of(alias(new Add(x, y), "e0"), alias(y, "e1"))), inner);
        var outs = Catalyst.<Attribute>list(calc.output());
        var plan = new Filter(new And(new GreaterThan(outs.get(0), outs.get(1)),
                new GreaterThan(outs.get(0), Literal$.MODULE$.apply(0))), calc);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }
}
