package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectSerializerPruningTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ObjectSerializerPruning.json"));

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void prunesSerializerFieldNotReferencedByOuterProject() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var ser0 = named(new GreaterThan(x, Literal$.MODULE$.apply(1)), "ser0");
        var ser1 = named(new LessThan(x, Literal$.MODULE$.apply(2)), "ser1");
        var ser2 = named(new GreaterThan(x, Literal$.MODULE$.apply(3)), "ser2");
        var serializer = new Project(Catalyst.seq(List.of(ser0, ser1, ser2)), base);
        var serializerOutput = Catalyst.<Attribute>list(serializer.output());
        var plan = new Project(Catalyst.seq(List.of(serializerOutput.get(0), serializerOutput.get(2))), serializer);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.child() instanceof Project inner);
        var outer = (Project) rewritten;
        var inner = (Project) outer.child();
        assertEquals(2, Catalyst.list(outer.projectList()).size());
        assertEquals(2, Catalyst.list(inner.projectList()).size());
        assertEquals("ser0", Catalyst.<NamedExpression>list(inner.projectList()).get(0).name());
        assertEquals("ser2", Catalyst.<NamedExpression>list(inner.projectList()).get(1).name());
        assertSame(base, inner.child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectWhoseOuterReferencesDifferFromPatternAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var ser0 = named(new GreaterThan(x, Literal$.MODULE$.apply(1)), "ser0");
        var ser1 = named(new LessThan(x, Literal$.MODULE$.apply(2)), "ser1");
        var ser2 = named(new GreaterThan(x, Literal$.MODULE$.apply(3)), "ser2");
        var serializer = new Project(Catalyst.seq(List.of(ser0, ser1, ser2)), base);
        var serializerOutput = Catalyst.<Attribute>list(serializer.output());
        var plan = new Project(Catalyst.seq(List.of(serializerOutput.get(0), serializerOutput.get(1))), serializer);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectWithFilterBetweenAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var ser0 = named(new GreaterThan(x, Literal$.MODULE$.apply(1)), "ser0");
        var ser1 = named(new LessThan(x, Literal$.MODULE$.apply(2)), "ser1");
        var ser2 = named(new GreaterThan(x, Literal$.MODULE$.apply(3)), "ser2");
        var serializer = new Project(Catalyst.seq(List.of(ser0, ser1, ser2)), base);
        var serializerOutput = Catalyst.<Attribute>list(serializer.output());
        var filtered = new Filter(new GreaterThan(serializerOutput.get(0), Literal$.MODULE$.apply(0)), serializer);
        var plan = new Project(Catalyst.seq(List.of(serializerOutput.get(0), serializerOutput.get(2))), filtered);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectOverTwoColumnInputAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var ser0 = named(new GreaterThan(x, Literal$.MODULE$.apply(1)), "ser0");
        var ser1 = named(new LessThan(x, Literal$.MODULE$.apply(2)), "ser1");
        var ser2 = named(new GreaterThan(x, Literal$.MODULE$.apply(3)), "ser2");
        var serializer = new Project(Catalyst.seq(List.of(ser0, ser1, ser2)), base);
        var serializerOutput = Catalyst.<Attribute>list(serializer.output());
        var plan = new Project(Catalyst.seq(List.of(serializerOutput.get(0), serializerOutput.get(2))), serializer);
        assertEquals(plan, rule.apply(plan));
    }
}
