package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneWindowInputCols(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneWindowInputCols")

	input, cols := e.scan("nn3", 3)
	windowInput, windowCols := e.scan("nn3", 3)
	needed := e.f.ConstructProject(windowInput, memo.EmptyProjectionsExpr, opt.MakeColSet(windowCols[0], windowCols[1]))
	aggCol := e.f.Metadata().AddColumn("window", types.Int)
	group := e.f.ConstructGroupBy(needed, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructConstAgg(e.v(windowCols[1])), aggCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(windowCols[0])})
	join := e.f.ConstructInnerJoin(input, group, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(group.Relational().OutputCols.ToList()[0]))), &memo.JoinPrivate{})
	fullCols := join.Relational().OutputCols.ToList()
	before := e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTuple(memo.ScalarListExpr{e.v(fullCols[0]), e.v(fullCols[1]), e.v(fullCols[4])}, types.MakeTuple([]*types.T{types.Int, types.Int, types.Int})), e.f.Metadata().AddColumn("result", types.MakeTuple([]*types.T{types.Int, types.Int, types.Int}))),
	}, opt.ColSet{})

	pruned := e.f.ConstructProject(input, memo.EmptyProjectionsExpr, opt.MakeColSet(cols[0], cols[1]))
	expectedInput, expectedCols := windowInput, windowCols
	expectedWindow := e.f.ConstructProject(expectedInput, memo.EmptyProjectionsExpr, opt.MakeColSet(expectedCols[0], expectedCols[1]))
	expectedAggCol := aggCol
	expectedGroup := e.f.ConstructGroupBy(expectedWindow, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructConstAgg(e.v(expectedCols[1])), expectedAggCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(expectedCols[0])})
	expectedJoin := e.f.ConstructInnerJoin(pruned, expectedGroup, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(expectedCols[0]))), &memo.JoinPrivate{})
	expectedJoinCols := expectedJoin.Relational().OutputCols.ToList()
	want := e.f.ConstructProject(expectedJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTuple(memo.ScalarListExpr{e.v(expectedJoinCols[0]), e.v(expectedJoinCols[1]), e.v(expectedJoinCols[3])}, types.MakeTuple([]*types.T{types.Int, types.Int, types.Int})), e.f.Metadata().AddColumn("expected", types.MakeTuple([]*types.T{types.Int, types.Int, types.Int}))),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearMiss, nearCols := e.scan("nn3", 3)
	nearK := e.f.Metadata().AddColumn("near-k", types.Int)
	nearV := e.f.Metadata().AddColumn("near-v", types.Int)
	nearNeeded := e.f.ConstructProject(nearMiss, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(nearCols[0]), nearK),
		e.f.ConstructProjectionsItem(e.v(nearCols[1]), nearV),
	}, opt.ColSet{})
	nearAggCol := e.f.Metadata().AddColumn("near-window", types.Int)
	nearGroup := e.f.ConstructGroupBy(nearNeeded, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructConstAgg(e.v(nearV)), nearAggCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nearK)})
	nearJoin := e.f.ConstructInnerJoin(nearMiss, nearGroup, e.filters(e.f.ConstructEq(e.v(nearCols[1]), e.v(nearK))), &memo.JoinPrivate{})
	nearJoinCols := nearJoin.Relational().OutputCols.ToList()
	nearPlan := e.f.ConstructProject(nearJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTuple(memo.ScalarListExpr{e.v(nearJoinCols[0]), e.v(nearJoinCols[1]), e.v(nearJoinCols[4])}, types.MakeTuple([]*types.T{types.Int, types.Int, types.Int})), e.f.Metadata().AddColumn("near-result", types.MakeTuple([]*types.T{types.Int, types.Int, types.Int}))),
	}, opt.ColSet{})
	e.expectNoMatch(rule, nearPlan)
}
