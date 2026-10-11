package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — inner join only, with the right input a single-row Values holding one constant column (a Boolean literal)
public record PruneSingleValue() implements RRule {

    static final RelNode valsNode =
        RuleBuilder.create().values(new String[]{"v"}, true).build();
    static final RelRN vals = () -> valsNode;
    static final RelRN other = RelRN.scan("O", "O_Type");

    @Override
    public RelRN before() {
        return other.join(JoinRelType.INNER, "jcond", vals);
    }

    @Override
    public RelRN after() {
        RexRN cond = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("jcond", true),
            Seq.of(other.field(0), RexRN.trueLiteral()));
        return other.filter(cond).project(Seq.of(other.field(0), RexRN.trueLiteral()));
    }
}
