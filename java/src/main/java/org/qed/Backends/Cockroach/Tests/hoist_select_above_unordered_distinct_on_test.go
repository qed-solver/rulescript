package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestHoistSelectAboveUnorderedDistinctOn(t *testing.T) {
	rule := loadRule(t, "HoistSelectAboveUnorderedDistinctOn")
	e := newEnv(t)
	in, cols := e.scan("nn", 2)
	keys := opt.MakeColSet(cols...)
	pred := func() memo.FiltersExpr {
		return e.filters(e.f.ConstructGt(e.v(cols[0]), e.num(0)))
	}
	group := func(input memo.RelExpr, aggs memo.AggregationsExpr) memo.RelExpr {
		return e.f.ConstructGroupBy(input, aggs, &memo.GroupingPrivate{GroupingCols: keys})
	}

	before := group(e.f.ConstructSelect(in, pred()), memo.AggregationsExpr{})
	want := e.f.ConstructSelect(group(in, memo.AggregationsExpr{}), pred())
	e.expectRewrite(rule, before, want)

	maxCol := e.f.Metadata().AddColumn("max_b", types.Int)
	withAgg := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(cols[1])), maxCol),
	}
	e.expectNoMatch(rule, group(e.f.ConstructSelect(in, pred()), withAgg))

	e.expectNoMatch(rule, group(in, memo.AggregationsExpr{}))

	e.expectNoMatch(rule, e.f.ConstructSelect(group(in, memo.AggregationsExpr{}), pred()))

	nullable, ncols := e.scan("t", 2)
	nullableKeys := opt.MakeColSet(ncols...)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		e.f.ConstructSelect(nullable, e.filters(e.f.ConstructGt(e.v(ncols[0]), e.num(0)))),
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: nullableKeys},
	))
}
