package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
public record ValuesReduce() implements RRule {

    @Override
    public RelRN before() {
        RelNode node = RuleBuilder.create()
                .values(new String[]{"a", "b", "c"}, 1, 2, 3, 4, 5, 6)
                .build();
        RelRN values = () -> node;
        return values.project(Seq.of(values.field(2), values.field(0)));
    }

    @Override
    public RelRN after() {
        RelNode node = RuleBuilder.create()
                .values(new String[]{"c", "a"}, 3, 1, 6, 4)
                .build();
        return () -> node;
    }
}
