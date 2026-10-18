package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectWindowTranspose(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectWindowTranspose")
	build := func(usesUnused bool) (memo.RelExpr, memo.RelExpr) {
		scan, cols := e.scan("t", 3)
		key := e.f.Metadata().AddColumn("window_key", types.Int)
		value := e.f.Metadata().AddColumn("window_value", types.Int)
		needed := e.f.ConstructProject(scan, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(cols[0]), key),
			e.f.ConstructProjectionsItem(e.v(cols[1]), value),
		}, opt.ColSet{})
		count := e.f.Metadata().AddColumn("window_sum", types.Decimal)
		max := e.f.Metadata().AddColumn("window_max", types.Int)
		grouped := e.f.ConstructGroupBy(needed, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(value)), count),
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(value)), max),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)})
		cond := e.f.ConstructEq(e.v(cols[0]), e.v(key))
		joined := e.f.ConstructInnerJoin(scan, grouped, e.filters(cond), &memo.JoinPrivate{})
		arg := e.v(cols[0])
		if usesUnused {
			arg = e.v(cols[2])
		}
		top := e.f.ConstructPlus(arg, e.v(cols[1]))
		beforeCol := e.f.Metadata().AddColumn("top_before", types.Int)
		before := e.f.ConstructProject(joined, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(top, beforeCol),
		}, opt.ColSet{})
		pruned := e.f.ConstructProject(scan, nil, opt.MakeColSet(cols[0], cols[1]))
		prunedGroup := e.f.ConstructGroupBy(pruned, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(cols[1])), count),
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(cols[1])), max),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])})
		groupKey := e.f.Metadata().AddColumn("group_key_after", types.Int)
		groupKeyProjection := e.f.ConstructProject(prunedGroup, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(cols[0]), groupKey),
		}, opt.MakeColSet(count, max))
		prunedJoin := e.f.ConstructInnerJoin(pruned, groupKeyProjection, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(groupKey))), &memo.JoinPrivate{})
		topAfter := e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1]))
		afterCol := e.f.Metadata().AddColumn("top_after", types.Int)
		after := e.f.ConstructProject(prunedJoin, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(topAfter, afterCol),
		}, opt.ColSet{})
		return before, after
	}
	before, want := build(false)
	e.expectRewriteUpToFreshColumns(rule, before, want)
	nearMiss, _ := build(true)
	e.expectNoMatch(rule, nearMiss)
}
