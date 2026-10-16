package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — the insert's input has one column (the primary key, passed through), it has exactly two returned columns modeled as uninterpreted functions of that column, and the outer RETURNING projection uses the key plus exactly one of the returned columns, dropping the other.
public record PruneInsertReturnCols() implements RRule {
    static final RelRN input = RelRN.scan("I", "I_Type");
    static final RexRN key = input.field(0);
    static final RexRN keptReturn = input.proj("Ret", "Ret_Type");
    static final RexRN prunedReturn = input.proj("Drop", "Drop_Type");
    static final SqlOperator top = RuleBuilder.create()
            .genericProjectionOp("Top", new RelType.VarType("Top_Type", true));

    @Override
    public RelRN before() {
        RelRN insert = input.project(Seq.from(new RexRN[]{key, keptReturn, prunedReturn}));
        return insert.project(
                new RexRN.Proj(top, Seq.from(new RexRN[]{insert.field(0), insert.field(1)})));
    }

    @Override
    public RelRN after() {
        RelRN insert = input.project(Seq.from(new RexRN[]{key, keptReturn}));
        return insert.project(
                new RexRN.Proj(top, Seq.from(new RexRN[]{insert.field(0), insert.field(1)})));
    }
}
