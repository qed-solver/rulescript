package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestTryDecorrelateWindow(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateWindow")
	e := newEnv(t)

	left, leftCols := e.scan("nn", 1)
	input, inputCols := e.scan("t", 1)
	windowAggCol := e.f.Metadata().AddColumn("window_agg", types.Int)
	scalarAgg := e.f.ConstructScalarGroupBy(input, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAnyNotNullAgg(e.v(inputCols[0])), windowAggCol),
	}, &memo.GroupingPrivate{})
	window := e.f.ConstructInnerJoin(input, scalarAgg, nil, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(left, window, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(inputCols[0]))), &memo.JoinPrivate{})

	cross := e.f.ConstructInnerJoin(left, input, nil, &memo.JoinPrivate{})
	partitionCol := e.f.Metadata().AddColumn("partition", types.Int)
	partitionItem := e.f.ConstructProjectionsItem(e.v(leftCols[0]), partitionCol)
	partitionLeft := e.f.ConstructProject(left, memo.ProjectionsExpr{partitionItem}, opt.ColSet{})
	partitionInput := e.f.ConstructInnerJoin(partitionLeft, input, nil, &memo.JoinPrivate{})
	groupAggCol := windowAggCol
	perPartition := e.f.ConstructGroupBy(partitionInput, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAnyNotNullAgg(e.v(inputCols[0])), groupAggCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(partitionCol)})
	partitionCond := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(partitionCol)))
	joined := e.f.ConstructInnerJoin(cross, perPartition, partitionCond, &memo.JoinPrivate{})
	selected := e.f.ConstructSelect(joined, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(inputCols[0]))))
	want := e.f.ConstructProject(selected, nil, opt.MakeColSet(leftCols[0], inputCols[0], groupAggCol))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearMiss := e.f.ConstructLeftJoin(left, window, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(inputCols[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
