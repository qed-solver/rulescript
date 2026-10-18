package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRewriteDistinctAggregates(t *testing.T) {
	rule := loadRule(t, "RewriteDistinctAggregates")
	t.Run("global_count_distinct_constant", func(t *testing.T) {
		e := newEnv(t)
		input, inputCol := singleColumnScan(e, "t", 0)
		constCol := e.f.Metadata().AddColumn("const", types.Int)
		countCol := e.f.Metadata().AddColumn("count", types.Int)
		constProject := e.f.ConstructProject(input, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.num(0), constCol),
		}, opt.MakeColSet(inputCol))
		before := e.f.ConstructScalarGroupBy(constProject, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(constCol))), countCol),
		}, &memo.GroupingPrivate{})
		first := e.f.Metadata().AddColumn("const", types.Int)
		gid := e.f.Metadata().AddColumn("gid", types.Int)
		extended := e.f.ConstructProject(input, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.num(0), first),
			e.f.ConstructProjectionsItem(e.num(0), gid),
		}, opt.ColSet{})
		keyOne := e.f.Metadata().AddColumn("key", types.Int)
		keyTwo := e.f.Metadata().AddColumn("key", types.Int)
		keyed := e.f.ConstructProject(extended, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.num(0), keyOne),
			e.f.ConstructProjectionsItem(e.num(0), keyTwo),
		}, opt.MakeColSet(first, gid))
		groups := e.f.ConstructGroupBy(keyed, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(keyOne, keyTwo)})
		want := e.f.ConstructScalarGroupBy(groups, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructCount(e.num(0)), countCol),
		}, &memo.GroupingPrivate{})
		e.expectRewriteUpToColumnNames(rule, before, want)
	})
	t.Run("count_distinct_column_near_miss", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 2)
		countCol := e.f.Metadata().AddColumn("count", types.Int)
		before := e.f.ConstructScalarGroupBy(input, memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(cols[1]))), countCol)}, &memo.GroupingPrivate{})
		e.expectNoMatch(rule, before)
	})
}
