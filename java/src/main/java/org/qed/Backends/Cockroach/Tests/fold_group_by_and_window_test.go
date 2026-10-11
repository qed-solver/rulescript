package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldGroupByAndWindow(t *testing.T) {
	rule := loadRule(t, "FoldGroupByAndWindow")
	e := newEnv(t)
	l, lc := e.scan("nn", 2)
	r, rc := e.scan("nn", 2)
	wcol := e.f.Metadata().AddColumn("w", types.Int)
	acol := e.f.Metadata().AddColumn("a", types.Int)
	window := e.f.ConstructGroupBy(r, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[1])), wcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructInnerJoin(l, window, on, &memo.JoinPrivate{})
	outerAgg := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(lc[1])), acol)}
	before := e.f.ConstructGroupBy(join, outerAgg, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	want := e.f.ConstructGroupBy(l, outerAgg, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(e.f.ConstructLeftJoin(l, window, on, &memo.JoinPrivate{}), outerAgg,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])}))
	otherWindow := e.f.ConstructGroupBy(r, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[1])), wcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[1])})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(e.f.ConstructInnerJoin(l, otherWindow,
		e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1]))), &memo.JoinPrivate{}), outerAgg,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])}))
}
