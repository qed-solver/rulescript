package org.qed.RRuleInstances;
// SCOPE: PARTIAL — INNER joins only, single-column L/IL/IR, uninterpreted 3-ary on and 2-ary innerOn
import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record TryDecorrelateInnerJoin() implements RRule {
  static final RelRN L  = RelRN.scan("L",  RexRN.varType("T", true), false);
  static final RelRN IL = RelRN.scan("IL", RexRN.varType("T", true), false);
  static final RelRN IR = RelRN.scan("IR", RexRN.varType("T", true), false);

  static final SqlOperator on      = RuleBuilder.create().genericPredicateOp("on", true);
  static final SqlOperator innerOn = RuleBuilder.create().genericPredicateOp("innerOn", true);

  // Inner join that is not yet decorrelated: its own ON still filters (IL,IR).
  static final RelRN inB = IL.join(JoinRelType.INNER,
      new RexRN.Pred(innerOn, IL.joinFields(IR)), IR);
  // Inner join with its condition pulled up: no remaining ON.
  static final RelRN inA = IL.join(JoinRelType.INNER,
      RexRN.trueLiteral(), IR);

  @Override
  public RelRN before() {
    return L.join(JoinRelType.INNER,
        new RexRN.Pred(on, L.joinFields(inB)), inB);
  }

  @Override
  public RelRN after() {
    return L.join(JoinRelType.INNER,
        RexRN.and(
            new RexRN.Pred(on, L.joinFields(inA)),
            new RexRN.Pred(innerOn,
                Seq.of(L.joinField(1, inA), L.joinField(2, inA)))),
        inA);
  }
}
