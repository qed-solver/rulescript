package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestReduceWindowPartitionCols(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ReduceWindowPartitionCols")
	build := func(extraGroup bool) (memo.RelExpr, memo.RelExpr) {
		leftBase, leftSource := e.scan("u", 2)
		k, v := leftSource[0], leftSource[1]
		fk := e.f.Metadata().AddColumn("fk", types.Int)
		win := e.f.ConstructProject(leftBase, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(k), e.num(1)), fk),
		}, opt.MakeColSet(k, v))
		rightBase, rightSource := e.scan("u", 2)
		rk, rv := rightSource[0], rightSource[1]
		rfk := e.f.Metadata().AddColumn("rfk", types.Int)
		rightWin := e.f.ConstructProject(rightBase, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(rk), e.num(1)), rfk),
		}, opt.MakeColSet(rk, rv))
		w := e.f.Metadata().AddColumn("w", types.Int)
		groupCols := opt.MakeColSet(rk, rfk)
		if extraGroup {
			groupCols.Add(rv)
		}
		full := e.f.ConstructGroupBy(rightWin, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructCountRows(), w),
		}, &memo.GroupingPrivate{GroupingCols: groupCols})
		join := e.f.ConstructInnerJoin(win, full, e.filters(
			e.f.ConstructEq(e.v(k), e.v(rk)),
			e.f.ConstructEq(e.v(fk), e.v(rfk)),
		), &memo.JoinPrivate{})
		top := e.f.ConstructPlus(e.f.ConstructPlus(e.v(k), e.v(fk)), e.f.ConstructPlus(e.v(v), e.v(w)))
		before := e.f.ConstructProject(join, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(top, e.f.Metadata().AddColumn("top", types.Int)),
		}, opt.ColSet{})
		reduced := e.f.ConstructGroupBy(rightWin, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructCountRows(), w),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rk)})
		right := e.f.ConstructProject(reduced, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(rk), e.num(1)), rfk),
		}, opt.MakeColSet(rk, w))
		rightJoin := e.f.ConstructInnerJoin(win, right, e.filters(
			e.f.ConstructEq(e.v(k), e.v(rk)),
			e.f.ConstructEq(e.v(fk), e.v(rfk)),
		), &memo.JoinPrivate{})
		wantTop := e.f.ConstructPlus(e.f.ConstructPlus(e.v(k), e.v(fk)), e.f.ConstructPlus(e.v(v), e.v(w)))
		want := e.f.ConstructProject(rightJoin, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(wantTop, e.f.Metadata().AddColumn("want", types.Int)),
		}, opt.ColSet{})
		return before, want
	}
	before, want := build(false)
	e.expectRewriteUpToFreshColumns(rule, before, want)
	nearMiss, _ := build(true)
	e.expectNoMatch(rule, nearMiss)
}
