package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSparkEliminateDistinct(t *testing.T) {
	rule := loadRule(t, "SparkEliminateDistinct")

	t.Run("unique-key-argument", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 1)
		key := e.f.Metadata().AddColumn("key", types.Int)
		input = e.f.ConstructProject(input, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[0]), key)}, input.Relational().OutputCols)
		group := e.f.ConstructBitAndAgg(e.v(cols[0]))
		distinct := e.f.ConstructAggDistinct(group)
		out := e.f.Metadata().AddColumn("agg", types.Int)
		before := e.f.ConstructGroupBy(input, memo.AggregationsExpr{e.f.ConstructAggregationsItem(distinct, out)}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)})
		plain := e.f.ConstructBitAndAgg(e.v(cols[0]))
		want := e.f.ConstructGroupBy(input, memo.AggregationsExpr{e.f.ConstructAggregationsItem(plain, out)}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)})
		e.expectRewrite(rule, before, want)
	})

	t.Run("non-unique-scan", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 2)
		group := e.f.ConstructBitAndAgg(e.v(cols[1]))
		distinct := e.f.ConstructAggDistinct(group)
		out := e.f.Metadata().AddColumn("agg", types.Int)
		plan := e.f.ConstructGroupBy(input, memo.AggregationsExpr{e.f.ConstructAggregationsItem(distinct, out)}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[1])})
		e.expectNoMatch(rule, plan)
	})
}
