package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateUnionAggregate(t *testing.T) {
	rule := loadRule(t, "AggregateUnionAggregate")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	l, lc := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("out", types.Int)
	priv := &memo.SetPrivate{
		LeftCols:  opt.ColList{ac[0]},
		RightCols: opt.ColList{lc[0]},
		OutCols:   opt.ColList{out},
	}
	grouping := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(out)}
	dedupL := e.f.ConstructGroupBy(l, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	before := e.f.ConstructGroupBy(e.f.ConstructUnionAll(a, dedupL, priv), nil, grouping)
	want := e.f.ConstructGroupBy(e.f.ConstructUnionAll(a, l, priv), nil, grouping)
	e.expectRewrite(rule, before, want)

	count := e.f.Metadata().AddColumn("count", types.Int)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(e.f.ConstructUnion(a, dedupL, priv), nil, grouping))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(e.f.ConstructUnionAll(a, dedupL, priv), memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(out)), count),
	}, grouping))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(e.f.ConstructUnionAll(a, l, priv), nil, grouping))
}
