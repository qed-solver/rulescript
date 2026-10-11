package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateMerge(t *testing.T) {
	rule := loadRule(t, "AggregateMerge")
	e := newEnv(t)
	s, c := e.scan("t", 3)
	k1 := e.f.Metadata().AddColumn("k1", types.Int)
	k2 := e.f.Metadata().AddColumn("k2", types.Int)
	v := e.f.Metadata().AddColumn("v", types.Int)
	n := e.f.Metadata().AddColumn("n", types.Int)

	keys := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(c[0]), k1),
		e.f.ConstructProjectionsItem(e.v(c[0]), k2),
	}, s.Relational().OutputCols)
	bottom := e.f.ConstructGroupBy(keys, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c[0])), v),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, k2)})
	before := e.f.ConstructGroupBy(bottom, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)})
	want := e.f.ConstructGroupBy(e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(c[0]), k1),
	}, s.Relational().OutputCols), nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructGroupBy(bottom, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(k2)), n),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)}))

	direct := e.f.ConstructGroupBy(s, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c[0])), v),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(c[0])})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(direct, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(c[0])}))

	oneKey := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(c[0]), k1),
	}, s.Relational().OutputCols)
	singleKeyBottom := e.f.ConstructGroupBy(oneKey, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c[0])), v),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(singleKeyBottom, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1)}))

	e.expectNoMatch(rule, e.f.ConstructSelect(bottom, e.filters(e.f.ConstructEq(e.v(k1), e.num(1)))))
}
