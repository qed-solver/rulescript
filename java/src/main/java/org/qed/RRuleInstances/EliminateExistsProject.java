// SCOPE: FULL — EXISTS is unconditionally insensitive to any Project on its input, since Project never changes row cardinality
package org.qed.RRuleInstances;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record EliminateExistsProject() implements RRule {
    record Exists(RelRN input) implements RexRN {
        @Override
        public RexNode semantics() {
            return RexSubQuery.exists(input.semantics());
        }
    }

    static final RelRN outer = RelRN.scan("Outer", "Outer_Type");
    static final RelRN input = RelRN.scan("Input", "Input_Type");
    static final RelRN projected = input.project(input.proj("f", "F_Type"));

    @Override
    public RelRN before() {
        return outer.filter(new Exists(projected));
    }

    @Override
    public RelRN after() {
        return outer.filter(new Exists(input));
    }
}
