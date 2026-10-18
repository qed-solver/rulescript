package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestExtractGroupingPythonUDFFromAggregate(t *testing.T) {
	t.Run("fires", func(t *testing.T) {
		rule := loadRule(t, "ExtractGroupingPythonUDFFromAggregate")
		e := newEnv(t)
		in, cols := e.scan("u", 1)
		x := cols[0]
		kCol := e.f.Metadata().AddColumn("k", types.Int)
		sCol := e.f.Metadata().AddColumn("s", types.Decimal)
		gx := func() opt.ScalarExpr { return e.f.ConstructPlus(e.v(x), e.num(1)) }
		keyed := e.f.ConstructProject(in, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(gx(), kCol),
		}, opt.MakeColSet(x))
		before := e.f.ConstructGroupBy(keyed, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(x)), sCol),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(kCol)})

		e.expectAbsorbed(rule, before)
	})

	t.Run("does not match", func(t *testing.T) {
		rule := loadRule(t, "ExtractGroupingPythonUDFFromAggregate")
		e := newEnv(t)
		in, cols := e.scan("u", 1)
		x := cols[0]
		kCol := e.f.Metadata().AddColumn("k", types.Int)
		sCol := e.f.Metadata().AddColumn("s", types.Decimal)
		sumX := func() opt.ScalarExpr { return e.f.ConstructSum(e.v(x)) }
		keyed := e.f.ConstructProject(in, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(x), e.num(1)), kCol),
		}, opt.MakeColSet(x))

		e.expectNoMatch(rule, e.f.ConstructGroupBy(keyed, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(sumX()), sCol),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(kCol)}))

		e.expectNoMatch(rule, e.f.ConstructGroupBy(in, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(sumX(), sCol),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(x)}))

		k2 := e.f.Metadata().AddColumn("k2", types.Int)
		twoKeys := e.f.ConstructProject(in, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(x), e.num(1)), kCol),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(x), e.num(2)), k2),
		}, opt.MakeColSet(x))
		e.expectNoMatch(rule, e.f.ConstructGroupBy(twoKeys, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(sumX(), sCol),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(kCol, k2)}))
	})
}
