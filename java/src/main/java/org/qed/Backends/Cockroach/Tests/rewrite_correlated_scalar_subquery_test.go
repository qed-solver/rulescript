package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRewriteCorrelatedScalarSubquery(t *testing.T) {
	rule := loadRule(t, "RewriteCorrelatedScalarSubquery")
	t.Run("rewrite correlated aggregate", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 1)
		right, rc := e.scan("t", 1)
		cond := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
		apply := e.f.ConstructLeftJoinApply(left, right, cond, &memo.JoinPrivate{})
		aggCol := e.f.Metadata().AddColumn("aggregate", types.Int)
		before := e.f.ConstructGroupBy(apply, memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[0])), aggCol)}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
		join := e.f.ConstructLeftJoin(left, right, cond, &memo.JoinPrivate{})
		want := e.f.ConstructGroupBy(join, memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[0])), aggCol)}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
		e.expectRewrite(rule, before, want)
	})
	t.Run("multi-column inner near miss", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 1)
		right, rc := e.scan("t", 2)
		cond := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
		apply := e.f.ConstructLeftJoinApply(left, right, cond, &memo.JoinPrivate{})
		aggCol := e.f.Metadata().AddColumn("aggregate", types.Int)
		plan := e.f.ConstructGroupBy(apply, memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[0])), aggCol)}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
		e.expectNoMatch(rule, plan)
	})
}
