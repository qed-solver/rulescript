package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateAggregateFilter(t *testing.T) {
	rule := loadRule(t, "EliminateAggregateFilter")
	e := newEnv(t)

	s, sc := e.scan("t", 2)
	out := e.f.Metadata().AddColumn("sum", types.Int)
	aggs := func() memo.AggregationsExpr {
		return memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(sc[1])), out)}
	}
	grouping := func() *memo.GroupingPrivate {
		return &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])}
	}

	trueSel := e.f.ConstructSelect(s, memo.FiltersExpr{})
	before := e.f.ConstructGroupBy(trueSel, aggs(), grouping())
	want := e.f.ConstructGroupBy(s, aggs(), grouping())
	e.expectRewrite(rule, before, want)

	realSel := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(sc[1]), e.num(1))))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(realSel, aggs(), grouping()))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, aggs(), grouping()))
}
