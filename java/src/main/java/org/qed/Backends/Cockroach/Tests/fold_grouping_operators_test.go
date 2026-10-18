package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldGroupingOperators(t *testing.T) {
	rule := loadRule(t, "FoldGroupingOperators")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	a := sc[0]
	keyCol := e.f.Metadata().AddColumn("k1", types.Int)
	maxCol := e.f.Metadata().AddColumn("max", types.Int)
	keyed := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(a), keyCol),
	}, opt.MakeColSet(a))
	inner := e.f.ConstructGroupBy(keyed, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(a)), maxCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(keyCol)})
	outer := e.f.ConstructGroupBy(inner, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(keyCol)})
	wantKeyed := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(a), keyCol),
	}, opt.MakeColSet(a))
	want := e.f.ConstructGroupBy(wantKeyed, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(keyCol)})
	e.expectRewriteUpToFreshColumns(rule, outer, want)

	outCol := e.f.Metadata().AddColumn("outmax", types.Int)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(inner, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(maxCol)), outCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(keyCol)}))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(inner, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(maxCol)}))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(keyed, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(keyCol)}))
}
