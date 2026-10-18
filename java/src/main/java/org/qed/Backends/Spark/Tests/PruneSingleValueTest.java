package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PruneSingleValueTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneSingleValue.json"));

    private static LocalRelation constantRelation(List<Boolean> values) {
        var flag = new AttributeReference("v", BooleanType$.MODULE$, false, Metadata.empty(),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()));
        var rows = new java.util.ArrayList<InternalRow>();
        for (var value : values) {
            rows.add(new GenericInternalRow(new Object[]{value}));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(flag)), Catalyst.<InternalRow>seq(rows), false,
                Match.noStream());
    }

    @Test
    void prunesSingleTrueValueFromInnerJoin() {
        var base = Support.relation("o", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Join(base, constantRelation(List.of(true)), Inner$.MODULE$,
                Option.<Expression>apply(new GreaterThan(x, Literal$.MODULE$.apply(2))), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var outer = assertInstanceOf(Project.class, rewritten);
        var inner = assertInstanceOf(Project.class, outer.child());
        var f = assertInstanceOf(Filter.class, inner.child());
        assertSame(base, f.child());
        assertEquals(2, Catalyst.list(inner.projectList()).size());
        assertEquals(new GreaterThan(x, Literal$.MODULE$.apply(2)), f.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var base = Support.relation("o", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Join(base, constantRelation(List.of(true)), LeftOuter$.MODULE$,
                Option.<Expression>apply(new GreaterThan(x, Literal$.MODULE$.apply(2))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFalseValueAlone() {
        var base = Support.relation("o", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Join(base, constantRelation(List.of(false)), Inner$.MODULE$,
                Option.<Expression>apply(new GreaterThan(x, Literal$.MODULE$.apply(2))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoRowValuesAlone() {
        var base = Support.relation("o", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Join(base, constantRelation(List.of(true, true)), Inner$.MODULE$,
                Option.<Expression>apply(new GreaterThan(x, Literal$.MODULE$.apply(2))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
