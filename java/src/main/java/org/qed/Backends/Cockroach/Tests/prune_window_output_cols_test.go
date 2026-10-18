package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneWindowOutputCols(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneWindowOutputCols")

	input, cols := e.scan("nn", 2)
	aggInput, aggCols := e.scan("nn", 2)
	windowOne := e.f.Metadata().AddColumn("window-one", types.Int)
	windowTwo := e.f.Metadata().AddColumn("window-two", types.Int)
	group := e.f.ConstructGroupBy(aggInput, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructConstAgg(e.v(aggCols[1])), windowOne),
		e.f.ConstructAggregationsItem(e.f.ConstructAnyNotNullAgg(e.v(aggCols[1])), windowTwo),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(aggCols[0])})
	join := e.f.ConstructInnerJoin(input, group, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(aggCols[0]))), &memo.JoinPrivate{})
	before := e.f.ConstructProject(join, memo.EmptyProjectionsExpr, opt.MakeColSet(cols[0], cols[1], windowOne))

	prunedGroup := e.f.ConstructGroupBy(aggInput, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructConstAgg(e.v(aggCols[1])), windowOne),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(aggCols[0])})
	prunedJoin := e.f.ConstructInnerJoin(input, prunedGroup, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(aggCols[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(prunedJoin, memo.EmptyProjectionsExpr, opt.MakeColSet(cols[0], cols[1], windowOne))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearInput, nearCols := e.scan("nn", 2)
	nearAggInput, nearAggCols := e.scan("nn", 2)
	nearOne := e.f.Metadata().AddColumn("near-window-one", types.Int)
	nearTwo := e.f.Metadata().AddColumn("near-window-two", types.Int)
	nearGroup := e.f.ConstructGroupBy(nearAggInput, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructConstAgg(e.v(nearAggCols[1])), nearOne),
		e.f.ConstructAggregationsItem(e.f.ConstructAnyNotNullAgg(e.v(nearAggCols[1])), nearTwo),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nearAggCols[0])})
	nearJoin := e.f.ConstructInnerJoin(nearInput, nearGroup, e.filters(e.f.ConstructEq(e.v(nearCols[0]), e.v(nearAggCols[0]))), &memo.JoinPrivate{})
	nearPlan := e.f.ConstructProject(nearJoin, memo.EmptyProjectionsExpr, opt.MakeColSet(nearCols[0], nearCols[1], nearTwo))
	e.expectNoMatch(rule, nearPlan)
}
