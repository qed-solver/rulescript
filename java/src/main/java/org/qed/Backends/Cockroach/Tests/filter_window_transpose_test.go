package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterWindowTranspose(t *testing.T) {
	rule := loadRule(t, "FilterWindowTranspose")
	e := newEnv(t)
	base, bc := e.scan("t", 2)
	k, v := bc[0], bc[1]
	maxCol := e.f.Metadata().AddColumn("max", types.Int)
	minCol := e.f.Metadata().AddColumn("min", types.Int)
	groupKeys := opt.MakeColSet(k)
	maxAgg := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(v)), maxCol)}
	group := func(input memo.RelExpr) memo.RelExpr {
		return e.f.ConstructGroupBy(input, maxAgg, &memo.GroupingPrivate{GroupingCols: groupKeys})
	}
	predP := e.f.ConstructGt(e.v(k), e.num(1))
	predQ := e.f.ConstructLt(e.v(k), e.v(maxCol))

	before := e.f.ConstructSelect(group(base), e.filters(predP, predQ))
	want := e.f.ConstructSelect(group(e.f.ConstructSelect(base, e.filters(predP))), e.filters(predQ))
	e.expectRewrite(rule, before, want)

	twoAggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(v)), maxCol),
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(v)), minCol),
	}
	twoAggGroup := e.f.ConstructGroupBy(base, twoAggs, &memo.GroupingPrivate{GroupingCols: groupKeys})
	e.expectNoMatch(rule, e.f.ConstructSelect(twoAggGroup, e.filters(predP, predQ)))
	e.expectNoMatch(rule, e.f.ConstructSelect(group(base), e.filters(predQ)))
}
