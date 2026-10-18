package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestTryDecorrelateGroupBy(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateGroupBy")
	t.Run("single_grouping_column", func(t *testing.T) {
		e := newEnv(t)
		l, lc := e.scan("u", 1)
		r, rc := e.scan("nn3", 2)
		aggCol := e.f.Metadata().AddColumn("count", types.Int)
		agg := e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[1])), aggCol)
		grouped := e.f.ConstructGroupBy(r, memo.AggregationsExpr{agg}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
		before := e.f.ConstructSelect(e.f.ConstructInnerJoin(l, grouped, memo.TrueFilter, memo.EmptyJoinPrivate), e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))))
		joined := e.f.ConstructInnerJoin(l, r, memo.TrueFilter, memo.EmptyJoinPrivate)
		want := e.f.ConstructSelect(e.f.ConstructGroupBy(joined, memo.AggregationsExpr{agg}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0], rc[0])}), e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))))
		e.expectRewrite(rule, before, want)
	})
	t.Run("extra_grouping_column_near_miss", func(t *testing.T) {
		e := newEnv(t)
		l, lc := e.scan("u", 1)
		r, rc := e.scan("nn3", 3)
		aggCol := e.f.Metadata().AddColumn("count", types.Int)
		agg := e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[2])), aggCol)
		grouped := e.f.ConstructGroupBy(r, memo.AggregationsExpr{agg}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0], rc[1])})
		plan := e.f.ConstructSelect(e.f.ConstructInnerJoin(l, grouped, memo.TrueFilter, memo.EmptyJoinPrivate), e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))))
		e.expectNoMatch(rule, plan)
	})
}
