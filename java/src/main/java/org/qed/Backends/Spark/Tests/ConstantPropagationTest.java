package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
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

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConstantPropagationTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ConstantPropagation.json"));

    private record Parts(LogicalPlan source, Attribute a, Attribute c, LogicalPlan constant) { }

    private static Parts parts() {
        var source = Support.relation("src", 1, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Support.attribute("c", false);
        var row = new GenericInternalRow(new Object[] {2});
        var constRel = new LocalRelation(Catalyst.<Attribute>seq(List.of(c)),
                Catalyst.<InternalRow>seq(List.of(row)), false, Match.noStream());
        var constant = new Aggregate(Catalyst.<Expression>seq(List.of(c)),
                Catalyst.<NamedExpression>seq(List.of(c)), constRel,
                Option.<AggregateHint>empty());
        return new Parts(source, a, c, constant);
    }

    private static Join join(LogicalPlan left, LogicalPlan right, JoinType type, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void propagatesTheConstantIntoTheOtherConjunct() {
        var p = parts();
        var joined = join(p.source(), p.constant(), Inner$.MODULE$, new EqualTo(p.a(), p.c()));
        var plan = new Filter(new And(new EqualTo(p.a(), p.c()), new GreaterThan(p.a(), Literal$.MODULE$.apply(1))),
                joined);
        var expected = new Filter(new And(new EqualTo(p.a(), p.c()), new GreaterThan(p.c(), Literal$.MODULE$.apply(1))),
                joined);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var p = parts();
        var joined = join(p.source(), p.constant(), LeftOuter$.MODULE$, new EqualTo(p.a(), p.c()));
        var plan = new Filter(new And(new EqualTo(p.a(), p.c()), new GreaterThan(p.a(), Literal$.MODULE$.apply(1))),
                joined);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANonEqualityConjunctAlone() {
        var p = parts();
        var joined = join(p.source(), p.constant(), Inner$.MODULE$, new EqualTo(p.a(), p.c()));
        var plan = new Filter(new And(new GreaterThan(p.a(), p.c()), new GreaterThan(p.a(), Literal$.MODULE$.apply(1))),
                joined);
        assertEquals(plan, rule.apply(plan));
    }
}
