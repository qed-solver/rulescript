package org.qed.RRuleInstances;
import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — the Select's input is an uninterpreted 3-column scan (col 0 standing in for the outer column, col 1 for the local column, col 2 an unrelated extra column), $on is the genuine equality col 0 = col 1 applied as the Select's filter, and the remapped reference is a unary uninterpreted predicate h applied in a filter nested in the input
public record TryRemapSelectOuterCols() implements RRule {
    static final RelRN input = RelRN.scanMany("Input", Seq.of(RexRN.varType("T", true), RexRN.varType("T", true), RexRN.varType("U", true)));
    static final SqlOperator h = RuleBuilder.create().genericPredicateOp("h", true);
    static final RexRN on = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(input.field(0), input.field(1)));

    @Override
    public RelRN before() {
        return input.filter(new RexRN.Pred(h, Seq.of(input.field(0)))).filter(on);
    }

    @Override
    public RelRN after() {
        return input.filter(new RexRN.Pred(h, Seq.of(input.field(1)))).filter(on);
    }
}