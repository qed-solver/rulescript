package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterAggregateTranspose(t *testing.T) {
	rule := loadRule(t, "FilterAggregateTranspose")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	sCol := e.f.Metadata().AddColumn("s", types.Int)
	agg := func(input memo.RelExpr) memo.RelExpr {
		return e.f.ConstructGroupBy(input, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(rc[0])), sCol),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	}
	keyPred := e.f.ConstructGt(e.v(lc[0]), e.num(1))
	keyLess := e.f.ConstructLt(e.v(lc[0]), e.num(10))
	sumPred := e.f.ConstructGt(e.v(sCol), e.num(5))
	before := e.f.ConstructSelect(agg(join), e.filters(
		e.f.ConstructAnd(keyPred, e.f.ConstructAnd(keyLess, sumPred)),
	))
	want := e.f.ConstructSelect(
		agg(e.f.ConstructSelect(join, e.filters(keyPred))),
		e.filters(keyLess, sumPred),
	)
	e.expectRewrite(rule, before, want)

	sumFirst := e.f.ConstructGt(e.v(sCol), e.num(1))
	e.expectNoMatch(rule, e.f.ConstructSelect(agg(join), e.filters(
		e.f.ConstructAnd(sumFirst, e.f.ConstructAnd(keyLess, sumPred)),
	)))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructGroupBy(e.f.ConstructLeftJoin(l, r, nil, &memo.JoinPrivate{}), memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(rc[0])), sCol),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])}),
		e.filters(e.f.ConstructAnd(keyPred, e.f.ConstructAnd(keyLess, sumPred))),
	))
}
