package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func foldWindowGroup(e *env, input memo.RelExpr, k, v opt.ColumnID) memo.RelExpr {
	w := e.f.Metadata().AddColumn("w", types.Int)
	aggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(v)), w),
	}
	private := memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k)}
	private.Ordering.FromOrderingWithOptCols(nil, opt.MakeColSet(k))
	return e.f.ConstructGroupBy(input, aggs, &private)
}

func foldGroupByOn(e *env, input memo.RelExpr, key opt.ColumnID, v opt.ColumnID, out opt.ColumnID) memo.RelExpr {
	aggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(v)), out),
	}
	private := memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)}
	private.Ordering.FromOrderingWithOptCols(nil, opt.MakeColSet(key))
	return e.f.ConstructGroupBy(input, aggs, &private)
}

func TestFoldGroupByAndWindow(t *testing.T) {
	rule := loadRule(t, "FoldGroupByAndWindow")
	e := newEnv(t)
	input, cols := e.scan("nn", 2)
	k, v := cols[0], cols[1]
	wInput, wcols := e.scan("nn", 2)
	wk := wcols[0]
	out := e.f.Metadata().AddColumn("a", types.Int)
	on := e.filters(e.f.ConstructEq(e.v(k), e.v(wk)))
	window := foldWindowGroup(e, wInput, wk, wcols[1])
	joined := e.f.ConstructInnerJoin(input, window, on, &memo.JoinPrivate{})
	before := foldGroupByOn(e, joined, k, v, out)
	want := foldGroupByOn(e, input, k, v, out)
	e.expectRewrite(rule, before, want)

	left := e.f.ConstructLeftJoin(input, window, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, foldGroupByOn(e, left, k, v, out))

	tab, tcols := e.scan("t", 2)
	tk, tv := tcols[0], tcols[1]
	tWindowInput, twcols := e.scan("t", 2)
	tWindow := foldWindowGroup(e, tWindowInput, twcols[0], twcols[1])
	tOn := e.filters(e.f.ConstructEq(e.v(tk), e.v(twcols[0])))
	tJoined := e.f.ConstructInnerJoin(tab, tWindow, tOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, foldGroupByOn(e, tJoined, tk, tv, out))

	e.expectNoMatch(rule, foldGroupByOn(e, joined, v, v, out))
}
