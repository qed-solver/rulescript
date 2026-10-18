package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the project emits exactly two boolean-literal constant columns and one non-constant column, and the aggregate groups by exactly those three columns with a single call on the non-constant one
public record AggregateProjectConstantToDummyJoin() implements RRule {
    static final RelRN source = RelRN.scan("S", "S_Type");
    static final RexRN lit0 = RexRN.trueLiteral();
    static final RexRN lit1 = RexRN.falseLiteral();

    static final RelRN project = source.project(Seq.of(lit0, lit1, source.field(0)));
    static final RexRN key0 = project.field(0);
    static final RexRN key1 = project.field(1);
    static final RexRN key2 = project.field(2);
    static final RelRN.AggCall f = key2.aggCall("f");

    @Override
    public RelRN before() {
        return new RelRN.Aggregate(project, Seq.of(key0, key1, key2), Seq.of(f));
    }

    @Override
    public RelRN after() {
        RelNode dummyNode = RuleBuilder.create()
                .values(new String[]{"x", "d"}, true, false)
                .build();
        RelRN dummy = () -> dummyNode;

        RelRN joined = source.join(JoinRelType.INNER, RexRN.trueLiteral(), dummy);
        RelRN newProject = joined.project(Seq.of(joined.field(1), joined.field(2), joined.field(0)));
        RexRN nkey0 = newProject.field(0);
        RexRN nkey1 = newProject.field(1);
        RexRN nkey2 = newProject.field(2);
        return new RelRN.Aggregate(newProject, Seq.of(nkey0, nkey1, nkey2), Seq.of(nkey2.aggCall("f")));
    }
}
