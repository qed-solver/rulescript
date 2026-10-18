package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateFilterTranspose(t *testing.T) {
	rule := loadRule(t, "AggregateFilterTranspose")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	sum := e.f.Metadata().AddColumn("sum", types.Int)
	groupBy := func(input memo.RelExpr) memo.RelExpr {
		aggs := memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(rc[0])), sum),
		}
		return e.f.ConstructGroupBy(input, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	}
	inner := e.f.ConstructInnerJoin(l, r, memo.FiltersExpr{}, &memo.JoinPrivate{})
	before := groupBy(e.f.ConstructSelect(inner, e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(5)))))
	want := e.f.ConstructSelect(groupBy(inner), e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(5))))
	e.expectRewrite(rule, before, want)

	left := e.f.ConstructLeftJoin(l, r, memo.FiltersExpr{}, &memo.JoinPrivate{})
	e.expectNoMatch(rule, groupBy(e.f.ConstructSelect(left, e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(5))))))
	e.expectNoMatch(rule, groupBy(e.f.ConstructSelect(inner, e.filters(e.f.ConstructGt(e.v(rc[0]), e.num(5))))))
}
