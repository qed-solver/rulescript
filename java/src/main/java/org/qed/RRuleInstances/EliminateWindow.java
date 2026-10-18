package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — QED has no Window operator (no frame/ordering semantics), so the zero-function window is modeled, per the established window modeling convention, as an INNER equi-join of a fixed two-column input (non-null partition key k, data column v) with its distinct partition keys.
public record EliminateWindow() implements RRule {
    static final RelType.VarType kT = RexRN.varType("k", false);
    static final RelType.VarType vT = RexRN.varType("v", false);

    // The Window's input: rows (k, v), k the partition column.
    static final RelRN input = RelRN.scanMany("Input", Seq.of(kT, vT));

    // A window with NO functions computes nothing per partition, so its
    // per-partition structure is just the distinct partition keys: the input
    // grouped by k with an empty function list.
    static final RelRN perPart = new RelRN.Aggregate(
            input,
            Seq.of(input.field(0)),
            Seq.<RelRN.AggCall>empty());

    // Window output = input cols + window cols. With no window cols this is
    // the INNER equi-join of the input with its distinct partition keys on
    // k: each input row matches exactly one key row, so the join preserves
    // every input row at exactly its input multiplicity. Join layout:
    // (input.k, input.v, perPart.k) — ordinals in the concatenated space.
    static final RexRN cond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(input.joinField(0, perPart), input.joinField(2, perPart)));
    static final RelRN join = input.join(JoinRelType.INNER, cond, perPart);

    // The window operator itself outputs exactly the input columns, so drop
    // the duplicated key column the join-back materialization carries.
    static final RelRN beforeRel = join.project(Seq.of(join.field(0), join.field(1)));

    @Override
    public RelRN before() {
        return beforeRel;
    }

    @Override
    public RelRN after() {
        return input;
    }
}