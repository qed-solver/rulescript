package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestReduceWindowPartitionCols(t *testing.T) {
	rule := loadRule(t, "ReduceWindowPartitionCols")
	e := newEnv(t)
	md := e.f.Metadata()
	left, lc := e.scan("t", 2)
	right, rc := e.scan("t", 2)
	fl, fr := md.AddColumn("fl", types.Int), md.AddColumn("fr", types.Int)
	w, z := md.AddColumn("w", types.Int), md.AddColumn("z", types.Int)
	window := func(input memo.RelExpr, cols []opt.ColumnID, f opt.ColumnID) memo.RelExpr {
		return e.f.ConstructProject(input, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(cols[0])), f),
		}, opt.MakeColSet(cols[0], cols[1]))
	}
	winLeft, winRight := window(left, lc, fl), window(right, rc, fr)
	maxOf := func() memo.AggregationsExpr {
		return memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(rc[1])), w)}
	}
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])), e.f.ConstructEq(e.v(fl), e.v(fr)))
	top := func(join memo.RelExpr) memo.RelExpr {
		sum := e.f.ConstructPlus(
			e.f.ConstructPlus(e.v(lc[0]), e.v(fl)), e.f.ConstructPlus(e.v(lc[1]), e.v(w)))
		return e.f.ConstructProject(join, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(sum, z)}, opt.ColSet{})
	}
	full := e.f.ConstructGroupBy(winRight, maxOf(), &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0], fr)})
	reduced := e.f.ConstructProject(
		e.f.ConstructGroupBy(winRight, maxOf(), &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])}),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(rc[0])), fr)},
		opt.MakeColSet(rc[0], w))
	before := top(e.f.ConstructInnerJoin(winLeft, full, on, &memo.JoinPrivate{}))
	want := top(e.f.ConstructInnerJoin(winLeft, reduced, on, &memo.JoinPrivate{}))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, want)
}
