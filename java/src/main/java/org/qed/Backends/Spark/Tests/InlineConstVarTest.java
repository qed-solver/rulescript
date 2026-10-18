package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThanOrEqual;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineConstVarTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InlineConstVar.json"));

    private static LocalRelation column(String name, int... values) {
        var attribute = Support.attribute(name, false);
        var rows = new ArrayList<InternalRow>();
        for (int value : values) {
            rows.add(new GenericInternalRow(new Object[] {value}));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(attribute)), Catalyst.<InternalRow>seq(rows),
                false, Match.noStream());
    }

    private static LogicalPlan uniqueConstant(String name, int value) {
        var base = column(name, value);
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        return new Aggregate(Catalyst.<Expression>seq(List.<Expression>of(key)),
                Catalyst.<NamedExpression>seq(List.<NamedExpression>of(key)), base,
                Option.<AggregateHint>empty());
    }

    private static Join join(LogicalPlan left, LogicalPlan right, org.apache.spark.sql.catalyst.plans.JoinType type,
                             Expression condition) {
        return new Join(left, right, type, Option.<Expression>apply(condition), JoinHint$.MODULE$.NONE());
    }

    @Test
    void movesFilterOnVariableToConstant() {
        var source = Support.relation("src", 1, List.of(), new Random(1));
        var constant = uniqueConstant("const", 2);
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var inner = join(source, constant, Inner$.MODULE$, new EqualTo(a, c));
        var plan = new Filter(new GreaterThanOrEqual(a, Literal$.MODULE$.apply(2)), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var filtered = (Filter) rewritten;
        assertEquals(new GreaterThanOrEqual(c, Literal$.MODULE$.apply(2)), filtered.condition());
        var joined = (Join) filtered.child();
        assertEquals(Inner$.MODULE$, joined.joinType());
        assertEquals(inner.condition(), joined.condition());
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leftOuterJoinIsLeftAlone() {
        var source = Support.relation("src", 1, List.of(), new Random(1));
        var constant = uniqueConstant("const", 2);
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var inner = join(source, constant, LeftOuter$.MODULE$, new EqualTo(a, c));
        var plan = new Filter(new GreaterThanOrEqual(a, Literal$.MODULE$.apply(2)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nonUniqueConstantIsLeftAlone() {
        var source = Support.relation("src", 1, List.of(), new Random(1));
        var constant = column("const", 2, 2);
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var inner = join(source, constant, Inner$.MODULE$, new EqualTo(a, c));
        var plan = new Filter(new GreaterThanOrEqual(a, Literal$.MODULE$.apply(2)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void lessThanJoinIsLeftAlone() {
        var source = Support.relation("src", 1, List.of(), new Random(1));
        var constant = uniqueConstant("const", 2);
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var inner = join(source, constant, Inner$.MODULE$, new LessThan(a, c));
        var plan = new Filter(new GreaterThanOrEqual(a, Literal$.MODULE$.apply(2)), inner);
        assertEquals(plan, rule.apply(plan));
    }
}
